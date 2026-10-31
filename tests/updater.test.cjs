'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { EventEmitter } = require('node:events');
const { createHash } = require('node:crypto');
const { createStudioUpdater, selectRelease, selectMacRelease, newer, readJSON, download, powershellCommand } = require('../updater/studio-updater.cjs');

const bytes = Buffer.from('verified installer bytes');
const sha256 = createHash('sha256').update(bytes).digest('hex');
function release(id = 'word', platform = 'win32', arch = 'x64', version = '1.4.4') {
  const filename = platform === 'win32'
    ? { sheet: `Sheet-Studio-${version}-Windows-x64-Setup.exe`, slide: 'Slide-Studio-Setup-x64.exe', word: `Word-Studio-${version}-windows-x64-setup.exe` }[id]
    : { sheet: `Sheet-Studio-${version}-macOS-${arch}.zip`, slide: `Slide-Studio-mac-${arch}.zip`, word: `Word-Studio-${version}-mac-${arch}.zip` }[id];
  return { tag_name: 'v' + version, draft: false, prerelease: false, assets: [{ name: filename, state: 'uploaded', size: bytes.length,
    digest: 'sha256:' + sha256, browser_download_url: `https://github.com/beaur8-prog/${id}-studio/releases/download/v${version}/${filename}` }] };
}
async function fixture(t, { platform = 'win32', answer = 1, fetchImpl, helperMode = 'ready' } = {}) {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'studio-update-test-'));
  const app = new EventEmitter();
  app.isPackaged = true; app.getVersion = () => '1.4.3'; app.getPath = () => directory;
  let quitCalls = 0;
  app.quit = () => { quitCalls++; };
  const requests = [], dialogs = [], launched = [], opened = [], warnings = [];
  const executable = path.join(directory, 'Word Studio.exe');
  await fs.writeFile(executable, 'test editor');
  await fs.writeFile(path.join(directory, 'Uninstall Word Studio.exe'), 'test uninstaller');
  const updater = createStudioUpdater({ app, id: 'word', platform, arch: platform === 'darwin' ? 'arm64' : 'x64',
    executable,
    dialog: { showMessageBox: async (...args) => { dialogs.push(args.at(-1)); return { response: answer }; } },
    shell: { showItemInFolder: file => opened.push(file), openExternal: async url => opened.push(url) },
    fetchImpl: fetchImpl || (async url => { requests.push(url); const item = release('word', platform, platform === 'darwin' ? 'arm64' : 'x64'); return url.includes('api.github.com') ? Response.json(platform === 'darwin' ? [item] : item) : new Response(bytes); }),
    helperReadyTimeout: helperMode === 'timeout' ? 150 : 5000,
    launch: (...args) => {
      launched.push(args);
      const helper = new EventEmitter(); helper.unref = () => {};
      const command = Buffer.from(/-EncodedCommand ([A-Za-z0-9+/=]+)$/.exec(args[1].at(-1))[1], 'base64').toString('utf16le');
      const jobFile = /-JobFile '([^']*(?:''[^']*)*)'$/.exec(command)[1].replaceAll("''", "'");
      setImmediate(async () => {
        if (helperMode === 'error') { helper.emit('error', new Error('Helper launch blocked')); return; }
        if (helperMode === 'exit') { helper.emit('exit', 9); return; }
        if (helperMode === 'timeout') return;
        if (helperMode === 'wrapperExit') helper.emit('exit', 0);
        const job = JSON.parse(await fs.readFile(jobFile, 'utf8'));
        await fs.writeFile(jobFile + '.ready', '\uFEFF' + JSON.stringify({ schema: 1, id: job.id, parentPid: job.parentPid }));
      });
      return helper;
    },
    logger: { warn: (...args) => warnings.push(args) },
  });
  t.after(async () => { updater.dispose(); await fs.rm(directory, { recursive: true, force: true }); });
  return { updater, directory, app, requests, dialogs, launched, opened, warnings, quitCalls: () => quitCalls };
}

