"""Package a Slide-only Android update; do not execute application tests."""
import pathlib,json,subprocess,os,hashlib,zipfile,shutil,re,datetime
s=pathlib.Path(__file__).resolve().parent.parent;r=s.parent;out=s/'release/android/1.0.1';out.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
lock=json.loads((s/'android/source-lock.json').read_text());original=json.loads((r/'baseline/BUILD-android-1.0.0.json').read_text());baseline=json.loads((r/'proofs/unchanged-apps.json').read_text());sdk=r/'android-sdk/build-tools/36.0.0';env=os.environ.copy();env['JAVA_HOME']='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'
apk=s/'android/app/build/outputs/apk/slide/release/app-slide-release.apk'
cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],env=env,text=True);expected='c166b002177773ea993d118545bec3ecdfd2a3f2f286cb7ac850ca646a7a9d3b';assert 'certificate SHA-256 digest: '+expected in cert
badging=subprocess.check_output([str(sdk/'aapt2'),'dump','badging',str(apk)],text=True);assert "name='io.officestudio.slide'" in badging and "versionCode='2'" in badging and "versionName='1.0.1'" in badging and 'application-debuggable' not in badging
filename='Slide-Studio-1.0.1-Android.apk';shutil.copy2(apk,out/filename)
web=json.loads((s/'android/manifests/web-assets.json').read_text())['slide']
with zipfile.ZipFile(apk) as z:
 for f in web['assetFiles']:assert hashlib.sha256(z.read('assets/public/'+f['path'])).hexdigest()==f['sha256']
 assert z.read('assets/studio-host.js')==(s/'android/app/src/main/assets/studio-host.js').read_bytes()
resources=subprocess.check_output([str(sdk/'aapt2'),'dump','resources',str(apk)],text=True);assert all(x in resources for x in ['mipmap/ic_launcher','mipmap/ic_launcher_round','drawable/ic_launcher_foreground','drawable/ic_launcher_monochrome'])
apps=[];componentFiles=[]
for id in ['sheet','slide','word']:
 prior=next(a for a in original['apps'] if a['id']==id)
 if id=='slide':
  data={**prior,'version':'1.0.1','versionCode':2,'filename':filename,'sha256':sha(out/filename),'bytes':(out/filename).stat().st_size,'sourceCommit':lock['apps']['slide']['sourceCommit'],'upstreamCommit':lock['apps']['slide']['upstreamCommit'],'downloadURL':'https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.1/'+filename,'rebuilt':True,'offlineAssetsVerified':len(web['assetFiles'])};componentFiles.append(out/filename)
 else:
  keep=next(a for a in baseline if a['filename']==prior['filename']);f=r/'baseline'/keep['filename'];assert sha(f)==prior['sha256'];data={**prior,'downloadURL':keep['url'],'rebuilt':False};componentFiles.append(f)
 apps.append(data)
bundle=out/'Office-Studio-1.0.1-Android-APKs.zip'
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_STORED) as z:
 for f in componentFiles:
  info=zipfile.ZipInfo(f.name,date_time=(2026,11,8,0,0,0));info.external_attr=0o100644<<16;z.writestr(info,f.read_bytes())
report={'schema':1,'product':'Office Studio','version':'1.0.1','target':'android','releaseType':'slide-only','changedApps':['slide'],'sourceCommit':'ff9ff7b0ec167f299850f7da8512f54892b12dcd','builtAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'apps':apps,'bundle':{'filename':bundle.name,'bytes':bundle.stat().st_size,'sha256':sha(bundle)},'tests':{'status':'skipped','executed':False,'explicitlyAuthorized':True,'reason':'Explicit user request to publish without tests','installationTested':False,'functionalTested':False},'checks':{'signature':True,'compiledManifest':True,'launcherIconResourcesIncluded':True,'offlineAssetsMatchBuild':True,'unchangedAppBytesRetained':True,'nativeDeviceTests':False},'physicalDeviceTested':False}
(out/'BUILD-android.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
files=componentFiles+[bundle,out/'BUILD-android.json'];(out/'SHA256SUMS-android.txt').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in files));(r/'proofs/slide-apk-signature.txt').write_text(cert);(r/'proofs/slide-apk-manifest.txt').write_text(badging)
print(json.dumps({'version':'1.0.1','changedApps':['slide'],'slideSHA256':apps[1]['sha256'],'slideBytes':apps[1]['bytes'],'testsExecuted':False,'bundleSHA256':sha(bundle)}))
