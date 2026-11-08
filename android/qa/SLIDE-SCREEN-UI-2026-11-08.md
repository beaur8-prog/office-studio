# Slide Android 화면 공간·확대·화면 맞춤 개선

기준: `codex/android-slide-1.0.1`의 `06eaa35`. 앱과 호스트의 개선 소스는 각각 `codex/android-screen-ui-20261108` 브랜치에 보관한다. 공개 APK·리토스 페이지는 갱신하지 않는다.

## 확인한 원인과 수정

기존 `MainActivity`는 발표 중에도 `Type.displayCutout()`을 부모 padding에 적용했다. 가로 화면에서 카메라 구멍이 왼쪽으로 가면 WebView 전체가 오른쪽으로 밀리고 원래의 밝은 창 배경이 남을 수 있다. 전체 화면 DOM이나 CSS만 바꿔서는 부모의 이 여백을 제거할 수 없다.

Slide 창은 cutout을 허용하고 시스템 바를 투명하게 설정한다. Slide 전용 부모는 키보드 높이만 padding으로 유지하며 시스템/카메라 여백은 `--studio-safe-area-inset-*` CSS 변수로 전달한다. 수치는 실제 화면 픽셀을 density로 나눈 CSS px다. 앱의 버튼은 이 여백을 사용하고, 발표 캔버스는 이를 padding으로 적용하지 않는다. 발표 진입·종료·포커스 복귀 시 시스템 바와 검정/일반 배경을 전환한다. Sheet/Word의 부모 padding 처리는 그대로 유지한다.

앱에는 100~500% 핀치 확대, 확대 상태의 한 손가락 이동, 두 번 탭 확대/맞춤, 확대·축소·화면 맞춤 버튼과 이름을 적용했다. 화면 맞춤 상태에서만 스와이프로 장을 넘긴다. 조회 변화는 문서 수정으로 기록하지 않는다.

## 검증과 한계

- Android UI·슬라이드 쇼·호스트 연결을 모사하는 Chrome 터치 검사 31개 통과(실패 0).
- 기존 main 전체 발표 회귀 검사 54개 중 53개 통과, Windows 전용 1개 건너뜀. 위 31개 검사와 중복되므로 합산하지 않는다.
- main과 APK 전용 파일 입출력 코드가 병합된 Slide 브랜치의 TypeScript·프로덕션 빌드 통과.
- MainActivity 및 전체 androidTest Java 소스가 JDK 17, 공식 SDK Platform 36(android.jar), AndroidX core 1.13.1·webkit 1.14.0·기존 테스트 의존성으로 javac 컴파일 통과. Gradle APK 조립/설치 검증을 의미하지 않는다.
- 네이티브 회귀 검사 `slideWindowFillsCameraAreaAndRestoresAfterPresentation`을 추가했다. cutout/system-bar 여백이 부모를 축소하지 않는지, 발표 배경, cutout mode, 키보드 여백·종료 복원을 검사한다. 이 검사는 컴파일만 확인했으며 Android에서 실행하지 않았다. 기존 identity 검사의 고정 버전 기대값도 현재 flavor의 BuildConfig로 바꿨다.
- 웹 검사에서 28/40/24/12px 안전 여백을 제공해도 발표 캔버스가 전체 960×450을 사용하고, 버튼만 안전 여백을 피하며 종료 후 일반 보기의 padding이 복원됨을 확인했다.

Android 실기기/에뮬레이터 실행, 제조사 cutout, 물리 화면 회전·상태바·탐색바·키보드 검증은 남아 있다. 실제 사용자 흰 띠의 스크린샷은 없어 동일 원인이라고 단정하지 않는다. APK 조립·서명·배포는 별도 요청 시 진행한다. 설치된 1.0.1 앱은 아직 이 변경을 포함하지 않는다.

로컬 원시 검증 로그: Slide main 체크아웃의 `qa/results/android-zoom-20261108/` 및 `qa/results/native-compile/` (Git 제외).

Android 정책 참고: [cutout 처리](https://developer.android.com/develop/ui/views/layout/display-cutout), [edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
