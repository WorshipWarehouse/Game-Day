package app.gameday.host.install;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.gameday.core.compat.ApkFacts;
import app.gameday.core.compat.CompatibilityIssue;
import app.gameday.core.compat.CompatibilityReport;
import app.gameday.core.compat.CompatibilityRules;
import app.gameday.core.compat.DeviceFacts;
import app.gameday.core.compat.InstallExpectations;
import app.gameday.core.install.InstallPlan;
import app.gameday.host.diagnostics.DeviceProbe;

/**
 * Gate between a downloaded or sideloaded APK and the platform installer.
 *
 * <p>Identity is established before anything else: GameDay pins the signing certificate of the
 * package it is upgrading (trust-on-first-use from the installed app, or an explicit pin), refuses
 * downgrades, and refuses anything whose declared Android level or ABI the device cannot satisfy.
 * No APK is ever re-signed or modified.
 */
public final class ApkVerifier {

    private final Context app;

    public ApkVerifier(Context context) {
        this.app = context.getApplicationContext();
    }

    public static final class Result {
        private final boolean ok;
        private final String message;
        private final CompatibilityReport report;
        private final List<ApkFacts> facts;

        Result(boolean ok, String message, CompatibilityReport report, List<ApkFacts> facts) {
            this.ok = ok;
            this.message = message;
            this.report = report;
            this.facts = facts;
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message;
        }

        public CompatibilityReport report() {
            return report;
        }

        public List<ApkFacts> facts() {
            return facts;
        }
    }

    /**
     * @param expectedPackage the package the caller intends to install
     * @param pinnedSigner allowed signer digest; when null the installed signer is used, and when
     *     the package is not installed the caller must supply a pin (otherwise verification fails)
     */
    public Result verifyForInstall(List<File> apks, String expectedPackage, String pinnedSigner,
            long installedVersionCode, boolean requireTelevision) {
        if (apks == null || apks.isEmpty()) {
            return new Result(false, "No APK files were supplied.", null, null);
        }
        List<ApkFacts> facts = new ArrayList<ApkFacts>();
        for (File apk : apks) {
            ApkFacts f = ApkInspector.inspect(app, apk);
            if (f == null) {
                return new Result(false, "Not a readable APK: " + apk.getName(), null, null);
            }
            facts.add(f);
        }

        InstallPlan.Validation plan = InstallPlan.validate(facts);
        if (!plan.isValid()) {
            return new Result(false, plan.describe(), null, facts);
        }

        String pin = pinnedSigner;
        if (pin == null || pin.trim().isEmpty()) {
            List<String> installed = ApkInspector.installedSigners(app, expectedPackage);
            if (!installed.isEmpty()) {
                pin = installed.get(0);
            }
        }
        if (pin == null || pin.trim().isEmpty()) {
            return new Result(false,
                    "The signing identity of " + expectedPackage + " cannot be established on this "
                            + "device, so the package cannot be verified. Install it once from the "
                            + "official store, or pin its signer in configuration.",
                    null, facts);
        }

        long installedForDowngradeCheck = installedVersionCode;
        if (installedForDowngradeCheck < 0) {
            installedForDowngradeCheck = ApkInspector.installedVersionCode(app, expectedPackage);
        }

        DeviceFacts device = DeviceProbe.facts(app);
        InstallExpectations expectations = InstallExpectations.builder()
                .packageName(expectedPackage)
                .pinnedSignerSha256(pin)
                .installedVersionCode(installedForDowngradeCheck)
                .requireLaunchable(true)
                .requireTelevision(requireTelevision)
                .deviceFeatures(DeviceProbe.deviceFeatures(app))
                .build();

        CompatibilityReport report = CompatibilityRules.evaluate(plan.plan().base(), device,
                expectations);
        if (!report.isInstallable()) {
            StringBuilder sb = new StringBuilder("Package rejected: ");
            for (CompatibilityIssue issue : report.blockers()) {
                sb.append(issue.code()).append(" (").append(issue.message()).append(") ");
            }
            return new Result(false, sb.toString().trim(), report, facts);
        }
        return new Result(true, report.summary(), report, facts);
    }

    /** Verifies a single APK whose package is GameDay itself. */
    public Result verifySelfUpdate(File apk) {
        String self = app.getPackageName();
        List<String> installed = ApkInspector.installedSigners(app, self);
        String pin = installed.isEmpty() ? null : installed.get(0);
        List<File> files = new ArrayList<File>();
        files.add(apk);
        return verifyForInstall(files, self, pin,
                ApkInspector.installedVersionCode(app, self), false);
    }

    /**
     * Trust-on-first-use for the hosted application: records the signer the first time it is seen
     * and reports a mismatch afterwards. A different signer means the installed app is not the app
     * GameDay has been hosting, which is exactly when the user must be warned.
     */
    public static final class TrustResult {
        public final boolean installed;
        public final boolean trusted;
        public final String signer;
        public final String message;

        TrustResult(boolean installed, boolean trusted, String signer, String message) {
            this.installed = installed;
            this.trusted = trusted;
            this.signer = signer;
            this.message = message;
        }
    }

    public static TrustResult observeTrust(Context context, String packageName,
            app.gameday.host.settings.SettingsStore settings, String pinnedKey) {
        List<String> signers = ApkInspector.installedSigners(context, packageName);
        if (signers.isEmpty()) {
            return new TrustResult(false, true, "", "Not installed.");
        }
        String signer = signers.get(0);
        String trusted = settings.getString(pinnedKey, null);
        if (trusted == null || trusted.isEmpty()) {
            settings.putString(pinnedKey, signer);
            settings.commit();
            return new TrustResult(true, true, signer, "Signing identity recorded.");
        }
        if (trusted.equalsIgnoreCase(signer)) {
            return new TrustResult(true, true, signer, "Signing identity matches.");
        }
        return new TrustResult(true, false, signer,
                "Signing identity changed since GameDay last saw it.");
    }
}
