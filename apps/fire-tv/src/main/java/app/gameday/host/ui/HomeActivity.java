package app.gameday.host.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.gameday.host.R;
import app.gameday.host.runtime.NativeHostRuntime;
import app.gameday.host.update.GameDayUpdateManager;
import app.gameday.host.update.NflUpdateManager;
import app.gameday.runtime.api.RuntimeResult;
import app.gameday.runtime.api.RuntimeState;

/**
 * The only screen a user normally sees: launch the guest, or explain precisely why it cannot start.
 *
 * <p>Every failure path leads to a screen with a next step (install from the store, grant install
 * permission, open recovery) rather than a dead end, and the auto-launch runs at most once per
 * screen instance so a returning user is never bounced back into the guest unexpectedly.
 */
public final class HomeActivity extends BaseActivity {

    public static final String EXTRA_AUTO_LAUNCH = "autoLaunch";
    public static final String EXTRA_UPDATE_MESSAGE = "updateMessage";

    private TextView statusView;
    private TextView messageView;
    private Ui.MenuRow primaryRow;
    private Ui.MenuRow installRow;
    private boolean autoLaunchHandled;
    private long lastLaunchAttemptMs;

    @Override
    protected int titleRes() {
        return R.string.app_name;
    }

    @Override
    protected int subtitleRes() {
        return R.string.app_tagline;
    }

    @Override
    protected void build(LinearLayout body) {
        statusView = Ui.body(this, "");
        statusView.setTextColor(Ui.TEXT);
        body.addView(statusView);

        messageView = Ui.caption(this, "");
        messageView.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        body.addView(messageView);

        primaryRow = Ui.menuRow(this, str(R.string.home_launch), "")
                .onClick(v -> primaryAction());
        body.addView(primaryRow.view);

        installRow = Ui.menuRow(this, str(R.string.home_install_action), installExplainer())
                .onClick(v -> openStoreForInstall());
        body.addView(installRow.view);

        body.addView(Ui.sectionHeader(this, "Maintenance"));
        body.addView(Ui.menuRow(this, str(R.string.home_updates), "")
                .onClick(v -> openUpdates(false)).view);
        body.addView(Ui.menuRow(this, str(R.string.home_compatibility), "")
                .onClick(v -> startActivity(new Intent(this, CompatibilityActivity.class))).view);
        body.addView(Ui.menuRow(this, str(R.string.home_diagnostics), "")
                .onClick(v -> startActivity(new Intent(this, DiagnosticsActivity.class))).view);
        body.addView(Ui.menuRow(this, str(R.string.home_settings), "")
                .onClick(v -> openSettings()).view);
        body.addView(Ui.menuRow(this, str(R.string.home_recovery), "")
                .onClick(v -> startActivity(new Intent(this, RecoveryActivity.class))).view);

        body.addView(Ui.divider(this));
        TextView disclaimer = Ui.caption(this, str(R.string.not_affiliated));
        body.addView(disclaimer);

        String extra = getIntent().getStringExtra(EXTRA_UPDATE_MESSAGE);
        if (extra != null && !extra.isEmpty()) {
            messageView.setText(extra);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        autoLaunchHandled = false;
    }

    private void refresh() {
        NflUpdateManager.Snapshot guest = services.nflUpdates().observe();
        GameDayUpdateManager.Status update = services.gameDayUpdates().storedStatus();

        StringBuilder status = new StringBuilder();
        if (guest.installed) {
            status.append(getString(R.string.status_installed_version, guest.describeVersion()));
        } else {
            status.append(str(R.string.status_not_installed));
        }
        status.append("  •  ").append(getString(R.string.status_runtime,
                services.runtime().descriptor().displayName()));

        if (update.isUpdateAvailable() && update.manifest != null) {
            status.append("  •  ").append(getString(R.string.status_gameday_update,
                    update.manifest.versionName()));
        }
        statusView.setText(status.toString());

        primaryRow.subtitle(guest.installed
                ? "Opens " + guest.versionName + " in place, with its own sign-in and player."
                : str(R.string.home_install_explainer));
        primaryRow.enabled(guest.installed && guest.signerTrusted && services.coordinator().state()
                != RuntimeState.UNAVAILABLE);
        primaryRow.titleView.setText(guest.installed
                ? str(R.string.home_launch)
                : str(R.string.home_install_required));
        installRow.view.setVisibility(guest.installed ? View.GONE : View.VISIBLE);

        if (!guest.signerTrusted) {
            showMessage("The installed NFL app's signing certificate does not match the one "
                    + "GameDay recorded. For safety GameDay will not launch it. Open Recovery for "
                    + "details.", Ui.DANGER);
        } else if (!update.message.isEmpty() && messageView.getText().length() == 0) {
            showMessage(update.message, Ui.MUTED);
        }

        boolean autoLaunch = getIntent().getBooleanExtra(EXTRA_AUTO_LAUNCH, false);
        if (autoLaunch && !autoLaunchHandled && guest.installed && guest.signerTrusted) {
            autoLaunchHandled = true;
            primaryAction();
        }
    }

    private void showMessage(String text, int color) {
        messageView.setText(text);
        messageView.setTextColor(color);
    }

    private void primaryAction() {
        NflUpdateManager.Snapshot guest = services.nflUpdates().observe();
        if (!guest.installed) {
            openStoreForInstall();
            return;
        }
        if (SystemClock.elapsedRealtime() - lastLaunchAttemptMs < 1500L) {
            return; // debounce a held SELECT button
        }
        lastLaunchAttemptMs = SystemClock.elapsedRealtime();
        showMessage(str(R.string.home_launching), Ui.MUTED);

        RuntimeResult<Void> result = services.coordinator().launchGuest();
        if (result.isOk()) {
            services.settings().setLastRuntimeError("");
            services.nflUpdates().noteSuccessfulLaunch();
            showMessage("", Ui.MUTED);
            return;
        }
        services.settings().setLastRuntimeError(result.error().toString());
        services.crashLog().record("launch failed: " + result.error());

        if (services.coordinator().isBlocked()) {
            showMessage(result.errorMessage(), Ui.DANGER);
            startActivity(new Intent(this, RecoveryActivity.class));
            return;
        }
        if ("HOST_NOT_INSTALLED".equals(result.errorCode())) {
            showMessage(result.errorMessage(), Ui.WARNING);
            openStoreForInstall();
            return;
        }
        showMessage(result.errorMessage() + " (" + result.errorCode() + ")", Ui.DANGER);
    }

    /** The store named here is the one this device actually has, not always the Appstore. */
    private String installExplainer() {
        return getString(NativeHostRuntime.AppStore.isInstalled(this)
                ? R.string.home_install_explainer
                : R.string.home_install_explainer_play);
    }

    private void openStoreForInstall() {
        boolean opened = services.nflUpdates().openStoreForInstall();
        showMessage(opened ? str(R.string.nfl_store_opened) : str(R.string.nfl_store_missing),
                opened ? Ui.MUTED : Ui.WARNING);
    }

    private void openUpdates(boolean checkNow) {
        Intent intent = new Intent(this, UpdateActivity.class);
        intent.putExtra(UpdateActivity.EXTRA_CHECK_NOW, checkNow);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // A clean return to GameDay after using the guest counts as a healthy session.
        services.crashGuard().recordHealthyExit(System.currentTimeMillis());
        services.nflUpdates().observe();
    }
}
