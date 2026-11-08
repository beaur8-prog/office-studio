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
    void waitForNativePresentation(){
        long end=SystemClock.uptimeMillis()+15000;boolean[] ready={false};
        do{inst.runOnMainSync(()->ready[0]=activity.hasWindowFocus()&&activity.immersive);if(ready[0])return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);
        fail("Native presentation window is not focused and immersive");
    }
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
        waitForNativePresentation();
    }
    void exitedToEditor(){waitFor("!document.querySelector('.android-presentation') && !!document.querySelector('.android-editor')");settle();assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());}
    void createViewer()throws Exception{
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor')");settle();
        js("window.dispatchEvent(new Event('slide-studio-android-back',{cancelable:true}))");
        waitFor("!!document.querySelector('.android-slide-viewport')");settle();
    }
    void viewerPresent()throws Exception{
        tap(".android-topbar [aria-label='현재 슬라이드에서 발표']");
        waitFor("!!document.querySelector('.android-presentation')");settle();waitForNativePresentation();
    }
    @Test public void viewerPresentationNeverCreatesSeparateFullscreenView()throws Exception{
        createViewer();viewerPresent();
        boolean[] original={false};inst.runOnMainSync(()->original[0]=activity.fullscreen==null&&activity.web.getVisibility()==android.view.View.VISIBLE);
        assertTrue("APK slideshow must remain in the original WebView",original[0]);
        assertEquals("false",js("!!document.fullscreenElement"));
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);
        waitFor("!document.querySelector('.android-presentation')&&!!document.querySelector('.android-slide-viewport')");
        assertFalse(activity.isFinishing());
    }
    @Test public void repeatedViewerEscapeAndBackCannotCloseActivity()throws Exception{
        createViewer();
        for(int i=0;i<6;i++){
            viewerPresent();
            inst.sendKeyDownUpSync(i%2==0?KeyEvent.KEYCODE_ESCAPE:KeyEvent.KEYCODE_BACK);
            waitFor("!document.querySelector('.android-presentation')&&!!document.querySelector('.android-slide-viewport')");settle();
            assertFalse("Viewer round "+i,activity.isFinishing());assertFalse(activity.isDestroyed());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);settle();
            assertEquals("true",js("!!document.querySelector('.android-slide-viewport')"));
        }
    }
    @Test public void presentationWindowHidesTheSoftKeyboard()throws Exception{
        js("(()=>{const input=document.createElement('input');input.id='native-ime-check';input.style.cssText='position:fixed;top:0;left:0;width:200px;height:60px;z-index:9999;background:white';document.body.appendChild(input)})()");
        tap("#native-ime-check");
        long end=SystemClock.uptimeMillis()+15000;boolean[] shown={false};
        do{inst.runOnMainSync(()->{androidx.core.view.WindowInsetsCompat i=androidx.core.view.ViewCompat.getRootWindowInsets(activity.root);shown[0]=i!=null&&i.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});if(shown[0])break;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);
        assertTrue("IME setup must be visible",shown[0]);
        js("window.OfficeStudioHost.request('systemBars',{hidden:true})");
        end=SystemClock.uptimeMillis()+15000;boolean[] hidden={false};
        do{inst.runOnMainSync(()->{androidx.core.view.WindowInsetsCompat i=androidx.core.view.ViewCompat.getRootWindowInsets(activity.root);hidden[0]=i!=null&&!i.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())&&activity.root.getPaddingBottom()==0;});if(hidden[0])break;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);
        assertTrue("Presentation must reclaim the keyboard area",hidden[0]);
        js("window.OfficeStudioHost.request('systemBars',{hidden:false})");
    }
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
    @Test public void preImeEscapeEndsOnlyPresentationAndConsumesBothEdges()throws Exception{
        createAndPresent();
        js("window.nativeEscapeCount=0;window.originalNativeEscape=window.OfficeStudioHost.escape;window.OfficeStudioHost.escape=()=>{nativeEscapeCount++;return originalNativeEscape()}");
        boolean[] consumed={false,false,false};
        long down=SystemClock.uptimeMillis();
        inst.runOnMainSync(()->consumed[0]=activity.web.dispatchKeyEventPreIme(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE,0)));
        assertTrue("Esc must be consumed before the input method",consumed[0]);
        exitedToEditor();
        inst.runOnMainSync(()->{
            consumed[1]=activity.web.dispatchKeyEventPreIme(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE,1));
            consumed[2]=activity.web.dispatchKeyEventPreIme(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ESCAPE,0));
        });
        settle();assertTrue("Repeated Esc down must be consumed",consumed[1]);assertTrue("Esc release must be consumed",consumed[2]);
        assertEquals("1",js("window.nativeEscapeCount"));
        assertEquals("true",js("!!document.querySelector('.android-editor')"));assertFalse(activity.isFinishing());
    }
    @Test public void systemBackEndsOnlyViewerPresentation()throws Exception{
        createViewer();viewerPresent();
        js("window.nativeNavigationCount=0;['escape','slideBack'].forEach(key=>{const original=window.OfficeStudioHost[key];window.OfficeStudioHost[key]=()=>{nativeNavigationCount++;return original()}})");
        assertTrue(UiDevice.getInstance(inst).pressBack());
        settle();
        assertEquals("One Back must run one document command; state="+js("JSON.stringify({show:!!document.querySelector('.android-presentation'),viewer:!!document.querySelector('.android-slide-viewport'),home:!!document.querySelector('.android-home')})"),"1",js("window.nativeNavigationCount"));
        waitFor("!document.querySelector('.android-presentation')&&!!document.querySelector('.android-slide-viewport')");
        settle();assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());
    }
    @Test public void keyboardBackPressAndReleaseEndsOnlyPresentation()throws Exception{
        createAndPresent();
        // Send through the OS input queue so Android's predictive Back
        // dispatcher participates; direct Activity calls bypass that path.
        long down=SystemClock.uptimeMillis();
        inst.sendKeySync(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0,0,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,android.view.InputDevice.SOURCE_KEYBOARD));
        SystemClock.sleep(150);
        inst.sendKeySync(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0,0,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,android.view.InputDevice.SOURCE_KEYBOARD));
        exitedToEditor();
    }
    @Test public void legacyPreImeBackConsumesReleaseAfterShowExit()throws Exception{
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT<33);
        createAndPresent();
        boolean[] consumed={false,false};
        inst.runOnMainSync(()->consumed[0]=activity.web.dispatchKeyEventPreIme(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));
        assertTrue("Presentation Back must be consumed before IME",consumed[0]);
        exitedToEditor();
        inst.runOnMainSync(()->consumed[1]=activity.web.dispatchKeyEventPreIme(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));
        settle();assertTrue("Back release must not leak after show exit",consumed[1]);
        assertEquals("true",js("!!document.querySelector('.android-editor')"));assertFalse(activity.isFinishing());
    }
    @Test public void sharedCloseRequestCannotClosePresentation()throws Exception{
        createAndPresent();js("window.OfficeStudioHost.request('close')");exitedToEditor();
    }
    @Test public void nativeBackCallbackEndsOnlyPresentation()throws Exception{
        createAndPresent();inst.runOnMainSync(()->activity.onBackPressed());exitedToEditor();
    }
}
