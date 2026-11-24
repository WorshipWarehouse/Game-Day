package app.gameday.host.update;

import android.content.Context;

import app.gameday.core.TimeFormat;
import app.gameday.host.runtime.NativeHostRuntime;
import app.gameday.host.settings.SettingsStore;
import app.gameday.runtime.api.HostAppInfo;
import app.gameday.runtime.api.RuntimeResult;

/**
 * Tracks the NFL application's version and delegates updates to the Amazon Appstore.
 *
 * <p>There is no authorized public API for "latest NFL app version". Inventing one, or scraping
 * an APK mirror, would be both unreliable and outside the project's rules. GameDay therefore does
 * what Fire OS actually supports: it reads the installed version exactly, opens the official store
 * page for updates, and detects the resulting version change the next time it looks. Everything it
 * reports about the guest is something it observed on the device.
 */
public final class NflUpdateManager {

    public static final class Snapshot {
        public final boolean installed;
        public final String versionName;
        public final long versionCode;
        public final String signer;
        public final String installerPackage;
        public final boolean signerTrusted;
        public final boolean launchable;
        public final long lastKnownWorkingCode;
        public final String lastKnownWorkingName;
        public final long lastCheckMs;
        public final long lastObservedUpdateMs;
        public final String note;

        Snapshot(boolean installed, String versionName, long versionCode, String signer,
                String installerPackage, boolean signerTrusted, boolean launchable,
                long lastKnownWorkingCode, String lastKnownWorkingName, long lastCheckMs,
                long lastObservedUpdateMs, String note) {
            this.installed = installed;
            this.versionName = versionName == null ? "" : versionName;
            this.versionCode = versionCode;
            this.signer = signer == null ? "" : signer;
            this.installerPackage = installerPackage == null ? "" : installerPackage;
            this.signerTrusted = signerTrusted;
            this.launchable = launchable;
            this.lastKnownWorkingCode = lastKnownWorkingCode;
            this.lastKnownWorkingName = lastKnownWorkingName == null ? "" : lastKnownWorkingName;
            this.lastCheckMs = lastCheckMs;
            this.lastObservedUpdateMs = lastObservedUpdateMs;
            this.note = note == null ? "" : note;
        }

        public String describeVersion() {
            if (!installed) {
                return "not installed";
            }
            return versionName + " (" + versionCode + ")";
        }

        public String describeLastKnownWorking() {
            if (lastKnownWorkingCode <= 0) {
                return "not recorded yet";
            }
            return lastKnownWorkingName + " (" + lastKnownWorkingCode + ")";
        }
    }

    private final Context app;
    private final SettingsStore settings;
    private final NativeHostRuntime runtime;

    public NflUpdateManager(Context context, SettingsStore settings, NativeHostRuntime runtime) {
        this.app = context.getApplicationContext();
        this.settings = settings;
        this.runtime = runtime;
    }

    /**
     * Reads the installed guest, records the signing identity on first use, and notices version
     * changes (including ones made by the Appstore while GameDay was not running).
     */
    public Snapshot observe() {
        RuntimeResult<HostAppInfo> result = runtime.hostAppInfo();
        HostAppInfo info = result.value();
        long now = System.currentTimeMillis();
        if (!result.isOk() || info == null || !info.isInstalled()) {
            return new Snapshot(false, "", -1, "", "", true, false,
                    settings.getLong(SettingsStore.NFL_LAST_KNOWN_WORKING_CODE, 0L),
                    settings.getString(SettingsStore.NFL_LAST_KNOWN_WORKING_NAME, ""),
                    settings.getLong(SettingsStore.NFL_LAST_CHECK_MS, 0L),
                    settings.getLong(SettingsStore.NFL_LAST_OBSERVED_UPDATE_MS, 0L),
                    "The NFL app is not installed on this device.");
        }

        String signer = info.signerSha256().isEmpty() ? "" : info.signerSha256().get(0);
        String trusted = settings.trustedNflSigner();
        boolean trustedOk = true;
        String note = "";
        if (!signer.isEmpty()) {
            if (trusted == null || trusted.isEmpty()) {
                settings.setTrustedNflSigner(signer);
                note = "Signing identity recorded on first launch.";
            } else if (!trusted.equalsIgnoreCase(signer)) {
                trustedOk = false;
                note = "The installed NFL app is signed by a different certificate than the one "
                        + "GameDay recorded. It was not updated by GameDay and will not be launched.";
            }
        }

        long storedCode = settings.getLong(SettingsStore.NFL_INSTALLED_VERSION_CODE, -1L);
        if (info.versionCode() != storedCode) {
            if (storedCode > 0 && info.versionCode() > storedCode) {
                settings.putLong(SettingsStore.NFL_LAST_OBSERVED_UPDATE_MS, now);
                note = "The NFL app changed from " + storedCode + " to " + info.versionCode()
                        + " since the last check.";
            }
            settings.putLong(SettingsStore.NFL_INSTALLED_VERSION_CODE, info.versionCode());
            settings.putString(SettingsStore.NFL_INSTALLED_VERSION_NAME, info.versionName());
            settings.commit();
        }

        return new Snapshot(true, info.versionName(), info.versionCode(), signer,
                info.installerPackage(), trustedOk, info.isLaunchable(),
                settings.getLong(SettingsStore.NFL_LAST_KNOWN_WORKING_CODE, 0L),
                settings.getString(SettingsStore.NFL_LAST_KNOWN_WORKING_NAME, ""),
                settings.getLong(SettingsStore.NFL_LAST_CHECK_MS, 0L),
                settings.getLong(SettingsStore.NFL_LAST_OBSERVED_UPDATE_MS, 0L), note);
    }

    /** Records that the guest launched successfully: the newest verified-compatible version. */
    public void noteSuccessfulLaunch() {
        RuntimeResult<HostAppInfo> result = runtime.hostAppInfo();
        HostAppInfo info = result.value();
        if (info == null || !info.isInstalled()) {
            return;
        }
        settings.putLong(SettingsStore.NFL_LAST_KNOWN_WORKING_CODE, info.versionCode());
        settings.putString(SettingsStore.NFL_LAST_KNOWN_WORKING_NAME, info.versionName());
        settings.commit();
    }

    public void noteCheckPerformed() {
        settings.putLong(SettingsStore.NFL_LAST_CHECK_MS, System.currentTimeMillis());
        settings.commit();
    }

    /**
     * Opens the official NFL app page in the Amazon Appstore, which is the authorized update path.
     * Returns false when the store (and its web fallback) cannot be reached.
     */
    public boolean openStoreForUpdates() {
        noteCheckPerformed();
        return NativeHostRuntime.AppStore.openHostUpdatePage(app);
    }

    public boolean openStoreForInstall() {
        noteCheckPerformed();
        return NativeHostRuntime.AppStore.openHostPage(app);
    }

    public String describeUpdateChannel() {
        Snapshot s = observe();
        if (!s.installed) {
            return "The NFL app is not installed. The Amazon Appstore provides it.";
        }
        String base = "Updates are delivered by the Amazon Appstore";
        if (s.lastObservedUpdateMs > 0) {
            base += "; last observed change " + TimeFormat.ago(System.currentTimeMillis(),
                    s.lastObservedUpdateMs);
        } else {
            base += "; no change observed yet";
        }
        return base + ".";
    }
}
