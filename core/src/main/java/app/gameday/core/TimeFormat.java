package app.gameday.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** UTC timestamps without java.time, so the same code runs on API 25 and on macOS tooling. */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String isoUtc(long epochMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(epochMillis));
    }

    /** Parses the ISO-8601 subset GameDay emits, returning {@code fallback} on anything else. */
    public static long parseIsoUtc(String text, long fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        String t = text.trim();
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd"
        };
        for (String f : formats) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(f, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                fmt.setLenient(false);
                Date d = fmt.parse(t);
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return fallback;
    }

    public static String duration(long millis) {
        if (millis < 0) {
            return "unknown";
        }
        long s = millis / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%dh %02dm", Long.valueOf(h), Long.valueOf(m));
        }
        if (m > 0) {
            return String.format(Locale.US, "%dm %02ds", Long.valueOf(m), Long.valueOf(sec));
        }
        return String.format(Locale.US, "%ds", Long.valueOf(sec));
    }

    public static String ago(long nowMs, long thenMs) {
        if (thenMs <= 0) {
            return "never";
        }
        return duration(Math.max(0L, nowMs - thenMs)) + " ago";
    }
}
