# Office Studio · Android

현재 배포: Sheet 1.0.3 · Slide 1.0.4 · Word 1.0.2. Android 통합 ZIP은 제공을 중단했으며 개별 APK만 게시합니다.

## Android 1.0.7 · 변경 앱 배포

이전 공개 APK의 upstreamCommit과 현재 개발 브랜치의 실제 제품 소스를 비교했고, 이번에는 세 앱 모두 변경되어 새로 빌드했습니다. Sheet main `92afcc3`, Slide main `4d4013d`, Word의 실제 Android 개발 브랜치 `codex/android-page-viewer`의 `b75cde0`을 사용했습니다. 각 앱의 정확한 배포 커밋은 `source-lock.json`에 고정합니다.

- Sheet 1.0.3/versionCode 4: 보기·입력 설정, 화면·카메라 영역, 키보드·Esc·종료 처리를 포함합니다. 네이티브 원본 `04fc43a`를 반영했습니다.
- Slide 1.0.4/versionCode 5: 편집 리본과 문서 안에서 동작하는 Esc 처리, 기존 파일 입출력 어댑터를 포함합니다. 네이티브 원본 `0c4c536`을 반영했습니다.
- Word 1.0.2/versionCode 3: 편집 리본과 읽기 화면의 Esc→편집 전환을 포함합니다. 네이티브 원본 `d0e5ca1`을 반영했습니다.

기존 앱 ID·서명 키·아이콘을 유지합니다. 프런트엔드 빌드 후 각 변경 앱의 `:app:assemble<Id>Release`만 실행했습니다. 설치·실행·기능·에뮬레이터·브라우저 테스트는 사용자 지시에 따라 생략했으며, 빌드·서명·APK 메타데이터·체크섬 결과와 구분합니다.

`android/package-changed-apks.py`는 changedApps에 있는 앱만 새로 묶고, 나머지는 이전 공개 URL을 유지합니다. 이번 `android-v1.0.7` 공개 자산은 변경 APK 세 개, `BUILD-android.json`, `SHA256SUMS-android.txt` 다섯 개입니다. 통합 ZIP은 생성하지 않습니다. 정식 릴리스를 `--latest=false`로 공개한 뒤 `node android/publish-lithos.cjs --publish --update --skip-tests`로 기존 Android 공유 주소만 갱신합니다. Windows latest·Mac 피드, 다른 페이지와 비공개 방문 통계는 보존합니다.

세 Android 전용 앱을 각각 서명한 범용 APK로 빌드합니다. Android 10(API 29) 이상과 Android System WebView 120 이상이 필요합니다. 오프라인 프런트엔드, 시스템 파일 선택기, 사본 저장·공유, 원형·적응형·단색 아이콘을 포함합니다.

원본은 `source-lock.json`의 각 앱 커밋으로 고정합니다. 인접한 `<id>-studio` 체크아웃에서 `npm ci --ignore-scripts && npm run build`를 실행한 뒤 `node android/prepare-web.cjs`로 APK assets와 아이콘을 준비합니다. `manifests/web-assets.json`에서 포함 파일의 SHA-256을 확인합니다.

`JAVA_HOME`은 JDK 17, `ANDROID_HOME`은 SDK 36·Build Tools 36.0.0 설치 위치로 설정합니다. `OFFICE_ANDROID_KEYSTORE`와 `OFFICE_ANDROID_PASSWORD`에 **기존 정식 서명 키**를 제공하고 `cd android && ./gradlew :app:assembleRelease :app:assembleAndroidTest`를 실행합니다. 키·암호·검증 전용 APK는 Git/공개 릴리스에 올리지 않습니다. 업데이트 시에는 applicationId와 서명 키를 유지하고 versionCode와 버전을 올립니다.

`qa/run-emulator.sh`는 서명된 정식 APK를 Android 에뮬레이터에 설치해 아이콘·오프라인 시작·실제 content URI 파일 읽기·선택기 취소·사본 쓰기 완료·실패·공유·HWP·재설치·제거와 자료 보존을 검사합니다. 실제 단말의 제조사 파일 제공자, 물리 한글 키보드·S Pen·DeX·대형 문서 성능 검증을 대신하지 않습니다.

