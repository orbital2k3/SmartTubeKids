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
