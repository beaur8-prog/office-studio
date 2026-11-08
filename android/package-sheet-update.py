"""Create Sheet-only release artifacts. No ZIP or application tests are run."""
from pathlib import Path
import json,os,subprocess,hashlib,shutil,datetime,re
s=Path(__file__).resolve().parent.parent;r=s.parent;out=s/'release/android/1.0.2';out.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
apk=s/'android/app/build/outputs/apk/sheet/release/app-sheet-release.apk';sdk=r/'android-sdk/build-tools/36.0.0';env=os.environ.copy();env['JAVA_HOME']='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'
cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],env=env,text=True);assert 'certificate SHA-256 digest: c166b002177773ea993d118545bec3ecdfd2a3f2f286cb7ac850ca646a7a9d3b' in cert
manifest=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True);assert "name='io.officestudio.sheet'" in manifest and "versionCode='2'" in manifest and "versionName='1.0.1'" in manifest and 'application-debuggable' not in manifest
name='Sheet-Studio-1.0.1-Android.apk';target=out/name;shutil.copy2(apk,target)
prior=json.loads((r/'records/previous-build.json').read_text());lock=json.loads((s/'android/source-lock.json').read_text());apps=[]
for a in prior['apps']:
 a={**a,'rebuilt':a['id']=='sheet'}
 if a['id']=='sheet':a.update(version='1.0.1',versionCode=2,filename=name,sha256=sha(target),bytes=target.stat().st_size,sourceCommit=lock['apps']['sheet']['sourceCommit'],upstreamCommit=lock['apps']['sheet']['upstreamCommit'],downloadURL='https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.2/'+name,offlineAssetsVerified=None)
 apps.append(a)
build={'schema':1,'product':'Office Studio','version':'1.0.2','target':'android','releaseType':'sheet-only','changedApps':['sheet'],'sourceCommit':'07c4817a541ce3fe3ba020b70d7543c5482effe3','builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'apps':apps,'combinedZIP':False,'tests':{'status':'skipped','executed':False,'explicitlyAuthorized':True,'reason':'Immediate build and publication without other tests, as explicitly requested','installationTested':False,'functionalTested':False},'checks':{'signature':True,'compiledManifest':True,'nativeDeviceTests':False},'physicalDeviceTested':False}
(out/'BUILD-android.json').write_text(json.dumps(build,ensure_ascii=False,indent=2)+'\n');(out/'SHA256SUMS-android.txt').write_text(''.join(a['sha256']+'  '+a['filename']+'\n' for a in apps)+sha(out/'BUILD-android.json')+'  BUILD-android.json\n')
(r/'records/sheet-signature.txt').write_text(cert);(r/'records/sheet-manifest.txt').write_text(manifest)
before=json.loads((r/'records/pages-before.json').read_text())['android'];body=before['content'];sheet=next(a for a in apps if a['id']=='sheet')
body=re.sub(r'\*\*Office Studio Android 1\.0\.1[^\n]*\*\*','**Office Studio Android 1.0.2 · 2026년 11월 8일 · Sheet Studio 업데이트**',body,count=1)
body=body.replace('Slide Studio만 **1.0.1**로 갱신했습니다. Sheet Studio와 Word Studio는 기존 **1.0.0** 파일을 그대로 제공합니다.','Sheet Studio를 **1.0.1**로 갱신했습니다. Slide Studio **1.0.1**, Word Studio **1.0.0**은 기존 파일을 그대로 제공합니다.')
body=body.replace('**Sheet Studio 1.0.0**','**Sheet Studio 1.0.1**').replace('android-v1.0.0/Sheet-Studio-1.0.0-Android.apk','android-v1.0.2/Sheet-Studio-1.0.1-Android.apk')
body=re.sub(r'(\| !\[Sheet Studio 아이콘\].*?\| )5\.9 MiB \|',lambda m:m[1]+f"{sheet['bytes']/1024/1024:.1f} MiB |",body)
start=body.index('### [세 앱 APK 한 번에 받기');end=body.index('[SHA-256 확인 파일]',start);body=body[:start]+body[end:]
for file in ['SHA256SUMS-android.txt','BUILD-android.json']:body=body.replace('android-v1.0.1/'+file,'android-v1.0.2/'+file)
body=body.replace('releases/tag/android-v1.0.1','releases/tag/android-v1.0.2')
body=body.replace('a6749eae8f313df81fec42b22ee41c37c3e3a3a35f8420e17beb0670e76e954d  Sheet-Studio-1.0.0-Android.apk',sheet['sha256']+'  '+name)
start=body.index('## 7. 이번 배포의 확인 범위');end=body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.',start)
body=body[:start]+"""## 7. 이번 배포의 확인 범위

**이번 Sheet Studio 1.0.1은 설치·실행·기능 테스트를 생략하고 배포했습니다.** 빌드를 완료하고 APK의 서명·앱 ID·버전·파일 크기와 SHA-256을 기록했습니다. 최신 소스의 컴팩트 화면과 연속 셀 입력 개선을 포함합니다. 앱 ID와 배포 서명 키를 유지하며 versionCode를 2로 올렸습니다.

Slide Studio 1.0.1과 Word Studio 1.0.0은 기존 파일을 유지합니다. Slide 1.0.1도 추가 기능 테스트 없이 배포한 버전이며, 위 1.0.0 검증 기록은 이전 배포 당시의 결과입니다.

"""+body[end:]
assert '-APKs.zip' not in body
pub=s/'publication/android';(pub/'downloads-ready.md').write_text(body);(pub/'PUBLISHED.json').write_text(json.dumps({'version':'1.0.1','title':before['title'],'url':before['url'],'source':'앱/오피스 스튜디오 Android 다운로드.md','contentSHA256':before['contentSHA256'],'readOnly':True,'expiresAt':None},ensure_ascii=False,indent=2)+'\n')
(pub/'release-notes.md').write_text("""Office Studio Android 1.0.2 — Sheet Studio만 갱신

Sheet Studio 1.0.1(versionCode 2)을 최신 main 89053c6 기준으로 새로 빌드했습니다. 컴팩트 Android 화면과 연속 셀 입력 개선, 네이티브 상태바 연결을 포함합니다. 기존 앱 ID와 배포 키를 유지합니다. 설치·실행·기능 테스트는 이번 요청에 따라 생략했습니다.

Slide Studio 1.0.1과 Word Studio 1.0.0은 기존 파일을 유지합니다. Android 통합 ZIP 제공을 종료하고 각 앱의 APK만 제공합니다.

[Android 다운로드 페이지](https://sentence-durable-databases-toys.trycloudflare.com/s/7ee660ab4fe42ff63c8aa9495bab1f1bfcdedc214e038c2a)
""")
print(json.dumps({'sheetVersion':'1.0.1','bytes':sheet['bytes'],'sha256':sheet['sha256'],'testsExecuted':False,'combinedZIP':False}))
