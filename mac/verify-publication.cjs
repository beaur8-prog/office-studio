'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const {root,readLock,hashFile}=require('./common.cjs');
const {selectMacRelease,selectRelease}=require('../updater/studio-updater.cjs');
async function getJSON(url){const r=await fetch(url,{headers:{Accept:'application/vnd.github+json','User-Agent':'Office-Studio-Mac-Verification'},signal:AbortSignal.timeout(25000)});assert.equal(r.status,200,url);return r.json();}
async function main(){
  const published=JSON.parse(await fs.readFile(path.join(root,'test-results/lithos-mac-published.json'),'utf8'));
  const expected=await fs.readFile(path.join(root,'publication/mac/downloads-ready.md'),'utf8');
  const lock=await readLock(),build=JSON.parse(await fs.readFile(path.join(root,'release/mac',lock.version,'BUILD-mac-arm64.json'),'utf8'));
  const data=await getJSON(published.url+'/data');
  assert.equal(data.content,expected);assert.equal(data.editingEnabled,false);assert.equal(data.expiresAt,null);
  assert.ok(expected.startsWith('> **Mac 설치 안내'));assert.ok(expected.includes('그래도 열기'));assert.ok(expected.includes('Apple Silicon'));
  assert.doesNotMatch(expected,/\.exe(?:\)|`)|\.deb(?:\)|`)|Windows 설치/);
  const urls=[...new Set([...expected.matchAll(/\]\((https:\/\/[^)]+)\)/g)].map(m=>m[1]))],links=[];
  for(let i=0;i<urls.length;i+=4)links.push(...await Promise.all(urls.slice(i,i+4).map(async url=>{const r=await fetch(url,{method:'HEAD',signal:AbortSignal.timeout(25000)});assert.equal(r.status,200,url);if(url.endsWith(build.filename))assert.equal(Number(r.headers.get('content-length')),build.bytes);return {url,status:r.status};})));
  assert.equal(await hashFile(path.join(root,'release/mac',lock.version,build.filename)),build.sha256);
  const previousMac=JSON.parse(await fs.readFile(path.join(root,'mac/previous-apps-lock.json'),'utf8'));
  const windows=JSON.parse(await fs.readFile(path.join(root,'suite-lock.json'),'utf8'));
  const previous=JSON.parse(await fs.readFile(path.join(root,'previous-apps-lock.json'),'utf8'));
  const updates=[];
  for(const app of lock.apps){
    const base='https://api.github.com/repos/beaur8-prog/'+app.id+'-studio/';
    const list=await getJSON(base+'releases?per_page=20');
    const candidate=selectMacRelease(list,{id:app.id,platform:'darwin',arch:'arm64',currentVersion:previousMac.apps.find(a=>a.id===app.id).version});
    assert.equal(candidate.version,app.version);assert.equal(candidate.sha256,app.asset.sha256);assert.equal(candidate.bytes,app.asset.bytes);
    const win=selectRelease(await getJSON(base+'releases/latest'),{id:app.id,platform:'win32',arch:'x64',currentVersion:previous.apps.find(a=>a.id===app.id).version});
    const baseline=windows.apps.find(a=>a.id===app.id);
    assert.equal(win.version,baseline.version);assert.equal(win.sha256,baseline.assets['win-x64'].sha256);
    updates.push({id:app.id,macVersion:candidate.version,macSHA256:candidate.sha256,windowsVersion:win.version});
  }
  const latest=await getJSON('https://api.github.com/repos/beaur8-prog/office-studio/releases/latest');assert.equal(latest.tag_name,'v'+windows.version);
  const result={url:published.url,title:data.title,bodyMatches:true,warningFirst:true,readOnly:true,installerSHA256:build.sha256,links,updates,windowsLatestPreserved:true};
  await fs.writeFile(path.join(root,'test-results/mac/publication-verified.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({url:result.url,bodyMatches:true,warningFirst:true,readOnly:true,verifiedLinks:links.length,macUpdateFeeds:updates.length,windowsLatestPreserved:true,installerSHA256:build.sha256},null,2));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
