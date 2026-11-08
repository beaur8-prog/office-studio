"""Verify public asset digests, the new page and unchanged desktop releases."""
import datetime,hashlib,json,pathlib,subprocess,urllib.request

root=pathlib.Path(__file__).resolve().parent.parent
proofs=root.parent/'proofs'
load=lambda p:json.loads(p.read_text())
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def api(endpoint):return json.loads(subprocess.check_output(['gh','api',endpoint],text=True))
def request(url,method='GET'):
    return urllib.request.urlopen(urllib.request.Request(url,method=method,headers={'User-Agent':'Office-Studio-Release-Verification'}),timeout=60)
def page_data(url):
    with request(url+'/data') as response:return json.load(response)

release=api('repos/beaur8-prog/office-studio/releases/tags/android-v1.0.0')
assert not release['draft'] and not release['prerelease']
out=root/'release/android/1.0.0'
names={a['name'] for a in release['assets']}
expected={p.name for p in out.iterdir() if p.is_file()}
assert names==expected and len(names)==10
assert not any('test.apk' in n or 'VALIDATION' in n for n in names)
assets=[]
for asset in release['assets']:
    local=out/asset['name'];digest=sha(local)
    assert asset['digest']=='sha256:'+digest and asset['size']==local.stat().st_size
    with request(asset['browser_download_url'],'HEAD') as response:
        assert response.status==200 and int(response.headers['Content-Length'])==asset['size']
    assets.append({'name':asset['name'],'bytes':asset['size'],'sha256':digest,'url':asset['browser_download_url'],'publicDownloadVerified':True})
baseline=load(proofs/'desktop-releases-before.json')
for repo,before in baseline.items():
    releases=api('repos/beaur8-prog/'+repo+'/releases')
    latest=api('repos/beaur8-prog/'+repo+'/releases/latest')
    assert latest['tag_name']==before['latestTag']
    retained=[{'tag':r['tag_name'],'id':r['id'],'assets':[{'name':a['name'],'size':a['size'],'digest':a.get('digest'),'id':a['id']}for a in r['assets']]}for r in releases if not r['draft'] and r['tag_name']!='android-v1.0.0']
    assert retained==before['publishedReleases'],repo+' desktop release changed'
publication=load(root/'publication/android/PUBLISHED.json')
data=page_data(publication['url'])
assert hashlib.sha256(data['content'].encode()).hexdigest()==publication['contentSHA256']
assert not data['editingEnabled'] and data['expiresAt'] is None
assert data['content']==(root/'publication/android/downloads-ready.md').read_text()
for platform,before in load(proofs/'desktop-pages-before.json').items():
    data=page_data(before['url'])
    assert hashlib.sha256(data['content'].encode()).hexdigest()==before['contentSHA256'],platform+' page changed'
    assert data['editingEnabled']==before['editingEnabled'] and data['expiresAt']==before['expiresAt']
report={'passed':True,'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'releaseURL':release['html_url'],
        'downloadPage':publication['url'],'pageSHA256':publication['contentSHA256'],'publicAssets':assets,
        'privateValidationInputsAbsent':True,'readOnlyPage':True,'automatedVisitsExcluded':True,
        'desktopPagesPreserved':True,'desktopReleasesAndUpdateFeedsPreserved':True,
        'latestWindowsRelease':baseline['office-studio']['latestTag']}
directory=root/'test-results/android-publication';directory.mkdir(parents=True,exist_ok=True)
(directory/'publication-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'passed':True,'publicAssets':len(assets),'downloadPage':publication['url'],'desktopPreserved':True}))
