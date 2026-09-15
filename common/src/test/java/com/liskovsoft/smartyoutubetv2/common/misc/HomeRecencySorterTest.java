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
