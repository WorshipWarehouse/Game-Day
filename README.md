# GameDay

**A lightweight, open-source Fire TV host for the official NFL app.**

GameDay is a single-purpose television launcher. It starts the official NFL application that you
installed from the Amazon Appstore, keeps it updated through the store, checks its signing identity,
recovers when something goes wrong, and gets out of the way. It is not a streaming service, not an
emulator, and not affiliated with the NFL.

```
Fire TV home  →  GameDay  →  official NFL app (native, hardware-decoded, Widevine-protected)
```

---

## Why it works this way

The honest answer to "how do I run the NFL app on a Fire TV Stick 4K from 2018" is: **the NFL app
already runs there natively.** Amazon's own device documentation lists `AFTMM` as Fire OS 6 /
Android 7.1 / API 25, and the NFL's published supported-device list includes Fire TV 6+. The official
Fire TV build (`com.gotv.nflgamecenter.us.lite`) declares `minSdk 25`, ships `armeabi-v7a` libraries,
and requires Android TV.

Every alternative was investigated and rejected with evidence (see
[docs/decisions/0001-runtime-selection.md](docs/decisions/0001-runtime-selection.md) and
[docs/RESEARCH.md](docs/RESEARCH.md)):

| Approach | Verdict |
| --- | --- |
| **Native hosting** (launch the Appstore build in place) | **Selected.** Keeps MediaCodec hardware decoding, secure surfaces, Widevine/HDCP and the app's own sign-in on the normal platform path. |
| Application virtualization (VirtualApp class) | Rejected. The open-source code is **unlicensed** (no licence file = all rights reserved), frozen since December 2017, and in-process hosting loses the secure playback path. |
| Waydroid container | Impossible: needs GNU/Linux, root, and a kernel binder driver. |
| Android Virtualization Framework | Impossible: Android 13+, ARM64, `MANAGE_VIRTUAL_MACHINE` system permission. |
| QEMU / Limbo full-system emulation | Not viable: software CPU emulation on a 1.7 GHz quad-core ARMv7 SoC cannot deliver 1080p hardware-decoded, DRM-protected video. |

GameDay is therefore a **host and update/managed-launch layer**, not a new runtime. That decision is
what makes 1080p60 hardware-decoded playback with working DRM possible at all on this hardware.

## One APK, two seatings

GameDay ships as a single package, `app.gameday.host`, that installs on a Fire TV **and** on a phone
or tablet. Nothing is stripped for either target: the layout adapts at runtime from what the device
reports, so there is one artifact to build, sign, verify and sideload.

| | Fire TV (leanback) | Phone / tablet (touch) |
| --- | --- | --- |
| Launcher entry | `LEANBACK_LAUNCHER` | `LAUNCHER` |
| Layout | 48 dp gutters, three-metre type scale, first row focused | 16–32 dp gutters, 48 dp touch targets, content centred past 720 dp |
| System bars | hidden (immersive) | left visible |
| Orientation | landscape, fixed by the device | rotates |
| NFL app source | Amazon Appstore (`amzn://` deep link) | Google Play (`market://`), used only when the Appstore is absent |

The active target is **Fire OS 6.7.1.1** (Fire TV Stick 4K, `AFTMM`: Android 7.1, API 25). That is
why no API-26+ call is allowed without a guard and why Lint runs as a build gate. Phone and tablet
support comes from the same code and exists so the host can be tried on hardware that is already on
the desk; it is not an Amazon-certified path.

## Runtime status (Phase 3)

GameDay has two runtimes, with deliberately different claims:

- **Fire TV / Android TV (`apps/fire-tv`)** — native host. Authorized playback works because the guest
  *is* the device's own app, with the device's Widevine L1 identity.
- **Apple silicon macOS (`runtime/desktop`)** — a genuine Android VM on the Avo model
  (Hypervisor.framework + unmodified AOSP Cuttlefish arm64 + gfxstream/Metal). Real isolation and a
  per-app desktop experience, but **no protected playback**: L1 needs a TEE, so a VM is L3 at best.

