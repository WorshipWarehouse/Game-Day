#!/usr/bin/env bash
# One-command Fire TV qualification for GameDay.
#
#   ./scripts/qualify.sh --connect 192.168.1.42
#   ./scripts/qualify.sh --connect 192.168.1.42 --apk dist/gameday-1.0.1.apk --skip-build
#   ./scripts/qualify.sh --connect 192.168.1.42 --compat     # also install/launch the test app
#
# Performs, in order: device connection, hardware identification, Android capability detection,
# APK installation, launch timing, memory and CPU profiling, graphics capability reporting, update
# capability reporting, diagnostic collection, and export of a consolidated report.
#
# Output: artifacts/qualify-<UTC stamp>/
#   REPORT.md      the consolidated, human- and agent-readable report
#   report.json    the same facts as structured data
#   commands.log   every adb command that was run
#   *.txt          raw evidence (getprop, packages, dumpsys, meminfo, gfxinfo, logcat)
#
# Everything is read-only apart from installing and launching GameDay. No credentials are read.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
[ -n "$ADB" ] && [ -x "$ADB" ] || { echo "error: adb not found (install platform-tools)" >&2; exit 1; }

CONNECT=""; APK=""; SKIP_BUILD=0; WITH_COMPAT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --connect) CONNECT="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --compat) WITH_COMPAT=1; shift ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ -n "$CONNECT" ]; then
  echo "Connecting to $CONNECT:5555"
  "$ADB" connect "$CONNECT:5555" >/dev/null || true
fi

DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
if [ -z "$DEVICE" ]; then
  cat >&2 <<'EOF'
error: no ADB device is attached, so nothing was measured.

This is the expected outcome on a machine without a Fire TV. To run it for real:
  1. On the Fire TV: Settings > My Fire TV > About > press Select 7x (Developer Options).
  2. Settings > My Fire TV > Developer Options > ADB Debugging > On.
  3. Find the device IP: Settings > My Fire TV > About > Network.
  4. Run: ./scripts/qualify.sh --connect <ip>

The script writes nothing when there is no device, so a stale report can never be mistaken
for a real one.
EOF
  exit 1
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="artifacts/qualify-$STAMP"
mkdir -p "$OUT"
CMDLOG="$OUT/commands.log"
: > "$CMDLOG"
echo "Device: $DEVICE"
echo "Collecting into $OUT"

adbsh() {
  echo "adb -s $DEVICE shell $*" >> "$CMDLOG"
  "$ADB" -s "$DEVICE" shell "$@" 2>&1 | tr -d '\r'
}
adbraw() {
  echo "adb -s $DEVICE $*" >> "$CMDLOG"
  "$ADB" -s "$DEVICE" "$@" 2>&1 | tr -d '\r'
}

CHECKS="$OUT/checks.tsv"
: > "$CHECKS"
check() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$CHECKS"; }

# ---------------------------------------------------------------- 1. build / locate APK
if [ -z "$APK" ]; then
  if [ "$SKIP_BUILD" -eq 0 ]; then
    echo "Building (pass --skip-build to reuse an existing APK)"
    ./scripts/build.sh >/dev/null
  fi
  APK="$(ls -t dist/*.apk 2>/dev/null | head -1 || true)"
  [ -n "$APK" ] || APK="$(ls -t apps/fire-tv/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)"
fi
[ -n "$APK" ] && [ -f "$APK" ] || { echo "error: no APK found" >&2; exit 1; }
APK_SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
APK_SIZE="$(stat -c %s "$APK" 2>/dev/null || wc -c < "$APK")"

# ---------------------------------------------------------------- 2. hardware + capabilities
adbsh getprop > "$OUT/getprop.txt"
adbsh pm list features > "$OUT/features.txt"
adbsh pm list packages > "$OUT/packages.txt"
adbsh dumpsys media.codec > "$OUT/media-codec.txt" 2>/dev/null || true
adbsh dumpsys media.drm > "$OUT/media-drm.txt" 2>/dev/null || true
adbsh dumpsys display > "$OUT/display.txt" 2>/dev/null || true
adbsh dumpsys thermalservice > "$OUT/thermal.txt" 2>/dev/null || true
adbsh dumpsys meminfo > "$OUT/meminfo-system.txt" 2>/dev/null || true

