'use strict';
// The share server owns persistent, immutable copies of these public releases.
// GitHub stays the release archive; APK buttons use same-origin attachments.
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto'),os=require('node:os');
const {execFileSync}=require('node:child_process');
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
async function matches(file,bytes,digest){try{const data=await fs.readFile(file);return data.length===bytes&&sha(data)===digest;}catch(error){if(error.code==='ENOENT')return false;throw error;}}
async function prepareDirectDownloads({root,build,content,baseURL,vaultRoot}){
 const cache=process.env.ANDROID_DOWNLOAD_CACHE||path.join(os.tmpdir(),'office-studio-android-download-cache');
 await fs.mkdir(cache,{recursive:true});
 const metadata=new Map(),files=[];
 function release(tag){if(!metadata.has(tag)){const data=JSON.parse(execFileSync('gh',['api','repos/beaur8-prog/office-studio/releases/tags/'+tag],{encoding:'utf8'}));if(data.draft||data.prerelease)throw Error('Release is not public and stable: '+tag);metadata.set(tag,data);}return metadata.get(tag);}
 async function asset(url,expected){
  const match=url.match(/^https:\/\/github\.com\/beaur8-prog\/office-studio\/releases\/download\/(android-v[0-9.]+)\/([a-zA-Z0-9_.-]+)$/);
  if(!match)throw Error('Unsupported public release URL');
  const entry=release(match[1]).assets.find(a=>a.name===match[2]);
  if(!entry||entry.state!=='uploaded'||entry.browser_download_url!==url||!/^sha256:[a-f0-9]{64}$/.test(entry.digest))throw Error('Public asset missing: '+match[2]);
  if(expected&&(entry.size!==expected.bytes||entry.digest!=='sha256:'+expected.sha256))throw Error('Public release asset differs: '+match[2]);
  const digest=entry.digest.slice(7),file=path.join(cache,entry.name);
  const installed=path.join(vaultRoot,'앱/attachments/office-studio-android',entry.name);
  if(await matches(installed,entry.size,digest))return {inputPath:installed,name:entry.name,bytes:entry.size,sha256:digest,sourceURL:url};
  if(!await matches(file,entry.size,digest)){
   const bytes=execFileSync('gh',['api','repos/beaur8-prog/office-studio/releases/assets/'+entry.id,'-H','Accept: application/octet-stream'],{maxBuffer:260*1024*1024});
   if(bytes.length!==entry.size||sha(bytes)!==digest)throw Error('Downloaded bytes differ: '+entry.name);
   await fs.writeFile(file,bytes,{mode:0o600});
  }
  return {inputPath:file,name:entry.name,bytes:entry.size,sha256:digest,sourceURL:url};
 }
 function add(entry,href,type){
  const resolved='앱/'+href,id=sha(resolved).slice(0,32);
  files.push({...entry,href,resolved,id,...(type?{type}:{})});
  return href;
 }
 const prefix='attachments/office-studio-android/';
 for(const app of build.apps){
  if(!/^[a-zA-Z0-9_.-]+\.apk$/.test(app.filename))throw Error('Invalid APK name');
  const href=prefix+app.filename,installed=path.join(vaultRoot,'앱',href);
  const input=await matches(installed,app.bytes,app.sha256)?{inputPath:installed,name:app.filename,bytes:app.bytes,sha256:app.sha256,sourceURL:app.downloadURL}:await asset(app.downloadURL,app);
  add(input,href);
  const lines=content.split('\n');let found=0;
  for(let i=0;i<lines.length;i++)if(lines[i].startsWith('| !['+app.name+' 아이콘]')){const parts=lines[i].split('|');parts[3]=' [APK 바로 받기]('+href+') ';lines[i]=parts.join('|');found++;}
  if(found!==1)throw Error('Download table changed: '+app.id);
  content=lines.join('\n');
  const iconURL='https://github.com/beaur8-prog/office-studio/releases/download/android-v1.0.0/'+app.id[0].toUpperCase()+app.id.slice(1)+'-Studio-Android-icon.png';
  const icon=await asset(iconURL);const iconHref=add(icon,prefix+icon.name,'image/png');
  content=content.replace(iconURL,iconHref);
 }
 for(const name of ['BUILD-android.json','SHA256SUMS-android.txt']){
  const file=path.join(root,'release/android',build.version,name),data=await fs.readFile(file);
  const url='https://github.com/beaur8-prog/office-studio/releases/download/android-v'+build.version+'/'+name;
  const published=release('android-v'+build.version).assets.find(a=>a.name===name);
  if(!published||published.size!==data.length||published.digest!=='sha256:'+sha(data))throw Error('Published metadata differs: '+name);
  const href=add({inputPath:file,name,bytes:data.length,sha256:sha(data),sourceURL:url},prefix+build.version+'/'+name);
  const label=name==='BUILD-android.json'?'빌드 구성·포함 소스':'SHA-256 확인 파일';
  const oldLink=new RegExp('\\['+label+'\\]\\([^)]*\\)');
  if(!oldLink.test(content))throw Error('Metadata download link missing: '+name);
  content=content.replace(oldLink,'['+label+']('+href+')');
 }
 content=content.replace('아래 GitHub 주소에서 받은 APK인지','이 페이지의 APK 바로 받기로 받은 파일인지');
 if(!content.includes('**APK 바로 받기**를 누르면 이 공유 페이지에서'))content=content.replace('## 1. Android 다운로드\n','## 1. Android 다운로드\n\n**APK 바로 받기**를 누르면 이 공유 페이지에서 설치 파일을 직접 내려받습니다.\n');
 for(const app of build.apps)if(!content.includes('[APK 바로 받기]('+prefix+app.filename+')'))throw Error('Direct APK link missing: '+app.id);
 const publicFiles=files.map(({inputPath,...file})=>({...file,url:baseURL+(file.type?'/asset/':'/download/')+file.id}));
 await fs.writeFile(path.join(root,'publication/android/DOWNLOADS.json'),JSON.stringify({schema:1,version:build.version,mode:'lithos-attachments',page:baseURL,files:publicFiles},null,2)+'\n');
 return {content,files};
}
module.exports={prepareDirectDownloads};
