# macOS roadmap

Status: **preparation only**. No macOS code exists, and none should be written until the Fire TV
implementation has been validated on hardware (Milestone 9 of the mission).

This document records what is already reusable, what a macOS runtime would actually have to do, and
the blockers that make "run the Android NFL app on a Mac" a research question rather than an
engineering task.

## 1. What is reusable today, unchanged

| Component | Why it ports |
| --- | --- |
| `core` (all of it) | Pure Java 17, no Android types: version comparison, update manifests and policy, compatibility rules, install-set validation, URL policy, redaction, JSON, diagnostic report rendering, crash-loop detection, remote-key mapping. |
| `runtime-api` | The `RuntimeBackend` contract, capability model and `RuntimeCoordinator` state machine with its failure budget — already backend-agnostic. |
| Update manifest schema and verification order | Platform-neutral; the macOS updater would verify the same fields. |
| Diagnostics schema and redaction | Same JSON shape, so reports from both platforms can be compared. |
| Settings key naming and semantics | Same keys, different storage. |
| Script conventions | `setup/build/test/diagnose/benchmark/package` with the same layout. |

Java is not the natural language for a macOS shell, so the practical options are: (a) port the pure
logic to Swift with the existing JVM tests as the specification, or (b) embed the JVM. Option (a) is
preferable for a native app; the test suite makes the port mechanical and verifiable.

## 2. What must be new

- `MacRuntimeBackend` implementing `RuntimeBackend`.
- A SwiftUI/AppKit shell: window, fullscreen, Dock integration, keyboard/mouse input, settings,
  diagnostics, recovery.
- An update installer (download, verify, replace the bundle, relaunch) with macOS code-signature and
  notarization checks instead of Android's `PackageInstaller`.
- Sandbox and entitlement design, hardened runtime, notarization in the release pipeline.

## 3. The actual blocker: there is no Android runtime for macOS with protected playback

| Option | Reality |
| --- | --- |
| **Apple Virtualization.framework** | Runs ARM64 Linux (and macOS) guests on Apple Silicon with excellent performance. It does **not** provide Android. You would still need a full Android userspace and a graphics/audio/input bridge. |
| **Apple Hypervisor.framework + QEMU (HVF)** | Viable for ARM64 Linux guests; same missing Android userspace problem, plus no camera/DRM passthrough. |
| **Android Studio emulator** | A developer tool. It runs Android images with hardware acceleration, but it does not provide Google Play services, Widevine L1, or a supported path for a third-party streaming app's DRM. |
| **Avo** | Container tooling for running Android apps on Linux; not a macOS runtime. |
| **Waydroid** | Linux only; requires a Linux kernel with binder. |
| **Native Android-on-macOS compatibility layer** | Does not exist in any maintained, licensed form. |

Protected playback is the same wall as on Fire TV: Widevine L1 requires a certified device with a
hardware-backed trust chain, and L3 requires a CDM with a device certificate that a virtual machine
cannot obtain. A virtualised Android on a Mac would, at best, play unprotected content.

## 4. What the NFL itself supports on Apple platforms

The NFL ships a native **tvOS** app and a web experience; the NFL+ supported-device list covers Apple
TV (tvOS 15+), iPhone/iPad and browsers. There is no native macOS Android app to host.

That changes what a macOS "GameDay" should be. The honest options, in order of likely value:

1. **A native macOS launcher for the NFL's web experience**, with the same orchestration features
   (update tracking, diagnostics, recovery, capability reporting) and no emulation at all. Reuses
   `core` and the diagnostics/update design directly.
2. **A future native runtime**, if and when a licensed, DRM-capable Android runtime exists on macOS.
   Only then does `RuntimeBackend` gain a second real implementation.

## 5. Milestones (not started)

| Id | Milestone | Exit criteria |
| --- | --- | --- |
| M9.1 | Extract shared contract documentation | Done: this document plus `runtime-api` |
| M9.2 | Swift port of `core` | The JVM test suite's cases pass in Swift, including redaction and update policy |
| M9.3 | macOS shell with a diagnostic-only backend | App launches, reports capabilities, runs recovery; no guest |
| M9.4 | Runtime evidence gathering | Measured playback evidence for a candidate runtime, or a written rejection |
| M9.5 | Packaging and notarization | Signed, notarized, auto-updating app bundle |

## 6. Rules for the port

- **Do not compromise the Fire TV implementation for code sharing.** The Fire TV app is the product.
- **Do not ship a macOS runtime that cannot play protected content and call it compatible.** It would
  repeat the mistake this project's research was written to avoid.
- **Keep the verification order identical.** Package identity, signature, version, compatibility,
  then install — on every platform.
- **Keep diagnostics comparable.** Same schema, same redaction, so a report from a Mac and a Fire TV
  can be diffed.
