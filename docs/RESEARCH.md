# Research: hosting the NFL application on a first-generation Fire TV Stick 4K

Status: complete for Milestone 1, extended by [RESEARCH-PHASE2.md](RESEARCH-PHASE2.md) (adversarial
second pass: BlackBox, managed profiles, closed-source VM apps). Every claim below was checked against
primary sources or the repository itself; anything not verified is labelled as such.

## 1. Method

1. Verified the target hardware against Amazon's own developer documentation.
2. Verified the guest application's real package, minimum Android level, ABI coverage and
   distribution channel from NFL documentation and published package metadata.
3. Audited each candidate runtime's repository metadata (licence, last commit, release history,
   architecture) and its documented requirements.
4. Distinguished the categories that are routinely conflated: full-system emulation, CPU emulation,
   hardware-assisted virtualization, Android containers, application-level virtualization,
   compatibility layers, and native application hosting.
5. Scored the survivors against the criteria in §6 using the evidence, not vendor claims.
6. Selected the architecture that maximises *verified* capability on the exact target device.

Note on limits: no physical `AFTMM` device was attached to this environment. Hardware performance
numbers for the runtime candidates therefore come from the architectural analysis in §5 and from the
published specifications, not from measurement. What *can* be measured here (GameDay's own build,
tests and APK properties) is reported in [PERFORMANCE.md](PERFORMANCE.md).

## 2. Target hardware, verified

Amazon's device specification page for the Fire TV Stick 4K (1st gen, build model `AFTMM`) states:

| Property | Value |
| --- | --- |
| Friendly name | Fire TV Stick 4K – 1st Gen (2018) |
| Build model | `AFTMM` |
| Android version | API Level 25 (Android 7.1) |
| OS version | Fire OS 6 |
| Processor (SoC) | MTK8695 + MT7668 |
| CPU | Quad Core 1.7 GHz |
| ABI | **32-bit** |
| GPU | IMG GE8300 |
| Memory | 1.5 GB DDR4 |
| Video | HEVC/AVC/VP9 up to 4K UHD 60 fps, HDCP 2.2 |

Amazon's Fire OS overview confirms Fire OS 6 is based on Android 7.1 (API 25) and lists `AFTMM`
against Fire OS 6. Sources:

- <https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-streaming-media-player.html>
- <https://developer.amazon.com/docs/fire-tv/fire-os-overview.html>
- <https://developer.amazon.com/docs/fire-tv/identify-amazon-fire-tv-devices.html>

Consequences that shaped every decision:

- A 32-bit application environment, so a 64-bit-only guest cannot run and AVF's ARM64 requirement is
  unsatisfiable.
- 1.5 GB total RAM: the host's overhead must be measured in tens of megabytes, not hundreds.
- No root and a locked bootloader, so kernel-module and device-node approaches are out.
- Hardware video decode and HDCP 2.2 exist at the platform level and are reached through
  `MediaCodec`/`MediaDrm` by ordinary apps.

## 3. The guest application, verified

The official Fire TV build is **`com.gotv.nflgamecenter.us.lite`** ("NFL", NFL Enterprises LLC).
Published package metadata for version 18.22.2 reports:

| Property | Value |
| --- | --- |
| Package | `com.gotv.nflgamecenter.us.lite` |
| Minimum Android | 7.1 (API 25) |
| Target Android | 11 (API 30) |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Declared requirement | Android TV (leanback launcher) |
| Distribution on Fire TV | Amazon Appstore |

The NFL's own support documentation describes installing it from the Fire TV home screen search, and
the NFL+ device list includes **"FireTV 6+"**, i.e. exactly this device generation. Sources:

- <https://support.nfl.com/hc/en-us/articles/39637377693844-How-to-Download-the-NFL-App-on-Your-Amazon-Fire-TV>
- <https://www.nfl.com/apps/>
- Published APK metadata for NFL (Fire TV) 18.22.2 (package facts above).

This is the decisive finding: **the guest does not need a compatibility layer to run on the target
device.** It is built for API 25+, ships an `armeabi-v7a` slice, and requires a television launcher,
all of which Fire OS 6 provides.

It also means the only genuinely open questions are about *orchestration*: launching it reliably,
knowing whether it is installed and current, verifying what got installed, and recovering from
failures.

## 4. Candidate audit

### 4.1 VirtualApp (`asLody/VirtualApp`)

- Architecture: in-process application virtualization. A host app embeds the SDK; guest APKs are
  loaded by a patched `ActivityThread`/`ClassLoader` with system services proxied through hooks.
- Maintenance: the README states the public code **stopped updating in December 2017**; the current
  engine is a paid commercial SDK. The repository has no licence file, and the README explicitly
  asserts restrictions and threatens enforcement against unauthorised use.
- Licensing consequence: with no licence grant, the default is all rights reserved. GameDay cannot
  vendor or link it under any open-source distribution model.
- Isolation consequence: the README and the VirtualXposed design notes state that guests run with
  the host's UID inside the host's process. That is weaker isolation than the Android sandbox and is
  exactly the signal application-protection and anti-tamper SDKs look for.
- Playback consequence: `MediaDrm` provisioning and secure decoder sessions are tied to the calling
  application's identity and process. Hosting the guest as the host app cannot be verified to keep
  the L1 secure path, and GameDay's rules forbid making protected playback work by defeating those
  checks.

