# ADR 0003 — Platform pivot: where a genuine Android runtime is possible, and what it costs

- **Status:** accepted
- **Date:** 2026-11-24
- **Extends:** [0001](0001-runtime-selection.md), [0002](0002-isolation-and-runtime-feasibility.md)
- **Evidence:** [../PLATFORMS.md](../PLATFORMS.md), [../RUNTIME-POC.md](../RUNTIME-POC.md)

## Context

The original vision was a single application that hosts the NFL app inside its *own* Android
environment. GameDay Native (Fire TV) delivers the experience but is a host, not a runtime. This
decision asks, on the evidence, which platform can actually provide a genuine isolated guest — and
whether that guest can still play authorized content.

## Decision

**Split the product into two runtimes with different platforms, and stop treating "isolated guest"
and "authorized playback" as the same goal.**

1. **GameDay Native (Android TV / Fire TV)** remains the playback product. The guest is the device's
   own Android app, which is the only arrangement with Widevine L1.
2. **GameDay Desktop** targets **Apple silicon macOS on Avo** — a real Hypervisor.framework VM booting
   an unmodified AOSP Cuttlefish arm64 image with gfxstream/Metal — for genuine isolation and a
   seamless per-app macOS experience. It is explicitly **not** a path to NFL+ playback.
3. **No runtime work continues on the first-generation Fire TV Stick.** It stays supported by GameDay
   Native as a minimum target, not as the platform for runtime experiments.

## Answers to the six questions

**1. Which platform best supports the original runtime architecture?**
Apple silicon macOS, with Avo. It is the only candidate with a maintained, Apache-2.0, hardware
virtualized Android guest, host-GPU composition, per-app windows, persistent data and self-updates —
i.e. the only one where the *product experience* in the original vision is achievable. For
*authorized playback*, the answer is the opposite: a certified Android TV device running its own
Android.

**2. Which open-source project should GameDay build upon?**
**Avo** (`nizwar/avo`, Apache-2.0): Rust VMM on Hypervisor.framework, AOSP Cuttlefish arm64 image,
gfxstream + Metal, macOS 15+. GameDay contributes the parts Avo deliberately does not: authorized
app identity/verification, update policy for a non-store app, its own diagnostics schema, and a
runtime contract that refuses to overstate DRM.

**3. What functionality has actually been demonstrated?**
- The Fire TV product: build, 111 unit tests, Lint-clean, signed APK, verified dependency graph.
- A **host readiness verifier** that was executed on a real host and correctly refuses to start a
  guest, with reasons (`./gradlew :runtime:desktop:hostVerdict`).
- A **unit-tested desktop backend** (`AvoRuntimeBackend`) that drives a real VM over adb and reports
  DRM/video-decode/update capabilities as unsupported, with causes.
- **Not demonstrated:** any Android guest boot, by this reviewer, anywhere. The build environment has
  no usable hypervisor and is not macOS. See §"Blockers" below.

**4. What hardware limitations remain?**
- Widevine L1 requires a TEE and per-device certification; **no VM can have it**, so protected HD
  playback inside a guest is structurally impossible.
- AOSP Cuttlefish carries no Google Play services and no Amazon Appstore, so a streaming app's
  account, licensing and updates are unavailable inside the guest.
- Cuttlefish decodes video in software; host GPU acceleration covers composition, not the guest's
  video pipeline.
- `AFTMM` (1.5 GB, API 25, 32-bit) cannot run AVF and has no headroom for a guest plus a host.

**5. What components can be shared between Fire TV and macOS?**
`core` (version/update/compatibility/redaction/diagnostics, 100 % platform-neutral), `runtime-api`
(contract + coordinator + capability model), the update manifest schema and verification order, the
diagnostics JSON schema, the redaction rules, and the script conventions. The UI, installers and
platform probes are per-platform.

**6. Is the first-generation Fire Stick a practical long-term target?**
No. It is a legitimate *minimum* target for the launcher (it is what the project was built for), but
API 25 / 32-bit / 1.5 GB excludes every modern runtime technique and every future Android API. The
practical floor for new work is **Fire OS 8 (API 30)** hardware, and for isolation work, Apple
silicon.

**7. What is the cheapest viable hardware platform?**
For the product as it exists (authorized playback): a **Fire TV Stick 4K Max 2nd gen** (`AFTKRT`,
Fire OS 8, 16 GB, ~$40–60). For desktop runtime development: any **Apple silicon Mac** (used M1 mini
or MacBook Air, ~$400–500). A Raspberry Pi 5 (~$90–110) is the cheapest Android-on-ARM testbed, but it
is uncertified, L3-only, and its LineageOS build carries non-commercial licensed components.

## Blockers in the review environment (evidence, not opinion)

- `/dev/kvm` exists but `open()` returns **EPERM**; the user is not in the `kvm` group, `sudo` is
  blocked by `no-new-privileges`, and Docker is inaccessible.
- No QEMU binary and no root to install one; `apt` reports no candidate.
- The Android Emulator's Linux build needs `libpulse.so.0`, `libnss3.so` and `libasound.so.2`, none of
  which are present or installable without root.
- The host is Linux x86_64, so **Hypervisor.framework cannot be exercised at all** — the selected
  platform is not even testable here.
- 2 vCPU / 3.8 GB / 8.1 GB free disk would not produce representative performance figures even if a
  TCG boot succeeded.

The honest consequence: Stage 4's guest-boot validation **has not been performed**, and no performance
number in this repository describes a guest.

## Consequences

**Positive**

- The project stops spending effort trying to make a VM play L1 content, which cannot work.
- macOS gets a concrete, licensed, active foundation (Avo) instead of a speculative plan.
- The runtime contract now encodes the DRM truth, so no future backend can silently overstate it.
- The Fire TV product is untouched and still passes its suite.

**Negative / accepted**

- The original "one app, own Android, full playback" vision is **not achievable** on any rootless
  platform; the documentation says so plainly.
- The desktop runtime cannot host the NFL app in a usable way (no store, no GMS, no L1).
- Guest performance remains unmeasured until someone runs the verifier and a VM on a Mac.

## Revisit criteria

Reopen if any of these becomes true:

1. A VM platform can obtain Widevine L1 (would require vendor certification of a virtual device —
   currently no such programme exists for third-party VMs).
2. Avo or a successor adds a licensed store and a supported DRM path.
3. GameDay Desktop's purpose is redefined as "run non-DRM Android apps", in which case Avo alone is
   sufficient and the remaining work is packaging and integration.
