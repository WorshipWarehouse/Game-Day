package app.gameday.core.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Facts about an APK (or a split-APK set) extracted from its package metadata. */
public final class ApkFacts {

    private final String packageName;
    private final long versionCode;
    private final String versionName;
    private final int minSdk;
    private final int targetSdk;
    private final List<String> nativeAbis;
    private final List<String> signerSha256;
    private final List<String> splitNames;
    private final List<String> requiredFeatures;
    private final long sizeBytes;
    private final boolean launchable;
    private final String label;

    private ApkFacts(Builder b) {
        this.packageName = b.packageName;
        this.versionCode = b.versionCode;
        this.versionName = b.versionName;
        this.minSdk = b.minSdk;
        this.targetSdk = b.targetSdk;
        this.nativeAbis = immutable(b.nativeAbis);
        this.signerSha256 = immutable(b.signerSha256);
        this.splitNames = immutable(b.splitNames);
        this.requiredFeatures = immutable(b.requiredFeatures);
        this.sizeBytes = b.sizeBytes;
        this.launchable = b.launchable;
        this.label = b.label;
    }

    private static List<String> immutable(List<String> in) {
        List<String> copy = new ArrayList<String>();
        if (in != null) {
            for (String s : in) {
                if (s != null && !s.trim().isEmpty()) {
                    copy.add(s.trim().toLowerCase(java.util.Locale.US));
                }
            }
        }
        return Collections.unmodifiableList(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String packageName() {
        return packageName;
    }

    public long versionCode() {
        return versionCode;
    }

    public String versionName() {
        return versionName;
    }

    public int minSdk() {
        return minSdk;
    }

    public int targetSdk() {
        return targetSdk;
    }

    public List<String> nativeAbis() {
        return nativeAbis;
    }

    public List<String> signerSha256() {
        return signerSha256;
    }

    public List<String> splitNames() {
        return splitNames;
    }

    public List<String> requiredFeatures() {
        return requiredFeatures;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public boolean isLaunchable() {
        return launchable;
    }

    public String label() {
        return label == null ? "" : label;
    }

    public boolean isSplit() {
        return !splitNames.isEmpty();
    }

    /** True when the APK ships native code for at least one of the device ABIs. */
    public boolean supportsAnyAbi(List<String> deviceAbis) {
        if (nativeAbis.isEmpty()) {
            return true; // pure Java/DEX: runs on every ABI.
        }
        if (deviceAbis == null) {
            return false;
        }
        for (String abi : deviceAbis) {
            if (nativeAbis.contains(abi.trim().toLowerCase(java.util.Locale.US))) {
                return true;
            }
        }
        return false;
    }

    public static final class Builder {
        private String packageName = "";
        private long versionCode;
        private String versionName = "";
        private int minSdk;
        private int targetSdk;
        private List<String> nativeAbis = new ArrayList<String>();
        private List<String> signerSha256 = new ArrayList<String>();
        private List<String> splitNames = new ArrayList<String>();
        private List<String> requiredFeatures = new ArrayList<String>();
        private long sizeBytes;
        private boolean launchable;
        private String label = "";

        public Builder packageName(String v) {
            this.packageName = v;
            return this;
        }

        public Builder versionCode(long v) {
            this.versionCode = v;
            return this;
        }

        public Builder versionName(String v) {
            this.versionName = v;
            return this;
        }

        public Builder minSdk(int v) {
            this.minSdk = v;
            return this;
        }

        public Builder targetSdk(int v) {
            this.targetSdk = v;
            return this;
        }

        public Builder nativeAbis(List<String> v) {
            this.nativeAbis = v;
            return this;
        }

        public Builder signerSha256(List<String> v) {
            this.signerSha256 = v;
            return this;
        }

        public Builder splitNames(List<String> v) {
            this.splitNames = v;
            return this;
        }

        public Builder requiredFeatures(List<String> v) {
            this.requiredFeatures = v;
            return this;
        }

        public Builder sizeBytes(long v) {
            this.sizeBytes = v;
            return this;
        }

        public Builder launchable(boolean v) {
            this.launchable = v;
            return this;
        }

        public Builder label(String v) {
            this.label = v;
            return this;
        }

        public ApkFacts build() {
            return new ApkFacts(this);
        }
    }
}
