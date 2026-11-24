package app.gameday.runtime.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.gameday.runtime.api.CapabilityStatus;
import app.gameday.runtime.api.RuntimeCapability;

public final class DesktopRuntimeTest {

    private static final long GB = 1024L * 1024L * 1024L;

    private static HostFacts mac(int cpus, long ramGb, long diskGb, String adb) {
        return new HostFacts("Mac OS X", "15.1", "aarch64", cpus, ramGb * GB, diskGb * GB, false,
                adb, "/usr/bin/java");
    }

    private static final class FakeProbe implements HostProbe {
        private final HostFacts facts;

        FakeProbe(HostFacts facts) {
            this.facts = facts;
        }

        @Override
        public HostFacts probe() {
            return facts;
        }
    }

    private static final class FakeAdb implements Adb {
        private final Map<String, Result> responses = new HashMap<String, Result>();
        private final List<String> calls = new ArrayList<String>();
        private Result fallback = Result.failed("unexpected command");

        FakeAdb on(String key, Result result) {
            responses.put(key, result);
            return this;
        }

        FakeAdb fallback(Result result) {
            this.fallback = result;
            return this;
        }

        @Override
        public Result run(String... args) {
            String key = join(args);
            calls.add(key);
            Result result = responses.get(key);
            return result == null ? fallback : result;
        }

