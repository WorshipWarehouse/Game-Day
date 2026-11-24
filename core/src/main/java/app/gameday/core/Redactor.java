package app.gameday.core;

import java.util.regex.Pattern;

/**
 * Removes credentials and personal identifiers from text before it reaches a log or an exported
 * diagnostic report.
 *
 * <p>The NFL application owns its own authentication: GameDay never reads its tokens, so in normal
 * operation nothing sensitive should ever be in GameDay's strings. This redactor is defence in
 * depth for the cases that do exist: a user pasting a URL into a support report, a stack trace that
 * echoes a request, or an HTTP error body that reflects an authorization header.
 */
public final class Redactor {

    public static final String MASK = "[redacted]";

    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\b");

    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\b(bearer)\\s+[A-Za-z0-9._~+/=-]{8,}");

    // Longest alternatives first: the regex engine takes the first alternative that matches, so
    // "sessiontoken" must be tried before "session" to mask the whole key name.
    private static final String SENSITIVE_KEYS =
            "access[_-]?token|accesstoken|refresh[_-]?token|refreshtoken"
            + "|auth[_-]?token|authtoken|id[_-]?token|idtoken"
            + "|session[_-]?token|sessiontoken|authorization|password|passwd|pwd"
            + "|api[_-]?key|apikey|secret|session[_-]?id|sessionid|session"
            + "|x-amz-signature|signature|sig|cookie|email|e-mail"
            + "|account[_-]?id|accountid|user[_-]?id|userid|username|uid|token";

    // Accepts both `key=value` and JSON's `"key":"value"` forms (the quote before the separator is
    // optional, and the value may itself be quoted).
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])(" + SENSITIVE_KEYS + ")(\"?)(\\s*[=:]\\s*)(\"?)([^\"\\s,;&}{)]+)");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+'\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Pattern LONG_BASE64 = Pattern.compile(
            "\\b[A-Za-z0-9+/]{40,}={0,2}\\b");

    private static final Pattern AUTH_HEADER_LINE = Pattern.compile(
            "(?im)^(\\s*(?:authorization|cookie|set-cookie)\\s*:\\s*).*$");

    private final boolean redactEmails;

    private Redactor(boolean redactEmails) {
        this.redactEmails = redactEmails;
    }

    public static Redactor standard() {
        return new Redactor(true);
    }

    /** Keeps e-mail addresses, for reports where a contact address is the point of the report. */
    public static Redactor withoutEmailRedaction() {
        return new Redactor(false);
    }

    public String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = AUTH_HEADER_LINE.matcher(input).replaceAll("$1" + MASK);
        out = JWT.matcher(out).replaceAll(MASK);
        out = BEARER.matcher(out).replaceAll("$1 " + MASK);
        out = KEY_VALUE.matcher(out).replaceAll("$1$2$3$4" + MASK);
        if (redactEmails) {
            out = EMAIL.matcher(out).replaceAll(MASK);
        }
        out = redactOpaqueBlobs(out);
        return out;
    }

    /**
     * Masks base64-ish blobs while preserving hex digests.
     *
     * <p>A 64-character lowercase hex string in a GameDay report is almost always a certificate or
     * APK digest, which is exactly what a support report needs, so those are left intact. Anything
     * else that is long and opaque is treated as a possible token and masked.
     */
    private static String redactOpaqueBlobs(String input) {
        java.util.regex.Matcher m = LONG_BASE64.matcher(input);
        StringBuffer sb = new StringBuffer(input.length());
        while (m.find()) {
            String blob = m.group();
            String replacement = isHexDigest(blob) ? blob : MASK;
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isHexDigest(String s) {
        if (s.length() < 32) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
