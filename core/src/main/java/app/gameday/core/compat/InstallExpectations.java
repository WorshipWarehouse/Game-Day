package app.gameday.core.compat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What a caller requires of an APK before it may be installed or launched. */
public final class InstallExpectations {

    public static final long DEFAULT_STORAGE_MARGIN_BYTES = 32L * 1024L * 1024L;

    private final String packageName;
    private final String pinnedSignerSha256;
    private final long installedVersionCode;
    private final long storageMarginBytes;
    private final boolean requireLaunchable;
    private final boolean requireTelevision;
    private final Set<String> deviceFeatures;

    private InstallExpectations(Builder b) {
        this.packageName = b.packageName;
        this.pinnedSignerSha256 = b.pinnedSignerSha256;
        this.installedVersionCode = b.installedVersionCode;
        this.storageMarginBytes = b.storageMarginBytes;
        this.requireLaunchable = b.requireLaunchable;
        this.requireTelevision = b.requireTelevision;
        this.deviceFeatures = Collections.unmodifiableSet(new LinkedHashSet<String>(b.deviceFeatures));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String packageName() {
        return packageName;
    }

    public String pinnedSignerSha256() {
        return pinnedSignerSha256;
    }

    public long installedVersionCode() {
        return installedVersionCode;
    }

    public long storageMarginBytes() {
        return storageMarginBytes;
    }

    public boolean requireLaunchable() {
        return requireLaunchable;
    }

    public boolean requireTelevision() {
        return requireTelevision;
    }

    public Set<String> deviceFeatures() {
        return deviceFeatures;
    }

    public static final class Builder {
        private String packageName;
        private String pinnedSignerSha256;
        private long installedVersionCode = -1L;
        private long storageMarginBytes = DEFAULT_STORAGE_MARGIN_BYTES;
        private boolean requireLaunchable;
        private boolean requireTelevision;
        private final Set<String> deviceFeatures = new LinkedHashSet<String>();

        public Builder packageName(String v) {
            this.packageName = v;
            return this;
        }

        /** Pin the accepted signing certificate. When null, the caller verifies identity elsewhere. */
        public Builder pinnedSignerSha256(String v) {
            this.pinnedSignerSha256 = v;
            return this;
        }

        /** Version code currently installed, or -1 when the package is absent. */
        public Builder installedVersionCode(long v) {
            this.installedVersionCode = v;
            return this;
        }

        public Builder storageMarginBytes(long v) {
            this.storageMarginBytes = v;
            return this;
        }

        public Builder requireLaunchable(boolean v) {
            this.requireLaunchable = v;
            return this;
        }

        public Builder requireTelevision(boolean v) {
            this.requireTelevision = v;
            return this;
        }

        public Builder deviceFeatures(Set<String> v) {
            this.deviceFeatures.clear();
            if (v != null) {
                this.deviceFeatures.addAll(v);
            }
            return this;
        }

        public InstallExpectations build() {
            return new InstallExpectations(this);
        }
    }
}