        private static String join(String[] args) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            return sb.toString();
        }
    }

    private static final String PKG = "com.example.video";

    private static AvoRuntimeBackend readyBackend(FakeAdb adb) {
        return new AvoRuntimeBackend(new FakeProbe(mac(10, 16, 100, "/opt/homebrew/bin/adb")), adb,
                PKG);
    }

    // ---------------------------------------------------------------- readiness

    @Test
    public void linuxHostIsBlockedWithReasons() {
        HostFacts facts = new HostFacts("Linux", "6.1", "x86_64", 2, 4 * GB, 8 * GB, false, null,
                "/usr/bin/java");
        HostReadiness.Result result = HostReadiness.evaluate(facts);
        assertFalse(result.isReady());
        assertEquals(HostReadiness.Verdict.BLOCKED, result.verdict());
        assertTrue(result.describe().contains("macOS"));
        assertTrue(result.describe().contains("arm64"));
        assertTrue(result.describe().contains("adb"));
    }

    @Test
    public void intelMacIsBlocked() {
        HostFacts facts = new HostFacts("Mac OS X", "15.1", "x86_64", 8, 16 * GB, 100 * GB, false,
                "/usr/bin/adb", "/usr/bin/java");
        HostReadiness.Result result = HostReadiness.evaluate(facts);
        assertFalse(result.isReady());
        assertTrue(result.describe().contains("Apple silicon"));
    }

    @Test
    public void lowDiskBlocksEvenOnAppleSilicon() {
        HostReadiness.Result result = HostReadiness.evaluate(mac(10, 16, 4, "/usr/bin/adb"));
        assertFalse(result.isReady());
        assertTrue(result.describe().contains("free disk"));
    }

    @Test
    public void lowMemoryBlocks() {
        HostReadiness.Result result = HostReadiness.evaluate(mac(4, 4, 100, "/usr/bin/adb"));
        assertFalse(result.isReady());
        assertTrue(result.describe().contains("RAM"));
    }

    @Test
    public void appleSiliconWithEverythingIsReady() {
        HostReadiness.Result result = HostReadiness.evaluate(mac(10, 16, 100, "/usr/bin/adb"));
        assertTrue(result.isReady());
        assertEquals(HostReadiness.Verdict.READY, result.verdict());
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    public void readinessAlwaysStatesTheDrmLimit() {
        HostReadiness.Result ready = HostReadiness.evaluate(mac(10, 16, 100, "/usr/bin/adb"));
        StringBuilder notes = new StringBuilder();
        for (String note : ready.notes()) {
            notes.append(note).append(' ');
        }
        String text = notes.toString();
        assertTrue(text.contains("L1"));
        assertTrue(text.contains("L3"));
        // A READY verdict must never read as "protected playback works".
        assertTrue(text.contains("no Google Play services"));
    }

    // ---------------------------------------------------------------- backend refusal

    @Test
    public void initializeRefusesOnThisLinuxHost() {
        HostFacts linux = new HostFacts("Linux", "6.1", "x86_64", 2, 4 * GB, 8 * GB, false, null,
                "/usr/bin/java");
        FakeAdb adb = new FakeAdb();
        AvoRuntimeBackend backend = new AvoRuntimeBackend(new FakeProbe(linux), adb, PKG);
        assertFalse(backend.isAvailable());
        app.gameday.runtime.api.RuntimeResult<?> init = backend.initialize();
        assertFalse(init.isOk());
        assertEquals("RUNTIME_UNAVAILABLE", init.errorCode());
        assertTrue(init.errorMessage().contains("macOS"));
        assertTrue("a blocked backend must not touch the guest", adb.calls.isEmpty());
    }

    // ---------------------------------------------------------------- capabilities

    @Test
    public void readyBackendReportsHonestCapabilities() {
        AvoRuntimeBackend backend = readyBackend(new FakeAdb());
        assertTrue(backend.initialize().isOk());
        assertEquals(CapabilityStatus.SUPPORTED, backend.statusOf(RuntimeCapability.GUEST_ISOLATION));
        assertEquals(CapabilityStatus.SUPPORTED,
                backend.statusOf(RuntimeCapability.PERSISTENT_STORAGE));
        assertEquals(CapabilityStatus.UNSUPPORTED,
                backend.statusOf(RuntimeCapability.DRM_PROTECTED_PLAYBACK));
        assertEquals(CapabilityStatus.UNSUPPORTED,
                backend.statusOf(RuntimeCapability.HARDWARE_VIDEO_DECODE));
        assertEquals(CapabilityStatus.UNSUPPORTED,
                backend.statusOf(RuntimeCapability.UPDATE_PROVISIONING));
        assertEquals(CapabilityStatus.UNSUPPORTED, backend.statusOf(RuntimeCapability.SUSPEND_RESUME));
    }

    @Test
    public void drmReasonNamesTheHardwareCause() {
        AvoRuntimeBackend backend = readyBackend(new FakeAdb());
        backend.initialize();
        String detail = null;
        for (app.gameday.runtime.api.Capability c : backend.capabilities()) {
            if (c.capability() == RuntimeCapability.DRM_PROTECTED_PLAYBACK) {
                detail = c.detail();
            }
        }
        assertNotNull(detail);
        assertTrue(detail.contains("L1"));
        assertTrue(detail.contains("TEE"));
    }

    // ---------------------------------------------------------------- guest control

    @Test
    public void hostAppInfoParsesVersionFromDumpsys() {
        FakeAdb adb = new FakeAdb()
                .on("shell pm list packages " + PKG, Adb.Result.ok("package:" + PKG + "\n"))
                .on("shell dumpsys package " + PKG, Adb.Result.ok(
                        "Packages:\n  Package [" + PKG + "]\n    versionCode=4210 minSdk=23 "
                                + "targetSdk=34\n    versionName=4.2.10\n"));
        AvoRuntimeBackend backend = readyBackend(adb);
        app.gameday.runtime.api.HostAppInfo info = backend.hostAppInfo().value();
        assertNotNull(info);
        assertTrue(info.isInstalled());
        assertEquals("4.2.10", info.versionName());
        assertEquals(4210L, info.versionCode());
        assertEquals(23, info.minSdk());
        assertEquals(34, info.targetSdk());
    }

    @Test
    public void missingPackageIsReportedAsNotInstalled() {
        FakeAdb adb = new FakeAdb().on("shell pm list packages " + PKG,
                Adb.Result.ok("package:com.other\n"));
        AvoRuntimeBackend backend = readyBackend(adb);
        app.gameday.runtime.api.HostAppInfo info = backend.hostAppInfo().value();
        assertNotNull(info);
        assertFalse(info.isInstalled());
    }

    @Test
    public void launchUsesMonkeyInsideTheGuest() {
        FakeAdb adb = new FakeAdb().on(
                "shell monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1",
                Adb.Result.ok("Events injected: 1"));
        AvoRuntimeBackend backend = readyBackend(adb);
        assertTrue(backend.launch().isOk());
        assertTrue(adb.calls.contains(
                "shell monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1"));
    }

    @Test
    public void launchFailureMapsToLaunchFailed() {
        FakeAdb adb = new FakeAdb().on(
                "shell monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1",
                Adb.Result.ok("** No activities found to run, monkey aborted."));
        AvoRuntimeBackend backend = readyBackend(adb);
        app.gameday.runtime.api.RuntimeResult<?> result = backend.launch();
        assertFalse(result.isOk());
        assertEquals("LAUNCH_FAILED", result.errorCode());
    }

    @Test
    public void stopUsesForceStop() {
        FakeAdb adb = new FakeAdb().on("shell am force-stop " + PKG, Adb.Result.ok(""));
        AvoRuntimeBackend backend = readyBackend(adb);
        assertTrue(backend.stop().isOk());
    }

    @Test
    public void runningStateComesFromPidof() {
        FakeAdb running = new FakeAdb().on("shell pidof " + PKG, Adb.Result.ok("1234\n"));
        assertTrue(readyBackend(running).isHostRunning().value().booleanValue());

        FakeAdb stopped = new FakeAdb().on("shell pidof " + PKG, Adb.Result.failed(""));
        assertFalse(readyBackend(stopped).isHostRunning().value().booleanValue());
    }

    @Test
    public void resourcesParseGuestPss() {
        FakeAdb adb = new FakeAdb().on("shell dumpsys meminfo " + PKG,
                Adb.Result.ok("** MEMINFO in pid 1234 [" + PKG + "]**\n"
                        + "                   Pss  Private  Private  SwapPss\n"
                        + "  TOTALS           0      0        0        0\n"
                        + "         TOTAL PSS:   184320            TOTAL RSS:   310000\n"));
        app.gameday.runtime.api.ResourceSnapshot snapshot =
                readyBackend(adb).resources().value();
        assertNotNull(snapshot);
        assertEquals(184320L * 1024L, snapshot.hostPssBytes());
    }

    @Test
    public void storageParsesFreeSpaceFromDf() {
        FakeAdb adb = new FakeAdb().on("shell df /data", Adb.Result.ok(
                "Filesystem     1K-blocks    Used Available Use% Mounted on\n"
                        + "/dev/block/vda  20971520 4194304  16777216  20% /data\n"));
        app.gameday.runtime.api.StorageStats stats = readyBackend(adb).storage().value();
        assertNotNull(stats);
        assertEquals(16777216L * 1024L, stats.freeBytes());
    }

    @Test
    public void installFailureIsReportedNotSwallowed() {
        FakeAdb adb = new FakeAdb().fallback(Adb.Result.failed("Failure [INSTALL_FAILED_TEST_ONLY]"));
        app.gameday.runtime.api.RuntimeResult<?> result =
                readyBackend(adb).install(Collections.singletonList("/tmp/app.apk"));
        assertFalse(result.isOk());
        assertEquals("INSTALL_FAILED", result.errorCode());
        assertTrue(result.errorMessage().contains("INSTALL_FAILED_TEST_ONLY"));
    }
}
