#!/usr/bin/env bash
set -euo pipefail
out="$PWD/test-results/android-word-viewer"
mkdir -p "$out"
profile="${1:-phone}"
case "$profile" in phone|tablet) ;; *) exit 2 ;; esac
adb wait-for-device
# Let a new emulator finish its launcher/setup animation before launching tests.
sleep 5
adb install -r android/app/build/outputs/apk/word/debug/app-word-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/word/debug/app-word-debug-androidTest.apk
# Each profile boots its own fresh hardware AVD. Resizing a used phone emulator
# into a tablet can crash the emulator renderer and never launch Word's tests.
adb shell am force-stop io.officestudio.word
adb shell pm clear io.officestudio.word
adb shell wm size > "$out/$profile-device.txt"
adb shell wm density >> "$out/$profile-device.txt"
adb shell getprop ro.build.version.release >> "$out/$profile-device.txt"
adb shell getprop ro.build.version.sdk >> "$out/$profile-device.txt"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am instrument -w -r -e studioQaProfile "$profile" -e class io.officestudio.shell.WordViewerInstrumentedTest,io.officestudio.shell.WordPrintInstrumentedTest io.officestudio.word.test/androidx.test.runner.AndroidJUnitRunner > "$out/$profile.log" 2>&1
cat "$out/$profile.log"
adb pull /sdcard/Android/data/io.officestudio.word/files/viewer-qa "$out/$profile-shots" || true
# Preserve only these synthetic QA outputs even if an in-app assertion fails.
mkdir -p "$out/$profile-pdfs"
adb pull /sdcard/Download/WordStudio-Print-QA-docx.pdf "$out/$profile-pdfs/docx.pdf" || true
adb pull /sdcard/Download/WordStudio-Print-QA-hwp.pdf "$out/$profile-pdfs/hwp.pdf" || true
adb logcat -d > "$out/$profile-logcat.txt"
adb exec-out screencap -p > "$out/$profile.png"
grep -q 'OK (5 tests)' "$out/$profile.log"
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$out/$profile.log"; then exit 1; fi
