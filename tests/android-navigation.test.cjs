'use strict';
const {test}=require('node:test'), assert=require('node:assert/strict'), vm=require('node:vm');
const source=require('node:fs').readFileSync(require('node:path').join(__dirname,'../android/app/src/main/assets/studio-host.js'),'utf8');
function host(studio='slide') {
 const events=new EventTarget(), docEvents=new EventTarget(),messages=[],view={show:false,home:false};
 class KeyboardEvent extends Event {constructor(type,options){super(type,options);this.key=options.key;this.code=options.code;}}
 class Anchor {click(){}}
 const state={location:{origin:'https://appassets.androidplatform.net'},__studioHostConfig:{studio},OfficeNative:{postMessage:text=>messages.push(JSON.parse(text))},navigator:{},document:{readyState:'loading',documentElement:{dataset:{}},activeElement:null,addEventListener(){},dispatchEvent:docEvents.dispatchEvent.bind(docEvents),querySelector:s=>s==='.android-presentation'&&view.show?{}:s==='.android-home'&&view.home?{}:null},HTMLAnchorElement:Anchor,URL:{createObjectURL(){return 'blob:test';},revokeObjectURL(){}},Event,KeyboardEvent,crypto:require('node:crypto').webcrypto,addEventListener:events.addEventListener.bind(events),dispatchEvent:events.dispatchEvent.bind(events)};
 state.window=state;vm.runInNewContext(source,state);return {state,messages,view};
}
test('Escape while showing emits dedicated show-end only, even if a keyboard handler would swallow Escape',()=>{
 const {state,view,messages}=host();view.show=true;let ended=0,keys=0;
 state.addEventListener('slide-studio-android-end-presentation',event=>{ended++;event.preventDefault();view.show=false;});
 state.addEventListener('keydown',event=>{keys++;event.preventDefault();});
 assert.equal(state.OfficeStudioHost.escape(),true);assert.equal(ended,1);assert.equal(keys,0);assert.deepEqual(messages,[]);
});
test('native Back during presentation cannot become an Activity close decision',()=>{
 const {state,view,messages}=host();view.show=true;let back=0;
 state.addEventListener('slide-studio-android-back',()=>back++);
 state.addEventListener('slide-studio-android-end-presentation',e=>e.preventDefault());
 assert.equal(state.OfficeStudioHost.slideBack(),'handled');assert.equal(back,0);assert.deepEqual(messages,[]);
});
test('presentation dataset protects a transition with no mounted show view',()=>{
 const {state}=host();state.document.documentElement.dataset.studioPresenting='true';
 assert.equal(state.OfficeStudioHost.slideBack(),'handled');
});
test('no handler during viewer/editor startup is unavailable, never close',()=>{
 const {state,messages}=host();assert.equal(state.OfficeStudioHost.slideBack(),'unavailable');assert.deepEqual(messages,[]);
});
test('normal handled document Back does not request closing',()=>{
 const {state}=host();state.addEventListener('slide-studio-android-back',e=>e.preventDefault());
 assert.equal(state.OfficeStudioHost.slideBack(),'handled');
});
test('only explicit Back at an unhandled home screen returns close',()=>{
 const {state,view}=host();view.home=true;assert.equal(state.OfficeStudioHost.slideBack(),'close');
});
test('legacy show assets receive one keyboard Escape fallback without any native close',()=>{
 const {state,view,messages}=host();view.show=true;let count=0;
 state.addEventListener('keydown',e=>{count++;e.preventDefault();});
 assert.equal(state.OfficeStudioHost.escape(),true);assert.equal(count,1);assert.deepEqual(messages,[]);
});
for(const studio of ['sheet','word'])test(`${studio} does not receive Slide close-decision methods`,()=>{
 const {state}=host(studio);assert.equal(state.OfficeStudioHost.slideBack,undefined);assert.equal(state.OfficeStudioHost.endPresentation,undefined);
});
