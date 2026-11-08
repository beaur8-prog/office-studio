'use strict';
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),sources=path.dirname(root);
async function main(){
 const sharp=require(path.join(sources,'slide-studio/node_modules/sharp'));
 const selected=process.argv.slice(2).length?process.argv.slice(2):['sheet','slide','word'];
 if(selected.some(id=>!['sheet','slide','word'].includes(id)))throw Error('Unknown Android app');
 const lock=JSON.parse(await fs.readFile(path.join(root,'android/source-lock.json'),'utf8'));
 if(selected.includes('sheet')){const branding=require(path.join(sources,'sheet-studio/scripts/branding.cjs'));await branding.createIcon(path.join(root,'android/generated-icons/sheet'));}
 const artwork={sheet:path.join(root,'android/generated-icons/sheet/app.png'),slide:path.join(sources,'slide-studio/electron/assets/SlideStudio.png'),word:path.join(sources,'word-studio/electron/WordStudio.svg')};
 const colors={sheet:'#108a41',slide:'#ed713d',word:'#2c73cc'};
 const symbols={sheet:'M29,27h50v54H29z M32,39h44 M32,53h44 M32,67h44 M46,39v40 M61,39v40',slide:'M26,30h56v40H26z M54,70v15 M40,88l14-9 14,9 M35,42h20 M38,60v-9 M53,60V46 M68,60V39',word:'M33,25h30l13,13v45H33z M61,25v15h15 M41,49h27 M41,58h27 M41,67h20'};
 const manifest=path.join(root,'android/manifests/web-assets.json');const hashes=await fs.readFile(manifest,'utf8').then(JSON.parse,()=>({}));
 for(const id of selected){
  const version=lock.apps[id].version;
  const source=path.join(sources,id+'-studio'),flavor=path.join(root,'android/app/src',id),dist=path.join(source,'dist');
  const assets=path.join(flavor,'assets/public');await fs.rm(assets,{recursive:true,force:true});await fs.mkdir(assets,{recursive:true});await fs.cp(dist,assets,{recursive:true});
  let html=await fs.readFile(path.join(assets,'index.html'),'utf8');html=html.replace('<head>',`<head><script>window.__studioHostConfig={studio:${JSON.stringify(id)},version:${JSON.stringify(version)}};</script><script src="../studio-host.js"></script>`);
  await fs.writeFile(path.join(assets,'index.html'),html);
  const res=path.join(flavor,'res');await fs.mkdir(path.join(res,'values'),{recursive:true});await fs.writeFile(path.join(res,'values/colors.xml'),`<resources><color name="brand">${colors[id]}</color></resources>\n`);
  for(const [density,size] of Object.entries({mdpi:48,hdpi:72,xhdpi:96,xxhdpi:144,xxxhdpi:192})){
   const folder=path.join(res,'mipmap-'+density);await fs.mkdir(folder,{recursive:true});await sharp(artwork[id]).resize(size,size).png().toFile(path.join(folder,'ic_launcher.png'));await fs.copyFile(path.join(folder,'ic_launcher.png'),path.join(folder,'ic_launcher_round.png'));
  }
  const draw=path.join(res,'drawable');await fs.mkdir(draw,{recursive:true});const mark=await sharp(artwork[id]).resize(288,288).png().toBuffer();await sharp({create:{width:432,height:432,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:mark,left:72,top:72}]).png().toFile(path.join(draw,'ic_launcher_foreground.png'));
  await fs.writeFile(path.join(draw,'ic_launcher_monochrome.xml'),`<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:pathData="${symbols[id]}" android:strokeColor="#FFFFFFFF" android:strokeWidth="4" android:fillColor="#00000000" android:strokeLineJoin="round"/></vector>`);
  const folder=path.join(res,'mipmap-anydpi-v26');await fs.mkdir(folder,{recursive:true});const adaptive='<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/brand"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_monochrome"/></adaptive-icon>\n';for(const name of ['ic_launcher.xml','ic_launcher_round.xml'])await fs.writeFile(path.join(folder,name),adaptive);
  const files=[];async function visit(dir,prefix=''){for(const e of (await fs.readdir(dir,{withFileTypes:true})).sort((a,b)=>a.name.localeCompare(b.name))){const next=path.posix.join(prefix,e.name);if(e.isDirectory())await visit(path.join(dir,e.name),next);else{const b=await fs.readFile(path.join(dir,e.name));files.push({path:next,bytes:b.length,sha256:crypto.createHash('sha256').update(b).digest('hex')});}}}await visit(assets);hashes[id]={version,assetFiles:files,iconSourceSHA256:crypto.createHash('sha256').update(await fs.readFile(artwork[id])).digest('hex'),launcherDensities:5,adaptiveIcon:true,roundIcon:true,monochromeIcon:true};
 }
 await fs.mkdir(path.join(root,'android/manifests'),{recursive:true});await fs.writeFile(path.join(root,'android/manifests/web-assets.json'),JSON.stringify(hashes,null,2)+'\n');console.log('Packaged offline web assets and icons for: '+selected.join(', '));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
