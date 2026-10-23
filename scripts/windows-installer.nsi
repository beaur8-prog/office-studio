Unicode true
RequestExecutionLevel user
SetCompress off
CRCCheck force
ManifestDPIAware true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
!include "Sections.nsh"

Name "Office Studio ${SUITE_VERSION}"
OutFile "${OUTPUT_FILE}"
BrandingText "Office Studio"
ShowInstDetails show
VIProductVersion "${SUITE_VERSION}.0"
VIAddVersionKey /LANG=1033 "ProductName" "Office Studio Setup"
VIAddVersionKey /LANG=1033 "FileDescription" "Sheet Studio, Slide Studio and Word Studio Setup"
VIAddVersionKey /LANG=1033 "CompanyName" "Office Studio"
VIAddVersionKey /LANG=1033 "FileVersion" "${SUITE_VERSION}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${SUITE_VERSION}"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Independent Studio applications"

Var CurrentApp
Var SetupMutex

!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "Office Studio ${SUITE_VERSION}"
!define MUI_WELCOMEPAGE_TEXT "$(WelcomeText)"
!insertmacro MUI_PAGE_WELCOME
!define MUI_COMPONENTSPAGE_NODESC
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE CheckSelection
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_TEXT "$(FinishText)"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Korean"

LangString WelcomeText ${LANG_ENGLISH} "Install Sheet Studio, Slide Studio and Word Studio for your Windows account.$\r$\n$\r$\nSave your work and close all three apps before continuing.$\r$\n$\r$\nAll three apps are selected by default. You can choose which apps to install on the next screen. Internet, Node.js and Office are not required."
LangString WelcomeText ${LANG_KOREAN} "현재 Windows 계정에 Sheet Studio, Slide Studio, Word Studio를 한 번에 설치합니다.$\r$\n$\r$\n계속하기 전에 작업을 저장하고 세 앱을 모두 종료하세요.$\r$\n$\r$\n다음 화면에서 설치할 앱을 선택할 수 있습니다. 기본값은 세 앱 모두 설치입니다. 인터넷, Node.js, Office는 필요하지 않습니다."
LangString FinishText ${LANG_ENGLISH} "The selected Studio apps have been installed. Open each app from the Start menu.$\r$\n$\r$\nTo remove an app, select its name in Windows Settings > Apps."
LangString FinishText ${LANG_KOREAN} "선택한 Studio 앱의 설치가 완료되었습니다. 시작 메뉴에서 각 앱을 실행하세요.$\r$\n$\r$\n앱을 제거하려면 Windows 설정 > 앱에서 해당 앱을 선택하세요."
LangString FailedText ${LANG_ENGLISH} "$CurrentApp could not be installed (code $0). Previously completed apps remain installed. Close the Studio apps, check disk space and run this installer again."
LangString FailedText ${LANG_KOREAN} "$CurrentApp 설치에 실패했습니다 (코드 $0). 앞서 설치된 앱은 유지됩니다. Studio 앱 종료와 디스크 여유 공간을 확인한 뒤 이 설치 파일을 다시 실행하세요."
LangString PlatformText ${LANG_ENGLISH} "Office Studio requires Windows 10 or later on an Intel/AMD 64-bit PC."
LangString PlatformText ${LANG_KOREAN} "Office Studio는 Windows 10 이상의 Intel/AMD 64비트 PC가 필요합니다."
LangString BusyText ${LANG_ENGLISH} "Another Office Studio installer is running."
LangString BusyText ${LANG_KOREAN} "다른 Office Studio 설치 프로그램이 실행 중입니다."
LangString SelectionText ${LANG_ENGLISH} "Select at least one Studio app."
LangString SelectionText ${LANG_KOREAN} "설치할 Studio 앱을 하나 이상 선택하세요."
LangString CloseText ${LANG_ENGLISH} "Save your work and close all Sheet Studio, Slide Studio and Word Studio windows before installing."
LangString CloseText ${LANG_KOREAN} "작업을 저장하고 Sheet Studio, Slide Studio, Word Studio의 모든 창을 종료한 뒤 다시 설치하세요."

; Query running executables through the built-in Windows process snapshot API.
; This also finds applications installed in a custom directory. No app is killed.
Function CheckAppsClosed
  System::Call 'kernel32::CreateToolhelp32Snapshot(i 2, i 0) p .r1'
  ${If} $1 == -1
    MessageBox MB_ICONSTOP "$(CloseText)" /SD IDOK
    SetErrorLevel 2
    Quit
  ${EndIf}
  ; PROCESSENTRY32W for this 32-bit NSIS process: dwSize=556, szExeFile at 36.
  System::Alloc 556
  Pop $2
  System::Call '*$2(i 556)'
  System::Call 'kernel32::Process32FirstW(p r1, p r2) i .r3'
  loop:
    ${If} $3 == 0
      Goto done
    ${EndIf}
    IntOp $4 $2 + 36
    System::Call '*$4(&w260 .r5)'
    ${If} $5 == "Sheet Studio.exe"
    ${OrIf} $5 == "Slide Studio.exe"
    ${OrIf} $5 == "Word Studio.exe"
      System::Free $2
      System::Call 'kernel32::CloseHandle(p r1)'
      MessageBox MB_ICONSTOP "$(CloseText)" /SD IDOK
      SetErrorLevel 2
      Quit
    ${EndIf}
    System::Call 'kernel32::Process32NextW(p r1, p r2) i .r3'
    Goto loop
  done:
  System::Free $2
  System::Call 'kernel32::CloseHandle(p r1)'
FunctionEnd

Function .onInit
  SetShellVarContext current
  ${IfNot} ${AtLeastWin10}
    Goto unsupported
  ${EndIf}
  ${IfNot} ${IsNativeAMD64}
    Goto unsupported
  ${EndIf}
  System::Call 'kernel32::CreateMutexW(p 0, i 0, w "Local\OfficeStudioSetup") p .r0 ?e'
  Pop $1
  StrCpy $SetupMutex $0
  ${If} $0 == 0
  ${OrIf} $1 == 183
    MessageBox MB_ICONSTOP "$(BusyText)" /SD IDOK
    SetErrorLevel 2
    Quit
  ${EndIf}
  InitPluginsDir
  Call CheckAppsClosed
  Return
  unsupported:
    MessageBox MB_ICONSTOP "$(PlatformText)" /SD IDOK
    SetErrorLevel 2
    Quit
FunctionEnd

!include "${APP_SECTIONS}"

Function CheckSelection
  SectionGetFlags ${sheet} $0
  SectionGetFlags ${slide} $1
  SectionGetFlags ${word} $2
  IntOp $0 $0 | $1
  IntOp $0 $0 | $2
  IntOp $0 $0 & ${SF_SELECTED}
  ${If} $0 == 0
    MessageBox MB_ICONEXCLAMATION "$(SelectionText)" /SD IDOK
    Abort
  ${EndIf}
  Call CheckAppsClosed
FunctionEnd
