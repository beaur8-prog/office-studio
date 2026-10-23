'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const {root,hashFile,readLock}=require('./common.cjs');
const {selectRelease}=require('../updater/studio-updater.cjs');
async function main(){
  const publication=JSON.parse(await fs.readFile(path.join(root,'test-results/lithos-published.json'),'utf8'));
  const expected=await fs.readFile(path.join(root,'publication/downloads-ready.md'),'utf8');
  const build=JSON.parse(await fs.readFile(path.join(root,'release/1.0.0/BUILD-win-x64.json'),'utf8'));
  const html=await fetch(publication.url,{signal:AbortSignal.timeout(20000)});
  assert.equal(html.status,200);assert.ok((await html.text()).includes('오피스 스튜디오'));
  const dataResponse=await fetch(publication.url+'/data',{signal:AbortSignal.timeout(20000)});
  assert.equal(dataResponse.status,200);
  const data=await dataResponse.json();
  assert.equal(data.content,expected);assert.equal(data.editingEnabled,false);assert.equal(data.expiresAt,null);
  assert.ok(data.content.startsWith('> **Windows 설치 안내'));
  assert.doesNotMatch(data.content, /\b(?:Mac|macOS|Linux|Apple Silicon)\b|\.deb\)|-mac(?:OS)?-/i, 'The Office Studio page must describe Windows distribution only');
  const urls=[...new Set([...expected.matchAll(/\]\((https:\/\/[^)]+)\)/g)].map(match=>match[1]))];
  const checks=[];
  for(let i=0;i<urls.length;i+=4){
    const items=await Promise.all(urls.slice(i,i+4).map(async url=>{
      const response=await fetch(url,{method:'HEAD',signal:AbortSignal.timeout(20000)});
      assert.equal(response.status,200,url);
      if(url.endsWith(build.filename))assert.equal(Number(response.headers.get('content-length')),build.bytes);
      return {url,status:response.status};
    }));checks.push(...items);
  }
  assert.equal(await hashFile(path.join(root,'release/1.0.0',build.filename)),build.sha256);
  const lock=await readLock(),previous=JSON.parse(await fs.readFile(path.join(root,'previous-apps-lock.json'),'utf8'));
  const updates=[];
  for(const app of lock.apps){
    const response=await fetch('https://api.github.com/repos/beaur8-prog/'+app.id+'-studio/releases/latest',{headers:{Accept:'application/vnd.github+json','User-Agent':'Office-Studio-Release-Verification'},signal:AbortSignal.timeout(20000)});
    assert.equal(response.status,200);
    const release=await response.json();
    const update=selectRelease(release,{id:app.id,currentVersion:previous.apps.find(old=>old.id===app.id).version,platform:'win32',arch:'x64'});
    assert.equal(update.version,app.version);assert.equal(update.sha256,app.assets['win-x64'].sha256);assert.equal(update.bytes,app.assets['win-x64'].bytes);
    updates.push({id:app.id,version:update.version,sha256:update.sha256});
  }
  const result={url:publication.url,title:data.title,warningFirst:true,bodyMatches:true,readOnly:true,links:checks,updates,installerSHA256:build.sha256};
  await fs.writeFile(path.join(root,'test-results/publication-verified.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({url:result.url,title:result.title,warningFirst:true,bodyMatches:true,readOnly:true,verifiedLinks:checks.length,verifiedUpdateFeeds:updates.length,installerSHA256:build.sha256},null,2));
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
