#!/usr/bin/env bash
# Collect evidence; not a visual review. Run on the agent's disposable emulator.
set -euo pipefail
out="${E2E_OUTPUT_DIR:-e2e-artifacts}"
mkdir -p "$out/captures"
package="fr.loevan.jeancalcul.v2"
activity="$package/.MainActivity"

serial=$(adb get-serialno 2>/dev/null | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "Refusing to capture a physical/personal device" >&2
  exit 2
fi
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r[:space:]')" != "1" ]]; then
  echo "Not an emulator" >&2
  exit 2
fi
if ! adb shell pm path "$package" | grep -q '^package:'; then
  echo "Debug APK missing on emulator; run instrumentation first" >&2
  exit 1
fi

restore() {
  adb shell cmd uimode night no >/dev/null 2>&1 || true
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
}
trap restore EXIT
start() {
  adb shell am force-stop "$package" >/dev/null 2>&1 || true
  adb shell am start -W -n "$activity" || return 1
  sleep 3
  adb shell pidof "$package" >/dev/null 2>&1 || {
    echo "Jean-Calcul process missing after launch" >&2
    return 1
  }
}
capture() {
  local name="$1"
  if ! adb exec-out screencap -p > "$out/captures/$name"; then
    rm -f "$out/captures/$name"
    return 1
  fi
  echo "Captured $name (unreviewed; agent must check for Android ANR dialogs)"
}

adb shell cmd uimode night yes
start && capture "05-dark.png"
adb shell cmd uimode night no
adb shell wm size 360x780
adb shell wm density 160
start && capture "06-small.png"
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.5
start && capture "07-large-font.png"
adb shell settings put system font_scale 1.0
start
# Tap bottom input: geometry is derived from real emulator bounds instead of
# hard-coded Pixel 6 coordinates. The agent must verify IME is actually open.
size=$(adb shell wm size | tr -d '\r' | tail -n 1)
size="${size##* }"
if [[ "$size" =~ ^([0-9]+)x([0-9]+)$ ]]; then
  width="${BASH_REMATCH[1]}"
  height="${BASH_REMATCH[2]}"
  adb shell input tap "$((width / 3))" "$((height - 115))"
else
  echo "Unable to determine screen bounds for keyboard scenario" >&2
  exit 1
fi
sleep 3
capture "08-keyboard.png"
