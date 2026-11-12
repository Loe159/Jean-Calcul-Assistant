#!/usr/bin/env bash
# Instrumentation on a disposable Android emulator (Linux/macOS). No visual review.
# The calling workflow/agent enforces the overall timeout.
set -uo pipefail
out="${E2E_OUTPUT_DIR:-e2e-artifacts}"
mkdir -p "$out/captures"
printf 'NOT_RUN\n' > "$out/instrumentation.txt"

diagnostics() {
  local reason="$1"
  printf 'FAIL\n' > "$out/instrumentation.txt"
  echo "E2E failure: $reason" >&2
  # Bounded diagnostics from the disposable emulator: no personal device/data.
  adb logcat -d -v brief -t 600 AndroidRuntime:E ActivityManager:E ActivityTaskManager:E InputDispatcher:E WindowManager:E '*:S' \
    > "$out/android-errors.log" 2>/dev/null || true
  adb shell dumpsys meminfo fr.loevan.jeancalcul.v2 \
    > "$out/app-meminfo.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$out/captures/00-failure-diagnostic.png" 2>/dev/null || true
}

if ! command -v adb >/dev/null 2>&1; then
  echo "ADB missing" >&2
  exit 1
fi
serial=$(adb get-serialno 2>/dev/null | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]] || \
   [[ "$(adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r[:space:]')" != "1" ]]; then
  echo "Refusing instrumentation on non-emulator device: $serial" >&2
  exit 1
fi
if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r[:space:]')" != "1" ]]; then
  diagnostics "emulator boot incomplete"
  exit 1
fi
echo "Emulator ready: $serial, SDK $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if ! ./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest; then
  diagnostics "Gradle build of instrumentation APK failed"
  exit 1
fi
if ! adb install -r app/build/outputs/apk/debug/app-debug.apk; then
  diagnostics "debug APK installation failed"
  exit 1
fi
test_apk=$(find app/build/outputs/apk/androidTest/debug -name '*.apk' -print -quit)
if [[ -z "$test_apk" ]] || ! adb install -r "$test_apk"; then
  diagnostics "test APK installation failed"
  exit 1
fi
# First check that Jean-Calcul starts. This separates emulator/OS instability
# from a failing Compose instrumentation test.
if ! adb shell am start -W -n \
   fr.loevan.jeancalcul.v2/.MainActivity > "$out/startup.log" 2>&1; then
  cat "$out/startup.log"
  diagnostics "app startup failed before instrumentation"
  exit 1
fi
cat "$out/startup.log"
sleep 2
if ! adb shell pidof fr.loevan.jeancalcul.v2 >/dev/null 2>&1; then
  diagnostics "app process disappeared after startup"
  exit 1
fi
adb exec-out screencap -p > "$out/captures/00-startup-diagnostic.png" 2>/dev/null || true
adb logcat -c || true
adb shell am force-stop fr.loevan.jeancalcul.v2 || true

# Run Compose/UI Automator tests; retain logs and screenshots even on a crash.
adb shell am instrument -w -r \
  fr.loevan.jeancalcul.v2.test/androidx.test.runner.AndroidJUnitRunner \
  > "$out/instrumentation.log" 2>&1
instrumentation_exit=$?
cat "$out/instrumentation.log"
adb pull /sdcard/Android/data/fr.loevan.jeancalcul.v2/files/e2e/. \
  "$out/captures/" >/dev/null 2>&1 || true
if [[ "$instrumentation_exit" -eq 0 ]] && \
   grep -q 'INSTRUMENTATION_CODE: -1' "$out/instrumentation.log" && \
   grep -q 'OK (' "$out/instrumentation.log"; then
  printf 'PASS\n' > "$out/instrumentation.txt"
  echo "Instrumented tests: PASS"
  exit 0
fi
diagnostics "instrumentation exit=$instrumentation_exit; see android-errors.log"
exit 1
