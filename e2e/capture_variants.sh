#!/usr/bin/env bash
# Synthetic emulator only; call after installing the debug APK.
set -euo pipefail
mkdir -p e2e-artifacts/captures
package=fr.loevan.jeancalcul.v2
activity="$package/.MainActivity"
restore() {
  adb shell cmd uimode night no || true
  adb shell wm size reset || true
  adb shell wm density reset || true
  adb shell settings put system font_scale 1.0 || true
}
trap restore EXIT
start() {
  adb shell am force-stop "$package" || true
  adb shell am start -n "$activity"
  sleep 3
}
adb shell cmd uimode night yes
start
adb exec-out screencap -p > e2e-artifacts/captures/05-dark.png
adb shell cmd uimode night no
adb shell wm size 360x780
adb shell wm density 160
start
adb exec-out screencap -p > e2e-artifacts/captures/06-small.png
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.5
start
adb exec-out screencap -p > e2e-artifacts/captures/07-large-font.png
adb shell settings put system font_scale 1.0
start
# Tap bottom composer on Pixel 6; the screenshot shows actual IME state, reviewed by vision.
adb shell input tap 320 2190
sleep 3
adb exec-out screencap -p > e2e-artifacts/captures/08-keyboard.png
