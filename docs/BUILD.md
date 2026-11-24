# Building GameDay

Everything builds locally. No cloud CI, no paid service, no NDK, no emulator.

## 1. Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | **17** | Temurin, OpenJDK, or Android Studio's JBR. AGP 8.5 requires 17+. |
| Android SDK | platform **34**, build-tools **34.0.0**, platform-tools | Installed by `sdkmanager` or Android Studio |
| Gradle | 8.7 | Provided by the wrapper; nothing to install |
| Python 3 + Pillow | any recent | Only for regenerating brand assets |

`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) must point at the SDK, or `local.properties` must exist.

## 2. First run

```bash
./scripts/setup.sh
```

It verifies Java, locates or installs the SDK packages, writes `local.properties`, generates the
Gradle wrapper if missing, and makes the scripts executable. It needs no root.

## 3. Everyday commands

```bash
./scripts/test.sh                     # 104 JVM unit tests + summary table
./scripts/build.sh                    # debug APK for the launcher
./scripts/build.sh --all              # + compatibility test app
./scripts/build.sh --release          # alias for package-release.sh
./scripts/package-release.sh          # signed + verified release APK in dist/
./scripts/deploy.sh --connect <ip>    # install and launch on the Fire TV
./scripts/diagnose.sh                 # device capability dump into artifacts/
./scripts/benchmark.sh --runs 5       # start-up, memory, frame and thermal numbers
```

Raw Gradle, if you prefer:

```bash
./gradlew :core:test :runtime-api:test
./gradlew :apps:fire-tv:assembleDebug
./gradlew :apps:fire-tv:assembleRelease
./gradlew :compatibility:test-app:assembleDebug
```

## 4. Project configuration

- `compileSdk 34`, `minSdk 25`, `targetSdk 34`.
- Java 17 source/target; no Kotlin plugin, no annotation processors, no desugaring library.
- `android.useAndroidX=false`: the app uses platform views only.
- Release builds are **not minified**: the APK is 108 KB and an unminified build is easier to audit
  and reproduce. `proguard-rules.pro` exists so a future minified build stays safe.
- `org.gradle.daemon=false` and a 1.5 GB heap so the build works on a small machine.

## 5. Signing

`./scripts/package-release.sh` creates `signing/gameday-release.jks` on first run with a random
password, stores it in `signing/keystore.properties`, and passes it to Gradle through
`-Pgameday.storeFile` / `-Pgameday.storePassword` / `-Pgameday.keyAlias` / `-Pgameday.keyPassword`.

- Both files are **git-ignored** and must never be committed.
- **Back up the keystore**: an update must be signed with the same key or Android refuses to install
  it, and GameDay's own verifier refuses it before that.
- Without any signing properties, `assembleRelease` falls back to the debug key — fine for local
  experiments, never for distribution.

## 6. Reproducibility

- All versions are pinned (`gradle/wrapper/gradle-wrapper.properties`, AGP in `build.gradle`).
- The build downloads only Gradle, AGP and JUnit from Google Maven / Maven Central.
- No timestamps or random values are embedded in the APK; the same inputs produce the same DEX.
- `dist/` contains the exact artefact this documentation refers to, with its SHA-256 printed by the
  packaging script.

## 7. Regenerating brand assets

```bash
python3 scripts/generate-brand-assets.py
```

Writes the launcher icons (mdpi…xxxhdpi) and the 320×180 Fire TV banner. The artwork is original and
uses no league, team or broadcaster marks.

## 8. Layout

```
settings.gradle          modules: :core, :runtime-api, :apps:fire-tv, :compatibility:test-app
build.gradle             AGP version, clean task
gradle.properties        JVM heap, no AndroidX, no codegen defaults
core/                    java-library + JUnit tests
runtime-api/             java-library + JUnit tests
apps/fire-tv/            com.android.application
compatibility/test-app/  com.android.application (separate package and signature)
scripts/                 setup, build, test, deploy, diagnose, benchmark, package-release
```

## 9. Build troubleshooting

| Symptom | Fix |
| --- | --- |
| `SDK location not found` | `export ANDROID_HOME=…` or run `./scripts/setup.sh` |
| `failed to find target with hash string 'android-34'` | `sdkmanager "platforms;android-34"` |
| `Unsupported class file major version` | You are on JDK 8/11; use JDK 17 |
| `Could not find method storePassword()` | You edited the signing block; see §5 for the property names |
| `java: not found` from `apksigner` | Add `$JAVA_HOME/bin` to `PATH` (the scripts do this automatically) |
| Gradle daemon OOM on a small VM | Keep `org.gradle.jvmargs=-Xmx1536m`; close other builds |
