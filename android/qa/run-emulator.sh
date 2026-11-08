#!/usr/bin/env bash
set -euo pipefail
kind="${1:?phone or tablet}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
inputs="$root/test-results/android-inputs"
out="$root/test-results/android-native-$kind"
mkdir -p "$out"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
if [ "$kind" = tablet ]; then adb shell wm size 1600x2560; adb shell wm density 240; fi
adb shell svc wifi disable
adb shell svc data disable
adb shell mkdir -p /sdcard/Download/OfficeStudioQA
printf 'PRESERVE EXTERNAL OFFICE DOCUMENT' > "$out/external-original.txt"
adb shell "printf '%s' 'PRESERVE EXTERNAL OFFICE DOCUMENT' > /sdcard/Download/OfficeStudioQA/original.txt"
adb shell getprop > "$out/device-properties.txt"
for id in sheet slide word; do
  apk="$inputs/$id.apk"
  test="$inputs/$id-test.apk"
  package="io.officestudio.$id"
  adb install "$apk"
  adb install "$test"
  adb shell dumpsys package "$package" > "$out/$id-package.txt"
  adb shell am instrument -w -r -e certificate "$CERTIFICATE_SHA256" "$package.test/androidx.test.runner.AndroidJUnitRunner" > "$out/$id-native.log" 2>&1
  cat "$out/$id-native.log"
  if ! grep -q 'OK (' "$out/$id-native.log" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$out/$id-native.log"; then
    adb logcat -d > "$out/$id-logcat.txt"
    adb exec-out screencap -p > "$out/$id-failed.png"
    exit 1
  fi
  adb install -r "$apk"
  adb shell am instrument -w -r -e reinstall true -e class io.officestudio.shell.ReleaseInstrumentedTest#verifyReinstallPreservesStorage "$package.test/androidx.test.runner.AndroidJUnitRunner" > "$out/$id-reinstall.log" 2>&1
  cat "$out/$id-reinstall.log"
  grep -q 'OK (' "$out/$id-reinstall.log"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$out/$id-reinstall.log"; then exit 1; fi
  adb shell am start -n "$package/io.officestudio.shell.MainActivity"
  sleep 3
  adb exec-out screencap -p > "$out/$id-startup.png"
  adb shell am force-stop "$package"
  adb uninstall "$package.test"
done
python3 android/qa/summarize.py "$out"
for id in sheet slide word; do adb uninstall "io.officestudio.$id"; done
adb shell cat /sdcard/Download/OfficeStudioQA/original.txt > "$out/external-after-uninstall.txt"
cmp "$out/external-original.txt" "$out/external-after-uninstall.txt"
python3 - "$out" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1])/'native-verification.json';j=json.loads(p.read_text());j['uninstallVerified']=True;j['externalDocumentPreservedAfterUninstall']=True;p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
PY
