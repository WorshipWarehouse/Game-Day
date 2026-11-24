# Architecture

## 1. Shape of the system

```
┌──────────────────────────────────────────────────────────────┐
│ apps/fire-tv  (Android app, Java 17, no AndroidX, no Kotlin)  │
│                                                              │
│  ui/            Startup, Home, Settings, Diagnostics,        │
│                 Compatibility, Updates, Recovery             │
│  runtime/       NativeHostRuntime, RuntimeCatalog            │
│  install/       ApkInspector, ApkVerifier, PackageInstaller  │
│  update/        GameDayUpdateManager, NflUpdateManager,      │
│                 HttpFetcher                                  │
│  diagnostics/   DeviceProbe, CrashLog, DiagnosticsExporter    │
│  settings/      SettingsStore (SharedPreferences + KV)       │
└───────────────┬───────────────────────────┬──────────────────┘
                │                           │
      ┌─────────▼─────────┐       ┌─────────▼──────────┐
      │ runtime-api (JVM) │       │ core (JVM)         │
      │ RuntimeBackend    │       │ version/update     │
      │ RuntimeCoordinator│       │ compatibility      │
      │ capability model  │       │ install planning   │
      └───────────────────┘       │ redaction, JSON    │
                                  │ crash guard, input │
                                  └────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ compatibility/test-app — separate package, UID and signature  │
│ validates rendering, storage, audio, decoders, DRM, network,  │
│ input and isolation on real hardware                          │
└──────────────────────────────────────────────────────────────┘
```

Dependencies point one way: `apps/fire-tv` → `runtime-api` + `core`. Neither JVM module imports an
Android type, which is what makes the state machines and rules exhaustively testable on any machine
and reusable by a macOS front end later.

## 2. Why these modules exist

- **`core`** holds every rule that can be wrong in a way that matters: version comparison, update
  eligibility and backoff, APK compatibility, split-set validation, URL policy, redaction, JSON
  parsing, crash-loop detection and remote-key mapping. All of it is pure Java and unit tested.
- **`runtime-api`** is the seam demanded by the mission: a backend-agnostic contract, a capability
  model that can say "unsupported with a reason", and a coordinator that owns the launch lifecycle
  and failure budget.
- **`apps/fire-tv`** is deliberately thin: platform probing, intents, `PackageManager`,
  `PackageInstaller`, `MediaCodec`/`MediaDrm` inspection, and views.
- **`compatibility/test-app`** exists so claims about the runtime can be checked by a separate
  application, not by the thing being tested.

## 3. Runtime contract

`RuntimeBackend` models: descriptor, availability, initialization (returning a capability list),
host app info, launch, stop, suspend, resume, running state, install, resources, storage, shutdown.

`Capability` pairs a capability with a `CapabilityStatus`:

| Status | Meaning |
| --- | --- |
| `SUPPORTED` | Verified working by this backend on this device |
| `UNSUPPORTED` | Cannot be provided; the reason is shown to the user |
| `REQUIRES_PERMISSION` | Possible after a specific access is granted |
| `REQUIRES_USER_ACTION` | Possible after the user acts (store install, uninstall) |
| `UNKNOWN` | Not probed |

`NativeHostRuntime` reports, among others:

- `APP_INFO`, `APP_LAUNCH`, `GUEST_ISOLATION`, `PERSISTENT_STORAGE`, `RESOURCE_MONITORING` —
  supported.
- `APP_INSTALL`, `SPLIT_APK_INSTALL` — supported with user confirmation.
- `PROCESS_MONITORING`, `CRASH_DETECTION` — `REQUIRES_PERMISSION` (usage access), otherwise the UI
  says it cannot observe foreground state instead of guessing.
- `SUSPEND_RESUME`, `POINTER_INJECTION`, `KEY_INJECTION` — `UNSUPPORTED`, with the reason.
- `HARDWARE_VIDEO_DECODE`, `DRM_PROTECTED_PLAYBACK` — derived from the actual codec list,
  `isFeatureSupported(FEATURE_SecurePlayback)` and `MediaDrm.isCryptoSchemeSupported(Widevine)`.

## 4. Launch lifecycle

`RuntimeCoordinator` is the only thing the UI calls:

