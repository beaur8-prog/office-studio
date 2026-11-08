"""Package changed Android APKs and update the existing page, without runtime tests or ZIPs."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
from zoneinfo import ZoneInfo

s = Path(__file__).resolve().parent.parent
r = s.parent
lock = json.loads((s / 'android/source-lock.json').read_text())
policy = json.loads((s / 'android/distribution-policy.json').read_text())
assert policy['mode'] == 'lithos-local-attachments' and policy['publishGitHubRelease'] is False and policy['uploadAPKsToGitHub'] is False
prior = json.loads((r / 'records/previous-build.json').read_text())
before = json.loads((r / 'records/page-before.json').read_text())
cache = json.loads((r / 'records/build-cache.json').read_text())
changed = lock['changedApps']
assert changed and len(changed) == len(set(changed))
assert lock['tests']['executed'] is False and lock['tests']['explicitlyAuthorized'] is True
assert lock['tests']['status'] == 'skipped' and lock['combinedZIP'] is False
version = lock['version']
out = s / 'release/android' / version
out.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['JAVA_HOME'] = cache['javaHome']
sdk = Path(cache['androidSDK']) / 'build-tools/36.0.0'


def sha(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


apps = []
for old in prior['apps']:
    id = old['id']
    app = {**old, 'rebuilt': id in changed}
    if id in changed:
        source = lock['apps'][id]
        assert source['versionCode'] > old['versionCode']
        apk = s / 'android/app/build/outputs/apk' / id / 'release' / f'app-{id}-release.apk'
        certificate = subprocess.check_output(
            [str(sdk / 'apksigner'), 'verify', '--print-certs', str(apk)], env=env, text=True)
        assert 'certificate SHA-256 digest: ' + old['certificateSHA256'] in certificate
        manifest = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
        for field in [f"name='{old['applicationId']}'", f"versionName='{source['version']}'",
                      f"versionCode='{source['versionCode']}'", "minSdkVersion:'29'", "targetSdkVersion:'36'"]:
            assert field in manifest, field
        assert 'application-debuggable' not in manifest
        filename = f"{id.title()}-Studio-{source['version']}-Android.apk"
        file = out / filename
        shutil.copy2(apk, file)
        app.update(version=source['version'], versionCode=source['versionCode'],
                   filename=filename, bytes=file.stat().st_size, sha256=sha(file),
                   sourceCommit=source['sourceCommit'], upstreamCommit=source['upstreamCommit'],
                   offlineAssetsVerified=None)
        (r / 'records' / (id + '-signature.txt')).write_text(certificate)
        (r / 'records' / (id + '-manifest.txt')).write_text(manifest)
    href = 'attachments/office-studio-android/' + app['filename']
    identifier = hashlib.sha256(('앱/' + href).encode()).hexdigest()[:32]
    app['downloadURL'] = before['url'] + '/download/' + identifier
    apps.append(app)

source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=s, text=True).strip()
build = {
    'schema': 1, 'product': 'Office Studio', 'version': version, 'target': 'android',
    'releaseType': 'changed-apps', 'changedApps': changed, 'sourceCommit': source,
    **{key: value for key, value in lock.items() if key.startswith('native') and key.endswith('UpstreamCommit')},
    'builtAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'apps': apps,
    'distribution': 'lithos-local-attachments', 'gitHubReleasePublished': False,
    'combinedZIP': False,
    'tests': {'status': 'skipped', 'executed': False, 'explicitlyAuthorized': True,
              'reason': 'Fast publication with tests omitted per established user instruction',
              'installationTested': False, 'functionalTested': False},
    'checks': {'signature': True, 'compiledManifest': True, 'nativeDeviceTests': False},
    'physicalDeviceTested': False,
}
(out / 'BUILD-android.json').write_text(json.dumps(build, ensure_ascii=False, indent=2) + '\n')
(out / 'SHA256SUMS-android.txt').write_text(
    ''.join(a['sha256'] + '  ' + a['filename'] + '\n' for a in apps) +
    sha(out / 'BUILD-android.json') + '  BUILD-android.json\n')

changes = {
    'sheet': '전체화면 셀 입력·수식 참조, 태블릿 편집과 키보드·Esc·Back 처리 개선',
    'slide': '발표 화면을 원래 앱 창에서 유지, IME 숨김과 Android 16 Esc·Back 처리 개선',
    'word': '느린 드래그 페이지 넘김과 태블릿 확대 배율 표시 개선',
}
day = datetime.datetime.now(ZoneInfo('Asia/Seoul')).strftime('%Y년 %m월 %d일')
summary = ', '.join(a['name'] + ' **' + a['version'] + '**' for a in apps if a['rebuilt'])
unchanged = [a for a in apps if not a['rebuilt']]
body = before['content']
body, count = re.subn(r'\*\*Office Studio Android [^\n]*\*\*',
                     f'**Office Studio Android {version} · {day} · 변경 앱 업데이트**', body, count=1)
assert count == 1
body, count = re.subn(r'^(?:Sheet|Slide|Word) Studio[^\n]*로 갱신했습니다\.[^\n]*$',
                     summary + '로 갱신했습니다.' + (' ' + ', '.join(a['name'] + ' **' + a['version'] + '**' for a in unchanged) + '은 기존 파일을 그대로 제공합니다.' if unchanged else ''),
                     body, count=1, flags=re.M)
assert count == 1
lines = body.splitlines()
for a in apps:
    if not a['rebuilt']:
        continue
    matches = 0
    for i, line in enumerate(lines):
        if line.startswith('| ![' + a['name'] + ' 아이콘]'):
            parts = line.split('|')
            parts[2] = f" **{a['name']} {a['version']}** "
            parts[3] = f" [APK 바로 받기]({a['downloadURL']}) "
            parts[4] = f" {a['bytes'] / 1024 / 1024:.1f} MiB "
            lines[i] = '|'.join(parts)
            matches += 1
    assert matches == 1
body = '\n'.join(lines) + '\n'
for file in ('BUILD-android.json', 'SHA256SUMS-android.txt'):
    body = body.replace('android-v' + prior['version'] + '/' + file, 'android-v' + version + '/' + file)
body = re.sub(r'\s*·\s*\[GitHub 릴리스\]\([^)]*\)', '', body)
body = body.replace('설치 파일은 GitHub에서도 받을 수 있습니다.', 'APK는 이 페이지의 로컬 첨부 파일로 제공합니다.')
for a in apps:
    if a['rebuilt']:
        old = next(x for x in prior['apps'] if x['id'] == a['id'])
        body = body.replace(old['sha256'] + '  ' + old['filename'], a['sha256'] + '  ' + a['filename'])
if 'slide' in changed:
    body = body.replace('1.0.5에는 태블릿 편집 화면 개선과 발표 종료·Esc·Back 처리를 포함합니다.',
                        '1.0.6에는 원래 앱 창에서 발표 화면 유지와 키보드 숨김, Android 16 Esc·뒤로가기 처리 개선을 포함합니다.')
if 'word' in changed:
    body = body.replace('1.0.3에는 연속 페이지 확대 배율 유지와 태블릿 편집, 안전한 Esc 처리를 포함합니다.',
                        '1.0.4에는 느린 드래그 페이지 넘김과 태블릿 확대 배율 표시 개선을 포함합니다.')
start = body.index('## 7. 이번 배포의 확인 범위')
end = body.index('이 문서는 리토스의 읽기 전용 공유 페이지입니다.', start)
details = '\n'.join(f"- **{a['name']} {a['version']}**: {changes[a['id']]} · versionCode {a['versionCode']}"
                    for a in apps if a['rebuilt'])
body = body[:start] + f'''## 7. 이번 배포의 확인 범위

**이번 변경 앱 배포는 추가 설치·실행·기능 테스트를 생략했습니다.** 이전 배포 소스와 비교해 변경된 앱만 새로 빌드했습니다.

{details}

기존 앱 ID와 서명 키를 유지했습니다. 빌드 후 서명·앱 ID·버전·크기와 SHA-256을 기록했습니다. 통합 ZIP은 제공하지 않습니다. 위 1.0.0 검증 기록은 이전 배포 당시 결과입니다.

''' + body[end:]
assert body.split('# 오피스 스튜디오 Android')[0] == before['content'].split('# 오피스 스튜디오 Android')[0]
assert '-APKs.zip' not in body
for a in apps:
    assert a['downloadURL'] in body or 'attachments/office-studio-android/' + a['filename'] in body
pub = s / 'publication/android'
(pub / 'downloads-ready.md').write_text(body)
prior_publication = json.loads((pub / 'PUBLISHED.json').read_text()) if (pub / 'PUBLISHED.json').exists() else {}
(pub / 'PUBLISHED.json').write_text(json.dumps({
    'version': prior['version'], 'title': before['title'], 'url': before['url'],
    'source': '앱/오피스 스튜디오 Android 다운로드.md',
    'downloadMode': 'lithos-attachments', 'distribution': 'local-only',
    'contentSHA256': before['contentSHA256'], 'readOnly': True, 'expiresAt': None,
    **({'downloadMode': 'lithos-attachments'} if prior_publication.get('downloadMode') == 'lithos-attachments' and prior_publication.get('url') == before['url'] else {}),
}, ensure_ascii=False, indent=2) + '\n')
commits = '\n'.join(f"- {a['name']} {a['version']}: 최신 소스 `{a['upstreamCommit']}`, 배포 소스 `{a['sourceCommit']}`"
                    for a in apps if a['rebuilt'])
(pub / 'release-notes.md').write_text(f'''Office Studio Android {version} — 변경 앱 갱신

{details}

{commits}

기존 앱 ID·정식 서명 키를 유지하고 versionCode를 올렸습니다. 설치·실행·기능 테스트와 통합 ZIP 생성은 생략했습니다. Windows latest·Mac 업데이트 피드와 배포 페이지는 유지합니다.

[Android 다운로드 페이지]({before['url']})
''')
print(json.dumps({'version': version, 'sourceCommit': source,
                  'apps': [{k: a[k] for k in ('id', 'version', 'versionCode', 'bytes', 'sha256')} for a in apps],
                  'testsExecuted': False, 'combinedZIP': False}, ensure_ascii=False, indent=2))
