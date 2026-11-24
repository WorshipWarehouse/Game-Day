package app.gameday.host.ui;

import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.gameday.core.TimeFormat;
import app.gameday.core.update.UpdateManifest;
import app.gameday.host.R;
import app.gameday.host.install.PackageInstallerHelper;
import app.gameday.host.update.GameDayUpdateManager;
import app.gameday.host.update.HttpFetcher;

/**
 * GameDay's own update screen: what is installed, what is published, and the verified install path.
 * The NFL application's updates are separate and always go through the Amazon Appstore.
 */
public final class UpdateActivity extends BaseActivity {

    public static final String EXTRA_CHECK_NOW = "checkNow";

    private TextView statusView;
    private TextView notesView;
    private Ui.MenuRow installRow;
    private Ui.MenuRow permissionRow;
    private GameDayUpdateManager.Status current;

    @Override
    protected int titleRes() {
        return R.string.updates_title;
    }

    @Override
    protected int subtitleRes() {
        return 0;
    }

    @Override
    protected void build(LinearLayout body) {
        statusView = Ui.body(this, "");
        body.addView(statusView);

        body.addView(Ui.menuRow(this, str(R.string.settings_check_gameday_updates), "")
                .onClick(v -> runCheck()).view);

        installRow = Ui.menuRow(this, "Download and install", "")
                .onClick(v -> install());
        body.addView(installRow.view);
        installRow.enabled(false);

        permissionRow = Ui.menuRow(this, "Grant install permission", "")
                .onClick(v -> openInstallPermissionSettings());
        permissionRow.view.setVisibility(android.view.View.GONE);
        body.addView(permissionRow.view);

        body.addView(Ui.sectionHeader(this, str(R.string.updates_release_notes)));
        notesView = Ui.caption(this, "");
        body.addView(notesView);

        body.addView(Ui.divider(this));
        body.addView(Ui.caption(this, "The NFL app is updated separately by the Amazon Appstore. "
                + "GameDay never downloads the NFL app from anywhere else."));

        if (getIntent().getBooleanExtra(EXTRA_CHECK_NOW, false)) {
            runCheck();
        }
    }

    @Override
    protected void onRefresh() {
        render(services.gameDayUpdates().storedStatus());
    }

    private void runCheck() {
        statusView.setText(str(R.string.updates_checking));
        Ui.background(() -> {
            final GameDayUpdateManager.Status status = services.gameDayUpdates().check(true);
            runOnUiThread(() -> render(status));
        });
    }

    private void render(GameDayUpdateManager.Status status) {
        current = status;
        StringBuilder sb = new StringBuilder();
        sb.append(status.message);
        if (status.checkedAtMs > 0) {
            sb.append("\nLast checked ").append(TimeFormat.ago(System.currentTimeMillis(),
                    status.checkedAtMs)).append('.');
        }
        if (services.settings().failures() > 0) {
            sb.append("\nConsecutive failed checks: ").append(services.settings().failures())
                    .append('.');
        }
        statusView.setText(sb.toString());

        boolean canInstall = status.isUpdateAvailable() && status.manifest != null;
        installRow.enabled(canInstall);
        installRow.subtitle(canInstall
                ? "Version " + status.manifest.versionName() + " ("
                        + app.gameday.core.StorageBudget.format(status.manifest.sizeBytes()) + ")"
                : "");
        boolean needsPermission = status.phase == GameDayUpdateManager.Phase.PERMISSION_REQUIRED;
        permissionRow.view.setVisibility(needsPermission ? android.view.View.VISIBLE
                : android.view.View.GONE);

        if (status.manifest != null) {
            notesView.setText(status.manifest.releaseNotes().isEmpty()
                    ? "No release notes were published." : status.manifest.releaseNotes());
        } else {
            notesView.setText("");
        }
    }

    private void install() {
        if (current == null || current.manifest == null) {
            return;
        }
        final UpdateManifest manifest = current.manifest;
        final long started = SystemClock.elapsedRealtime();
        installRow.enabled(false);
        statusView.setText(str(R.string.updates_downloading));
        Ui.background(() -> {
            GameDayUpdateManager.Status result = services.gameDayUpdates().downloadAndInstall(
                    manifest, (bytesRead, expected) -> {
                        if (expected <= 0) {
                            return;
                        }
                        final int percent = (int) Math.min(100L, bytesRead * 100L / expected);
                        runOnUiThread(() -> {
                            if (statusView != null) {
                                statusView.setText(getString(R.string.updates_downloading, percent));
                            }
                        });
                    });
            final long elapsed = SystemClock.elapsedRealtime() - started;
            runOnUiThread(() -> {
                render(result);
                if (result.phase == GameDayUpdateManager.Phase.INSTALLING) {
                    Ui.toast(UpdateActivity.this, str(R.string.updates_staged));
                }
                services.crashLog().record("gameday update attempt finished in "
                        + elapsed + " ms with " + result.phase);
            });
        });
    }

    private void openInstallPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (RuntimeException ignored) {
                Ui.toast(this, str(R.string.updates_permission_needed));
            }
        }
    }
}
