package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.gameday.core.diagnostics.DiagnosticReport;
import app.gameday.core.net.UrlPolicy;
import org.junit.Test;

public class MiscCoreTest {

    @Test
    public void urlPolicyAcceptsHttpsOnly() {
        assertTrue(UrlPolicy.isSecure("https://api.github.com/x"));
        assertFalse(UrlPolicy.isSecure("http://api.github.com/x"));
        assertFalse(UrlPolicy.isSecure("https://user:pw@api.github.com/x"));
        assertFalse(UrlPolicy.isSecure("file:///etc/passwd"));
        assertFalse(UrlPolicy.isSecure(""));
        assertFalse(UrlPolicy.isSecure(null));
    }

    @Test
    public void urlPolicySanitizesForLogs() {
        assertEquals("https://api.github.com/repos/x",
                UrlPolicy.sanitizeForLog("https://api.github.com/repos/x?token=abc#frag"));
        assertEquals("[invalid url]", UrlPolicy.sanitizeForLog("http://insecure/x"));
    }

    @Test
    public void urlPolicyAllowlistAndRedirects() {
        assertTrue(UrlPolicy.isAllowedUpdateUrl("https://api.github.com/repos/a/b/releases"));
        assertTrue(UrlPolicy.isAllowedUpdateUrl(
                "https://github.com/a/b/releases/download/v1/app.apk"));
        assertFalse(UrlPolicy.isAllowedUpdateUrl("https://evil.example/app.apk"));
        assertFalse(UrlPolicy.isAllowedUpdateUrl("http://github.com/a/b"));
        assertTrue(UrlPolicy.isRedirectAllowed("https://api.github.com/x",
                "https://objects.githubusercontent.com/y"));
        assertFalse(UrlPolicy.isRedirectAllowed("https://api.github.com/x", "http://evil/y"));
    }

    @Test
    public void storageBudgetMathAndFormatting() {
        assertTrue(StorageBudget.hasRoomFor(100, 100));
        assertFalse(StorageBudget.hasRoomFor(101, 100));
        assertTrue(StorageBudget.hasRoomFor(0, 0));
        assertEquals(StorageBudget.MIN_MARGIN_BYTES, StorageBudget.requiredFree(0));
        assertEquals("512 B", StorageBudget.format(512));
        assertEquals("1 KB", StorageBudget.format(1024));
        assertEquals("1.5 MB", StorageBudget.format(1024L * 1024L * 3 / 2));
        assertEquals("unknown", StorageBudget.format(-1));
    }

    @Test
    public void timeFormattingRoundTripsUtc() {
        long t = 1_800_000_000_000L;
        String iso = TimeFormat.isoUtc(t);
        assertTrue(iso.endsWith("Z"));
        assertEquals(t, TimeFormat.parseIsoUtc(iso, -1));
        assertEquals(-1, TimeFormat.parseIsoUtc("not a date", -1));
        assertEquals(-1, TimeFormat.parseIsoUtc("2026-13-45T99:00:00Z", -1));
        assertEquals("2h 01m", TimeFormat.duration(7_260_000L));
        assertEquals("5m 03s", TimeFormat.duration(303_000L));
        assertEquals("42s", TimeFormat.duration(42_000L));
        assertEquals("never", TimeFormat.ago(1000, 0));
    }

    @Test
    public void diagnosticReportRendersJsonAndTextAndRedacts() {
        DiagnosticReport report = new DiagnosticReport("app.gameday.host", "1.0.0", 1_800_000_000_000L,
                "2027-01-15T08:00:00Z");
        report.field("deviceModel", "AFTMM");
        report.field("sdkInt", 25);
        report.section("runtime", Json.object()
                .put("id", "native-host")
                .put("sessionToken", "super-secret-value")
                .put("history", Json.array().add("READY").add("RUNNING")));

        String json = report.renderJson(Redactor.standard());
        assertTrue(json.contains("AFTMM"));
        assertTrue(json.contains("\"sessionToken\":\"[redacted]\""));
        assertFalse(json.contains("super-secret-value"));

        String text = report.renderText(Redactor.standard());
        assertTrue(text.contains("deviceModel: AFTMM"));
        assertTrue(text.contains("runtime.history: READY, RUNNING"));
        assertFalse(text.contains("super-secret-value"));
    }

    @Test
    public void jsonSectionFromMapPreservesValues() {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<String, String>();
        m.put("a", "1");
        m.put("b", null);
        Json j = DiagnosticReport.section(m);
        assertEquals("1", j.get("a").asString(""));
        assertTrue(j.get("b").isNull());
        assertNull(j.get("c"));
    }
}
