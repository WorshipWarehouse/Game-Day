package app.gameday.host.diagnostics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import app.gameday.core.Json;
import app.gameday.core.Redactor;
import app.gameday.core.StorageBudget;
import app.gameday.core.TimeFormat;
import app.gameday.core.diagnostics.DiagnosticReport;
import app.gameday.host.AppServices;
import app.gameday.host.BuildConfig;
import app.gameday.host.runtime.RuntimeCatalog;
import app.gameday.runtime.api.Capability;
import app.gameday.runtime.api.ResourceSnapshot;
import app.gameday.runtime.api.RuntimeDescriptor;
import app.gameday.runtime.api.StorageStats;

/**
 * Builds the diagnostic report shown on screen and written to disk.
 *
 * <p>The report is deliberately about capabilities, not content: it never reads the guest's data,
 * credentials or playback state. Everything is redacted once, at render time, by the shared
 * {@link Redactor}. Files land in the app-specific external directory, which needs no storage
 * permission and disappears with the app.
 */
public final class DiagnosticsExporter {

    public static final String FILE_PREFIX = "gameday-diagnostics-";

    private final Context app;
    private final AppServices services;

    public DiagnosticsExporter(Context context) {
        this.app = context.getApplicationContext();
        this.services = AppServices.get(app);
    }

    public DiagnosticReport build() {
        long now = System.currentTimeMillis();
        DiagnosticReport report = new DiagnosticReport(app.getPackageName(),
                BuildConfig.VERSION_NAME, now, TimeFormat.isoUtc(now));

        report.field("deviceModel", Build.MODEL);
        report.field("deviceManufacturer", Build.MANUFACTURER);
        report.field("fireOsVersion", DeviceProbe.fireOsVersion());
        report.field("androidRelease", Build.VERSION.RELEASE);
        report.field("sdkInt", Build.VERSION.SDK_INT);
        report.field("buildFingerprint", Build.FINGERPRINT);
        report.field("abis", DeviceProbe.abis().toString());
        report.field("isTelevision", DeviceProbe.isTelevision(app));
        report.section("display", Json.object()
                .put("widthPx", DeviceProbe.displayWidth(app))
                .put("heightPx", DeviceProbe.displayHeight(app))
                .put("refreshHz", (long) DeviceProbe.refreshRate(app)));
        report.section("video", Json.object()
                .put("hardwareAvc", DeviceProbe.hardwareDecoderFor("video/avc"))
                .put("hardwareHevc", DeviceProbe.hardwareDecoderFor("video/hevc"))
                .put("decodersHevc", new JsonArrayBuilder()
                        .addAll(DeviceProbe.decodersFor("video/hevc")).build())
                .put("decodersAvc", new JsonArrayBuilder()
                        .addAll(DeviceProbe.decodersFor("video/avc")).build())
                .put("widevine", DeviceProbe.widevineSupported())
                .put("secureDecoder", DeviceProbe.secureVideoSupported()));

        report.section("gameday", Json.object()
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", buildVersionCode())
                .put("channel", services.settings().channel().id())
                .put("autoLaunch", services.settings().autoLaunch())
                .put("autoUpdateNfl", services.settings().autoUpdateNfl())
                .put("autoUpdateGameDay", services.settings().autoUpdateGameDay()));

        RuntimeDescriptor descriptor = services.runtime().descriptor();
        Json capabilities = Json.object();
        for (Capability c : services.runtime().capabilities()) {
            capabilities.put(c.capability().name(),
                    c.status().name() + (c.detail().isEmpty() ? "" : ": " + c.detail()));
        }
        report.section("runtime", Json.object()
                .put("id", descriptor.id())
                .put("name", descriptor.displayName())
                .put("kind", descriptor.kind().name())
                .put("state", services.coordinator().state().name())
                .put("consecutiveFailures", services.coordinator().consecutiveFailures())
                .put("history", new JsonArrayBuilder()
                        .addAll(services.coordinator().history()).build())
                .put("capabilities", capabilities)
                .put("lastError", services.settings().lastRuntimeError()));

        Json alternatives = Json.object();
        for (RuntimeCatalog.Entry entry : RuntimeCatalog.entries()) {
            alternatives.put(entry.id, Json.object()
                    .put("available", entry.available)
                    .put("kind", entry.kind.name())
                    .put("reason", entry.reason));
        }
        report.section("runtimeOptions", alternatives);

        Json host = Json.object()
                .put("packageName", BuildConfig.HOST_PACKAGE)
                .put("lastKnownWorking",
                        services.settings().getLong("nfl.lastKnownWorkingCode", 0L))
                .put("lastKnownWorkingName",
                        services.settings().getString("nfl.lastKnownWorkingName", ""))
                .put("lastCheckMs", services.settings().getLong("nfl.lastCheckMs", 0L))
                .put("lastObservedUpdateMs",
                        services.settings().getLong("nfl.lastObservedUpdateMs", 0L))
                .put("signerTrusted", services.nflUpdates().observe().signerTrusted);
        report.section("hostApp", host);

        report.section("updates", Json.object()
                .put("lastCheckMs", services.settings().getLong("gd.lastCheckMs", 0L))
                .put("failures", services.settings().failures())
                .put("knownVersionName",
                        services.settings().getString("gd.latest.versionName", ""))
                .put("lastInstallResult",
                        services.settings().getString("install.lastStatus", "")));

        ResourceSnapshot resources = services.runtime().resources().value();
        StorageStats storage = services.runtime().storage().value();
        report.section("resources", resources == null ? Json.nul() : Json.object()
                .put("hostPss", StorageBudget.format(resources.hostPssBytes()))
                .put("javaHeap", StorageBudget.format(resources.hostJavaHeapBytes()))
                .put("nativeHeap", StorageBudget.format(resources.hostNativeHeapBytes()))
                .put("systemAvailable", StorageBudget.format(resources.systemAvailableBytes()))
                .put("systemTotal", StorageBudget.format(resources.systemTotalBytes()))
                .put("hostCpuPercent", (long) resources.hostCpuPercent())
                .put("lowMemory", resources.isLowMemory()));
        report.section("storage", storage == null ? Json.nul() : Json.object()
                .put("hostData", StorageBudget.format(storage.hostDataBytes()))
                .put("hostCache", StorageBudget.format(storage.hostCacheBytes()))
                .put("guestApk", StorageBudget.format(storage.guestApkBytes()))
                .put("free", StorageBudget.format(storage.freeBytes())));

        report.section("network", Json.object()
                .put("connected", isNetworkConnected())
                .put("type", networkType()));

        Json crashes = Json.array();
        List<String> recent = services.crashLog().recent();
        for (String entry : recent) {
            crashes.add(entry.length() > 2000 ? entry.substring(0, 2000) : entry);
        }
        report.section("recentCrashes", crashes);

        return report;
    }

