package app.gameday.runtime.api;

/** The categories of runtime GameDay can reason about. */
public enum RuntimeKind {

    /**
     * The guest application runs natively on the device, installed by the platform's own app store,
     * and GameDay orchestrates launch, monitoring, updates and recovery around it.
     *
     * <p>This is the only category that preserves the guest's hardware video pipeline, DRM and
     * system integration without root on Fire OS 6.
     */
    NATIVE_HOST,

    /**
     * The guest application is loaded inside GameDay's own process space by an application-level
     * virtualization engine (for example a licensed VirtualApp-class SDK).
     */
    APP_VIRTUALIZATION,

    /**
     * A full Android system runs in a virtual machine (QEMU, crosvm, AVF). Not viable on the
     * target hardware; retained so capability reports can state why.
     */
    SYSTEM_EMULATION
}
