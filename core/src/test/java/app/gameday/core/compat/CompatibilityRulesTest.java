package app.gameday.core.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class CompatibilityRulesTest {

    private static final String CERT =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String OTHER_CERT =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static DeviceFacts fireTv() {
        return DeviceFacts.builder()
                .sdkInt(25)
                .abis(Collections.singletonList("armeabi-v7a"))
                .television(true)
                .touchscreen(false)
                .availableStorageBytes(2L * 1024L * 1024L * 1024L)
                .display(1920, 1080)
                .hardwareDecode(true, true)
                .build();
    }

    private static ApkFacts apk() {
        return ApkFacts.builder()
                .packageName("com.gotv.nflgamecenter.us.lite")
                .versionName("18.22.2")
                .versionCode(101395279L)
                .minSdk(25)
                .targetSdk(30)
                .nativeAbis(Arrays.asList("arm64-v8a", "armeabi-v7a"))
                .signerSha256(Collections.singletonList(CERT))
                .sizeBytes(72_000_000L)
                .launchable(true)
                .build();
    }

    private static InstallExpectations expectations() {
        return InstallExpectations.builder()
                .packageName("com.gotv.nflgamecenter.us.lite")
                .pinnedSignerSha256(CERT)
                .installedVersionCode(-1)
                .requireLaunchable(true)
                .requireTelevision(true)
                .build();
    }

    @Test
    public void compatiblePackagePasses() {
        CompatibilityReport report = CompatibilityRules.evaluate(apk(), fireTv(), expectations());
        assertTrue(report.summary(), report.isInstallable());
        assertEquals(Collections.emptyList(), report.blockers());
    }

    @Test
    public void wrongPackageIsBlocked() {
        InstallExpectations exp = InstallExpectations.builder()
                .packageName("app.gameday.host").build();
        assertTrue(CompatibilityRules.evaluate(apk(), fireTv(), exp)
                .hasCode(CompatibilityIssue.PACKAGE_MISMATCH));
    }

    @Test
    public void downgradeIsBlocked() {
        InstallExpectations exp = InstallExpectations.builder()
                .installedVersionCode(101395280L).build();
        assertTrue(CompatibilityRules.evaluate(apk(), fireTv(), exp)
                .hasCode(CompatibilityIssue.VERSION_DOWNGRADE));
    }

    @Test
    public void newerMinSdkIsBlocked() {
        ApkFacts tooNew = ApkFacts.builder().packageName("p").versionCode(1).minSdk(29)
                .signerSha256(Collections.singletonList(CERT)).build();
        assertTrue(CompatibilityRules.evaluate(tooNew, fireTv(), null)
                .hasCode(CompatibilityIssue.MIN_SDK_TOO_HIGH));
    }

    @Test
    public void incompatibleNativeAbiIsBlocked() {
        ApkFacts arm64Only = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                .nativeAbis(Collections.singletonList("arm64-v8a"))
                .signerSha256(Collections.singletonList(CERT)).build();
        assertTrue(CompatibilityRules.evaluate(arm64Only, fireTv(), null)
                .hasCode(CompatibilityIssue.NO_COMPATIBLE_ABI));
    }

    @Test
    public void pureJavaPackageRunsOnAnyAbi() {
        ApkFacts pureJava = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                .signerSha256(Collections.singletonList(CERT)).build();
        assertTrue(CompatibilityRules.evaluate(pureJava, fireTv(), null).isInstallable());
    }

    @Test
    public void signerMismatchAndMissingSignerAreBlocked() {
        ApkFacts wrongSigner = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                .signerSha256(Collections.singletonList(OTHER_CERT)).build();
        assertTrue(CompatibilityRules.evaluate(wrongSigner, fireTv(), expectations())
                .hasCode(CompatibilityIssue.SIGNER_MISMATCH));

        ApkFacts unsigned = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25).build();
        assertTrue(CompatibilityRules.evaluate(unsigned, fireTv(), null)
                .hasCode(CompatibilityIssue.SIGNER_MISSING));
    }

    @Test
    public void insufficientStorageIsBlocked() {
        DeviceFacts nearlyFull = DeviceFacts.builder().sdkInt(25)
                .abis(Collections.singletonList("armeabi-v7a"))
                .availableStorageBytes(40L * 1024L * 1024L).build();
        assertTrue(CompatibilityRules.evaluate(apk(), nearlyFull, expectations())
                .hasCode(CompatibilityIssue.STORAGE_LOW));
    }

    @Test
    public void missingLaunchIntentIsBlockedWhenRequired() {
        ApkFacts noLauncher = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                .signerSha256(Collections.singletonList(CERT)).launchable(false).build();
        assertTrue(CompatibilityRules.evaluate(noLauncher, fireTv(), expectations())
                .hasCode(CompatibilityIssue.NO_LAUNCH_INTENT));
    }

    @Test
    public void requiredFeatureMissingIsBlocked() {
        ApkFacts needsLeanback = ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                .signerSha256(Collections.singletonList(CERT))
                .requiredFeatures(Collections.singletonList("android.software.leanback")).build();
        assertTrue(CompatibilityRules.evaluate(needsLeanback, fireTv(), null)
                .hasCode(CompatibilityIssue.FEATURE_MISSING));

        InstallExpectations withLeanback = InstallExpectations.builder()
                .deviceFeatures(Collections.singleton("android.software.leanback")).build();
        assertTrue(CompatibilityRules.evaluate(needsLeanback, fireTv(), withLeanback)
                .isInstallable());
    }

    @Test
    public void softFindingsAreWarningsNotBlockers() {
        ApkFacts legacyTarget = ApkFacts.builder().packageName("p").versionCode(1).minSdk(21)
                .targetSdk(22).signerSha256(Collections.singletonList(CERT)).build();
        CompatibilityReport report = CompatibilityRules.evaluate(legacyTarget, fireTv(), null);
        assertTrue(report.hasCode(CompatibilityIssue.TARGET_SDK_LEGACY));
        assertTrue(report.isInstallable());

        DeviceFacts phone = DeviceFacts.builder().sdkInt(33)
                .abis(Collections.singletonList("arm64-v8a")).television(false).build();
        CompatibilityReport tvReport = CompatibilityRules.evaluate(
                ApkFacts.builder().packageName("p").versionCode(1).minSdk(25)
                        .signerSha256(Collections.singletonList(CERT)).build(), phone,
                InstallExpectations.builder().requireTelevision(true).build());
        assertTrue(tvReport.hasCode(CompatibilityIssue.NOT_TELEVISION));
        assertTrue(tvReport.isInstallable());
    }

    @Test
    public void nullApkProducesBlocker() {
        assertFalse(CompatibilityRules.evaluate(null, fireTv(), null).isInstallable());
    }
}
