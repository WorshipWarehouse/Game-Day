package app.gameday.host.ui;

import android.os.Build;
import android.widget.LinearLayout;

import java.util.List;

import app.gameday.core.StorageBudget;
import app.gameday.host.BuildConfig;
import app.gameday.host.R;
import app.gameday.host.diagnostics.DeviceProbe;
import app.gameday.host.install.PackageInstallerHelper;
import app.gameday.host.runtime.NativeHostRuntime;
import app.gameday.host.runtime.RuntimeCatalog;
import app.gameday.host.update.NflUpdateManager;
import app.gameday.runtime.api.Capability;
import app.gameday.runtime.api.HostAppInfo;
import app.gameday.runtime.api.RuntimeCapability;

/**
 * The verified compatibility matrix, measured on this device each time it opens.
 *
 * <p>Nothing here is hard-coded from a device list: every row is a probe with a pass/fail result and
 * the evidence that produced it, which is also what the exported report contains.
 */
public final class CompatibilityActivity extends BaseActivity {

    private LinearLayout checksContainer;

    @Override
    protected int titleRes() {
        return R.string.compat_title;
    }

    @Override
    protected int subtitleRes() {
        return 0;
    }

    @Override
    protected void build(LinearLayout body) {
        body.addView(Ui.menuRow(this, str(R.string.compat_refresh), "")
                .onClick(v -> render()).view);
        deviceInfo = Ui.caption(this, "");
        body.addView(deviceInfo);
        checksContainer = Ui.column(this);
        body.addView(checksContainer);
        render();
    }

    private android.widget.TextView deviceInfo;

    @Override
    protected void onRefresh() {
        render();
    }

    private void render() {
        checksContainer.removeAllViews();
        NflUpdateManager.Snapshot guest = services.nflUpdates().observe();
        HostAppInfo info = services.runtime().hostAppInfo().value();
        boolean installed = guest.installed;

        deviceInfo.setText(Build.MODEL + " • " + DeviceProbe.fireOsVersion() + " • API "
                + Build.VERSION.SDK_INT + " • " + DeviceProbe.abis() + " • "
                + DeviceProbe.summary(this).displaySummary());

        checksContainer.addView(Ui.sectionHeader(this, "Hosted application"));
        addCheck("NFL app installed", installed ? "version " + guest.describeVersion()
                : "not installed; the Amazon Appstore provides it", installed);
        addCheck("Launchable activity", guest.launchable ? "leanback launch intent found"
                : "no launch intent for this device", guest.launchable);
        addCheck("Signing identity", guest.signerTrusted
                ? (guest.signer.isEmpty() ? "not yet recorded" : "trusted: " + shortDigest(guest.signer))
                : "certificate does not match the recorded one", guest.signerTrusted);
        boolean apiOk = info == null || !info.isInstalled() || info.minSdk() <= Build.VERSION.SDK_INT;
        addCheck("Android level", info == null || !info.isInstalled() ? "unknown"
                : "app requires API " + info.minSdk() + ", device has " + Build.VERSION.SDK_INT,
                apiOk);
        boolean abiOk = DeviceProbe.abis().contains("armeabi-v7a")
                || DeviceProbe.abis().contains("arm64-v8a");
        addCheck("Application binary interface", DeviceProbe.abis() + " (32-bit environment "
                + (DeviceProbe.abis().contains("armeabi-v7a") ? "present" : "absent") + ")", abiOk);

        checksContainer.addView(Ui.sectionHeader(this, "Playback"));
        addCheck("Hardware AVC decoder", DeviceProbe.decodersFor("video/avc").toString(),
                DeviceProbe.hardwareDecoderFor("video/avc"));
        addCheck("Hardware HEVC decoder", DeviceProbe.decodersFor("video/hevc").toString(),
                DeviceProbe.hardwareDecoderFor("video/hevc"));
        addCheck("Widevine DRM", DeviceProbe.widevineSupported()
                ? "MediaDrm reports Widevine support" : "not reported by MediaDrm",
                DeviceProbe.widevineSupported());
        addCheck("Secure video path", DeviceProbe.secureVideoSupported()
                ? "a secure decoder is reported" : "no secure decoder reported",
                DeviceProbe.secureVideoSupported());

        checksContainer.addView(Ui.sectionHeader(this, "Device and permissions"));
        addCheck("Amazon Appstore", NativeHostRuntime.AppStore.isInstalled(this)
                ? "present" : "not found; store delegation unavailable",
                NativeHostRuntime.AppStore.isInstalled(this));
        addCheck("Install permission", PackageInstallerHelper.canRequestPackageInstalls(this)
                ? "granted (or not required on this Android version)"
                : "not granted; sideloaded updates need it",
                PackageInstallerHelper.canRequestPackageInstalls(this));
        addCheck("Usage access", services.runtime().hasUsageAccess()
                ? "granted; foreground detection available"
                : "not granted; launch state is not observable",
                services.runtime().hasUsageAccess());
        long free = DeviceProbe.availableStorageBytes(this);
        addCheck("Free storage", StorageBudget.format(free), free > 200L * 1024L * 1024L);
        addCheck("Hosted package", BuildConfig.HOST_PACKAGE, true);

        checksContainer.addView(Ui.sectionHeader(this, "Runtime capabilities"));
        List<Capability> capabilities = services.runtime().capabilities();
        if (capabilities.isEmpty()) {
            checksContainer.addView(Ui.caption(this, "Runtime not initialised yet."));
        } else {
            for (Capability capability : capabilities) {
                boolean essential = capability.capability() == RuntimeCapability.APP_LAUNCH
                        || capability.capability() == RuntimeCapability.APP_INFO
                        || capability.capability() == RuntimeCapability.HARDWARE_VIDEO_DECODE
                        || capability.capability() == RuntimeCapability.DRM_PROTECTED_PLAYBACK;
                if (!essential) {
                    continue;
                }
                addCheck(capability.capability().name().replace('_', ' ').toLowerCase(),
                        capability.status() + ": " + capability.detail(),
                        capability.status().isUsable());
            }
        }

        checksContainer.addView(Ui.sectionHeader(this, "Runtime options evaluated"));
        for (RuntimeCatalog.Entry entry : RuntimeCatalog.entries()) {
            addCheck(entry.name + (entry.available ? " (in use)" : " (not used)"),
                    entry.reason, entry.available);
        }
    }

    private void addCheck(String label, String message, boolean ok) {
        checksContainer.addView(Ui.statusRow(this, label, message, ok));
    }

    private static String shortDigest(String digest) {
        if (digest == null || digest.length() < 16) {
            return digest == null ? "" : digest;
        }
        return digest.substring(0, 16) + "…";
    }
}
