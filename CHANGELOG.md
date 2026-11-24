# Changelog

All notable changes to GameDay are documented here. Format loosely follows Keep a Changelog;
versions follow semantic versioning, with Android `versionCode` as the installation authority.

## [Unreleased] — platform pivot

Phase 3: establishes which platform can host a *genuine* Android runtime, and encodes the answer.
The Fire TV application is unchanged and still fully tested.

### Added

- `runtime/desktop`: `AvoRuntimeBackend`, a `RuntimeBackend` for a real Android VM (Hypervisor.framework
  + unmodified AOSP Cuttlefish arm64, driven over adb), with 18 unit tests. Reports DRM, hardware
  video decode, update provisioning and suspend/resume as **unsupported**, each with its cause.
- `HostReadiness` + `DeviceProbe`-style host probe and `./gradlew :runtime:desktop:hostVerdict`: a
  machine-checkable go/no-go gate (macOS, Apple silicon, 8 GB RAM, 10 GB disk, adb) that refuses to
  start a guest and says why. Executed on the review host; output recorded in `docs/RUNTIME-POC.md`.
- `docs/PLATFORMS.md` (macOS, ARM64 Android, Android TV boxes, Raspberry Pi 5, mini PCs, Fire OS 8),
  `docs/RUNTIME-POC.md`, and ADR 0003 answering the platform questions.
- **One APK for TV and phone/tablet.** `FormFactor` derives gutters, splash type size, system-bar
  handling and initial focus from what the device reports, so the Fire TV layout is untouched while
  a phone gets 48 dp touch targets, rotation and visible system bars. Screens no longer pin
  `screenOrientation`.
- Google Play fallback for the guest listing when the Amazon Appstore is not installed, so a phone
  is not sent to an Amazon page that cannot install anything. The home screen names the store that
  is actually present.

### Changed

- `./scripts/test.sh` now runs `:runtime:desktop:test` and `:apps:fire-tv:testDebugUnitTest`
  (137 tests total).
- `docs/MACOS-ROADMAP.md` records Avo as the selected foundation and marks superseded assumptions.

### Not done, deliberately

- No Android guest was booted: the build host has no usable hypervisor, is not macOS, and cannot
  install the emulator's missing host libraries. Stage 5 (seamless desktop UX) is correctly blocked
  on that validation.

## [1.0.1] — 2026-11-24

Independent review pass. Ten defects fixed; no architecture change. See
[docs/REVIEW-2026-11.md](docs/REVIEW-2026-11.md).

### Fixed

- **Critical:** `PackageInstaller.SessionParams.setInstallReason()` (API 26) was called unguarded, so
  every staged install would have thrown `NoSuchMethodError` on Fire OS 6 (API 25). Now guarded, and
  Android Lint runs as a test gate for this class of bug.
- Install confirmations delivered as `STATUS_PENDING_USER_ACTION` are now forwarded, so staged
  installs complete on platform builds that hand the dialog back to the caller.
- Startup no longer blocks on the release check; the splash routes immediately and the check runs on
  a daemon thread, with its result persisted.
- "Restore default configuration" no longer clears the pinned NFL signing certificate (or version
  facts). Only user preferences are reset.
- `MENU` works more than once per screen.
- Long-press `BACK` only exits GameDay from the home screen.
- The cached update manifest now reports the published `minSdk` and `publishedAt` instead of
  reconstructing them locally.
- Reinstalls of the NFL app are distinguished from updates via `firstInstallTime`, so silent data
  loss is reported instead of hidden.
- Release checks send `If-None-Match` and honour `304 Not Modified`, avoiding repeated full fetches.
- A missing install permission is reported as `PERMISSION_REQUIRED` rather than a verification
  failure.

### Added

- `IsolationAssessment` (+7 tests) and `DeviceProbe.isolationFacts`: on-device evidence for the only
  rootless isolation candidate (managed profile), exported in diagnostics as `isolation`.
- `./scripts/qualify.sh`: one command for connection, identification, capability detection, install,
  launch timing, memory/CPU/graphics profiling, update-capability reporting, diagnostics and a
  consolidated `REPORT.md` + `report.json`.
- Android Lint (API-level and correctness) as part of `./scripts/test.sh`.
- Second-pass research report (`docs/RESEARCH-PHASE2.md`) and ADR 0002.

### Changed

