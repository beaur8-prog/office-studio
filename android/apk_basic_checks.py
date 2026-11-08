"""Static release APK checks; these do not claim an Android device run."""
import hashlib
import subprocess
import zipfile


def verify_apk(apk, app_id, asset_manifest, bridge_file, build_tools):
    resources = subprocess.check_output([str(build_tools / 'aapt2'), 'dump', 'resources', str(apk)], text=True)
    for resource in ('mipmap/ic_launcher', 'mipmap/ic_launcher_round', 'drawable/ic_launcher_monochrome'):
        assert resource in resources, f'{app_id}: missing compiled icon {resource}'
    expected = asset_manifest['assetFiles']
    with zipfile.ZipFile(apk) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate APK entries'
        for asset in expected:
            data = archive.read('assets/public/' + asset['path'])
            assert len(data) == asset['bytes'], asset['path']
            assert hashlib.sha256(data).hexdigest() == asset['sha256'], asset['path']
        bridge = archive.read('assets/studio-host.js')
        assert hashlib.sha256(bridge).hexdigest() == hashlib.sha256(bridge_file.read_bytes()).hexdigest()
        html = archive.read('assets/public/index.html').decode()
        assert f'studio:"{app_id}"' in html or f'"studio":"{app_id}"' in html
        assert '../studio-host.js' in html
        dex = b''.join(archive.read(name) for name in archive.namelist() if name.startswith('classes') and name.endswith('.dex'))
        print_class = {'sheet': 'SheetPrintAdapter', 'slide': 'SlidePrintSession', 'word': 'WordPrintSession'}[app_id]
        assert ('Lio/officestudio/shell/' + print_class + ';').encode() in dex, print_class
    return {'offlineAssets': True, 'webAssetFiles': len(expected), 'nativeBridge': True,
            'compiledLauncherIcons': True, 'compiledPrintClass': print_class, 'nativeDeviceTested': False}
