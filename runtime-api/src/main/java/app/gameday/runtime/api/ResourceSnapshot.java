package app.gameday.runtime.api;

/** A point-in-time resource reading for GameDay's own process plus system-wide memory pressure. */
public final class ResourceSnapshot {

    private final long takenAtMs;
    private final long hostPssBytes;
    private final long hostJavaHeapBytes;
    private final long hostNativeHeapBytes;
    private final long systemAvailableBytes;
    private final long systemTotalBytes;
    private final double hostCpuPercent;
    private final boolean lowMemory;

    public ResourceSnapshot(long takenAtMs, long hostPssBytes, long hostJavaHeapBytes,
            long hostNativeHeapBytes, long systemAvailableBytes, long systemTotalBytes,
            double hostCpuPercent, boolean lowMemory) {
        this.takenAtMs = takenAtMs;
        this.hostPssBytes = hostPssBytes;
        this.hostJavaHeapBytes = hostJavaHeapBytes;
        this.hostNativeHeapBytes = hostNativeHeapBytes;
        this.systemAvailableBytes = systemAvailableBytes;
        this.systemTotalBytes = systemTotalBytes;
        this.hostCpuPercent = hostCpuPercent;
        this.lowMemory = lowMemory;
    }

    public long takenAtMs() {
        return takenAtMs;
    }

    public long hostPssBytes() {
        return hostPssBytes;
    }

    public long hostJavaHeapBytes() {
        return hostJavaHeapBytes;
    }

    public long hostNativeHeapBytes() {
        return hostNativeHeapBytes;
    }

    public long systemAvailableBytes() {
        return systemAvailableBytes;
    }

    public long systemTotalBytes() {
        return systemTotalBytes;
    }

    /** GameDay's own CPU usage as a percentage of one core, averaged since the previous sample. */
    public double hostCpuPercent() {
        return hostCpuPercent;
    }

    public boolean isLowMemory() {
        return lowMemory;
    }
}
