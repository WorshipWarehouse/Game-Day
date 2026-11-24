package app.gameday.core;

/** Android versionCode arithmetic, kept walled off so update logic is testable off-device. */
public final class VersionCode {

    private VersionCode() {
    }

    public static int compare(long installed, long candidate) {
        return Long.compare(installed, candidate);
    }

    /** True when {@code candidate} can replace {@code installed} as a normal forward upgrade. */
    public static boolean isUpgrade(long installed, long candidate) {
        return candidate > installed;
    }

    /**
     * True when {@code candidate} is the same or a strictly newer version code.
     *
     * <p>Android only accepts an install whose versionCode is at least the installed one (older
     * codes require a downgrade flag that a normal app cannot set), so update planning uses this
     * predicate rather than {@link #isUpgrade(long, long)}.
     */
    public static boolean isInstallableOver(long installed, long candidate) {
        return candidate >= installed;
    }

    public static boolean isDowngrade(long installed, long candidate) {
        return candidate < installed;
    }
}