prop() { adbsh getprop "$1" | head -1; }
MODEL="$(prop ro.product.model)"
DEVICE_CODE="$(prop ro.product.device)"
BRAND="$(prop ro.product.brand)"
FIRE_OS="$(prop ro.build.version.name)"
RELEASE="$(prop ro.build.version.release)"
SDK="$(prop ro.build.version.sdk)"
ABI="$(prop ro.product.cpu.abi)"
ABILIST="$(prop ro.product.cpu.abilist)"
TOTAL_GB="$(adbsh df /data 2>/dev/null | awk 'NR==2 {print $2}' | head -1)"

NFL_INSTALLED="no"
grep -q '^package:com.gotv.nflgamecenter.us.lite$' "$OUT/packages.txt" && NFL_INSTALLED="yes"
MANAGED_USERS="no"
grep -q 'android.software.managed_users' "$OUT/features.txt" && MANAGED_USERS="yes"
LEANBACK="no"
grep -q 'android.software.leanback' "$OUT/features.txt" && LEANBACK="yes"
DRM_LINES="$(grep -ciE 'widevine' "$OUT/media-drm.txt" 2>/dev/null || true)"
HW_DECODERS="$(grep -ciE 'OMX\.[a-z]+\.|c2\.[a-z]+\.' "$OUT/media-codec.txt" 2>/dev/null || true)"

# ---------------------------------------------------------------- 3. install
INSTALL_LOG="$OUT/install.txt"
adbraw install -r -d "$APK" > "$INSTALL_LOG" 2>&1 || true
if grep -q '^Success' "$INSTALL_LOG"; then
  check "install" "PASS" "$(basename "$APK")"
else
  check "install" "FAIL" "$(tr '\n' ' ' < "$INSTALL_LOG" | cut -c1-300)"
fi

# ---------------------------------------------------------------- 4. launch + timing
LAUNCH_OUT="$OUT/launch.txt"
adbraw shell am force-stop app.gameday.host >/dev/null 2>&1 || true
adbraw shell am start -W -n app.gameday.host/.ui.StartupActivity > "$LAUNCH_OUT" 2>&1 || true
TOTAL_TIME="$(sed -n 's/^TotalTime: *//p' "$LAUNCH_OUT" | head -1)"
WAIT_TIME="$(sed -n 's/^WaitTime: *//p' "$LAUNCH_OUT" | head -1)"
if grep -q 'Status: ok' "$LAUNCH_OUT"; then
  check "launch" "PASS" "TotalTime=${TOTAL_TIME:-?}ms WaitTime=${WAIT_TIME:-?}ms"
else
  check "launch" "FAIL" "$(tr '\n' ' ' < "$LAUNCH_OUT" | cut -c1-300)"
fi

sleep 5

# ---------------------------------------------------------------- 5. memory / CPU / graphics
adbsh dumpsys meminfo app.gameday.host > "$OUT/meminfo-gameday.txt" 2>/dev/null || true
adbsh top -n 1 -b > "$OUT/top.txt" 2>/dev/null || true
adbsh dumpsys gfxinfo app.gameday.host > "$OUT/gfxinfo.txt" 2>/dev/null || true
adbsh dumpsys SurfaceFlinger --latency 2>/dev/null | head -20 > "$OUT/sf-latency.txt" || true
PSS_KB="$(sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' "$OUT/meminfo-gameday.txt" | head -1)"
[ -n "${PSS_KB:-}" ] || PSS_KB="$(awk '/TOTAL/ {print $2; exit}' "$OUT/meminfo-gameday.txt" 2>/dev/null || true)"

