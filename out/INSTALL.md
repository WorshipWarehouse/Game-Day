# GameDay 1.0.1 — sideload build

`gameday-1.0.1.apk` is built from the current `main` (`e16cefc`), not a stored artifact.

| | |
|---|---|
| Package | `app.gameday.host` |
| Version | 1.0.1 (versionCode 2) |
| Min / target API | 25 / 34 |
| Launcher activity | `app.gameday.host.ui.StartupActivity` |
| Size | 112,132 bytes |
| Signer | `CN=GameDay Open Source` — the same release key as `dist/` |

Built with `./scripts/package-release.sh` from `main` at `e16cefc`. Rebuild it
yourself at any time with the same command.

## Install on a Fire TV Stick

1. On the Stick: **Settings → My Fire TV → About → Network**, note the IP.
2. **Settings → My Fire TV → Developer Options** → turn on **ADB Debugging**
   (and *Install unknown apps* if prompted).
3. From a computer with `adb` (platform-tools) on the same network:

```bash
adb connect 192.168.1.50:5555     # your Stick's IP
adb devices                       # must show "device", not "unauthorized"
adb install -r gameday-1.0.1.apk
```

Because it carries the same release key as every earlier GameDay build, `-r`
upgrades in place. Only if you previously installed a *debug-signed* build will
you hit `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — remove it first:

```bash
adb uninstall app.gameday.host
adb install gameday-1.0.1.apk
```

GameDay appears in the Fire TV app row as **GameDay**. It does not bundle the NFL
app: on first run it reports whether `com.gotv.nflgamecenter.us.lite` is present
and hands you to the Amazon Appstore to install or update it.

## Verify the download

```bash
sha256sum -c SHA256SUMS.txt
```
