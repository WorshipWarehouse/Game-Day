package app.gameday.runtime.api;

/** How a capability is available on this device, right now. */
public enum CapabilityStatus {

    /** Verified working by this backend. */
    SUPPORTED,
    /** The backend cannot provide it at all. */
    UNSUPPORTED,
    /** Possible, but a runtime permission or special access has not been granted. */
    REQUIRES_PERMISSION,
    /** Possible, but the user must complete an action (for example a store install). */
    REQUIRES_USER_ACTION,
    /** The backend cannot determine this on the current device. */
    UNKNOWN;

    public boolean isUsable() {
        return this == SUPPORTED;
    }
}