Android 릴리스는 `android-v<version>` 태그와 `--latest=false`로 게시해 기존 Windows latest와 Mac 피드를 유지합니다.

`python3 android/package-release.py`는 서명·매니페스트·아이콘 및 모든 웹 자산을 대조하고 공개 후보 APK와 비공개 검증 입력 ZIP을 준비합니다. `android-sdk/build-tools/36.0.0`이 이 저장소의 인접 디렉터리에 있어야 합니다. 검증 입력에는 별도 프로세스의 시험용 문서 제공자·공유 수신기가 들어 있습니다. **검증 ZIP과 `*-androidTest.apk`는 공개 자산에서 반드시 제외합니다.**

`python3 android/finalize-release.py --proofs <proofs-directory> --run <workflow-id>`는 성공한 휴대폰·태블릿 네이티브 결과와 브라우저 검사를 확인한 뒤 공개 ZIP·체크섬·검증 기록을 만듭니다. `python3 android/build-release-page.py`로 다운로드 문서와 릴리스 본문을 생성합니다. 공개 GitHub 릴리스에는 검증된 APK 세 개, APK 묶음 ZIP, 아이콘 PNG 세 개, `BUILD-android.json`, `ANDROID-VERIFICATION.json`, `SHA256SUMS-android.txt`만 올립니다.

`node android/publish-lithos.cjs --publish`는 공개 다운로드와 검증 기록을 확인하고 실행 중인 리토스에 **새 Android 페이지**를 만듭니다. 기존 페이지·서비스·터널을 유지하고 기존 방문 통계의 직렬 저장 큐 안에서 Android 카운터만 추가합니다. 외부에서 통계는 보이지 않습니다. 이 명령은 로컬 배포 운영자용이며 다운로드 사용자에게 필요하지 않습니다.

## Slide Studio 1.0.1 단독 갱신

이번 배포는 최신 Slide main `0dd3e1a`의 발표 화면·터치 조작 개선과 기존 Android 파일 입출력 연결을 합칩니다. 네이티브 호스트에서 SystemBars·ScreenOrientation API를 제공하고 `versionName 1.0.1`, `versionCode 2`로 빌드합니다. Sheet·Word는 공개된 1.0.0 APK를 바이트 변경 없이 ZIP에 다시 담습니다. 앱 ID와 기존 서명 키를 유지합니다.

이번 요청은 추가 테스트 없이 배포하도록 명시되어 설치·실행·기능·에뮬레이터·브라우저 테스트를 수행하지 않았습니다. APK 조립과 서명·매니페스트·자산 포함·체크섬 기록만 수행하며 `BUILD-android.json`의 테스트 상태는 `skipped`입니다. 이전 1.0.0 검사 결과와 별도로 기록합니다.

앱 소스를 잠금 커밋에 맞춘 뒤 `npm run build`, `node android/prepare-web.cjs slide`, `cd android && ./gradlew :app:assembleSlideRelease`로 Slide만 빌드합니다. `package-slide-update.py`는 인접 `baseline/`에 내려받은 기존 Sheet·Word APK, `BUILD-android-1.0.0.json` 및 운영 기록을 사용해 새 APK·ZIP·체크섬을 만듭니다. `build-slide-update-page.py`로 기존 페이지 갱신안을 만들고 공개 배포 후 `node android/publish-lithos.cjs --publish --update --skip-tests`로 같은 공유 주소를 갱신합니다. `--skip-tests`는 명시적으로 승인된 테스트 생략 기록이 있을 때만 허용합니다.

## Sheet Studio 1.0.1 단독 갱신 · ZIP 제공 중단

최신 Sheet main `89053c6`의 컴팩트 화면·연속 셀 입력을 포함하며 Android 네이티브 `setFullscreen` 상태바 제어를 연결했습니다. Sheet만 versionName 1.0.1/versionCode 2로 조립·서명합니다. 설치·실행·기능 테스트는 명시적인 요청에 따라 모두 생략합니다. 빌드는 `:app:assembleSheetRelease`만 사용합니다.

