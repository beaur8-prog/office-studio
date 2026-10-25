> **Mac 설치 안내 — 보안 경고가 표시될 때**
>
> 아래 공식 GitHub 링크에서 받은 **Office-Studio-1.0.0-mac-arm64-Setup.pkg**인지 확인하세요. 현재 배포본은 Apple Developer ID 서명·공증을 받지 않았으므로 개발자 확인 경고가 나타날 수 있습니다. 파일을 한 번 열어 차단된 뒤 **시스템 설정 → 개인정보 보호 및 보안 → 그래도 열기 → 열기**를 선택합니다. 구버전 macOS에서는 **시스템 환경설정 → 보안 및 개인정보 보호**에서 해당 파일의 열기 허용 항목을 확인하세요. 설치 후 앱을 처음 실행할 때도 앱별로 같은 확인이 필요할 수 있습니다.
>
> 파일 출처와 맨 아래 SHA-256을 확인한 경우에만 허용하세요. 악성 소프트웨어 감지·손상 경고가 뜨면 강제로 실행하지 말고 파일을 다시 내려받아 체크섬을 확인하세요. 조직에서 관리하는 Mac은 관리자 정책을 따라야 합니다. [Apple 공식 보안 경고 안내](https://support.apple.com/ko-kr/102445)

# 오피스 스튜디오 Mac 다운로드

**Office Studio 1.0.0 · 2026-10-25 · Apple Silicon 전용 통합 설치**

Sheet Studio·Slide Studio·Word Studio를 한 설치 파일로 설치합니다. 설치 화면의 **사용자화**에서 필요한 앱을 고를 수 있으며, 기본값은 세 앱 모두입니다.

## 1. 다운로드

### [오피스 스튜디오 Mac 설치 파일 다운로드](https://github.com/beaur8-prog/office-studio/releases/download/mac-v1.0.0/Office-Studio-1.0.0-mac-arm64-Setup.pkg)

- **대상:** Apple Silicon(M1 이상) Mac · macOS 12 Monterey 이상
- **파일:** `Office-Studio-1.0.0-mac-arm64-Setup.pkg`
- **크기:** 385.5 MiB (404,253,605바이트)
- **설치 위치:** Finder의 **응용 프로그램** 폴더(`/Applications`)
- **권한:** 관리자 인증 필요

Intel Mac에서는 실행할 수 없습니다. Microsoft Office·Node.js를 따로 설치할 필요가 없고, 설치와 기본 편집은 오프라인에서도 가능합니다. 업데이트 확인과 다운로드에는 인터넷 연결이 필요합니다.

[SHA-256 확인 파일](https://github.com/beaur8-prog/office-studio/releases/download/mac-v1.0.0/Office-Studio-1.0.0-mac-arm64-Setup.pkg.sha256) · [배포 구성·검증 기록](https://github.com/beaur8-prog/office-studio/releases/download/mac-v1.0.0/BUILD-mac-arm64.json) · [Mac 실행 검증 기록](https://github.com/beaur8-prog/office-studio/releases/download/mac-v1.0.0/MAC-VERIFICATION.json) · [GitHub Mac 릴리스](https://github.com/beaur8-prog/office-studio/releases/tag/mac-v1.0.0)

## 2. 포함 앱과 개별 다운로드

| 앱 | 포함 버전 | 개별 다운로드 |
| --- | --- | --- |
| Sheet Studio | 0.7.23 | [Mac ZIP](https://github.com/beaur8-prog/sheet-studio/releases/download/v0.7.23/Sheet-Studio-0.7.23-macOS-arm64.zip) |
| Slide Studio | 1.6.11 | [Mac ZIP](https://github.com/beaur8-prog/slide-studio/releases/download/v1.6.11/Slide-Studio-mac-arm64.zip) |
| Word Studio | 1.4.5 | [Mac ZIP](https://github.com/beaur8-prog/word-studio/releases/download/v1.4.5/Word-Studio-1.4.5-mac-arm64.zip) |

통합 설치 파일은 세 앱을 응용 프로그램 폴더에 설치합니다. 개별 ZIP을 사용할 때는 압축을 풀고 원하는 앱을 응용 프로그램 폴더로 옮기세요.

## 3. 설치와 첫 실행

1. 중요한 문서를 저장하고 **세 Studio 앱을 모두 종료**합니다.
2. 다운로드한 PKG 파일을 엽니다. 개발자 경고가 나타나면 맨 위 안내를 따릅니다.
3. 설치 화면에서 계속을 누릅니다. 필요한 앱만 설치하려면 **사용자화**에서 선택합니다.
4. 관리자 인증 후 설치를 완료합니다. 실행 중인 앱이나 더 최신 버전의 기존 앱이 있으면 설치가 중단됩니다.
5. Finder의 **응용 프로그램**에서 Sheet Studio·Slide Studio·Word Studio를 엽니다. 앱 첫 실행 경고도 맨 위 안내에 따라 확인합니다.

이전에 다른 폴더에 설치한 복사본이 있다면 Finder에서 새 앱의 버전을 확인하세요. Dock이 예전 복사본을 가리키면 새 앱으로 다시 등록합니다.

## 4. 기본 사용

- **Sheet Studio:** 표·수식·차트 편집, XLSX·CSV 등의 열기와 저장.
- **Slide Studio:** 슬라이드 편집·발표, PPTX 열기와 저장. 이번 Mac 버전에는 최근 표 행 높이·테두리 표시 수정이 포함되어 있습니다.
- **Word Studio:** 문서·표·서식 편집, DOCX 열기와 저장, HWP·HWPX 변환 지원.

각 앱의 파일 메뉴에서 문서를 열거나 저장합니다. 중요한 원본은 별도로 보관하고 다른 이름으로 저장해 확인하세요. Microsoft Office와 완전히 같은 기능·레이아웃을 보장하지 않으며, 복잡한 문서·매크로·외부 연결 등에는 제한이 있을 수 있습니다.

## 5. Mac 업데이트

이번 Mac 앱은 실행 15초 뒤와 이후 6시간마다 **해당 앱의 Mac 정식 릴리스**를 확인하고 새 ZIP 파일을 자동 다운로드합니다. Windows 릴리스와 별도로 확인합니다.

Mac 배포 인증서가 없으므로 앱을 자동 교체하지는 않습니다. 다운로드 준비 알림 또는 **도움말 → 업데이트 확인…**에서 **다운로드 파일 보기**를 선택한 뒤, 문서를 저장하고 앱을 종료하고 ZIP을 풀어 기존 앱을 교체하세요. 통합 PKG의 새 버전을 다시 설치하는 방법도 사용할 수 있습니다.

- **업데이트 끄기:** 각 앱의 **도움말 → 업데이트 자동 확인·다운로드** 체크를 해제합니다. 앱별로 기억합니다.
- **나중에 설치:** 새 버전이 준비되어도 직접 교체하기 전까지 기존 버전을 계속 사용할 수 있습니다.
- **이전 Mac 버전 사용자:** 업데이트 기능이 없는 기존 앱은 이번 배포본을 한 번 직접 설치해야 합니다.
- **파일 확인:** 업데이트는 파일 크기와 SHA-256을 검사한 뒤 제공합니다.

## 6. 문서 보존과 제거

설치 패키지는 앱 파일만 설치하며 저장한 문서와 사용자 설정 폴더를 설치 대상으로 포함하지 않습니다. 설치·교체 전에 문서를 저장하고 중요한 파일은 별도 백업하세요.

앱을 제거하려면 종료한 뒤 응용 프로그램 폴더의 해당 앱을 휴지통으로 이동합니다. 문서 파일과 사용자 설정·자동 복구 자료는 별도로 남을 수 있습니다.

## 7. 문제가 생기면

- **개발자 확인 경고:** 맨 위 설치 안내와 Apple 공식 안내를 확인합니다.
- **설치 중단:** 세 앱이 모두 종료됐는지 확인합니다. 이미 더 최신 앱이 있다면 해당 버전을 유지하세요.
- **앱이 실행되지 않음:** Apple Silicon Mac인지, macOS 12 이상인지, 응용 프로그램 폴더의 새 복사본을 열었는지 확인합니다.
- **업데이트 확인 실패:** 인터넷 연결을 확인한 뒤 도움말에서 다시 확인하거나 이 페이지의 개별 ZIP을 사용합니다.

## 8. 설치 파일 확인

다운로드한 파일의 SHA-256:

```text
48a1a512fdb48ab017c5696d31402a1b3aa1d0527ab7257c53506d13869b7302
```

터미널에서 다음 명령으로 확인할 수 있습니다. 다운로드 폴더나 파일명을 바꿨다면 경로도 맞춰주세요.

```sh
shasum -a 256 ~/Downloads/Office-Studio-1.0.0-mac-arm64-Setup.pkg
```

표시된 값이 위 값과 정확히 같아야 합니다.

## 9. 이번 배포의 검증 범위

원본 ZIP과 통합 PKG의 체크섬, 포함 앱의 버전·Apple Silicon 실행 파일·앱 서명 무결성, 설치 앱 선택 항목을 확인했습니다. 최종 PKG에서 추출한 세 앱을 각각 별도 시험 프로필로 실행해 편집 화면·업데이트 메뉴·설정 저장·정상 종료를 검사하고, 포함 편집 코드가 최신 빌드와 같은지도 대조했습니다. 관리자 권한으로 기존 앱을 덮어쓰는 실제 시스템 설치와 Apple 공증은 이번 검증에 포함되지 않습니다.

이 문서는 리토스의 읽기 전용 공유 페이지입니다. 공유 호스트의 실행·네트워크 상태에 따라 연결이 중단될 수 있으며 설치 파일은 GitHub Mac 릴리스에서도 받을 수 있습니다.