- Version 1.0.1, `versionCode` 2, so this build installs over 1.0.0.
- Release APK: 112,132 bytes, SHA-256
  `a24439b1c75e9b55033030443fd3c2e8fe9f069f68037b865bc9ccbdeed1f374`; the reviewed 1.0.0 artefact
  (108,924 bytes) is kept in `dist/` for comparison.

## [1.0.0] — 2026-11-24

First Fire TV release. Native-hosting architecture; no emulator or virtualization engine.

### Added

- **Runtime abstraction** (`runtime-api`): `RuntimeBackend` contract, capability report with
  `SUPPORTED` / `UNSUPPORTED` / `REQUIRES_PERMISSION` / `REQUIRES_USER_ACTION` / `UNKNOWN`, and a
  `RuntimeCoordinator` that blocks automatic launches after three consecutive failures.
- **Native host runtime**: launches the Amazon Appstore build of `com.gotv.nflgamecenter.us.lite` in
  place; reports package metadata, signer digests, splits, installer package and launchability;
  probes hardware AVC/HEVC decoders, secure decoders and Widevine; samples its own PSS/CPU and the
  device's memory pressure.
- **Capability catalogue** that records the rejected runtimes (application virtualization, Waydroid,
  AVF, QEMU) with evidence, visible in the Compatibility screen and diagnostics.
- **NFL app update handling**: exact installed-version reading, trust-on-first-use signer pinning,
  version-change detection, Appstore deep-link delegation
  (`amzn://apps/android?p=…&intent=app_update`), last-known-working version tracking.
- **GameDay self-updates**: strict `gameday-update.json` manifest, GitHub release discovery with
  newest-compatible fallback, SHA-256 verification while streaming, APK signer/package/version
  verification, staged `PackageInstaller` install, install-result reporting.
- **Update policy**: six-hour interval, exponential backoff with cap, circuit breaker after five
  failures, manual checks always available.
- **Compatibility engine**: package identity, downgrade rejection, `minSdk`, ABI intersection,
  signer pin, launch intent, device features, storage headroom, split-set validation.
- **Diagnostics**: redacted JSON and text reports, device/runtime/host/update/resource/storage/network
  sections, bounded crash log, export to the app-specific directory, share through a non-exported
  read-only provider.
- **Fire TV UX**: leanback banner and launcher entry, branded startup screen, D-pad-only home,
  settings, compatibility report, diagnostics, updates and recovery screens; `MENU` → settings,
  `INFO` → diagnostics, long-press `BACK` → exit.
- **Recovery**: crash-loop guard with safe mode, runtime restart, temporary-file clearing, default
  configuration restore, and GameDay-only data reset. NFL app data is never touched.
- **Compatibility test application**: separate package, UID and signature; measures rendering FPS,
  storage persistence, audio output, hardware decoders, secure decoders, Widevine, network, input,
  memory and unprotected playback of an operator-supplied clip.
- **Scripts**: `setup.sh`, `build.sh`, `test.sh`, `deploy.sh`, `diagnose.sh`, `benchmark.sh`,
  `package-release.sh`, plus reproducible brand-asset generation.
- **Documentation**: research report, runtime decision record, architecture, compatibility matrix,
  performance report, build guide, Fire TV setup, updates, troubleshooting, macOS roadmap, security
  policy, contributor guide.

### Verified

- Reproducible build with JDK 17, Gradle 8.7, AGP 8.5.2 (`compileSdk 34`, `minSdk 25`,
  `targetSdk 34`).
- `aapt2`: `sdkVersion:'25'`, `targetSdkVersion:'34'`, leanback-launchable `StartupActivity`, four
  permissions, label "GameDay".
- `apksigner`: "Verifies", APK Signature Scheme v2, single RSA-4096 signer.
- Release APK size: 108,924 bytes. No native libraries. 168 DEX classes.
- 104 JVM unit tests passing.

### Not verified (requires the physical Fire TV)

- Installation and launch on `AFTMM` / Fire OS 6.
- Launching the Appstore-installed NFL app and protected playback with an NFL+ subscription.
- Measured cold/warm start, PSS, FPS, thermal behaviour, four-hour soak.
- Appstore deep-link behaviour on Fire OS 6.
- GameDay self-update against a published GitHub release.

### Known limitations

See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) §3: no input injection, no silent install, no NFL
version API, no guest crash capture, no suspend/resume of the guest.
