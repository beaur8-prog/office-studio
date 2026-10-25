'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { root,readLock,verifiedInputs,checkBundle,preinstallScript,hashFile,xml,run } = require('./common.cjs');
async function plist(file,value) {
  const json = file + '.json';
  await fs.writeFile(json,JSON.stringify(value));
  run('/usr/bin/plutil',['-convert','xml1','-o',file,json]);
}
function fixedComponents(items) {
  return items.map(item => ({ ...item,BundleIsRelocatable:false,BundleHasStrictIdentifier:true,BundleIsVersionChecked:true,BundleOverwriteAction:'upgrade',
    ...(item.ChildBundles ? { ChildBundles:fixedComponents(item.ChildBundles) } : {}) }));
}
async function main() {
  if (process.platform !== 'darwin' || process.arch !== 'arm64') throw Error('Build on an Apple Silicon Mac');
  const lock = await readLock(), inputs = await verifiedInputs(lock);
  const work = await fs.mkdtemp(path.join(os.tmpdir(),'office-studio-mac-'));
  try {
    const packages=path.join(work,'packages'),resources=path.join(work,'resources'),scripts=path.join(work,'scripts');
    await Promise.all([packages,resources,scripts].map(p=>fs.mkdir(p)));
    await fs.writeFile(path.join(scripts,'preinstall'),preinstallScript(inputs),{mode:0o755});
    const preflight=path.join(packages,'preflight.pkg');
    run('/usr/bin/pkgbuild',['--nopayload','--scripts',scripts,'--identifier','local.officestudio.mac.preflight','--version',lock.version,preflight]);
    const packageFiles=[preflight],sourceHashes={};
    for(const app of inputs) {
      const extracted=path.join(work,'extracted-'+app.id),payload=path.join(work,'payload-'+app.id);
      const entries=run('/usr/bin/unzip',['-Z1',app.file],true).trim().split('\n');
      if(entries.some(n=>!n.startsWith(app.product+'.app/')||n.split('/').includes('..')||n.includes('\\')))throw Error('Unexpected Mac archive entry: '+app.product);
      run('/usr/bin/ditto',['-x','-k',app.file,extracted]);
      const original=path.join(extracted,app.product+'.app');
      checkBundle(original,app);
      sourceHashes[app.id]=await hashFile(path.join(original,'Contents/Resources/app.asar'));
      await fs.mkdir(path.join(payload,'Applications'),{recursive:true});
      await fs.rename(original,path.join(payload,'Applications',app.product+'.app'));
      const components=path.join(work,app.id+'-components.plist');
      run('/usr/bin/pkgbuild',['--analyze','--root',payload,components]);
      await plist(components,fixedComponents(JSON.parse(run('/usr/bin/plutil',['-convert','json','-o','-',components],true))));
      const file=path.join(packages,app.id+'.pkg');
      run('/usr/bin/pkgbuild',['--root',payload,'--component-plist',components,'--install-location','/','--identifier','local.officestudio.mac.app.'+app.id,'--version',app.version,'--ownership','recommended',file]);
      packageFiles.push(file);
    }
    const requirements=path.join(work,'requirements.plist');
    await plist(requirements,{arch:['arm64'],os:['12.0'],home:false});
    const distribution=path.join(work,'Distribution.xml');
    run('/usr/bin/productbuild',['--synthesize','--product',requirements,...packageFiles.flatMap(file=>['--package',file]),distribution]);
    let document=await fs.readFile(distribution,'utf8');
    document=document.replace(/<options\b[^>]*\/>/,'<options customize="always" require-scripts="true" hostArchitectures="arm64"/>');
    document=document.replace(/<installer-gui-script\b[^>]*>/,m=>m+'\n<title>Office Studio '+xml(lock.version)+'</title>\n<welcome file="Welcome.html"/>\n<domains enable_localSystem="true" enable_currentUserHome="false" enable_anywhere="false"/>');
    document=document.replace(/<choice id="local\.officestudio\.mac\.preflight"[^>]*>/,'<choice id="local.officestudio.mac.preflight" visible="false" selected="true" enabled="false">');
    for(const app of inputs) {
      const id='local.officestudio.mac.app.'+app.id;
      document=document.replace(new RegExp('<choice id="'+id.replaceAll('.','\\.')+'"[^>]*>'),`<choice id="${id}" visible="true" start_selected="true" title="${xml(app.product+' '+app.version)}" description="응용 프로그램 폴더에 ${xml(app.product)}를 설치합니다.">`);
    }
    await fs.writeFile(distribution,document);
    await fs.writeFile(path.join(resources,'Welcome.html'),'<!doctype html><html lang="ko"><meta charset="utf-8"><style>body{font:14px -apple-system,sans-serif;line-height:1.65;padding:20px}h1{font-size:24px}</style><h1>오피스 스튜디오 · Mac</h1><p>Sheet Studio · Slide Studio · Word Studio를 한 번에 설치합니다.</p><ul>'+inputs.map(a=>`<li>${xml(a.product)} ${xml(a.version)}</li>`).join('')+'</ul><p>사용자화에서 설치할 앱을 선택할 수 있으며 기본값은 세 앱 모두입니다.</p><p>먼저 문서를 저장하고 세 앱을 모두 종료하세요. /Applications에 설치하며 관리자 인증이 필요합니다. 더 최신 버전의 앱이 있으면 설치를 중단합니다. 문서와 사용자 설정은 설치 대상에 포함하지 않습니다.</p><p>Apple Silicon(M1 이상) · macOS 12 이상. Intel Mac에서는 실행할 수 없습니다.</p><p>Apple Developer ID 서명·공증을 받지 않은 배포본입니다. 앱 업데이트는 자동 확인·다운로드 후 ZIP을 풀어 직접 교체합니다.</p></html>');
    const filename=`Office-Studio-${lock.version}-mac-arm64-Setup.pkg`,file=path.join(work,filename);
    run('/usr/bin/productbuild',['--distribution',distribution,'--package-path',packages,'--resources',resources,file]);
    const expanded=path.join(work,'verified');
    run('/usr/sbin/pkgutil',['--expand-full',file,expanded]);
    for(const app of inputs) {
      const bundle=path.join(expanded,app.id+'.pkg/Payload/Applications',app.product+'.app');
      checkBundle(bundle,app);
      if(await hashFile(path.join(bundle,'Contents/Resources/app.asar'))!==sourceHashes[app.id])throw Error('Packaged Mac ASAR differs: '+app.id);
    }
    const choices=run('/usr/sbin/installer',['-pkg',file,'-target','/','-showChoicesXML'],true);
    for(const app of inputs)if(!choices.includes('local.officestudio.mac.app.'+app.id)||!choices.includes(app.product))throw Error('Mac install choice missing');
    const output=path.join(root,'release','mac',lock.version);await fs.mkdir(output,{recursive:true});
    const sha256=await hashFile(file),bytes=(await fs.stat(file)).size;
    const report={product:lock.product,version:lock.version,target:lock.target,tag:lock.tag,builtAt:new Date().toISOString(),filename,bytes,sha256,
      signing:'installer unsigned; apps ad-hoc signed; not Apple notarized',apps:inputs.map(({file,...app})=>({...app,asarSHA256:sourceHashes[app.id]})),
      checks:{inputSHA256:true,bundleVersions:true,appleSilicon:true,originalCodeSignatures:true,expandedPayloadSHA256:true,installerChoices:true,nativeInstall:false}};
    await fs.copyFile(file,path.join(output,filename));
    await fs.writeFile(path.join(output,'BUILD-mac-arm64.json'),JSON.stringify(report,null,2)+'\n');
    await fs.writeFile(path.join(output,filename+'.sha256'),sha256+'  '+filename+'\n');
    await fs.writeFile(path.join(output,'installer-choices-mac-arm64.xml'),choices);
    console.log(JSON.stringify({output:path.join(output,filename),bytes,sha256,checks:report.checks},null,2));
  }finally{await fs.rm(work,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
