package io.officestudio.shell;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SlidePrintInstrumentedTest {
    Instrumentation inst;
    MainActivity activity;
    UiDevice device;
    File folder;
    @Before public void launch(){
        Assume.assumeTrue(BuildConfig.STUDIO.equals("slide"));
        inst=InstrumentationRegistry.getInstrumentation();device=UiDevice.getInstance(inst);
        activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        folder=new File(activity.getExternalFilesDir(null),"print-qa");assertTrue(folder.isDirectory()||folder.mkdirs());
        waitJS("!!document.querySelector('.android-home')");
    }
    @After public void close(){if(activity!=null&&!activity.isDestroyed())inst.runOnMainSync(()->activity.finish());}
    String js(String expression){
        CountDownLatch done=new CountDownLatch(1);String[] value={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript(expression,v->{value[0]=v;done.countDown();}));
        try{assertTrue("JavaScript timed out",done.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return value[0];
    }
    void until(BooleanSupplier check,long timeout,String message){
        long end=SystemClock.uptimeMillis()+timeout;
        do{if(check.getAsBoolean())return;SystemClock.sleep(150);}while(SystemClock.uptimeMillis()<end);
        shot("failure");try{device.dumpWindowHierarchy(new File(folder,"failure.xml"));}catch(IOException ignored){}
        fail(message+"; foreground="+device.getCurrentPackageName());
    }
    void waitJS(String expression){until(()->"true".equals(js(expression)),30000,expression);}
    void shot(String name){assertTrue(device.takeScreenshot(new File(folder,name+".png")));}
    void tap(String selector)throws Exception{
        SystemClock.sleep(300);inst.waitForIdleSync();
        JSONArray p=new JSONArray(js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [(r.x+r.width/2)*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
        int[] origin=new int[2];boolean[] focused={false};inst.runOnMainSync(()->{activity.web.getLocationOnScreen(origin);focused[0]=activity.hasWindowFocus();});
        assertTrue("System window must not cover app",focused[0]);assertTrue(device.click(origin[0]+p.getInt(0),origin[1]+p.getInt(1)));
    }
    void print()throws Exception{
        tap(".android-topbar [aria-label='문서 메뉴']");waitJS("!!document.querySelector('.android-menu-items')");tap(".android-menu-items button:first-child");
        until(()->"com.android.printspooler".equals(device.getCurrentPackageName()),45000,"System print window did not open");
        UiObject2 destination=device.wait(Until.findObject(By.res("com.android.printspooler:id/destination_spinner")),10000);assertNotNull(destination);
        if(!device.hasObject(By.text("Save as PDF"))){destination.click();UiObject2 pdf=device.wait(Until.findObject(By.text("Save as PDF")),10000);assertNotNull(pdf);pdf.click();}
        until(()->{UiObject2 b=device.findObject(By.res("com.android.printspooler:id/print_button"));return b!=null&&b.isEnabled();},45000,"Preview not ready");
    }
    void returned(){until(()->"io.officestudio.slide".equals(device.getCurrentPackageName()),30000,"Slide did not regain focus");waitJS("!document.querySelector('.busy-backdrop') && document.querySelectorAll('.print-slide').length===0");}
    File savePdf(String filename)throws Exception{
        if(!device.hasObject(By.text("Save as PDF"))){UiObject2 d=device.findObject(By.res("com.android.printspooler:id/destination_spinner"));assertNotNull(d);d.click();UiObject2 pdf=device.wait(Until.findObject(By.text("Save as PDF")),10000);assertNotNull(pdf);pdf.click();}
        until(()->{UiObject2 b=device.findObject(By.res("com.android.printspooler:id/print_button"));return b!=null&&b.isEnabled();},30000,"PDF action unavailable");
        shot("preview-"+filename);device.dumpWindowHierarchy(new File(folder,"preview.xml"));
        device.findObject(By.res("com.android.printspooler:id/print_button")).click();
        until(()->device.getCurrentPackageName()!=null&&device.getCurrentPackageName().contains("documentsui"),30000,"PDF save picker did not open");
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000));device.waitForIdle(2000);
        if(device.hasObject(By.text("Save to"))||!device.hasObject(By.res("com.google.android.documentsui:id/breadcrumb_text").text("Downloads"))){
            if(!device.hasObject(By.text("Save to")))device.findObject(new UiSelector().description("Show roots")).click();
            // Match the drawer item, not the non-clickable toolbar title.
            UiObject downloads=device.findObject(new UiSelector().resourceId("android:id/title").text("Downloads"));
            assertTrue(downloads.waitForExists(5000));assertTrue(downloads.click());
        }
        until(()->!device.hasObject(By.text("Save to")),10000,"Save location drawer still open");
        UiObject2 name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000);assertNotNull(name);name.setText(filename);
        device.waitForIdle(2000);
        until(()->{UiObject2 b=device.findObject(By.text(Pattern.compile("(?i)^save$")));return b!=null&&b.isEnabled();},10000,"System Save not enabled");
        shot("before-system-save");device.dumpWindowHierarchy(new File(folder,"before-system-save.xml"));
        UiObject save=device.findObject(new UiSelector().resourceId("android:id/button1").textMatches("(?i)^save$"));
        assertTrue(save.exists());assertTrue(save.click());
        returned();
        until(()->{try{return device.executeShellCommand("ls -l /sdcard/Download/"+filename).contains(filename);}catch(IOException e){return false;}},30000,"Saved PDF missing");
        File copy=new File(folder,filename);device.executeShellCommand("cp /sdcard/Download/"+filename+" "+copy.getAbsolutePath());assertTrue(copy.length()>100);return copy;
    }
    void verifyPdf(File file)throws Exception{
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)){
            assertEquals(2,pdf.getPageCount());JSONArray sizes=new JSONArray();
            for(int i=0;i<pdf.getPageCount();i++)try(PdfRenderer.Page p=pdf.openPage(i)){assertTrue("A4 landscape width",Math.abs(p.getWidth()-842)<=2);assertTrue("A4 landscape height",Math.abs(p.getHeight()-595)<=2);sizes.put(new JSONObject().put("width",p.getWidth()).put("height",p.getHeight()));}
            try(FileWriter w=new FileWriter(new File(folder,file.getName()+".json"))){w.write(new JSONObject().put("pages",pdf.getPageCount()).put("sizes",sizes).toString(2));}
        }
    }
    @Test public void cancelReopenAndSavePdfRetainsReaderAndEverySlide()throws Exception{
        String json;try(InputStream input=inst.getContext().getAssets().open("slide-print.slide")){json=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String base64=Base64.encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8),Base64.NO_WRAP);
        js("(()=>{const bytes=Uint8Array.from(atob('"+base64+"'),c=>c.charCodeAt(0)),d=new DataTransfer();d.items.add(new File([bytes],'print.slide',{type:'application/json'}));const input=document.querySelector('input[accept*=\".pptx\"]');input.files=d.files;input.dispatchEvent(new Event('change',{bubbles:true}))})()");
        waitJS("!!document.querySelector('[data-testid=android-slide-viewport]')");
        print();shot("before-cancel");device.pressBack();returned();
        inst.runOnMainSync(()->{assertNull(activity.pending);assertNull(activity.slidePrint);assertFalse(activity.isFinishing());});
        assertEquals("\"false\"",js("sessionStorage.getItem('slide-studio-file-dirty')"));
        print();
        device.findObject(new UiSelector().resourceId("com.android.printspooler:id/expand_collapse_handle")).click();device.waitForIdle(2000);
        device.dumpWindowHierarchy(new File(folder,"paper-options.xml"));
        UiObject paper=device.findObject(new UiSelector().resourceId("com.android.printspooler:id/paper_size_spinner"));
        if(!paper.exists())paper=device.findObject(new UiSelector().text("Letter"));
        assertTrue("Paper selector unavailable",paper.exists());paper.click();
        device.dumpWindowHierarchy(new File(folder,"paper-choices.xml"));
        UiSelector a4Selector=new UiSelector().textMatches(".*A4.*");
        UiObject a4=device.findObject(a4Selector);
        if(!a4.exists())new UiScrollable(new UiSelector().className("android.widget.ListView")).scrollIntoView(a4Selector);
        assertTrue("A4 unavailable",a4.waitForExists(5000));a4.click();
        device.findObject(new UiSelector().resourceId("com.android.printspooler:id/expand_collapse_handle")).click();
        File pdf=savePdf("SlideStudio-Print-QA.pdf");verifyPdf(pdf);
        inst.runOnMainSync(()->{assertNull(activity.pending);assertNull(activity.slidePrint);assertFalse(activity.isFinishing());});
        assertEquals("\"false\"",js("sessionStorage.getItem('slide-studio-file-dirty')"));shot("returned");
    }
}
