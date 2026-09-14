# Implementation Plan: Sort and Filter Recommended Videos

This plan details the technical steps to filter out watched videos and sort the remaining recommendations by recency on the Home screen.

## Decisions Made

- **Filtering strictness**: We will filter out videos that have been partially watched with a progress of >5%.
- **Scope of the feature**: This feature will apply **only** to the main "Home" tab (Recommended section).
- **Timestamp parsing**: We will use a date helper to parse the text strings (e.g., "2 days ago", "1 year ago") from the video's subtitle/secondary text into approximate timestamps for sorting.
- **Sparse rows**: It is acceptable if some rows end up with fewer videos after filtering, so no aggressive fetching is necessary.

## Proposed Implementation

### 1. Filtering Logic
**Target File**: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java`
- Modify the `filterHomeIfNeeded(List<MediaGroup> mediaGroups)` method.
- Iterate through each `MediaGroup`'s media items.
- For each item, verify its watch progress against a 5% threshold:
  - **Check Local State**: Query `VideoStateService.instance(getContext()).getByVideoId(item.getVideoId())`. If the state is not null and the computed `percentWatched` is > 5%, remove the item.
  - **Check Remote State**: Check `item.getPercentWatched()`. If it's > 5%, remove the item.

### 2. Timestamp Parsing Utility
**Target File**: `SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/helpers/DateHelper.java` (or an appropriate utility class)
- Implement a method `parseTimeAgo(String timeAgoText)` that converts strings like "2 hours ago" or "1 year ago" into a unix timestamp or a comparable `long` value.

### 3. Sorting Logic
**Target File**: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java`
- After removing the watched items in `filterHomeIfNeeded`, sort the remaining items within each `MediaGroup`.
- Extract the `timeAgo` text (usually available in `item.getSecondTitle()` or a dedicated field) and pass it to the parsing utility.
- Sort the list in descending order (newest first).

## Verification
- Open the "Home" section.
- Confirm that no videos with a red progress bar over 5% appear.
- Confirm that the videos in the grid/rows are sorted from newest to oldest based on their textual timestamps.
