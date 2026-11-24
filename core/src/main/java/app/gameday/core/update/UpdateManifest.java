package app.gameday.core.update;

import java.util.List;

import app.gameday.core.Json;

/**
 * A GameDay release descriptor, published by the project itself.
 *
 * <p>This is deliberately not a general "app store" format. It describes exactly one GameDay APK,
 * its integrity digest, its signing expectations and the Android level it needs, so an update can
 * be rejected before anything is downloaded or executed.
 */
public final class UpdateManifest {

    public static final int SCHEMA_VERSION = 1;

    /** Refuse anything absurd for a launcher-sized APK (defends against a hostile manifest). */
    public static final long MAX_APK_BYTES = 200L * 1024L * 1024L;

    private final String appId;
    private final ReleaseChannel channel;
    private final String versionName;
    private final long versionCode;
    private final int minSdk;
    private final String apkUrl;
    private final String sha256;
    private final long sizeBytes;
    private final String publishedAt;
    private final String releaseNotes;

    public UpdateManifest(String appId, ReleaseChannel channel, String versionName, long versionCode,
            int minSdk, String apkUrl, String sha256, long sizeBytes, String publishedAt,
            String releaseNotes) {
        this.appId = appId;
        this.channel = channel;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.minSdk = minSdk;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.publishedAt = publishedAt;
        this.releaseNotes = releaseNotes;
    }

    public String appId() {
        return appId;
    }

    public ReleaseChannel channel() {
        return channel;
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

    public String apkUrl() {
        return apkUrl;
    }

    public String sha256() {
        return sha256;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String publishedAt() {
        return publishedAt;
    }

    public String releaseNotes() {
        return releaseNotes == null ? "" : releaseNotes;
    }

    public Json toJson() {
        return Json.object()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("appId", appId)
                .put("channel", channel.id())
                .put("versionName", versionName)
                .put("versionCode", versionCode)
                .put("minSdk", minSdk)
                .put("apkUrl", apkUrl)
                .put("sha256", sha256)
                .put("sizeBytes", sizeBytes)
                .put("publishedAt", publishedAt)
                .put("releaseNotes", releaseNotes());
    }

    public static UpdateManifest fromJson(Json json, List<String> problems) {
        // Parsed through UpdateManifestParser so validation lives in exactly one place.
        UpdateManifestParser.Result r = UpdateManifestParser.parse(json.toJson(), null);
        problems.addAll(r.problems);
        return r.manifest;
    }
}