test('versions compare numerically and reject ambiguous versions', () => {
  assert.equal(newer('1.10.0', '1.9.9'), true);
  assert.equal(newer('1.4.3', '1.4.3'), false);
  assert.equal(newer('0.9.9', '1.0.0'), false);
  assert.throws(() => newer('1.4.4-beta.1', '1.4.3'));
  assert.throws(() => newer('1.04.4', '1.4.3'));
});
test('all app/platform assets select the intended stable release only', () => {
  for (const id of ['sheet','slide','word']) for (const [platform,arch] of [['win32','x64'],['darwin','arm64'],['darwin','x64']]) {
    const candidate = selectRelease(release(id,platform,arch), { id,currentVersion:'1.4.3',platform,arch });
    assert.equal(candidate.sha256,sha256); assert.equal(candidate.version,'1.4.4');
  }
  const options = { id:'word',currentVersion:'1.4.3',platform:'win32',arch:'x64' };
  assert.equal(selectRelease({ ...release(), prerelease:true }, options), null);
  assert.equal(selectRelease({ ...release(), draft:true }, options), null);
  assert.equal(selectRelease(release('word','win32','x64','1.4.2'), options), null);
  assert.equal(selectRelease(release(), { ...options, arch:'arm64' }), null);
});
test('unexpected sources, missing digests, duplicate assets and oversized payloads are rejected', () => {
  const options = { id:'word',currentVersion:'1.4.3',platform:'win32',arch:'x64' };
  for (const mutate of [
    r => { r.assets[0].browser_download_url = 'https://attacker.example/setup.exe'; },
    r => { delete r.assets[0].digest; },
    r => { r.assets.push(r.assets[0]); },
    r => { r.assets[0].size = 3 * 1024 ** 3; },
  ]) { const r = release(); mutate(r); assert.throws(() => selectRelease(r,options)); }
});
test('metadata response is bounded', async () => {
  await assert.rejects(readJSON(new Response('x'.repeat(1024 * 1024 + 1))), /너무 큽니다/);
});
test('Mac updates select the highest compatible version independently of Windows latest', () => {
  const options = { id:'word', currentVersion:'1.4.3', platform:'darwin', arch:'arm64' };
  const list = [release('word','win32','x64','1.4.9'), release('word','darwin','arm64','1.4.4'),
    { ...release('word','darwin','arm64','1.4.8'), prerelease:true }, release('word','darwin','x64','1.4.7'),
    release('word','darwin','arm64','1.4.5')];
  assert.equal(selectMacRelease(list, options).version, '1.4.5');
  assert.equal(selectMacRelease(list, { ...options, currentVersion:'1.4.5' }), null);
  assert.throws(() => selectMacRelease(list, { ...options, platform:'win32' }));
});
test('Mac release enumeration rejects malformed metadata and untrusted matching downloads', () => {
  const options = { id:'word', currentVersion:'1.4.3', platform:'darwin', arch:'arm64' };
  assert.throws(() => selectMacRelease({}, options));
  assert.throws(() => selectMacRelease(Array(21).fill(release()), options));
  const item = release('word','darwin','arm64');
  item.assets[0].browser_download_url = 'https://attacker.example/app.zip';
  assert.throws(() => selectMacRelease([item], options));
});
test('download verifies the whole file and rechecks a cached file before reuse', async t => {
  const { directory } = await fixture(t);
  const asset = selectRelease(release(), { id:'word',currentVersion:'1.4.3',platform:'win32',arch:'x64' });
  let requests = 0;
  const fetchImpl = async () => { requests++; return new Response(bytes); };
  const file = await download(asset,directory,fetchImpl);
  await download(asset,directory,fetchImpl);
  assert.equal(requests,1);
  await fs.writeFile(file,Buffer.alloc(bytes.length));
  await download(asset,directory,fetchImpl);
  assert.equal(requests,2);
  assert.deepEqual(await fs.readFile(file),bytes);
});
test('corrupt/truncated/oversized downloads leave no installable partial file', async t => {
  const { directory } = await fixture(t);
  const asset = selectRelease(release(), { id:'word',currentVersion:'1.4.3',platform:'win32',arch:'x64' });
  for (const payload of [Buffer.alloc(bytes.length),bytes.subarray(1),Buffer.concat([bytes,bytes])]) {
    await assert.rejects(download(asset,directory,async () => new Response(payload)));
    assert.deepEqual(await fs.readdir(path.join(directory,sha256)),[]);
  }
});
test('Windows confirms helper startup before requesting a normal editor quit', async t => {
  const f = await fixture(t,{ answer:0 });
  await f.updater.check(true);
  assert.equal(f.quitCalls(),1);
  assert.equal(f.launched.length,1,'the waiting helper must be ready before the editor quits');
  f.app.emit('quit');
  assert.equal(f.launched.length,1,'quit must not launch a second helper');
  const command = await fs.readFile(path.join(f.directory, 'updates', sha256, (await fs.readdir(path.join(f.directory,'updates',sha256))).find(name => name.endsWith('.ps1'))), 'utf8');
  assert.ok(command.includes('WaitForExit(250)'));
  assert.ok(!command.includes('Stop-Process') && !command.includes('taskkill') && !command.includes('ExecutionPolicy'));
  assert.equal(f.launched[0][2].detached, false);
  assert.equal(f.launched[0][2].windowsHide, true);
  assert.match(f.launched[0][1].at(-1), /^start "" \/b /);
  assert(f.launched[0][1].at(-1).length < 8191);
});
test('canceling document close cancels installation even if the user quits later', async t => {
  const f = await fixture(t,{ answer:0 });
  await f.updater.check(true);
  await f.updater.cancelInstall();
  f.app.emit('quit');
  assert.equal(f.launched.length,1);
  assert.deepEqual((await fs.readdir(path.join(f.directory,'updates',sha256))).filter(name => name.endsWith('.json') || name.endsWith('.ready')), []);
  assert.equal(f.updater.getState().phase,'ready');
});
test('a successful short-lived console wrapper still waits for the helper acknowledgement', async t => {
  const f = await fixture(t, { answer: 0, helperMode: 'wrapperExit' });
  await f.updater.check(true);
  assert.equal(f.quitCalls(), 1); assert.equal(f.launched.length, 1);
  assert.equal(f.opened.length, 0);
});
test('deferring an update never quits the app or launches an installer', async t => {
  const f = await fixture(t,{ answer:1 });
  await f.updater.check(true);
  f.app.emit('quit');
  assert.equal(f.quitCalls(),0); assert.equal(f.launched.length,0);
});
for (const helperMode of ['error', 'exit', 'timeout']) test('helper startup ' + helperMode + ' keeps the editor open and offers the cached installer', async t => {
  const f = await fixture(t, { answer: 0, helperMode });
  await f.updater.check(true);
  assert.equal(f.quitCalls(), 0);
  assert.equal(f.updater.getState().phase, 'ready');
  assert.equal(f.opened.length, 1); assert.match(f.opened[0], /setup\.exe$/);
  assert(f.dialogs.some(dialog => dialog.message === '업데이트를 완료하지 못했습니다.' && dialog.buttons.includes('설치 파일 보기')));
  assert.match(await fs.readFile(path.join(f.directory, 'updates/last-install.log'), 'utf8'), /Helper startup failed:/);
  assert.deepEqual((await fs.readdir(path.join(f.directory,'updates',sha256))).filter(name => name.endsWith('.json')), []);
});
test('portable Windows apps offer Setup without overwriting their running folder', async t => {
  const f = await fixture(t,{ answer:0 });
  await fs.rm(path.join(f.directory,'Uninstall Word Studio.exe'));
  await f.updater.check(true);
  f.app.emit('quit');
  assert.equal(f.quitCalls(),0); assert.equal(f.launched.length,0); assert.equal(f.opened.length,1);
});
test('a prepared update is revalidated against the server before installation', async t => {
  let currentRelease = release();
  const f = await fixture(t,{ answer:0, fetchImpl: async url => url.includes('api.github.com') ? Response.json(currentRelease) : new Response(bytes) });
  await f.updater.check(false);
  assert.equal(f.updater.getState().phase,'ready');
  currentRelease = release('word','win32','x64','1.4.3');
  await f.updater.installNow();
  f.app.emit('quit');
  assert.equal(f.quitCalls(),0); assert.equal(f.launched.length,0);
});
test('Mac downloads the exact ZIP and offers manual replacement without closing the editor', async t => {
  const f = await fixture(t,{ platform:'darwin',answer:0 });
  await f.updater.check(true);
  assert.equal(f.opened.length,1); assert.ok(f.opened[0].endsWith('Word-Studio-1.4.4-mac-arm64.zip'));
  assert.equal(f.quitCalls(),0); assert.equal(f.launched.length,0);
  assert.ok(f.dialogs.some(d => d.detail.includes('직접 교체')));
  assert.ok(f.requests[0].endsWith('/releases?per_page=20'));
});
test('Linux uses the individual release page without any network/download/install automation', async t => {
  const f = await fixture(t,{ platform:'linux' });
  await f.updater.check(false); await f.updater.check(true);
  assert.equal(f.requests.length,0); assert.deepEqual(f.opened,['https://github.com/beaur8-prog/word-studio/releases/latest']);
  assert.equal(f.quitCalls(),0);
});
test('automatic update preference persists and concurrent checks share one request', async t => {
  const f = await fixture(t);
  await f.updater.setAutomatic(false);
  assert.equal(JSON.parse(await fs.readFile(path.join(f.directory,'updates/settings.json'),'utf8')).automatic,false);
  await Promise.all([f.updater.check(),f.updater.check()]);
  assert.equal(f.requests.filter(u => u.includes('api.github.com')).length,1);
});
test('PowerShell job paths are data, including apostrophes and command characters', () => {
  const command = Buffer.from(powershellCommand('param($JobFile)\nWrite-Output $JobFile',"C:\\O'Brien;$x\\job.json"),'base64').toString('utf16le');
  assert.ok(command.endsWith("-JobFile 'C:\\O''Brien;$x\\job.json'"));
  assert.throws(() => powershellCommand('x','bad\npath'));
});
test('each local app packages its pinned updater implementation', async t => {
  const root = path.resolve(__dirname,'..');
  const available = await Promise.all(['sheet','slide','word'].map(id => fs.access(path.join(root,'..',id+'-studio/electron')).then(() => true, () => false)));
  if (!available.every(Boolean)) return t.skip('App source checkouts are required for the workspace integration check.');
  const pins = JSON.parse(await fs.readFile(path.join(root,'updater/app-sources.json'),'utf8'));
  const lock = JSON.parse(await fs.readFile(path.join(root,'suite-lock.json'),'utf8'));
  for (const app of lock.apps) {
    const pin = pins.apps[app.id];
    assert.equal(pin.version, app.version); assert.equal(pin.sourceCommit, app.sourceCommit);
    for (const [file, digest] of Object.entries(pin.files)) {
      const source = await fs.readFile(path.join(root,'..',app.id+'-studio/electron',file),'utf8');
      assert.equal(createHash('sha256').update(source.replaceAll('\r\n','\n')).digest('hex'),digest,app.id+': '+file);
    }
  }
});
