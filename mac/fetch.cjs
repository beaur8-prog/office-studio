'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {root,readLock}=require('./common.cjs');
const {verifyAsset}=require('../scripts/common.cjs');
async function main(){
  const lock=await readLock(),directory=path.join(root,'inputs','mac-arm64');await fs.mkdir(directory,{recursive:true});
  for(const app of lock.apps){
    const file=path.join(directory,app.asset.filename);
    try{await fs.access(file);await verifyAsset(file,app.asset);continue;}catch(e){if(e.code!=='ENOENT')throw e;}
    const response=await fetch(app.asset.url,{signal:AbortSignal.timeout(10*60*1000)});
    if(!response.ok||!response.body)throw Error('Mac asset download failed: '+app.id);
    const temporary=file+'.partial';
    try{
      const handle=await fs.open(temporary,'wx');let length=0;
      try{for await(const chunk of response.body){length+=chunk.length;if(length>app.asset.bytes)throw Error('Mac asset exceeds expected size');await handle.write(chunk);}}finally{await handle.close();}
      await verifyAsset(temporary,app.asset);await fs.rename(temporary,file);
    }finally{await fs.rm(temporary,{force:true});}
    console.log('Verified '+app.asset.filename);
  }
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
