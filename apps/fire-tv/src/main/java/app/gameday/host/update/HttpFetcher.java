package app.gameday.host.update;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

import app.gameday.core.Sha256;
import app.gameday.core.net.UrlPolicy;

/**
 * The only way GameDay reaches the network.
 *
 * <p>HTTPS only, allowlisted hosts, manual redirect validation, bounded response sizes and a hard
 * timeout. Downloads are hashed while streaming so a corrupted or substituted APK is detected before
 * it is ever handed to the installer.
 */
public final class HttpFetcher {

    public static final int DEFAULT_TIMEOUT_MS = 15_000;
    public static final int MAX_TEXT_BYTES = 512 * 1024;
    public static final long MAX_DOWNLOAD_BYTES = 200L * 1024L * 1024L;

    private static final int MAX_REDIRECTS = 4;

    public interface Progress {
        void onProgress(long bytesRead, long expectedBytes);
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final String body;
        public final File file;
        public final String sha256;
        public final long bytes;

        Result(boolean ok, String message, String body, File file, String sha256, long bytes) {
            this.ok = ok;
            this.message = message;
            this.body = body;
            this.file = file;
            this.sha256 = sha256;
            this.bytes = bytes;
        }

        public static Result error(String message) {
            return new Result(false, message, null, null, null, 0L);
        }
    }

    private final int timeoutMs;

    public HttpFetcher() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public HttpFetcher(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Result fetchText(String url) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            int code = connection.getResponseCode();
            if (code != 200) {
                return Result.error("HTTP " + code + " from " + UrlPolicy.sanitizeForLog(url));
            }
            InputStream in = new BufferedInputStream(connection.getInputStream());
            try {
                String body = readBounded(in, MAX_TEXT_BYTES);
                return new Result(true, "OK", body, null, null, body.length());
            } finally {
                closeQuietly(in);
            }
        } catch (IOException e) {
            return Result.error("Network error: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.error("Request failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Streams {@code url} to {@code destination}, hashing as it goes.
     *
     * @param expectedSize when > 0, the download is aborted once exceeded
     * @param expectedSha256 when non-null, the digest is compared before success is reported
     */
    public Result download(String url, File destination, long expectedSize, String expectedSha256,
            Progress progress) {
        HttpURLConnection connection = null;
        long limit = expectedSize > 0 ? expectedSize : MAX_DOWNLOAD_BYTES;
        if (limit > MAX_DOWNLOAD_BYTES) {
            return Result.error("Refusing a download larger than " + MAX_DOWNLOAD_BYTES + " bytes.");
        }
        MessageDigest digest = Sha256.newDigest();
        long total = 0L;
        try {
            connection = open(url);
            int code = connection.getResponseCode();
            if (code != 200) {
                return Result.error("HTTP " + code + " from " + UrlPolicy.sanitizeForLog(url));
            }
            InputStream in = new BufferedInputStream(connection.getInputStream());
            OutputStream out = new FileOutputStream(destination, false);
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > limit) {
                        throw new IOException("Download exceeded the expected size ("
                                + limit + " bytes)");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    if (progress != null) {
                        progress.onProgress(total, limit);
                    }
                }
                out.flush();
            } finally {
                closeQuietly(in);
                closeQuietly(out);
            }
        } catch (IOException e) {
            delete(destination);
            return Result.error("Download failed: " + e.getMessage());
        } catch (RuntimeException e) {
            delete(destination);
            return Result.error("Download failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        String actualSha = Sha256.toHex(digest.digest());
        if (expectedSize > 0 && total != expectedSize) {
            delete(destination);
            return Result.error("Downloaded " + total + " bytes but expected " + expectedSize + ".");
        }
        if (expectedSha256 != null && !Sha256.matches(expectedSha256, actualSha)) {
            delete(destination);
            return Result.error("Checksum mismatch: the download is not the published artifact.");
        }
        return new Result(true, "OK", null, destination, actualSha, total);
    }

    private HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!UrlPolicy.isAllowedUpdateUrl(current)) {
                throw new IOException("Blocked URL (must be https on an approved host): "
                        + UrlPolicy.sanitizeForLog(current));
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json, application/octet-stream");
            connection.setRequestProperty("User-Agent", "GameDay-Android/" + userAgentVersion());
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    throw new IOException("Redirect without a Location header");
                }
                if (!UrlPolicy.isRedirectAllowed(current, location)) {
                    throw new IOException("Refused redirect to "
                            + UrlPolicy.sanitizeForLog(location));
                }
                current = location;
                continue;
            }
            return connection;
        }
        throw new IOException("Too many redirects");
    }

    private static String userAgentVersion() {
        return "1.0";
    }

    private static String readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > max) {
                throw new IOException("Response larger than " + max + " bytes");
            }
        }
        return out.toString(String.valueOf(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void delete(File f) {
        if (f != null && f.exists() && !f.delete()) {
            // deliberately ignored: the next attempt overwrites the file
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    static String percent(long read, long total) {
        if (total <= 0) {
            return "?";
        }
        return String.format(Locale.US, "%d%%", Long.valueOf(read * 100 / total));
    }
}
