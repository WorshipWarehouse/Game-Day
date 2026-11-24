package app.gameday.core;

import java.util.Locale;

/** Storage arithmetic and formatting shared by install planning and diagnostics. */
public final class StorageBudget {

    /** Reserved headroom so an update can never fill the data partition completely. */
    public static final long MIN_MARGIN_BYTES = 24L * 1024L * 1024L;

    private StorageBudget() {
    }

    public static boolean hasRoomFor(long requiredBytes, long availableBytes) {
        if (requiredBytes <= 0) {
            return true;
        }
        return availableBytes >= requiredBytes;
    }

    /** Bytes that must remain free after an install of {@code apkBytes}. */
    public static long requiredFree(long apkBytes) {
        return Math.max(0L, apkBytes) + MIN_MARGIN_BYTES;
    }

    public static String format(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.0f KB", Double.valueOf(kb));
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.US, "%.1f MB", Double.valueOf(mb));
        }
        return String.format(Locale.US, "%.2f GB", Double.valueOf(mb / 1024.0));
    }
}
