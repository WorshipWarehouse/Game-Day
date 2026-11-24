package app.gameday.runtime.api;

/**
 * Describes the guest application GameDay hosts: which package, which signing identity it must
 * present, and which version codes are acceptable.
 */
public final class HostAppSpec {

    private final String packageName;
    private final String displayName;
    private final String expectedSignerSha256;
    private final long minimumVersionCode;
    private final boolean requireTelevision;

    public HostAppSpec(String packageName, String displayName, String expectedSignerSha256,
            long minimumVersionCode, boolean requireTelevision) {
        this.packageName = packageName;
        this.displayName = displayName;
        this.expectedSignerSha256 = expectedSignerSha256;
        this.minimumVersionCode = minimumVersionCode;
        this.requireTelevision = requireTelevision;
    }

    public String packageName() {
        return packageName;
    }

    public String displayName() {
        return displayName;
    }

    /** Lowercase hex SHA-256 of the signing certificate, or null when identity is not pinned here. */
    public String expectedSignerSha256() {
        return expectedSignerSha256;
    }

    public long minimumVersionCode() {
        return minimumVersionCode;
    }

    public boolean requireTelevision() {
        return requireTelevision;
    }
}
