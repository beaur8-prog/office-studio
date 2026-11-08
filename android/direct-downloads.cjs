'use strict';
// Android distribution uses local signed files and retained Lithos attachments.
// No release upload, API download or external asset lookup belongs in this path.
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
async function localFile(file,name,expected,source){
 let data;try{data=await fs.readFile(file);}catch(error){if(error.code==='ENOENT')return null;throw error;}
 if(expected&&(data.length!==expected.bytes||sha(data)!==expected.sha256))throw Error('Local attachment checksum differs: '+name);
 return {inputPath:file,name,bytes:data.length,sha256:sha(data),source};
}
async function prepareDirectDownloads({root,build,content,baseURL,vaultRoot}){
 const policy=JSON.parse(await fs.readFile(path.join(root,'android/distribution-policy.json'),'utf8'));
 if(policy.mode!=='lithos-local-attachments'||policy.publishGitHubRelease!==false||policy.uploadAPKsToGitHub!==false)throw Error('Android local-only distribution policy required');
 const files=[],prefix='attachments/office-studio-android/';
 function add(entry,href,type){
  const resolved='앱/'+href,id=sha(resolved).slice(0,32);
  files.push({...entry,href,resolved,id,...(type?{type}:{})});return href;
 }
 function replaceLink(label,href){
  const link=new RegExp('\\['+label+'\\]\\([^)]*\\)');
  if(!link.test(content))throw Error('Attachment link missing: '+label);
  content=content.replace(link,'['+label+']('+href+')');
 }
 for(const app of build.apps){
  if(!/^[a-zA-Z0-9_.-]+\.apk$/.test(app.filename))throw Error('Invalid APK name');
  const href=prefix+app.filename;
  const candidates=[
   [path.join(root,'release/android',build.version,app.filename),'local-release-file'],
   [path.join(vaultRoot,'앱',href),'retained-local-attachment'],
   ...(process.env.ANDROID_APK_INPUT_DIR?[[path.join(process.env.ANDROID_APK_INPUT_DIR,app.filename),'local-input-file']]:[]),
  ];
  let input;
  for(const [file,source]of candidates){input=await localFile(file,app.filename,app,source);if(input)break;}
  if(!input)throw Error('Local signed APK is missing: '+app.filename+'. Build it locally or provide ANDROID_APK_INPUT_DIR.');
  add(input,href);
  const lines=content.split('\n');let found=0;
  for(let i=0;i<lines.length;i++)if(lines[i].startsWith('| !['+app.name+' 아이콘]')){const parts=lines[i].split('|');parts[3]=' [APK 바로 받기]('+href+') ';lines[i]=parts.join('|');found++;}
  if(found!==1)throw Error('Download table changed: '+app.id);
  content=lines.join('\n');
  const name=app.id[0].toUpperCase()+app.id.slice(1)+'-Studio-Android-icon.png';
  const icon=await localFile(path.join(root,'android/distribution-assets',name),name,null,'local-icon');
  if(!icon)throw Error('Local icon missing: '+name);
  const iconHref=add(icon,prefix+name,'image/png');
  const image=new RegExp('!\\['+app.name+' 아이콘\\]\\([^)]*\\)');
  if(!image.test(content))throw Error('App icon link missing: '+app.id);
  content=content.replace(image,'!['+app.name+' 아이콘]('+iconHref+')');
 }
 for(const name of ['BUILD-android.json','SHA256SUMS-android.txt']){
  const entry=await localFile(path.join(root,'release/android',build.version,name),name,null,'local-build-metadata');
  if(!entry)throw Error('Local build metadata missing: '+name);
  const href=add(entry,prefix+build.version+'/'+name);
  replaceLink(name==='BUILD-android.json'?'빌드 구성·포함 소스':'SHA-256 확인 파일',href);
 }
 const basic=await localFile(path.join(root,'release/android',build.version,'BASIC-VERIFICATION.json'),'BASIC-VERIFICATION.json',null,'local-basic-verification');
 if(basic){const href=add(basic,prefix+build.version+'/BASIC-VERIFICATION.json');replaceLink('이번 기본 검증 기록',href);}
 const oldProof=await localFile(path.join(root,'android/distribution-assets/ANDROID-VERIFICATION-1.0.0.json'),'ANDROID-VERIFICATION.json',null,'local-historical-proof');
 if(oldProof){const href=add(oldProof,prefix+'1.0.0/ANDROID-VERIFICATION.json');replaceLink('1.0.0 배포 당시 검증 기록',href);}
 content=content.replace(/\s*·\s*\[GitHub 릴리스\]\([^)]*\)/g,'');
 content=content.replace('설치 파일은 GitHub에서도 받을 수 있습니다.','APK는 이 페이지의 로컬 첨부 파일로 제공합니다.');
 content=content.replace('아래 GitHub 주소에서 받은 APK인지','이 페이지의 APK 바로 받기로 받은 파일인지');
 if(!content.includes('**APK 바로 받기**를 누르면 이 공유 페이지에서'))content=content.replace('## 1. Android 다운로드\n','## 1. Android 다운로드\n\n**APK 바로 받기**를 누르면 이 공유 페이지에서 설치 파일을 직접 내려받습니다.\n');
 for(const app of build.apps)if(!content.includes('[APK 바로 받기]('+prefix+app.filename+')'))throw Error('Direct APK link missing: '+app.id);
 const publicFiles=files.map(({inputPath,...file})=>({...file,url:baseURL+(file.type?'/asset/':'/download/')+file.id}));
 await fs.writeFile(path.join(root,'publication/android/DOWNLOADS.json'),JSON.stringify({schema:1,version:build.version,mode:'lithos-attachments',releaseDistribution:'local-only',page:baseURL,files:publicFiles},null,2)+'\n');
 return {content,files};
}
module.exports={prepareDirectDownloads};
