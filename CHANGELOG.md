# Changelog

All notable changes to GameDay are documented here. Format loosely follows Keep a Changelog;
versions follow semantic versioning, with Android `versionCode` as the installation authority.

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
