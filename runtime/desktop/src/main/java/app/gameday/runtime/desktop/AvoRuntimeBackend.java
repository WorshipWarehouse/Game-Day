package app.gameday.runtime.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.gameday.runtime.api.Capability;
import app.gameday.runtime.api.CapabilityStatus;
import app.gameday.runtime.api.HostAppInfo;
import app.gameday.runtime.api.ResourceSnapshot;
import app.gameday.runtime.api.RuntimeBackend;
import app.gameday.runtime.api.RuntimeCapability;
import app.gameday.runtime.api.RuntimeDescriptor;
import app.gameday.runtime.api.RuntimeError;
import app.gameday.runtime.api.RuntimeKind;
import app.gameday.runtime.api.RuntimeResult;
import app.gameday.runtime.api.StorageStats;

/**
 * Desktop runtime: a genuine Android virtual machine on Apple silicon (Avo-class engine).
 *
 * <p>This is a real guest — an unmodified AOSP Cuttlefish arm64 image booted by a
 * Hypervisor.framework VMM, with host-GPU composition through gfxstream — not an application
 * virtualization sandbox and not a second launcher.
 *
 * <p>It is deliberately honest about what a virtual machine cannot do. Protected playback is
 * reported {@code UNSUPPORTED}: Widevine L1 requires TEE-bound per-device certification, and a guest
 * VM gets L3 at best. Avo's own specification states the same. Updates are reported as unsupported
 * because the Cuttlefish image carries no app store; GameDay's own verified update feed is the only
 * sanctioned path.
 *
 * <p>All guest interaction goes through {@link Adb}, so the entire backend is unit-testable with a
 * fake channel — and so the verdict about hardware is never confused with the verdict about code.
 */
public final class AvoRuntimeBackend implements RuntimeBackend {

    public static final String ID = "avo-desktop";

    private static final Pattern VERSION_NAME = Pattern.compile("versionName=(\\S+)");
    private static final Pattern VERSION_CODE = Pattern.compile("versionCode=(\\d+)");
    private static final Pattern MIN_SDK = Pattern.compile("minSdk=(\\d+)");
    private static final Pattern TARGET_SDK = Pattern.compile("targetSdk=(\\d+)");
    private static final Pattern TOTAL_PSS = Pattern.compile("TOTAL PSS:\\s*(\\d+)");

    private final HostProbe probe;
    private final Adb adb;
    private final String packageName;
    private final List<Capability> capabilities = new ArrayList<Capability>();
    private HostReadiness.Result readiness;

    public AvoRuntimeBackend(HostProbe probe, Adb adb, String packageName) {
        this.probe = probe;
        this.adb = adb;
        this.packageName = packageName;
    }

    @Override
    public RuntimeDescriptor descriptor() {
        return new RuntimeDescriptor(ID, "Desktop Android VM (Avo-class)", RuntimeKind.SYSTEM_EMULATION,
                "0.1", "Boots an unmodified AOSP arm64 image in a Hypervisor.framework VM and drives "
                        + "it over adb.");
    }

    @Override
    public boolean isAvailable() {
        return readiness().isReady();
    }

    public HostReadiness.Result readiness() {
        if (readiness == null) {
            readiness = HostReadiness.evaluate(probe.probe());
        }
        return readiness;
    }

