# Office Studio · Android

세 Android 전용 앱을 각각 서명한 범용 APK로 빌드합니다. Android 10(API 29) 이상과 Android System WebView 120 이상이 필요합니다. 오프라인 프런트엔드, 시스템 파일 선택기, 사본 저장·공유, 원형·적응형·단색 아이콘을 포함합니다.

원본은 `source-lock.json`의 각 앱 커밋으로 고정합니다. 인접한 `<id>-studio` 체크아웃에서 `npm ci --ignore-scripts && npm run build`를 실행한 뒤 `node android/prepare-web.cjs`로 APK assets와 아이콘을 준비합니다. `manifests/web-assets.json`에서 포함 파일의 SHA-256을 확인합니다.

`JAVA_HOME`은 JDK 17, `ANDROID_HOME`은 SDK 36·Build Tools 36.0.0 설치 위치로 설정합니다. `OFFICE_ANDROID_KEYSTORE`와 `OFFICE_ANDROID_PASSWORD`에 **기존 정식 서명 키**를 제공하고 `cd android && ./gradlew :app:assembleRelease :app:assembleAndroidTest`를 실행합니다. 키·암호·검증 전용 APK는 Git/공개 릴리스에 올리지 않습니다. 업데이트 시에는 applicationId와 서명 키를 유지하고 versionCode와 버전을 올립니다.

`qa/run-emulator.sh`는 서명된 정식 APK를 Android 에뮬레이터에 설치해 아이콘·오프라인 시작·실제 content URI 파일 읽기·선택기 취소·사본 쓰기 완료·실패·공유·HWP·재설치·제거와 자료 보존을 검사합니다. 실제 단말의 제조사 파일 제공자, 물리 한글 키보드·S Pen·DeX·대형 문서 성능 검증을 대신하지 않습니다.

Android 릴리스는 `android-v<version>` 태그와 `--latest=false`로 게시해 기존 Windows latest와 Mac 피드를 유지합니다.

`python3 android/package-release.py`는 서명·매니페스트·아이콘 및 모든 웹 자산을 대조하고 공개 후보 APK와 비공개 검증 입력 ZIP을 준비합니다. `android-sdk/build-tools/36.0.0`이 이 저장소의 인접 디렉터리에 있어야 합니다. 검증 입력에는 별도 프로세스의 시험용 문서 제공자·공유 수신기가 들어 있습니다. **검증 ZIP과 `*-androidTest.apk`는 공개 자산에서 반드시 제외합니다.**

`python3 android/finalize-release.py --proofs <proofs-directory> --run <workflow-id>`는 성공한 휴대폰·태블릿 네이티브 결과와 브라우저 검사를 확인한 뒤 공개 ZIP·체크섬·검증 기록을 만듭니다. `python3 android/build-release-page.py`로 다운로드 문서와 릴리스 본문을 생성합니다. 공개 GitHub 릴리스에는 검증된 APK 세 개, APK 묶음 ZIP, 아이콘 PNG 세 개, `BUILD-android.json`, `ANDROID-VERIFICATION.json`, `SHA256SUMS-android.txt`만 올립니다.

`node android/publish-lithos.cjs --publish`는 공개 다운로드와 검증 기록을 확인하고 실행 중인 리토스에 **새 Android 페이지**를 만듭니다. 기존 페이지·서비스·터널을 유지하고 기존 방문 통계의 직렬 저장 큐 안에서 Android 카운터만 추가합니다. 외부에서 통계는 보이지 않습니다. 이 명령은 로컬 배포 운영자용이며 다운로드 사용자에게 필요하지 않습니다.
