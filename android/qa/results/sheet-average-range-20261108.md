# Android Sheet Average range QA

Renderer `418f39cf3fe4bb966714739bb66fb66adde1541b` (0.7.48), native `26f24c7d00232d09ebca6aea14c40a9956be704d`.

[CI](https://github.com/beaur8-prog/office-studio/actions/runs/42473013774): **8 passed**, 170.742s. Existing full-window, IME, Escape/Back, close/recovery, numeric overflow and native mouse regression cases are retained.

The tablet 100k-cell case also clicks the actual Formula ribbon Average button. It checks `=AVERAGE(A1:A3)` in focused inline editing, the selected `A1:A3` reference, a real KEYCODE_ENTER commit, numeric evaluation and undo/redo.

Isolated package `io.officestudio.sheet.qa`, version 1.0.7-qa/code8; SHA-256 `042012ad7da62bf5356edafbfbd4fefd40304c5464495e1513310cc9bdcc53fd`. No production release/Lithos change. Physical Android/DeX remains untested.
