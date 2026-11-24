package app.gameday.runtime.api;

/** One capability report entry: what it is, whether it is usable, and why. */
public final class Capability {

    private final RuntimeCapability capability;
    private final CapabilityStatus status;
    private final String detail;

    public Capability(RuntimeCapability capability, CapabilityStatus status, String detail) {
        this.capability = capability;
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    public static Capability supported(RuntimeCapability c, String detail) {
        return new Capability(c, CapabilityStatus.SUPPORTED, detail);
    }

    public static Capability unsupported(RuntimeCapability c, String detail) {
        return new Capability(c, CapabilityStatus.UNSUPPORTED, detail);
    }

    public RuntimeCapability capability() {
        return capability;
    }

    public CapabilityStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return capability + "=" + status + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
