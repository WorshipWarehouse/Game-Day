package app.gameday.core.update;

/**
 * How often update checks may run, and how failures back off.
 *
 * <p>GameDay checks on start, then at most every six hours while it is active, and on demand when
 * the user asks. Failures never retry faster than the backoff schedule and the circuit opens after
 * a bounded number of consecutive failures, so a missing network or a broken endpoint can never
 * turn into a polling loop on a 1.5 GB device.
 */
public final class UpdatePolicy {

    public static final long DEFAULT_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    public static final long DEFAULT_BASE_BACKOFF_MS = 60L * 1000L;
    public static final long DEFAULT_MAX_BACKOFF_MS = 6L * 60L * 60L * 1000L;
    public static final int DEFAULT_MAX_FAILURES = 5;

    private final long intervalMs;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final int maxFailures;

    public UpdatePolicy() {
        this(DEFAULT_INTERVAL_MS, DEFAULT_BASE_BACKOFF_MS, DEFAULT_MAX_BACKOFF_MS,
                DEFAULT_MAX_FAILURES);
    }

    public UpdatePolicy(long intervalMs, long baseBackoffMs, long maxBackoffMs, int maxFailures) {
        if (intervalMs <= 0 || baseBackoffMs <= 0 || maxBackoffMs < baseBackoffMs) {
            throw new IllegalArgumentException("invalid policy timings");
        }
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be >= 1");
        }
        this.intervalMs = intervalMs;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.maxFailures = maxFailures;
    }

    public long intervalMs() {
        return intervalMs;
    }

    public int maxFailures() {
        return maxFailures;
    }

    /**
     * Whether an automatic check may run now.
     *
     * @param nowMs current time
     * @param lastCheckMs time of the last check attempt (0 when never checked)
     * @param consecutiveFailures failures since the last success
     * @param lastFailureMs time of the last failure (0 when none)
     */
    public boolean isAutomaticCheckDue(long nowMs, long lastCheckMs, int consecutiveFailures,
            long lastFailureMs) {
        if (isCircuitOpen(consecutiveFailures)) {
            return false;
        }
        if (lastCheckMs <= 0) {
            return true;
        }
        if (consecutiveFailures > 0) {
            return nowMs - lastFailureMs >= backoffMs(consecutiveFailures);
        }
        return nowMs - lastCheckMs >= intervalMs;
    }

    /** True when the caller must not retry automatically any more. */
    public boolean isCircuitOpen(int consecutiveFailures) {
        return consecutiveFailures >= maxFailures;
    }

    /** Exponential backoff, capped. Attempt 1 waits the base delay. */
    public long backoffMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return 0L;
        }
        int shift = Math.min(consecutiveFailures - 1, 20);
        long delay = baseBackoffMs << shift;
        if (delay <= 0 || delay > maxBackoffMs) {
            return maxBackoffMs;
        }
        return delay;
    }

    /** Automatic (not user-initiated) retry time after a failure. */
    public long nextAttemptAfterFailure(long nowMs, int consecutiveFailures) {
        return nowMs + backoffMs(consecutiveFailures);
    }

    /** Next routine check time after a success. */
    public long nextCheckAfterSuccess(long nowMs) {
        return nowMs + intervalMs;
    }
}
