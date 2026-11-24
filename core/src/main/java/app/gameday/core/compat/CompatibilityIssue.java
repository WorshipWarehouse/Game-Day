package app.gameday.core.compat;

/** One compatibility finding, with a stable machine-readable code and a user-facing message. */
public final class CompatibilityIssue {

    public static final String PACKAGE_MISSING = "PACKAGE_MISSING";
    public static final String PACKAGE_MISMATCH = "PACKAGE_MISMATCH";
    public static final String VERSION_DOWNGRADE = "VERSION_DOWNGRADE";
    public static final String MIN_SDK_TOO_HIGH = "MIN_SDK_TOO_HIGH";
    public static final String NO_COMPATIBLE_ABI = "NO_COMPATIBLE_ABI";
    public static final String SIGNER_MISSING = "SIGNER_MISSING";
    public static final String SIGNER_MISMATCH = "SIGNER_MISMATCH";
    public static final String NO_LAUNCH_INTENT = "NO_LAUNCH_INTENT";
    public static final String STORAGE_LOW = "STORAGE_LOW";
    public static final String FEATURE_MISSING = "FEATURE_MISSING";
    public static final String TARGET_SDK_LEGACY = "TARGET_SDK_LEGACY";
    public static final String NOT_TELEVISION = "NOT_TELEVISION";
    public static final String BASE_MISSING = "BASE_MISSING";
    public static final String MULTIPLE_BASE = "MULTIPLE_BASE";
    public static final String SPLIT_NAME_DUPLICATE = "SPLIT_NAME_DUPLICATE";
    public static final String SPLIT_PACKAGE_MISMATCH = "SPLIT_PACKAGE_MISMATCH";
    public static final String SPLIT_VERSION_MISMATCH = "SPLIT_VERSION_MISMATCH";
    public static final String SPLIT_SIGNER_MISMATCH = "SPLIT_SIGNER_MISMATCH";
    public static final String EMPTY_APK_SET = "EMPTY_APK_SET";

    private final String code;
    private final IssueSeverity severity;
    private final String message;

    public CompatibilityIssue(String code, IssueSeverity severity, String message) {
        this.code = code;
        this.severity = severity;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public IssueSeverity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return severity + " " + code + ": " + message;
    }
}
