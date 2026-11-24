package app.gameday.host.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import app.gameday.host.R;

/**
 * Recovery and safe mode.
 *
 * <p>Safe mode is entered when the crash guard sees repeated startup failures; it stops automatic
 * guest launches and offers recovery actions. Nothing here deletes NFL application data, and the
 * one destructive action (reset GameDay data) is clearly labelled and confirmed.
 */
public final class RecoveryActivity extends BaseActivity {

    public static final String EXTRA_SAFE_MODE = "safeMode";

    private TextView introView;
    private TextView historyView;

    @Override
    protected int titleRes() {
        return R.string.recovery_title;
    }

    @Override
    protected int subtitleRes() {
        return 0;
    }

    @Override
    protected void build(LinearLayout body) {
        introView = Ui.body(this, "");
        introView.setTextColor(Ui.WARNING);
        body.addView(introView);

        body.addView(Ui.menuRow(this, str(R.string.settings_restart_runtime), "")
                .onClick(v -> restartRuntime()).view);
        body.addView(Ui.menuRow(this, str(R.string.settings_clear_temp), "")
                .onClick(v -> clearTemporaryFiles()).view);
        body.addView(Ui.menuRow(this, str(R.string.settings_reset_config), "")
                .onClick(v -> confirm(str(R.string.settings_reset_config),
                        "Restore GameDay's default settings? The NFL app is not affected.",
                        this::resetConfiguration)).view);
        body.addView(Ui.menuRow(this, str(R.string.settings_reset_data),
                str(R.string.settings_reset_data_warning))
                .onClick(v -> confirm(str(R.string.settings_reset_data),
                        str(R.string.settings_reset_data_warning), this::resetGameDayData)).view);
        body.addView(Ui.divider(this));
        body.addView(Ui.menuRow(this, str(R.string.home_compatibility), "")
                .onClick(v -> startActivity(new Intent(this, CompatibilityActivity.class))).view);
        body.addView(Ui.menuRow(this, str(R.string.home_diagnostics), "")
                .onClick(v -> startActivity(new Intent(this, DiagnosticsActivity.class))).view);
        body.addView(Ui.menuRow(this, str(R.string.home_settings), "")
                .onClick(v -> openSettings()).view);
        body.addView(Ui.menuRow(this, "Back to GameDay", "")
                .onClick(v -> {
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                }).view);

        body.addView(Ui.sectionHeader(this, "Recent failures"));
        historyView = Ui.caption(this, "");
        body.addView(historyView);
    }

    @Override
    protected void onRefresh() {
        boolean safeMode = getIntent().getBooleanExtra(EXTRA_SAFE_MODE, false)
                || services.crashGuard().shouldEnterSafeMode(System.currentTimeMillis());
        introView.setText(safeMode
                ? str(R.string.recovery_safe_mode_title) + "\n" + str(R.string.recovery_safe_mode_body)
                : "Recovery tools. Nothing here deletes NFL app data or sign-in.");

        StringBuilder sb = new StringBuilder();
        String lastError = services.settings().lastRuntimeError();
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("Last runtime error: ").append(lastError).append('\n');
        }
        List<String> crashes = services.crashLog().recent();
        if (crashes.isEmpty()) {
            sb.append("No GameDay crashes have been recorded.");
        } else {
            for (int i = 0; i < Math.min(3, crashes.size()); i++) {
                String entry = crashes.get(i);
                sb.append(entry.length() > 400 ? entry.substring(0, 400) + "…" : entry)
                        .append("\n\n");
            }
        }
        historyView.setText(sb.toString().trim());
    }

    private void restartRuntime() {
        services.coordinator().clearFailures();
        services.coordinator().initialize();
        services.settings().setLastRuntimeError("");
        Ui.toast(this, str(R.string.recovery_restarted));
        onRefresh();
    }

    private void clearTemporaryFiles() {
        deleteContents(getCacheDir());
        Ui.toast(this, str(R.string.recovery_temp_cleared));
        onRefresh();
    }

    private void resetConfiguration() {
        services.settings().resetToDefaults();
        Ui.toast(this, str(R.string.recovery_config_reset));
        recreate();
    }

    private void resetGameDayData() {
        services.settings().resetToDefaults();
        services.crashLog().clear();
        deleteContents(getCacheDir());
        deleteContents(getFilesDir());
        deleteContents(new File(getExternalFilesDir(null), "diagnostics"));
        services.coordinator().clearFailures();
        Ui.toast(this, str(R.string.recovery_data_reset));
        onRefresh();
    }

    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(str(R.string.action_cancel), null)
                .setPositiveButton(str(R.string.action_confirm), (dialog, which) -> action.run())
                .show();
    }

    private static void deleteContents(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteContents(child);
            }
            if (!child.delete()) {
                // best effort
            }
        }
    }
}