Office Android 릴리스 `android-v1.0.2`는 Sheet APK, 빌드 기록, 체크섬 세 파일만 제공합니다. 기존 Android 1.0.0·1.0.1 릴리스의 통합 APK ZIP도 삭제했습니다. Slide·Word APK, Windows·Mac 페이지와 업데이트 피드는 유지합니다. 이전 ZIP 생성 스크립트는 과거 배포 기록이며 현재 배포에서는 실행하지 않습니다.

## Slide Studio 1.0.2 빠른 배포

최신 앱 main `1ee351b`의 발표 확대·이름 있는 터치 도구·안전 여백 CSS와 호스트 브랜치 `584be72`의 창·카메라 영역 수정을 함께 포함합니다. Sheet의 상태바 API는 유지하고 Slide만 versionName 1.0.2/versionCode 3으로 빌드합니다. 기존 앱 ID와 서명 키를 재사용합니다.

SDK·Gradle·npm 캐시는 `/private/tmp/office-studio-android-build-cache`를 재사용하며 `:app:assembleSlideRelease`만 실행합니다. 설치·실행·브라우저·에뮬레이터 테스트와 ZIP 생성은 생략합니다. `android-v1.0.3`에는 Slide APK·빌드 기록·체크섬 세 파일을 게시하고 기존 공유 주소를 갱신합니다. Sheet 1.0.1과 Word 1.0.0은 기존 파일을 유지합니다.

## Sheet Studio 1.0.2 빠른 배포

최신 Sheet main `cd47544`의 전체화면 격자·여백 개선을 포함합니다. 호스트는 Sheet의 edgeToEdge 요청과 상태줄·탐색줄 숨김을 처리하고 systemBars/displayCutout 스냅샷을 CSS px 이벤트로 전달합니다. 부모의 중복 여백은 제거하고 키보드 여백은 유지합니다. 다른 플랫폼과 앱의 공개 APK는 변경하지 않습니다.

Sheet만 versionName 1.0.2/versionCode 3으로 빌드합니다. 기존 키와 앱 ID를 유지하고 빌드 캐시를 재사용합니다. 설치·실행·기능 테스트와 통합 ZIP 생성은 생략합니다. `android-v1.0.4`에는 Sheet APK·빌드 기록·체크섬 세 파일을 게시합니다.

## Slide Studio 1.0.3 빠른 배포

최신 앱 main `80e04b9`의 뷰어 편집 바로가기와 호스트 `8d121c3`의 시작·뷰어·편집 화면 상태바 숨김을 포함합니다. 기존 Sheet 전체화면·IME·inset 연결을 병합 과정에서 보존했습니다. Slide만 versionName 1.0.3/versionCode 4로 빌드하며 설치·실행·기능 테스트와 ZIP 생성은 생략합니다.

`android/package-single-apk.py`는 source-lock의 changedApps 한 개와 현재 버전, 인접 records의 이전 빌드·페이지·캐시·변경 설명을 읽어 APK·빌드 기록·체크섬 세 파일과 문서를 준비합니다. `android-v1.0.5`로 게시하고 기존 공유 페이지를 갱신합니다.

## Word Studio 1.0.1 빠른 배포

실제 개발 원본과 원격 Android 브랜치의 일치 커밋 `ccba472`를 확인했습니다. 문서·편집 페이지 맞춤과 전체화면 스와이프 뷰어 개선을 포함하며 호스트 `8e56284`의 Word 전체화면·카메라 여백·포커스 복귀를 연결했습니다. 기존 Sheet·Slide 호스트 정책과 APK는 유지합니다.

Word만 versionName 1.0.1/versionCode 2로 빌드하고 기존 앱 ID·서명 키를 사용합니다. 빌드 캐시를 재사용하며 설치·실행·기능 테스트와 통합 ZIP 생성은 생략합니다. `android-v1.0.6`에는 Word APK·빌드 기록·체크섬 세 파일을 게시하고 기존 Android 공유 주소를 갱신합니다.
