"""Fast one-app APK publication metadata. No runtime tests or combined ZIP."""
from pathlib import Path
import json,hashlib,os,subprocess,shutil,re,datetime
from zoneinfo import ZoneInfo
s=Path(__file__).resolve().parent.parent;r=s.parent;lock=json.loads((s/'android/source-lock.json').read_text());assert len(lock['changedApps'])==1 and lock['tests']['status']=='skipped'
id=lock['changedApps'][0];version=lock['version'];app=lock['apps'][id];appVersion=app['version'];code=app['versionCode'];name=id.title()+' Studio';cache=json.loads((r/'records/build-cache.json').read_text());prior=json.loads((r/'records/previous-build.json').read_text());old=next(a for a in prior['apps'] if a['id']==id);changes=json.loads((r/'records/release-options.json').read_text())['changes'];sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
out=s/'release/android'/version;out.mkdir(parents=True,exist_ok=True);apk=s/'android/app/build/outputs/apk'/id/'release'/f'app-{id}-release.apk';sdk=Path(cache['androidSDK'])/'build-tools/36.0.0';env=os.environ.copy();env['JAVA_HOME']=cache['javaHome']
cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],env=env,text=True);assert 'certificate SHA-256 digest: '+old['certificateSHA256'] in cert
manifest=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True);assert f"name='io.officestudio.{id}'" in manifest and f"versionName='{appVersion}'" in manifest and f"versionCode='{code}'" in manifest
filename=f'{id.title()}-Studio-{appVersion}-Android.apk';file=out/filename;shutil.copy2(apk,file);apps=[]
for a in prior['apps']:
 a={**a,'rebuilt':a['id']==id}
 if a['id']==id:a.update(version=appVersion,versionCode=code,filename=filename,sha256=sha(file),bytes=file.stat().st_size,sourceCommit=app['sourceCommit'],upstreamCommit=app['upstreamCommit'],downloadURL=f'https://github.com/beaur8-prog/office-studio/releases/download/android-v{version}/{filename}',offlineAssetsVerified=None)
 apps.append(a)
source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=s,text=True).strip();build={'schema':1,'product':'Office Studio','version':version,'target':'android','releaseType':id+'-only','changedApps':[id],'sourceCommit':source,'nativeSlideHostUpstreamCommit':lock.get('nativeSlideHostUpstreamCommit'),'nativeWordHostUpstreamCommit':lock.get('nativeWordHostUpstreamCommit'),'builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'apps':apps,'combinedZIP':False,'tests':{'status':'skipped','executed':False,'explicitlyAuthorized':True,'reason':'Fast publication with tests omitted per established user instruction','installationTested':False,'functionalTested':False},'checks':{'signature':True,'compiledManifest':True,'nativeDeviceTests':False},'physicalDeviceTested':False}
(out/'BUILD-android.json').write_text(json.dumps(build,ensure_ascii=False,indent=2)+'\n');(out/'SHA256SUMS-android.txt').write_text(''.join(a['sha256']+'  '+a['filename']+'\n' for a in apps)+sha(out/'BUILD-android.json')+'  BUILD-android.json\n');(r/'records'/(id+'-signature.txt')).write_text(cert);(r/'records'/(id+'-manifest.txt')).write_text(manifest)
before=json.loads((r/'records/page-before.json').read_text());body=before['content'];current=next(a for a in apps if a['id']==id);others=[a for a in apps if a['id']!=id];summary=', '.join(a['name']+' **'+a['version']+'**' for a in others);day=datetime.datetime.now(ZoneInfo('Asia/Seoul')).strftime('%Y년 %m월 %d일')
body=re.sub(r'\*\*Office Studio Android [^\n]*\*\*',f'**Office Studio Android {version} · {day} · {name} 업데이트**',body,count=1)
body=re.sub(r'^(?:Sheet|Slide|Word) Studio를 \*\*[^\n]+로 갱신했습니다[^\n]*$',name+'를 **'+appVersion+'**로 갱신했습니다. '+summary+'은 기존 파일을 그대로 제공합니다.',body,count=1,flags=re.M)
lines=body.splitlines()
for i,line in enumerate(lines):
 if line.startswith('| !['+name+' 아이콘]'):
  parts=line.split('|');parts[2]=f' **{name} {appVersion}** ';parts[3]=f" [APK 받기]({current['downloadURL']}) ";parts[4]=f" {current['bytes']/1024/1024:.1f} MiB ";lines[i]='|'.join(parts)
body='\n'.join(lines)+'\n'
for f in ['BUILD-android.json','SHA256SUMS-android.txt']:body=body.replace('android-v'+prior['version']+'/'+f,'android-v'+version+'/'+f)
body=body.replace('releases/tag/android-v'+prior['version'],'releases/tag/android-v'+version).replace(old['sha256']+'  '+old['filename'],current['sha256']+'  '+filename)
start=body.index('## 7. 이번 배포의 확인 범위');end=body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.',start)
body=body[:start]+f"""## 7. 이번 배포의 확인 범위

**이번 {name} {appVersion}은 추가 설치·실행·기능 테스트를 생략하고 배포했습니다.** {changes} 빌드 후 서명·앱 ID·버전·크기와 SHA-256을 기록했습니다. 기존 앱 ID와 서명 키를 유지하고 versionCode를 {code}로 올렸습니다.

{summary}은 기존 파일을 유지합니다. 위 1.0.0 검증 기록은 이전 배포 당시 결과입니다.

"""+body[end:];assert '-APKs.zip' not in body and current['downloadURL'] in body
pub=s/'publication/android';(pub/'downloads-ready.md').write_text(body);(pub/'PUBLISHED.json').write_text(json.dumps({'version':prior['version'],'title':before['title'],'url':before['url'],'source':'앱/오피스 스튜디오 Android 다운로드.md','contentSHA256':before['contentSHA256'],'readOnly':True,'expiresAt':None},ensure_ascii=False,indent=2)+'\n')
(pub/'release-notes.md').write_text(f"""Office Studio Android {version} — {name} 갱신

{name} {appVersion}(versionCode {code})만 최신 소스 {app['upstreamCommit'][:7]}로 새로 빌드했습니다. {changes} 앱 ID와 기존 서명 키를 유지합니다.

설치·실행·기능 테스트와 통합 ZIP 생성은 생략했습니다. {summary}은 기존 파일을 유지합니다.

[Android 다운로드 페이지]({before['url']})
""")
print(json.dumps({'app':id,'version':appVersion,'releaseVersion':version,'bytes':current['bytes'],'sha256':current['sha256'],'sourceCommit':source,'testsExecuted':False,'combinedZIP':False}))
