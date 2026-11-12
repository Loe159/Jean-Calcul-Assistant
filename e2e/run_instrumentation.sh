#!/usr/bin/env bash
# Preserve test APK and external screenshots until ADB pulls them.
set -uo pipefail
mkdir -p e2e-artifacts/captures
if ! ./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest; then
  echo FAIL > e2e-artifacts/instrumentation.txt
  exit 1
fi
if ! adb install -r app/build/outputs/apk/debug/app-debug.apk; then
  echo FAIL > e2e-artifacts/instrumentation.txt
  exit 1
fi
test_apk=$(find app/build/outputs/apk/androidTest/debug -name '*.apk' -print -quit)
if [[ -z "$test_apk" ]] || ! adb install -r "$test_apk"; then
  echo FAIL > e2e-artifacts/instrumentation.txt
  exit 1
fi
adb shell am instrument -w -r \
  fr.loevan.jeancalcul.v2.test/androidx.test.runner.AndroidJUnitRunner \
  > e2e-artifacts/instrumentation.log 2>&1
cat e2e-artifacts/instrumentation.log
adb pull /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/e2e/. \
  e2e-artifacts/captures/ >/dev/null 2>&1 || true
if grep -q 'INSTRUMENTATION_CODE: -1' e2e-artifacts/instrumentation.log \
   && grep -q 'OK (' e2e-artifacts/instrumentation.log; then
  echo PASS > e2e-artifacts/instrumentation.txt
  exit 0
fi
echo FAIL > e2e-artifacts/instrumentation.txt
exit 1
