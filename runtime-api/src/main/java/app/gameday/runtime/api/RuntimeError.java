package app.gameday.runtime.api;

/** A runtime failure with a stable code (for support and tests) and a user-facing message. */
public final class RuntimeError {

    public static final String RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";
    public static final String INIT_FAILED = "INIT_FAILED";
    public static final String NOT_READY = "NOT_READY";
    public static final String HOST_NOT_INSTALLED = "HOST_NOT_INSTALLED";
    public static final String HOST_NOT_LAUNCHABLE = "HOST_NOT_LAUNCHABLE";
    public static final String LAUNCH_FAILED = "LAUNCH_FAILED";
    public static final String BLOCKED_BY_RECOVERY = "BLOCKED_BY_RECOVERY";
    public static final String UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION";
    public static final String INSTALL_FAILED = "INSTALL_FAILED";
    /** The platform needs the user to grant an install-related permission first. */
    public static final String PERMISSION_REQUIRED = "PERMISSION_REQUIRED";
    public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";
    public static final String STORAGE_INSUFFICIENT = "STORAGE_INSUFFICIENT";
    public static final String NETWORK_FAILED = "NETWORK_FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final String code;
    private final String message;

    public RuntimeError(String code, String message) {
        this.code = code;
        this.message = message == null ? "" : message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}
