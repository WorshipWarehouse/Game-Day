package app.gameday.runtime.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides whether this host can run the desktop runtime, and says exactly why not.
 *
 * <p>The desktop runtime is a virtual machine (Avo-class: Apple Hypervisor.framework + an unmodified
 * AOSP Cuttlefish image). Its requirements are therefore narrow and checkable, and a host that fails
 * any of them cannot be talked into working by configuration. Refusing early with a reason is more
 * useful than a stack trace from a half-started VM.
 *
 * <p>The requirements are the measured floor for that design: Apple Silicon (the Cuttlefish image is
 * arm64-only, and HVF on Intel Macs cannot run it), macOS 15+, 8 GB RAM, 10 GB free disk (Avo's own
 * published requirement) and an {@code adb} binary to control the guest.
 */
public final class HostReadiness {

    public static final long MIN_MEMORY_BYTES = 8L * 1024L * 1024L * 1024L;
    public static final long MIN_FREE_DISK_BYTES = 10L * 1024L * 1024L * 1024L;

    public enum Verdict {
        /** Every requirement is met; a guest can be booted. */
        READY,
        /** At least one requirement fails; the guest must not be started. */
        BLOCKED
    }

    public static final class Result {
        private final Verdict verdict;
        private final List<String> blockers;
        private final List<String> notes;

        Result(Verdict verdict, List<String> blockers, List<String> notes) {
            this.verdict = verdict;
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
        }

        public Verdict verdict() {
            return verdict;
        }

        public boolean isReady() {
            return verdict == Verdict.READY;
        }

        public List<String> blockers() {
            return blockers;
        }

        public List<String> notes() {
            return notes;
        }

        public String describe() {
            if (isReady()) {
                return "ready";
            }
            StringBuilder sb = new StringBuilder("blocked: ");
            for (int i = 0; i < blockers.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(blockers.get(i));
            }
            return sb.toString();
        }
    }

    private HostReadiness() {
    }

    public static Result evaluate(HostFacts facts) {
        List<String> blockers = new ArrayList<String>();
        List<String> notes = new ArrayList<String>();

        if (facts == null) {
            blockers.add("no host facts were collected");
            return new Result(Verdict.BLOCKED, blockers, notes);
        }
        if (!facts.isMacOs()) {
            blockers.add("requires macOS (Apple Hypervisor.framework); this host reports "
                    + facts.osName());
        }
        if (!facts.isArm64()) {
            blockers.add("requires Apple silicon (arm64: the AOSP Cuttlefish guest image is arm64 "
                    + "only); this host reports " + facts.arch());
        }
        if (facts.memoryBytes() > 0 && facts.memoryBytes() < MIN_MEMORY_BYTES) {
            blockers.add(String.format(java.util.Locale.ROOT,
                    "requires at least %.0f GB RAM; this host has %.1f GB",
                    MIN_MEMORY_BYTES / (1024.0 * 1024.0 * 1024.0), facts.memoryGb()));
        }
        if (facts.freeDiskBytes() > 0 && facts.freeDiskBytes() < MIN_FREE_DISK_BYTES) {
            blockers.add(String.format(java.util.Locale.ROOT,
                    "requires at least %.0f GB free disk for the guest image and app data; this host "
                            + "has %.1f GB",
                    MIN_FREE_DISK_BYTES / (1024.0 * 1024.0 * 1024.0), facts.freeDiskGb()));
        }
        if (facts.adbPath() == null || facts.adbPath().isEmpty()) {
            blockers.add("adb is not on PATH or under ANDROID_HOME/platform-tools, so the guest "
                    + "could not be controlled");
        }

        // Always reported, ready or not: these are properties of the design, not of this machine.
        notes.add("A virtual machine gives Widevine L3 at best. Widevine L1 requires TEE-bound, "
                + "per-device certification, which no VM or emulator can provide.");
        notes.add("The AOSP Cuttlefish image ships no Google Play services and no Amazon Appstore, so "
                + "a streaming app's account sign-in and app-store updates are unavailable in it.");
        notes.add("Avo's own specification states that DRM and hardware attestation are not "
                + "promised inside its VM.");

        return new Result(blockers.isEmpty() ? Verdict.READY : Verdict.BLOCKED, blockers, notes);
    }
}
