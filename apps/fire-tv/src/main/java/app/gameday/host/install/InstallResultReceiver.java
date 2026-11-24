package app.gameday.host.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import app.gameday.host.AppServices;

/**
 * Receives the platform's verdict on a staged install.
 *
 * <p>The outcome is persisted so the settings and update screens can show what happened after the
 * user returns from the system dialog, instead of silently assuming success.
 */
public final class InstallResultReceiver extends BroadcastReceiver {

    public static final String KEY_LAST_STATUS = "install.lastStatus";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        AppServices services = AppServices.get(context);
        String text;
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                text = "Install succeeded.";
                break;
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                text = "Waiting for the user to confirm the install.";
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                text = "Install was cancelled.";
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                text = "Install was blocked by the device.";
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                text = "Install conflicts with the installed package (signing identity or version).";
                break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                text = "Install failed: package is incompatible with this device.";
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                text = "Install failed: the package is invalid.";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                text = "Install failed: not enough storage.";
                break;
            default:
                text = "Install failed (status " + status + ").";
        }
        if (message != null && !message.isEmpty()) {
            text = text + " " + message;
        }
        services.settings().putString(KEY_LAST_STATUS, text);
        services.settings().commit();
        services.crashLog().record("install result: " + text);

        // Some platform builds do not show the confirmation themselves: they hand it back so the
        // caller can present it. Ignoring this extra means a staged install would never complete
        // on those devices, so the intent is forwarded whenever the platform asks for it.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The confirmation Intent travels in Intent.EXTRA_INTENT, not a PackageInstaller
            // constant; this is the documented contract for STATUS_PENDING_USER_ACTION.
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (RuntimeException e) {
                    services.crashLog().record("could not show install confirmation: "
                            + e.getMessage());
                }
            }
        }
    }
}