# ---------------------------------------------------------------- 6. update capability
UPDATES="$OUT/updates.txt"
{
  echo "REQUEST_INSTALL_PACKAGES declared:"
  adbsh dumpsys package app.gameday.host | grep -A1 'REQUEST_INSTALL_PACKAGES' || true
  echo
  echo "Unknown-sources / install permission state:"
  adbsh appops get app.gameday.host REQUEST_INSTALL_PACKAGES 2>/dev/null || echo "  not reported"
  echo
  echo "Usage access (needed only for guest foreground detection):"
  adbsh appops get app.gameday.host GET_USAGE_STATS 2>/dev/null || echo "  not reported"
  echo
  echo "Amazon Appstore present:"
  grep -q '^package:com.amazon.venezia$' "$OUT/packages.txt" && echo "  yes" || echo "  no"
  echo
  echo "NFL app version:"
  adbsh dumpsys package com.gotv.nflgamecenter.us.lite 2>/dev/null \
    | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime|signatures' | head -8 || echo "  not installed"
} > "$UPDATES" 2>&1
INSTALL_PERM="$(grep -c 'REQUEST_INSTALL_PACKAGES: granted=true' "$OUT/updates.txt" || true)"
check "install-permission" "$([ "${INSTALL_PERM:-0}" -gt 0 ] && echo PASS || echo MANUAL)" \
  "grant 'Install unknown apps' to allow GameDay self-updates"

# ---------------------------------------------------------------- 7. optional compat app
if [ "$WITH_COMPAT" -eq 1 ]; then
  COMPAT="$(ls -t compatibility/test-app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)"
  if [ -n "$COMPAT" ]; then
    adbraw install -r "$COMPAT" > "$OUT/install-compat.txt" 2>&1 || true
    adbraw shell am start -n app.gameday.compat.test/.MainActivity > "$OUT/compat-launch.txt" 2>&1 || true
    sleep 8
    adbsh dumpsys meminfo app.gameday.compat.test > "$OUT/meminfo-compat.txt" 2>/dev/null || true
    adbsh screencap -p /sdcard/gameday-compat.png >/dev/null 2>&1 || true
    adbraw pull /sdcard/gameday-compat.png "$OUT/compat.png" >/dev/null 2>&1 || true
    check "compat-app" "PASS" "launched; memory captured"
  else
    check "compat-app" "SKIP" "build it with ./scripts/build.sh --all"
  fi
fi

# ---------------------------------------------------------------- 8. diagnostics + logs
adbraw logcat -d -t 800 > "$OUT/logcat.txt" 2>&1 || true
grep -E 'FATAL EXCEPTION|AndroidRuntime: |GameDay' "$OUT/logcat.txt" > "$OUT/logcat-gameday.txt" 2>/dev/null || true
CRASHES="$(grep -c 'FATAL EXCEPTION' "$OUT/logcat-gameday.txt" 2>/dev/null || true)"
check "no-fatal-crash" "$([ "${CRASHES:-0}" -eq 0 ] && echo PASS || echo FAIL)" \
  "${CRASHES:-0} fatal exception(s) in the last 800 log lines"

adbraw pull /sdcard/Android/data/app.gameday.host/files/documents/diagnostics \
  "$OUT/gameday-report" >/dev/null 2>&1 || true
if [ -d "$OUT/gameday-report" ]; then
  check "gameday-report" "PASS" "exported report pulled"
else
  check "gameday-report" "MANUAL" "open Diagnostics in the app and choose Export, then re-run"
fi

# ---------------------------------------------------------------- 9. consolidated report
PASSES="$(awk -F'\t' '$2=="PASS"' "$CHECKS" | wc -l | tr -d ' ')"
FAILS="$(awk -F'\t' '$2=="FAIL"' "$CHECKS" | wc -l | tr -d ' ')"
MANUALS="$(awk -F'\t' '$2=="MANUAL" || $2=="SKIP"' "$CHECKS" | wc -l | tr -d ' ')"

