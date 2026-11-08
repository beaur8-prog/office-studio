"""Package one Android APK and refresh the existing page, without tests or ZIP."""
from pathlib import Path
import json,hashlib,os,subprocess,shutil,datetime,re
s=Path(__file__).resolve().parent.parent;r=s.parent;lock=json.loads((s/'android/source-lock.json').read_text());out=s/'release/android/1.0.3';out.mkdir(parents=True,exist_ok=True);cache=json.loads((r/'records/build-cache.json').read_text());sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
apk=s/'android/app/build/outputs/apk/slide/release/app-slide-release.apk';sdk=Path(cache['androidSDK'])/'build-tools/36.0.0';env=os.environ.copy();env['JAVA_HOME']=cache['javaHome']
cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],env=env,text=True);assert 'certificate SHA-256 digest: c166b002177773ea993d118545bec3ecdfd2a3f2f286cb7ac850ca646a7a9d3b' in cert
manifest=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True);assert "name='io.officestudio.slide'" in manifest and "versionName='1.0.2'" in manifest and "versionCode='3'" in manifest
name='Slide-Studio-1.0.2-Android.apk';file=out/name;shutil.copy2(apk,file);prior=json.loads((r/'records/previous-build.json').read_text());apps=[]
for a in prior['apps']:
 a={**a,'rebuilt':a['id']=='slide'}
 if a['id']=='slide':a.update(version='1.0.2',versionCode=3,filename=name,sha256=sha(file),bytes=file.stat().st_size,sourceCommit=lock['apps']['slide']['sourceCommit'],upstreamCommit=lock['apps']['slide']['upstreamCommit'],downloadURL='https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.3/'+name,offlineAssetsVerified=None)
 apps.append(a)
build={'schema':1,'product':'Office Studio','version':'1.0.3','target':'android','releaseType':'slide-only','changedApps':['slide'],'sourceCommit':'3abbe13bd67561a52a57a1bd70387ff0ccefb812','nativeSlideHostUpstreamCommit':lock['nativeSlideHostUpstreamCommit'],'builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'apps':apps,'combinedZIP':False,'tests':{'status':'skipped','executed':False,'explicitlyAuthorized':True,'reason':'Fast publication without additional application tests, following the user release preference','installationTested':False,'functionalTested':False},'checks':{'signature':True,'compiledManifest':True,'nativeDeviceTests':False},'physicalDeviceTested':False}
(out/'BUILD-android.json').write_text(json.dumps(build,ensure_ascii=False,indent=2)+'\n');(out/'SHA256SUMS-android.txt').write_text(''.join(a['sha256']+'  '+a['filename']+'\n' for a in apps)+sha(out/'BUILD-android.json')+'  BUILD-android.json\n')
(r/'records/slide-signature.txt').write_text(cert);(r/'records/slide-manifest.txt').write_text(manifest)
before=json.loads((r/'records/page-before.json').read_text());body=before['content'];slide=next(a for a in apps if a['id']=='slide')
body=re.sub(r'\*\*Office Studio Android 1\.0\.2[^\n]*\*\*','**Office Studio Android 1.0.3 · 2026년 11월 8일 · Slide Studio 업데이트**',body,count=1)
body=body.replace('Sheet Studio를 **1.0.1**로 갱신했습니다. Slide Studio **1.0.1**, Word Studio **1.0.0**은 기존 파일을 그대로 제공합니다.','Slide Studio를 **1.0.2**로 갱신했습니다. Sheet Studio **1.0.1**, Word Studio **1.0.0**은 기존 파일을 그대로 제공합니다.')
body=body.replace('**Slide Studio 1.0.1**','**Slide Studio 1.0.2**').replace('android-v1.0.1/Slide-Studio-1.0.1-Android.apk','android-v1.0.3/Slide-Studio-1.0.2-Android.apk')
body=re.sub(r'(\| !\[Slide Studio 아이콘\].*?\| )[0-9.]+ MiB \|',lambda m:m[1]+f"{slide['bytes']/1024/1024:.1f} MiB |",body)
for f in ['BUILD-android.json','SHA256SUMS-android.txt']:body=body.replace('android-v1.0.2/'+f,'android-v1.0.3/'+f)
body=body.replace('releases/tag/android-v1.0.2','releases/tag/android-v1.0.3')
body=body.replace('fb84c9487d3be5c734dcf67bd87af7e6a9cc46b07523e521be5a60ae83052ffb  Slide-Studio-1.0.1-Android.apk',slide['sha256']+'  '+name)
body=body.replace('1.0.1에는 화면 전체의 가용 영역을 사용하는 발표 화면, 좌우 스와이프 이동과 탭으로 여닫는 도구 모음을 포함합니다.','1.0.2에는 발표 확대·이동, 이름이 표시된 터치 도구와 Android 화면 가장자리 여백 개선을 포함합니다.')
start=body.index('## 7. 이번 배포의 확인 범위');end=body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.',start)
body=body[:start]+"""## 7. 이번 배포의 확인 범위

**이번 Slide Studio 1.0.2는 추가 설치·실행·기능 테스트를 생략하고 배포했습니다.** 최신 소스의 발표 확대·터치 도구와 Android 호스트의 카메라·시스템 바 안전 여백 개선을 포함합니다. 빌드를 완료하고 APK 서명·앱 ID·버전·크기와 SHA-256을 기록했습니다. 기존 서명 키와 앱 ID를 유지하고 versionCode를 3으로 올렸습니다.

Sheet Studio 1.0.1과 Word Studio 1.0.0은 기존 파일을 유지합니다. Sheet 1.0.1도 추가 기능 테스트 없이 배포한 버전이며, 위 1.0.0 검증 기록은 이전 배포 당시의 결과입니다.

"""+body[end:];assert '-APKs.zip' not in body
pub=s/'publication/android';(pub/'downloads-ready.md').write_text(body);(pub/'PUBLISHED.json').write_text(json.dumps({'version':'1.0.2','title':before['title'],'url':before['url'],'source':'앱/오피스 스튜디오 Android 다운로드.md','contentSHA256':before['contentSHA256'],'readOnly':True,'expiresAt':None},ensure_ascii=False,indent=2)+'\n')
(pub/'release-notes.md').write_text("""Office Studio Android 1.0.3 — Slide Studio 갱신

Slide Studio 1.0.2(versionCode 3)만 새로 빌드했습니다. 최신 main 1ee351b의 발표 확대·터치 도구·안전 여백과 Android 호스트 584be72의 카메라·시스템 바 영역 수정을 포함합니다. 기존 앱 ID와 서명 키를 유지합니다.

설치·실행·기능 테스트는 생략했습니다. Sheet Studio 1.0.1과 Word Studio 1.0.0은 기존 파일을 유지하며 통합 ZIP은 만들지 않습니다.

[Android 다운로드 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ee660ab4fe42ff63c8aa9495bab1f1bfcdedc214e038c2a)
""")
print(json.dumps({'slideVersion':'1.0.2','bytes':slide['bytes'],'sha256':slide['sha256'],'testsExecuted':False,'combinedZIP':False}))
