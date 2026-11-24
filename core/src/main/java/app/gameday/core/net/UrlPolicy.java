package app.gameday.core.net;

import java.net.URI;

/**
 * Network policy: GameDay only ever talks HTTPS to a small set of project-controlled hosts.
 *
 * <p>Enforcing this in shared, unit-tested code means a redirect or a hostile update manifest
 * cannot downgrade GameDay's transport, and diagnostics can log a URL without leaking its query
 * string.
 */
public final class UrlPolicy {

    /** Hosts that may serve GameDay's own update metadata and APK. */
    private static final String[] UPDATE_HOSTS = {
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com"
    };

    private UrlPolicy() {
    }

    public static boolean isSecure(String url) {
        return parse(url) != null;
    }

    public static String host(String url) {
        URI uri = parse(url);
        return uri == null ? null : uri.getHost();
    }

    /** https://host/path with query, fragment and credentials removed. */
    public static String sanitizeForLog(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return "[invalid url]";
        }
        StringBuilder sb = new StringBuilder("https://").append(uri.getHost());
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            sb.append(path);
        }
        return sb.toString();
    }

    public static boolean isAllowedUpdateUrl(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.US);
        for (String allowed : UPDATE_HOSTS) {
            if (host.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /** Redirects are followed only between two HTTPS URLs on allowed hosts. */
    public static boolean isRedirectAllowed(String from, String to) {
        return isAllowedUpdateUrl(from) && isAllowedUpdateUrl(to);
    }

    private static URI parse(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            return null;
        }
        if (uri.getUserInfo() != null) {
            return null;
        }
        return uri;
    }
}
