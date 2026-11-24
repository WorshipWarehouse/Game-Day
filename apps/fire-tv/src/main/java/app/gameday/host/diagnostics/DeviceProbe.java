package app.gameday.host.diagnostics;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import app.gameday.core.Json;
import app.gameday.core.StorageBudget;
import app.gameday.core.compat.DeviceFacts;
import app.gameday.core.compat.IsolationAssessment;

/**
 * Reads what this device can actually do. Every value is measured on-device; nothing is assumed
 * from the Android version.
 *
 * <p>Hardware decode is determined from the codec list (and, on Android 10+, from the platform's own
 * {@code isHardwareAccelerated} flag) rather than from a device-name allowlist, and protected
 * playback is determined by asking {@code MediaDrm} about Widevine. A capability GameDay cannot
 * observe is reported as unknown, never as supported.
 */
public final class DeviceProbe {

    public static final String WIDEVINE_UUID = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed";

    private DeviceProbe() {
    }

    public static DeviceFacts facts(Context context) {
        PackageManager pm = context.getPackageManager();
        return DeviceFacts.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .abis(abis())
                .television(isTelevision(context))
                .touchscreen(pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
                .availableStorageBytes(availableStorageBytes(context))
                .display(displayWidth(context), displayHeight(context))
                .hardwareDecode(hardwareDecoderFor("video/avc"), hardwareDecoderFor("video/hevc"))
                .build();
    }

    public static List<String> abis() {
        List<String> out = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                out.add(abi);
            }
        } else if (Build.CPU_ABI != null) {
            out.add(Build.CPU_ABI);
            if (Build.CPU_ABI2 != null && !Build.CPU_ABI2.isEmpty()) {
                out.add(Build.CPU_ABI2);
            }
        }
        return out;
    }

    public static long availableStorageBytes(Context context) {
        try {
            File dir = context.getFilesDir();
            StatFs stat = new StatFs(dir.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    public static long totalStorageBytes() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    public static int displayWidth(Context context) {
        DisplayMetrics m = metrics(context);
        return m == null ? 0 : m.widthPixels;
    }

    public static int displayHeight(Context context) {
        DisplayMetrics m = metrics(context);
        return m == null ? 0 : m.heightPixels;
    }

    private static DisplayMetrics metrics(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return null;
        }
        Display display = wm.getDefaultDisplay();
        DisplayMetrics m = new DisplayMetrics();
        display.getRealMetrics(m);
        return m;
    }

    public static float refreshRate(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return 0f;
        }
        return wm.getDefaultDisplay().getRefreshRate();
    }

    public static boolean isTelevision(Context context) {
        UiModeManager ui = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /** True when a non-software decoder exists for the MIME type. */
    public static boolean hardwareDecoderFor(String mimeType) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                boolean typeMatch = false;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mimeType)) {
                        typeMatch = true;
                        break;
                    }
                }
                if (!typeMatch) {
                    continue;
                }
                if (isHardwareCodec(info)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // A device with a broken codec service is reported as "no hardware decoder".
        }
        return false;
    }

    /** Names of decoders for a MIME type, for the diagnostics report. */
    public static List<String> decodersFor(String mimeType) {
        List<String> out = new ArrayList<String>();
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mimeType)) {
                        out.add(info.getName() + (isHardwareCodec(info) ? " (hw)" : " (sw)"));
                        break;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // reported as an empty list
        }
        return out;
    }

    /**
     * Android 10+ exposes a vendor-provided flag; older releases are judged by codec naming, which
     * is the convention every shipping Android device follows (OMX.google / c2.android are the
     * software fallbacks).
     */
    @SuppressWarnings("deprecation")
    private static boolean isHardwareCodec(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return info.isHardwareAccelerated() && !info.isSoftwareOnly();
            } catch (RuntimeException ignored) {
                // fall through to naming heuristics
            }
        }
        String name = info.getName().toLowerCase(Locale.US);
        if (name.startsWith("omx.google.") || name.startsWith("c2.android.")
                || name.contains(".sw.") || name.contains("software")) {
            return false;
        }
        return true;
    }

    /** Widevine presence, which is what protected NFL playback depends on. */
    public static boolean widevineSupported() {
        try {
            return MediaDrm.isCryptoSchemeSupported(UUID.fromString(WIDEVINE_UUID));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether this device can render into a secure surface (HDCP path). */
    public static boolean secureVideoSupported() {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (!"video/avc".equalsIgnoreCase(type) && !"video/hevc".equalsIgnoreCase(type)) {
                        continue;
                    }
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    if (caps != null && isSecureDecoder(info, caps)) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Unknown is reported as false; the report states the method used.
        }
        return false;
    }

    /** Secure (HDCP-capable) decoder: the documented feature flag, or the vendor name convention. */
    private static boolean isSecureDecoder(MediaCodecInfo info,
            MediaCodecInfo.CodecCapabilities caps) {
        try {
            if (caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // fall through to the naming convention
        }
        return info.getName().toLowerCase(Locale.US).contains(".secure");
    }

    public static Set<String> deviceFeatures(Context context) {
        Set<String> out = new LinkedHashSet<String>();
        PackageManager pm = context.getPackageManager();
        FeatureInfo[] features = pm.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo f : features) {
                if (f.name != null) {
                    out.add(f.name);
                }
            }
        }
        return out;
    }

    /** Fire OS release name, e.g. "Fire OS 6.2.8.1", read from the build property. */
    public static String fireOsVersion() {
        String name = systemProperty("ro.build.version.name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String release = systemProperty("ro.build.version.release");
        return release == null ? Build.VERSION.RELEASE : release;
    }

    public static String systemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String installerPackage(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.content.pm.InstallSourceInfo info = pm.getInstallSourceInfo(pkg);
                return info.getInstallingPackageName();
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return pm.getInstallerPackageName(pkg);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Facts for the one rootless isolation candidate that is not virtualization: a managed profile
     * (work profile). This is measured, never assumed - Fire OS may omit both the feature and the
     * provisioning flow. See docs/decisions/0002-isolation-feasibility.md.
     */
    public static IsolationAssessment.Facts isolationFacts(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean managedUsers = false;
        try {
            managedUsers = pm.hasSystemFeature("android.software.managed_users");
        } catch (RuntimeException ignored) {
            // reported as absent
        }

        boolean provisioningResolves = false;
        try {
            Intent provision = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            provision.addCategory(Intent.CATEGORY_DEFAULT);
            provision.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    new ComponentName(context, "app.gameday.host.admin.GameDayDeviceAdmin"));
            provisioningResolves = pm.resolveActivity(provision, PackageManager.MATCH_DEFAULT_ONLY)
                    != null;
        } catch (RuntimeException ignored) {
            // reported as absent
        }

        boolean profileOwner = false;
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context
                    .getSystemService(Context.DEVICE_POLICY_SERVICE);
            profileOwner = dpm != null && dpm.isProfileOwnerApp(context.getPackageName());
        } catch (RuntimeException ignored) {
            // reported as absent
        }

        return new IsolationAssessment.Facts(Build.VERSION.SDK_INT, managedUsers,
                provisioningResolves, profileOwner);
    }

    /** Immutable snapshot used by the UI; cheap to build and safe to cache per process. */
    public static final class DeviceSummary {
        public final String model;
        public final String manufacturer;
        public final String fireOsVersion;
        public final int sdkInt;
        public final String abis;
        public final boolean television;
        public final int widthPx;
        public final int heightPx;
        public final float refreshHz;
        public final long totalStorageBytes;
        public final long availableStorageBytes;
        public final long totalMemoryBytes;
        public final String gpuRenderer;
        public final boolean hardwareAvc;
        public final boolean hardwareHevc;
        public final boolean widevine;
        public final boolean secureVideo;

        DeviceSummary(String model, String manufacturer, String fireOsVersion, int sdkInt,
                String abis, boolean television, int widthPx, int heightPx, float refreshHz,
                long totalStorageBytes, long availableStorageBytes, long totalMemoryBytes,
                String gpuRenderer, boolean hardwareAvc, boolean hardwareHevc, boolean widevine,
                boolean secureVideo) {
            this.model = model;
            this.manufacturer = manufacturer;
            this.fireOsVersion = fireOsVersion;
            this.sdkInt = sdkInt;
            this.abis = abis;
            this.television = television;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.refreshHz = refreshHz;
            this.totalStorageBytes = totalStorageBytes;
            this.availableStorageBytes = availableStorageBytes;
            this.totalMemoryBytes = totalMemoryBytes;
            this.gpuRenderer = gpuRenderer;
            this.hardwareAvc = hardwareAvc;
            this.hardwareHevc = hardwareHevc;
            this.widevine = widevine;
            this.secureVideo = secureVideo;
        }

        public Json toJson() {
            return Json.object()
                    .put("model", model)
                    .put("manufacturer", manufacturer)
                    .put("osVersion", fireOsVersion)
                    .put("sdkInt", sdkInt)
                    .put("abis", abis)
                    .put("television", television)
                    .put("displayWidthPx", widthPx)
                    .put("displayHeightPx", heightPx)
                    .put("refreshHz", (long) refreshHz)
                    .put("totalStorage", StorageBudget.format(totalStorageBytes))
                    .put("availableStorage", StorageBudget.format(availableStorageBytes))
                    .put("totalMemory", StorageBudget.format(totalMemoryBytes))
                    .put("gpuRenderer", gpuRenderer)
                    .put("hardwareDecodeAvc", hardwareAvc)
                    .put("hardwareDecodeHevc", hardwareHevc)
                    .put("widevine", widevine)
                    .put("secureVideo", secureVideo);
        }

        public String displaySummary() {
            return widthPx + "x" + heightPx + " @ " + Math.round(refreshHz) + "Hz";
        }
    }

    public static DeviceSummary summary(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        long totalMem = -1L;
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            totalMem = info.totalMem;
        }
        return new DeviceSummary(
                Build.MODEL,
                Build.MANUFACTURER,
                fireOsVersion(),
                Build.VERSION.SDK_INT,
                joinAbis(),
                isTelevision(context),
                displayWidth(context),
                displayHeight(context),
                refreshRate(context),
                totalStorageBytes(),
                availableStorageBytes(context),
                totalMem,
                systemProperty("ro.hardware.egl"),
                hardwareDecoderFor("video/avc"),
                hardwareDecoderFor("video/hevc"),
                widevineSupported(),
                secureVideoSupported());
    }

    private static String joinAbis() {
        StringBuilder sb = new StringBuilder();
        List<String> abis = abis();
        for (int i = 0; i < abis.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(abis.get(i));
        }
        return sb.toString();
    }
}