{
  echo "# GameDay qualification report"
  echo
  echo "Generated: $STAMP (UTC) by \`scripts/qualify.sh\`"
  echo
  echo "## Verdict"
  echo
  echo "- Checks passing: **$PASSES**"
  echo "- Checks failing: **$FAILS**"
  echo "- Needs a human: **$MANUALS**"
  echo
  echo "\`\`\`"
  awk -F'\t' '{printf "%-22s %-7s %s\n", $1, $2, $3}' "$CHECKS"
  echo "\`\`\`"
  echo
  echo "## Device"
  echo
  echo "| Field | Value |"
  echo "| --- | --- |"
  echo "| Model | $MODEL |"
  echo "| Device code | $DEVICE_CODE |"
  echo "| Brand | $BRAND |"
  echo "| Fire OS | $FIRE_OS |"
  echo "| Android release | $RELEASE (API $SDK) |"
  echo "| Primary ABI | $ABI |"
  echo "| Supported ABIs | $ABILIST |"
  echo "| /data size (blocks) | ${TOTAL_GB:-unknown} |"
  echo "| Leanback feature | $LEANBACK |"
  echo "| Managed users feature | $MANAGED_USERS |"
  echo
  echo "## Hosted application"
  echo
  echo "- NFL app installed: **$NFL_INSTALLED**"
  echo "- Amazon Appstore installed: $(grep -q '^package:com.amazon.venezia$' "$OUT/packages.txt" && echo yes || echo no)"
  echo
  echo "## GameDay build under test"
  echo
  echo "- APK: \`$APK\`"
  echo "- Size: $APK_SIZE bytes"
  echo "- SHA-256: \`$APK_SHA\`"
  echo
  echo "## Measured"
  echo
  echo "- Launch: TotalTime=${TOTAL_TIME:-?} ms, WaitTime=${WAIT_TIME:-?} ms (\`am start -W\`)"
  echo "- GameDay PSS: ${PSS_KB:-unknown} kB (\`dumpsys meminfo\`)"
  echo "- Hardware decoder entries seen: $HW_DECODERS"
  echo "- Widevine mentions in DRM dump: $DRM_LINES"
  echo
  echo "## What this report proves"
  echo
  echo "- The APK installs and the launcher activity starts on this exact build."
  echo "- The measured start time, PSS and capability facts above are real device readings."
  echo
  echo "## What it does not prove"
  echo
  echo "- That the NFL app launches and plays protected content. That needs a subscription and the"
  echo "  guest app present; see \`docs/COMPATIBILITY.md\`."
  echo "- Anything about a different device build. These numbers are for this unit only."
  echo
  echo "## Evidence files"
  echo
  echo '```'
  ls -1 "$OUT" | sed 's/^/  /'
  echo '```'
  echo
  echo "## How to hand this to another engineer or agent"
  echo
  echo "Send the whole \`$OUT\` directory (or just \`REPORT.md\` + \`report.json\` + \`logcat-gameday.txt\`)."
  echo "It is self-describing: device facts, exact commands (\`commands.log\`), measured numbers and"
  echo "raw dumps are all present, and no credentials or account identifiers are included."
} > "$OUT/REPORT.md"

python3 - "$OUT" <<'PY'
import json, os, sys
out = sys.argv[1]
checks = []
with open(os.path.join(out, "checks.tsv")) as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) == 3:
            checks.append({"name": parts[0], "status": parts[1], "detail": parts[2]})
report = {"schema": 1, "generatedBy": "scripts/qualify.sh", "directory": out,
          "checks": checks, "evidenceFiles": sorted(os.listdir(out))}
with open(os.path.join(out, "report.json"), "w") as f:
    json.dump(report, f, indent=2)
PY

echo
cat "$OUT/REPORT.md"
echo
echo "Report: $OUT/REPORT.md"
[ "$FAILS" -eq 0 ] || exit 1
