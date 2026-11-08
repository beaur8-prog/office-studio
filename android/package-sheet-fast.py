"""Sheet-only fast release: no application tests, no combined ZIP."""
from pathlib import Path
import json,subprocess,os,hashlib,shutil,datetime,re
s=Path(__file__).resolve().parent.parent;r=s.parent;lock=json.loads((s/'android/source-lock.json').read_text());cache=json.loads((r/'records/build-cache.json').read_text());out=s/'release/android/1.0.4';out.mkdir(parents=True,exist_ok=True);sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
apk=s/'android/app/build/outputs/apk/sheet/release/app-sheet-release.apk';sdk=Path(cache['androidSDK'])/'build-tools/36.0.0';env=os.environ.copy();env['JAVA_HOME']=cache['javaHome'];cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],env=env,text=True);assert 'certificate SHA-256 digest: c166b002177773ea993d118545bec3ecdfd2a3f2f286cb7ac850ca646a7a9d3b' in cert
manifest=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True);assert "name='io.officestudio.sheet'" in manifest and "versionName='1.0.2'" in manifest and "versionCode='3'" in manifest
name='Sheet-Studio-1.0.2-Android.apk';f=out/name;shutil.copy2(apk,f);prior=json.loads((r/'records/previous-build.json').read_text());apps=[]
for a in prior['apps']:
 a={**a,'rebuilt':a['id']=='sheet'}
 if a['id']=='sheet':a.update(version='1.0.2',versionCode=3,filename=name,sha256=sha(f),bytes=f.stat().st_size,sourceCommit=lock['apps']['sheet']['sourceCommit'],upstreamCommit=lock['apps']['sheet']['upstreamCommit'],downloadURL='https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.4/'+name,offlineAssetsVerified=None)
 apps.append(a)
build={'schema':1,'product':'Office Studio','version':'1.0.4','target':'android','releaseType':'sheet-only','changedApps':['sheet'],'sourceCommit':'3daf59964cd7e75eb48c213168a6353b6bc12475','builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'apps':apps,'combinedZIP':False,'tests':{'status':'skipped','executed':False,'explicitlyAuthorized':True,'reason':'Fast publication without installation or functional tests following established user preference','installationTested':False,'functionalTested':False},'checks':{'signature':True,'compiledManifest':True,'nativeDeviceTests':False},'physicalDeviceTested':False}
(out/'BUILD-android.json').write_text(json.dumps(build,ensure_ascii=False,indent=2)+'\n');(out/'SHA256SUMS-android.txt').write_text(''.join(a['sha256']+'  '+a['filename']+'\n' for a in apps)+sha(out/'BUILD-android.json')+'  BUILD-android.json\n');(r/'records/sheet-signature.txt').write_text(cert);(r/'records/sheet-manifest.txt').write_text(manifest)
before=json.loads((r/'records/page-before.json').read_text());body=before['content'];sheet=next(a for a in apps if a['id']=='sheet')
body=re.sub(r'\*\*Office Studio Android 1\.0\.3[^\n]*\*\*','**Office Studio Android 1.0.4 · 2026년 11월 8일 · Sheet Studio 업데이트**',body,count=1)
body=body.replace('Slide Studio를 **1.0.2**로 갱신했습니다. Sheet Studio **1.0.1**, Word Studio **1.0.0**은 기존 파일을 그대로 제공합니다.','Sheet Studio를 **1.0.2**로 갱신했습니다. Slide Studio **1.0.2**, Word Studio **1.0.0**은 기존 파일을 그대로 제공합니다.')
body=body.replace('**Sheet Studio 1.0.1**','**Sheet Studio 1.0.2**').replace('android-v1.0.2/Sheet-Studio-1.0.1-Android.apk','android-v1.0.4/Sheet-Studio-1.0.2-Android.apk')
body=re.sub(r'(\| !\[Sheet Studio 아이콘\].*?\| )[0-9.]+ MiB \|',lambda m:m[1]+f"{sheet['bytes']/1024/1024:.1f} MiB |",body)
for name2 in ['BUILD-android.json','SHA256SUMS-android.txt']:body=body.replace('android-v1.0.3/'+name2,'android-v1.0.4/'+name2)
body=body.replace('releases/tag/android-v1.0.3','releases/tag/android-v1.0.4').replace('267f437580f8c143aa797f24fadc90133b0a3602044bab4f9c48a73d089b2479  Sheet-Studio-1.0.1-Android.apk',sheet['sha256']+'  '+sheet['filename'])
start=body.index('## 7. 이번 배포의 확인 범위');end=body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.',start)
body=body[:start]+"""## 7. 이번 배포의 확인 범위

**이번 Sheet Studio 1.0.2는 추가 설치·실행·기능 테스트를 생략하고 배포했습니다.** 최신 소스의 전체화면 격자 영역과 화면 여백 개선, Android 호스트의 전체 창·상태바·탐색바·카메라 여백 전달을 함께 반영했습니다. 빌드 후 서명·앱 ID·버전·크기와 SHA-256을 기록했습니다. 기존 앱 ID와 서명 키를 유지하고 versionCode를 3으로 올렸습니다.

Slide Studio 1.0.2와 Word Studio 1.0.0은 기존 파일을 유지합니다. Slide 1.0.2도 추가 기능 테스트 없이 배포한 버전이며, 위 1.0.0 검증 기록은 이전 배포 당시 결과입니다.

"""+body[end:];assert '-APKs.zip' not in body
pub=s/'publication/android';(pub/'downloads-ready.md').write_text(body);(pub/'PUBLISHED.json').write_text(json.dumps({'version':'1.0.3','title':before['title'],'url':before['url'],'source':'앱/오피스 스튜디오 Android 다운로드.md','contentSHA256':before['contentSHA256'],'readOnly':True,'expiresAt':None},ensure_ascii=False,indent=2)+'\n')
(pub/'release-notes.md').write_text("""Office Studio Android 1.0.4 — Sheet Studio 갱신

Sheet Studio 1.0.2(versionCode 3)만 최신 main cd47544로 새로 빌드했습니다. 전체화면 격자 영역과 여백 개선 및 해당 Android 호스트 연결을 함께 포함하며 앱 ID·기존 서명 키를 유지합니다.

설치·실행·기능 테스트와 통합 ZIP 생성은 생략했습니다. Slide Studio 1.0.2와 Word Studio 1.0.0은 기존 파일을 그대로 제공합니다.

[Android 다운로드 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ee660ab4fe42ff63c8aa9495bab1f1bfcdedc214e038c2a)
""")
print(json.dumps({'sheetVersion':'1.0.2','bytes':sheet['bytes'],'sha256':sheet['sha256'],'testsExecuted':False,'ZIPCreated':False}))
