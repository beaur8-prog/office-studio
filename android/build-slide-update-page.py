"""Update only Slide downloads and mark this build as not application-tested."""
from pathlib import Path
import json,re
s=Path(__file__).resolve().parent.parent;r=s.parent
build=json.loads((s/'release/android/1.0.1/BUILD-android.json').read_text());assert build['tests']['executed'] is False
slide=next(a for a in build['apps'] if a['id']=='slide');before=json.loads((r/'proofs/pages-before.json').read_text())['android'];body=before['content']
body=re.sub(r'\*\*Office Studio Android 1\.0\.0[^\n]*\*\*','**Office Studio Android 1.0.1 · 2026년 11월 8일 · Slide Studio 업데이트**\n\nSlide Studio만 **1.0.1**로 갱신했습니다. Sheet Studio와 Word Studio는 기존 **1.0.0** 파일을 그대로 제공합니다.',body,count=1)
body=body.replace('**Slide Studio 1.0.0**','**Slide Studio 1.0.1**')
body=body.replace('android-v1.0.0/Slide-Studio-1.0.0-Android.apk','android-v1.0.1/Slide-Studio-1.0.1-Android.apk')
body=body.replace('android-v1.0.0/Office-Studio-1.0.0-Android-APKs.zip','android-v1.0.1/Office-Studio-1.0.1-Android-APKs.zip')
for file in ['SHA256SUMS-android.txt','BUILD-android.json']:body=body.replace('android-v1.0.0/'+file,'android-v1.0.1/'+file)
body=body.replace('[Android 검증 기록]','[1.0.0 배포 당시 검증 기록]').replace('releases/tag/android-v1.0.0','releases/tag/android-v1.0.1')
body=body.replace('**Slide Studio:** PPTX 보기·슬라이드 이동·검색·발표와 기본 터치 편집.','**Slide Studio:** PPTX 보기·슬라이드 이동·검색·발표와 기본 터치 편집. 1.0.1에는 화면 전체의 가용 영역을 사용하는 발표 화면, 좌우 스와이프 이동과 탭으로 여닫는 도구 모음을 포함합니다.')
body=body.replace('1601ed4743eab9d2619376fbfa35fc57ba2ec47c1617026b6f2087a7265a7562  Slide-Studio-1.0.0-Android.apk',slide['sha256']+'  '+slide['filename'])
start=body.index('## 7. 이번 배포의 검증 범위');end=body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.',start)
body=body[:start]+"""## 7. 이번 배포의 확인 범위

**Slide Studio 1.0.1은 추가 설치·실행·문서 기능 테스트를 생략하고 배포했습니다.** 빌드를 완료하고 APK 서명·앱 ID·버전, 포함 자산과 아이콘 리소스, 파일 크기와 SHA-256을 확인했습니다. 기존 앱 위에 설치할 수 있도록 앱 ID와 배포 서명 키를 유지하고 versionCode를 2로 올렸습니다.

Sheet Studio·Word Studio 1.0.0은 이전 배포와 동일한 파일입니다. 위의 1.0.0 검증 기록은 이전 배포 당시의 에뮬레이터 검사 결과입니다. 실제 단말의 제조사별 파일 제공자·화면 회전·시스템 바·물리 한글 키보드·S Pen·DeX·대형 문서 성능은 별도 확인이 필요합니다.

"""+body[end:]
assert slide['downloadURL'] in body and 'Slide-Studio-1.0.0-Android.apk' not in body
out=s/'publication/android';out.mkdir(parents=True,exist_ok=True);(out/'downloads-ready.md').write_text(body)
previous={'version':'1.0.0','title':before['title'],'url':before['url'],'source':'앱/오피스 스튜디오 Android 다운로드.md','contentSHA256':before['contentSHA256'],'readOnly':True,'expiresAt':before['expiresAt']};(out/'PUBLISHED.json').write_text(json.dumps(previous,ensure_ascii=False,indent=2)+'\n')
notes="""Office Studio Android 1.0.1 — Slide Studio 갱신

Slide Studio 1.0.1 APK만 새로 빌드했습니다. 최신 main의 전체 화면 발표·터치 탐색 개선과 기존 파일 열기·사본 저장·공유 연결을 포함합니다. Android 호스트의 시스템 바·방향 API를 연결하고 기존 앱 ID·서명 키를 유지하며 versionCode를 2로 올렸습니다.

Sheet Studio와 Word Studio는 1.0.0 파일을 유지합니다. 묶음 ZIP에는 새 Slide APK와 기존 두 APK를 담았습니다. 이번 Slide 버전은 설치·실행·기능 테스트를 생략하고 공개합니다. BUILD-android.json에 빌드·서명·체크섬과 테스트 미실행 상태를 기록했습니다.

[Android 다운로드 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ee660ab4fe42ff63c8aa9495bab1f1bfcdedc214e038c2a)
""";(out/'release-notes.md').write_text(notes)
print('Prepared Slide 1.0.1 page and bundle links; installation/function tests explicitly marked skipped')
