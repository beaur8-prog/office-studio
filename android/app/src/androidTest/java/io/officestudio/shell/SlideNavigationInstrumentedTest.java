package io.officestudio.shell;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SlideNavigationInstrumentedTest {
    Instrumentation inst;
    MainActivity activity;
    @Before public void launch() {
        Assume.assumeTrue(BuildConfig.STUDIO.equals("slide"));
        inst=InstrumentationRegistry.getInstrumentation();
        activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("!!window.OfficeStudioHost && !!document.querySelector('button')");
    }
    @After public void close(){if(activity!=null&&!activity.isDestroyed())inst.runOnMainSync(()->activity.finish());}
    String js(String expression){
        CountDownLatch done=new CountDownLatch(1);String[] value={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript(expression,v->{value[0]=v;done.countDown();}));
        try{assertTrue("JS timed out",done.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
        return value[0];
    }
    void waitFor(String expression){long end=SystemClock.uptimeMillis()+30000;do{if("true".equals(js(expression)))return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);fail("Not ready: "+expression);}
    void settle(){SystemClock.sleep(600);inst.waitForIdleSync();}
    void tap(String selector)throws Exception{
        JSONArray point=new JSONArray(js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [(r.x+r.width/2)*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
        int[] origin=new int[2];inst.runOnMainSync(()->activity.web.getLocationOnScreen(origin));
        assertTrue(UiDevice.getInstance(inst).click(origin[0]+(int)Math.round(point.getDouble(0)),origin[1]+(int)Math.round(point.getDouble(1))));
    }
    void createAndPresent()throws Exception{
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor')");
        tap(".android-editor [aria-label='슬라이드 쇼 시작'], .android-editor-tablet .document-actions .button.primary");
        waitFor("!!document.querySelector('.android-presentation')");
    }
    void exitedToEditor(){waitFor("!document.querySelector('.android-presentation') && !!document.querySelector('.android-editor')");settle();assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());}
    @Test public void tabletUsesFullDesktopShapeGallery()throws Exception{
        Assume.assumeTrue("true".equals(js("Math.min(screen.width,screen.height)>=600")));
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor-tablet')");
        assertEquals("true",js("!document.querySelector('.android-editor-bars') && !!document.querySelector('.topbar') && !!document.querySelector('.statusbar')"));
        js("[...document.querySelectorAll('.tabs button')].find(b=>b.textContent.trim()==='삽입').click()");
        js("[...document.querySelectorAll('.ribbon button')].find(b=>b.textContent.trim()==='도형').click()");
        waitFor("document.querySelectorAll('.shape-options button').length===22");
        settle();UiDevice.getInstance(inst).waitForIdle();
        assertTrue(UiDevice.getInstance(inst).takeScreenshot(new java.io.File(activity.getExternalFilesDir(null),"tablet-full-gallery.png")));
    }
    @Test public void explicitHomeBackStillClosesActivity(){
        waitFor("!!document.querySelector('.android-home')");
        inst.runOnMainSync(()->activity.onBackPressed());settle();
        assertTrue("Explicit root close must remain available",activity.isFinishing()||activity.isDestroyed());
    }
    @Test public void falseCallbackMustNotFinishActivity(){
        js("window.OfficeStudioHost.back=()=>false;window.OfficeStudioHost.slideBack=()=>false");
        inst.runOnMainSync(()->activity.onBackPressed());settle();
        assertFalse("false callback is not consent to close",activity.isFinishing());assertFalse(activity.isDestroyed());
    }
    @Test public void nullCallbackMustNotFinishActivity(){
        js("window.OfficeStudioHost.back=()=>null;window.OfficeStudioHost.slideBack=()=>null");
        inst.runOnMainSync(()->activity.onBackPressed());settle();
        assertFalse("null callback is not consent to close",activity.isFinishing());assertFalse(activity.isDestroyed());
    }
    @Test public void hardwareEscapeEndsOnlyPresentation()throws Exception{
        createAndPresent();inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);exitedToEditor();
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);settle();assertEquals("true",js("!!document.querySelector('.android-editor')"));assertFalse(activity.isFinishing());
    }
    @Test public void escapeMappedToBackConsumesReleaseAfterShowExit()throws Exception{
        createAndPresent();
        inst.runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));
        exitedToEditor();
        inst.runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));
        settle();assertEquals("true",js("!!document.querySelector('.android-editor')"));assertFalse(activity.isFinishing());
    }
    @Test public void sharedCloseRequestCannotClosePresentation()throws Exception{
        createAndPresent();js("window.OfficeStudioHost.request('close')");exitedToEditor();
    }
    @Test public void nativeBackCallbackEndsOnlyPresentation()throws Exception{
        createAndPresent();inst.runOnMainSync(()->activity.onBackPressed());exitedToEditor();
    }
}
