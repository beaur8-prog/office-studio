# Sheet Android system printing QA

Renderer `3b00ce0748d8a260c34c3137c78553ad64927fa9` (0.7.49), native `2364ded71d578f6b03322bfb740eb237aaa1a32d`.

[CI](https://github.com/beaur8-prog/office-studio/actions/runs/42488723539): **9 passed**, 190.915s. Existing real window/cutout, IME/live draft, Escape/Back, close/recovery, stable scrolling, numeric overflow/mouse resize and Average range cases remain enabled.

The new native print case loads a readonly fixture, cancels/retries the real print activity, saves selection (1 page), A4 landscape (4 pages), and Letter landscape (4 pages, 792x612pt) through DocumentsUI. PdfRenderer checks page count, orientation, nonblank output and red PNG pixels. Invalid HTML then a fresh print/cancel checks file-lock/view cleanup. The workbook, undo history and dirty state stay identical.

All nine Poppler pages were visually reviewed (Korean, formulas, repeats, merged title, red negative format, numeric hashes, chart, image). Final CI rasters exactly match the reviewed pages. A corrupt older test PNG was repaired upstream; system paper-list scrolling and transition waits were fixed in the harness. Only named Launcher/system-guide interruptions are handled; app failures are retained and taps are repeated only when no DOM pointer was delivered.

Isolated package `io.officestudio.sheet.qa`, version 1.0.8-qa/code9. APK SHA-256 `a99c2f4954cfa356fece378d7c777e760e346b5349a44829a6e3688f0650b391`. CI debug signing; no production release, Lithos or shared URL change. Shared active Word/Slide worktree remains untouched. Physical Android/DeX and hardware printers are not connected. System paper/margin changes scale prepared pages; app orientation repaginates. Some pdfplumber Type 3 glyph extraction returns NUL; visible glyphs were cross-checked in PDF page renders.