    @Override
    public RuntimeResult<List<Capability>> initialize() {
        capabilities.clear();
        HostReadiness.Result host = readiness();
        if (!host.isReady()) {
            return RuntimeResult.error(RuntimeError.RUNTIME_UNAVAILABLE,
                    "This host cannot boot the desktop runtime: " + host.describe());
        }

        readiness = host;
        capabilities.add(Capability.supported(RuntimeCapability.APP_INFO,
                "read from the guest over adb (dumpsys package)"));
        capabilities.add(Capability.supported(RuntimeCapability.APP_LAUNCH,
                "launched inside the VM over adb"));
        capabilities.add(Capability.supported(RuntimeCapability.APP_INSTALL,
                "adb install into the guest; a development path, not an authorized store"));
        capabilities.add(Capability.supported(RuntimeCapability.SPLIT_APK_INSTALL,
                "adb install-multiple"));
        capabilities.add(Capability.supported(RuntimeCapability.GUEST_ISOLATION,
                "hardware-isolated VM: the guest has its own kernel"));
        capabilities.add(Capability.supported(RuntimeCapability.PERSISTENT_STORAGE,
                "the guest disk survives restarts, so app data does too"));
        capabilities.add(Capability.supported(RuntimeCapability.RESOURCE_MONITORING,
                "guest memory read over adb"));
        capabilities.add(Capability.supported(RuntimeCapability.PROCESS_MONITORING,
                "guest process presence read over adb"));
        capabilities.add(Capability.supported(RuntimeCapability.FORCE_STOP,
                "am force-stop inside the guest"));
        capabilities.add(Capability.supported(RuntimeCapability.CRASH_DETECTION,
                "guest logcat is readable over adb"));
        capabilities.add(new Capability(RuntimeCapability.HARDWARE_VIDEO_DECODE,
                CapabilityStatus.UNSUPPORTED,
                "the host GPU accelerates composition (gfxstream/Metal); the Cuttlefish guest decodes "
                        + "video in software, so this is not hardware video decoding"));
        capabilities.add(new Capability(RuntimeCapability.DRM_PROTECTED_PLAYBACK,
                CapabilityStatus.UNSUPPORTED,
                "Widevine L1 needs TEE-bound per-device certification; a VM gets L3 at best, and "
                        + "L1-only content will refuse to play"));
        capabilities.add(new Capability(RuntimeCapability.UPDATE_PROVISIONING,
                CapabilityStatus.UNSUPPORTED,
                "the AOSP image has no app store; a guest app cannot update itself the way it does "
                        + "on a certified device"));
        capabilities.add(new Capability(RuntimeCapability.SUSPEND_RESUME,
                CapabilityStatus.UNSUPPORTED,
                "VM suspend/resume is a daemon control-channel operation, not something adb exposes"));
        capabilities.add(new Capability(RuntimeCapability.POINTER_INJECTION,
                CapabilityStatus.UNSUPPORTED,
                "input is translated host-side; GameDay does not synthesize guest input"));
        capabilities.add(new Capability(RuntimeCapability.KEY_INJECTION,
                CapabilityStatus.UNSUPPORTED, "same limitation as pointer injection"));

        return RuntimeResult.ok(Collections.unmodifiableList(new ArrayList<Capability>(capabilities)));
    }

    @Override
    public List<Capability> capabilities() {
        return Collections.unmodifiableList(new ArrayList<Capability>(capabilities));
    }

    @Override
    public CapabilityStatus statusOf(RuntimeCapability capability) {
        for (Capability c : capabilities) {
            if (c.capability() == capability) {
                return c.status();
            }
        }
        return CapabilityStatus.UNKNOWN;
    }

