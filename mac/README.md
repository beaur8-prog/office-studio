# Office Studio · Mac Apple Silicon

[리토스 Mac 배포 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ef9c9402c857485a69bb9e8e0c441f57e1025bc8dd9d002) · [Mac 릴리스](https://github.com/beaur8-prog/office-studio/releases/tag/mac-v1.0.1)

Windows 배포와 별도로 관리하는 Apple Silicon(M1 이상) · macOS 12 이상용 통합 PKG입니다. `mac/suite-lock.json`은 Mac 입력만 고정하고, 기존 루트 `suite-lock.json`과 Windows 릴리스는 유지합니다.

```sh
npm run fetch:mac
npm test
npm run package:mac
npm run test:mac
node mac/build-release-note.cjs
```

Mac 빌드는 Apple Silicon Mac과 기본 macOS 개발 도구(pkgbuild, productbuild, pkgutil, codesign)가 필요합니다. 실행 검증은 별도 프로필을 사용하며, 실제 `/Applications` 설치와 기존 사용자 문서 변경을 수행하지 않습니다. 실행 검증에는 인접한 앱 체크아웃의 Playwright 의존성이 필요합니다.

출력은 `release/mac/<version>/`에 저장합니다. 패키지는 관리자 인증을 받아 `/Applications`에 설치하며 사용자화에서 세 앱을 선택합니다. 설치 전 실행 중인 Studio 앱과 더 최신 버전의 기존 앱을 확인해 중단합니다. 기존 문서·설정 폴더는 패키지에 포함하지 않습니다.

앱은 ad-hoc 서명이며 Developer ID 서명·공증은 없습니다. 이 제한과 시스템 설정의 앱별 열기 허용 방법은 배포 문서 첫머리에 안내합니다.

## 플랫폼별 릴리스

- Windows와 Mac을 같은 앱 버전으로 배포할 때는 두 플랫폼 자산을 같은 정식 릴리스에 올리고 Windows latest로 지정합니다.
- 앱별 Mac 전용 릴리스는 각 저장소의 정식 `v<app-version>` 태그에 게시하되 **GitHub latest를 변경하지 않도록 `--latest=false`**를 지정합니다. 자산은 해당 버전에 고정한 파일명·URL·SHA-256을 사용합니다.
- Mac 앱은 `releases?per_page=20`에서 해당 아키텍처의 ZIP이 있는 가장 높은 정식 버전을 확인합니다. 새 Mac 버전이 최근 20개 릴리스에 포함되도록 관리합니다.
- Windows 앱은 기존 `/releases/latest` 경로를 계속 사용합니다. Mac 전용 릴리스로 Windows latest를 바꾸면 기존 Windows 앱의 업데이트 확인이 실패하므로 반드시 유지하세요.
- Office Studio Mac 통합본은 같은 저장소의 별도 **`mac-v<version>`** 릴리스에 `--latest=false`로 게시합니다. Windows `v<version>` 자산을 교체하지 않습니다.
- Mac은 새 버전 자동 확인·다운로드 후 ZIP 직접 교체 방식입니다. 문서 저장·앱 종료·교체는 사용자가 수행하며 도움말에서 자동 다운로드를 끌 수 있습니다.

`publication/mac/downloads-ready.md`는 별도 리토스 페이지의 본문이며, `publication/mac/PUBLISHED.json`에 공유 링크와 배포 체크섬을 기록합니다. 공개 후 `node mac/verify-publication.cjs`로 본문·다운로드 링크·Mac 업데이트와 기존 Windows 업데이트 경로를 검증합니다.
