#!/usr/bin/env bash
# Runs GameDay's automated tests locally. No cloud CI and no device required.
#
#   ./scripts/test.sh            # JVM unit tests for :core and :runtime-api
#   ./scripts/test.sh --device   # also installs and launches the on-device compat test app
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ -f local.properties ]; then
  export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
[ -x ./gradlew ] || { echo "error: ./gradlew missing; run ./scripts/setup.sh" >&2; exit 1; }

echo "Running JVM unit tests"
./gradlew --console=plain :core:test :runtime-api:test

python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET

total = failures = skipped = 0
rows = []
for path in glob.glob("*/build/test-results/test/*.xml") + \
            glob.glob("*/*/build/test-results/test/*.xml"):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for suite in root.iter("testsuite"):
        if suite.get("name") is None:
            continue
        t = int(suite.get("tests", 0))
        f = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        s = int(suite.get("skipped", 0))
        total += t
        failures += f
        skipped += s
        rows.append((suite.get("name"), t, f, s))

print()
print("Test summary")
print("{:<42} {:>6} {:>8} {:>8}".format("suite", "tests", "failed", "skipped"))
for name, t, f, s in sorted(rows):
    print("{:<42} {:>6} {:>8} {:>8}".format(name.replace("app.gameday.", ""), t, f, s))
print("-" * 68)
print("{:<42} {:>6} {:>8} {:>8}".format("TOTAL", total, failures, skipped))
raise SystemExit(1 if failures else 0)
PY

echo
echo "Running Android Lint (API-level and correctness gate)"
# Lint is the regression gate for a whole class of device-only bugs: an API-26 call in API-25 code
# compiles cleanly and then throws NoSuchMethodError on the Fire TV. The unit tests cannot see it;
# lint's API database can.
./gradlew --console=plain :apps:fire-tv:lintDebug :compatibility:test-app:lintDebug

if [ "${1:-}" = "--device" ]; then
  echo
  echo "Installing the compatibility test app"
  ./scripts/deploy.sh --compat-only
fi
