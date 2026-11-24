package app.gameday.host.update;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.gameday.core.Json;
import app.gameday.core.TimeFormat;
import app.gameday.core.VersionCode;
import app.gameday.core.net.UrlPolicy;
import app.gameday.core.update.ReleaseChannel;
import app.gameday.core.update.UpdateCheck;
import app.gameday.core.update.UpdateManifest;
import app.gameday.core.update.UpdateManifestParser;
import app.gameday.core.update.UpdatePolicy;
import app.gameday.host.BuildConfig;
import app.gameday.host.diagnostics.CrashLog;
import app.gameday.host.install.ApkVerifier;
import app.gameday.host.install.PackageInstallerHelper;
import app.gameday.host.settings.SettingsStore;

/**
 * GameDay's self-update path.
 *
 * <p>Release metadata comes from the project's own GitHub releases: a small {@code
 * gameday-update.json} asset per release, which pins the APK URL, its SHA-256 and the minimum Android
 * level. Checks run on start, at most every six hours while GameDay is active, and on demand. A
 * failed check backs off exponentially and eventually stops retrying instead of hammering the
 * network on a device with 1.5 GB of RAM.
 */
public final class GameDayUpdateManager {

    public enum Phase {
        IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, VERIFYING, INSTALLING, FAILED, BLOCKED,
        PERMISSION_REQUIRED
    }

    public static final class Status {
        public final Phase phase;
        public final String message;
        public final UpdateManifest manifest;
        public final long checkedAtMs;

        Status(Phase phase, String message, UpdateManifest manifest, long checkedAtMs) {
            this.phase = phase;
            this.message = message == null ? "" : message;
            this.manifest = manifest;
            this.checkedAtMs = checkedAtMs;
        }

        public boolean isUpdateAvailable() {
            return manifest != null && (phase == Phase.AVAILABLE || phase == Phase.DOWNLOADING
                    || phase == Phase.VERIFYING || phase == Phase.INSTALLING
                    || phase == Phase.PERMISSION_REQUIRED);
        }
    }

    private final Context app;
    private final SettingsStore settings;
    private final CrashLog log;
    private final UpdatePolicy policy = new UpdatePolicy();
    private final HttpFetcher http = new HttpFetcher();

    public GameDayUpdateManager(Context context, SettingsStore settings, CrashLog log) {
        this.app = context.getApplicationContext();
        this.settings = settings;
        this.log = log;
    }

    public UpdatePolicy policy() {
        return policy;
    }

