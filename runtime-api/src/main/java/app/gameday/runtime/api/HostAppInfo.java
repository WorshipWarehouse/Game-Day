package app.gameday.runtime.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metadata about the guest application as installed on this device. */
public final class HostAppInfo {

    private final boolean installed;
    private final String packageName;
    private final String versionName;
    private final long versionCode;
    private final int minSdk;
    private final int targetSdk;
    private final List<String> signerSha256;
    private final List<String> splitNames;
    private final String installerPackage;
    private final boolean launchable;
    private final long lastUpdateTimeMs;
    private final long apkBytes;

    public HostAppInfo(boolean installed, String packageName, String versionName, long versionCode,
            int minSdk, int targetSdk, List<String> signerSha256, List<String> splitNames,
            String installerPackage, boolean launchable, long lastUpdateTimeMs, long apkBytes) {
        this.installed = installed;
        this.packageName = packageName == null ? "" : packageName;
        this.versionName = versionName == null ? "" : versionName;
        this.versionCode = versionCode;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
        this.signerSha256 = copy(signerSha256);
        this.splitNames = copy(splitNames);
        this.installerPackage = installerPackage == null ? "" : installerPackage;
        this.launchable = launchable;
        this.lastUpdateTimeMs = lastUpdateTimeMs;
        this.apkBytes = apkBytes;
    }

    private static List<String> copy(List<String> in) {
        if (in == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(in));
    }

    public static HostAppInfo notInstalled(String packageName) {
        return new HostAppInfo(false, packageName, "", -1, 0, 0, null, null, "", false, 0, 0);
    }

    public boolean isInstalled() {
        return installed;
    }

    public String packageName() {
        return packageName;
    }

    public String versionName() {
        return versionName;
    }

    public long versionCode() {
        return versionCode;
    }

    public int minSdk() {
        return minSdk;
    }

    public int targetSdk() {
        return targetSdk;
    }

    public List<String> signerSha256() {
        return signerSha256;
    }

    public List<String> splitNames() {
        return splitNames;
    }

    /** The package that installed the guest, used to tell a store install from a sideload. */
    public String installerPackage() {
        return installerPackage;
    }

    public boolean isLaunchable() {
        return launchable;
    }

    public long lastUpdateTimeMs() {
        return lastUpdateTimeMs;
    }

    public long apkBytes() {
        return apkBytes;
    }
}