    /** Writes the report as JSON and as plain text; returns both files. */
    public File[] export() throws IOException {
        DiagnosticReport report = build();
        Redactor redactor = Redactor.standard();
        File dir = new File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir.getAbsolutePath());
        }
        String stamp = TimeFormat.isoUtc(System.currentTimeMillis()).replace(':', '-');
        File json = new File(dir, FILE_PREFIX + stamp + ".json");
        File text = new File(dir, FILE_PREFIX + stamp + ".txt");
        write(json, report.renderJson(redactor));
        write(text, report.renderText(redactor));
        return new File[]{json, text};
    }

    private long buildVersionCode() {
        try {
            android.content.pm.PackageInfo info = app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception e) {
            return -1L;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private String networkType() {
        ConnectivityManager cm =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return "unknown";
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return "offline";
        }
        return info.getTypeName() + "/" + info.getSubtypeName();
    }

    private static void write(File file, String content) throws IOException {
        FileOutputStream out = new FileOutputStream(file, false);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
    }

    /** Small helper so report sections can be built with arrays without verbose builder chains. */
    private static final class JsonArrayBuilder {
        private final Json array = Json.array();

        JsonArrayBuilder addAll(List<String> values) {
            if (values != null) {
                for (String v : values) {
                    array.add(v);
                }
            }
            return this;
        }

        Json build() {
            return array;
        }
    }
}
