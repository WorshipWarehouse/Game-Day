package app.gameday.runtime.api;

import java.util.List;

/**
 * The contract every runtime backend implements.
 *
 * <p>Backends must never claim a capability they have not verified on the current device. A
 * capability that cannot be provided is reported as {@link CapabilityStatus#UNSUPPORTED} with a
 * human-readable reason, and the corresponding method returns {@code UNSUPPORTED_OPERATION} rather
 * than pretending to succeed.
 *
 * <p>The interface is free of Android types so the same contract can be implemented by a macOS
 * backend later, and so coordinators can be unit tested against fakes.
 */
public interface RuntimeBackend {

    RuntimeDescriptor descriptor();

    /** Cheap availability check: can this backend run here at all? */
    boolean isAvailable();

    /**
     * Initializes the backend and returns the current capability report.
     * Calling it twice must be safe (idempotent).
     */
    RuntimeResult<List<Capability>> initialize();

    /** Last capability report; empty before initialization. */
    List<Capability> capabilities();

    /** Capability status lookup, defaulting to UNKNOWN when not probed. */
    CapabilityStatus statusOf(RuntimeCapability capability);

    RuntimeResult<HostAppInfo> hostAppInfo();

    RuntimeResult<Void> launch();

    /** Best-effort stop of the guest. May be unsupported on some backends. */
    RuntimeResult<Void> stop();

    RuntimeResult<Void> suspend();

    RuntimeResult<Void> resume();

    /** @return true when the guest is currently in the foreground. */
    RuntimeResult<Boolean> isHostRunning();

    /**
     * Installs a verified APK set. Paths are absolute paths to already-downloaded, already-verified
     * files; the backend must re-check identity before handing them to the platform installer.
     */
    RuntimeResult<Void> install(List<String> apkPaths);

    RuntimeResult<ResourceSnapshot> resources();

    RuntimeResult<StorageStats> storage();

    /** Releases backend resources. Safe to call more than once. */
    void shutdown();
}
