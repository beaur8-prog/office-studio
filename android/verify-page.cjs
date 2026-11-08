'use strict';
const fs=require('node:fs/promises'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const {chromium}=require(path.join(root,'../word-studio/node_modules/@playwright/test'));
async function main(){
 const publication=JSON.parse(await fs.readFile(path.join(root,'publication/android/PUBLISHED.json'),'utf8'));
 const out=path.join(root,'test-results/android-page');await fs.mkdir(out,{recursive:true});
 const browser=await chromium.launch({channel:'chrome',headless:true});const results=[];
 try{
  for(const [profile,viewport]of [['phone',{width:390,height:844}],['desktop',{width:1280,height:900}]]){
   const context=await browser.newContext({viewport,userAgent:'Office-Studio-Release-Verification HeadlessChrome',permissions:['clipboard-read','clipboard-write']});
   const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
   const response=await page.goto(publication.url,{waitUntil:'networkidle',timeout:60000});
   if(!response.ok())throw Error('Page status '+response.status());
   await page.getByRole('heading',{name:'오피스 스튜디오 Android',exact:true}).last().waitFor();
   const images=page.locator('img[alt$="아이콘"]');if(await images.count()!==3)throw Error('Missing app icon previews');
   for(const image of await images.all()){await image.scrollIntoViewIfNeeded();await image.evaluate(img=>img.decode());if(!await image.evaluate(img=>img.naturalWidth>0))throw Error('Icon preview failed');}
   if(await page.getByRole('link',{name:'APK 받기',exact:true}).count()!==3)throw Error('Missing individual APK links');
   const copy=page.getByRole('button',{name:/링크.*복사|복사/});if(await copy.count()!==1)throw Error('Copy-link button missing or ambiguous: '+JSON.stringify(await page.getByRole('button').allTextContents()));
   await copy.scrollIntoViewIfNeeded();await copy.click();
   const clipboard=await page.evaluate(()=>navigator.clipboard.readText());if(clipboard!==publication.url)throw Error('Copied URL differs');
   const dimensions=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));
   if(dimensions.document>dimensions.viewport+2)throw Error('Page overflows mobile viewport');
   if(errors.length)throw Error('Page errors: '+errors.join('; '));
   await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:path.join(out,profile+'.png'),fullPage:true});
   results.push({profile,status:response.status(),iconImages:3,apkLinks:3,copyLinkVerified:true,dimensions,javascriptErrors:errors});await context.close();
  }
 }finally{await browser.close();}
 const report={passed:true,url:publication.url,verifiedAt:new Date().toISOString(),automatedVisitsExcluded:true,profiles:results};await fs.writeFile(path.join(out,'page-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
