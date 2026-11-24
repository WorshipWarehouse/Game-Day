# Platform comparison for a genuine Android runtime

Phase 3 research. The question is no longer "can GameDay launch the NFL app on a Fire TV" (it can),
but "on which platform can GameDay provide a *genuine, isolated Android guest* — its own kernel, its
own Android userspace, hardware-accelerated graphics and supported media playback".

Prices are approximate street prices in late 2026 and are marked as such; every capability claim is
sourced.

## 1. What counts as a genuine runtime

| Category | Example | Is it a guest OS? | Isolation | Can it carry DRM identity? |
| --- | --- | --- | --- | --- |
| Hardware virtual machine | Avo (HVF), QEMU/KVM, Android Emulator | **yes** — own kernel, own Android userspace | strong (hypervisor) | **no** — L3 at best |
| OS-level container | Waydroid, Anbox | shares the host kernel | binder/namespace | no (no device certification) |
| Application-level virtualization | VirtualApp, BlackBox | no — guest runs in the host process | none at the Linux layer | no |
| Native host | GameDay Native (this repo) | n/a — the app *is* the device's app | the app's own sandbox | **yes** (the device's TEE) |

Only two categories can be called a runtime, and only one of them keeps the device's DRM identity:
the platform's own Android. That asymmetry drives everything below.

## 2. The DRM wall (the finding that orders the whole matrix)

- Widevine **L1 requires a hardware TEE** bound to a per-device certificate; L3 is software-only.
  Google's own documentation and Widevine's partner material state this, and Amazon's DRM overview
  spells out that L1/L2 need a TEE while L3 does not.
- The Waydroid maintainer, answering a request for L1 support: *"This is a requirement of Google to
  get widevine certification on a per device basis… as we are a generic device tree, along with all
  Android x86 variants, the only thing available to us is L3 (uncertified)"*
  (<https://github.com/waydroid/waydroid/issues/1408>).
- ExoPlayer's maintainer on casting to an emulator: *"Cast requires L1 support, and L1 support
  requires real hardware"* (<https://github.com/google/ExoPlayer/issues/10158>).
- Therefore: **every VM, container and emulator tops out at L3**, and content owners routinely require
  L1 for HD/4K on television form factors.

Consequence: an embedded Android runtime can *host* an app, but it cannot *authorize* premium
playback. Any plan that says otherwise is wrong, on every platform in this document.

## 3. Candidate platforms

### 3.1 Apple Silicon macOS

| Property | Finding |
| --- | --- |
| Virtualization | Apple **Hypervisor.framework** (HVF), used directly by Avo; also QEMU with `-accel hvf`, and Virtualization.framework (Linux guests) |
| Best OSS runtime | **Avo** — Apache-2.0, `nizwar/avo`, created 2026-10, 338 commits, v1.0.0 released 2026-10-27 |
| Guest | unmodified **AOSP Cuttlefish arm64** image (`aosp_cf_arm64_only_phone-userdebug`, Android 17), fetched from Google's public build server |
| Graphics | **gfxstream + Metal** (through MoltenVK) — real GPU composition, not software rendering |
| Experience | every app in its own macOS window with a Dock icon, Launchpad/Spotlight entries, notifications, clipboard, drag-and-drop, controller support; persistent data; notarized; self-updating |
| Requirements | Apple silicon (M1+), macOS 15+, ~10 GB free disk, 26 MB download |
| DRM reality | **L3 only.** Avo's own spec says bank/e-wallet/DRM apps are not promised and that *"Play Integrity and hardware attestation cannot be bypassed in a VM"* |
| Store reality | Google Play services are **not bundled** (GMS distribution needs a Google licence); no Amazon Appstore either |
| Cost | Mac mini M4 ~$599 new; used M1 mini / MacBook Air roughly $400–500 (approx.) |
| Verdict | **The best foundation for a desktop Android runtime, and still not enough for authorized NFL playback.** It is the right macOS host; it is not a way to watch NFL+ |

### 3.2 Modern ARM64 Android devices (phones/tablets)

| Property | Finding |
| --- | --- |
| Virtualization | **AVF** (pKVM + crosvm), Android 13+/API 34, **ARM64 only**, `MANAGE_VIRTUAL_MACHINE` system permission |
| Can an app boot its own Android image? | **No.** AOSP: the bootloader only permits pVM images *signed by Google or the device vendor*, so *"apps running pVMs can't bundle their own kernels"*; the supported payload is Microdroid, not a full Android GUI |
| DRM | L1 present **natively**, not inside a pVM |
| Verdict | AVF is for trusted applets (biometrics, DRM components), **not** for hosting an arbitrary Android app. No embedded runtime here |

### 3.3 Android TV / Google TV boxes (certified)

| Property | Finding |
| --- | --- |
| Runtime | the device's own Android (Google TV 12–14); no usable AVF for third-party apps |
| DRM | **L1** (certified devices), HDCP, secure surfaces |
| App availability | Google Play (phone build) or Amazon Appstore (Fire TV build) |
| Cost | ~$100–150 for a certified 4K box (approx.; verify L1 per model) |
| Verdict | **Best platform for authorized playback.** Not a host for an embedded guest — but it is the platform where GameDay's existing native architecture delivers everything the user actually wants |

### 3.4 Raspberry Pi 5

| Property | Finding |
| --- | --- |
| Android build | KonstaKANG **LineageOS 24.0 Android TV (Android 17)**, Oct 2026; AOSP 17 also available |
| Graphics | VideoCore VII / **V3D with OpenGL and Vulkan** |
| Media | **hardware H.265 decode** (and the build reports it to the framework) |
| DRM | not certified; **Widevine L3** must be flashed separately (tied to the Android version); no L1, no Chromecast |
| Store | MindTheGapps possible, but Play often withholds streaming apps on uncertified devices |
| Licensing | the LineageOS build includes components under **CC BY-NC-SA 4.0 (non-commercial)** — a redistribution problem for an open-source project |
| Cost | Pi 5 4 GB ~$60 + PSU + SD/NVMe ≈ $90–110 (approx.) |
| Verdict | an excellent *testbed* for Android-on-ARM and a poor product platform: no L1, uncertified, licence-contaminated build |

### 3.5 Intel/AMD mini PCs

Android-x86/Bliss OS in a VM or on bare metal. GPU acceleration via Mesa works; **no certification, no
L1**, streaming apps are frequently unavailable in Play and refuse HD. Cost $150–300. Strictly worse
than a Pi for this purpose and worse than a certified box for playback.

### 3.6 Newer Fire TV hardware (Fire OS 8)

| Property | Finding |
| --- | --- |
| Devices | Fire TV Stick 4K Max 2nd gen (`AFTKRT`, 16 GB), Fire TV Stick 4K 2nd gen (`AFTKM`, 8 GB) |
| OS | Fire OS 8 = **API 30** (Android 11), ARM64-capable |
| DRM | L1, HDCP, Amazon Appstore |
| Runtime options | still no AVF (needs API 34 + system permission), still no root |
| Cost | ~$40–60 (approx.) |
| Verdict | the **cheapest viable hardware** for the product as it exists: authorized playback, twice the RAM of `AFTMM`, supported Appstore updates |

## 4. Runtime projects, verified

| Project | Licence | Status | What it actually does | Verdict for GameDay |
| --- | --- | --- | --- | --- |
| **Avo** (`nizwar/avo`) | Apache-2.0 | Active (v1.0.0, Oct 2026, 338 commits) | HVF VMM (Rust) + AOSP Cuttlefish arm64 + gfxstream/Metal, per-app macOS windows | **Selected** as the desktop foundation |
| QEMU + HVF/KVM | GPL-2.0 | Active | generic machine emulation; needs a guest image and a graphics stack | viable but reinventing Avo; kept as fallback |
| Android Emulator | proprietary (SDK) | Active | goldfish/ranchu AVDs; HVF on macOS, KVM on Linux; **L3 only** | dev tool, not a product runtime |
| Virtualization.framework | Apple framework | Active | ARM64 Linux/macOS guests, no Android userspace | wrong abstraction |
| Hypervisor.framework | Apple framework | Active | bare VT-x/EL2 access — what Avo uses | building block, not a runtime |
| Mandroid | — | **not verifiable** as a maintained Android runtime | searches surface no credible repository or release | discarded |
| AVF | Apache-2.0 | Active, API 34+/ARM64 | signed pVM images only (Microdroid) | cannot host a full Android UI guest |
| Waydroid | GPL-3.0 | Active | Linux containers, binder, **L3 only**, needs root | impossible on Fire OS, no DRM |
| Anbox | Apache-2.0 | archived | Linux containers | dead |
| Cuttlefish | Apache-2.0 | Active | the AOSP virtual device Avo boots | image source, not a host |
| VirtualApp / BlackBox | none / unclear | frozen / deleted upstream | in-process virtualization | rejected (see ADR 0001/0002) |

## 5. Feature comparison for the requirement list

| Requirement | Avo on macOS | Android TV box (native) | Pi 5 (LineageOS) | Fire TV 4K Max (Fire OS 8) |
| --- | --- | --- | --- | --- |
| Genuine isolated Android guest | **yes** | no | n/a (it *is* Android) | no |
| Hardware GPU acceleration | yes (gfxstream/Metal) | yes | yes (V3D) | yes |
| Hardware video decode in guest | **no (software in Cuttlefish)** | yes | yes (H.265) | yes |
| Widevine L1 | **no** | **yes** | no (L3 flashable) | **yes** |
| App store / sign-in for a streaming app | **no** | yes | partial, uncertified | yes (Amazon) |
| Persistent guest storage | yes | yes | yes | yes |
| Single-app window / seamless launch | yes (native macOS windows) | yes (leanback) | yes | yes |
| Auto-updates of the hosted app | no (no store) | yes | no | yes |
| Cost | $400–600 | $100–150 | $90–110 | **$40–60** |

The pattern is unambiguous: **isolation and authorized playback are mutually exclusive outside a
certified device.** Avo gives the isolation and loses playback; a certified Android TV gives playback
and has no guest to isolate.

## 6. Measured in this review

The build environment was measured rather than assumed, because the first question a runtime claim
must answer is "where did you run it":

| Fact | Value |
| --- | --- |
| Host OS / arch | Linux x86_64, kernel 6.1.102, **not macOS** |
| `/dev/kvm` | node exists (`root:kvm`), **open denied (EPERM)** — no `kvm` group, no sudo (`no-new-privileges`), no Docker |
| QEMU | not installed; no root to install it; apt has no candidate |
| Android Emulator host libraries | `libpulse.so.0`, `libnss3.so`, `libasound.so.2` **missing**, uninstallable without root |
| CPU / RAM / free disk | 2 vCPU / 3.8 GB / 8.1 GB |
| Effect | **no Android guest can boot in this environment**, with or without a hypervisor |

`./gradlew :runtime:desktop:hostVerdict` reproduces this verdict as JSON on any machine. The same
command on an Apple Silicon Mac is the go/no-go gate for the desktop runtime.

## 7. Sources

- Avo: <https://github.com/nizwar/avo> (README, `docs/spec.md`, `docs/engine.md`, `docs/architecture.md`, releases)
- AOSP: <https://source.android.com/docs/core/virtualization> and `/security` (ARM64-only, signed pVM images, no app-supplied kernels)
- Waydroid L1 request and maintainer answer: <https://github.com/waydroid/waydroid/issues/1408>
- ExoPlayer emulator/cast L1: <https://github.com/google/ExoPlayer/issues/10158>
- Widevine platform support: <https://developers.google.com/widevine/drm/overview>; Amazon DRM overview (TEE requirement for L1/L2)
- Amazon Fire OS versions and Fire OS 8 devices: <https://developer.amazon.com/docs/fire-tv/fire-os-overview.html>
- KonstaKANG Raspberry Pi 5 LineageOS 24 ATV: <https://konstakang.com/devices/rpi5/LineageOS24-ATV/>; AOSP 17: <https://konstakang.com/devices/rpi5/AOSP17>
- Raspberry Pi 5 hardware: <https://www.raspberrypi.com/news/introducing-raspberry-pi-5>
