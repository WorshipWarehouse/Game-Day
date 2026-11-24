package app.gameday.runtime.api;

/** Storage use attributable to GameDay and, when known, to the guest application. */
public final class StorageStats {

    private final long hostDataBytes;
    private final long hostCacheBytes;
    private final long guestApkBytes;
    private final long guestDataBytes;
    private final long freeBytes;

    public StorageStats(long hostDataBytes, long hostCacheBytes, long guestApkBytes,
            long guestDataBytes, long freeBytes) {
        this.hostDataBytes = hostDataBytes;
        this.hostCacheBytes = hostCacheBytes;
        this.guestApkBytes = guestApkBytes;
        this.guestDataBytes = guestDataBytes;
        this.freeBytes = freeBytes;
    }

    public long hostDataBytes() {
        return hostDataBytes;
    }

    public long hostCacheBytes() {
        return hostCacheBytes;
    }

    public long guestApkBytes() {
        return guestApkBytes;
    }

    public long guestDataBytes() {
        return guestDataBytes;
    }

    public long freeBytes() {
        return freeBytes;
    }
}
