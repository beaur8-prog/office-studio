(() => {
 'use strict';
 if (location.origin !== 'https://appassets.androidplatform.net' || !window.OfficeNative) return;
 const limit=128*1024*1024, pending=new Map(), listeners=new Set(), backListeners=new Set(), incoming=[];
 const request=(method,params={})=>new Promise((resolve,reject)=>{const id=crypto.randomUUID();pending.set(id,{resolve,reject});OfficeNative.postMessage(JSON.stringify({id,method,params}));});
 OfficeNative.onmessage=event=>{try{const message=JSON.parse(event.data),item=pending.get(message.id);if(!item)return;pending.delete(message.id);message.error?item.reject(new Error(message.error)):item.resolve(message.result);}catch{}};
 const encode=bytes=>btoa(String.fromCharCode(...bytes));
 async function readFile(meta){
  if(!meta||meta.cancelled)return null;
  if(!Number.isSafeInteger(meta.bytes)||meta.bytes<0||meta.bytes>limit)throw new Error('Android 파일은 128 MiB 이하여야 합니다');
  const bytes=new Uint8Array(meta.bytes);
  try{let offset=0;while(offset<bytes.length){const reply=await request('read',{token:meta.token,offset}),text=atob(reply.chunk);if(reply.offset!==offset||!text.length||offset+text.length>bytes.length)throw new Error('파일 내용을 끝까지 읽지 못했습니다');for(let i=0;i<text.length;i++)bytes[offset+i]=text.charCodeAt(i);offset+=text.length;}return {name:meta.name,mime:meta.mime,type:meta.mime,data:bytes,requestId:meta.requestId};}
  finally{await request('release',{token:meta.token}).catch(()=>{});}
 }
 async function output(file,method){
  const bytes=file.data instanceof Uint8Array?file.data:new Uint8Array(file.data);
  if(bytes.byteLength>limit)throw new Error('Android 파일은 128 MiB 이하여야 합니다');
  const transfer=await request('begin',{name:file.name,mime:file.mime||file.type||'application/octet-stream',bytes:bytes.byteLength});
  try{for(let offset=0;offset<bytes.length;offset+=24576)await request('append',{token:transfer.token,offset,chunk:encode(bytes.subarray(offset,offset+24576))});return await request(method,{token:transfer.token});}
  catch(error){await request('abort',{token:transfer.token}).catch(()=>{});throw error;}
 }
 const openFile=async()=>readFile(await request('open'));
 const subscribe=listener=>{listeners.add(listener);flushIncoming();return()=>listeners.delete(listener);};
 let importing=false;
 async function flushIncoming(){if(importing||!listeners.size||!incoming.length)return;importing=true;const meta=incoming.shift();try{const file=await readFile(meta);if(file)for(const receive of [...listeners])receive(file);}catch(error){alert(error.message);}finally{importing=false;if(incoming.length)void flushIncoming();}}
 const save=file=>output(file,'save'),share=file=>output(file,'share');
 const clipboard=async value=>request('clipboard',{text:typeof value==='string'?value:value.text||'',html:typeof value==='object'?value.html||'':''});
 const nativePlugins=window.__studioHostConfig?.studio==='slide'?{
  SystemBars:{hide:()=>request('systemBars',{hidden:true}),show:()=>request('systemBars',{hidden:false})},
  ScreenOrientation:{lock:({orientation})=>request('orientation',{orientation}),unlock:()=>request('orientation',{orientation:'unlock'})}
 }:{};
 window.Capacitor={getPlatform:()=> 'android',isNativePlatform:()=>true,Plugins:nativePlugins,isPluginAvailable:name=>Object.hasOwn(nativePlugins,name),registerPlugin:name=>nativePlugins[name]};
 window.wordAndroid={openFile,saveFile:async file=>(await save(file)).completed?'completed':'cancelled',shareFile:async file=>(await share(file)).completed?'completed':'cancelled',onOpenFile:subscribe,onBack:fn=>{backListeners.add(fn);return()=>backListeners.delete(fn);},close:()=>{void request('close');}};
 window.sheetStudioAndroid={protocolVersion:1,platform:'android',resumeSession:true,setFullscreen:options=>request('sheetFullscreen',options),closeApp:()=>request('close'),openFile,saveCopy:save,shareFile:share,writeClipboard:clipboard,onOpenFile:subscribe,takeRequestedFile:async()=>null};
 window.slideStudioAndroid={openFile,saveFile:async file=>(await save(file)).completed===true,shareFile:share,onOpenFile:subscribe,print:async()=>request('print')};
 Object.defineProperty(navigator,'canShare',{configurable:true,value:({files}={})=>!!files&&files.length===1&&files[0].size<=limit});
 Object.defineProperty(navigator,'share',{configurable:true,value:async({files})=>{if(!files||files.length!==1)throw new Error('한 번에 파일 하나를 공유할 수 있습니다');const file=files[0],result=await share({name:file.name,mime:file.type,data:new Uint8Array(await file.arrayBuffer())});if(!result.completed)throw new DOMException('공유를 취소했습니다','AbortError');}});
 try{Object.defineProperty(navigator,'clipboard',{configurable:true,value:{readText:async()=>request('readClipboard'),writeText:clipboard,write:async items=>{if(items?.length){const blob=await items[0].getType('text/plain');await clipboard(await blob.text());}}}});}catch{}
 window.OfficeStudioHost={request,receive(meta){if(incoming.length){void request('release',{token:meta.token});alert('열기 대기 중인 파일이 있습니다. 잠시 뒤 다시 열어 주세요.');return;}incoming.push(meta);void flushIncoming();},back(){if(backListeners.size){for(const back of [...backListeners])back();return true;}const names=['sheet-studio-android-back','slide-studio-android-back'];for(const name of names){const event=new Event(name,{cancelable:true});window.dispatchEvent(event);if(event.defaultPrevented)return true;}if(window.sheetStudio?.flushRecovery){void Promise.resolve(window.sheetStudio.flushRecovery()).then(()=>request('close')).catch(error=>alert('복구 저장에 실패했습니다. 사본 저장 후 다시 종료해 주세요. '+error.message));return true;}return false;}};
 // Escape is an editor/viewer command. Never translate it into Android Back.
 window.OfficeStudioHost.escape=()=>{
  const target=document.activeElement||document,event=new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true});
  target.dispatchEvent(event);return event.defaultPrevented;
 };
 // Existing recovery/original-download controls may create detached anchors.
 // Keep those bytes in the Android save picker instead of a WebView blob URL.
 const urls=new Map(),create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL),click=HTMLAnchorElement.prototype.click;
 URL.createObjectURL=blob=>{const url=create(blob);urls.set(url,blob);return url;};
 URL.revokeObjectURL=url=>{urls.delete(url);revoke(url);};
 HTMLAnchorElement.prototype.click=function(){const blob=urls.get(this.href);if(blob&&this.download){const name=this.download;void blob.arrayBuffer().then(data=>save({name,mime:blob.type,data:new Uint8Array(data)})).catch(error=>alert(error.message));return;}return click.call(this);};
 const ready=()=>{void request('ready').catch(error=>alert(error.message));};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
