package app.gameday.runtime.api;

/** Lifecycle state of a runtime backend as seen through the coordinator. */
public enum RuntimeState {

    /** Nothing has been probed yet. */
    UNINITIALIZED,
    /** Capability probing / backend initialization in progress. */
    PROBING,
    /** Backend initialized and able to launch the guest. */
    READY,
    /** A launch has been requested and has not yet completed. */
    LAUNCHING,
    /** The guest appears to be running. */
    RUNNING,
    /** Too many consecutive failures: automatic launch is disabled until recovery runs. */
    BLOCKED,
    /** The backend cannot run on this device at all. */
    UNAVAILABLE,
    /** The last operation failed but another attempt is allowed. */
    ERROR,
    /** GameDay is shutting down. */
    SHUTDOWN;

    public boolean canLaunch() {
        return this == READY || this == RUNNING;
    }
}
