'use strict';
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
(async()=>{
 const root=path.resolve(__dirname,'../..'),source=path.resolve(process.argv[2]||''),lock=JSON.parse(await fs.readFile(path.join(root,'android/source-lock.json'),'utf8'));
 const commit=execFileSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'}).trim();if(commit!==lock.apps.sheet.sourceCommit)throw Error('Sheet source does not match source-lock.json');
 const dest=path.join(root,'android/app/src/sheet/assets/public');await fs.rm(dest,{recursive:true,force:true});await fs.mkdir(dest,{recursive:true});await fs.cp(path.join(source,'dist'),dest,{recursive:true});
 const html=path.join(dest,'index.html');await fs.writeFile(html,(await fs.readFile(html,'utf8')).replace('<head>',`<head><script>window.__studioHostConfig={studio:'sheet',version:${JSON.stringify(lock.apps.sheet.version)}};</script><script src="../studio-host.js"></script>`));
 const files=[];async function walk(dir,base=''){for(const e of (await fs.readdir(dir,{withFileTypes:true})).sort((a,b)=>a.name.localeCompare(b.name))){const name=path.posix.join(base,e.name),file=path.join(dir,e.name);if(e.isDirectory())await walk(file,name);else{const b=await fs.readFile(file);files.push({path:name,bytes:b.length,sha256:crypto.createHash('sha256').update(b).digest('hex')});}}}await walk(dest);
 const manifest=path.join(root,'android/manifests/web-assets.json'),hashes=JSON.parse(await fs.readFile(manifest,'utf8'));hashes.sheet={...hashes.sheet,version:lock.apps.sheet.version,sourceCommit:commit,assetFiles:files};await fs.writeFile(manifest,JSON.stringify(hashes,null,2)+'\n');console.log('Prepared Sheet web assets at locked commit '+commit);
})().catch(error=>{console.error(error.message);process.exitCode=1;});
