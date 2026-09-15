# Home Feed Recency Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every row on the Home tab show its videos newest-first (and drop videos already watched past 5%), for both the initial page and any continuation pages appended to a row.

**Architecture:** YouTube's browse API never gives an absolute timestamp for feed cards (`MediaItem.getPublishedDate()` is hard-coded to `-1` in `BaseMediaItem.kt`); the only recency signal is the localized relative text such as `"2 days ago"` exposed by `MediaItem.getProductionDate()` and embedded in `Video.secondTitle`. We add a small pure parser that turns that text into an age in minutes, a pure sorter/filter that orders a `List<Video>` by that age, two tiny mutators on `VideoGroup` (`sort`, `removeIf`), and call them from `BrowsePresenter` on the two Home code paths (initial rows and row continuation). Nothing in the `MediaServiceCore` or `SharedModules` submodules is modified.

**Tech Stack:** Java 8 (Android, minSdk 19 compatible: no streams, no `List.sort`, no `java.time`), JUnit 4 (already a `testImplementation` of `:common`), Gradle wrapper.

**Spec:** `implementation_plan.md` at the repo root (decisions: filter >5% watched, Home tab only, parse relative-time text, sparse rows acceptable). This plan supersedes that file; replace its contents with this document when starting work.

## Global Constraints

- Only the `common` module is edited. `MediaServiceCore/` and `SharedModules/` are git submodules and must stay untouched.
- Language level is Java 8 without desugared library APIs: use `Collections.sort`, `Helpers.removeIf`, `java.util.regex`. No lambdas that capture Android-only types in unit tests.
- Existing behaviour on every non-Home section must be byte-for-byte unchanged. All new calls are guarded by `isHomeSection()`.
- Unparseable or non-English recency text must never crash and must never reorder items relative to each other (stable sort, unknown age sinks to the end).
- Commit after every task with a Conventional-Commit message ending in `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

---

## Why the previous attempt showed no effect (root cause)

Nothing was ever implemented. Commit `458936f3e` added only `implementation_plan.md`; there is no sort or filter code in `BrowsePresenter.java`, `VideoGroup.java`, or `DateHelper.java` (verified with grep on a clean working tree). So the Home tab still shows rows exactly in the order YouTube returns them.

Two further facts that any implementation must respect and that the old plan missed:

1. `MediaItem.getPublishedDate()` returns `-1` for every YouTube item (`MediaServiceCore/youtubeapi/.../mediaitem/BaseMediaItem.kt:123`). The usable field is `MediaItem.getProductionDate()`, which returns the raw relative text (`"2 days ago"`, `"Streamed 3 weeks ago"`), populated from `publishedTimeText` in `MediaItemImpl.kt:25`. `Video.from(MediaItem)` keeps a reference in `video.mediaItem`, so the presenter can read it.
2. Rows are filled in two phases. `updateVideoRows` builds a `VideoGroup` per row from the first page, then `continueGroupIfNeeded` may fetch more pages and *append* them to the same `VideoGroup` (`VideoGroup.from(VideoGroup, MediaGroup)`), and `VideoGroupObjectAdapter.append` only inserts the new tail `subList(begin, end)`. Sorting only the first page would leave later pages unsorted; re-sorting the whole list after an append would corrupt the adapter's index arithmetic. Therefore each appended batch is sorted independently before it is appended.

Relevant existing code to reuse:

- `Helpers.removeIf(Collection<T>, Filter<T>)` in `SharedModules/sharedutils/.../Helpers.java:1814`.
- `VideoGroup.add(int, Video)` (`common/.../data/VideoGroup.java:456`) already drops `percentWatched > 95` on Home and syncs local watch state from `VideoStateService`, so by the time our filter runs `video.percentWatched` already reflects local progress.
- `VideoGroup.from(VideoGroup base, VideoGroup batch)` (`VideoGroup.java:152`) appends a batch group's videos to a base group and keeps the continuation `MediaGroup`. This is the hook for sorted continuation.

## File Structure

| File | Responsibility |
|---|---|
| Create `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParser.java` | Pure text → age-in-minutes parser. No Android imports. |
| Create `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParserTest.java` | JUnit tests for the parser. |
| Create `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorter.java` | Recency key for a `Video`, watched filter, comparator, `apply(VideoGroup)`. |
| Create `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorterTest.java` | JUnit tests for the pure `List<Video>` sorting/filtering. |
| Modify `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/data/VideoGroup.java` | Add `sort(Comparator<Video>)` and `removeIf(Helpers.Filter<Video>)`. |
| Modify `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java` | Call the sorter on Home initial rows (`updateVideoRows`, ~line 744) and Home continuation (`continueGroup`, ~line 858). |
| Replace `implementation_plan.md` | Store this plan in the repo for the implementing agent. |

---

### Task 1: RelativeDateParser (text → age in minutes)

**Files:**
- Create: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParser.java`
- Test: `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParserTest.java`

