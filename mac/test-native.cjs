'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const assert=require('node:assert/strict');
const {root,readLock,checkBundle,hashFile,run}=require('./common.cjs');
const { _electron,expect }=require(require.resolve('@playwright/test',{paths:[path.join(root,'../slide-studio')]}));
const {createHash}=require('node:crypto');
async function main(){
  const lock=await readLock(),release=path.join(root,'release/mac',lock.version);
  const build=JSON.parse(await fs.readFile(path.join(release,'BUILD-mac-arm64.json'),'utf8'));
  const setup=path.join(release,build.filename);
  assert.equal(await hashFile(setup),build.sha256);
  const work=await fs.mkdtemp(path.join(os.tmpdir(),'office-studio-mac-native-'));
  const report={target:lock.target,suiteVersion:lock.version,setupSHA256:build.sha256,passed:false,nativeInstall:false,apps:[]};
  const destination=path.join(root,'test-results/mac');await fs.mkdir(destination,{recursive:true});
  try{
    const expanded=path.join(work,'expanded');run('/usr/sbin/pkgutil',['--expand-full',setup,expanded]);
    const sources=JSON.parse(await fs.readFile(path.join(root,'mac/updater-sources.json'),'utf8'));
    const asar=require(require.resolve('@electron/asar',{paths:[path.join(root,'../slide-studio')]}));
    for(const metadata of lock.apps){
      const bundle=path.join(expanded,metadata.id+'.pkg/Payload/Applications',metadata.product+'.app');
      const info=checkBundle(bundle,metadata),archive=path.join(bundle,'Contents/Resources/app.asar');
      const actual=asar.extractFile(archive,'electron/studio-updater.cjs');
      const pinned=sources.apps[metadata.id];
      assert.equal(pinned.version,metadata.version);assert.equal(pinned.sourceCommit,metadata.sourceCommit);
      for(const [name,digest] of Object.entries(pinned.files))assert.equal(createHash('sha256').update(asar.extractFile(archive,'electron/'+name).toString('utf8').replaceAll('\r\n','\n')).digest('hex'),digest,'Packaged updater differs from pinned app source: '+name);
      assert.equal(JSON.parse(asar.extractFile(archive,'package.json')).version,metadata.version);
      assert.equal(await hashFile(archive),build.apps.find(a=>a.id===metadata.id).asarSHA256);
      const sourceRoot=process.env['OFFICE_STUDIO_'+metadata.id.toUpperCase()+'_SOURCE']||path.join(root,'..',metadata.id+'-studio');
      const sourceCommit=run('git',['-C',sourceRoot,'rev-parse','HEAD'],true).trim();
      assert.equal(sourceCommit,metadata.sourceCommit,'Source checkout differs from the pinned release commit');
      const sourceDirectory=path.join(sourceRoot,'dist');
      let rendererFiles=0;
      async function verifyTree(dir,relative='',prefix='dist'){
        for(const entry of await fs.readdir(dir,{withFileTypes:true})){
          const next=path.posix.join(relative,entry.name);
          if(entry.isDirectory())await verifyTree(path.join(dir,entry.name),next,prefix);
          else {assert.deepEqual(asar.extractFile(archive,prefix+'/'+next),await fs.readFile(path.join(dir,entry.name)),'Renderer differs: '+next);rendererFiles++;}
        }
      }
      await verifyTree(sourceDirectory);
      const frontendFiles=rendererFiles;
      await verifyTree(path.join(sourceRoot,'electron'),'','electron');
      const electronFiles=rendererFiles-frontendFiles;
      rendererFiles=frontendFiles;
      const profile=path.join(work,'profile-'+metadata.id);
      await fs.mkdir(profile);
      const sentinel=path.join(profile,'preserve-existing-user-data.txt'),preserved='보존할 문서와 설정 '+metadata.id;
      await fs.writeFile(sentinel,preserved);
      const env={...process.env,STUDIO_DISABLE_UPDATES:'1',SHEET_STUDIO_DATA:profile};
      delete env.ELECTRON_RUN_AS_NODE;delete env.SLIDE_STUDIO_DEV;delete env.WORD_STUDIO_DEV;
      const native=await _electron.launch({executablePath:path.join(bundle,'Contents/MacOS',metadata.product),args:['--user-data-dir='+profile],env,timeout:45000});
      let item;
      try{
        const page=await native.firstWindow();
        await page.locator({sheet:'.cell',slide:'.editable-slide',word:'.tiptap'}[metadata.id]).first().waitFor({timeout:45000});
        const state=await native.evaluate(({app,Menu})=>({packaged:app.isPackaged,version:app.getVersion(),profile:app.getPath('userData'),help:Menu.getApplicationMenu().items.find(i=>i.label==='도움말')?.submenu.items.map(i=>({label:i.label,checked:i.checked}))}));
        assert.equal(state.packaged,true);assert.equal(state.version,metadata.version);assert.equal(state.profile,profile);
        assert.ok(state.help.some(i=>i.label==='업데이트 자동 확인·다운로드'&&i.checked));
        await native.evaluate(({Menu})=>{const item=Menu.getApplicationMenu().items.find(i=>i.label==='도움말').submenu.items.find(i=>i.label==='업데이트 자동 확인·다운로드');item.click(item);});
        await expect.poll(async()=>{try{return JSON.parse(await fs.readFile(path.join(profile,'updates/settings.json'),'utf8')).automatic;}catch(e){if(e.code==='ENOENT')return null;throw e;}},{timeout:10000}).toBe(false);
        await page.screenshot({path:path.join(destination,metadata.id+'-startup.png')});
        item={id:metadata.id,...info,sourceCommit,nativeStartup:true,packaged:true,profileIsolated:true,updateMenu:true,disableUpdatesPersists:true,rendererFiles,electronFiles,rendererMatchesSource:true,electronMatchesSource:true,updaterSHA256:createHash('sha256').update(actual).digest('hex')};
      }finally{
        await native.evaluate(({dialog})=>{dialog.showMessageBoxSync=()=>1;dialog.showMessageBox=async()=>({response:1});}).catch(()=>{});
        await native.close();
      }
      assert.equal(await fs.readFile(sentinel,'utf8'),preserved);
      item.normalClose=true;item.profileSentinelPreserved=true;report.apps.push(item);
      console.log(metadata.product+' '+metadata.version+': packaged native startup, updater preference, source integrity and close verified');
    }
    report.passed=true;report.nativeStartup=true;
    build.checks.nativeStartup=true;build.checks.packagedRendererMatchesSource=true;build.checks.updatePreferences=true;
    await fs.writeFile(path.join(release,'BUILD-mac-arm64.json'),JSON.stringify(build,null,2)+'\n');
  }finally{
    await fs.writeFile(path.join(destination,'native-verification.json'),JSON.stringify(report,null,2)+'\n');
    await fs.rm(work,{recursive:true,force:true});
  }
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
