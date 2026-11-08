'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../android/app/src/main/assets/studio-host.js'),'utf8');
function host(studio='slide') {
 const events=new EventTarget(),messages=[];
 class KeyboardEvent extends Event {constructor(type,options){super(type,options);this.key=options.key;this.code=options.code;}}
 class Anchor {click(){}}
 const state={location:{origin:'https://appassets.androidplatform.net'},__studioHostConfig:{studio},OfficeNative:{postMessage:text=>messages.push(JSON.parse(text))},navigator:{},document:{readyState:'loading',addEventListener(){}},HTMLAnchorElement:Anchor,URL:{createObjectURL(){return 'blob:test';},revokeObjectURL(){}},Event,KeyboardEvent,crypto:require('node:crypto').webcrypto,addEventListener:events.addEventListener.bind(events),dispatchEvent:events.dispatchEvent.bind(events)};
 state.window=state;
 vm.runInNewContext(source,state);
 return {state,messages};
}
test('Slide Escape emits a cancelable keydown once and never requests close/back',()=>{
 const {state,messages}=host();let count=0;
 state.addEventListener('keydown',event=>{count++;assert.equal(event.key,'Escape');assert.equal(event.code,'Escape');assert.equal(event.cancelable,true);event.preventDefault();});
 assert.equal(state.OfficeStudioHost.escape(),true);
 assert.equal(count,1);
 assert.deepEqual(messages,[]);
});
test('unhandled Escape cannot fall back to closing the Android activity',()=>{
 const {state,messages}=host();
 assert.equal(state.OfficeStudioHost.escape(),false);
 assert.deepEqual(messages,[]);
});
for(const studio of ['sheet','word']) test(`${studio} keeps its existing back/keyboard handling`,()=>{
 const {state}=host(studio);
 assert.equal(state.OfficeStudioHost.escape,undefined);
 assert.equal(typeof state.OfficeStudioHost.back,'function');
});