`./gradlew :runtime:desktop:hostVerdict` prints whether the current machine can boot that guest, and
refuses with reasons when it cannot. Research: [docs/PLATFORMS.md](docs/PLATFORMS.md); decision:
[docs/decisions/0003-platform-and-runtime.md](docs/decisions/0003-platform-and-runtime.md); executed
evidence: [docs/RUNTIME-POC.md](docs/RUNTIME-POC.md).

## What GameDay actually does

- **One-button launch.** Fire TV remote, D-pad only, no desktop, no configuration.
- **Capability truth.** Reports hardware decoders, Widevine, secure surfaces, ABI and storage as
  measured on the device, never assumed from the Android version.
- **NFL app update handling.** Reads the installed version exactly, delegates installation and
  updates to the Amazon Appstore (the authorized path), and detects the resulting version change
  plus any signing-certificate change (trust-on-first-use pinning).
- **GameDay self-updates.** Checks the project's GitHub releases, verifies SHA-256 and the APK's
  signing identity against the installed app, then hands it to Android's `PackageInstaller`.
- **Recovery.** Crash-loop guard with safe mode, bounded backoff, no infinite restart, and no
  deletion of NFL app data or sign-in state.
- **Diagnostics.** Redacted capability report (JSON + text), exportable and shareable.
- **No background services.** Update checks run at startup, at most every six hours while GameDay is
  in the foreground, and on demand.

## Verified target device

| Property | Value | Source |
| --- | --- | --- |
| Model | `AFTMM` — Fire TV Stick 4K (1st gen, 2018) | Amazon developer device specifications |
| OS | Fire OS 6, Android 7.1, **API 25** | Amazon Fire OS overview |
| SoC / CPU | MediaTek MT8695, quad-core 1.7 GHz | Amazon device specifications |
| ABI | **32-bit** application environment | Amazon device specifications |
| GPU | IMG PowerVR GE8300 | Amazon device specifications |
| RAM / storage | 1.5 GB DDR4 / 8 GB | Amazon device specifications |
| Root / bootloader | none / locked | device policy |
| Video | HEVC/AVC/VP9 up to 4K60, HDCP 2.2 | Amazon device specifications |

GameDay itself contains no native code, so its APK runs on every ABI (including `armeabi-v7a`).
`minSdk` is 25 and `targetSdk` is 34.

## Installation (Fire TV)

```bash
# 1. On the Fire TV: Settings → My Fire TV → About → press Select 7× (unlocks Developer Options)
#    Settings → My Fire TV → Developer Options → ADB Debugging → On
# 2. Find the device IP: Settings → My Fire TV → About → Network
./scripts/deploy.sh --connect 192.168.1.42
```

The release APK can also be sideloaded with any file-transfer tool:
`dist/gameday-1.0.1.apk`. Full instructions, including the on-device Appstore install of the NFL app,
are in [docs/FIRE-TV-SETUP.md](docs/FIRE-TV-SETUP.md).

## Build and test

Requires JDK 17 and an Android SDK with platform 34 and build-tools 34.0.0. No cloud CI, no paid
services, no NDK.

```bash
./scripts/setup.sh              # verify/locate the toolchain, write local.properties
./scripts/test.sh               # 129 JVM unit tests, plus Android Lint
./scripts/build.sh --all        # debug APK + compatibility test app
./scripts/package-release.sh    # signed, verified release APK in dist/
./scripts/deploy.sh --connect <fire-tv-ip>
./scripts/diagnose.sh           # device capability dump
./scripts/benchmark.sh          # cold/warm start, memory, frame stats
./scripts/qualify.sh --connect <fire-tv-ip>   # one-command qualification + consolidated report
./gradlew :runtime:desktop:hostVerdict        # can this machine boot the desktop Android guest?
```

Details: [docs/BUILD.md](docs/BUILD.md).

## Compatibility status

Split deliberately into what was verified here and what needs the device.

**Verified in this repository (reproducible):**

- APK builds reproducibly with JDK 17 + AGP 8.5.2 + Gradle 8.7.
- `aapt2` confirms `minSdk 25`, `targetSdk 34`, leanback-launchable activity, four permissions.
- `apksigner` confirms a 4096-bit RSA signature; the APK is 112 KB.
- 129 unit tests pass (version logic, update policy/backoff, signature and compatibility rules, split
  APK validation, redaction, JSON, crash guard, remote-key mapping, runtime state machine).
