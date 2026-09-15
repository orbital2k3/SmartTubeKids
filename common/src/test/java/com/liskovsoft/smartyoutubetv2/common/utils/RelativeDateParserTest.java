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
