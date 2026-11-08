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
 const policy=JSON.parse(await fs.readFile(path.join(root,'android/distribution-policy.json'),'utf8'));
 if(policy.mode!=='lithos-local-attachments'||policy.publishGitHubRelease!==false||policy.uploadAPKsToGitHub!==false)throw Error('Android local-only distribution policy required');
 const version=JSON.parse(await fs.readFile(path.join(root,'android/source-lock.json'),'utf8')).version;
 const e={pid,directory,vaultRoot,publish,directDownloads:true,source:'앱/오피스 스튜디오 Android 다운로드.md',title:'오피스 스튜디오 Android'};
 if(publish){
  e.update=process.argv.includes('--update');if(e.update){const previous=JSON.parse(await fs.readFile(path.join(root,'publication/android/PUBLISHED.json'),'utf8'));e.directDownloads=e.directDownloads||previous.downloadMode==='lithos-attachments';if(!e.directDownloads){try{const saved=JSON.parse(await fs.readFile(path.join(root,'publication/android/DOWNLOADS.json'),'utf8'));e.directDownloads=saved.mode==='lithos-attachments'&&saved.page===previous.url;}catch(error){if(error.code!=='ENOENT')throw error;}}e.previousHash=previous.contentSHA256;e.previousURL=previous.url;e.previousToken=new URL(previous.url).pathname.split('/').at(-1);}
  const build=JSON.parse(await fs.readFile(path.join(root,'release/android',version,'BUILD-android.json'),'utf8'));
  if(skipTests){if(build.tests?.status!=='skipped'||build.tests?.executed!==false||build.tests?.explicitlyAuthorized!==true)throw Error('Explicit no-test release record required');}
  else{const proof=JSON.parse(await fs.readFile(path.join(root,'release/android',version,'ANDROID-VERIFICATION.json'),'utf8'));if(!build.checks?.nativeDeviceTests||!proof.passed||proof.profiles.length!==2||proof.profiles.some(p=>!p.passed))throw Error('Android native verification is incomplete');}
  e.content=await fs.readFile(path.join(root,'publication/android/downloads-ready.md'),'utf8');e.contentHash=hash(e.content);
  if(!e.content.startsWith('> **Android 설치 안내')||!e.content.includes('# 오피스 스튜디오 Android'))throw Error('Android draft format changed');
  if(!e.update)throw Error('Local attachments require the existing Android share');
  const prepared=await require('./direct-downloads.cjs').prepareDirectDownloads({root,build,content:e.content,baseURL:e.previousURL,vaultRoot});
  e.content=prepared.content;e.contentHash=hash(e.content);e.directFiles=prepared.files;
  await fs.writeFile(path.join(root,'publication/android/downloads-ready.md'),e.content);
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
   let directRecord=null,previousDownloadSet=record?.downloadSet;
   if(e.directFiles){
    if(!record||!e.update)throw Error('Existing Android attachment record required');
    const prefix='attachments/office-studio-android/',set=crypto.randomBytes(16).toString('hex'),downloadDirectory=path.join(s.directory,'downloads',set);
    const downloads=Object.fromEntries(Object.entries(record.downloads||{}).filter(([href])=>!href.startsWith(prefix)));
    const images=Object.fromEntries(Object.entries(record.images||{}).filter(([href])=>!href.startsWith(prefix)));
    const assets=Object.fromEntries(Object.entries(record.assets||{}).filter(([id])=>Object.values(images).includes(id)));
    await fs.mkdir(downloadDirectory,{recursive:true,mode:0o700});
    const verified=async(file,entry)=>{const bytes=await fs.readFile(file);if(bytes.length!==entry.bytes||hash(bytes)!==entry.sha256)throw Error('Attachment bytes changed: '+entry.name);return bytes;};
    const immutableCopy=async(source,destination,entry)=>{
     try{await verified(destination,entry);return;}catch(error){if(error.code!=='ENOENT')throw error;}
     await fs.mkdir(path.dirname(destination),{recursive:true});
     await fs.link(source,destination).catch(async error=>{if(error.code!=='EXDEV')throw error;await fs.copyFile(source,destination,1);});
     await fs.chmod(destination,0o600);await verified(destination,entry);
    };
    try{
     for(const download of Object.values(downloads)){
      if(!/^[a-f0-9]{32}$/.test(download.id)||!/^[a-f0-9]{32}$/.test(previousDownloadSet||''))throw Error('Unrelated attachment identity changed');
      await fs.copyFile(path.join(s.directory,'downloads',previousDownloadSet,download.id),path.join(downloadDirectory,download.id),1);
     }
     let total=0;
     for(const entry of e.directFiles){
      if(!entry.href.startsWith(prefix)||entry.resolved!=='앱/'+entry.href||entry.id!==hash(entry.resolved).slice(0,32)||entry.bytes>250*1024*1024||(total+=entry.bytes)>500*1024*1024)throw Error('Invalid direct attachment');
      await verified(entry.inputPath,entry);
      const destination=path.resolve(v.root,entry.resolved);if(!destination.startsWith(path.resolve(v.root,'앱',prefix)+path.sep))throw Error('Attachment path escaped');
      await fs.mkdir(path.dirname(destination),{recursive:true});
      if(!(await fs.realpath(path.dirname(destination))).startsWith((await fs.realpath(v.root))+path.sep))throw Error('Attachment symlink escaped');
      await immutableCopy(entry.inputPath,destination,entry);
      if(entry.type){
       if(entry.type!=='image/png'||entry.bytes>8*1024*1024)throw Error('Invalid attachment image');
       images[entry.href]=entry.id;assets[entry.id]={type:entry.type,data:(await verified(destination,entry)).toString('base64')};
      }else{
       const snapshotFile=path.join(downloadDirectory,entry.id);
       await fs.copyFile(destination,snapshotFile,1);await fs.chmod(snapshotFile,0o600);await verified(snapshotFile,entry);
       downloads[entry.href]={id:entry.id,name:entry.name,size:entry.bytes};
      }
     }
     directRecord={downloads,images,assets,downloadSet:set,includeFiles:true};
    }catch(error){await fs.rm(downloadDirectory,{recursive:true,force:true});throw error;}
   }
   if(e.update&&(before!==e.content||directRecord)){if(before===null)throw Error('Existing Android note missing');await fs.writeFile(file,e.content);record={...record,...directRecord,title:e.title,content:e.content,updatedAt:Date.now()};await s.persist(record);}
   if(directRecord&&previousDownloadSet&&previousDownloadSet!==record.downloadSet&&![...s.records.values()].some(r=>r.downloadSet===previousDownloadSet))await s.removeDownloads(previousDownloadSet);
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
   return {title:item.title,url:item.url,source:e.source,contentSHA256:e.contentHash,pid:process.pid,port:s.port,tunnelPID,expiresAt:item.expiresAt,warnings:item.warnings,otherSharesPreserved:true,serverAndTunnelNotRestarted:true,privateVisitorTrackingEnabled:counter.tokens.has(record.token),existingVisitDataPreserved:existingCountsPreserved,directDownloads:Boolean(e.directFiles),downloadCount:new Set(Object.values(record.downloads||{}).map(d=>d.id)).size};
  }`})).value;
  await fs.mkdir(path.join(root,'test-results/android-lithos'),{recursive:true});
  await fs.writeFile(path.join(root,'test-results/android-lithos',publish?'maintenance-receipt.json':'ready.json'),JSON.stringify(result,null,2)+'\n');
  if(publish){await fs.mkdir(path.join(root,'publication/android'),{recursive:true});const publicRecord={version,title:result.title,url:result.url,source:result.source,contentSHA256:result.contentSHA256,readOnly:true,expiresAt:result.expiresAt,...(e.directFiles?{downloadMode:'lithos-attachments',distribution:'local-only'}:{})};await fs.writeFile(path.join(root,'publication/android/PUBLISHED.json'),JSON.stringify(publicRecord,null,2)+'\n');}
  console.log(JSON.stringify(result,null,2));
 }finally{
  if(owns){try{await call('Runtime.releaseObjectGroup',{objectGroup:'android-publication'});await call('Runtime.evaluate',{expression:"setTimeout(()=>process.getBuiltinModule('inspector').close(),200);'closing'",returnByValue:true});}finally{ws.close();}
   let closed=false;for(let i=0;i<40;i++){await delay(100);if(!await fetch('http://127.0.0.1:9229/json/list').then(()=>true,()=>false)){closed=true;break;}}if(!closed)throw Error('Inspector did not close');
  }else ws.close();
 }
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
