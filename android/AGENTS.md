# Android 배포 원칙

Android APK는 로컬에서 빌드·서명한 뒤 기존 리토스 페이지에 직접 첨부한다. Android GitHub 릴리스 생성, APK 업로드·재다운로드는 배포 단계에 넣지 않는다. 사용자가 명시적으로 방식을 바꾼 경우에만 예외로 한다.

소스의 Git pull·commit·push와 버전 태그 관리는 유지한다. 현재 배포는 `package-changed-apks.py`와 `publish-lithos.cjs`를 사용한다. 구 GitHub 배포 스크립트는 과거 기록이다. Windows·Mac 릴리스와 업데이트 피드는 그대로 보존한다. 테스트 범위는 사용자 요청을 따른다.
