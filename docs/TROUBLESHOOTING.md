# Troubleshooting

Start with **Diagnostics** (press `INFO` on any GameDay screen, or Home → Diagnostics) and
**Compatibility** (Home → Compatibility report). Both are generated from live probes and can be
exported.

## 1. GameDay will not start the NFL app

| Code / symptom | Meaning | Action |
| --- | --- | --- |
| `HOST_NOT_INSTALLED` | The NFL app is not installed for this user | Home → "Get the NFL app", or install it from the Fire TV search |
| `HOST_NOT_LAUNCHABLE` | Installed, but exposes no launch intent for a television | The installed build is not the Fire TV build; reinstall from the Amazon Appstore |
| `LAUNCH_FAILED` | The platform refused to start the activity | Check the message text; usually the package was disabled or being updated. Retry after the store finishes |
| `RUNTIME_UNAVAILABLE` | The device lacks a required platform feature | Check Compatibility; this should not occur on Fire OS 6 |
| `BLOCKED_BY_RECOVERY` | Three consecutive launch failures | Recovery → "Restart runtime", then retry |
| Signer mismatch message | The installed NFL app is signed by a different certificate than the one GameDay recorded | Reinstall the app from the Amazon Appstore. GameDay will not launch a package whose identity changed |

## 2. Safe mode

GameDay enters safe mode when three process starts happen within five minutes without a healthy
exit. Safe mode does **not** launch the guest automatically and does **not** install updates.

Order of actions:

1. **Restart runtime** — clears the failure counter and re-probes.
2. **Clear temporary files** — removes cache and any partial update download.
3. **Restore default configuration** — resets GameDay's preferences.
4. **Reset GameDay data** — clears settings, crash log and exported reports. It **never** touches the
   NFL app, its sign-in or its downloads, and the dialog says so.
5. If the app still crashes at startup, collect the report and file an issue with the device model,
   Fire OS version and the last crash entry.

## 3. Update problems

| Symptom | Cause | Action |
| --- | --- | --- |
| "Checks paused after N failures" | Circuit opened after 5 consecutive failures | Fix connectivity, then Settings → Check for GameDay updates (manual checks still work) |
| "Allow GameDay to install updates" | `REQUEST_INSTALL_PACKAGES` not granted (API 26+) | Settings → Applications → GameDay → Install unknown apps, or the button on the Updates screen |
| "Checksum mismatch" | Download corrupted or replaced | Retry; if it persists the published manifest and APK disagree — report it |
| "Update rejected: signing certificate does not match" | The APK was not signed with the project key | Do not install it. Report the release |
| "Install failed: … CONFLICT" | Different signer or lower version code than installed | Uninstall the old build only if you accept losing GameDay settings; never do this to the NFL app |
| No NFL update appears | The Appstore owns NFL updates | Use "Open NFL app in Appstore"; the store decides what is available in your region |

## 4. Playback problems

GameDay does not decode video; the NFL app does. If playback fails:

1. Confirm the NFL app plays the same content when launched directly from the Fire TV home screen.
   If it fails there too, the issue is the NFL app, the subscription or the network — not GameDay.
2. Confirm the device reports hardware decoders and Widevine: Home → Compatibility report →
   *Playback* section. Expected on `AFTMM`: hardware AVC and HEVC decoders, Widevine present, secure
   decoder reported.
3. Check HDMI/HDCP: protected content needs an HDCP 2.2 path. A non-compliant splitter or old
   receiver will cause a black screen or an error from the NFL app.
4. Check the network: `./scripts/diagnose.sh` records connectivity and DNS information.

## 5. Remote control

- Focus is shown by the accent bar and a green outline. If nothing has focus, press D-pad Down once.
- `MENU` opens Settings, `INFO` opens Diagnostics, long-press `BACK` exits GameDay.
- While the NFL app is in the foreground, GameDay does not intercept keys at all; the NFL app handles
  them. Press `Home` to come back.
- GameDay deliberately does not provide a pointer mode: injecting touch into another app requires an
  accessibility service or an overlay the guest accepts, and that is out of scope.

## 6. Collecting a report

```bash
./scripts/diagnose.sh                 # device properties, packages, codecs, DRM, logcat
# or, in the app: Home → Diagnostics → Export, then Share
```

Reports are redacted: tokens, passwords, e-mail addresses, account identifiers and long opaque blobs
are replaced with `[redacted]`. Certificate and APK digests are preserved because they are the point
of the report.

## 7. Logs

```bash
./scripts/deploy.sh --logs            # GameDay-tagged logs and fatal exceptions
adb logcat -d -t 500 > logcat.txt     # everything recent
adb shell dumpsys meminfo app.gameday.host
```

## 8. Uninstalling cleanly

Fire TV → Settings → Applications → Manage Installed Applications → GameDay → Uninstall. The NFL app
and its data are unaffected; GameDay never wrote into the NFL app's storage.
