# Sheet Android trackpad pinch / Ctrl-wheel QA

Renderer `4c113931f7b404e1b6147720096f9b50fdcfb255` (0.7.50), native `f8ff0be164810379c4acc5fde65b1d5d9bdf1411` on `codex/android-sheet-gesture-zoom-20261110`.

[Final CI](https://github.com/beaur8-prog/office-studio/actions/runs/42791651239): **10 passed**, 204.327s, API 35 emulator. Evidence artifact ID `13752561000`.

The shared DOM Ctrl-wheel route alone did not change scale in the installed Android WebView (first [regression run](https://github.com/beaur8-prog/office-studio/actions/runs/42784778058), 9 pass / 1 fail). Sheet now consumes native Ctrl+ACTION_SCROLL before WebView page zoom and forwards normalized pointer coordinates to the same sheet zoom controller. Plain wheel and Word/Slide keep their default route.

API34+ classified trackpad pinch is forwarded separately, consuming every relative AXIS_GESTURE_PINCH_SCALE_FACTOR sample including batched history. The native test dispatches actual MotionEvents through WebView: Ctrl-wheel up/down; classified DOWN, MOVE with 1.02 historical and 1.03 current factors, MOVE 1.1 and UP. The expected rounded scale includes all three factors. Page scale remains 1 and innerWidth, workbook, selection, undo history and dirty state stay identical. Readonly Ctrl-wheel retains its permission and model. Native screenshots `native-trackpad-pinch.png` and `native-ctrl-wheel-zoom.png` were reviewed.

The existing nine native checks remain enabled: full window/cutout and IME/live input, Escape/Back/viewer exit, confirmed Activity close and recovery, stable calculation scrolling, numeric overflow/mouse resizing, Average range selection and real PrintManager cancellation/PDF saving. All passed. The classified path leaves touchscreen and ordinary trackpad scrolling with WebView. Native pinch state resets on focus loss; web pending gestures also cancel on blur/hidden/resize.

Isolated test package `io.officestudio.sheet.qa`, version `1.0.9-qa` / code 10. APK SHA-256 `ac1ea6d3e2b02e39dfedbaaeffa470d7b4d5a22b50c079f1ea69aac62d690f2c`. Debug signing only; no production release or Lithos update. Active shared Word/Slide worktree and user profiles/files/clipboard were not modified.

No physical Android/DeX hardware or trackpad was connected. Older Android versions retain Ctrl-wheel/the common browser input route where delivered; the classified trackpad API exists from Android 14. [Official MotionEvent input contract](https://developer.android.com/reference/android/view/MotionEvent#AXIS_GESTURE_PINCH_SCALE_FACTOR).