    public boolean shouldAutoCheck(long nowMs) {
        if (!settings.autoUpdateGameDay()) {
            return false;
        }
        return policy.isAutomaticCheckDue(nowMs, settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L),
                settings.failures(), settings.getLong(SettingsStore.GD_LAST_FAILURE_MS, 0L));
    }

    public Status storedStatus() {
        UpdateManifest manifest = storedManifest();
        long checkedAt = settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L);
        if (manifest == null) {
            return new Status(Phase.IDLE, settings.failures() >= policy.maxFailures()
                    ? "Automatic checks paused after repeated failures."
                    : "No update information yet.", null, checkedAt);
        }
        long installed = currentVersionCode();
        if (VersionCode.isUpgrade(installed, manifest.versionCode())) {
            return new Status(Phase.AVAILABLE, "Version " + manifest.versionName() + " is available.",
                    manifest, checkedAt);
        }
        return new Status(Phase.UP_TO_DATE, "GameDay is up to date.", null, checkedAt);
    }

    public UpdateManifest storedManifest() {
        long code = settings.getLong(SettingsStore.GD_LATEST_VERSION_CODE, -1L);
        String url = settings.getString(SettingsStore.GD_LATEST_URL, "");
        String sha = settings.getString(SettingsStore.GD_LATEST_SHA256, "");
        if (code <= 0 || url == null || url.isEmpty() || sha == null || sha.isEmpty()) {
            return null;
        }
        // Values are read back exactly as published. Earlier versions reconstructed minSdk and
        // publishedAt from local state, which misreported the artefact in diagnostics and exports.
        return new UpdateManifest(app.getPackageName(), settings.channel(),
                settings.getString(SettingsStore.GD_LATEST_VERSION_NAME, "0.0.0"), code,
                (int) settings.getLong(SettingsStore.GD_LATEST_MIN_SDK, 0L),
                url, sha, settings.getLong(SettingsStore.GD_LATEST_SIZE, 0L),
                settings.getString(SettingsStore.GD_LATEST_PUBLISHED_AT, ""),
                settings.getString(SettingsStore.GD_LATEST_NOTES, ""));
    }

    /** Network check. Call from a background thread: it performs up to three HTTPS requests. */
    public Status check(boolean userInitiated) {
        long now = System.currentTimeMillis();
        if (!userInitiated) {
            if (!settings.autoUpdateGameDay()) {
                return new Status(Phase.IDLE, "Automatic GameDay update checks are off.", null,
                        settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L));
            }
            if (!policy.isAutomaticCheckDue(now,
                    settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L),
                    settings.failures(),
                    settings.getLong(SettingsStore.GD_LAST_FAILURE_MS, 0L))) {
                if (settings.failures() >= policy.maxFailures()) {
                    return new Status(Phase.BLOCKED,
                            "Automatic checks paused after repeated failures.",
                            storedManifest(), settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L));
                }
                return new Status(Phase.IDLE, "Last checked "
                        + TimeFormat.ago(now, settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L))
                        + ".", storedManifest(),
                        settings.getLong(SettingsStore.GD_LAST_CHECK_MS, 0L));
            }
        }

        settings.putLong(SettingsStore.GD_LAST_CHECK_MS, now);
        String releasesUrl = "https://api.github.com/repos/" + BuildConfig.UPDATE_REPOSITORY
                + "/releases?per_page=10";
        String cachedEtag = settings.getString(SettingsStore.GD_RELEASES_ETAG, null);
        HttpFetcher.Result response = http.fetchText(releasesUrl, cachedEtag);
        if (!response.ok) {
            return failure(now, response.message);
        }
        if (response.notModified) {
            // Conditional request: the feed is unchanged, so no release document is parsed and no
            // further requests are made. This is what keeps a 1.5 GB device off the network.
            settings.setFailures(0);
            settings.commit();
            return new Status(Phase.UP_TO_DATE, "Release feed unchanged since the last check.",
                    storedManifest(), now);
        }
        if (response.etag != null && !response.etag.isEmpty()) {
            settings.putString(SettingsStore.GD_RELEASES_ETAG, response.etag);
        }
        Json releases = Json.parseOrNull(response.body);
        if (releases == null || !releases.isArray()) {
            return failure(now, "The release feed returned an unexpected document.");
        }

        ReleaseChannel channel = settings.channel();
        long installed = currentVersionCode();
        List<String> problems = new ArrayList<String>();
        boolean sawIncompatible = false;
        String newestIncompatible = "";

        for (int i = 0; i < releases.size(); i++) {
            Json release = releases.at(i);
            if (release == null || !release.isObject()) {
                continue;
            }
            if (release.get("draft") != null && release.get("draft").asBoolean(false)) {
                continue;
            }
            boolean prerelease = release.get("prerelease") != null
                    && release.get("prerelease").asBoolean(false);
            if (channel == ReleaseChannel.STABLE && prerelease) {
                continue;
            }
            String manifestUrl = assetUrl(release, BuildConfig.UPDATE_MANIFEST_ASSET);
            if (manifestUrl == null) {
                continue;
            }
            HttpFetcher.Result manifestResponse = http.fetchText(manifestUrl);
            if (!manifestResponse.ok) {
                problems.add(manifestResponse.message);
                continue;
            }
            UpdateManifestParser.Result parsed = UpdateManifestParser.parse(manifestResponse.body,
                    app.getPackageName());
            if (!parsed.isSuccess()) {
                problems.add("Release manifest rejected: " + parsed.problems);
                continue;
            }
            UpdateCheck.Result evaluation = UpdateCheck.evaluate(parsed.manifest, installed,
                    Build.VERSION.SDK_INT, channel);
            if (evaluation.status() == UpdateCheck.Status.INCOMPATIBLE_DEVICE) {
                sawIncompatible = true;
                newestIncompatible = parsed.manifest.versionName()
                        + " needs Android API " + parsed.manifest.minSdk();
                continue; // keep looking for the newest *compatible* release
            }
            if (evaluation.status() == UpdateCheck.Status.UPDATE_AVAILABLE) {
                store(parsed.manifest, now);
                settings.setFailures(0);
                return new Status(Phase.AVAILABLE,
                        "Version " + parsed.manifest.versionName() + " is available.",
                        parsed.manifest, now);
            }
            if (evaluation.status() == UpdateCheck.Status.UP_TO_DATE) {
                settings.setFailures(0);
                settings.clearGameDayUpdateInfo();
                return new Status(Phase.UP_TO_DATE, "GameDay is up to date.", null, now);
            }
        }

        settings.setFailures(0);
        if (sawIncompatible) {
            return new Status(Phase.UP_TO_DATE,
                    "No compatible update: " + newestIncompatible + ".", null, now);
        }
        if (!problems.isEmpty()) {
            log.record("update check found no usable release: " + problems);
            return new Status(Phase.UP_TO_DATE,
                    "No release with a usable manifest was found for this channel.", null, now);
        }
        return new Status(Phase.UP_TO_DATE, "GameDay is up to date.", null, now);
    }

    /**
     * Downloads, verifies and stages the update. Call from a background thread.
     *
     * @param progress optional byte-progress callback, invoked on the downloading thread
     */
    public Status downloadAndInstall(UpdateManifest manifest, HttpFetcher.Progress progress) {
        if (manifest == null) {
            return new Status(Phase.FAILED, "No update to install.", null,
                    System.currentTimeMillis());
        }
        if (!UrlPolicy.isAllowedUpdateUrl(manifest.apkUrl())) {
            return new Status(Phase.FAILED, "Update URL is not on an approved host.", manifest,
                    System.currentTimeMillis());
        }
        if (!PackageInstallerHelper.canRequestPackageInstalls(app)) {
            return new Status(Phase.PERMISSION_REQUIRED,
                    "Allow GameDay to install updates, then try again.", manifest,
                    System.currentTimeMillis());
        }

        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            return new Status(Phase.FAILED, "Could not create the update directory.", manifest,
                    System.currentTimeMillis());
        }
        File apk = new File(dir, "gameday-" + manifest.versionCode() + ".apk");
        if (apk.exists() && !apk.delete()) {
            return new Status(Phase.FAILED, "Could not clear the previous download.", manifest,
                    System.currentTimeMillis());
        }

        HttpFetcher.Result download = http.download(manifest.apkUrl(), apk, manifest.sizeBytes(),
                manifest.sha256(), progress);
        if (!download.ok) {
            return new Status(Phase.FAILED, download.message, manifest, System.currentTimeMillis());
        }

        ApkVerifier.Result verified = new ApkVerifier(app).verifySelfUpdate(apk);
        if (!verified.ok()) {
            delete(apk);
            return new Status(Phase.FAILED, "Update rejected: " + verified.message(), manifest,
                    System.currentTimeMillis());
        }

        PackageInstallerHelper.Result install =
                PackageInstallerHelper.install(app, java.util.Collections.singletonList(apk), true);
        if (!install.ok()) {
            if (install.permissionNeeded()) {
                return new Status(Phase.PERMISSION_REQUIRED, install.message(), manifest,
                        System.currentTimeMillis());
            }
            delete(apk);
            return new Status(Phase.FAILED, install.message(), manifest,
                    System.currentTimeMillis());
        }
        // The session has copied the bytes; the cache file is no longer needed.
        delete(apk);
        settings.clearGameDayUpdateInfo();
        return new Status(Phase.INSTALLING, "Update verified and handed to the installer.",
                manifest, System.currentTimeMillis());
    }

    private void store(UpdateManifest manifest, long now) {
        settings.putLong(SettingsStore.GD_LATEST_VERSION_CODE, manifest.versionCode());
        settings.putString(SettingsStore.GD_LATEST_VERSION_NAME, manifest.versionName());
        settings.putString(SettingsStore.GD_LATEST_URL, manifest.apkUrl());
        settings.putString(SettingsStore.GD_LATEST_SHA256, manifest.sha256());
        settings.putLong(SettingsStore.GD_LATEST_SIZE, manifest.sizeBytes());
        settings.putString(SettingsStore.GD_LATEST_NOTES, manifest.releaseNotes());
        settings.putLong(SettingsStore.GD_LATEST_MIN_SDK, manifest.minSdk());
        settings.putString(SettingsStore.GD_LATEST_PUBLISHED_AT, manifest.publishedAt());
        settings.putLong(SettingsStore.GD_LAST_CHECK_MS, now);
        settings.commit();
    }

    private Status failure(long now, String message) {
        int failures = settings.failures() + 1;
        settings.setFailures(failures);
        settings.putLong(SettingsStore.GD_LAST_FAILURE_MS, now);
        settings.commit();
        log.record("update check failed (" + failures + "): " + message);
        if (policy.isCircuitOpen(failures)) {
            return new Status(Phase.BLOCKED,
                    "Checks paused after " + failures + " failures: " + message, storedManifest(), now);
        }
        long retryIn = policy.backoffMs(failures);
        return new Status(Phase.FAILED,
                message + " Next automatic attempt in " + TimeFormat.duration(retryIn) + ".",
                storedManifest(), now);
    }

    private long currentVersionCode() {
        try {
            android.content.pm.PackageInfo info = app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return -1L;
        }
    }

    private static String assetUrl(Json release, String assetName) {
        Json assets = release.get("assets");
        if (assets == null || !assets.isArray()) {
            return null;
        }
        for (int i = 0; i < assets.size(); i++) {
            Json asset = assets.at(i);
            if (asset == null) {
                continue;
            }
            String name = asset.get("name") == null ? "" : asset.get("name").asString("");
            if (!assetName.equals(name)) {
                continue;
            }
            String url = asset.get("browser_download_url") == null
                    ? "" : asset.get("browser_download_url").asString("");
            if (!url.isEmpty() && UrlPolicy.isAllowedUpdateUrl(url)) {
                return url;
            }
        }
        return null;
    }

    private static void delete(File f) {
        if (f != null && f.exists() && !f.delete()) {
            // keep going: the next attempt overwrites it
        }
    }
}
