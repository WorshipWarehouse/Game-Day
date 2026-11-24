#!/usr/bin/env bash
# Builds the GameDay Fire TV APK (debug by default).
#
#   ./scripts/build.sh              # debug APK for the launcher
#   ./scripts/build.sh --release    # signed release APK (see package-release.sh)
#   ./scripts/build.sh --all        # debug APK + compatibility test app
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
[ -n "${ANDROID_HOME:-}" ] || { echo "error: set ANDROID_HOME or run ./scripts/setup.sh" >&2; exit 1; }
[ -x ./gradlew ] || { echo "error: ./gradlew missing; run ./scripts/setup.sh" >&2; exit 1; }

MODE="debug"
ALL=0
for arg in "$@"; do
  case "$arg" in
    --release) MODE="release" ;;
    --all) ALL=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [ "$MODE" = "release" ]; then
  exec ./scripts/package-release.sh
fi

TASKS=(":apps:fire-tv:assembleDebug")
[ "$ALL" -eq 1 ] && TASKS+=(":compatibility:test-app:assembleDebug")

echo "Building: ${TASKS[*]}"
./gradlew --console=plain "${TASKS[@]}"

echo
find . -path '*build/outputs/apk/*' -name '*.apk' -exec ls -lh {} \;
