const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/studio-host.js'), 'utf8');
function host(studio) {
  const calls = [], native = { postMessage(raw) { const call = JSON.parse(raw); calls.push(call); queueMicrotask(() => native.onmessage({ data: JSON.stringify({ id: call.id, result: true }) })); } };
  const window = { OfficeNative: native, __studioHostConfig: { studio }, dispatchEvent() {} };
  class Anchor { click() {} }
  const context = { window, OfficeNative: native, location: { origin: 'https://appassets.androidplatform.net' },
    crypto: { randomUUID }, navigator: {}, document: { readyState: 'loading', addEventListener() {} },
    HTMLAnchorElement: Anchor, URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} },
    Uint8Array, Event, DOMException, btoa, atob, alert() {}, queueMicrotask };
  vm.runInNewContext(source, context);
  return { window, calls };
}
test('Word host exposes acknowledged immersive mode and restores system bars', async () => {
  const { window, calls } = host('word');
  await window.wordAndroid.setImmersiveMode(true); await window.wordAndroid.setImmersiveMode(false);
  assert.deepEqual(calls.map(({ method, params }) => ({ method, params })), [
    { method: 'systemBars', params: { hidden: true } }, { method: 'systemBars', params: { hidden: false } },
  ]);
  assert.equal(window.Capacitor.isPluginAvailable('SystemBars'), true);
  assert.equal(window.Capacitor.isPluginAvailable('ScreenOrientation'), false);
});
test('Slide retains its existing system-bars and orientation capabilities', async () => {
  const { window, calls } = host('slide');
  assert.equal(window.wordAndroid.setImmersiveMode, undefined);
  await window.Capacitor.registerPlugin('SystemBars').hide();
  await window.Capacitor.registerPlugin('ScreenOrientation').lock({ orientation: 'landscape' });
  assert.deepEqual(calls.map(call => call.method), ['systemBars', 'orientation']);
});
test('Sheet does not gain Word immersive or Slide orientation controls', () => {
  const { window } = host('sheet');
  assert.equal(window.wordAndroid.setImmersiveMode, undefined);
  assert.equal(window.Capacitor.isPluginAvailable('SystemBars'), false);
  assert.equal(window.Capacitor.isPluginAvailable('ScreenOrientation'), false);
});
