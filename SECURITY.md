# Security

## Supported versions

| Version | Supported |
| --- | --- |
| 1.0.x | Yes |
| Unreleased builds from `main` | Best effort |

## Reporting a vulnerability

Open a private security advisory on the GitHub repository (Security → Advisories) rather than a
public issue. Include the GameDay version, device model, Fire OS version, reproduction steps, and the
exported diagnostic report if it is relevant. Please do not attach credentials, tokens, or captured
video.

## What GameDay protects

### Assets

1. The user's NFL account, sign-in and playback authorization — **inside the NFL app, never touched**.
2. The integrity of what gets installed: no substituted or re-signed APK may reach the platform
   installer.
3. GameDay's own settings and diagnostics, which must not leak credentials.

### Threat model

| Threat | Mitigation |
| --- | --- |
| Hostile update feed (DNS/transport tampering, compromised manifest) | HTTPS only; allowlisted hosts; manual redirect validation; strict manifest schema; SHA-256 over the streamed bytes; size caps; package/signer/version checks before staging |
| Substituted APK served from the right URL | SHA-256 from the manifest **and** signer equality with the installed package; a mismatch deletes the download |
| Downgrade attack (roll back to a vulnerable version) | `versionCode` may not decrease; the platform enforces the same rule |
| Malicious APK in a split set | `InstallPlan` requires one base, identical package/version/signers and unique split names |
| Another app silently updating GameDay | The platform's `PackageInstaller` confirmation; GameDay's own signer is checked before staging |
| Credentials leaking into logs or reports | Central `Redactor` masks JWTs, bearer tokens, `key=value` secrets (including camelCase and JSON-quoted forms), e-mail addresses, authorization/cookie header lines and long opaque blobs; certificate and APK digests are deliberately preserved |
| Path traversal through the report provider | Non-exported `ContentProvider`, read-only, canonical-path containment inside the diagnostics directory only |
| Cleartext downgrade | `usesCleartextTraffic="false"` plus a network security config that trusts only system CAs |
| Excessive privileges | Four permissions total, all justified in the manifest; no storage, location, contacts, accessibility or overlay permissions |
| Background abuse | No services, no alarms, no jobs; work happens only while an Activity is in the foreground |

### Explicit non-goals

GameDay does **not** and will **not**:

- bypass DRM, HDCP, attestation, integrity or geo-restriction checks;
- spoof device identity or falsify hardware attestation;
- capture, decrypt or re-route protected video;
- modify, re-sign or repackage the NFL application;
- disable or weaken Android package signature verification;
- collect telemetry or send anything to a server the user did not ask for.

If the NFL application refuses to run in a given configuration, GameDay reports it and stops. That is
a feature.

## Privacy

GameDay has no account system, no analytics, no crash upload, and no ad or tracking SDK. Its only
network peers are `api.github.com` and GitHub's release asset hosts, and only when checking for a
GameDay update.

Diagnostics contain device capabilities, installed versions and GameDay state. They are written to
the app-specific external directory, are not uploaded anywhere automatically, and are redacted at
render time.

## Dependencies

The runtime application has **no third-party dependencies**: no AndroidX, no Kotlin runtime, no JSON
library, no networking library, no logging library. The only build-time dependency is the Android
Gradle Plugin, and JUnit in tests. This is a security decision as much as a size one: there is no
transitive supply chain inside the shipped APK.

## Verification you can reproduce

```bash
./scripts/test.sh                       # 104 tests, including redaction and verification rules
./scripts/package-release.sh            # verifies the APK signature with apksigner
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging dist/gameday-1.0.0.apk   # permissions, minSdk
unzip -l dist/gameday-1.0.0.apk         # no native libraries, no unexpected files
```
