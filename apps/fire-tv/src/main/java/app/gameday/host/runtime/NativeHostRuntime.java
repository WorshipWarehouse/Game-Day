package app.gameday.host.runtime;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.gameday.core.Sha256;
import app.gameday.host.BuildConfig;
import app.gameday.host.diagnostics.DeviceProbe;
import app.gameday.host.install.ApkInspector;
import app.gameday.host.install.ApkVerifier;
import app.gameday.host.install.PackageInstallerHelper;
import app.gameday.runtime.api.Capability;
import app.gameday.runtime.api.CapabilityStatus;
import app.gameday.runtime.api.HostAppInfo;
import app.gameday.runtime.api.HostAppSpec;
import app.gameday.runtime.api.ResourceSnapshot;
import app.gameday.runtime.api.RuntimeBackend;
import app.gameday.runtime.api.RuntimeCapability;
import app.gameday.runtime.api.RuntimeDescriptor;
import app.gameday.runtime.api.RuntimeError;
import app.gameday.runtime.api.RuntimeKind;
import app.gameday.runtime.api.RuntimeResult;
import app.gameday.runtime.api.StorageStats;

/**
 * Hosts the official NFL application natively on Fire OS.
 *
 * <p>This backend does not run the guest inside GameDay's process. It launches the Amazon Appstore
 * build in place, which is the only arrangement on Fire OS 6 that keeps the guest's hardware video
 * decoder, secure surfaces, Widevine and HDCP on their normal platform path - the things protected
 * NFL playback actually depends on.
 *
 * <p>Capabilities that cannot be provided without root or special access are reported as
 * unsupported, with the reason shown in Diagnostics.
 */
public final class NativeHostRuntime implements RuntimeBackend {

    public static final String ID = "native-host";

    private final Context app;
    private final HostAppSpec spec;
    private final List<Capability> capabilities = new ArrayList<Capability>();

    private long lastCpuTicks = -1L;
    private long lastCpuSampleMs = -1L;
    private double lastCpuPercent;

    public NativeHostRuntime(Context context) {
        this.app = context.getApplicationContext();
        this.spec = new HostAppSpec(BuildConfig.HOST_PACKAGE, "NFL", null, 0L, true);
    }

    public HostAppSpec hostSpec() {
        return spec;
    }

    @Override
    public RuntimeDescriptor descriptor() {
        return new RuntimeDescriptor(ID, "Fire OS native host", RuntimeKind.NATIVE_HOST, "1.0",
                "Launches the Appstore build of the NFL app natively, keeping its decoder, DRM and "
                        + "sign-in intact.");
    }

    @Override
    public boolean isAvailable() {
        // PackageManager, started activities and MediaDrm exist on every Android this build supports.
        return Build.VERSION.SDK_INT >= 21;
    }

