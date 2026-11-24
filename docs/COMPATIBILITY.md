# Compatibility

Two tables: what was verified in this repository, and what still requires the physical device.
Nothing moves from the second table to the first without evidence from an `AFTMM` (or a documented
successor) device.

## 1. Verified here (reproducible on any machine)

| Item | Result | How it was checked |
| --- | --- | --- |
| Builds with JDK 17 + Gradle 8.7 + AGP 8.5.2 | Pass | `./scripts/build.sh --all` |
| `minSdk` = 25 | Pass | `aapt2 dump badging` → `sdkVersion:'25'` |
| `targetSdk` = 34 | Pass | `aapt2 dump badging` → `targetSdkVersion:'34'` |
| Release APK signed, v2 scheme | Pass | `apksigner verify --verbose` → "Verifies", v2 true |
| APK size | 112,132 bytes (112 KB per `du -h`) | `du -h dist/gameday-1.0.1.apk` |
| No native libraries (runs on any ABI, incl. `armeabi-v7a`) | Pass | `aapt2`, `unzip -l` (no `lib/` entries) |
| Leanback-launchable activity | Pass | `aapt2` → `leanback-launchable-activity: …StartupActivity` |
| Permissions are minimal and declared | Pass | 4 permissions (INTERNET, ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES, KILL_BACKGROUND_PROCESSES) |
| Compat test app builds, own package and UID | Pass | `app.gameday.compat.test`, minSdk 25, leanback launchable |
| Unit tests | 111 pass, 0 fail | `./scripts/test.sh` |
| Android Lint (API-level correctness) | 0 errors | `./scripts/test.sh` (Lint step) |
| Managed-profile feasibility probe | Implemented, unit tested; device verdict pending | `IsolationAssessment`, `isolation` in diagnostics |
| Version/update/backoff logic | Pass | `core` tests |
| APK and split-set verification rules | Pass | `CompatibilityRulesTest`, `InstallPlanTest` |
| Credential/PII redaction | Pass | `RedactorTest` |
| Crash-loop guard and safe mode | Pass | `CrashGuardTest`, `RuntimeCoordinatorTest` |
| JSON parser strictness | Pass | `JsonTest` |
| Remote key mapping and long-press | Pass | `RemoteKeyMapTest` |

## 2. Requires the physical Fire TV (not verified)

| Item | Status | How to verify |
| --- | --- | --- |
| Install and launch on Fire OS 6 (`AFTMM`) | **Not verified** | `./scripts/deploy.sh --connect <ip>` |
| Appears in the Fire TV launcher with banner and icon | **Not verified** | visual check on device |
| Cold start under 3 s / warm resume under 1 s | **Not verified** | `./scripts/benchmark.sh` |
| NFL app detection, version and signer reporting | **Not verified** | Settings → NFL app updates; Diagnostics |
| Launching the Appstore-installed NFL app | **Not verified** | Home → "Watch NFL" |
| Appstore deep link `amzn://apps/android?p=…` | **Not verified** | Home → "Get the NFL app" |
| NFL app update via Appstore, then version change detected | **Not verified** | update in Appstore, reopen GameDay |
| Protected NFL playback (NFL+ subscription) | **Not verified** | play any authorized title |
| Hardware decode confirmed during playback | **Not verified** | `./scripts/diagnose.sh` decoder list + playback stats |
| GameDay self-update download + install | **Not verified** | needs a published release and a device |
| Usage-access foreground detection | **Not verified** | grant usage access, then check Compatibility screen |
| 4-hour soak without crash | **Not verified** | `./scripts/benchmark.sh --soak 240` |
| Thermal behaviour during playback | **Not verified** | `./scripts/benchmark.sh` (`dumpsys thermalservice`) |
| Managed-profile availability on Fire OS 6 | **Not verified** | `./scripts/qualify.sh` → `REPORT.md` (Managed users feature), diagnostics `isolation` |
| Install permission state and usage-access state | **Not verified** | `./scripts/qualify.sh` → `updates.txt`, `REPORT.md` |
| Consolidated hardware qualification | **Not verified** | `./scripts/qualify.sh --connect <ip>` writes `artifacts/qualify-*/REPORT.md` |

## 3. Known limitations (by design, not defects)

| Limitation | Why |
| --- | --- |
| No touch/key injection into the NFL app | Requires an accessibility service or an accepted overlay; the project does not add either, and must not interfere with secure input. |
| No silent NFL install or update | Android requires user confirmation for `PackageInstaller`; GameDay stages and reports. |
| No NFL version feed | No authorized public API exists. GameDay observes the installed version and delegates to the Appstore. |
| Guest crash/ANR details unavailable | A normal app cannot read another app's crash records. GameDay reports its own crashes and coarse foreground changes. |
| No suspend/resume of the NFL app | It owns its own lifecycle as a separately launched application. |
| Fire OS may hide the usage-access screen | Then `PROCESS_MONITORING` stays `REQUIRES_PERMISSION` and GameDay says so. |
| No 4K-specific handling | The app never renders video itself; it inherits whatever the NFL app negotiates with the display. |

## 4. Claimed vs. measured — a rule for this repository

- Anything in §1 may be stated as working.
- Anything in §2 must be stated as "not verified on hardware" until a maintainer records a device
  run in `artifacts/`.
- Capability reports inside the app come from live probes (`MediaCodecList`, `MediaDrm`,
  `PackageManager`, `StatFs`), never from a device allowlist.
