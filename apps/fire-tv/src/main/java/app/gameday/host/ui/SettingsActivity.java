package app.gameday.host.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import app.gameday.core.StorageBudget;
import app.gameday.core.TimeFormat;
import app.gameday.core.update.ReleaseChannel;
import app.gameday.host.BuildConfig;
import app.gameday.host.R;
import app.gameday.host.diagnostics.DiagnosticsExporter;
import app.gameday.host.update.GameDayUpdateManager;
import app.gameday.host.update.NflUpdateManager;
import app.gameday.runtime.api.HostAppInfo;
import app.gameday.runtime.api.ResourceSnapshot;
import app.gameday.runtime.api.StorageStats;

/**
 * Remote-first settings: one vertical list of focusable rows, no nested navigation, no text entry.
 * Destructive actions always require an explicit confirmation and never touch the NFL application's
 * data or sign-in.
 */
public final class SettingsActivity extends BaseActivity {

    private interface BooleanSetter {
        void set(boolean value);
    }

    private TextView installedVersionValue;
    private TextView compatibleVersionValue;
    private TextView lastNflCheckValue;
    private TextView gdVersionValue;
    private TextView gdLatestValue;
    private TextView hwDecodeValue;
    private TextView memoryValue;
    private TextView storageValue;
    private TextView deviceValue;
    private Ui.MenuRow channelRow;

    @Override
    protected int titleRes() {
        return R.string.settings_title;
    }

    @Override
    protected int subtitleRes() {
        return 0;
    }

    @Override
    protected void build(LinearLayout body) {
        body.addView(Ui.sectionHeader(this, str(R.string.settings_general)));

        addToggle(body, R.string.settings_auto_launch, R.string.settings_auto_launch_summary,
                services.settings().autoLaunch(), services.settings()::setAutoLaunch);
        addToggle(body, R.string.settings_resume, R.string.settings_resume_summary,
                services.settings().resumeSession(), services.settings()::setResumeSession);

        body.addView(Ui.sectionHeader(this, str(R.string.settings_nfl_updates)));
        addToggle(body, R.string.settings_auto_update_nfl,
                R.string.settings_auto_update_nfl_summary,
                services.settings().autoUpdateNfl(), services.settings()::setAutoUpdateNfl);
        installedVersionValue = addValueRow(body, str(R.string.settings_installed_version));
        compatibleVersionValue = addValueRow(body, str(R.string.settings_latest_compatible));
        lastNflCheckValue = addValueRow(body, str(R.string.settings_last_nfl_check));
        body.addView(Ui.menuRow(this, str(R.string.settings_check_nfl_updates),
                "Delegates to the Amazon Appstore, which is the authorized update path.")
                .onClick(v -> {
                    boolean ok = services.nflUpdates().openStoreForUpdates();
                    Ui.toast(this, ok ? str(R.string.nfl_store_opened) : str(R.string.nfl_store_missing));
                }).view);

        body.addView(Ui.sectionHeader(this, str(R.string.settings_gameday_updates)));
        addToggle(body, R.string.settings_auto_update_gameday,
                R.string.settings_auto_update_gameday_summary,
                services.settings().autoUpdateGameDay(), services.settings()::setAutoUpdateGameDay);
        gdVersionValue = addValueRow(body, "Installed GameDay");
        gdLatestValue = addValueRow(body, "Latest known GameDay");
        channelRow = Ui.menuRow(this, str(R.string.settings_channel), "")
                .onClick(v -> {
                    ReleaseChannel next = services.settings().channel() == ReleaseChannel.STABLE
                            ? ReleaseChannel.BETA : ReleaseChannel.STABLE;
                    services.settings().setChannel(next);
                    refresh();
                });
        body.addView(channelRow.view);
        body.addView(Ui.menuRow(this, str(R.string.settings_check_gameday_updates), "")
                .onClick(v -> {
                    Intent intent = new Intent(this, UpdateActivity.class);
                    intent.putExtra(UpdateActivity.EXTRA_CHECK_NOW, true);
                    startActivity(intent);
                }).view);

        body.addView(Ui.sectionHeader(this, str(R.string.settings_performance)));
        hwDecodeValue = addValueRow(body, str(R.string.settings_hw_decode));
        memoryValue = addValueRow(body, str(R.string.settings_memory));
        storageValue = addValueRow(body, str(R.string.settings_storage));

        body.addView(Ui.sectionHeader(this, str(R.string.settings_diagnostics)));
        deviceValue = addValueRow(body, str(R.string.settings_device));
        body.addView(Ui.menuRow(this, str(R.string.home_compatibility), "")
                .onClick(v -> startActivity(new Intent(this, CompatibilityActivity.class))).view);
        body.addView(Ui.menuRow(this, str(R.string.settings_export), "")
                .onClick(v -> exportReport()).view);
        body.addView(Ui.menuRow(this, str(R.string.home_diagnostics), "")
                .onClick(v -> startActivity(new Intent(this, DiagnosticsActivity.class))).view);

        body.addView(Ui.sectionHeader(this, str(R.string.settings_recovery)));
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
        body.addView(Ui.caption(this, str(R.string.not_affiliated)));
    }

