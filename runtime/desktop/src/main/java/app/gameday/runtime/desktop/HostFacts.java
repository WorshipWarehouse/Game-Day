package app.gameday.runtime.desktop;

/**
 * Measured facts about the machine GameDay is running on.
 *
 * <p>Every field is observed, never assumed from a platform name. This matters because the same
 * "macOS" can be an Intel Mac without a usable hypervisor or an Apple Silicon Mac with one.
 */
public final class HostFacts {

    private final String osName;
    private final String osVersion;
    private final String arch;
    private final int cpuCount;
    private final long memoryBytes;
    private final long freeDiskBytes;
    /** True when /dev/kvm can actually be opened (Linux), not merely when the node exists. */
    private final boolean kvmReadable;
    private final String adbPath;
    private final String javaPath;

    public HostFacts(String osName, String osVersion, String arch, int cpuCount, long memoryBytes,
            long freeDiskBytes, boolean kvmReadable, String adbPath, String javaPath) {
        this.osName = osName == null ? "" : osName;
        this.osVersion = osVersion == null ? "" : osVersion;
        this.arch = arch == null ? "" : arch;
        this.cpuCount = cpuCount;
        this.memoryBytes = memoryBytes;
        this.freeDiskBytes = freeDiskBytes;
        this.kvmReadable = kvmReadable;
        this.adbPath = adbPath;
        this.javaPath = javaPath;
    }

    public String osName() {
        return osName;
    }

    public String osVersion() {
        return osVersion;
    }

    public String arch() {
        return arch;
    }

    public int cpuCount() {
        return cpuCount;
    }

    public long memoryBytes() {
        return memoryBytes;
    }

    public long freeDiskBytes() {
        return freeDiskBytes;
    }

    public boolean kvmReadable() {
        return kvmReadable;
    }

    public String adbPath() {
        return adbPath;
    }

    public String javaPath() {
        return javaPath;
    }

    public boolean isMacOs() {
        return osName.toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    /** Apple Silicon reports aarch64 (JVM) or arm64 (uname) depending on the launcher. */
    public boolean isArm64() {
        String a = arch.toLowerCase(java.util.Locale.ROOT);
        return a.contains("aarch64") || a.contains("arm64");
    }

    public double memoryGb() {
        return memoryBytes <= 0 ? -1 : memoryBytes / (1024.0 * 1024.0 * 1024.0);
    }

    public double freeDiskGb() {
        return freeDiskBytes <= 0 ? -1 : freeDiskBytes / (1024.0 * 1024.0 * 1024.0);
    }
}
