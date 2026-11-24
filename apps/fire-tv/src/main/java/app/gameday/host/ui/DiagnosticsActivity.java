package app.gameday.host.ui;

import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import app.gameday.core.Redactor;
import app.gameday.core.diagnostics.DiagnosticReport;
import app.gameday.host.R;
import app.gameday.host.diagnostics.DiagnosticsExporter;

/** Shows the redacted capability report and lets the user export or share it. */
public final class DiagnosticsActivity extends BaseActivity {

    private TextView reportView;
    private File lastExport;

    @Override
    protected int titleRes() {
        return R.string.diagnostics_title;
    }

    @Override
    protected int subtitleRes() {
        return 0;
    }

    @Override
    protected void build(LinearLayout body) {
        body.addView(Ui.caption(this, str(R.string.diagnostics_privacy)));

        Ui.MenuRow refresh = Ui.menuRow(this, str(R.string.action_refresh), "")
                .onClick(v -> loadReport());
        body.addView(refresh.view);
        body.addView(Ui.menuRow(this, str(R.string.action_export), "")
                .onClick(v -> export()).view);
        body.addView(Ui.menuRow(this, str(R.string.diagnostics_share), "")
                .onClick(v -> share()).view);

        reportView = Ui.caption(this, str(R.string.diagnostics_generating));
        reportView.setPadding(0, Ui.dp(this, 14), 0, 0);
        // Long reports scroll with the screen; a monospace face keeps columns readable.
        reportView.setTypeface(android.graphics.Typeface.MONOSPACE);
        reportView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        body.addView(reportView);
    }

    @Override
    protected void onRefresh() {
        loadReport();
    }

    private void loadReport() {
        Ui.background(() -> {
            DiagnosticReport report = new DiagnosticsExporter(this).build();
            final String text = report.renderText(Redactor.standard());
            runOnUiThread(() -> {
                if (reportView != null) {
                    reportView.setText(text);
                }
            });
        });
    }

    private void export() {
        Ui.background(() -> {
            try {
                File[] files = new DiagnosticsExporter(this).export();
                lastExport = files[0];
                final String path = files[0].getAbsolutePath();
                runOnUiThread(() -> Ui.toast(this,
                        getString(R.string.diagnostics_exported, path)));
            } catch (final Exception e) {
                runOnUiThread(() -> Ui.toast(this, "Export failed: " + e.getMessage()));
            }
        });
    }

    private void share() {
        File file = lastExport;
        if (file == null || !file.isFile()) {
            export();
            Ui.toast(this, "Export the report first, then share it.");
            return;
        }
        Uri uri = Uri.parse("content://" + getPackageName()
                + DiagnosticShareProviderSuffix.SUFFIX + "/" + file.getName());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(file.getName().endsWith(".json") ? "application/json" : "text/plain");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, str(R.string.diagnostics_share)));
        } catch (RuntimeException e) {
            Ui.toast(this, "No sharing app is available. The report is at " + file.getAbsolutePath());
        }
    }

    /** Kept as a tiny indirection so the provider authority stays in one place. */
    private static final class DiagnosticShareProviderSuffix {
        static final String SUFFIX = ".diagnostics";
    }
}
