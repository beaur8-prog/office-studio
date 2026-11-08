> **Android 설치 안내 — APK를 열었는데 설치가 막힐 때**
>
> 아래 GitHub 주소에서 받은 APK인지 파일명과 체크섬을 확인하세요. 설치 화면이 해당 브라우저 또는 파일 앱의 설치 권한을 요청하면 **설정 → 이 출처 허용**을 확인하고 설치 화면으로 돌아옵니다. 메뉴 이름은 기기마다 다를 수 있습니다. [Google의 다른 소스 앱 설치 안내](https://support.google.com/android/answer/9457058?hl=ko)
>
> 회사·기관 정책으로 설치가 제한되거나 유해 앱 탐지 경고가 나타나면 설치를 중단하고 관리자에게 확인하세요. Play 프로텍트의 유해 앱 차단은 유지하세요. [Google Play 프로텍트 안내](https://support.google.com/googleplay/answer/2812853?hl=ko)

> **Android 업데이트 안내 — 새 APK를 받아 기존 앱 위에 설치하세요**
>
> Android는 현재 자동 설치·자동 업데이트를 제공하지 않습니다. 다음 버전은 이 페이지에서 해당 앱의 APK를 내려받아 설치하세요. 기존 앱을 먼저 삭제하면 기기 내 복구 자료·설정이 삭제될 수 있으므로, 업데이트할 때는 앱을 유지한 채 새 APK를 설치합니다. 중요한 문서는 사본으로 저장해 보관하세요.

# 오피스 스튜디오 Android

**Office Studio Android 1.0.9 · 2026년 11월 08일 · 변경 앱 업데이트**

Word Studio **1.0.3**로 갱신했습니다. Sheet Studio **1.0.4**, Slide Studio **1.0.5**은 기존 파일을 그대로 제공합니다.

표·발표 자료·문서를 보고 기본 터치 편집을 할 수 있는 **Sheet Studio·Slide Studio·Word Studio**를 제공합니다. 세 앱은 각각 설치하며 앱 목록에 고유한 아이콘으로 표시됩니다.

## 1. Android 다운로드

**Android 10 이상 · Android System WebView 120 이상**이 필요합니다. 시스템 WebView는 최신 버전으로 업데이트하는 것을 권장합니다. 설치와 기본 문서 처리는 오프라인에서도 가능하며 Node.js나 Microsoft Office를 설치할 필요가 없습니다.

| 아이콘 | 앱 | 설치 파일 | 크기 |
| --- | --- | --- | --- |
| ![Sheet Studio 아이콘](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.0/Sheet-Studio-Android-icon.png) | **Sheet Studio 1.0.4** | [APK 받기](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.8/Sheet-Studio-1.0.4-Android.apk) | 5.9 MiB |
| ![Slide Studio 아이콘](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.0/Slide-Studio-Android-icon.png) | **Slide Studio 1.0.5** | [APK 받기](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.8/Slide-Studio-1.0.5-Android.apk) | 12.9 MiB |
| ![Word Studio 아이콘](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.0/Word-Studio-Android-icon.png) | **Word Studio 1.0.3** | [APK 받기](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.9/Word-Studio-1.0.3-Android.apk) | 36.4 MiB |

[SHA-256 확인 파일](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.9/SHA256SUMS-android.txt) · [빌드 구성·포함 소스](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.9/BUILD-android.json) · [1.0.0 배포 당시 검증 기록](https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.0/ANDROID-VERIFICATION.json) · [GitHub 릴리스](https://github.com/beaur8-prog/office-studio/releases/tag/android-v1.0.9)

## 2. 설치와 실행

1. 필요한 앱의 APK를 내려받습니다.
2. 브라우저의 다운로드 목록이나 파일 앱에서 APK를 엽니다. 설치 권한 안내는 맨 위를 확인합니다.
3. 설치가 끝나면 앱 목록에서 **Sheet Studio**, **Slide Studio**, **Word Studio**를 실행합니다.
4. 파일 열기에서 문서를 선택합니다. 저장·공유 화면에서는 사용자가 직접 대상과 위치를 고릅니다.

일반·원형·적응형 런처 아이콘을 포함하며, 지원 런처에서는 단색 테마 아이콘도 사용할 수 있습니다. 앱 이름과 버전은 APK에 포함되어 있습니다.

## 3. 기본 사용

- **Sheet Studio:** XLSX·CSV 등의 표 보기, 시트 이동·검색·계산값·차트 확인과 기본 셀 편집. 편집하려면 편집 모드로 전환하고, 사본 저장에서 XLSX 등 필요한 형식을 선택합니다. 1.0.4에는 전체화면 셀 입력·수식 참조와 태블릿 편집 개선을 포함합니다. XLSM은 원본 보기·공유 중심으로 사용하며 매크로 작업은 PC에서 진행합니다.
- **Slide Studio:** PPTX 보기·슬라이드 이동·검색·발표와 기본 터치 편집. 뷰어 상단의 **편집** 버튼으로 바로 편집 모드로 전환합니다. 1.0.5에는 태블릿 편집 화면 개선과 발표 종료·Esc·Back 처리를 포함합니다. PPTX 저장은 Android 파일 선택기에서 사본 위치를 정하고 완료합니다. 지원하지 않는 원본 요소가 있으면 저장 안내를 확인하고 중요한 파일은 원본과 비교하세요.
- **Word Studio:** DOCX·텍스트와 HWP/HWPX 보기, 문서 검색·쪽 이동·기본 편집. 1.0.3에는 연속 페이지 확대 배율 유지와 태블릿 편집, 안전한 Esc 처리를 포함합니다. 편집한 문서는 DOCX 사본으로 저장할 수 있습니다. HWP/HWPX 원본 파일에 직접 덮어쓰는 방식은 아닙니다.

읽기 화면과 편집 화면을 구분하며, 중요한 원본은 보관하고 사본으로 작업하세요. Android판은 모바일 보기·기본 편집 중심이며 PC판의 고급 메뉴·출력 기능과 차이가 있습니다.

## 4. 파일과 자료 보관

시스템 파일 선택기에서 사용자가 선택한 파일만 읽고, 사본 저장은 선택한 위치에 새 파일을 씁니다. 공유는 Android 공유 창에서 대상 앱을 선택합니다. 저장을 취소하거나 파일 제공자에서 오류가 나면 저장 완료로 표시하지 않습니다.

복구 자료는 앱 내부에 보관되며 앱 삭제·데이터 초기화 시 없어질 수 있습니다. 중요한 자료는 **사본 저장**으로 파일에 보관하고 별도 백업하세요. 앱 밖에 저장한 문서는 앱 제거와 별도로 남습니다.

## 5. 호환 범위와 문제 해결

- **앱 시작 때 WebView 업데이트 안내:** Android System WebView를 업데이트한 뒤 다시 실행합니다.
- **설치 실패:** Android 버전, 다운로드 파일의 체크섬과 기기 설치 정책을 확인합니다. 서명이 다른 비공식 APK로 교체하지 마세요.
- **파일 열기·저장 실패:** 파일 제공자 앱의 접근 가능 상태와 저장 공간을 확인한 뒤 파일을 다시 선택합니다. 원본을 유지하고 사본으로 시도하세요.
- **서식 차이:** Office의 모든 기능과 원본 레이아웃을 완전히 재현하지는 않습니다. 복잡한 표·그림·글꼴과 변환 결과를 원본과 비교하세요.

문제가 있으면 기기·Android 버전, 앱 이름·버전, 오류 메시지와 재현 순서를 [Office Studio 이슈](https://github.com/beaur8-prog/office-studio/issues)에 남겨 주세요. 개인 문서 대신 민감한 내용을 제거한 예제를 사용하세요.

## 6. 다운로드 파일 확인

각 APK의 SHA-256입니다.

```text
2dc2902100494f83d5813eb045f0325fd68261ab84573ce2169f67a8a96ca028  Sheet-Studio-1.0.4-Android.apk
92710993b512b78131758bf62386097087d85510583c9df8890a47d371ae679e  Slide-Studio-1.0.5-Android.apk
2b587b2d32f33d21692372ff9fdc4d85adbf8f953cd4397cdd8a358eb839ef6c  Word-Studio-1.0.3-Android.apk
```

세 앱은 같은 정식 배포 서명으로 확인했습니다. 서명 인증서 SHA-256:

```text
c166b002177773ea993d118545bec3ecdfd2a3f2f286cb7ac850ca646a7a9d3b
```

## 7. 이번 배포의 확인 범위

**이번 변경 앱 배포는 추가 설치·실행·기능 테스트를 생략했습니다.** 이전 배포 소스와 비교해 변경된 앱만 새로 빌드했습니다.

- **Word Studio 1.0.3**: 연속 페이지 확대 배율 유지, 태블릿 편집과 안전한 네이티브 Esc 처리 개선 · versionCode 4

기존 앱 ID와 서명 키를 유지했습니다. 빌드 후 서명·앱 ID·버전·크기와 SHA-256을 기록했습니다. 통합 ZIP은 제공하지 않습니다. 위 1.0.0 검증 기록은 이전 배포 당시 결과입니다.

이 문서는 리토스의 읽기 전용 공유 페이지입니다. 설치 파일은 GitHub에서도 받을 수 있습니다.
