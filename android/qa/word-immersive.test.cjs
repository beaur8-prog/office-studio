const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/studio-host.js'), 'utf8');
function host(studio) {
  const calls = [], native = { postMessage(raw) { const call = JSON.parse(raw); calls.push(call); const result=call.method==='begin'?{token:'qa-print-token'}:call.method==='wordPrint'?{status:'closed'}:true;queueMicrotask(() => native.onmessage({ data: JSON.stringify({ id: call.id, result }) })); } };
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
test('Word print streams immutable HTML in ordered chunks and carries paper dimensions', async () => {
  const { window, calls }=host('word');
  const data=Uint8Array.from({length:70000},(_,i)=>i%256);
  assert.equal(await window.wordAndroid.printDocument({name:'문서.html',type:'text/html',data,pageWidthMils:8270,pageHeightMils:11690,pages:3}),'closed');
  assert.equal(calls[0].method,'begin');assert.equal(calls[0].params.mime,'text/html');
  const chunks=calls.filter(c=>c.method==='append');assert.deepEqual(chunks.map(c=>c.params.offset),[0,24576,49152]);
  assert.deepEqual(Buffer.concat(chunks.map(c=>Buffer.from(c.params.chunk,'base64'))),Buffer.from(data));
  assert.deepEqual(calls.at(-1),{id:calls.at(-1).id,method:'wordPrint',params:{pageWidthMils:8270,pageHeightMils:11690,pages:3,token:'qa-print-token'}});
});
test('Word document printing does not change Slide printing or add it to Sheet', async () => {
  const slide=host('slide'),sheet=host('sheet');
  assert.equal(slide.window.wordAndroid.printDocument,undefined);assert.equal(sheet.window.wordAndroid.printDocument,undefined);
  await slide.window.slideStudioAndroid.print();assert.equal(slide.calls.at(-1).method,'print');
});
