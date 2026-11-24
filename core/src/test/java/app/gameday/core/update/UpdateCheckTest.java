package app.gameday.core.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateCheckTest {

    private static final ReleaseChannel STABLE = ReleaseChannel.STABLE;

    private static UpdateManifest manifest(ReleaseChannel channel, long versionCode, int minSdk) {
        return new UpdateManifest("app.gameday.host", channel, "1.2.0", versionCode, minSdk,
                "https://github.com/x/y.apk", UpdateManifestParserTest.SHA, 1000L,
                "2026-11-24T00:00:00Z", "");
    }

    @Test
    public void newerCompatibleReleaseIsAvailable() {
        UpdateCheck.Result r =
                UpdateCheck.evaluate(manifest(STABLE, 200, 25), 100, 25, STABLE);
        assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, r.status());
        assertTrue(r.isUpdateAvailable());
    }

    @Test
    public void sameVersionIsUpToDate() {
        assertEquals(UpdateCheck.Status.UP_TO_DATE,
                UpdateCheck.evaluate(manifest(STABLE, 100, 25), 100, 25, STABLE).status());
    }

    @Test
    public void olderReleaseIsNotOffered() {
        assertEquals(UpdateCheck.Status.UP_TO_DATE,
                UpdateCheck.evaluate(manifest(STABLE, 90, 25), 100, 25, STABLE).status());
    }

    @Test
    public void releaseRequiringNewerAndroidIsRejected() {
        UpdateCheck.Result r =
                UpdateCheck.evaluate(manifest(STABLE, 200, 29), 100, 25, STABLE);
        assertEquals(UpdateCheck.Status.INCOMPATIBLE_DEVICE, r.status());
        assertTrue(r.reason().contains("API 29"));
    }

    @Test
    public void betaReleaseHiddenFromStableChannel() {
        assertEquals(UpdateCheck.Status.CHANNEL_NOT_ACCEPTED,
                UpdateCheck.evaluate(manifest(ReleaseChannel.BETA, 200, 25), 100, 25, STABLE)
                        .status());
        assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE,
                UpdateCheck.evaluate(manifest(ReleaseChannel.BETA, 200, 25), 100, 25,
                        ReleaseChannel.BETA).status());
    }

    @Test
    public void stableReleaseVisibleToBetaChannel() {
        assertTrue(ReleaseChannel.BETA.accepts(ReleaseChannel.STABLE));
        assertFalse(ReleaseChannel.STABLE.accepts(ReleaseChannel.BETA));
    }

    @Test
    public void missingManifestIsInvalid() {
        assertEquals(UpdateCheck.Status.INVALID, UpdateCheck.evaluate(null, 1, 25, STABLE).status());
    }

    @Test
    public void automaticChecksRespectIntervalAndBackoff() {
        UpdatePolicy policy = new UpdatePolicy(6 * 3600_000L, 60_000L, 3600_000L, 5);
        assertTrue("never checked before", policy.isAutomaticCheckDue(0L, 0L, 0, 0L));
        long now = 10_000_000L;
        assertFalse(policy.isAutomaticCheckDue(now, now - 1000L, 0, 0L));
        assertTrue(policy.isAutomaticCheckDue(now, now - 6 * 3600_000L, 0, 0L));
    }

    @Test
    public void failuresBackOffExponentiallyAndOpenTheCircuit() {
        UpdatePolicy policy = new UpdatePolicy(6 * 3600_000L, 60_000L, 600_000L, 4);
        assertEquals(60_000L, policy.backoffMs(1));
        assertEquals(120_000L, policy.backoffMs(2));
        assertEquals(240_000L, policy.backoffMs(3));
        assertEquals(600_000L, policy.backoffMs(10));
        assertFalse(policy.isCircuitOpen(3));
        assertTrue(policy.isCircuitOpen(4));
        assertFalse(policy.isAutomaticCheckDue(1_000_000L, 0L, 4, 1_000_000L));
    }

    @Test
    public void failureBackoffGatesRetries() {
        UpdatePolicy policy = new UpdatePolicy(6 * 3600_000L, 60_000L, 600_000L, 5);
        long now = 1_000_000L;
        assertFalse(policy.isAutomaticCheckDue(now, now, 1, now - 30_000L));
        assertTrue(policy.isAutomaticCheckDue(now, now, 1, now - 60_000L));
    }
}