- `dist/gameday-1.0.1.apk` — `a24439b1c75e9b55033030443fd3c2e8fe9f069f68037b865bc9ccbdeed1f374…` (SHA-256 printed by the packaging script).

**Not verified here — requires the physical Fire TV:**

- Installation and startup on `AFTMM` (Fire OS 6).
- Launching the Appstore-installed NFL app and playing protected content.
- Measured cold/warm start, PSS, FPS, thermal and 4-hour soak figures.
- Amazon Appstore deep-link behaviour on the device.

Nothing in this repository claims those tests passed. See
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for the matrix and
[docs/PERFORMANCE.md](docs/PERFORMANCE.md) for what must still be measured.

## Known limitations

- **No input injection.** GameDay cannot send touches or keys into another app without an
  accessibility service or an overlay the guest accepts; it does not provide one, and does not
  interfere with secure input.
- **No silent installs.** Android requires user confirmation; GameDay stages a verified install
  session and reports the outcome.
- **Foreground detection needs usage access.** Without it, GameDay cannot tell whether the NFL app is
  running and says so instead of guessing.
- **No NFL app download API.** There is no authorized public version API; updates go through the
  Amazon Appstore, which GameDay opens and then verifies afterwards.
- **Guest crash reports are not visible** to a normal app. GameDay reports its own crashes and
  coarse guest exit, nothing more.
- **Fire OS may hide the usage-access screen.** The capability is then reported as unavailable.

## Security model

GameDay never touches NFL credentials, tokens, playback keys or content. It pins signing
certificates (trust-on-first-use plus explicit pins for its own updates), refuses downgrades,
requires HTTPS with an allowlisted host set, verifies SHA-256 before install, and redacts
credentials and identifiers from every diagnostic string. It has no telemetry, no external server,
and no analytics. See [SECURITY.md](SECURITY.md).

## Repository layout

```
apps/fire-tv/          Fire TV application (Java 17, no AndroidX, no Kotlin runtime)
runtime-api/           Backend-agnostic runtime contract and coordinator (JVM, shared by both apps)
runtime/desktop/       Apple-silicon Android VM backend (Avo-class) + host readiness verifier
core/                  Version, update, compatibility, install, redaction, diagnostics logic + tests
compatibility/test-app Own test application that validates the runtime on real hardware
scripts/               setup, build, test, deploy, diagnose, benchmark, package-release
docs/                  research, architecture, compatibility, performance, updates, roadmap
dist/                  Signed release APKs
```

## Documentation

- [docs/REVIEW-2026-11.md](docs/REVIEW-2026-11.md) — independent review of 1.0.0 and the ten fixes
- [docs/RESEARCH.md](docs/RESEARCH.md) — the runtime investigation and measurements
- [docs/RESEARCH-PHASE2.md](docs/RESEARCH-PHASE2.md) — adversarial second pass (BlackBox, managed profiles, closed VM apps)
- [docs/decisions/0002-isolation-and-runtime-feasibility.md](docs/decisions/0002-isolation-and-runtime-feasibility.md) — feasibility decision and revisit criteria
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — modules, flows, state machines
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) — verified/blocked matrix
- [docs/PERFORMANCE.md](docs/PERFORMANCE.md) — budgets, measurements, what remains
- [docs/UPDATES.md](docs/UPDATES.md) — the two independent update systems
- [docs/FIRE-TV-SETUP.md](docs/FIRE-TV-SETUP.md) — device setup and sideloading
- [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) — failure modes and recovery
- [docs/PLATFORMS.md](docs/PLATFORMS.md) — platform comparison for a genuine Android runtime
- [docs/RUNTIME-POC.md](docs/RUNTIME-POC.md) — what was executed, what the environment refused
- [docs/MACOS-ROADMAP.md](docs/MACOS-ROADMAP.md) — future Apple-platform plan
- [CHANGELOG.md](CHANGELOG.md), [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md)

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE). The licence keeps the door open for legally compatible
open-source components that GameDay may need later; no third-party runtime code is vendored today.

GameDay is an independent project. It is not affiliated with, endorsed by, sponsored by, or
supported by the National Football League, NFL Enterprises, or Amazon.
