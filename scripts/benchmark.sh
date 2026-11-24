#!/usr/bin/env bash
# Measures cold start, warm start, memory and rendering on a connected Fire TV.
#
#   ./scripts/benchmark.sh                  # one run
#   ./scripts/benchmark.sh --runs 5         # average several runs
#   ./scripts/benchmark.sh --soak 240       # additionally watch a 240 minute playback session
#
# Results are written to artifacts/benchmark-<timestamp>.txt and printed. Without a device the
# script exits non-zero rather than inventing numbers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] && [ -x "$ADB" ] || { echo "error: adb not found" >&2; exit 1; }

RUNS=3
SOAK_MIN=0
while [ $# -gt 0 ]; do
  case "$1" in
    --runs) RUNS="${2:-3}"; shift 2 ;;
    --soak) SOAK_MIN="${2:-0}"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
[ -n "$DEVICE" ] || {
  echo "error: no device connected. Connect the Fire TV and re-run (see docs/FIRE-TV-SETUP.md)." >&2
  exit 1
}
echo "Device: $DEVICE"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p artifacts
OUT="artifacts/benchmark-$STAMP.txt"
{
  echo "# GameDay benchmark $STAMP"
  "$ADB" -s "$DEVICE" shell getprop ro.product.model | tr -d '\r'
  "$ADB" -s "$DEVICE" shell getprop ro.build.version.name | tr -d '\r'
  "$ADB" -s "$DEVICE" shell getprop ro.build.version.sdk | tr -d '\r'
  echo
} > "$OUT"

for i in $(seq 1 "$RUNS"); do
  "$ADB" -s "$DEVICE" shell am force-stop app.gameday.host
  sleep 2
  echo "== cold start run $i ==" | tee -a "$OUT"
  "$ADB" -s "$DEVICE" shell am start -W -n app.gameday.host/.ui.StartupActivity \
    | tr -d '\r' | tee -a "$OUT"
  sleep 1
  "$ADB" -s "$DEVICE" shell am force-stop app.gameday.host
  sleep 1
  "$ADB" -s "$DEVICE" shell am start -W -n app.gameday.host/.ui.StartupActivity \
    | tr -d '\r' | tee -a "$OUT"
  sleep 4
  echo "== warm start run $i ==" | tee -a "$OUT"
  "$ADB" -s "$DEVICE" shell am start -W -n app.gameday.host/.ui.StartupActivity \
    | tr -d '\r' | tee -a "$OUT"
done

{
  echo
  echo "== meminfo (GameDay) =="
  "$ADB" -s "$DEVICE" shell dumpsys meminfo app.gameday.host | tr -d '\r' | head -30
  echo
  echo "== gfxinfo =="
  "$ADB" -s "$DEVICE" shell dumpsys gfxinfo app.gameday.host | tr -d '\r' | head -25
  echo
  echo "== thermal =="
  "$ADB" -s "$DEVICE" shell dumpsys thermalservice | tr -d '\r' | head -20 || true
} >> "$OUT" 2>&1

if [ "$SOAK_MIN" -gt 0 ]; then
  echo "Soak: watching GameDay for $SOAK_MIN minutes" | tee -a "$OUT"
  END=$(( $(date +%s) + SOAK_MIN * 60 ))
  while [ "$(date +%s)" -lt "$END" ]; do
    sleep 60
    printf '%s pss=%s\n' "$(date -u +%H:%M:%S)" \
      "$("$ADB" -s "$DEVICE" shell dumpsys meminfo app.gameday.host | tr -d '\r' \
        | awk '/TOTAL PSS/ {print $3; exit}')" | tee -a "$OUT"
    if ! "$ADB" -s "$DEVICE" shell pidof app.gameday.host >/dev/null 2>&1; then
      echo "GameDay process is gone; soak aborted" | tee -a "$OUT"
      break
    fi
  done
fi

echo
echo "Wrote $OUT"
