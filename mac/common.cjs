'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { root, products, hashFile, verifyAsset, xml, run } = require('../scripts/common.cjs');
const bundleIds = { sheet:'local.lattice.sheetstudio', slide:'local.slidestudio.app', word:'local.wordstudio.app' };
function validateLock(lock) {
  if (lock.schema !== 1 || lock.product !== 'Office Studio' || lock.target !== 'mac-arm64' || !/^\d+\.\d+\.\d+$/.test(lock.version)) throw Error('Invalid Mac suite metadata');
  if (lock.tag !== 'mac-v' + lock.version) throw Error('Mac releases require a separate mac-v tag');
  if (lock.apps?.length !== 3 || new Set(lock.apps.map(a => a.id)).size !== 3) throw Error('Expected three distinct Mac apps');
  for (const app of lock.apps) {
    if (products[app.id] !== app.product || bundleIds[app.id] !== app.bundleId || !/^\d+\.\d+\.\d+$/.test(app.version)) throw Error('Invalid Mac app metadata');
    const a = app.asset;
    const expected = { sheet:`Sheet-Studio-${app.version}-macOS-arm64.zip`, slide:'Slide-Studio-mac-arm64.zip', word:`Word-Studio-${app.version}-mac-arm64.zip` }[app.id];
    if (!a || a.filename !== expected || !Number.isSafeInteger(a.bytes) || a.bytes <= 0 || !/^[a-f0-9]{64}$/.test(a.sha256)) throw Error('Invalid Mac asset');
    if (a.url !== `https://github.com/beaur8-prog/${app.id}-studio/releases/download/v${app.version}/${expected}`) throw Error('Mac inputs must use pinned release URLs');
  }
  return lock;
}
async function readLock() { return validateLock(JSON.parse(await fs.readFile(path.join(__dirname,'suite-lock.json'),'utf8'))); }
async function verifiedInputs(lock) {
  const inputs = [];
  for (const app of lock.apps) {
    const file = path.join(root,'inputs','mac-arm64',app.asset.filename);
    await verifyAsset(file,app.asset);
    inputs.push({ ...app,file });
  }
  return inputs;
}
function checkBundle(bundle, app) {
  const info = JSON.parse(run('/usr/bin/plutil',['-convert','json','-o','-',path.join(bundle,'Contents/Info.plist')],true));
  if (info.CFBundleIdentifier !== app.bundleId || info.CFBundleShortVersionString !== app.version || info.CFBundleExecutable !== app.product) throw Error('Mac bundle metadata mismatch: ' + app.product);
  run('/usr/bin/codesign',['--verify','--deep','--strict',bundle]);
  const architectures = run('/usr/bin/lipo',['-archs',path.join(bundle,'Contents/MacOS',app.product)],true).trim().split(/\s+/);
  if (architectures.length !== 1 || architectures[0] !== 'arm64') throw Error('Expected Apple Silicon app: ' + app.product);
  if (!info.LSMinimumSystemVersion || Number(info.LSMinimumSystemVersion.split('.')[0]) > 12) throw Error('Unexpected minimum macOS: ' + app.product);
  return { version:info.CFBundleShortVersionString,minimumMacOS:info.LSMinimumSystemVersion,architecture:'arm64' };
}
function preinstallScript(apps) {
  let script = '#!/bin/sh\nset -eu\n' +
    'if [ "$3" != "/" ]; then\n echo "Office Studio must be installed on the startup volume." >&2\n exit 1\nfi\n' +
    'if /usr/bin/pgrep -f "/(Sheet Studio|Slide Studio|Word Studio)\\.app/Contents/MacOS/(Sheet Studio|Slide Studio|Word Studio)( |$)" >/dev/null; then\n' +
    ' echo "작업을 저장하고 Sheet Studio, Slide Studio, Word Studio를 모두 종료한 뒤 다시 설치하세요. Save your work and close all three Studio apps before installing." >&2\n exit 1\nfi\n';
  for (const app of apps) script += `old=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' '/Applications/${app.product}.app/Contents/Info.plist' 2>/dev/null || true)\n` +
    `if [ -n "$old" ] && /usr/bin/awk -v old="$old" -v new='${app.version}' 'BEGIN { if (old !~ /^[0-9]+\\.[0-9]+\\.[0-9]+$/) exit 0; split(old,a,"."); split(new,b,"."); for(i=1;i<=3;i++){ if(a[i]+0>b[i]+0) exit 0; if(a[i]+0<b[i]+0) exit 1 } exit 1 }'; then\n` +
    ` echo "${app.product}의 더 최신 버전 또는 확인할 수 없는 버전이 설치되어 있습니다. Existing newer or unrecognized app preserved: $old" >&2\n exit 1\nfi\n`;
  return script;
}
module.exports = { root, readLock, validateLock, verifiedInputs, checkBundle, preinstallScript, hashFile, xml, run };