    @Override
    protected void onRefresh() {
        refresh();
    }

    private void refresh() {
        NflUpdateManager.Snapshot guest = services.nflUpdates().observe();
        installedVersionValue.setText(guest.installed ? guest.describeVersion() : "not installed");
        compatibleVersionValue.setText(guest.describeLastKnownWorking());
        lastNflCheckValue.setText(TimeFormat.ago(System.currentTimeMillis(), guest.lastCheckMs));

        GameDayUpdateManager.Status update = services.gameDayUpdates().storedStatus();
        gdVersionValue.setText(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        gdLatestValue.setText(update.manifest == null
                ? (update.message.isEmpty() ? "unknown" : update.message)
                : update.manifest.versionName());
        channelRow.subtitle(services.settings().channel() == ReleaseChannel.BETA
                ? "Beta: prereleases included"
                : "Stable releases only");

        hwDecodeValue.setText(app.gameday.host.diagnostics.DeviceProbe.hardwareDecoderFor("video/hevc")
                ? "HEVC and AVC hardware decoders reported"
                : "No hardware HEVC decoder reported");
        ResourceSnapshot resources = services.runtime().resources().value();
        if (resources == null) {
            memoryValue.setText("unavailable");
        } else {
            memoryValue.setText("GameDay " + StorageBudget.format(resources.hostPssBytes())
                    + " • system free " + StorageBudget.format(resources.systemAvailableBytes()));
        }
        StorageStats storage = services.runtime().storage().value();
        storageValue.setText(storage == null ? "unavailable"
                : "GameDay data " + StorageBudget.format(storage.hostDataBytes())
                + " • cache " + StorageBudget.format(storage.hostCacheBytes())
                + " • free " + StorageBudget.format(storage.freeBytes()));

        HostAppInfo info = services.runtime().hostAppInfo().value();
        deviceValue.setText(android.os.Build.MODEL + " • "
                + app.gameday.host.diagnostics.DeviceProbe.fireOsVersion()
                + " • API " + android.os.Build.VERSION.SDK_INT
                + (info != null && info.isInstalled() ? " • NFL " + info.versionName() : ""));
    }

    private TextView addValueRow(LinearLayout body, String label) {
        LinearLayout row = Ui.valueRow(this, label, "");
        body.addView(row);
        // The value is the last child added by valueRow.
        return (TextView) row.getChildAt(1);
    }

    private void addToggle(LinearLayout body, int titleRes, int summaryRes, boolean initial,
            BooleanSetter setter) {
        final boolean[] state = {initial};
        Ui.MenuRow row = Ui.menuRow(this, str(titleRes), "");
        row.subtitle(str(summaryRes) + " — " + (state[0] ? str(R.string.on_off_on)
                : str(R.string.on_off_off)));
        row.onClick(v -> {
            state[0] = !state[0];
            setter.set(state[0]);
            row.subtitle(str(summaryRes) + " — " + (state[0] ? str(R.string.on_off_on)
                    : str(R.string.on_off_off)));
        });
        body.addView(row.view);
    }

    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(str(R.string.action_cancel), null)
                .setPositiveButton(str(R.string.action_confirm), (dialog, which) -> action.run())
                .show();
    }

    private void restartRuntime() {
        services.coordinator().clearFailures();
        services.coordinator().initialize();
        services.settings().setLastRuntimeError("");
        Ui.toast(this, str(R.string.recovery_restarted));
        refresh();
    }

    private void clearTemporaryFiles() {
        deleteContents(getCacheDir());
        deleteContents(new File(getExternalFilesDir(null), "updates"));
        Ui.toast(this, str(R.string.recovery_temp_cleared));
        refresh();
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
        recreate();
    }

    private void exportReport() {
        Ui.background(() -> {
            try {
                File[] files = new DiagnosticsExporter(this).export();
                final String path = files[0].getAbsolutePath();
                runOnUiThread(() -> Ui.toast(this,
                        getString(R.string.diagnostics_exported, path)));
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Export failed: " + e.getMessage()));
            }
        });
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
                // best effort: a locked file will be retried next time
            }
        }
    }
}
