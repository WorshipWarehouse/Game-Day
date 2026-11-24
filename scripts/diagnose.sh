#!/usr/bin/env bash
# Collects device capability data from a connected Fire TV.
#
#   ./scripts/diagnose.sh                 # collect into artifacts/device-<timestamp>/
#   ./scripts/diagnose.sh --connect <ip>
#
# Everything is read-only: getprop, package manager queries, dumpsys and logcat.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] && [ -x "$ADB" ] || { echo "error: adb not found" >&2; exit 1; }

CONNECT=""
if [ "${1:-}" = "--connect" ]; then
  CONNECT="${2:-}"
  [ -n "$CONNECT" ] && "$ADB" connect "$CONNECT:5555"
fi

DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
[ -n "$DEVICE" ] || { echo "error: no device connected" >&2; exit 1; }

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="artifacts/device-$STAMP"
mkdir -p "$OUT"
echo "Collecting into $OUT"

sh() { "$ADB" -s "$DEVICE" shell "$@" 2>&1 | tr -d '\r'; }

sh getprop > "$OUT/getprop.txt"
{
  echo "model=$(sh getprop ro.product.model)"
  echo "device=$(sh getprop ro.product.device)"
  echo "fire_os=$(sh getprop ro.build.version.name)"
  echo "android_release=$(sh getprop ro.build.version.release)"
  echo "sdk=$(sh getprop ro.build.version.sdk)"
  echo "abi=$(sh getprop ro.product.cpu.abi)"
  echo "abi2=$(sh getprop ro.product.cpu.abi2)"
  echo "supported_abis=$(sh getprop ro.product.cpu.abilist)"
  echo "fingerprint=$(sh getprop ro.build.fingerprint)"
  echo "hardware_egl=$(sh getprop ro.hardware.egl)"
  echo "low_ram=$(sh getprop ro.config.low_ram)"
} > "$OUT/device.txt"

sh pm list packages > "$OUT/packages.txt"
for pkg in com.gotv.nflgamecenter.us.lite app.gameday.host app.gameday.compat.test com.amazon.venezia; do
  sh dumpsys package "$pkg" > "$OUT/dumpsys-$pkg.txt" || true
done

sh cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER \
  com.gotv.nflgamecenter.us.lite > "$OUT/nfl-leanback-activity.txt" 2>&1 || true
sh dumpsys media.codec > "$OUT/media-codec.txt" 2>&1 || true
sh dumpsys meminfo > "$OUT/meminfo.txt" 2>&1 || true
sh dumpsys display > "$OUT/display.txt" 2>&1 || true
sh dumpsys media.drm > "$OUT/drm.txt" 2>&1 || true
"$ADB" -s "$DEVICE" logcat -d -t 600 > "$OUT/logcat.txt" 2>&1 || true

# If GameDay has exported its own report, pull it as well.
"$ADB" -s "$DEVICE" shell run-as app.gameday.host ls >/dev/null 2>&1 || true
"$ADB" -s "$DEVICE" pull /sdcard/Android/data/app.gameday.host/files/documents/diagnostics \
  "$OUT/gameday-report" >/dev/null 2>&1 || true

{
  echo "# Device snapshot $STAMP"
  cat "$OUT/device.txt"
  echo
  echo "NFL app installed: $(grep -c 'com.gotv.nflgamecenter.us.lite' "$OUT/packages.txt" || true)"
  echo "Leanback activity: $(cat "$OUT/nfl-leanback-activity.txt" 2>/dev/null || echo unknown)"
  echo
  echo "Video decoders:"
  grep -iE 'OMX\.|c2\.' "$OUT/media-codec.txt" 2>/dev/null | head -40 || echo "  unavailable"
} > "$OUT/SUMMARY.txt"

cat "$OUT/SUMMARY.txt"
