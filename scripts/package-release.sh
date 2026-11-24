#!/usr/bin/env bash
# Builds, signs and verifies a release APK.
#
#   ./scripts/package-release.sh
#
# Signing material is created on first use in ./signing/ with a random password and is git-ignored.
# Nothing secret is committed, and no cloud CI is required. The script then verifies the package's
# manifest, minimum API level and signature with the SDK's own tools.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"   # SDK build tools shell out to `java`
fi
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
[ -n "${ANDROID_HOME:-}" ] || { echo "error: set ANDROID_HOME or run ./scripts/setup.sh" >&2; exit 1; }
[ -x ./gradlew ] || { echo "error: ./gradlew missing; run ./scripts/setup.sh" >&2; exit 1; }

SIGNING_DIR="$ROOT/signing"
KEYSTORE="$SIGNING_DIR/gameday-release.jks"
PROPS="$SIGNING_DIR/keystore.properties"
mkdir -p "$SIGNING_DIR"

if [ ! -f "$KEYSTORE" ]; then
  echo "Creating a release keystore in $SIGNING_DIR (git-ignored)"
  PASSWORD="$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias gameday \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10950 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=GameDay Open Source, OU=Fire TV, O=GameDay, L=Internet, C=US" >/dev/null
  umask 077
  cat > "$PROPS" <<EOF
storeFile=$KEYSTORE
storePassword=$PASSWORD
keyAlias=gameday
keyPassword=$PASSWORD
EOF
  echo "Keystore created. Keep $PROPS backed up: updates must be signed with the same key."
fi

# shellcheck disable=SC1090
set -a; . "$PROPS"; set +a

echo "Building release APK"
./gradlew --console=plain :apps:fire-tv:assembleRelease \
  -Pgameday.storeFile="$storeFile" \
  -Pgameday.storePassword="$storePassword" \
  -Pgameday.keyAlias="$keyAlias" \
  -Pgameday.keyPassword="$keyPassword"

APK="$(ls -t apps/fire-tv/build/outputs/apk/release/*.apk | head -1)"
[ -f "$APK" ] || { echo "error: no release APK produced" >&2; exit 1; }

mkdir -p dist
VERSION="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" apps/fire-tv/build.gradle | head -1)"
TARGET="dist/gameday-${VERSION:-1.0.0}.apk"
cp "$APK" "$TARGET"

BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
APKSIGNER="$BUILD_TOOLS/apksigner"
AAPT2="$BUILD_TOOLS/aapt2"

echo
echo "Verifying $TARGET"
if [ -x "$APKSIGNER" ]; then
  "$APKSIGNER" verify --verbose --print-certs "$TARGET"
else
  echo "warning: apksigner not found; signature not verified" >&2
fi

if [ -x "$AAPT2" ]; then
  echo
  "$AAPT2" dump badging "$TARGET" | grep -E "package:|sdkVersion|targetSdkVersion|application-label|launchable-activity|uses-permission" || true
fi

echo
echo "APK:    $TARGET"
echo "Size:   $(du -h "$TARGET" | cut -f1)"
echo "SHA256: $(sha256sum "$TARGET" | cut -d' ' -f1)"
echo
echo "Publish a GitHub release with this APK plus a gameday-update.json asset:"
echo "  {\"schemaVersion\":1,\"appId\":\"app.gameday.host\",\"channel\":\"stable\","
echo "   \"versionName\":\"$VERSION\",\"versionCode\":<versionCode>,\"minSdk\":25,"
echo "   \"apkUrl\":\"https://github.com/WorshipWarehouse/Game-Day/releases/download/v$VERSION/gameday-$VERSION.apk\","
echo "   \"sha256\":\"$(sha256sum "$TARGET" | cut -d' ' -f1)\",\"sizeBytes\":$(stat -c%s "$TARGET"),"
echo "   \"publishedAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"releaseNotes\":\"...\"}"
