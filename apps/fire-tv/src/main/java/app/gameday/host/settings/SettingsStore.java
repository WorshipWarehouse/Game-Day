package app.gameday.host.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import app.gameday.core.KeyValueStore;
import app.gameday.core.update.ReleaseChannel;

/**
 * GameDay's persistent configuration.
 *
 * <p>Only GameDay's own preferences live here. The NFL application's sign-in, tokens and playback
 * state stay inside the NFL application's sandbox where they belong; GameDay never reads them.
 */
public final class SettingsStore implements KeyValueStore {

    private static final String PREFS = "gameday";

    public static final String AUTO_LAUNCH = "general.autoLaunch";
    public static final String RESUME_SESSION = "general.resumeSession";
    public static final String AUTO_UPDATE_NFL = "updates.nfl.auto";
    public static final String AUTO_UPDATE_GAMEDAY = "updates.gameday.auto";
    public static final String CHANNEL = "updates.gameday.channel";

    public static final String NFL_TRUSTED_SIGNER = "nfl.trustedSigner";
    public static final String NFL_INSTALLED_VERSION_CODE = "nfl.installedVersionCode";
    public static final String NFL_INSTALLED_VERSION_NAME = "nfl.installedVersionName";
    public static final String NFL_LAST_KNOWN_WORKING_CODE = "nfl.lastKnownWorkingCode";
    public static final String NFL_LAST_KNOWN_WORKING_NAME = "nfl.lastKnownWorkingName";
    public static final String NFL_LAST_CHECK_MS = "nfl.lastCheckMs";
    public static final String NFL_LAST_OBSERVED_UPDATE_MS = "nfl.lastObservedUpdateMs";
    public static final String NFL_FIRST_INSTALL_MS = "nfl.firstInstallMs";
    public static final String NFL_STORE_LATEST_VERSION_NAME = "nfl.storeLatestVersionName";

    public static final String GD_LAST_CHECK_MS = "gd.lastCheckMs";
    public static final String GD_LAST_FAILURE_MS = "gd.lastFailureMs";
    public static final String GD_FAILURES = "gd.failures";
    public static final String GD_LATEST_VERSION_CODE = "gd.latest.versionCode";
    public static final String GD_LATEST_VERSION_NAME = "gd.latest.versionName";
    public static final String GD_LATEST_URL = "gd.latest.url";
    public static final String GD_LATEST_SHA256 = "gd.latest.sha256";
    public static final String GD_LATEST_SIZE = "gd.latest.size";
    public static final String GD_LATEST_NOTES = "gd.latest.notes";
    public static final String GD_LATEST_MIN_SDK = "gd.latest.minSdk";
    public static final String GD_LATEST_PUBLISHED_AT = "gd.latest.publishedAt";
    public static final String GD_RELEASES_ETAG = "gd.releases.etag";
    public static final String GD_INSTALL_ATTEMPT_MS = "gd.installAttemptMs";

    public static final String RUNTIME_LAST_ERROR = "runtime.lastError";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- typed accessors

    public boolean autoLaunch() {
        return prefs.getBoolean(AUTO_LAUNCH, true);
    }

    public void setAutoLaunch(boolean value) {
        prefs.edit().putBoolean(AUTO_LAUNCH, value).apply();
    }

    public boolean resumeSession() {
        return prefs.getBoolean(RESUME_SESSION, true);
    }

    public void setResumeSession(boolean value) {
        prefs.edit().putBoolean(RESUME_SESSION, value).apply();
    }

    public boolean autoUpdateNfl() {
        return prefs.getBoolean(AUTO_UPDATE_NFL, true);
    }

    public void setAutoUpdateNfl(boolean value) {
        prefs.edit().putBoolean(AUTO_UPDATE_NFL, value).apply();
    }

    public boolean autoUpdateGameDay() {
        return prefs.getBoolean(AUTO_UPDATE_GAMEDAY, true);
    }

    public void setAutoUpdateGameDay(boolean value) {
        prefs.edit().putBoolean(AUTO_UPDATE_GAMEDAY, value).apply();
    }

    public ReleaseChannel channel() {
        return ReleaseChannel.fromId(prefs.getString(CHANNEL, "stable"));
    }

    public void setChannel(ReleaseChannel channel) {
        prefs.edit().putString(CHANNEL, channel.id()).apply();
    }

    public String trustedNflSigner() {
        return prefs.getString(NFL_TRUSTED_SIGNER, null);
    }

    public void setTrustedNflSigner(String sha256) {
        prefs.edit().putString(NFL_TRUSTED_SIGNER, sha256).apply();
    }

    public int failures() {
        return prefs.getInt(GD_FAILURES, 0);
    }

    public void setFailures(int value) {
        prefs.edit().putInt(GD_FAILURES, Math.max(0, value)).apply();
    }

    public void clearGameDayUpdateInfo() {
        prefs.edit()
                .remove(GD_LATEST_VERSION_CODE)
                .remove(GD_LATEST_VERSION_NAME)
                .remove(GD_LATEST_URL)
                .remove(GD_LATEST_SHA256)
                .remove(GD_LATEST_SIZE)
                .remove(GD_LATEST_NOTES)
                .remove(GD_LATEST_MIN_SDK)
                .remove(GD_LATEST_PUBLISHED_AT)
                .apply();
    }

    public void setLastRuntimeError(String message) {
        prefs.edit().putString(RUNTIME_LAST_ERROR, message).apply();
    }

    public String lastRuntimeError() {
        return prefs.getString(RUNTIME_LAST_ERROR, "");
    }

    // ---------------------------------------------------------------- KeyValueStore

    @Override
    public String getString(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    @Override
    public long getLong(String key, long fallback) {
        return prefs.getLong(key, fallback);
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        return prefs.getBoolean(key, fallback);
    }

    @Override
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    @Override
    public boolean commit() {
        return prefs.edit().commit();
    }

    /**
     * Restores factory defaults without touching the NFL application.
     *
     * <p>Observed facts are deliberately preserved: the pinned NFL signing certificate (clearing it
     * would silently re-open trust-on-first-use to any package that replaced the app), the version
     * GameDay last saw installed, and the last version known to launch. Only user preferences are
     * reset, so "restore defaults" cannot weaken package verification.
     */
    public void resetToDefaults() {
        Set<String> preserved = new LinkedHashSet<String>();
        preserved.add(NFL_TRUSTED_SIGNER);
        preserved.add(NFL_INSTALLED_VERSION_CODE);
        preserved.add(NFL_INSTALLED_VERSION_NAME);
        preserved.add(NFL_LAST_KNOWN_WORKING_CODE);
        preserved.add(NFL_LAST_KNOWN_WORKING_NAME);
        preserved.add(NFL_LAST_OBSERVED_UPDATE_MS);
        preserved.add(NFL_FIRST_INSTALL_MS);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!preserved.contains(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** Keys that survive a configuration reset, for the confirmation dialog and diagnostics. */
    public static Set<String> preservedAcrossReset() {
        Set<String> preserved = new LinkedHashSet<String>();
        preserved.add(NFL_TRUSTED_SIGNER);
        preserved.add(NFL_INSTALLED_VERSION_CODE);
        preserved.add(NFL_INSTALLED_VERSION_NAME);
        preserved.add(NFL_LAST_KNOWN_WORKING_CODE);
        preserved.add(NFL_LAST_KNOWN_WORKING_NAME);
        preserved.add(NFL_LAST_OBSERVED_UPDATE_MS);
        preserved.add(NFL_FIRST_INSTALL_MS);
        return Collections.unmodifiableSet(preserved);
    }
}
