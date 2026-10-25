'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {readLock,validateLock,preinstallScript}=require('../mac/common.cjs');
test('Mac releases have a separate tag and immutable Apple Silicon inputs',async()=>{
  const original=await readLock();
  for(const change of [l=>{l.tag='v'+l.version;},l=>{l.target='mac-x64';},l=>{l.apps[0].asset.filename='../app.zip';},l=>{l.apps[1].asset.url=l.apps[1].asset.url.replace('/download/v','/latest/download/v');},l=>{l.apps[2].asset.sha256='';}]){
    const lock=structuredClone(original);change(lock);assert.throws(()=>validateLock(lock));
  }
});
test('Mac installation blocks running editors and downgrades before writing payloads',async()=>{
  const lock=await readLock(),script=preinstallScript(lock.apps);
  assert.ok(script.includes('/usr/bin/pgrep -f'));
  assert.ok(script.includes('Save your work and close all three Studio apps'));
  for(const app of lock.apps)assert.ok(script.includes('/Applications/'+app.product+'.app/Contents/Info.plist'));
  assert.ok(script.includes('/usr/bin/awk'));
  assert.doesNotMatch(script,/killall|kill -|rm -|xattr|spctl/);
});
