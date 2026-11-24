#!/usr/bin/env bash
# Installs and launches GameDay on a Fire TV over ADB.
#
#   ./scripts/deploy.sh                          # install the newest debug APK on the first device
#   ./scripts/deploy.sh --connect 192.168.1.42   # enable ADB over Wi-Fi, then install
#   ./scripts/deploy.sh --apk dist/gameday.apk   # install a specific (e.g. release) APK
#   ./scripts/deploy.sh --compat-only            # install only the compatibility test app
#   ./scripts/deploy.sh --logs                   # tail GameDay logs
#
# Fire TV prerequisites: Settings > My Fire TV > About > press Select 7x to unlock Developer
# Options, then enable ADB Debugging. See docs/FIRE-TV-SETUP.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] && [ -x "$ADB" ] || { echo "error: adb not found (install platform-tools)" >&2; exit 1; }

APK=""
CONNECT=""
COMPAT_ONLY=0
LOGS=0
while [ $# -gt 0 ]; do
  case "$1" in
    --connect) CONNECT="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --compat-only) COMPAT_ONLY=1; shift ;;
    --logs) LOGS=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ "$LOGS" -eq 1 ]; then
  exec "$ADB" logcat -v time -s GameDay:V AndroidRuntime:E ActivityManager:I
fi

if [ -n "$CONNECT" ]; then
  echo "Connecting to $CONNECT:5555"
  "$ADB" connect "$CONNECT:5555"
fi

DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$DEVICES" ] || {
  echo "error: no device. Enable ADB debugging on the Fire TV and run with --connect <ip>." >&2
  exit 1
}
DEVICE="$(echo "$DEVICES" | head -1)"
echo "Device: $DEVICE"

install_apk() {
  local file="$1"
  [ -f "$file" ] || { echo "error: APK not found: $file" >&2; exit 1; }
  echo "Installing $file ($(du -h "$file" | cut -f1))"
  "$ADB" -s "$DEVICE" install -r -d "$file"
}

LAUNCHER_APK="$APK"
COMPAT_APK="$(ls -t compatibility/test-app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)"

if [ "$COMPAT_ONLY" -eq 0 ]; then
  if [ -z "$LAUNCHER_APK" ]; then
    LAUNCHER_APK="$(ls -t dist/*.apk 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$LAUNCHER_APK" ]; then
    LAUNCHER_APK="$(ls -t apps/fire-tv/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)"
  fi
  [ -n "$LAUNCHER_APK" ] || { echo "error: no GameDay APK found; run ./scripts/build.sh" >&2; exit 1; }
  install_apk "$LAUNCHER_APK"
fi

if [ -n "$COMPAT_APK" ]; then
  install_apk "$COMPAT_APK"
fi

if [ "$COMPAT_ONLY" -eq 0 ]; then
  echo "Launching GameDay"
  "$ADB" -s "$DEVICE" shell am start -n app.gameday.host/.ui.StartupActivity
  echo
  echo "Watching for startup errors for 8 seconds"
  timeout 8 "$ADB" -s "$DEVICE" logcat -v brief -s AndroidRuntime:E ActivityManager:I GameDay:V \
    || true
  echo
  echo "Useful follow-ups:"
  echo "  ./scripts/diagnose.sh --connect $CONNECT"
  echo "  ./scripts/benchmark.sh"
  echo "  ./scripts/deploy.sh --logs"
else
  echo "Launching the compatibility test app"
  "$ADB" -s "$DEVICE" shell am start -n app.gameday.compat.test/.MainActivity
fi
