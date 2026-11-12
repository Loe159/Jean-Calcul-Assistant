#!/usr/bin/env bash
# Local instrumented tests on a disposable Android emulator only.
set -uo pipefail
out="${E2E_OUTPUT_DIR:-e2e-artifacts}"
mkdir -p "$out/captures"
echo NOT_RUN > "$out/instrumentation.txt"

if ! command -v adb >/dev/null 2>&1; then
  echo "ADB missing" >&2; exit 1
fi
serial=$(adb get-serialno 2>/dev/null | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "Refusing instrumentation on a physical/personal Android device: $serial" >&2
  exit 1
fi
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r[:space:]')" != "1" ]]; then
  echo "Device does not appear to be an emulator" >&2; exit 1
fi
if ! ./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest; then
  echo FAIL > "$out/instrumentation.txt"
  exit 1
fi
if ! adb install -r app/build/outputs/apk/debug/app-debug.apk; then
  echo FAIL > "$out/instrumentation.txt"
  exit 1
fi
test_apk=$(find app/build/outputs/apk/androidTest/debug -name '*.apk' -print -quit)
if [[ -z "$test_apk" ]] || ! adb install -r "$test_apk"; then
  echo FAIL > "$out/instrumentation.txt"
  exit 1
fi

# Keep the app installed to pull the test screenshots BEFORE Gradle's cleanup.
adb shell am instrument -w -r \
  fr.loevan.jeancalcul.v2.test/androidx.test.runner.AndroidJUnitRunner \
  > "$out/instrumentation.log" 2>&1
cat "$out/instrumentation.log"
adb pull /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/e2e/. \
  "$out/captures/" >/dev/null 2>&1 || true
if grep -q 'INSTRUMENTATION_CODE: -1' "$out/instrumentation.log" \
   && grep -q 'OK (' "$out/instrumentation.log"; then
  echo PASS > "$out/instrumentation.txt"
  exit 0
fi
echo FAIL > "$out/instrumentation.txt"
echo "Instrumentation failed. A process crash or ANR is a FAIL, not a visual PASS." >&2
exit 1
