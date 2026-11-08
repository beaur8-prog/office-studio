# Office Studio Android · 로컬 첨부 배포

현재 앱: Sheet 1.0.4 · Slide 1.0.5 · Word 1.0.3. 기존 Android 공유 주소를 유지합니다.

Android는 **로컬에서 빌드·서명 → 파일·버전·서명·체크섬 확인 → 리토스 첨부 게시**로 배포합니다. GitHub 릴리스 생성, APK 업로드, APK 재다운로드는 하지 않습니다. 소스의 Git pull·commit·push와 버전 태그 관리는 그대로 사용합니다. `distribution-policy.json`으로 이 방식을 고정합니다.

## 배포 순서

1. 사용자 작업본을 보존하고 최신 개발 Git을 별도 복제본으로 가져옵니다. `source-lock.json`에 포함 커밋을 고정하고 변경 앱의 versionName/versionCode를 올립니다.
2. 변경 앱의 프런트엔드를 빌드하고 APK 자산을 준비합니다. JDK 17·SDK 36·Build Tools 36.0.0과 기존 정식 서명 키를 사용해 해당 앱의 `:app:assemble<Id>Release`만 실행합니다. 앱 ID와 서명 키를 유지합니다.
3. `python3 android/package-changed-apks.py`로 APK·BUILD·SHA256SUMS와 문서를 준비합니다. 인접 `records`의 이전 빌드·페이지·캐시 기록을 사용합니다. 공개 APK URL은 같은 리토스 페이지의 첨부 주소로 기록합니다.
4. `npm run publish:android -- --skip-tests`로 기존 페이지에 바로 첨부합니다. 테스트를 생략하는 이번 사용자 방침은 BUILD의 명시적 기록으로 확인하며 앱 빌드·서명·해시 검사와 구분합니다.
5. 실제 공개 다운로드의 파일명·전체 바이트·SHA-256을 확인하고, 영구 첨부와 필요한 기록을 남긴 뒤 임시 빌드·복제본을 정리합니다.

## 로컬 파일 선택과 보존

`direct-downloads.cjs`는 먼저 `release/android/<배포버전>/<APK명>`을 사용하고, 변경 없는 앱은 Lithos Vault의 기존 첨부를 사용합니다. 필요하면 `ANDROID_APK_INPUT_DIR`로 로컬 입력 폴더를 지정합니다. BUILD의 크기·SHA-256과 일치하는 파일만 허용하며 파일이 없으면 중단합니다. 네트워크에서 APK를 받는 대체 경로는 없습니다.

아이콘과 과거 1.0.0 검증 기록은 `distribution-assets/`의 로컬 파일을 사용합니다. BUILD·SHA256SUMS도 로컬 빌드 결과를 그대로 첨부합니다. `DOWNLOADS.json`에는 공개 첨부 주소와 체크섬을 기록합니다.

현재 배포 파일은 Lithos Vault의 `앱/attachments/office-studio-android/`에 보관하고 공유 서버의 독립된 다운로드 스냅샷에서도 보존합니다. 이 파일들은 임시 파일이 아닙니다. 페이지는 파일 첨부 다운로드 헤더와 Range 이어받기를 제공합니다. 페이지 주소·링크 복사 버튼·비공개 방문 통계·서버·터널을 유지합니다.

Android는 현재 새 APK를 기존 앱 위에 직접 설치해 업데이트합니다. 자동 업데이트와 통합 ZIP은 제공하지 않습니다. Windows latest·Mac 릴리스와 자동 업데이트 피드는 별개로 유지합니다.

구 GitHub 기반 스크립트와 [과거 배포 기록](HISTORICAL-RELEASES.md)은 이력이며 현재 Android 배포에서 실행하지 않습니다. 기존 GitHub 공개 자산은 이번 정책 변경으로 삭제하거나 바꾸지 않습니다.
