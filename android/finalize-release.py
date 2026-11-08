"""Finalize public artifacts only after native CI and mobile browser checks pass."""
import argparse,datetime,hashlib,json,pathlib,zipfile,subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--proofs',type=pathlib.Path,required=True)
parser.add_argument('--run',type=int,required=True)
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parent.parent
out=root/'release/android/1.0.0'
read=lambda p:json.loads(p.read_text())
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
ci=read(args.proofs/'native-ci-current.json')
assert ci['status']=='completed' and ci['conclusion']=='success'
assert str(args.run) in ci['url']
assert len(ci['jobs'])==2 and all(j['conclusion']=='success' for j in ci['jobs'])
build=read(out/'BUILD-android.json')
lock=read(root/'android/verification-inputs.json')
ci_lock=json.loads(subprocess.check_output(['git','show',ci['headSha']+':android/verification-inputs.json'],cwd=root,text=True))
assert ci_lock==lock, 'Validation input lock differs from the successful native workflow'
profiles=[]
for name in ['phone','tablet']:
    profile=read(args.proofs/'native-final'/('android-native-'+name)/'native-verification.json')
    assert profile['passed'] and profile['nativeScenarios']==18
    assert profile['uninstallVerified'] and profile['externalDocumentPreservedAfterUninstall']
    for app in build['apps']:
        identity=[r for r in profile['results'] if r['app']==app['id'] and r['scenario']=='identity-icons-offline']
        assert len(identity)==1 and identity[0]['certificateSHA256']==app['certificateSHA256']
    profiles.append(profile)
for app in build['apps']:
    expected=next(a for a in lock['productionApks'] if a['id']==app['id'])
    assert sha(out/app['filename'])==app['sha256']==expected['sha256']
    assert (out/app['filename']).stat().st_size==app['bytes']==expected['bytes']
browser=read(args.proofs/'browser-validation.json')
assert browser['sheet']['passed'] and browser['slide']['final']['unexpected']==0 and browser['slide']['final']['expected']==19
assert browser['word']['unexpected']==0 and browser['word']['expected']==13
bundle=out/'Office-Studio-1.0.0-Android-APKs.zip'
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_STORED) as z:
    for app in build['apps']:
        info=zipfile.ZipInfo(app['filename'],date_time=(2026,11,8,0,0,0));info.external_attr=0o100644<<16
        z.writestr(info,(out/app['filename']).read_bytes())
with zipfile.ZipFile(bundle) as z:
    assert len(z.namelist())==3
    for app in build['apps']:assert hashlib.sha256(z.read(app['filename'])).hexdigest()==app['sha256']
proof={'schema':1,'product':'Office Studio','version':'1.0.0','platform':'android',
       'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'passed':True,
       'buildSourceCommit':build['sourceCommit'],'verificationSourceCommit':ci['headSha'],
       'workflowURL':ci['url'],'validationInputsSHA256':lock['sha256'],
       'apps':[{k:a[k] for k in ['id','applicationId','version','versionCode','sourceCommit','sha256','certificateSHA256']}for a in build['apps']],
       'profiles':profiles,'browserChecks':browser,'priorAttempts':read(args.proofs/'CI-ATTEMPTS.json'),
       'bundle':{'filename':bundle.name,'bytes':bundle.stat().st_size,'sha256':sha(bundle)},
       'physicalDeviceTested':False,
       'scopeNotes':['Android 15 API 35 x86_64 Google APIs emulators: phone and tablet layouts.',
                     'System document picker cancellation is exercised through its actual UI. Success and read-only failures use content URI results from a separate test-only document provider.',
                     'Android chooser hands exact bytes to a separate recipient process using production FileProvider URI grants.',
                     'Sharing cancellation and an early chooser RESULT_CANCELED callback are tested before successful target selection.',
                     'Reinstall uses adb install -r of the same signed APK; this is the first Android release.',
                     'Manufacturer file providers, physical Korean keyboards, S Pen, DeX, external displays and large-document performance have not been verified on physical devices.']}
(out/'ANDROID-VERIFICATION.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
build['checks'].update(nativeDeviceTests=True,browserAndroidUI=True)
build.pop('publicRelease',None)
build['nativeVerificationURL']=ci['url']
(out/'BUILD-android.json').write_text(json.dumps(build,ensure_ascii=False,indent=2)+'\n')
assets=[out/a['filename'] for a in build['apps']]+[bundle]+sorted(out.glob('*-icon.png'))+[out/'BUILD-android.json',out/'ANDROID-VERIFICATION.json']
assert len(assets)==9 and not any('test.apk' in p.name for p in assets)
(out/'SHA256SUMS-android.txt').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in assets))
print(json.dumps({'passed':True,'nativeScenarios':sum(p['nativeScenarios'] for p in profiles),'publicAssets':10,'bundleSHA256':sha(bundle)}))
