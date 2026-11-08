package io.officestudio.shell;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReleaseInstrumentedTest {
    MainActivity activity;
    Instrumentation inst;
    Context context,target;
    UiDevice device;
    static final byte[] PAYLOAD=new byte[80003];
    static {for(int i=0;i<PAYLOAD.length;i++)PAYLOAD[i]=(byte)(i%256);}
    @Before public void launch() throws Exception {
        inst=InstrumentationRegistry.getInstrumentation();context=inst.getContext();target=inst.getTargetContext();device=UiDevice.getInstance(inst);
        activity=(MainActivity)inst.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        until(()->"true".equals(js("!!window.OfficeStudioHost&&document.body.innerText.length>35&&!!document.querySelector('button')")),120000,"packaged Android editor did not start");
    }
    @After public void close(){if(activity!=null)inst.runOnMainSync(()->activity.finish());}
    String js(String expression){
        CountDownLatch latch=new CountDownLatch(1);String[] value={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript("JSON.stringify("+expression+")",v->{try{Object outer=new JSONTokener(v).nextValue();if(outer instanceof String)value[0]=(String)outer;}catch(Exception ignored){}latch.countDown();}));
        try{assertTrue("JavaScript callback timed out",latch.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return value[0];
    }
    void exec(String code){js("(()=>{"+code+";return true})()");}
    void until(BooleanSupplier condition,long timeout,String message){long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(condition.getAsBoolean())return;SystemClock.sleep(150);}fail(message+"; body="+js("document.body.innerText.slice(0,1200)"));}
    String host(){return BuildConfig.STUDIO.equals("sheet")?"window.sheetStudioAndroid":BuildConfig.STUDIO.equals("word")?"window.wordAndroid":"window.slideStudioAndroid";}
    String saveMethod(){return BuildConfig.STUDIO.equals("sheet")?"saveCopy":"saveFile";}
    void callSave(){exec("window.__qaResult=null;"+host()+"."+saveMethod()+"({name:'한글 APK 사본.bin',mime:'application/octet-stream',type:'application/octet-stream',data:Uint8Array.from({length:80003},(_,i)=>i%256)}).then(r=>window.__qaResult=r).catch(e=>window.__qaResult={error:e.message})");}
    boolean pending(String method){boolean[] found={false};inst.runOnMainSync(()->found[0]=activity.pending!=null&&activity.pending.method.equals(method));return found[0];}
    File fixture(String name,byte[] bytes)throws Exception{File folder=new File(context.getCacheDir(),"fixtures");folder.mkdirs();File f=new File(folder,name);try(OutputStream out=new FileOutputStream(f)){out.write(bytes);}return f;}
    Uri grant(File f,int flags)throws Exception{
        String authority=null;for(ProviderInfo p:context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PROVIDERS).providers)if(p.authority.endsWith(".fixtures"))authority=p.authority;
        assertNotNull(authority);Uri uri=FileProvider.getUriForFile(context,authority,f);context.grantUriPermission(target.getPackageName(),uri,flags);return uri;
    }
    byte[] asset(String name)throws Exception{try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    void picker(){until(()->{String p=device.getCurrentPackageName();return p!=null&&p.contains("documentsui");},20000,"Android system file picker not visible");}
    void result(int request,Uri uri){picker();inst.runOnMainSync(()->activity.onActivityResult(request,Activity.RESULT_OK,new Intent().setData(uri)));device.pressBack();device.waitForIdle();}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}
    void status(JSONObject record){Bundle bundle=new Bundle();bundle.putString("stream","OFFICE_RESULT "+record+"\n");inst.sendStatus(2,bundle);}
    @Test public void packagedIdentityIconsAndOfflineAssets()throws Exception{
        PackageInfo info=target.getPackageManager().getPackageInfo(target.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        assertEquals("1.0.0",info.versionName);assertEquals(0,info.applicationInfo.flags&ApplicationInfo.FLAG_DEBUGGABLE);
        String cert=hex(MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray()));String expected=InstrumentationRegistry.getArguments().getString("certificate");if(expected!=null)assertEquals(expected.toLowerCase(Locale.ROOT),cert);
        Drawable icon=target.getPackageManager().getApplicationIcon(target.getPackageName());assertTrue(icon instanceof AdaptiveIconDrawable);Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,192,192);icon.draw(new Canvas(bitmap));assertNotEquals(0,bitmap.getPixel(96,96));ByteArrayOutputStream png=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,png);
        Bundle bundle=new Bundle();bundle.putString("stream","OFFICE_ICON "+BuildConfig.STUDIO+" "+android.util.Base64.encodeToString(png.toByteArray(),android.util.Base64.NO_WRAP)+"\n");inst.sendStatus(2,bundle);
        assertEquals("\"android\"",js("Capacitor.getPlatform()"));assertEquals("false",js("!!window.desktop||!!window.wordDesktop"));
        assertTrue(js("location.href").contains(MainActivity.ORIGIN));assertEquals("true",js("!!document.querySelector('script[src*=\"studio-host\"]')"));
        PackageInfo web=android.webkit.WebView.getCurrentWebViewPackage();status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","identity-icons-offline","passed",true,"version",info.versionName,"certificateSHA256",cert,"webView",web==null?"unknown":web.versionName,"sdk",Build.VERSION.SDK_INT,"debuggable",false,"adaptiveIcon",true));
    }
    @Test public void nativePickerCancellationAndChunkedRead()throws Exception{
        exec("window.__qaResult=null;"+host()+".openFile().then(f=>window.__qaResult=f?{name:f.name,bytes:f.data.length,first:f.data[0],last:f.data[f.data.length-1]}:'cancelled').catch(e=>window.__qaResult={error:e.message})");until(()->pending("open"),15000,"system open picker not launched");picker();device.pressBack();until(()->!"null".equals(js("window.__qaResult")),15000,"picker cancellation not acknowledged");assertEquals("\"cancelled\"",js("window.__qaResult"));
        File file=fixture("chunked-open.bin",PAYLOAD);Uri uri=grant(file,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        exec("window.__qaResult=null;"+host()+".openFile().then(f=>window.__qaResult=f?{name:f.name,bytes:f.data.length,first:f.data[0],last:f.data[f.data.length-1]}:'cancelled').catch(e=>window.__qaResult={error:e.message})");until(()->pending("open"),15000,"second open picker not launched");result(MainActivity.OPEN,uri);until(()->!"null".equals(js("window.__qaResult")),30000,"native file stream not returned");JSONObject result=new JSONObject(js("window.__qaResult"));assertEquals(PAYLOAD.length,result.getInt("bytes"));assertEquals(PAYLOAD[PAYLOAD.length-1]&255,result.getInt("last"));assertArrayEquals(PAYLOAD,java.nio.file.Files.readAllBytes(file.toPath()));status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","saf-open-cancel-and-chunked-read","passed",true,"bytes",PAYLOAD.length));
    }
    @Test public void nativeSaveCancellationCompletionAndFailure()throws Exception{
        callSave();until(()->pending("save"),20000,"save picker not launched");assertEquals("null",js("window.__qaResult"));picker();device.pressBack();until(()->!"null".equals(js("window.__qaResult")),15000,"save cancel not acknowledged");assertEquals("true",js("window.__qaResult==='cancelled'||window.__qaResult===false||window.__qaResult?.cancelled===true"));
        File output=fixture("saved-copy.bin",new byte[0]);Uri writable=grant(output,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);callSave();until(()->pending("save"),20000,"second save picker not launched");assertEquals("null",js("window.__qaResult"));result(MainActivity.SAVE,writable);until(()->!"null".equals(js("window.__qaResult")),30000,"write completion not acknowledged");assertEquals("true",js("window.__qaResult==='completed'||window.__qaResult===true||window.__qaResult?.completed===true"));assertArrayEquals(PAYLOAD,java.nio.file.Files.readAllBytes(output.toPath()));
        File original=fixture("read-only-original.bin",new byte[]{11,22,33});Uri readonly=grant(original,Intent.FLAG_GRANT_READ_URI_PERMISSION);callSave();until(()->pending("save"),20000,"failure save picker not launched");result(MainActivity.SAVE,readonly);until(()->!"null".equals(js("window.__qaResult")),30000,"provider failure not reported");assertEquals("true",js("typeof window.__qaResult?.error==='string'"));assertArrayEquals(new byte[]{11,22,33},java.nio.file.Files.readAllBytes(original.toPath()));status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","saf-save-cancel-confirmed-write-and-provider-failure","passed",true,"bytes",PAYLOAD.length,"originalPreserved",true));
    }
    @Test public void realDocumentIntentAndHwp()throws Exception{
        String name=BuildConfig.STUDIO.equals("sheet")?"fixture.csv":BuildConfig.STUDIO.equals("slide")?"fixture.pptx":"fixture.txt";File original=fixture(name,asset(name));Uri uri=grant(original,Intent.FLAG_GRANT_READ_URI_PERMISSION);byte[] before=java.nio.file.Files.readAllBytes(original.toPath());
        inst.runOnMainSync(()->activity.onNewIntent(new Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
        until(()->"true".equals(js("document.body.innerText.includes('APK 문서 확인')")),90000,"external document did not open in Android UI");assertArrayEquals(before,java.nio.file.Files.readAllBytes(original.toPath()));
        if(BuildConfig.STUDIO.equals("word")){File hwp=fixture("fixture.hwp",asset("fixture.hwp"));Uri hwpUri=grant(hwp,Intent.FLAG_GRANT_READ_URI_PERMISSION);inst.runOnMainSync(()->activity.onNewIntent(new Intent(Intent.ACTION_VIEW,hwpUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));until(()->"true".equals(js("!!document.querySelector('.hwp-sheet svg')")),90000,"packaged HWP reader did not render");}
        exec("localStorage.setItem('office.release.reinstall','keep-'+"+JSONObject.quote(BuildConfig.STUDIO)+")");assertEquals(JSONObject.quote("keep-"+BuildConfig.STUDIO),js("localStorage.getItem('office.release.reinstall')"));SystemClock.sleep(600);status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","real-document-intent-and-recovery-storage","passed",true,"originalPreserved",true,"hwp",BuildConfig.STUDIO.equals("word")));
    }
    @Test public void nativeSharingHandsOffExactBytes()throws Exception{
        File received=new File(context.getCacheDir(),"shared-result.bin");received.delete();exec("window.__qaResult=null;"+host()+".shareFile({name:'APK 공유.bin',mime:'application/octet-stream',type:'application/octet-stream',data:Uint8Array.from({length:80003},(_,i)=>i%256)}).then(r=>window.__qaResult=r).catch(e=>window.__qaResult={error:e.message})");until(()->pending("share"),20000,"Android share chooser not launched");UiObject2 target=device.wait(Until.findObject(By.text("Office QA Receiver")),15000);assertNotNull("test share receiver not offered",target);target.click();until(()->received.isFile(),30000,"shared FileProvider bytes not readable by recipient");assertArrayEquals(PAYLOAD,java.nio.file.Files.readAllBytes(received.toPath()));until(()->!"null".equals(js("window.__qaResult")),20000,"share selection not acknowledged");assertEquals("true",js("window.__qaResult==='completed'||window.__qaResult?.completed===true"));status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","real-chooser-fileprovider-share","passed",true,"bytes",PAYLOAD.length));
    }
    @Test public void verifyReinstallPreservesStorage()throws Exception{
        // Selected separately by CI after adb install -r of the exact same APK.
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("reinstall")))return;
        assertEquals(JSONObject.quote("keep-"+BuildConfig.STUDIO),js("localStorage.getItem('office.release.reinstall')"));status(MainActivity.obj("app",BuildConfig.STUDIO,"scenario","same-package-signed-reinstall-preserves-storage","passed",true));
    }
}