    @Override
    public RuntimeResult<HostAppInfo> hostAppInfo() {
        Adb.Result listing = adb.run("shell", "pm", "list", "packages", packageName);
        if (!listing.ok) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Could not query the guest: " + listing.output.trim());
        }
        if (!listing.output.contains("package:" + packageName)) {
            return RuntimeResult.ok(HostAppInfo.notInstalled(packageName));
        }
        Adb.Result details = adb.run("shell", "dumpsys", "package", packageName);
        String text = details.ok ? details.output : "";
        String versionName = firstGroup(VERSION_NAME, text, "");
        long versionCode = parseLong(firstGroup(VERSION_CODE, text, "-1"), -1L);
        int minSdk = (int) parseLong(firstGroup(MIN_SDK, text, "0"), 0L);
        int targetSdk = (int) parseLong(firstGroup(TARGET_SDK, text, "0"), 0L);
        return RuntimeResult.ok(new HostAppInfo(true, packageName, versionName, versionCode, minSdk,
                targetSdk, Collections.<String>emptyList(), Collections.<String>emptyList(),
                "guest", true, -1L, -1L));
    }

    @Override
    public RuntimeResult<Void> launch() {
        Adb.Result result = adb.run("shell", "monkey", "-p", packageName,
                "-c", "android.intent.category.LAUNCHER", "1");
        String out = result.output;
        if (!result.ok || out.contains("No activities found")) {
            return RuntimeResult.error(RuntimeError.LAUNCH_FAILED,
                    "The guest did not start the app: " + out.trim());
        }
        return RuntimeResult.ok();
    }

    @Override
    public RuntimeResult<Void> stop() {
        Adb.Result result = adb.run("shell", "am", "force-stop", packageName);
        if (!result.ok) {
            return RuntimeResult.error(RuntimeError.INSTALL_FAILED,
                    "Could not stop the guest app: " + result.output.trim());
        }
        return RuntimeResult.ok();
    }

    @Override
    public RuntimeResult<Void> suspend() {
        return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                "Suspending the VM is a control-channel operation; adb cannot do it.");
    }

    @Override
    public RuntimeResult<Void> resume() {
        return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                "Resuming the VM is a control-channel operation; use launch instead.");
    }

    @Override
    public RuntimeResult<Boolean> isHostRunning() {
        Adb.Result result = adb.run("shell", "pidof", packageName);
        if (!result.ok) {
            return RuntimeResult.ok(Boolean.FALSE);
        }
        return RuntimeResult.ok(Boolean.valueOf(!result.output.trim().isEmpty()));
    }

    @Override
    public RuntimeResult<Void> install(List<String> apkPaths) {
        if (apkPaths == null || apkPaths.isEmpty()) {
            return RuntimeResult.error(RuntimeError.INSTALL_FAILED, "No APK paths supplied.");
        }
        Adb.Result result;
        if (apkPaths.size() == 1) {
            result = adb.run("install", "-r", "-d", apkPaths.get(0));
        } else {
            List<String> args = new ArrayList<String>();
            args.add("install-multiple");
            args.add("-r");
            args.add("-d");
            args.addAll(apkPaths);
            result = adb.run(args.toArray(new String[0]));
        }
        if (!result.ok || !result.output.contains("Success")) {
            return RuntimeResult.error(RuntimeError.INSTALL_FAILED,
                    "The guest rejected the package: " + result.output.trim());
        }
        return RuntimeResult.ok();
    }

    @Override
    public RuntimeResult<ResourceSnapshot> resources() {
        Adb.Result result = adb.run("shell", "dumpsys", "meminfo", packageName);
        if (!result.ok) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Could not read guest memory: " + result.output.trim());
        }
        long pssKb = parseLong(firstGroup(TOTAL_PSS, result.output, "-1"), -1L);
        long pssBytes = pssKb < 0 ? -1L : pssKb * 1024L;
        return RuntimeResult.ok(new ResourceSnapshot(System.currentTimeMillis(), pssBytes, -1L, -1L,
                -1L, -1L, -1.0, false));
    }

    @Override
    public RuntimeResult<StorageStats> storage() {
        Adb.Result result = adb.run("shell", "df", "/data");
        if (!result.ok) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Could not read guest storage: " + result.output.trim());
        }
        long free = -1L;
        for (String line : result.output.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 4 && parts[0].startsWith("/dev")) {
                long kb = parseLong(parts[3], -1L);
                if (kb > 0) {
                    free = kb * 1024L;
                }
            }
        }
        return RuntimeResult.ok(new StorageStats(-1L, -1L, -1L, -1L, free));
    }

    @Override
    public void shutdown() {
        // Nothing is held open; the VM is owned by the runtime daemon, not by this object.
    }

    private static String firstGroup(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Package name this backend is configured to host. */
    public String hostPackageName() {
        return packageName;
    }

    /** The commands this backend would run, for diagnostics and tests. */
    public List<String> describeControlPlan() {
        return Arrays.asList(
                "adb shell pm list packages " + packageName,
                "adb shell dumpsys package " + packageName,
                "adb shell monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1",
                "adb shell am force-stop " + packageName,
                "adb shell dumpsys meminfo " + packageName);
    }

    static String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
