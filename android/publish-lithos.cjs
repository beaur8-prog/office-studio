'use strict';
// Publish a new Android share through the existing Lithos backend. Existing
// desktop shares, tunnel, copy-link UI and private statistics remain in place.
const fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),pid=Number(process.env.LITHOS_PID||18533);
const directory=path.join(os.homedir(),'Library/Application Support/Lithos/shares');
const vaultRoot=path.join(os.homedir(),'Lithos Vault');
const modulePath='/Applications/Lithos.app/Contents/Resources/app.asar/electron/share-server.cjs';
const hash=x=>crypto.createHash('sha256').update(x).digest('hex');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function main(){
 const publish=process.argv.includes('--publish'),skipTests=process.argv.includes('--skip-tests');
 const version=JSON.parse(await fs.readFile(path.join(root,'android/source-lock.json'),'utf8')).version;
 const e={pid,directory,vaultRoot,publish,source:'앱/오피스 스튜디오 Android 다운로드.md',title:'오피스 스튜디오 Android'};
 if(publish){
  e.update=process.argv.includes('--update');if(e.update){const previous=JSON.parse(await fs.readFile(path.join(root,'publication/android/PUBLISHED.json'),'utf8'));e.previousHash=previous.contentSHA256;e.previousURL=previous.url;e.previousToken=new URL(previous.url).pathname.split('/').at(-1);}
  const build=JSON.parse(await fs.readFile(path.join(root,'release/android',version,'BUILD-android.json'),'utf8'));
  if(skipTests){if(build.tests?.status!=='skipped'||build.tests?.executed!==false||build.tests?.explicitlyAuthorized!==true)throw Error('Explicit no-test release record required');}
  else{const proof=JSON.parse(await fs.readFile(path.join(root,'release/android',version,'ANDROID-VERIFICATION.json'),'utf8'));if(!build.checks?.nativeDeviceTests||!proof.passed||proof.profiles.length!==2||proof.profiles.some(p=>!p.passed))throw Error('Android native verification is incomplete');}
  e.content=await fs.readFile(path.join(root,'publication/android/downloads-ready.md'),'utf8');e.contentHash=hash(e.content);
  if(!e.content.startsWith('> **Android 설치 안내')||!e.content.includes('# 오피스 스튜디오 Android'))throw Error('Android draft format changed');
  for(const app of build.apps){const response=await fetch(app.downloadURL||('https://github.com/beaur8-prog/office-studio/releases/download/android-v'+version+'/'+app.filename),{method:'HEAD'});if(!response.ok||Number(response.headers.get('content-length'))!==app.bytes)throw Error('Public APK download unavailable: '+app.id);}
 }
 if(execFileSync('ps',['-p',String(pid),'-o','command='],{encoding:'utf8'}).trim()!=='/Applications/Lithos.app/Contents/MacOS/Lithos')throw Error('Lithos process identity changed');
 if(await fetch('http://127.0.0.1:9229/json/list').then(()=>true,()=>false))throw Error('Existing inspector left untouched');
 process.kill(pid,'SIGUSR1');let targets;
 for(let i=0;i<50;i++){try{targets=await(await fetch('http://127.0.0.1:9229/json/list')).json();break;}catch{await delay(50);}}
 if(targets?.length!==1)throw Error('Ambiguous maintenance endpoint');
 const ws=new WebSocket(targets[0].webSocketDebuggerUrl),pending=new Map();let next=0,owns=false;
 ws.addEventListener('message',event=>{const m=JSON.parse(event.data),p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);});
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++next,timer=setTimeout(()=>{pending.delete(id);reject(Error('Maintenance timed out'));},45000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params}));});
 const checked=r=>{if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result;};
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
 try{
  if(checked(await call('Runtime.evaluate',{expression:'process.pid',returnByValue:true})).value!==pid)throw Error('Wrong process');owns=true;
  const prototype=checked(await call('Runtime.evaluate',{expression:`process.getBuiltinModule('module').createRequire(${JSON.stringify(modulePath)})(${JSON.stringify(modulePath)}).ShareServer.prototype`,objectGroup:'android-publication'}));
  const objects=await call('Runtime.queryObjects',{prototypeObjectId:prototype.objectId,objectGroup:'android-publication'});
  const result=checked(await call('Runtime.callFunctionOn',{objectId:objects.objects.objectId,arguments:[{value:e}],returnByValue:true,awaitPromise:true,functionDeclaration: String.raw`async function(e){
   const matches=this.filter(s=>s.directory===e.directory&&s.server&&s.tunnel.origin);
   if(matches.length!==1)throw Error('Ambiguous sharing backend');
   const s=matches[0],v=s.collaboration.getVault(),fs=process.getBuiltinModule('fs/promises'),path=process.getBuiltinModule('path'),crypto=process.getBuiltinModule('crypto'),hash=x=>crypto.createHash('sha256').update(x).digest('hex');
   if(v.root!==e.vaultRoot||process.pid!==e.pid)throw Error('Vault identity changed');
   const state=s.state(),counter=s.visitCounter,handler=s.handle,tunnelPID=s.tunnel.child.pid;
   if(!counter?.enabled)throw Error('Private tracking unavailable');
   const oldShares=[...s.records].filter(([token,r])=>r.source!==e.source).map(([token,r])=>[token,hash(JSON.stringify(r))]);
   if(!e.publish)return {pid:process.pid,port:s.port,origin:state.origin,tunnelPID,privateTrackingEnabled:counter.enabled,existing:state.shares.map(({title,source,url})=>({title,source,url})),createSource:s.create.toString(),persistSource:s.persist.toString()};
   if(hash(e.content)!==e.contentHash)throw Error('Draft changed');
   let record=[...s.records.values()].find(r=>r.source===e.source&&r.vaultId===v.id);
   if(e.update&&(!record||record.token!==e.previousToken||s.state().shares.find(r=>r.token===record.token)?.url!==e.previousURL))throw Error('Existing Android share identity changed');
   if(record&&(record.editing||(record.content!==e.content&&(!e.update||hash(record.content)!==e.previousHash))))throw Error('Existing Android share has different content; preserved');
   const file=path.join(v.root,e.source);let before=null;
   try{before=await fs.readFile(file,'utf8');}catch(error){if(error.code!=='ENOENT')throw error;}
   if(before!==null&&before!==e.content&&(!e.update||hash(before)!==e.previousHash))throw Error('Existing Android note differs; preserved');
   if(e.update&&before!==e.content){if(before===null)throw Error('Existing Android note missing');await fs.writeFile(file,e.content);record={...record,title:e.title,content:e.content,updatedAt:Date.now()};await s.persist(record);}
   if(before===null){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,e.content,{flag:'wx'});}
   if(!record){const now=Date.now();record={title:e.title,content:e.content,images:{},downloads:{},assets:{},warnings:[],includeFiles:false,downloadSet:null,token:crypto.randomBytes(24).toString('hex'),vaultId:v.id,source:e.source,createdAt:now,updatedAt:now,expiresAt:null};await s.persist(record);}
   await counter.flush();let existingCountsPreserved=false;
   counter.queue=counter.queue.then(async()=>{
    const oldPages=Object.entries(counter.data.pages).filter(([token])=>token!==record.token).map(([token,p])=>[token,hash(JSON.stringify(p))]);
    const salt=counter.data.salt,oldTokens=[...counter.tokens],now=new Date().toISOString();
    if(!counter.data.pages[record.token]){counter.data.pages[record.token]={title:e.title,startedAt:now,views:0,visitors:[],days:{},lastVisitAt:null};counter.enableAnalytics(now);counter.data.pages[record.token].analytics.startedAt=now;}
    counter.data.pages[record.token].title=e.title;counter.tokens.add(record.token);await counter.persist();
    const configFile=path.join(counter.directory,'config.json'),config=JSON.parse(await fs.readFile(configFile,'utf8'));
    if(config.version!==1||!config.pages||Array.isArray(config.pages))throw Error('Analytics configuration changed');
    config.pages[record.token]=e.title;const temporary=configFile+'.android-'+crypto.randomBytes(4).toString('hex');await fs.writeFile(temporary,JSON.stringify(config,null,2)+'\n',{mode:0o600});await fs.rename(temporary,configFile);
    for(const [token,before]of oldPages)if(hash(JSON.stringify(counter.data.pages[token]))!==before)throw Error('Existing visit data changed unexpectedly');
    if(counter.data.salt!==salt||oldTokens.some(t=>!counter.tokens.has(t)))throw Error('Tracking identity changed');
    existingCountsPreserved=true;
   });
   await counter.flush();
   const item=s.state().shares.find(r=>r.token===record.token);
   if(!item?.url||item.expired||item.expiresAt!==null||item.warnings.length||hash(s.records.get(record.token).content)!==e.contentHash)throw Error('New share verification failed');
   for(const [token,before]of oldShares)if(hash(JSON.stringify(s.records.get(token)))!==before)throw Error('Existing share changed');
   if(s.tunnel.child.pid!==tunnelPID||s.state().origin!==state.origin||s.visitCounter!==counter||s.handle!==handler)throw Error('Sharing service changed');
   return {title:item.title,url:item.url,source:e.source,contentSHA256:e.contentHash,pid:process.pid,port:s.port,tunnelPID,expiresAt:item.expiresAt,warnings:item.warnings,otherSharesPreserved:true,serverAndTunnelNotRestarted:true,privateVisitorTrackingEnabled:counter.tokens.has(record.token),existingVisitDataPreserved:existingCountsPreserved};
  }`})).value;
  await fs.mkdir(path.join(root,'test-results/android-lithos'),{recursive:true});
  await fs.writeFile(path.join(root,'test-results/android-lithos',publish?'maintenance-receipt.json':'ready.json'),JSON.stringify(result,null,2)+'\n');
  if(publish){await fs.mkdir(path.join(root,'publication/android'),{recursive:true});const publicRecord={version,title:result.title,url:result.url,source:result.source,contentSHA256:result.contentSHA256,readOnly:true,expiresAt:result.expiresAt};await fs.writeFile(path.join(root,'publication/android/PUBLISHED.json'),JSON.stringify(publicRecord,null,2)+'\n');}
  console.log(JSON.stringify(result,null,2));
 }finally{
  if(owns){try{await call('Runtime.releaseObjectGroup',{objectGroup:'android-publication'});await call('Runtime.evaluate',{expression:"setTimeout(()=>process.getBuiltinModule('inspector').close(),200);'closing'",returnByValue:true});}finally{ws.close();}
   let closed=false;for(let i=0;i<40;i++){await delay(100);if(!await fetch('http://127.0.0.1:9229/json/list').then(()=>true,()=>false)){closed=true;break;}}if(!closed)throw Error('Inspector did not close');
  }else ws.close();
 }
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
