package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedactorTest {

    private final Redactor redactor = Redactor.standard();

    @Test
    public void masksJwt() {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0."
                + "dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String out = redactor.redact("Authorization token was " + token);
        assertFalse(out.contains(token));
        assertTrue(out.contains(Redactor.MASK));
    }

    @Test
    public void masksBearerCredentials() {
        String out = redactor.redact("header Bearer abcdef1234567890 end");
        assertFalse(out.contains("abcdef1234567890"));
        assertTrue(out.contains("Bearer " + Redactor.MASK));
    }

    @Test
    public void masksSensitiveKeyValuePairs() {
        String out = redactor.redact(
                "authToken=abc123&access_token=zzz&password=hunter2&email=a@b.com");
        assertFalse(out.contains("abc123"));
        assertFalse(out.contains("zzz"));
        assertFalse(out.contains("hunter2"));
        assertFalse(out.contains("a@b.com"));
        assertTrue(out.contains("authToken=" + Redactor.MASK));
    }

    @Test
    public void masksJsonStyleSecrets() {
        String out = redactor.redact("{\"refresh_token\":\"s3cr3t-value\",\"note\":\"ok\"}");
        assertFalse(out.contains("s3cr3t-value"));
        assertTrue(out.contains("\"refresh_token\":\"" + Redactor.MASK + "\""));
        assertTrue(out.contains("\"note\":\"ok\""));
    }

    @Test
    public void masksStandaloneEmailAddresses() {
        String out = redactor.redact("contact: someone@example.org please");
        assertFalse(out.contains("someone@example.org"));
    }

    @Test
    public void masksOpaqueBase64ButKeepsHexDigests() {
        String digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        String base64 = "QWxhZGRpbjpvcGVuIHNlc2FtZSBhbmQgbW9yZSBwYWRkaW5nIGhlcmU=";
        String out = redactor.redact("cert=" + digest + " blob=" + base64);
        assertTrue("hex digests must survive for support", out.contains(digest));
        assertFalse(out.contains(base64));
    }

    @Test
    public void leavesOrdinaryDiagnosticsAlone() {
        String text = "deviceModel: AFTMM\nsdkInt: 25\nabis: armeabi-v7a\nminSdk: 25\n"
                + "hardwareDecodeAvc: true\nfreeBytes: 1234567\n";
        assertEquals(text, redactor.redact(text));
    }

    @Test
    public void masksWholeAuthorizationHeaderLine() {
        String out = redactor.redact("GET /v1/x\nAuthorization: Basic dXNlcjpwYXNz\nAccept: */*");
        assertFalse(out.contains("dXNlcjpwYXNz"));
        assertTrue(out.contains("Accept: */*"));
    }

    @Test
    public void handlesNullAndEmpty() {
        assertEquals(null, redactor.redact(null));
        assertEquals("", redactor.redact(""));
    }
}
