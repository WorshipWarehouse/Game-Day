package app.gameday.core.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What the device can do, as far as compatibility decisions are concerned. */
public final class DeviceFacts {

    private final int sdkInt;
    private final List<String> abis;
    private final boolean television;
    private final boolean touchscreen;
    private final long availableStorageBytes;
    private final int displayWidthPx;
    private final int displayHeightPx;
    private final boolean hardwareDecodeAvc;
    private final boolean hardwareDecodeHevc;

    private DeviceFacts(Builder b) {
        this.sdkInt = b.sdkInt;
        this.abis = Collections.unmodifiableList(new ArrayList<String>(b.abis));
        this.television = b.television;
        this.touchscreen = b.touchscreen;
        this.availableStorageBytes = b.availableStorageBytes;
        this.displayWidthPx = b.displayWidthPx;
        this.displayHeightPx = b.displayHeightPx;
        this.hardwareDecodeAvc = b.hardwareDecodeAvc;
        this.hardwareDecodeHevc = b.hardwareDecodeHevc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int sdkInt() {
        return sdkInt;
    }

    public List<String> abis() {
        return abis;
    }

    public boolean isTelevision() {
        return television;
    }

    public boolean hasTouchscreen() {
        return touchscreen;
    }

    public long availableStorageBytes() {
        return availableStorageBytes;
    }

    public int displayWidthPx() {
        return displayWidthPx;
    }

    public int displayHeightPx() {
        return displayHeightPx;
    }

    public boolean hardwareDecodeAvc() {
        return hardwareDecodeAvc;
    }

    public boolean hardwareDecodeHevc() {
        return hardwareDecodeHevc;
    }

    public static final class Builder {
        private int sdkInt;
        private List<String> abis = new ArrayList<String>();
        private boolean television;
        private boolean touchscreen = true;
        private long availableStorageBytes;
        private int displayWidthPx;
        private int displayHeightPx;
        private boolean hardwareDecodeAvc;
        private boolean hardwareDecodeHevc;

        public Builder sdkInt(int v) {
            this.sdkInt = v;
            return this;
        }

        public Builder abis(List<String> v) {
            this.abis = v == null ? new ArrayList<String>() : new ArrayList<String>(v);
            return this;
        }

        public Builder television(boolean v) {
            this.television = v;
            return this;
        }

        public Builder touchscreen(boolean v) {
            this.touchscreen = v;
            return this;
        }

        public Builder availableStorageBytes(long v) {
            this.availableStorageBytes = v;
            return this;
        }

        public Builder display(int widthPx, int heightPx) {
            this.displayWidthPx = widthPx;
            this.displayHeightPx = heightPx;
            return this;
        }

        public Builder hardwareDecode(boolean avc, boolean hevc) {
            this.hardwareDecodeAvc = avc;
            this.hardwareDecodeHevc = hevc;
            return this;
        }

        public DeviceFacts build() {
            return new DeviceFacts(this);
        }
    }
}