**Interfaces:**
- Produces: `public static long RelativeDateParser.toAgeMinutes(CharSequence text)` returning minutes since publish, or `RelativeDateParser.UNKNOWN` (`-1`) when the text contains no recognised pattern. Also `public static final long UNKNOWN = -1`.

- [ ] **Step 1: Write the failing test**

```java
package com.liskovsoft.smartyoutubetv2.common.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RelativeDateParserTest {
    @Test
    public void parsesPlainUnits() {
        assertEquals(0, RelativeDateParser.toAgeMinutes("30 seconds ago"));
        assertEquals(1, RelativeDateParser.toAgeMinutes("1 minute ago"));
        assertEquals(45, RelativeDateParser.toAgeMinutes("45 minutes ago"));
        assertEquals(3 * 60, RelativeDateParser.toAgeMinutes("3 hours ago"));
        assertEquals(2 * 1440, RelativeDateParser.toAgeMinutes("2 days ago"));
        assertEquals(1 * 10080, RelativeDateParser.toAgeMinutes("1 week ago"));
        assertEquals(5 * 43200, RelativeDateParser.toAgeMinutes("5 months ago"));
        assertEquals(2 * 525600, RelativeDateParser.toAgeMinutes("2 years ago"));
    }

    @Test
    public void parsesYouTubePrefixes() {
        assertEquals(4 * 60, RelativeDateParser.toAgeMinutes("Streamed 4 hours ago"));
        assertEquals(1440, RelativeDateParser.toAgeMinutes("Premiered 1 day ago"));
        assertEquals(6 * 1440, RelativeDateParser.toAgeMinutes("Updated 6 days ago"));
    }

    @Test
    public void parsesFromFullSubtitle() {
        // Video.secondTitle is "Channel • 1.2M views • 2 days ago"
        assertEquals(2 * 1440, RelativeDateParser.toAgeMinutes("Some Channel • 1.2M views • 2 days ago"));
    }

    @Test
    public void isCaseInsensitiveAndTrimTolerant() {
        assertEquals(60, RelativeDateParser.toAgeMinutes("  1 HOUR AGO "));
    }

    @Test
    public void returnsUnknownWhenNothingMatches() {
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes(null));
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes(""));
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes("1.2M views"));
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes("Premieres in 2 hours"));
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes("Scheduled for 9/20/26, 3:00 PM"));
        assertEquals(RelativeDateParser.UNKNOWN, RelativeDateParser.toAgeMinutes("2 дня назад")); // non-English is out of scope
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:testStstableDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.utils.RelativeDateParserTest"`

(If that variant name is rejected, list variants with `./gradlew :common:tasks --all | grep -i unittest` and use the `test<Flavor>DebugUnitTest` task shown.)

