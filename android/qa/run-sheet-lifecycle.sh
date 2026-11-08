#!/usr/bin/env bash
set -euo pipefail
out=test-results/sheet-native
mkdir -p "$out"
# This runner is for a fresh emulator and uses a separate .qa application ID.
adb install -r android/app/build/outputs/apk/sheet/debug/app-sheet-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/sheet/debug/app-sheet-debug-androidTest.apk
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.tall
adb shell am instrument --no-hidden-api-checks -w -r -e class io.officestudio.shell.SheetWindowInstrumentedTest io.officestudio.sheet.qa.test/androidx.test.runner.AndroidJUnitRunner > "$out/instrumentation.log" 2>&1 || true
adb logcat -d > "$out/logcat.txt"
adb pull /sdcard/Android/data/io.officestudio.sheet.qa/files/sheet-qa "$out/" || true
cat "$out/instrumentation.log"
python3 - "$out/instrumentation.log" <<'PY'
import pathlib,sys
s=pathlib.Path(sys.argv[1]).read_text()
assert 'OK (8 tests)' in s and not any(t in s for t in ['FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed']),s
PY
