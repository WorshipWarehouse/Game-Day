package app.gameday.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects repeated startup failures so GameDay can fall back to a safe startup mode instead of
 * restarting the runtime forever.
 *
 * <p>Every entry into the launcher records a boot timestamp. A healthy exit clears the record. If
 * {@code threshold} boots happen inside {@code windowMs} without a healthy exit in between, the
 * next start is a safe-mode start: the runtime is not auto-launched, no updates are installed, and
 * the user is offered recovery actions.
 */
public final class CrashGuard {

    public static final String KEY_BOOTS = "crashguard.boots";

    private static final int MAX_RECORDS = 8;

    private final KeyValueStore store;
    private final int threshold;
    private final long windowMs;

    public CrashGuard(KeyValueStore store) {
        this(store, 3, 5 * 60 * 1000L);
    }

    public CrashGuard(KeyValueStore store, int threshold, long windowMs) {
        if (store == null) {
            throw new IllegalArgumentException("store == null");
        }
        if (threshold < 2) {
            throw new IllegalArgumentException("threshold must be >= 2");
        }
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be > 0");
        }
        this.store = store;
        this.threshold = threshold;
        this.windowMs = windowMs;
    }

    public int threshold() {
        return threshold;
    }

    public long windowMs() {
        return windowMs;
    }

    /** Records a startup. Call once per process start, before any runtime launch. */
    public void recordBoot(long nowMs) {
        List<Long> boots = read();
        boots.add(Long.valueOf(nowMs));
        while (boots.size() > MAX_RECORDS) {
            boots.remove(0);
        }
        write(boots);
    }

    /** Number of recorded boots still inside the failure window. */
    public int recentBoots(long nowMs) {
        int count = 0;
        for (Long t : read()) {
            if (nowMs - t.longValue() <= windowMs && nowMs >= t.longValue()) {
                count++;
            }
        }
        return count;
    }

    /** True when the previous start pattern looks like a crash loop. */
    public boolean shouldEnterSafeMode(long nowMs) {
        return recentBoots(nowMs) >= threshold;
    }

    /** Marks the session as healthy: clears the loop counter. */
    public void recordHealthyExit(long nowMs) {
        store.remove(KEY_BOOTS);
        store.commit();
    }

    public void reset() {
        store.remove(KEY_BOOTS);
        store.commit();
    }

    private List<Long> read() {
        List<Long> out = new ArrayList<Long>();
        String raw = store.getString(KEY_BOOTS, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.valueOf(Long.parseLong(s)));
            } catch (NumberFormatException ignored) {
                // Corrupt entries are dropped rather than failing the whole guard.
            }
        }
        return out;
    }

    private void write(List<Long> boots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < boots.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(boots.get(i).longValue());
        }
        store.putString(KEY_BOOTS, sb.toString());
        store.commit();
    }
}