Expected: compilation FAILS with `cannot find symbol: class RelativeDateParser`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts YouTube's relative publish text ("2 days ago", "Streamed 4 hours ago")
 * into an approximate age in minutes so cards can be ordered by recency.
 * The YouTube API gives no absolute timestamp for feed cards; this text is the only signal.
 * Only English is recognised; anything else yields {@link #UNKNOWN}.
 */
public final class RelativeDateParser {
    public static final long UNKNOWN = -1;

    private static final long MINUTE = 1;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    // "3 hours ago", "1 day ago"; find() so prefixes ("Streamed ", "Channel • 1M views • ") are tolerated.
    private static final Pattern AGO = Pattern.compile(
            "(\\d+)\\s*(second|minute|hour|day|week|month|year)s?\\s+ago",
            Pattern.CASE_INSENSITIVE);

    private RelativeDateParser() {
    }

    /**
     * @return age in minutes (0 for "seconds ago"), or {@link #UNKNOWN} when no pattern is found
     */
    public static long toAgeMinutes(CharSequence text) {
        if (text == null || text.length() == 0) {
            return UNKNOWN;
        }

        Matcher matcher = AGO.matcher(text);

        if (!matcher.find()) {
            return UNKNOWN;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }

        String unit = matcher.group(2).toLowerCase();

        switch (unit) {
            case "second": return 0;
            case "minute": return amount * MINUTE;
            case "hour":   return amount * HOUR;
            case "day":    return amount * DAY;
            case "week":   return amount * WEEK;
            case "month":  return amount * MONTH;
            case "year":   return amount * YEAR;
            default:       return UNKNOWN;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:testStstableDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.utils.RelativeDateParserTest"`

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParser.java common/src/test/java/com/liskovsoft/smartyoutubetv2/common/utils/RelativeDateParserTest.java
git commit -m "feat(common): add RelativeDateParser for YouTube 'N units ago' text

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: VideoGroup mutators (`sort`, `removeIf`)

**Files:**
- Modify: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/data/VideoGroup.java` (add after `remove(Video)` at ~line 431)

**Interfaces:**
- Produces: `public void VideoGroup.sort(Comparator<Video> comparator)` (stable, in place, no-op when list is null/empty or read-only) and `public void VideoGroup.removeIf(Helpers.Filter<Video> filter)`.

No unit test for this task: `VideoGroup.add` touches `VideoStateService`/`BlockedChannelData` singletons that need an Android context, and the new methods are two-line delegations to library calls. They are exercised on-device in Task 4 verification.

- [ ] **Step 1: Add the two methods**

Insert directly after the existing `remove(Video video)` method:

```java
    /**
     * Stable in-place sort. Safe on read-only lists (logs and leaves order unchanged).
     */
    public void sort(Comparator<Video> comparator) {
        if (mVideos == null || comparator == null || mVideos.size() < 2) {
            return;
        }

        try {
            Collections.sort(mVideos, comparator); // Collections.sort is stable
        } catch (UnsupportedOperationException | ConcurrentModificationException e) { // read only collection
            e.printStackTrace();
        }
    }

    public void removeIf(Helpers.Filter<Video> filter) {
        if (mVideos == null || filter == null) {
            return;
        }

        try {
            Helpers.removeIf(mVideos, filter);
        } catch (UnsupportedOperationException | ConcurrentModificationException e) { // read only collection
            e.printStackTrace();
        }
    }
```

Add `import java.util.Comparator;` if not already imported (`java.util.Collections`, `java.util.ConcurrentModificationException`, and `com.liskovsoft.sharedutils.helpers.Helpers` are already imported in this file).

- [ ] **Step 2: Compile**

Run: `./gradlew :common:compileStstableDebugJavaWithJavac`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/data/VideoGroup.java
git commit -m "feat(common): add VideoGroup.sort and removeIf helpers

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: HomeRecencySorter (pure ordering + watched filter)

**Files:**
- Create: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorter.java`
- Test: `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorterTest.java`

**Interfaces:**
- Consumes: `RelativeDateParser.toAgeMinutes(CharSequence)` (Task 1); `VideoGroup.sort`, `VideoGroup.removeIf` (Task 2); `Video` public fields `mediaItem`, `secondTitle`, `isLive`, `isUpcoming`, `percentWatched`.
- Produces:
  - `public static final float WATCHED_THRESHOLD_PERCENT = 5f`
  - `public static long recencyKey(Video video)` — minutes of age; `0` for live streams; `Long.MAX_VALUE` for upcoming or unknown.
  - `public static boolean isWatched(Video video)` — `percentWatched > 5`.
  - `public static void sort(List<Video> videos)` — stable sort ascending by `recencyKey`, in place.
  - `public static void apply(VideoGroup group)` — removes watched then sorts; used by the presenter.

- [ ] **Step 1: Write the failing test**

```java
package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeRecencySorterTest {
    private static Video video(String title, String subtitle) {
        Video v = new Video();
        v.title = title;
        v.secondTitle = subtitle;
        return v;
    }

    @Test
    public void recencyKeyReadsAgeFromSubtitle() {
        assertEquals(2 * 1440, HomeRecencySorter.recencyKey(video("a", "Ch • 10K views • 2 days ago")));
    }

    @Test
    public void liveIsNewestAndUpcomingOrUnknownIsOldest() {
        Video live = video("live", "Ch • 3K watching");
        live.isLive = true;
        Video upcoming = video("up", "Ch • Premieres in 2 hours");
        upcoming.isUpcoming = true;
        Video unknown = video("?", "Ch • 1M views");

        assertEquals(0, HomeRecencySorter.recencyKey(live));
        assertEquals(Long.MAX_VALUE, HomeRecencySorter.recencyKey(upcoming));
        assertEquals(Long.MAX_VALUE, HomeRecencySorter.recencyKey(unknown));
    }

    @Test
    public void sortsNewestFirstAndKeepsUnknownOrderStable() {
        Video oneYear = video("year", "Ch • 1 year ago");
        Video twoHours = video("2h", "Ch • 2 hours ago");
        Video unknownA = video("uA", "Ch • 5K views");
        Video threeDays = video("3d", "Ch • 3 days ago");
        Video unknownB = video("uB", "Ch • 7K views");
        Video live = video("live", "Ch • 900 watching");
        live.isLive = true;

        List<Video> list = new ArrayList<>(Arrays.asList(oneYear, twoHours, unknownA, threeDays, unknownB, live));
        HomeRecencySorter.sort(list);

        assertEquals(Arrays.asList(live, twoHours, threeDays, oneYear, unknownA, unknownB), list);
    }

    @Test
    public void watchedThresholdIsFivePercent() {
        Video fresh = video("f", "Ch • 1 day ago");
        fresh.percentWatched = -1; // never watched (default from API)
        Video barely = video("b", "Ch • 1 day ago");
        barely.percentWatched = 5;
        Video watched = video("w", "Ch • 1 day ago");
        watched.percentWatched = 6;

        assertFalse(HomeRecencySorter.isWatched(fresh));
        assertFalse(HomeRecencySorter.isWatched(barely));
        assertTrue(HomeRecencySorter.isWatched(watched));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:testStstableDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.misc.HomeRecencySorterTest"`

Expected: compilation FAILS with `cannot find symbol: class HomeRecencySorter`.

(If the `Video` class cannot be constructed in a plain JVM test because of a static Android dependency, annotate the test class with `@RunWith(RobolectricTestRunner.class)`; Robolectric is already a test dependency of `:common` and `testOptions.unitTests.includeAndroidResources = true` is set in `common/build.gradle`.)

- [ ] **Step 3: Write minimal implementation**

```java
package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.utils.RelativeDateParser;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orders Home feed cards newest-first and drops cards the user already started watching.
 * Recency comes from YouTube's relative "N days ago" text because the API gives no absolute date.
 */
public final class HomeRecencySorter {
    public static final float WATCHED_THRESHOLD_PERCENT = 5f;

    private static final Comparator<Video> BY_RECENCY = new Comparator<Video>() {
        @Override
        public int compare(Video a, Video b) {
            long ka = recencyKey(a);
            long kb = recencyKey(b);
            return ka < kb ? -1 : (ka == kb ? 0 : 1);
        }
    };

    private HomeRecencySorter() {
    }

    /**
     * @return minutes since publish: 0 for live streams, Long.MAX_VALUE for upcoming or unparseable
     */
    public static long recencyKey(Video video) {
        if (video == null || video.isUpcoming) {
            return Long.MAX_VALUE;
        }

        if (video.isLive) {
            return 0;
        }

        long age = RelativeDateParser.UNKNOWN;

        if (video.mediaItem != null) {
            age = RelativeDateParser.toAgeMinutes(video.mediaItem.getProductionDate());
        }

        if (age == RelativeDateParser.UNKNOWN) {
            age = RelativeDateParser.toAgeMinutes(video.secondTitle);
        }

        return age == RelativeDateParser.UNKNOWN ? Long.MAX_VALUE : age;
    }

    public static boolean isWatched(Video video) {
        return video != null && video.percentWatched > WATCHED_THRESHOLD_PERCENT;
    }

    /** Stable, in place. Unknown ages keep their original relative order at the end. */
    public static void sort(List<Video> videos) {
        if (videos == null || videos.size() < 2) {
            return;
        }

        Collections.sort(videos, BY_RECENCY);
    }

    /** Remove watched cards, then order the remainder newest-first. */
    public static void apply(VideoGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        group.removeIf(HomeRecencySorter::isWatched);
        group.sort(BY_RECENCY);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:testStstableDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.misc.HomeRecencySorterTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorter.java common/src/test/java/com/liskovsoft/smartyoutubetv2/common/misc/HomeRecencySorterTest.java
git commit -m "feat(common): add HomeRecencySorter (newest-first, drop >5% watched)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Wire the sorter into BrowsePresenter (initial rows + continuation)

**Files:**
- Modify: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java`
  - `updateVideoRows(BrowseSection, Observable<List<MediaGroup>>)` around lines 738–754
  - `continueGroup(VideoGroup, boolean)` around lines 853–863

**Interfaces:**
- Consumes: `HomeRecencySorter.apply(VideoGroup)` (Task 3); existing `isHomeSection()`, `VideoGroup.from(MediaGroup, BrowseSection)`, `VideoGroup.from(VideoGroup, MediaGroup)`, `VideoGroup.from(VideoGroup, VideoGroup)`.

- [ ] **Step 1: Sort each initial Home row**

In `updateVideoRows`, inside the `for (MediaGroup mediaGroup : mediaGroups)` loop, change

```java
                                VideoGroup videoGroup = VideoGroup.from(mediaGroup, section);

                                if (TextUtils.isEmpty(videoGroup.getTitle())) {
```

to

```java
                                VideoGroup videoGroup = VideoGroup.from(mediaGroup, section);

                                if (isHomeSection()) {
                                    HomeRecencySorter.apply(videoGroup);
                                }

                                if (videoGroup.isEmpty()) { // every card was watched
                                    continue;
                                }

                                if (TextUtils.isEmpty(videoGroup.getTitle())) {
```

Add `import com.liskovsoft.smartyoutubetv2.common.misc.HomeRecencySorter;`.

- [ ] **Step 2: Sort each continuation batch before appending**

In `continueGroup(VideoGroup group, boolean showLoading)`, change the success lambda body

```java
                        continueGroup -> {
                            getView().showProgressBar(false);

                            VideoGroup videoGroup = VideoGroup.from(group, continueGroup);
                            getView().updateSection(videoGroup);
```

to

```java
                        continueGroup -> {
                            getView().showProgressBar(false);

                            VideoGroup videoGroup;

                            if (isHomeSection()) {
                                // Sort the new page on its own, then append. The adapter only inserts the
                                // appended tail (VideoGroupObjectAdapter.append), so the already visible
                                // part of the row must not be reordered.
                                VideoGroup batch = VideoGroup.from(continueGroup, group.getSection());
                                HomeRecencySorter.apply(batch);
                                videoGroup = VideoGroup.from(group, batch);
                            } else {
                                videoGroup = VideoGroup.from(group, continueGroup);
                            }

                            getView().updateSection(videoGroup);
```

Note: `VideoGroup.from(VideoGroup base, VideoGroup batch)` sets `base.mMediaGroup = batch.mMediaGroup` (which `VideoGroup.from(continueGroup, section)` set to `continueGroup`), so the next-page key for further continuation is preserved exactly as before.

- [ ] **Step 3: Compile and run all `:common` unit tests**

Run: `./gradlew :common:testStstableDebugUnitTest`

Expected: BUILD SUCCESSFUL, all tests pass (the two new test classes plus any pre-existing ones).

- [ ] **Step 4: Build and install a debug APK**

Run: `./gradlew assembleStstableDebug` then `adb install -r smarttubetv/build/outputs/apk/ststable/debug/*.apk` (see `build.md` for flavour names).

Expected: BUILD SUCCESSFUL, app installs.

- [ ] **Step 5: On-device verification**

1. Set app language to English (Settings → General → Language) if it is not already; the parser only understands English text.
2. Open the Home tab. In every row, read the "N units ago" text on consecutive cards: live streams first, then ascending age (hours before days before weeks before months before years). Cards without a time (playlists, channels, mixes) sit at the end of the row in their original order.
3. Scroll right to the end of a short row so a continuation page loads. The appended page must itself be ascending, though it may restart from a smaller age than the last card of the previous page (documented limitation).
4. Play any Home video for ~30 seconds so its local progress exceeds 5%, go back, and refresh Home (long-press Home or leave/return). That video must no longer be in any row.
5. Open Subscriptions and any channel: order must be unchanged from before this change.

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java
git commit -m "feat(browse): sort Home rows newest-first and hide watched cards

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Replace the stale plan document in the repo

**Files:**
- Modify: `implementation_plan.md` (repo root)

- [ ] **Step 1: Overwrite with this plan**

Copy this document verbatim into `implementation_plan.md`, replacing the 35-line stub from commit `458936f3e`.

- [ ] **Step 2: Commit**

```bash
git add implementation_plan.md
git commit -m "docs: replace Home recency sort plan with implemented design

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Known limitations (intentional, per spec decisions)

- **English only.** The relative text arrives in the app's YouTube language (`LocaleManager.getLanguage()`). For other languages every key is `UNKNOWN`, the row stays in YouTube's order, and nothing crashes. Adding a language means adding a second `Pattern` in `RelativeDateParser` with that language's unit words.
- **Month/year granularity.** "1 month ago" is ordered as 30 days; YouTube rounds, so two cards both labelled "1 year ago" keep their original relative order.
- **Per-page ordering on continuation.** A row is sorted per fetched page, not globally, because the row adapter appends by index. In practice Home rows only continue when they have fewer than `MIN_ROW_GROUP_SIZE` cards (`MediaServiceManager.shouldContinueTheGroup`), so this rarely shows.
- **Sparse rows.** Filtering can shrink or empty a row; an emptied row is skipped. No extra fetching is done.
