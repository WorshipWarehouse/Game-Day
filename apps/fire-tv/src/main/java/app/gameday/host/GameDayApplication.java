package app.gameday.host;

import android.app.Application;

import app.gameday.host.diagnostics.CrashLog;

/**
 * Process entry point.
 *
 * <p>Two things happen here and nothing else: the service graph is created, and a boot is recorded
 * for the crash-loop guard. Keeping {@code onCreate} cheap matters on a 1.5 GB device where the
 * launcher must appear in under three seconds- nothing here touches the network or the disk beyond
 * a single SharedPreferences read.
 */
public final class GameDayApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppServices services = AppServices.init(this);

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new CrashLog.Handler(services.crashLog(), previous));

        services.crashGuard().recordBoot(System.currentTimeMillis());
    }
}
