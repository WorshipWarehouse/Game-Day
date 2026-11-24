package app.gameday.host;

import android.content.Context;

import app.gameday.core.CrashGuard;
import app.gameday.host.diagnostics.CrashLog;
import app.gameday.host.diagnostics.DeviceProbe;
import app.gameday.host.runtime.NativeHostRuntime;
import app.gameday.host.settings.SettingsStore;
import app.gameday.host.update.GameDayUpdateManager;
import app.gameday.host.update.NflUpdateManager;
import app.gameday.runtime.api.RuntimeCoordinator;

/**
 * Manual dependency graph for the Fire TV app.
 *
 * <p>GameDay has no DI framework: the graph is four objects with a fixed creation order, and a
 * hand-written singleton keeps the APK small and startup deterministic. Everything here is created
 * lazily except the settings store, which the crash guard needs immediately.
 */
public final class AppServices {

    private static volatile AppServices instance;

    private final Context appContext;
    private final SettingsStore settings;
    private final CrashLog crashLog;
    private final CrashGuard crashGuard;
    private final NativeHostRuntime runtime;
    private final RuntimeCoordinator coordinator;
    private final NflUpdateManager nflUpdates;
    private final GameDayUpdateManager gameDayUpdates;

    private AppServices(Context context) {
        this.appContext = context.getApplicationContext();
        this.settings = new SettingsStore(appContext);
        this.crashLog = new CrashLog(appContext);
        this.crashGuard = new CrashGuard(settings);
        this.runtime = new NativeHostRuntime(appContext);
        this.coordinator = new RuntimeCoordinator(runtime, 3);
        this.nflUpdates = new NflUpdateManager(appContext, settings, runtime);
        this.gameDayUpdates = new GameDayUpdateManager(appContext, settings, crashLog);
    }

    public static AppServices init(Context context) {
        AppServices local = instance;
        if (local == null) {
            synchronized (AppServices.class) {
                local = instance;
                if (local == null) {
                    local = new AppServices(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public static AppServices get(Context context) {
        AppServices local = instance;
        return local != null ? local : init(context);
    }

    public Context context() {
        return appContext;
    }

    public SettingsStore settings() {
        return settings;
    }

    public CrashLog crashLog() {
        return crashLog;
    }

    public CrashGuard crashGuard() {
        return crashGuard;
    }

    public NativeHostRuntime runtime() {
        return runtime;
    }

    public RuntimeCoordinator coordinator() {
        return coordinator;
    }

    public NflUpdateManager nflUpdates() {
        return nflUpdates;
    }

    public GameDayUpdateManager gameDayUpdates() {
        return gameDayUpdates;
    }

    public DeviceProbe.DeviceSummary deviceSummary() {
        return DeviceProbe.summary(appContext);
    }
}
