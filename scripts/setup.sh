#!/usr/bin/env bash
# GameDay local toolchain setup.
#
# Verifies a JDK 17 and an Android SDK with platform 34 and build-tools 34.0.0, installs the SDK
# packages when sdkmanager is available, and writes local.properties. Needs no root and no paid
# services. Run once per machine; safe to re-run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

info() { printf '  %s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

echo "GameDay setup"

# ---------------------------------------------------------------- Java
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
else
  fail "no Java found. Install JDK 17 (Temurin, OpenJDK, or Android Studio's JBR) and re-run."
fi
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | head -1)"
info "java: $JAVA_VERSION"
case "$JAVA_VERSION" in
  *\"17*|*\"18*|*\"19*|*\"20*|*\"21*) : ;;
  *) printf 'warning: the build is qualified on JDK 17; %s may still work.\n' "$JAVA_VERSION" >&2 ;;
esac

# ---------------------------------------------------------------- Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$ROOT/local.properties" ]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -1)"
fi
if [ -z "$SDK" ]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/tc/sdk"; do
    if [ -d "$candidate" ]; then SDK="$candidate"; break; fi
  done
fi

if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  fail "no Android SDK found. Install Android Studio or the command-line tools, then export ANDROID_HOME."
fi
info "android sdk: $SDK"

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  if [ ! -d "$SDK/platforms/android-34" ] || [ ! -d "$SDK/build-tools/34.0.0" ]; then
    info "installing platform-tools, platforms;android-34, build-tools;34.0.0"
    yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  fi
else
  info "sdkmanager not found; assuming Android Studio installed the packages"
fi

for required in "platforms/android-34" "build-tools/34.0.0"; do
  [ -d "$SDK/$required" ] || fail "missing SDK component: $required (install it with sdkmanager)"
done

printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/local.properties"
info "wrote local.properties"

# ---------------------------------------------------------------- Gradle wrapper
if [ ! -x "$ROOT/gradlew" ]; then
  if command -v gradle >/dev/null 2>&1; then
    info "generating the Gradle wrapper"
    ( cd "$ROOT" && gradle wrapper --gradle-version 8.7 --distribution-type bin )
  else
    fail "gradlew is missing and system gradle is not installed."
  fi
fi
chmod +x "$ROOT/gradlew" "$ROOT"/scripts/*.sh 2>/dev/null || true

echo
echo "Setup complete. Next:"
echo "  ./scripts/test.sh              # unit tests"
echo "  ./scripts/build.sh             # debug APK"
echo "  ./scripts/package-release.sh   # signed release APK in dist/"
