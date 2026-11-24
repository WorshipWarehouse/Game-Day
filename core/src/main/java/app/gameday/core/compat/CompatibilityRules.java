package app.gameday.core.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.gameday.core.StorageBudget;
import app.gameday.core.VersionCode;

/**
 * The single gate every APK passes before GameDay installs or launches it.
 *
 * <p>Rules are intentionally conservative: anything that cannot be verified on the target device is
 * a blocker rather than an optimistic pass. The same rules run for GameDay's own updates, for a
 * sideloaded host APK, and for the compatibility test application, so behaviour cannot drift between
 * them.
 */
public final class CompatibilityRules {

    private CompatibilityRules() {
    }

    public static CompatibilityReport evaluate(ApkFacts apk, DeviceFacts device,
            InstallExpectations expectations) {
        List<CompatibilityIssue> issues = new ArrayList<CompatibilityIssue>();
        if (apk == null) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.EMPTY_APK_SET,
                    IssueSeverity.BLOCKER, "No package metadata was available."));
            return new CompatibilityReport("", -1, issues);
        }
        if (device == null) {
            throw new IllegalArgumentException("device == null");
        }
        InstallExpectations exp = expectations == null
                ? InstallExpectations.builder().build() : expectations;

        if (apk.packageName() == null || apk.packageName().isEmpty()) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.PACKAGE_MISSING,
                    IssueSeverity.BLOCKER, "The package has no package name."));
        } else if (exp.packageName() != null && !exp.packageName().equals(apk.packageName())) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.PACKAGE_MISMATCH,
                    IssueSeverity.BLOCKER, "Expected package " + exp.packageName() + " but the file is "
                            + apk.packageName() + "."));
        }

        if (exp.installedVersionCode() >= 0
                && VersionCode.isDowngrade(exp.installedVersionCode(), apk.versionCode())) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.VERSION_DOWNGRADE,
                    IssueSeverity.BLOCKER, "Version code " + apk.versionCode()
                            + " is older than the installed " + exp.installedVersionCode()
                            + "; Android would reject the install."));
        }

        if (apk.minSdk() > 0 && apk.minSdk() > device.sdkInt()) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.MIN_SDK_TOO_HIGH,
                    IssueSeverity.BLOCKER, "Requires Android API " + apk.minSdk()
                            + " but this device runs API " + device.sdkInt() + "."));
        }

        if (!apk.supportsAnyAbi(device.abis())) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.NO_COMPATIBLE_ABI,
                    IssueSeverity.BLOCKER, "Ships native code for " + apk.nativeAbis()
                            + " but the device supports " + device.abis() + "."));
        }

        if (apk.signerSha256().isEmpty()) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.SIGNER_MISSING,
                    IssueSeverity.BLOCKER, "No signing certificate could be read from the package."));
        } else if (exp.pinnedSignerSha256() != null) {
            boolean match = false;
            for (String cert : apk.signerSha256()) {
                if (cert.equalsIgnoreCase(exp.pinnedSignerSha256().trim())) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                issues.add(new CompatibilityIssue(CompatibilityIssue.SIGNER_MISMATCH,
                        IssueSeverity.BLOCKER, "Signing certificate does not match the pinned "
                                + "certificate for this package."));
            }
        }

        if (exp.requireLaunchable() && !apk.isLaunchable()) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.NO_LAUNCH_INTENT,
                    IssueSeverity.BLOCKER, "No launchable activity for this device was found."));
        }

        for (String feature : apk.requiredFeatures()) {
            if (!exp.deviceFeatures().contains(feature)) {
                issues.add(new CompatibilityIssue(CompatibilityIssue.FEATURE_MISSING,
                        IssueSeverity.BLOCKER, "Requires device feature '" + feature
                                + "' which was not reported by this device."));
            }
        }

        if (apk.sizeBytes() > 0) {
            long needed = apk.sizeBytes() + exp.storageMarginBytes();
            if (!StorageBudget.hasRoomFor(needed, device.availableStorageBytes())) {
                issues.add(new CompatibilityIssue(CompatibilityIssue.STORAGE_LOW,
                        IssueSeverity.BLOCKER, "Needs " + StorageBudget.format(needed)
                                + " free but only " + StorageBudget.format(
                                        device.availableStorageBytes()) + " is available."));
            }
        }

        if (apk.targetSdk() > 0 && apk.targetSdk() < 23) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.TARGET_SDK_LEGACY,
                    IssueSeverity.WARNING, "Targets API " + apk.targetSdk()
                            + " and therefore runs with legacy compatibility behaviour."));
        }

        if (exp.requireTelevision() && !device.isTelevision()) {
            issues.add(new CompatibilityIssue(CompatibilityIssue.NOT_TELEVISION,
                    IssueSeverity.WARNING, "This package is built for television but the device "
                            + "did not report television features."));
        }

        return new CompatibilityReport(apk.packageName(), apk.versionCode(), issues);
    }

    /** Human-readable ABI summary for reports. */
    public static String describeAbis(List<String> abis) {
        if (abis == null || abis.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abis.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(abis.get(i).toLowerCase(Locale.US));
        }
        return sb.toString();
    }
}
