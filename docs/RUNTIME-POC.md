# Runtime proof of concept — what was executed, and what was not

This is the Phase 3/4 evidence record. It is deliberately split into three parts: what ran, what the
environment refused to do, and what a Mac must show before anyone claims the runtime works.

## 1. What actually ran (in this repository, on this machine)

```bash
./gradlew :runtime:desktop:test         # 18 tests, 0 failures
./gradlew :runtime:desktop:hostVerdict  # executed on the review host
```

Real output of the verifier on the machine this work was done on:

```json
{
  "backend": "avo-desktop",
  "os": "Linux",
  "osVersion": "6.1.102",
  "arch": "amd64",
  "cpus": 2,
  "memoryGb": 3.8,
  "freeDiskGb": 8.1,
  "kvmReadable": false,
  "adb": ".../tc/sdk/platform-tools/adb",
  "java": ".../tc/jdk-17.0.21.1+2/bin/java",
  "ready": false,
  "verdict": "BLOCKED",
  "blockers": [
    "requires macOS (Apple Hypervisor.framework); this host reports Linux",
    "requires Apple silicon (arm64: the AOSP Cuttlefish guest image is arm64 only); this host reports amd64",
    "requires at least 8 GB RAM; this host has 3.8 GB",
    "requires at least 10 GB free disk for the guest image and app data; this host has 8.1 GB"
  ],
  "notes": [
    "A virtual machine gives Widevine L3 at best. Widevine L1 requires TEE-bound, per-device certification, which no VM or emulator can provide.",
    "The AOSP Cuttlefish image ships no Google Play services and no Amazon Appstore, so a streaming app's account sign-in and app-store updates are unavailable in it.",
    "Avo's own specification states that DRM and hardware attestation are not promised inside its VM."
  ]
}
```

That is the deliverable: a machine-checkable go/no-go gate, run for real, that refuses to let a guest
start on a host that cannot carry one — and states the DRM consequence even when the verdict is
`READY`.

## 2. What the environment refused, and why (blocker evidence)

Stage 4 asks for a genuine Android guest boot. It did not happen here. The precise blockers, each
measured:

| Check | Result | Consequence |
| --- | --- | --- |
| `uname -m` | `x86_64` | Hypervisor.framework (the selected platform) cannot be exercised at all |
| `/dev/kvm` | exists, `crw-rw---- root:kvm` | — |
| `open("/dev/kvm", O_RDWR)` | **EPERM (13)** | no hardware virtualization for this process |
| groups / sudo | only `freebuff`; `sudo` blocked by `no-new-privileges` | cannot join the `kvm` group or install anything |
| Docker | socket not accessible | no containerized emulator path |
| QEMU | not installed; `apt` has no candidate; no root | cannot boot a guest with KVM or TCG |
| Android Emulator host libs | `libpulse.so.0`, `libnss3.so`, `libasound.so.2` **missing** | the emulator binary could not run even if downloaded |
| CPU / RAM / disk | 2 vCPU / 3.8 GB / 8.1 GB free | even a successful TCG boot would not be representative |

So the honest statement is: **no Android guest was booted by this reviewer on any machine.** Anything
else would be fabrication, and the project's own rule is that untested results are never reported as
working.

## 3. What was built instead (and why it is not a substitute launcher)

`runtime/desktop` implements GameDay's existing `RuntimeBackend` contract against a **real VM**
(Avo-class: Hypervisor.framework VMM + unmodified AOSP Cuttlefish arm64 image), driven over adb:

| Operation | Guest command |
| --- | --- |
| app presence / version | `adb shell pm list packages <pkg>`, `adb shell dumpsys package <pkg>` |
| launch | `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1` |
| stop | `adb shell am force-stop <pkg>` |
| running | `adb shell pidof <pkg>` |
| memory | `adb shell dumpsys meminfo <pkg>` → `TOTAL PSS` |
| storage | `adb shell df /data` |
| install (dev path) | `adb install -r -d`, `adb install-multiple` |

Capabilities are reported from the design, not from optimism:

- `SUPPORTED`: app info, launch, install, split install, **guest isolation** (own kernel),
  persistent storage, resource/process monitoring, force-stop, crash detection (logcat).
- `UNSUPPORTED`, each with its cause: **DRM protected playback** (L1 needs a TEE; a VM is L3 at best),
  **hardware video decode** (Cuttlefish decodes in software; the host GPU accelerates composition,
  not the guest's video pipeline), **update provisioning** (no store in the image), suspend/resume
  (daemon control channel, not adb), input injection.

Eighteen JVM tests (no VM required) pin this behaviour: refusal on a non-Apple-Silicon host, the
exact adb commands, parsing of version/memory/storage, failure mapping (`LAUNCH_FAILED`,
`INSTALL_FAILED`), and the rule that a `READY` verdict still carries the L1 warning.

## 4. Completing Stage 4 on a Mac (the run this environment could not do)

On an Apple silicon Mac with macOS 15+ and 10 GB free:

```bash
# 1. Gate the host first; do not skip this.
./gradlew :runtime:desktop:hostVerdict      # expect "ready": true on an M-series Mac

# 2. Install the runtime the backend targets.
brew install --cask avo                     # or build github.com/nizwar/avo from source
#    Avo's setup assistant downloads the AOSP Cuttlefish arm64 image on first launch.
#    Enable adb in Avo's settings.

# 3. Confirm the guest is reachable.
adb devices

# 4. Install a self-owned test APK and drive it through the backend.
adb install -r compatibility/test-app/build/outputs/apk/debug/test-app-debug.apk
./gradlew :runtime:desktop:test             # the backend is already exercised against fakes
```

Then record, and only then claim:

| Measurement | How | Expected on this design |
| --- | --- | --- |
| Guest boot time | `adb wait-for-device` + `getprop sys.boot_completed` timestamps | tens of seconds on M-series |
| App launch time | `adb shell am start -W` | one to a few seconds |
| GPU acceleration | `dumpsys SurfaceFlinger`/gfxstream logs; visual check | accelerated (gfxstream/Metal) |
| Video decode | `dumpsys media.codec` | **software** — no hardware decoder in Cuttlefish |
| DRM level | a MediaDrm probe APK (`SecurityLevel`) | **L3**, never L1 |
| Playback of protected content | the app's own player | expected to fail or cap at SD — this is the blocker, not a bug |
| Memory | `adb shell dumpsys meminfo` + Activity Monitor | guest RSS + VM overhead |
| Suspend/resume | Avo's own controls | not exposed over adb |
| Audio | playback test | Core Audio via virtio-snd |

**Pass criteria for "the runtime is viable for GameDay":** guest boots, app launches, graphics are
accelerated, storage persists across restarts, and *ordinary* (non-DRM) playback works. **Protected
playback is expected to fail**, and that is a property of every VM on every platform, not a defect to
be fixed.

## 5. Conclusion

- The runtime architecture is **feasible on Apple silicon using Avo**: a real guest, real isolation,
  real GPU acceleration, real persistence.
- It is **not** a way to deliver authorized NFL playback, because Widevine L1 is hardware-bound.
- The Fire TV / Android TV product remains the only path to the original viewing experience, and it
  is untouched and still fully tested.
