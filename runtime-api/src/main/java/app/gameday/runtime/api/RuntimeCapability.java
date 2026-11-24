package app.gameday.runtime.api;

/** Capabilities a runtime backend may provide. Backends must report honestly. */
public enum RuntimeCapability {

    /** Enumerate the guest application's package metadata. */
    APP_INFO,
    /** Start the guest application. */
    APP_LAUNCH,
    /** Install or update the guest application. */
    APP_INSTALL,
    /** Install split APK sets atomically. */
    SPLIT_APK_INSTALL,
    /** Remove the guest application. */
    APP_UNINSTALL,
    /** Determine whether the guest is currently in the foreground. */
    PROCESS_MONITORING,
    /** Stop the guest application. */
    FORCE_STOP,
    /** Suspend a running guest and resume it later. */
    SUSPEND_RESUME,
    /** Separate the guest's data and identity from GameDay's. */
    GUEST_ISOLATION,
    /** Inject pointer events into the guest (accessible pointer mode). */
    POINTER_INJECTION,
    /** Inject key events into the guest. */
    KEY_INJECTION,
    /** Video is decoded by the device's hardware decoder. */
    HARDWARE_VIDEO_DECODE,
    /** DRM-protected playback is possible (secure decoder, Widevine). */
    DRM_PROTECTED_PLAYBACK,
    /** Guest data survives process restarts and GameDay updates. */
    PERSISTENT_STORAGE,
    /** Report memory and CPU usage. */
    RESOURCE_MONITORING,
    /** Detect guest crashes or abnormal exits. */
    CRASH_DETECTION,
    /** Delegate guest updates to an authorized distribution channel. */
    UPDATE_PROVISIONING
}
