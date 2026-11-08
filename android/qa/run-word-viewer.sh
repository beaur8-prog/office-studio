#!/usr/bin/env bash
set -euo pipefail
out="$PWD/test-results/android-word-viewer"
mkdir -p "$out"
adb install -r android/app/build/outputs/apk/word/debug/app-word-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/word/debug/app-word-debug-androidTest.apk
for profile in phone tablet; do
  adb shell am force-stop io.officestudio.word
  adb shell pm clear io.officestudio.word
  if [ "$profile" = tablet ]; then adb shell wm size 1600x2560; adb shell wm density 240; fi
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  adb shell am instrument -w -r -e class io.officestudio.shell.WordViewerInstrumentedTest io.officestudio.word.test/androidx.test.runner.AndroidJUnitRunner > "$out/$profile.log" 2>&1
  cat "$out/$profile.log"
  adb logcat -d > "$out/$profile-logcat.txt"
  adb exec-out screencap -p > "$out/$profile.png"
  grep -q 'OK (2 tests)' "$out/$profile.log"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$out/$profile.log"; then exit 1; fi
  adb logcat -c
 done