    @Override
    public RuntimeResult<List<Capability>> initialize() {
        capabilities.clear();
        HostAppInfo installedInfo = hostAppInfo().value();
        boolean installed = installedInfo != null && installedInfo.isInstalled();

        capabilities.add(Capability.supported(RuntimeCapability.APP_INFO,
                "PackageManager metadata, including signer digests"));
        capabilities.add(installed
                ? Capability.supported(RuntimeCapability.APP_LAUNCH, "leanback launch intent")
                : new Capability(RuntimeCapability.APP_LAUNCH, CapabilityStatus.REQUIRES_USER_ACTION,
                        "The NFL app is not installed; get it from the Amazon Appstore"));

        capabilities.add(canRequestInstall()
                ? Capability.supported(RuntimeCapability.APP_INSTALL,
                        "PackageInstaller session with user confirmation")
                : new Capability(RuntimeCapability.APP_INSTALL, CapabilityStatus.REQUIRES_PERMISSION,
                        "Allow GameDay to install unknown apps"));
        capabilities.add(Capability.supported(RuntimeCapability.SPLIT_APK_INSTALL,
                Build.VERSION.SDK_INT >= 26
                        ? "multi-APK PackageInstaller sessions"
                        : "single APK only: split metadata is not readable before Android 8"));
        capabilities.add(new Capability(RuntimeCapability.APP_UNINSTALL,
                CapabilityStatus.REQUIRES_USER_ACTION,
                "Android removes applications only with explicit user confirmation"));

        capabilities.add(hasUsageAccess()
                ? Capability.supported(RuntimeCapability.PROCESS_MONITORING,
                        "usage-event foreground tracking")
                : new Capability(RuntimeCapability.PROCESS_MONITORING,
                        CapabilityStatus.REQUIRES_PERMISSION,
                        "Grant usage access for foreground detection"));
        capabilities.add(Capability.supported(RuntimeCapability.FORCE_STOP,
                "best effort: killBackgroundProcesses cannot stop a foreground app"));
        capabilities.add(Capability.supported(RuntimeCapability.GUEST_ISOLATION,
                "the guest keeps its own sandbox, UID and signature"));
        capabilities.add(Capability.supported(RuntimeCapability.PERSISTENT_STORAGE,
                "the guest owns its data; GameDay never moves or clears it"));
        capabilities.add(Capability.supported(RuntimeCapability.RESOURCE_MONITORING,
                "own-process memory and CPU, system memory pressure"));
        capabilities.add(new Capability(RuntimeCapability.POINTER_INJECTION,
                CapabilityStatus.UNSUPPORTED,
                "needs an accessibility service or an overlay the guest accepts; not provided"));
        capabilities.add(new Capability(RuntimeCapability.KEY_INJECTION,
                CapabilityStatus.UNSUPPORTED,
                "same limitation as pointer injection"));
        capabilities.add(new Capability(RuntimeCapability.SUSPEND_RESUME,
                CapabilityStatus.UNSUPPORTED,
                "an externally launched app owns its own lifecycle"));

        boolean hwDecode = DeviceProbe.hardwareDecoderFor("video/avc")
                || DeviceProbe.hardwareDecoderFor("video/hevc");
        capabilities.add(hwDecode
                ? Capability.supported(RuntimeCapability.HARDWARE_VIDEO_DECODE,
                        "platform decoders: " + DeviceProbe.decodersFor("video/hevc"))
                : new Capability(RuntimeCapability.HARDWARE_VIDEO_DECODE,
                        CapabilityStatus.UNSUPPORTED, "no non-software AVC/HEVC decoder found"));
        capabilities.add(DeviceProbe.widevineSupported()
                ? Capability.supported(RuntimeCapability.DRM_PROTECTED_PLAYBACK,
                        "Widevine present; secure decoder reported: " + DeviceProbe.secureVideoSupported())
                : new Capability(RuntimeCapability.DRM_PROTECTED_PLAYBACK,
                        CapabilityStatus.UNSUPPORTED, "MediaDrm reports no Widevine support"));
        capabilities.add(hasUsageAccess()
                ? Capability.supported(RuntimeCapability.CRASH_DETECTION,
                        "coarse: guest left the foreground without returning")
                : new Capability(RuntimeCapability.CRASH_DETECTION,
                        CapabilityStatus.REQUIRES_PERMISSION,
                        "usage access enables coarse guest exit detection"));

        boolean store = AppStore.isInstalled(app);
        capabilities.add(store
                ? Capability.supported(RuntimeCapability.UPDATE_PROVISIONING,
                        "delegates to the Amazon Appstore update path")
                : new Capability(RuntimeCapability.UPDATE_PROVISIONING,
                        CapabilityStatus.REQUIRES_USER_ACTION,
                        "Amazon Appstore not found on this device"));

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
        PackageManager pm = app.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(spec.packageName(), PackageManager.GET_SIGNATURES);
            if (info == null || info.applicationInfo == null) {
                return RuntimeResult.ok(HostAppInfo.notInstalled(spec.packageName()));
            }
            ApplicationInfo ai = info.applicationInfo;
            List<String> signers = ApkInspector.signaturesToSha256(info.signatures);
            List<String> splits = new ArrayList<String>();
            if (Build.VERSION.SDK_INT >= 26 && info.splitNames != null) {
                splits.addAll(Arrays.asList(info.splitNames));
            }
            long apkBytes = new File(ai.sourceDir).length();
            if (ai.splitSourceDirs != null) {
                for (String path : ai.splitSourceDirs) {
                    apkBytes += new File(path).length();
                }
            }
            boolean launchable = pm.getLeanbackLaunchIntentForPackage(spec.packageName()) != null
                    || pm.getLaunchIntentForPackage(spec.packageName()) != null;
            return RuntimeResult.ok(new HostAppInfo(true, info.packageName, info.versionName,
                    info.versionCode, info.applicationInfo.minSdkVersion,
                    info.applicationInfo.targetSdkVersion, signers, splits,
                    DeviceProbe.installerPackage(app, spec.packageName()), launchable,
                    info.lastUpdateTime, apkBytes));
        } catch (PackageManager.NameNotFoundException e) {
            return RuntimeResult.ok(HostAppInfo.notInstalled(spec.packageName()));
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Could not read package metadata: " + e.getMessage());
        }
    }

    @Override
    public RuntimeResult<Void> launch() {
        PackageManager pm = app.getPackageManager();
        if (!isHostInstalled()) {
            return RuntimeResult.error(RuntimeError.HOST_NOT_INSTALLED,
                    "The NFL app is not installed. Get it from the Amazon Appstore first.");
        }
        Intent intent = pm.getLeanbackLaunchIntentForPackage(spec.packageName());
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(spec.packageName());
        }
        if (intent == null) {
            return RuntimeResult.error(RuntimeError.HOST_NOT_LAUNCHABLE,
                    "The installed NFL app exposes no launchable activity for this device.");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            app.startActivity(intent);
            return RuntimeResult.ok();
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.LAUNCH_FAILED,
                    "Could not start the NFL app: " + e.getMessage());
        }
    }

    private boolean isHostInstalled() {
        try {
            app.getPackageManager().getPackageInfo(spec.packageName(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public RuntimeResult<Void> stop() {
        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                        "ActivityManager unavailable");
            }
            am.killBackgroundProcesses(spec.packageName());
            return RuntimeResult.ok();
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                    "Could not stop the guest: " + e.getMessage());
        }
    }

    @Override
    public RuntimeResult<Void> suspend() {
        return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                "An externally launched app cannot be suspended by GameDay without root.");
    }

    @Override
    public RuntimeResult<Void> resume() {
        return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                "Resuming is the guest app's own responsibility; use launch instead.");
    }

    @Override
    public RuntimeResult<Boolean> isHostRunning() {
        if (!hasUsageAccess()) {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                    "Usage access is not granted, so foreground state cannot be observed.");
        }
        try {
            String foreground = lastForegroundPackage();
            return RuntimeResult.ok(Boolean.valueOf(spec.packageName().equals(foreground)));
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION,
                    "Usage events unavailable: " + e.getMessage());
        }
    }

    @Override
    public RuntimeResult<Void> install(List<String> apkPaths) {
        if (apkPaths == null || apkPaths.isEmpty()) {
            return RuntimeResult.error(RuntimeError.INSTALL_FAILED, "No APK paths supplied.");
        }
        List<File> files = new ArrayList<File>();
        for (String path : apkPaths) {
            File f = new File(path);
            if (!f.isFile() || !f.canRead()) {
                return RuntimeResult.error(RuntimeError.INSTALL_FAILED,
                        "APK is missing or unreadable: " + path);
            }
            files.add(f);
        }
        HostAppInfo installed = hostAppInfo().value();
        long installedCode = installed != null && installed.isInstalled()
                ? installed.versionCode() : -1L;
        ApkVerifier verifier = new ApkVerifier(app);
        ApkVerifier.Result verification = verifier.verifyForInstall(files, spec.packageName(),
                null, installedCode, spec.requireTelevision());
        if (!verification.ok()) {
            return RuntimeResult.error(RuntimeError.VERIFICATION_FAILED, verification.message());
        }
        PackageInstallerHelper.Result result =
                PackageInstallerHelper.install(app, files, installedCode >= 0);
        if (!result.ok()) {
            return RuntimeResult.error(result.permissionNeeded()
                    ? RuntimeError.VERIFICATION_FAILED : RuntimeError.INSTALL_FAILED,
                    result.message());
        }
        return RuntimeResult.ok();
    }

    @Override
    public RuntimeResult<ResourceSnapshot> resources() {
        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            long pss = -1L;
            long javaHeap = -1L;
            long nativeHeap = -1L;
            boolean lowMemory = false;
            long available = -1L;
            long total = -1L;
            if (am != null) {
                Debug.MemoryInfo[] infos = am.getProcessMemoryInfo(new int[]{Process.myPid()});
                if (infos != null && infos.length > 0) {
                    pss = infos[0].getTotalPss() * 1024L;
                    javaHeap = infos[0].dalvikPss * 1024L;
                    nativeHeap = infos[0].nativePss * 1024L;
                }
                ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mem);
                available = mem.availMem;
                total = mem.totalMem;
                lowMemory = mem.lowMemory;
            }
            double cpu = sampleCpuPercent();
            return RuntimeResult.ok(new ResourceSnapshot(System.currentTimeMillis(), pss,
                    javaHeap, nativeHeap, available, total, cpu, lowMemory));
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Resource sampling failed: " + e.getMessage());
        }
    }

    @Override
    public RuntimeResult<StorageStats> storage() {
        try {
            StatFs stat = new StatFs(app.getFilesDir().getAbsolutePath());
            long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long data = directorySize(app.getFilesDir());
            long cache = directorySize(app.getCacheDir());
            HostAppInfo guest = hostAppInfo().value();
            long guestApk = guest != null ? guest.apkBytes() : 0L;
            return RuntimeResult.ok(new StorageStats(data, cache, guestApk, -1L, free));
        } catch (RuntimeException e) {
            return RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Storage sampling failed: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // Nothing is held open: the guest is a separate process owned by the platform.
    }

    // ---------------------------------------------------------------- helpers

    private boolean canRequestInstall() {
        if (Build.VERSION.SDK_INT < 26) {
            return true; // the user enables "unknown sources" globally
        }
        try {
            return app.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean hasUsageAccess() {
        if (Build.VERSION.SDK_INT < 21) {
            return false;
        }
        try {
            AppOpsManager ops = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                    app.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String lastForegroundPackage() {
        UsageStatsManager usm =
                (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 30L * 60L * 1000L, now);
        if (events == null) {
            return null;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        String last = null;
        long lastTs = 0L;
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) {
                break;
            }
            int type = event.getEventType();
            boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (Build.VERSION.SDK_INT >= 29
                            && type == UsageEvents.Event.ACTIVITY_RESUMED);
            if (foreground && event.getTimeStamp() >= lastTs) {
                last = event.getPackageName();
                lastTs = event.getTimeStamp();
            }
        }
        return last;
    }

    /** Reads CPU time from /proc/self/stat: field 14 (utime) + 15 (stime), in clock ticks. */
    private double sampleCpuPercent() {
        long ticks = readSelfCpuTicks();
        long now = System.currentTimeMillis();
        if (ticks < 0) {
            return -1;
        }
        if (lastCpuTicks >= 0 && lastCpuSampleMs > 0 && now > lastCpuSampleMs) {
            long deltaTicks = ticks - lastCpuTicks;
            long deltaMs = now - lastCpuSampleMs;
            // 100 ticks per second on Android.
            lastCpuPercent = (deltaTicks * 10.0 / deltaMs) * 100.0;
        }
        lastCpuTicks = ticks;
        lastCpuSampleMs = now;
        return lastCpuPercent;
    }

    private static long readSelfCpuTicks() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/self/stat"));
            String line = reader.readLine();
            if (line == null) {
                return -1;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 15) {
                return -1;
            }
            return Long.parseLong(parts[13]) + Long.parseLong(parts[14]);
        } catch (Exception e) {
            return -1;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static long directorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children == null) {
            return dir.length();
        }
        for (File child : children) {
            total += child.isDirectory() ? directorySize(child) : child.length();
        }
        return total;
    }

    /** Amazon Appstore deep links, used to delegate installs and updates to the store. */
    public static final class AppStore {

        private AppStore() {
        }

        public static boolean isInstalled(Context context) {
            try {
                context.getPackageManager().getPackageInfo(BuildConfig.AMAZON_APPSTORE_PACKAGE, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        public static boolean openHostPage(Context context) {
            return open(context, "amzn://apps/android?p=" + BuildConfig.HOST_PACKAGE);
        }

        public static boolean openHostUpdatePage(Context context) {
            return open(context,
                    "amzn://apps/android?p=" + BuildConfig.HOST_PACKAGE + "&intent=app_update");
        }

        private static boolean open(Context context, String uri) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (RuntimeException e) {
                // Fall back to the public Appstore web page, which also drives a Fire TV install.
                try {
                    Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                            "https://www.amazon.com/gp/mas/dl/android?p=" + BuildConfig.HOST_PACKAGE));
                    web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(web);
                    return true;
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
        }
    }

    /** Convenience for diagnostics: the digest GameDay currently trusts for the guest. */
    public static String signerDigest(byte[] certificate) {
        return Sha256.of(certificate);
    }
}
