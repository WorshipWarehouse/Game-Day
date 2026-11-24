package app.gameday.host.install;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Hands a verified APK set to Android's own installer.
 *
 * <p>An ordinary app cannot install silently: the platform shows its confirmation dialog (or the
 * Fire TV equivalent). GameDay therefore stages a {@code PackageInstaller} session, commits it, and
 * reports the user's decision back through {@link InstallResultReceiver}. Staging keeps the APK
 * under GameDay's control until the moment of install, so the verified bytes are the installed
 * bytes.
 */
public final class PackageInstallerHelper {

    public static final String ACTION_INSTALL_RESULT = "app.gameday.host.action.INSTALL_RESULT";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_PACKAGE = "package";

    private PackageInstallerHelper() {
    }

    public static final class Result {
        private final boolean ok;
        private final boolean permissionNeeded;
        private final String message;

        Result(boolean ok, boolean permissionNeeded, String message) {
            this.ok = ok;
            this.permissionNeeded = permissionNeeded;
            this.message = message;
        }

        public boolean ok() {
            return ok;
        }

        public boolean permissionNeeded() {
            return permissionNeeded;
        }

        public String message() {
            return message;
        }
    }

    public static boolean canRequestPackageInstalls(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return true; // legacy global "unknown sources" setting
        }
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param inheritExisting true when upgrading an installed package, so its data and splits are
     *     retained; false for a fresh install
     */
    public static Result install(Context context, List<File> apks, boolean inheritExisting) {
        if (apks == null || apks.isEmpty()) {
            return new Result(false, false, "No APKs to install.");
        }
        if (!canRequestPackageInstalls(context)) {
            return new Result(false, true,
                    "Android needs permission to install updates from GameDay.");
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            int mode = inheritExisting
                    ? PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
                    : PackageInstaller.SessionParams.MODE_FULL_INSTALL;
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(mode);
            // setInstallReason() is API 26; calling it unguarded throws NoSuchMethodError on
            // Fire OS 6 (API 25), which would break every staged install on the target device.
            if (Build.VERSION.SDK_INT >= 26) {
                params.setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER);
            }
            sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try {
                long total = 0L;
                for (File f : apks) {
                    total += f.length();
                }
                session.setStagingProgress(0f);
                long written = 0L;
                for (int i = 0; i < apks.size(); i++) {
                    File apk = apks.get(i);
                    String name = i == 0 ? "base.apk" : "split-" + i + ".apk";
                    InputStream in = new FileInputStream(apk);
                    OutputStream out = session.openWrite(name, 0L, apk.length());
                    try {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            written += read;
                            if (total > 0) {
                                session.setStagingProgress((float) written / (float) total);
                            }
                        }
                        out.flush();
                    } finally {
                        closeQuietly(in);
                        closeQuietly(out);
                    }
                }
                Intent callback = new Intent(context, InstallResultReceiver.class);
                callback.setAction(ACTION_INSTALL_RESULT);
                callback.putExtra(EXTRA_PACKAGE, apks.get(0).getName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback, flags);
                session.commit(pending.getIntentSender());
            } finally {
                session.close();
            }
            return new Result(true, false, "Install session " + sessionId + " committed.");
        } catch (IOException | RuntimeException e) {
            if (sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId);
                } catch (RuntimeException ignored) {
                    // nothing to clean up
                }
            }
            return new Result(false, false, "Could not stage the install: " + e.getMessage());
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
}
