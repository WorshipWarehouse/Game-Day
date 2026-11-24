# Fire TV setup and installation

Target: Fire TV Stick 4K (1st gen, `AFTMM`, Fire OS 6). The same steps work on other Fire OS 6+
televisions; nothing is specific to one serial number or account.

## 1. Install the NFL app first (normal, official route)

GameDay hosts the official NFL application; it does not ship it.

1. From the Fire TV home screen, choose **Find** (or press the Alexa button) and search for **NFL**.
2. Select the official **NFL** app by NFL Enterprises and choose **Get**.
   Alternatively, open the Amazon Appstore page for the NFL app on your computer and choose
   **Deliver to device**.
3. Open the NFL app once, and sign in or subscribe there if you want NFL+ content.

NFL+ is available in the United States only; outside the US the NFL directs viewers to DAZN. Sign-in,
subscription and playback authorization belong entirely to the NFL app.

## 2. Enable ADB on the Fire TV

1. **Settings → My Fire TV → About**.
2. Highlight **Fire TV Stick** and press **Select** seven times. A toast confirms Developer Options.
3. **Settings → My Fire TV → Developer Options → ADB Debugging → On**.
4. Note the IP address: **Settings → My Fire TV → About → Network**.

## 3. Install GameDay

From a computer on the same network:

```bash
./scripts/deploy.sh --connect 192.168.1.42
```

That script runs `adb connect`, installs `dist/gameday-1.0.0.apk` (or the newest debug build),
installs the compatibility test app if it is built, launches GameDay, and watches the log for eight
seconds.

Manual alternative:

```bash
adb connect 192.168.1.42:5555
adb install -r dist/gameday-1.0.0.apk
adb shell am start -n app.gameday.host/.ui.StartupActivity
```

If you prefer not to use ADB, sideload the APK with any Fire TV file-transfer utility and open it.
Fire OS will ask you to allow installation from that source.

## 4. First run

1. GameDay appears in the Fire TV app row (leanback banner, 320×180) and in **Your Apps & Channels**.
2. On first launch it probes the device, records the NFL app's signing certificate, and shows the
   home screen.
3. **Watch NFL** launches the NFL app. Your existing sign-in is preserved because the NFL app is
   never reinstalled or modified.
4. GameDay's own update check runs once at start and then at most every six hours while it is open.

## 5. Optional permissions

| Permission | Why | Where |
| --- | --- | --- |
| **Install unknown apps** (API 26+) | Only needed if you choose to install a GameDay update from its release feed | Settings → Applications → GameDay → Install unknown apps |
| **Usage access** | Lets GameDay report whether the NFL app is in the foreground | Android settings → Special access → Usage access. Fire OS may not expose this screen; GameDay reports the capability as unavailable if so. |

GameDay requests nothing else. It has no location, microphone, camera, contacts, storage or
accessibility permissions.

## 6. Remote control

| Key | Action |
| --- | --- |
| D-pad | Move focus |
| Select | Activate |
| Back | Leave the current screen |
| Long-press Back (GameDay home) | Exit GameDay |
| Menu | Open Settings from any GameDay screen |
| Info | Open Diagnostics from any GameDay screen |
| Play/Pause, FF, Rewind | Pass through to the guest while it is in the foreground |

Inside the NFL app, the remote behaves exactly as the NFL app defines it, because the NFL app owns
the foreground. To return to GameDay, press **Home** and choose GameDay again.

## 7. Uninstalling

- GameDay: Fire TV **Settings → Applications → Manage Installed Applications → GameDay → Uninstall**.
- GameDay never uninstalls, clears or backs up the NFL app. Removing GameDay leaves the NFL app, its
  sign-in and its downloads untouched.
- "Reset GameDay data" in Settings/Recovery clears GameDay's settings, crash log and reports only,
  and says so in the confirmation dialog.

## 8. Verifying the install

```bash
adb shell dumpsys package app.gameday.host | head -20
adb shell cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER app.gameday.host
./scripts/diagnose.sh
```

Expected: package `app.gameday.host`, `versionName=1.0.0`, `minSdk=25`, and a leanback-launchable
activity named `app.gameday.host.ui.StartupActivity`.
