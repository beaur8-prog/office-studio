# Sheet Android 실제 창·입력·종료 검증

시험 패키지: `io.officestudio.sheet.qa`, 정식 설치본과 분리한다. Sheet 소스는 source-lock.json에 고정한다.

- 로컬 SDK: debug APK·계측 APK 빌드, Java/계측 Java 컴파일 통과.
- 웹 단위 535개 중 534개 통과, Windows 전용 1개 제외. Android UI 15개 시나리오군과 Electron 실행 검사 통과.
- 첫 CI는 SDK manager가 PATH에 없어 앱 실행 전에 실패했다. Android SDK의 명시적 경로를 사용하도록 수정했다.
- 에뮬레이터 검증: 실행 중. 실제 창의 상하단·카메라 영역, IME Enter 유지, 셀 미리보기·취소, 실제 Back→닫기→Activity 종료, 종료 후 복구를 검사한다.

공개 APK/릴리스/리토스는 변경하지 않는다. 실제 제조사 폰과 물리 키보드 검증은 포함하지 않는다.
