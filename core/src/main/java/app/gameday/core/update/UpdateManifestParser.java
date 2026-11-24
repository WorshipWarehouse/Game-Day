package app.gameday.core.update;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.gameday.core.Json;
import app.gameday.core.Semver;
import app.gameday.core.TimeFormat;

/**
 * Strict parser for GameDay release manifests.
 *
 * <p>Every field is validated before use: an update manifest is remote input that can eventually
 * cause an APK install, so a permissive parse is a security bug. Problems are collected rather than
 * thrown so the UI can show exactly what was wrong with a release feed.
 */
public final class UpdateManifestParser {

    private UpdateManifestParser() {
    }

    public static final class Result {
        public final UpdateManifest manifest;
        public final List<String> problems;

        Result(UpdateManifest manifest, List<String> problems) {
            this.manifest = manifest;
            this.problems = Collections.unmodifiableList(problems);
        }

        public boolean isSuccess() {
            return manifest != null && problems.isEmpty();
        }
    }

    /**
     * @param expectedAppId when non-null, the manifest must describe this application id.
     */
    public static Result parse(String text, String expectedAppId) {
        List<String> problems = new ArrayList<String>();
        Json json;
        try {
            json = Json.parse(text);
        } catch (RuntimeException e) {
            problems.add("Manifest is not valid JSON: " + e.getMessage());
            return new Result(null, problems);
        }
        if (!json.isObject()) {
            problems.add("Manifest root must be a JSON object.");
            return new Result(null, problems);
        }

        long schema = json.get("schemaVersion") == null
                ? -1 : json.get("schemaVersion").asLong(-1);
        if (schema != UpdateManifest.SCHEMA_VERSION) {
            problems.add("Unsupported manifest schemaVersion: " + schema);
        }

        String appId = trimmed(json, "appId");
        if (appId.isEmpty()) {
            problems.add("Missing appId.");
        } else if (expectedAppId != null && !expectedAppId.equals(appId)) {
            problems.add("Manifest is for " + appId + ", expected " + expectedAppId + ".");
        }

        ReleaseChannel channel = ReleaseChannel.fromId(trimmed(json, "channel"));
        if (!channel.id().equalsIgnoreCase(trimmed(json, "channel"))) {
            problems.add("Unknown channel: '" + trimmed(json, "channel") + "'.");
        }

        String versionName = trimmed(json, "versionName");
        Semver semver = Semver.parseOrNull(versionName);
        if (semver == null) {
            problems.add("Invalid versionName: '" + versionName + "'.");
        }

        long versionCode = json.get("versionCode") == null ? -1 : json.get("versionCode").asLong(-1);
        if (versionCode < 1) {
            problems.add("versionCode must be >= 1.");
        }

        int minSdk = json.get("minSdk") == null ? -1 : (int) json.get("minSdk").asLong(-1);
        if (minSdk < 1 || minSdk > 100) {
            problems.add("minSdk out of range: " + minSdk);
        }

        String apkUrl = trimmed(json, "apkUrl");
        String urlProblem = checkApkUrl(apkUrl);
        if (urlProblem != null) {
            problems.add(urlProblem);
        }

        String sha256 = trimmed(json, "sha256").toLowerCase(Locale.US);
        if (!sha256.matches("[0-9a-f]{64}")) {
            problems.add("sha256 must be 64 lowercase hex characters.");
        }

        long sizeBytes = json.get("sizeBytes") == null ? -1 : json.get("sizeBytes").asLong(-1);
        if (sizeBytes <= 0) {
            problems.add("sizeBytes must be > 0.");
        } else if (sizeBytes > UpdateManifest.MAX_APK_BYTES) {
            problems.add("APK is larger than the accepted maximum (" + sizeBytes + " bytes).");
        }

        String publishedAt = trimmed(json, "publishedAt");
        if (TimeFormat.parseIsoUtc(publishedAt, -1L) < 0) {
            problems.add("publishedAt must be an ISO-8601 UTC timestamp.");
        }

        if (!problems.isEmpty()) {
            return new Result(null, problems);
        }

        UpdateManifest manifest = new UpdateManifest(appId, channel, semver.toString(), versionCode,
                minSdk, apkUrl, sha256, sizeBytes, publishedAt, trimmed(json, "releaseNotes"));
        return new Result(manifest, problems);
    }

    private static String trimmed(Json json, String key) {
        Json v = json.get(key);
        return v == null ? "" : v.asString("").trim();
    }

    /** HTTPS only, a real host, no embedded credentials, no non-443 cleartext fallback. */
    static String checkApkUrl(String url) {
        if (url.isEmpty()) {
            return "Missing apkUrl.";
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return "apkUrl is not a valid URI.";
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return "apkUrl must use https.";
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            return "apkUrl must include a host.";
        }
        if (uri.getUserInfo() != null) {
            return "apkUrl must not embed credentials.";
        }
        return null;
    }
}
