# Sheet Android 실제 창·입력·종료 검증

시험 패키지: `io.officestudio.sheet.qa`, 정식 설치본과 분리한다. Sheet 소스는 source-lock.json에 고정한다.

- 로컬 SDK: debug APK·계측 APK 빌드, Java/계측 Java 컴파일 통과.
- 웹 단위 535개 중 534개 통과, Windows 전용 1개 제외. Android UI 15개 시나리오군과 Electron 실행 검사 통과.
- 첫 CI는 SDK manager가 PATH에 없어 앱 실행 전에 실패했다. Android SDK의 명시적 경로를 사용하도록 수정했다.
- [최종 에뮬레이터 실행](https://github.com/beaur8-prog/office-studio/actions/runs/42448953777): **3개 모두 통과, 실패 0개**, 65.343초. Android API 35 Pixel 6·tall cutout, 별도 `.qa` 패키지에서 수행했다.
- `cameraAndSystemRowsBelongToTheRealWindowInBothOrientations`: 세로/가로 WebView의 y=0, native padding=0, 부모 창과 같은 크기, status/navigation bars 숨김 확인.
- `liveDraftEnterKeepsImeAndCancelRestores`: 실제 터치→IME 표시, 셀 초안 미리보기와 모델/이력 불변, Enter 후 다음 셀·포커스·실제 IME 유지, 초안 취소 확인.
- `backCloseFinishesActivityAndRecoverySurvives`: 실제 Back으로 종료 확인, 취소의 문서 유지, 닫기의 Activity finish, 새 Activity에서 A1=123 복구와 A2 미확정 값 미저장을 확인했다.
- 코드: Sheet `42b93a34ba7f9cf1ca81b28c443999049437b3b8`, 호스트/계측 `a3d002c`. 렌더러 및 호스트 코드 검증 후 이 문서를 기록했다.
- 첫 시작 OS 안내창/입력 포커스 준비를 기다리고, 빈 희소 셀은 optional chaining으로 조회하며, 에뮬레이터에 화면 IME를 명시적으로 켠 조건으로 최종 재검증했다. 초기 테스트 실패를 제품 검증 통과로 계산하지 않았다.

공개 APK/릴리스/리토스는 변경하지 않는다. 실제 제조사 폰과 물리 키보드 검증은 포함하지 않는다.

## 0.7.44 · 편집/뷰어·태블릿 배치

- Sheet 코드 `65cd5a51396eeb560c19e34e6ff4c207e9a02661`, 네이티브/계측 `07e1b0207d492baffaabafc64317a1fc68f365e9`. Sheet 1.0.3-qa/versionCode 4, 별도 `.qa` ID.
- [최종 API 35 Pixel 6 실행](https://github.com/beaur8-prog/office-studio/actions/runs/42452508762): **OK (4 tests)**, 100.451초. 위 창/IME/종료 복구 세 검사와 새 `viewerUsesWholeViewportAndHardwareEscapeReturnsToEditingWithoutClosing` 모두 통과.
- 실제 뷰어 격자 y=0·height=innerHeight, 실제 하드웨어 Esc 입력의 편집 복귀, 반복 Esc가 Activity를 닫지 않음과 문서·이력·dirty 불변을 검증했다. 캡처 전 계산/렌더 완료를 기다리며 입력 캡처는 A1 확정123·A2 초안456·IME가 함께 보이는 시점이다.
- 첫 [실행](https://github.com/beaur8-prog/office-studio/actions/runs/42452054040)은 세 검사가 통과했으나 첫 OS 몰입 안내창 포커스 반환 대기에서 입력 검사 준비가 실패했다. 안내창을 대기 전체에서 확인하는 시험 코드만 보강했으며, 제품 코드는 유지한 채 최종 네 검사 모두를 재실행해 통과했다.
- 웹 단위 537개 중 536개 통과/Windows 전용1개 제외. Android UI15개군, 모드·플랫폼 분리·설정 유지4개군, Electron 실행/데스크톱 편집 회귀 통과. 태블릿·DeX 배치는 브라우저 크기/입력장치 모의이며 실제 제조사 태블릿·DeX·물리 한글 키보드를 검증한 것은 아니다.
- 공개 APK·리토스·Word/Slide의 작업본과 배포 파일은 유지한다. APK를 업데이트하려면 이 브랜치의 Sheet 호스트와 고정한 렌더러를 함께 포함해야 한다.

## 0.7.45 · 전체화면 입력·직접 Esc/Back 전달

- 네이티브 기준: 최신 공개 통합 호스트 `7c3f7f8`. Sheet 전용 수정이며 Word/Slide 실행 경로는 유지한다.
- Sheet 1.0.4-qa/versionCode 5, 별도 `.qa` ID. 렌더러는 source-lock.json에 고정한 0.7.45.
- 공통 OfficeStudioHost가 없는 상태에서도 실제 Esc와 Android Back이 전체화면만 종료하는 검사, 전체화면 셀 입력·실제 IME 유지·Esc 초안 보존 검사를 포함한 5개 계측 시나리오를 사용한다.
- 로컬 Java/계측 컴파일과 전체 QA APK 빌드를 검사했다. 웹 단위 536개 통과/Windows 전용1개 제외, Android UI15개군 및 전체화면·태블릿 UI7개군, Electron/기존 데스크톱 편집 회귀 통과.
- [최종 실제 API 35 Pixel 6 실행](https://github.com/beaur8-prog/office-studio/actions/runs/42462060236): **OK (5 tests)**, 100.17초. 실패0개.
- 공통 호스트 제거 후 실제 Esc·Back의 화면 복원/Activity 미종료, 전체화면 입력·Enter의 실제 IME 유지·Esc 초안/이력 불변, 창/미리보기/종료/복구 세 가지 기존 회귀 모두 통과했다. `SheetInput: Escape via pre-ime` 로그가 실제 입력기 전 경로를 확인한다.
- [초기 실행](https://github.com/beaur8-prog/office-studio/actions/runs/42461275596)의 전체화면 터치 입력/Esc 두 실패를 확인하고 스마트폰 입력 대기와 키보드 연결 정보를 분리했다. WebView 입력기 전 Esc 처리와 화면 전환 안정화 대기를 보강한 뒤 위 다섯 검사를 재실행했다.
- 검증 코드: Sheet `8d433b8db31c1c1efd7d4ae1a3b217b8787660d3`, 네이티브/계측 `80cfbcab146278de37d09e7d4accc3be655311c6`. 이후 결과 메타데이터 기록은 제품 코드 변경이 아니다.
- 공개 배포는 하지 않는다. 실물 제조사 태블릿·DeX·물리 한글 키보드는 별도 검증 대상이다.
