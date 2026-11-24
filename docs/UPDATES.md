# Updates

Two independent systems. Updating one never requires reinstalling the other.

- **NFL app** — owned by the Amazon Appstore; GameDay delegates and verifies.
- **GameDay** — owned by this project's GitHub releases; GameDay downloads, verifies and stages.

## 1. NFL app updates

### What is actually possible

There is no authorized public API that returns "the latest NFL app version". Scraping APK mirrors or
inventing an endpoint would be unreliable and outside this project's rules. Fire OS already has an
update mechanism: the Amazon Appstore.

### What GameDay does

| Step | Implementation |
| --- | --- |
| Read the installed version | `PackageManager` → exact `versionName`/`versionCode` |
| Read the installing package | `getInstallSourceInfo` (API 30+) / `getInstallerPackageName` |
| Record the signing identity | `trustedNflSigner` in GameDay's prefs, on first observation (trust-on-first-use) |
| Detect a change | Every start/resume compares the installed version and signer with the recorded ones |
| Offer an update | Opens `amzn://apps/android?p=com.gotv.nflgamecenter.us.lite&intent=app_update` (documented Amazon deep link), falling back to the Appstore web page |
| After the user returns | Re-reads the version; a change is recorded with a timestamp and shown in Settings |
| Last known working version | Written after a successful launch (`lastKnownWorkingCode/Name`) |
| Trust failure | A signer that differs from the recorded one blocks launching and is surfaced in Recovery |

### Compatibility management

GameDay keeps these distinct and shows them separately:

- **Installed** — measured.
- **Last known working** — measured after a successful launch.
- **Latest available** — the Appstore's responsibility; GameDay does not invent a number. If the
  store reports a newer version after an update, GameDay records it.

If the Appstore installs a version that no longer launches, the user sees the failure with the
version that last worked, and can restore a known-good state by reinstalling from the store.

### What GameDay refuses to do

- Download the NFL app from anywhere other than the user's own store interaction.
- Re-sign, patch or modify the NFL package.
- Install an NFL APK whose signing certificate does not match the recorded one.
- Clear NFL app data as a recovery step.

## 2. GameDay self-updates

### Release feed

Each GitHub release carries an asset named **`gameday-update.json`**:

```json
{
  "schemaVersion": 1,
  "appId": "app.gameday.host",
  "channel": "stable",
  "versionName": "1.1.0",
  "versionCode": 2,
  "minSdk": 25,
  "apkUrl": "https://github.com/WorshipWarehouse/Game-Day/releases/download/v1.1.0/gameday-1.1.0.apk",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "sizeBytes": 112640,
  "publishedAt": "2026-11-24T00:00:00Z",
  "releaseNotes": "Faster start; fixes remote focus on the settings screen."
}
```

`./scripts/package-release.sh` prints a ready-to-paste manifest for the APK it just built, including
the correct SHA-256 and size.

### Discovery

`https://api.github.com/repos/WorshipWarehouse/Game-Day/releases?per_page=10` is fetched over HTTPS,
releases are walked newest-first, drafts are skipped, and prereleases are only considered on the
`beta` channel. For each release the named asset must exist and be on an allowlisted host.

Walking newest-first is what implements *"retain the newest compatible version"*: if the newest
release requires a newer Android level than the device has, the loop continues to older releases
until it finds one that is compatible.

### Verification chain (in order)

1. Manifest is syntactically valid JSON and a JSON object.
2. `schemaVersion` = 1, `appId` = GameDay's own package, `channel` known.
3. `versionName` parses as a semantic version; `versionCode` ≥ 1; `minSdk` in range.
4. `apkUrl` is HTTPS, has a host, has no embedded credentials.
5. `sha256` is exactly 64 lowercase hex characters; `sizeBytes` within 200 MB.
6. `publishedAt` parses as ISO-8601 UTC.
7. Download is streamed with a byte cap and hashed while writing.
8. Size and SHA-256 must match exactly, or the file is deleted.
9. The APK is parsed: package name must be `app.gameday.host`; signer must equal the installed app's
   signer; `versionCode` may not go backwards; `minSdk` must be ≤ the device's API.
10. `PackageInstaller` is used to stage and commit; the platform shows its own confirmation.

### Scheduling and backoff

`UpdatePolicy` (unit tested):

- Interval: 6 hours between automatic checks.
- On failure: 60 s, 120 s, 240 s … capped at 6 hours.
- After 5 consecutive failures the circuit opens and automatic checks stop. Manual checks still work.
- Manual checks bypass the interval but still record failures.
- Check bookkeeping lives in GameDay's prefs; no background service is scheduled.

### Installation and recovery

- The download goes to `cache/updates/` and is deleted after the session commits (or on failure).
- A cancelled or blocked install is reported with the platform's status code
  (`InstallResultReceiver`), not silently swallowed.
- The currently installed GameDay keeps working until the new one is committed; Android's installer is
  atomic.
- Rollback is not offered for GameDay itself: Android does not allow a downgrade, and GameDay will not
  weaken that by shipping a "downgrade" flag. Recovering from a bad release means publishing a fixed
  release.

### Rate limiting

Unauthenticated GitHub API requests are limited to 60/hour per IP. A six-hour interval uses at most a
handful of requests per day. GameDay caches the last known manifest in prefs so the UI still shows
something useful when a check is skipped or fails.

## 3. Publishing a GameDay release

```bash
./scripts/package-release.sh
# → dist/gameday-1.0.0.apk plus a manifest template with SHA-256 and size

# Create a GitHub release tagged v1.0.0 and attach:
#   gameday-1.0.0.apk
#   gameday-update.json
```

Bump `versionCode` in `apps/fire-tv/build.gradle` for every published build: Android rejects
upgrades whose version code does not increase, and GameDay's own check treats a non-increase as
"up to date".

Use the `beta` channel (`"channel": "beta"` plus a prerelease tag) for experimental builds; stable
channels never see them.
