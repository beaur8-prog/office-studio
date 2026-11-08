# Sheet numeric width and tablet mouse QA

Renderer: `8ce7d347dc229e8286f60c788dfb962b74ef75ef` (0.7.47). Native: `666fc26a656062fb4e5efb996605d008c9293d8b`.

[CI final run](https://github.com/beaur8-prog/office-studio/actions/runs/42471446698): **8 passed**, 166.122s. An earlier environment was blocked by an Android system-process ANR before app window focus. Final code was validated on a fresh emulator. Earlier harness revisions missed Chromium mouse-button events or used remapped global mouse coordinates; they are not passing validation.

API35 Pixel6 with tall cutout. Tablet case temporarily uses 1920x1200/mdpi and restores it. SOURCE_MOUSE MotionEvents are delivered through native WebView touch + generic-motion dispatch with BUTTON_PRESS/BUTTON_RELEASE; reflection access to the framework setter is enabled only for instrumentation. Shipping app code and configuration are unaffected.

100k cells: fixed/currency/formula hashes, General scientific notation, full formula input; mouse guide with unchanged width and 1 grid redraw(s) across 20 moves (initial focus/worker repaint allowed, per-move redraw forbidden); commit, full display and undo/redo. Existing full-window, IME, Escape/Back and key-up, close/recovery and scroll display cases also pass.

QA package: `io.officestudio.sheet.qa`, 1.0.6-qa/code7; SHA-256 `acb9c738e89863a8b781b9273e0036f37750a7d0933137b5c1009042b0f0c9a5`. No production release or Lithos update. Physical OEM/DeX and global mouse input remain untested.
