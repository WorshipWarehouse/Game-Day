package app.gameday.host.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.gameday.host.AppServices;
import app.gameday.host.R;
import app.gameday.host.update.GameDayUpdateManager;
import app.gameday.host.update.NflUpdateManager;
import app.gameday.runtime.api.RuntimeResult;

/**
 * Branded start screen. Probes capabilities, records the guest's installed state, runs the rate
 * limited GameDay update check, then routes to the home screen, safe mode, or straight into the
 * guest.
 *
 * <p>All of that happens on a background thread; the splash is shown for a minimum of 600 ms so the
 * transition does not flash, and never longer than the work takes.
 */
public final class StartupActivity extends Activity {

    private static final long MIN_SPLASH_MS = 600L;

    private TextView statusView;
    private AppServices services;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        services = AppServices.get(this);
        FormFactor form = FormFactor.detect(this);
        Ui.applyWindowMode(this, form);
        setContentView(buildSplash(form));
        runInitialization();
    }

    private LinearLayout buildSplash(FormFactor form) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, form.sidePaddingDp()), 0, Ui.dp(this, form.sidePaddingDp()), 0);

        TextView brand = new TextView(this);
        brand.setText("GameDay");
        brand.setTextColor(Ui.TEXT);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, form.brandTextSizeSp());
        brand.setIncludeFontPadding(false);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView tagline = new TextView(this);
        tagline.setText(R.string.app_tagline);
        tagline.setTextColor(Ui.MUTED);
        tagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        root.addView(tagline);

        statusView = new TextView(this);
        statusView.setText(R.string.startup_initializing);
        statusView.setTextColor(Ui.ACCENT);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.TRANSPARENT);
        root.addView(divider, new LinearLayout.LayoutParams(1, Ui.dp(this, 40)));
        return root;
    }

    private void runInitialization() {
        final long startedAt = SystemClock.elapsedRealtime();
        Ui.background(() -> {
            final AppServices services = this.services;
            final long now = System.currentTimeMillis();
            final boolean safeMode = services.crashGuard().shouldEnterSafeMode(now);

            setStatus(R.string.startup_probing_runtime);
            final RuntimeResult<Void> init = services.coordinator().initialize();

            final NflUpdateManager.Snapshot guest = services.nflUpdates().observe();

            // Startup never waits on the network. The cached status is shown immediately and the
            // real check (which can take seconds on a slow link) runs after routing.
            final GameDayUpdateManager.Status finalUpdate =
                    services.gameDayUpdates().storedStatus();

            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            if (elapsed < MIN_SPLASH_MS) {
                try {
                    Thread.sleep(MIN_SPLASH_MS - elapsed);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            runOnUiThread(() -> route(safeMode, init, guest, finalUpdate));
        });
    }

    private void route(boolean safeMode, RuntimeResult<Void> init, NflUpdateManager.Snapshot guest,
            GameDayUpdateManager.Status update) {
        if (isFinishing()) {
            return;
        }
        if (safeMode) {
            Intent recovery = new Intent(this, RecoveryActivity.class);
            recovery.putExtra(RecoveryActivity.EXTRA_SAFE_MODE, true);
            startActivity(recovery);
            finish();
            return;
        }

        Intent home = new Intent(this, HomeActivity.class);
        if (update != null) {
            home.putExtra(HomeActivity.EXTRA_UPDATE_MESSAGE, update.message);
        }
        boolean autoLaunch = services.settings().autoLaunch()
                && init.isOk()
                && guest.installed
                && guest.launchable
                && guest.signerTrusted;
        home.putExtra(HomeActivity.EXTRA_AUTO_LAUNCH, autoLaunch);
        startActivity(home);
        finish();
        checkForUpdatesInBackground();
    }

    /**
     * Runs the rate-limited release check off the startup path. The result is persisted by the
     * manager, so whichever screen reads {@code storedStatus()} next shows it; nothing here touches
     * the UI thread, and the thread is a daemon so it cannot keep the process alive.
     */
    private void checkForUpdatesInBackground() {
        if (!services.gameDayUpdates().shouldAutoCheck(System.currentTimeMillis())) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                services.gameDayUpdates().check(false);
            } catch (RuntimeException e) {
                services.crashLog().record("background update check failed: " + e.getMessage());
            }
        }, "gameday-release-check");
        worker.setDaemon(true);
        worker.start();
    }

    private void setStatus(final int stringRes) {
        runOnUiThread(() -> {
            if (statusView != null) {
                statusView.setText(stringRes);
            }
        });
    }
}
