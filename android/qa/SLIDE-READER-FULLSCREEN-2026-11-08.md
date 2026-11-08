# Slide Android 일반 화면 상태바·편집 바로가기

기준: 배포된 Slide 1.0.2 / Office Studio `8146a96`. 이번 개발 브랜치: 앱·호스트 각각 `codex/android-reader-fullscreen-20261108`.

## 확인한 원인

1.0.2에도 이전 네이티브 여백 수정은 포함되어 있다. 그러나 `setSlideImmersive(false)`가 상단 상태바까지 다시 표시하므로 발표 이외의 화면은 상단 영역을 쓸 수 없었다. `systemBars()` 전체 높이를 CSS 안전 여백으로 전달해 일시적으로 상태바를 펼칠 때 빈 상단 행도 다시 생길 수 있었다.

Slide는 시작·뷰어·편집·발표 모두 상단 상태바를 숨긴다. 일반 화면은 하단 탐색바를 표시하고 발표 중에는 숨긴다. 포커스 복귀 시 현재 모드로 재적용한다. 네이티브 CSS 안전 여백은 카메라·창 제목줄·하단 탐색 영역을 사용하고 일시적 상태바 높이는 제외한다. 키보드의 부모 여백과 Sheet의 별도 상태바 제어는 유지한다. 뷰어 상단 `편집` 버튼은 기존 문서/복구 처리 경로를 재사용한다.

## 검증 결과

- Android 보기·편집·발표·플랫폼 격리 브라우저 검사 32개 전부 통과.
- 360px 휴대폰·가로 휴대폰·태블릿에서 편집 버튼 터치 크기와 화면 범위, 현재 슬라이드 유지, 보기 복귀, 수정 상태 없음 확인. 휴대폰 화면 캡처도 확인.
- main/Android 전용 소스 브랜치 웹 빌드 통과.
- JDK 17·SDK 36·기존 AndroidX 의존성으로 MainActivity와 네이티브 검사 Java 소스 직접 컴파일 통과.
- 실제 Gradle 앱 설정에서 `:app:compileSlideReleaseJavaWithJavac :app:compileSlideReleaseAndroidTestJavaWithJavac` **BUILD SUCCESSFUL**, 31개 작업(15개 실행·16개 기존 결과 사용). 먼저 오프라인 시도에서는 미캐시 테스트 의존성이 없어 실패했다. 기존 의존성을 내려받고 재실행해 통과했다.
- `SlideWindowInstrumentedTest`에 창 상태바/탐색바 가시성, 발표 종료·포커스 복귀, 일시적 상태바·카메라·키보드 inset을 검사하는 두 시나리오를 추가했다. 기존 identity 검사의 고정 1.0.0 기대값을 현재 BuildConfig 버전으로 수정했다.

네이티브 검사는 컴파일만 확인했으며 Android 실기기/에뮬레이터에서 실행하지 않았다. 새 APK 조립·서명·설치·공개 배포는 하지 않았다. 설치된 1.0.2에는 이번 후속 수정이 아직 없다. 다음 APK에 앱 자산과 호스트를 함께 포함해야 한다. 현재 공개 릴리스·다운로드 문서·버전은 그대로 유지한다.

원시 로그/캡처: Slide main의 `qa/results/android-reader-fullscreen-20261108/` (Git 제외). Android 구현 근거: [공식 시스템 바 숨김 API](https://developer.android.com/develop/ui/views/layout/immersive).
