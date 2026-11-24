package app.gameday.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers used for APK and certificate integrity checks. */
public final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {
    }

    public static String of(byte[] data) {
        return toHex(digest(data));
    }

    public static String ofUtf8(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String ofStream(InputStream in) throws IOException {
        MessageDigest md = newDigest();
        byte[] buf = new byte[64 * 1024];
        int read;
        while ((read = in.read(buf)) != -1) {
            md.update(buf, 0, read);
        }
        return toHex(md.digest());
    }

    public static byte[] digest(byte[] data) {
        MessageDigest md = newDigest();
        return md.digest(data);
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    public static String normalizeHex(String hex) {
        if (hex == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (c == ':' || c == ' ' || c == '-' || c == '[' || c == ']') {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** Constant-time digest comparison. Malformed or empty expectations never match. */
    public static boolean matches(String expectedHex, String actualHex) {
        String e = normalizeHex(expectedHex);
        String a = normalizeHex(actualHex);
        if (e == null || a == null || e.isEmpty() || a.isEmpty() || e.length() != a.length()) {
            return false;
        }
        if (!isHex(e) || !isHex(a)) {
            return false;
        }
        return MessageDigest.isEqual(
                e.getBytes(StandardCharsets.US_ASCII), a.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean isHex(String s) {
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
