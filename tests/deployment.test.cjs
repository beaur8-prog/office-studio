'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { createHash } = require('node:crypto');
const { readLock, validateLock, verifyAsset, targetArg, nsisQuote } = require('../scripts/common.cjs');
const { sections } = require('../scripts/windows.cjs');

test('lock rejects moving release URLs, missing apps and injected metadata', async () => {
  const original = await readLock();
  for (const modify of [
    lock => { lock.apps[0].assets['win-x64'].url = 'https://github.com/beaur8-prog/sheet-studio/releases/latest/download/setup.exe'; },
    lock => { lock.apps[0].assets['win-x64'].filename = '../setup.exe'; },
    lock => { lock.apps[0].version = '0.7.20\nExec malware'; },
    lock => { lock.apps[1] = lock.apps[0]; },
    lock => { lock.apps.pop(); },
    lock => { lock.apps[0].bundleId = 'com.other.app'; },
  ]) {
    const lock = structuredClone(original);
    modify(lock);
    assert.throws(() => validateLock(lock));
  }
});
test('payload corruption fails closed, including same-size edits', async () => {
  const work = await fs.mkdtemp(path.join(os.tmpdir(), 'suite-test-'));
  try {
    const file = path.join(work, 'asset');
    const bytes = Buffer.from('verified payload');
    const asset = { filename: 'test.exe', bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
    await fs.writeFile(file, bytes);
    await verifyAsset(file, asset);
    await fs.writeFile(file, 'modified payload');
    await assert.rejects(verifyAsset(file, asset), /checksum mismatch/);
    await fs.writeFile(file, 'truncated');
    await assert.rejects(verifyAsset(file, asset), /checksum mismatch/);
  } finally { await fs.rm(work, { recursive: true, force: true }); }
});
test('NSIS input paths escape variable interpolation and quotes', () => {
  assert.equal(nsisQuote('C:\\$TEMP\\a"b.exe'), '"C:\\$$TEMP\\a$\\"b.exe"');
  assert.throws(() => nsisQuote('x\nExec y'));
});
test('Windows chain stops on child failure and does not launch installed apps', async () => {
  const lock = await readLock();
  const script = sections(lock.apps.map(app => ({ ...app, file: 'C:\\inputs\\' + app.id + '.exe' })));
  assert.equal((script.match(/ExecWait/g) || []).length, 3);
  assert.equal((script.match(/IfErrors execution_failed/g) || []).length, 3);
  assert.equal((script.match(/SetErrorLevel 2\n    Quit/g) || []).length, 3);
  assert.equal((script.match(/SetRebootFlag true/g) || []).length, 3);
  assert.ok(script.includes('sheet-setup.exe" /S /currentuser'));
  assert.ok(script.includes('slide-setup.exe" /S\''));
  assert.ok(!script.includes('taskkill') && !script.includes('RMDir') && !script.includes('/force-run'));
});
test('integrated release accepts Windows-only inputs and rejects Mac/Linux targets or assets', async () => {
  const lock = await readLock();
  assert.equal(validateLock(lock).target, 'win-x64');
  assert.equal(targetArg('win-x64'), 'win-x64');
  for (const target of ['mac-arm64','mac-x64','linux-x64']) {
    assert.throws(() => targetArg(target), /win-x64 only/);
    const mixed = structuredClone(lock);
    mixed.apps[0].assets[target] = structuredClone(mixed.apps[0].assets['win-x64']);
    assert.throws(() => validateLock(mixed), /separate app releases/);
  }
  assert.throws(() => validateLock({ ...lock,target:'mac-arm64' }), /Windows x64 only/);
});
