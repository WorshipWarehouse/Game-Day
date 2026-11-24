package app.gameday.core.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateManifestParserTest {

    static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    static String manifest(String overrides) {
        String json = "{\"schemaVersion\":1,\"appId\":\"app.gameday.host\",\"channel\":\"stable\","
                + "\"versionName\":\"1.1.0\",\"versionCode\":10101,\"minSdk\":25,"
                + "\"apkUrl\":\"https://github.com/WorshipWarehouse/Game-Day/releases/download/"
                + "v1.1.0/gameday-1.1.0.apk\",\"sha256\":\"" + SHA + "\","
                + "\"sizeBytes\":1500000,\"publishedAt\":\"2026-11-24T00:00:00Z\","
                + "\"releaseNotes\":\"Faster start\"}";
        if (overrides == null) {
            return json;
        }
        return json.replace(overrides.split("=>")[0], overrides.split("=>")[1]);
    }

    @Test
    public void parsesValidManifest() {
        UpdateManifestParser.Result r =
                UpdateManifestParser.parse(manifest(null), "app.gameday.host");
        assertTrue(r.problems.toString(), r.isSuccess());
        UpdateManifest m = r.manifest;
        assertEquals("1.1.0", m.versionName());
        assertEquals(10101L, m.versionCode());
        assertEquals(25, m.minSdk());
        assertEquals(SHA, m.sha256());
        assertEquals(ReleaseChannel.STABLE, m.channel());
        assertEquals("Faster start", m.releaseNotes());
    }

    @Test
    public void rejectsCleartextUrl() {
        UpdateManifestParser.Result r = UpdateManifestParser.parse(
                manifest("https://github.com=>http://example.org"), "app.gameday.host");
        assertFalse(r.isSuccess());
        assertNull(r.manifest);
        assertTrue(r.problems.toString().contains("https"));
    }

    @Test
    public void rejectsUrlWithEmbeddedCredentials() {
        UpdateManifestParser.Result r = UpdateManifestParser.parse(
                manifest("https://github.com/WorshipWarehouse=>https://user:pw@github.com/x"),
                "app.gameday.host");
        assertFalse(r.isSuccess());
    }

    @Test
    public void rejectsShortOrNonHexDigest() {
        assertFalse(UpdateManifestParser.parse(manifest(SHA + "=>abc"), "app.gameday.host")
                .isSuccess());
        assertFalse(UpdateManifestParser.parse(manifest(SHA + "=>" + SHA.replace('a', 'z')),
                "app.gameday.host").isSuccess());
    }

    @Test
    public void rejectsWrongApplicationId() {
        UpdateManifestParser.Result r =
                UpdateManifestParser.parse(manifest(null), "app.gameday.compat.test");
        assertFalse(r.isSuccess());
        assertTrue(r.problems.toString().contains("expected"));
    }

    @Test
    public void rejectsUnknownChannelAndSchema() {
        assertFalse(UpdateManifestParser.parse(manifest("\"stable\"=>\"nightly\""),
                "app.gameday.host").isSuccess());
        assertFalse(UpdateManifestParser.parse(manifest("\"schemaVersion\":1=>\"schemaVersion\":9"),
                "app.gameday.host").isSuccess());
    }

    @Test
    public void rejectsMissingOrBadTimestamp() {
        assertFalse(UpdateManifestParser.parse(
                manifest("\"publishedAt\":\"2026-11-24T00:00:00Z\"=>\"yesterday\""),
                "app.gameday.host").isSuccess());
    }

    @Test
    public void rejectsAbsurdApkSize() {
        assertFalse(UpdateManifestParser.parse(manifest("\"sizeBytes\":1500000=>\"sizeBytes\":999999999"),
                "app.gameday.host").isSuccess());
        assertFalse(UpdateManifestParser.parse(manifest("\"sizeBytes\":1500000=>\"sizeBytes\":0"),
                "app.gameday.host").isSuccess());
    }

    @Test
    public void rejectsInvalidJsonAndNonObjectRoot() {
        assertFalse(UpdateManifestParser.parse("{not json", null).isSuccess());
        assertFalse(UpdateManifestParser.parse("[1,2,3]", null).isSuccess());
    }

    @Test
    public void rejectsBadVersionNameAndMinSdk() {
        assertFalse(UpdateManifestParser.parse(manifest("\"1.1.0\"=>\"1.x\""),
                "app.gameday.host").isSuccess());
        assertFalse(UpdateManifestParser.parse(manifest("\"minSdk\":25=>\"minSdk\":0"),
                "app.gameday.host").isSuccess());
    }

    @Test
    public void roundTripsToJson() {
        UpdateManifest m = UpdateManifestParser.parse(manifest(null), "app.gameday.host").manifest;
        assertNotNull(m);
        UpdateManifestParser.Result again =
                UpdateManifestParser.parse(m.toJson().toJson(), "app.gameday.host");
        assertTrue(again.isSuccess());
        assertEquals(m.versionCode(), again.manifest.versionCode());
    }
}
