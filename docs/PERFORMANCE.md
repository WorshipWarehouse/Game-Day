# Performance

This document separates **measured** facts from **targets** and **unmeasured** device behaviour. No
number below was invented; anything that needs the physical Fire TV is marked as such.

## 1. Measured here

| Metric | Value | Method |
| --- | --- | --- |
| Release APK size | **112,132 bytes (110 KiB)** | `du -h dist/gameday-1.0.1.apk` |
| Debug APK size | 160 KiB | `ls -lh …/fire-tv-debug.apk` |
| Compatibility test APK | 64 KiB | `ls -lh …/test-app-debug.apk` |
| DEX classes in release APK | 168 | `dexdump -f dist/gameday-1.0.1.apk` |
| Native libraries | none | APK contains no `lib/` entries |
| Third-party runtime libraries | none (no AndroidX, no Kotlin stdlib, no JSON lib) | Gradle dependency graph |
| Unit tests | 104, all passing | `./scripts/test.sh` |
| Warm `assembleDebug` (both APKs) | ~14 s | Gradle console output on a 2-vCPU VM |
| Warm `assembleRelease` | ~21 s | `./scripts/package-release.sh` |
| Signing | RSA-4096, APK Signature Scheme v2 | `apksigner verify --verbose` |

1.0.0 measured 108,924 bytes; 1.0.1 adds the isolation probe and conditional-request support, and
is 112,132 bytes. Both are in `dist/`.

The APK is **~0.4 %** of the 30 MB budget. There is essentially nothing to shrink further: the
remaining bytes are the platform-required manifest, `resources.arsc`, five icon densities and one
banner.

## 2. Engineering targets and status

| Target | Status |
| --- | --- |
| Native startup under 3 s | Not measured on device. Architecture is designed for it (no DI framework, no XML inflation, one background thread, 168 classes). Verify with `./scripts/benchmark.sh`. |
| Warm resume under 1 s | Not measurable by GameDay (the guest is a separate app). GameDay's own resume rebuilds one screen of views. |
| Interface rendering up to 60 FPS | Not measured on device. No animations, no images in layouts, no `RecyclerView`; ~20 views per screen. |
| Video 1080p60 where authorized | Not verified. GameDay never touches the video path: decode is the NFL app's `MediaCodec` session on the platform's hardware decoder. |
| Host memory overhead < 100 MB | Not measured on device. Budget analysis below. |
| A/V sync within 50 ms | Out of GameDay's control; it renders no video or audio. |
| APK < 30 MB | **Measured: 0.11 MB.** |
| 4 h continuous operation | Not measured. Use `./scripts/benchmark.sh --soak 240`. |
| No persistent memory leaks | Design: no static view references, no listeners registered without unregistering, no caches. Verify with `dumpsys meminfo` during a soak. |
| No unnecessary background services | **Enforced by design**: no `Service`, no `JobScheduler`, no `AlarmManager`. Checks run only while an Activity is in the foreground. |
| No external server | **Enforced**: only `api.github.com` / `github.com` release assets. |
| No extra subscription | **Enforced**: GameDay has no billing code and no account. |

## 3. Memory budget analysis (host only)

The release APK's own footprint is dominated by the platform, not by GameDay:

| Component | Estimate |
| --- | --- |
| DEX + loaded GameDay classes | ~1.5 MB of mappings |
| Views on a typical screen (~20 rows, 3 `TextView`s each) | < 2 MB |
| `SharedPreferences` (≈20 keys) | < 64 KB |
| Crash log (max 16 entries, 4 KB each) | ≤ 64 KB |
| Update download buffer | 64 KB |
| **Expected total PSS** | **well under 40 MB**, versus the 100 MB budget |

The guest's memory is excluded by definition: it runs in its own process exactly as it would without
GameDay, plus the launcher's small resident set.

## 4. Design choices made for performance

- **No AndroidX, no Kotlin runtime, no JSON library.** The whole app is 168 classes.
- **Programmatic views instead of inflated XML.** No layout parser on the startup path.
- **No image loading library.** Icons are static PNGs; the splash is three `TextView`s.
- **One background thread per operation**, daemon, no executor pool — nothing survives the screen.
- **No polling.** Update checks: startup, then at most every six hours while foreground, plus manual.
- **Bounded everything**: crash log entries, state history, HTTP response sizes, redirect count,
  update size, backoff attempts.
- **No frame copies.** GameDay renders no video; it never creates a `SurfaceTexture`, never touches
  `MediaCodec` buffers, and never reads pixels.
- **No `largeHeap`.** Requesting it would only mask leaks and increase pressure on a 1.5 GB device.

## 5. What the device run must record

Run on the Fire TV and attach the artefacts to a release:

```bash
./scripts/benchmark.sh --runs 5            # cold/warm start, meminfo, gfxinfo, thermal
./scripts/benchmark.sh --soak 240          # 4-hour stability with PSS samples
./scripts/diagnose.sh                      # device capability and decoder dump
```

Acceptance for the first hardware-qualified release:

1. Cold start (`am start -W` total time) median < 3000 ms over 5 runs.
2. GameDay PSS < 100 MB at rest; no upward trend over the soak.
3. No `AndroidRuntime` fatal in `logcat` during the soak.
4. Guest launches and reaches its home screen on the first attempt in 5/5 runs.
5. Protected playback starts and holds for 30 minutes without a decoder error.

Until those numbers exist in `artifacts/`, this document will not claim them.
