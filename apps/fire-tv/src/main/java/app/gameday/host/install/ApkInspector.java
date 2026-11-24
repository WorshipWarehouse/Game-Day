package app.gameday.host.install;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import app.gameday.core.Sha256;
import app.gameday.core.compat.ApkFacts;

/** Extracts the facts GameDay needs from an APK file, before that file is allowed to install. */
public final class ApkInspector {

    private ApkInspector() {
    }

    /** @return facts, or null when the file is not a readable APK. */
    @SuppressWarnings("deprecation")
    public static ApkFacts inspect(Context context, File apk) {
        if (apk == null || !apk.isFile()) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                PackageManager.GET_SIGNATURES | PackageManager.GET_ACTIVITIES);
        if (info == null || info.packageName == null) {
            return null;
        }
        ApplicationInfo ai = info.applicationInfo;

        List<String> splits = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 26 && info.splitNames != null) {
            for (String s : info.splitNames) {
                splits.add(s);
            }
        }

        List<String> features = new ArrayList<String>();
        if (info.reqFeatures != null) {
            for (FeatureInfo f : info.reqFeatures) {
                if (f.name != null) {
                    features.add(f.name);
                }
            }
        }

        return ApkFacts.builder()
                .packageName(info.packageName)
                .versionCode(info.versionCode)
                .versionName(info.versionName)
                .minSdk(ai == null ? 0 : ai.minSdkVersion)
                .targetSdk(ai == null ? 0 : ai.targetSdkVersion)
                .nativeAbis(nativeAbis(apk))
                .signerSha256(signaturesToSha256(info.signatures))
                .splitNames(splits)
                .requiredFeatures(features)
                .sizeBytes(apk.length())
                .launchable(info.activities != null && info.activities.length > 0)
                .label(info.packageName)
                .build();
    }

    /** Signing certificate digests, lowercase hex SHA-256, de-duplicated and ordered. */
    @SuppressWarnings("deprecation")
    public static List<String> signaturesToSha256(Signature[] signatures) {
        Set<String> out = new LinkedHashSet<String>();
        if (signatures != null) {
            for (Signature s : signatures) {
                if (s != null) {
                    out.add(Sha256.of(s.toByteArray()));
                }
            }
        }
        return new ArrayList<String>(out);
    }

    /** ABIs with native libraries, read from the archive contents so no device support is assumed. */
    static List<String> nativeAbis(File apk) {
        Set<String> abis = new LinkedHashSet<String>();
        ZipFile zip = null;
        try {
            zip = new ZipFile(apk);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("lib/")) {
                    continue;
                }
                String[] parts = name.split("/");
                if (parts.length >= 3 && !parts[1].isEmpty()) {
                    abis.add(parts[1].toLowerCase(Locale.US));
                }
            }
        } catch (Exception ignored) {
            // An unreadable archive is reported as "no native code"; verification fails elsewhere.
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return new ArrayList<String>(abis);
    }

    /** Signer digests of an installed package, or an empty list when it is absent. */
    @SuppressWarnings("deprecation")
    public static List<String> installedSigners(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            return signaturesToSha256(info.signatures);
        } catch (PackageManager.NameNotFoundException e) {
            return new ArrayList<String>();
        }
    }

    public static String installedVersionName(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static long installedVersionCode(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1L;
        }
    }
}