### 4.2 VirtualXposed (`android-hacker/VirtualXposed`)

- Licence: GPL-3.0 (the repository does carry `LICENSE.txt`).
- Architecture: a fork/embedding of VirtualApp with an Xposed-compatible hook layer; all guest apps
  run in one virtual space under one UID.
- Maintenance: last release "Initial support for Android 12" (0.22.0, June 2022); repository pushed
  in March 2024. Supported range documented as Android 5.0–10.0 in the README, with later work adding
  newer versions.
- Blockers for GameDay: it inherits the unlicensed VirtualApp component; it targets a *sandbox for
  hooking* rather than a faithful host; and its single-UID model is incompatible with the
  "preserve normal device integrity checks" requirement.

### 4.3 Waydroid

- Architecture: full Android userspace in LXC containers on a **GNU/Linux** host, sharing the host
  kernel through `binder`.
- Documented requirements: Linux, a Wayland session, `binder` (or `rust_binder`) and `ashmem`/`memfd`
  kernel support, systemd, and root to manage the container.
- Verdict: impossible on Fire OS 6. It is not an Android app; it is a Linux distribution feature.

### 4.4 Android Virtualization Framework (AVF)

- AOSP states AVF is supported **only on ARM64 devices**, introduced with Android 13, using pKVM at
  EL2 and `crosvm`.
- The `android.system.virtualmachine` API surface is `@SystemApi` (API 34) gated by
  `android.permission.MANAGE_VIRTUAL_MACHINE`, available to privileged/preinstalled apps.
- Verdict: impossible. Android 7.1, 32-bit, third-party app.

### 4.5 QEMU and Limbo (full-system / CPU emulation)

- QEMU's TCG can emulate ARM guests on ARM hosts, but without KVM/HVF-class acceleration the whole
  Android userspace, including its graphics stack, runs as interpreted or JIT-translated code.
- Hardware video decode inside the guest would have to be passed through from the host
  (`v4l2`/`mediacodec` emulation), which no maintained Android-on-Android QEMU configuration
  provides. DRM playback inside such a guest has no supported path at all.
- A 1.7 GHz quad-core ARMv7 SoC with 1.5 GB RAM is far below even generous estimates for booting and
  playing 1080p video under TCG.
- Verdict: not viable, and it would violate the project's requirement not to ship fake capability.

### 4.6 AOSP / Android platform features

The platform offers no third-party mechanism for one app to host another app's UI, input and DRM
session without root, a system signature, or a device-owner/managed-profile arrangement. Work
profiles (device owner) can create an isolated user, but they cannot be created by an ordinary
sideloaded app on Fire OS, and they would not move protected playback to a different security domain.

### 4.7 Out of scope by rule

APK mirror scraping, re-signed packages, patched NFL binaries and device-identity spoofing were
rejected before evaluation: they are outside the project's stated boundaries and would break the
security model the NFL app relies on.

## 5. Comparison matrix (weighted)

Weights reflect the project's stated priorities: compatibility and reliable authorized playback
first, then hardware acceleration and performance, then user experience and maintenance.

| Criterion | Weight | Native host | VirtualApp-class | Waydroid | AVF | QEMU/Limbo |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Protected playback compatibility | 25% | 5 | 1 | 2 | 3 | 1 |
| Hardware video decode | 20% | 5 | 3 | 2 | 3 | 1 |
| Performance on AFTMM | 15% | 5 | 3 | 3 | 3 | 0 |
| Rootless operation | 10% | 5 | 5 | 0 | 1 | 4 |
| API 25 availability | 10% | 5 | 3 | 0 | 0 | 3 |
| Licence / legal usability | 10% | 5 | 0 | 4 | 5 | 5 |
| Maintenance | 5% | 5 | 1 | 4 | 4 | 4 |
| Future macOS reuse | 5% | 2 | 0 | 3 | 2 | 4 |
| **Weighted score** | 100% | **4.85** | 2.15 | 2.10 | 2.70 | 2.05 |

Scores are 0–5, where 5 means "verified or architecturally certain" and 0 means "impossible or
prohibited". The native host wins on every criterion that affects whether a subscriber can actually
watch a game.

## 6. Findings that changed the design

1. **The NFL app is already a first-class Fire TV app.** The most compatible runtime is the platform
   itself. GameDay's value is orchestration, not virtualization.
2. **Application virtualization is legally unusable here**, independent of its technical merits: no
   licence grant exists for the only mature open-source engine.
3. **Protected playback is the constraint that eliminates everything else.** Any design that moves
   the guest out of its own sandbox puts Widevine, secure surfaces and app integrity at risk.
4. **Update discovery for the NFL app has no authorized public API.** The only supported path is the
   Amazon Appstore, so GameDay must delegate and then verify, not invent a feed.
5. **The target is 32-bit.** This alone rules out AVF and most modern virtualization work.
6. **Root is unavailable and irrelevant to the chosen design**, which keeps GameDay installable by an
   ordinary user through ADB sideloading.

## 7. What was still unverifiable

- Actual playback of protected NFL content on the device (requires a subscription and the physical
  device).
- Amazon Appstore deep-link behaviour on Fire OS 6 (`amzn://apps/android?p=…`) — documented by Amazon
  for Fire OS, but not exercised here.
- Measured runtime overhead, FPS, thermals and soak stability.

These are recorded as unverified in [COMPATIBILITY.md](COMPATIBILITY.md) rather than assumed.
