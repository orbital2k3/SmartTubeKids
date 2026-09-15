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
