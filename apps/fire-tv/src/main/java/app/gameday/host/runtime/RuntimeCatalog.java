package app.gameday.host.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.gameday.runtime.api.RuntimeKind;

/**
 * The runtime options GameDay evaluated, and their status on the target device.
 *
 * <p>This is shipped data, not a placeholder: it powers the compatibility screen and the exported
 * diagnostic report so a user or maintainer can see exactly which approaches were considered and
 * why the unsupported ones are not used. See docs/decisions/0001-runtime-selection.md.
 */
public final class RuntimeCatalog {

    private RuntimeCatalog() {
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final RuntimeKind kind;
        public final boolean available;
        public final String reason;

        Entry(String id, String name, RuntimeKind kind, boolean available, String reason) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.available = available;
            this.reason = reason;
        }
    }

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<Entry>();
        out.add(new Entry("native-host", "Fire OS native host", RuntimeKind.NATIVE_HOST, true,
                "Launches the Amazon Appstore build of the NFL app in place, so hardware decoding, "
                        + "HDCP and Widevine stay on the normal platform path."));
        out.add(new Entry("virtualapp", "Application virtualization (VirtualApp class)", RuntimeKind.APP_VIRTUALIZATION,
                false,
                "Not used: the open-source VirtualApp code has no licence grant (all rights "
                        + "reserved), has not been updated since December 2017, and running the guest "
                        + "inside the host process loses the secure playback path protected content "
                        + "requires."));
        out.add(new Entry("waydroid", "Waydroid / Android container", RuntimeKind.SYSTEM_EMULATION,
                false,
                "Not possible: needs a GNU/Linux host, root, and a kernel binder driver. Fire OS 6 "
                        + "is an Android device without root or a bootloader unlock."));
        out.add(new Entry("avf", "Android Virtualization Framework", RuntimeKind.SYSTEM_EMULATION,
                false,
                "Not available: AVF requires Android 13+, ARM64 and the MANAGE_VIRTUAL_MACHINE "
                        + "system permission. This device is Android 7.1, 32-bit."));
        out.add(new Entry("qemu", "QEMU/Limbo full-system emulation", RuntimeKind.SYSTEM_EMULATION,
                false,
                "Not viable: software CPU emulation on a 1.7 GHz quad-core ARMv7 SoC cannot decode "
                        + "1080p video, and hardware decoders plus DRM cannot be passed through "
                        + "without root or a vendor backend."));
        return Collections.unmodifiableList(out);
    }

    public static boolean anyExperimentalAvailable() {
        for (Entry e : entries()) {
            if (e.available && e.kind != RuntimeKind.NATIVE_HOST) {
                return true;
            }
        }
        return false;
    }
}
