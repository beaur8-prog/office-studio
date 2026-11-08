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