```
UNINITIALIZED ──initialize()──► PROBING ──ok──► READY ──launchGuest()──► LAUNCHING
                                    │                                     │
                                    ├──unavailable──► UNAVAILABLE         ├──ok──► RUNNING
                                    └──failure──────► ERROR               └──fail──► ERROR
                                                                                   │
                                          3 consecutive failures ──► BLOCKED ◄─────┘
                                                                        │
                                                         clearFailures() └──► UNINITIALIZED
```

Properties that matter on a television:

- `BLOCKED` refuses further automatic launches. The user must visit Recovery. This is the anti-boot-loop
  guarantee, tested in `RuntimeCoordinatorTest`.
- A throwing backend is converted to a `RuntimeResult`, never propagated into the UI thread.
- Every transition is recorded in a bounded 24-entry history that appears in diagnostics.
- `CrashGuard` independently watches process starts: three starts inside five minutes without a
  healthy exit puts the app into safe mode before any guest launch is attempted.

## 5. Startup flow

1. `GameDayApplication.onCreate` builds the service graph and records a boot timestamp.
2. `StartupActivity` shows the brand screen immediately, then on a background thread:
   initializes the runtime, observes the installed NFL app (recording its signer on first use), and
   runs a GameDay update check if the six-hour policy allows it.
3. Routes to safe mode (Recovery), Home, or straight into the guest when `autoLaunch` is on and the
   guest is installed, launchable and signer-trusted.

The splash has a 600 ms floor so it never flashes, and no ceiling other than the work itself.

## 6. Update paths

Two independent managers, deliberately not coupled:

- **`NflUpdateManager`** — reads the installed version exactly, pins the signing certificate
  (trust-on-first-use), notices version changes made by the Appstore between launches, opens the
  official store page, and records the last version that launched successfully.
- **`GameDayUpdateManager`** — polls the project's GitHub releases for a `gameday-update.json` asset,
  validates it strictly, rates-limits and backs off, downloads over HTTPS with allowlisted hosts,
  hashes while streaming, verifies the APK's signer against the installed app, then stages a
  `PackageInstaller` session.

Full detail: [UPDATES.md](UPDATES.md).

## 7. Verification chain for any APK

1. Parse the archive with `PackageManager.getPackageArchiveInfo`.
2. Extract package, version code, `minSdk`/`targetSdk`, ABI slices (from `lib/<abi>/` entries in the
   zip), signer SHA-256, split names, required features.
3. Validate the install set (`InstallPlan`): exactly one base, consistent package/version/signers,
   unique split names.
4. Evaluate `CompatibilityRules`: package, downgrade, min SDK, ABI intersection, signer pin, launch
   intent, device features, storage headroom.
5. Only then stage the session.

Identity comes first. If the signing certificate cannot be established, nothing is installed.

## 8. Storage and privacy

- GameDay's own data lives in `SharedPreferences` (`gameday`) plus a bounded crash log; nothing else
  is written except update downloads (cache) and exported reports (app-specific external directory).
- The NFL app's data is never read, moved, backed up or deleted. Reset actions in Settings and
  Recovery are explicitly scoped to GameDay and say so.
- File sharing goes through a non-exported `ContentProvider` that only serves files inside the
  diagnostics directory.

## 9. UI approach

Native `Activity` + programmatic views: no AndroidX, no XML layout inflation, no animation library.
Each screen is a header, a scrollable column of focusable rows, and a footer. D-pad traversal is the
platform's; focus is shown with an accent bar and a 2 dp outline. `MENU` opens settings, `INFO` opens
diagnostics, long-press `BACK` leaves GameDay. This is a few hundred kilobytes of code and starts in
well under the budget on a 1.7 GHz Cortex-A53.

## 10. Deliberate non-goals

- No emulator, no hypervisor, no guest kernel.
- No accessibility-service key/pointer injection.
- No analytics, telemetry, crash upload or account system.
- No background service or `JobScheduler` polling: checks happen while GameDay is in the foreground.
- No NDK, no CMake, no native libraries.

## 11. Future macOS reuse

The contract, the coordinator, all of `core`, the update manifest format, the diagnostic schema and
the redaction rules are platform-neutral. A macOS app would implement `RuntimeBackend` over a
different mechanism and reuse everything else. See [MACOS-ROADMAP.md](MACOS-ROADMAP.md).
