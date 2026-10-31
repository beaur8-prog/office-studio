# Office Studio 배포

[리토스 배포 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/b448bde7b88792d8776c7f6a908e88891c473d4b5f6cace2) · [Windows 설치 파일](https://github.com/beaur8-prog/office-studio/releases/download/v1.0.5/Office-Studio-1.0.5-win-x64-Setup.exe)

[Mac 리토스 배포 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ef9c9402c857485a69bb9e8e0c441f57e1025bc8dd9d002) · [Mac Apple Silicon 설치 파일](https://github.com/beaur8-prog/office-studio/releases/download/mac-v1.0.5/Office-Studio-1.0.5-mac-arm64-Setup.pkg) · [Mac 배포 안내](publication/mac/downloads-ready.md)

Sheet Studio, Slide Studio, Word Studio를 묶어 설치하는 배포 프로젝트입니다. 기존 Windows x64 통합본과 별도 Mac Apple Silicon 통합본을 관리합니다. 아래 내용과 루트 잠금 파일은 Windows 배포용이며, Mac 배포 절차와 잠금 파일은 [mac/README.md](mac/README.md)에 있습니다.

| 구성 | 포함 버전 |
| --- | --- |
| Office Studio | 1.0.5 |
| Sheet Studio | 0.7.36 |
| Slide Studio | 1.6.21 |
| Word Studio | 1.4.11 |

## 설치 파일

결과물은 `release/1.0.5/`에 저장합니다.

- `Office-Studio-1.0.5-win-x64-Setup.exe`: Intel/AMD Windows 10·11 x64. 한 마법사에서 앱을 선택하고 현재 사용자 계정에 설치합니다. 세 앱 모두 기본 선택입니다. 시작 메뉴와 Windows의 앱별 제거 항목은 기존 설치 방식대로 생성됩니다.

먼저 문서를 저장하고 세 앱을 종료하세요. 설치 프로그램은 실행 중인 Studio 앱을 확인하고 종료를 안내합니다. Windows에서는 원래의 앱별 설치 파일을 순서대로 실행합니다. 중간에 실패하면 설치가 완료된 앱은 유지하고 오류를 표시하므로, 원인을 해결한 뒤 다시 실행하세요. 통합 설치 파일에는 앱별 제거 프로그램이 포함되며, 별도의 Office Studio 제거 항목은 생성하지 않습니다.

설치 파일은 오프라인으로 세 앱을 설치할 수 있습니다. Windows Setup은 배포용 코드 서명을 받지 않아 운영체제 보안 경고가 나타날 수 있습니다.

## 앱 업데이트

세 앱 소스에 **도움말 → 업데이트 확인…**과 **업데이트 자동 확인·다운로드**를 추가했습니다. Windows에서는 Alt 키로 숨겨진 메뉴를 표시할 수 있습니다. 설치된 앱은 실행 15초 뒤와 이후 6시간마다 해당 앱의 공개 GitHub 최신 정식 릴리스를 확인합니다. 새 버전은 백그라운드에서 내려받고 알림을 표시합니다. 자동 확인·다운로드 설정은 앱별로 기억하며 끌 수 있습니다. 개발 실행에서는 자동 확인하지 않습니다.

- Windows 설치형 앱: 알림이나 도움말 메뉴에서 **종료하고 업데이트**를 선택하면 기존 문서 저장·종료 절차를 거칩니다. 종료가 완료된 뒤에만 별도 설치 도우미가 설치 파일을 실행하고 앱을 다시 엽니다. 문서 종료 취소나 자동 복구 저장 실패·시간 초과 시 설치 요청도 취소됩니다. 폴더에서 직접 실행하는 휴대용 앱은 설치 파일 위치를 안내합니다.

업데이트 파일은 해당 앱의 고정된 GitHub 저장소·태그·기기별 파일 이름만 허용합니다. GitHub가 제공하는 SHA-256과 크기를 다운로드·실행 전 다시 검사하고 이전 버전·시험판·출처가 다른 파일을 거부합니다. 설치 요청 시에는 최신 릴리스도 다시 확인합니다. 확인·다운로드 실패가 문서 편집을 중단시키지는 않습니다.

공통 기준 구현은 `updater/`입니다. 앱별 업데이트 화면과 설치 진행 방식은 각 앱 소스에서 관리하며, 이번 배포에 포함한 정확한 버전·커밋·업데이터 체크섬은 `updater/app-sources.json`에 고정합니다. 공통 파일을 `npm run updater:sync`로 옮길 때는 앱별 변경을 먼저 검토하고 병합한 뒤 각 앱 저장소에 커밋하세요. 기존 패키징 스크립트가 `electron/` 전체를 ASAR에 포함하므로 추가 런타임 의존성은 필요하지 않습니다. 이 프로젝트의 `npm test`로 업데이트 무결성·종료 취소·플랫폼별 동작을 검증합니다.

**Office Studio 1.0.5에는 업데이트 코드가 포함된 세 Windows 앱을 묶습니다.** 업데이트 기능이 없거나 “종료하고 업데이트”에서 앱만 닫히는 이전 버전을 사용한다면 이 통합본을 한 번 직접 설치해야 하며, 이후의 새 버전을 앱의 업데이트 화면에서 받을 수 있습니다. 앱별 버전은 독립적으로 갱신됩니다.

## 다시 빌드하기

Node.js 22.12 이상을 사용합니다. 이 배포 프로젝트 자체에는 npm 의존성이 없습니다.

```sh
cd studio-suite
npm test
npm run fetch:win
npm run package:win
```

Windows Setup 빌드에는 NSIS와 7-Zip이 필요합니다. 도구가 PATH에 없으면 `MAKENSIS`와 `SEVENZIP`에 실행 파일 경로를 지정합니다. 이 Windows 빌드 경로는 `win-x64`만 허용합니다. Mac 빌드는 별도의 `npm run package:mac`을 사용합니다.

`fetch:win`은 `suite-lock.json`에 고정한 GitHub 태그에서 Windows 설치 파일을 받아 `inputs/`에 저장합니다. 파일 크기·SHA-256이 맞는 파일만 사용하고, 이미 저장된 파일의 체크섬이 다르면 중단합니다. 로컬 빌드의 `inputs/`는 검증된 앱별 설치 파일을 보관하며 Git에는 포함하지 않습니다. 새 환경에서는 `fetch:win`을 사용하면 됩니다. 통합 릴리즈 잠금 파일에는 Windows 자산만 등록합니다.

새 배포에서는 `suite-lock.json`의 통합 버전과 앱 버전·태그별 URL·파일 크기·SHA-256을 함께 갱신하고, `package.json` 버전도 맞춥니다. 체크섬은 해당 앱의 검증된 배포 기록과 대조하세요. `latest` URL은 빌드 입력으로 허용하지 않습니다. 한 번 배포한 통합 버전의 구성 파일을 바꾸지 말고 새 통합 버전을 발급합니다.

각 결과물에는 SHA-256 파일과 `BUILD-<target>.json`을 생성합니다. 통합본에 포함한 앱 버전, 원본 다운로드 주소, 원본 및 통합본 체크섬, 검증 범위를 함께 기록합니다. 빌드 명령은 로컬 결과물을 만들며, 공개 업로드는 배포 대상 저장소를 정한 뒤 수행합니다.

## 검증

`npm test`는 입력 변조·잘못된 버전 및 URL·NSIS 문자열 처리·설치 실패 처리와 Windows 전용 릴리즈 범위를 확인합니다. 앱 업데이트의 무결성과 문서 종료 취소 처리도 확인합니다. Windows 빌드는 생성한 Setup을 다시 풀어 세 설치 파일의 SHA-256을 원본과 비교합니다.

이 검증은 실제 시스템 설치·앱 실행 시험과 구분합니다. 빌드 보고서의 `nativeInstall: false`는 해당 통합본의 실제 설치 시험이 아직 남아 있음을 뜻합니다. 격리된 Windows CI에서 `scripts/test-windows.ps1`로 전체 설치·재설치·제거와 문서 보존을 확인한 뒤 Windows 통합본만 공개합니다.

Windows 검증은 `previous-apps-lock.json`에 고정한 이전 앱에서 실제 업데이트 도우미로 새 앱을 설치하고, 새 창 실행·정상 종료·업데이트 모듈 포함 여부도 확인합니다. 결과와 정확한 설치 파일 SHA-256은 Actions 검증 기록으로 보관합니다.

공개 다운로드는 [Office Studio 릴리스](https://github.com/beaur8-prog/office-studio/releases)에서 제공합니다. 리토스 배포 문서 본문은 `publication/downloads-ready.md`에 보관하며 `node scripts/build-release-note.cjs`로 버전·파일 크기·체크섬을 반영합니다.
