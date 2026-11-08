package io.officestudio.shell;

import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Isolated .qa package only: native window, IME, renderer draft and Activity exit. */
@RunWith(AndroidJUnit4.class)
public class SheetWindowInstrumentedTest {
    String js(ActivityScenario<MainActivity> scenario,String code) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        scenario.onActivity(a->a.web.evaluateJavascript(code,value->{result.set(value);done.countDown();}));
        assertTrue("JavaScript timeout",done.await(15,TimeUnit.SECONDS));return result.get();
    }
    void waitJS(ActivityScenario<MainActivity> s,String expression) throws Exception {
        long end=SystemClock.uptimeMillis()+60000;
        while(SystemClock.uptimeMillis()<end){if("true".equals(js(s,expression)))return;SystemClock.sleep(150);}
        fail("Timed out: "+expression+"; body="+js(s,"document.body.innerText.slice(-900)"));
    }
    void ready(ActivityScenario<MainActivity> s) throws Exception {
        assertTrue("Never use the user's production package",InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName().endsWith(".qa"));
        waitJS(s,"!!window.sheetStudio && document.body.classList.contains('android-fullscreen')");
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        androidx.test.uiautomator.UiObject2 guide=device.findObject(androidx.test.uiautomator.By.text("Got it"));
        if(guide!=null)guide.click();
    }
    void screenshot(String name){
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"sheet-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(dir,name+".png")));
    }
    void tap(ActivityScenario<MainActivity> s,String selector) throws Exception {
        JSONObject p=new JSONObject(js(s,"(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth}})()"));
        s.onActivity(a->{try{float scale=a.web.getWidth()/(float)p.getDouble("width"),x=(float)p.getDouble("x")*scale,y=(float)p.getDouble("y")*scale;long t=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);a.web.dispatchTouchEvent(down);a.web.dispatchTouchEvent(up);down.recycle();up.recycle();
        }catch(Exception e){throw new AssertionError(e);}});
    }
    void setDraft(ActivityScenario<MainActivity> s,String value) throws Exception {
        js(s,"(()=>{const i=document.querySelector('#formula');i.value="+JSONObject.quote(value)+";i.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText'}));})()");
    }
    void assertFullWindow(ActivityScenario<MainActivity> s) throws Exception {
        waitJS(s,"document.querySelector('#app').getBoundingClientRect().top===0");
        s.onActivity(a->{
            WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(a.root);assertNotNull(insets);
            assertFalse(insets.isVisible(WindowInsetsCompat.Type.statusBars()));assertFalse(insets.isVisible(WindowInsetsCompat.Type.navigationBars()));
            assertEquals(0,a.root.getPaddingTop());assertEquals(0,a.root.getPaddingLeft());assertEquals(0,a.root.getPaddingRight());
            int[] point=new int[2];a.web.getLocationInWindow(point);assertEquals("WebView must occupy the old antenna row",0,point[1]);
            assertEquals(a.getWindow().getDecorView().getWidth(),a.web.getWidth());assertEquals(a.getWindow().getDecorView().getHeight(),a.web.getHeight());
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,a.getWindow().getAttributes().layoutInDisplayCutoutMode);
        });
    }
    @Test public void cameraAndSystemRowsBelongToTheRealWindowInBothOrientations() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);assertFullWindow(s);screenshot("portrait-full-window");
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitJS(s,"innerWidth>innerHeight");SystemClock.sleep(400);assertFullWindow(s);screenshot("landscape-full-window");
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        }
    }
    @Test public void liveDraftEnterKeepsImeAndCloseReallyFinishesWhileRecoverySurvives() throws Exception {
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);s.onActivity(activity::set);tap(s,"#android-cell-0-0");waitJS(s,"document.activeElement.id==='formula'");
            setDraft(s,"123");waitJS(s,"document.getElementById('android-cell-0-0').textContent==='123'");assertEquals("\"\"",js(s,"sheetStudio.getCell('A1').raw"));assertEquals("0",js(s,"sheetStudio.historyStats().undo"));
            long end=SystemClock.uptimeMillis()+10000;boolean[] ime={false};do{s.onActivity(a->{WindowInsetsCompat i=ViewCompat.getRootWindowInsets(a.root);ime[0]=i!=null&&i.isVisible(WindowInsetsCompat.Type.ime());});if(!ime[0])SystemClock.sleep(150);}while(!ime[0]&&SystemClock.uptimeMillis()<end);assertTrue("Touch entry must open a real IME",ime[0]);
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ENTER);
            waitJS(s,"sheetStudio.getSelection().r===1 && sheetStudio.getCell('A1').raw==='123' && document.activeElement.id==='formula'");
            s.onActivity(a->assertTrue("Enter must keep the real keyboard visible",ViewCompat.getRootWindowInsets(a.root).isVisible(WindowInsetsCompat.Type.ime())));
            screenshot("live-entry-next-cell");setDraft(s,"456");waitJS(s,"document.getElementById('android-cell-1-0').textContent==='456'");js(s,"document.querySelector('[data-action=cancel-edit]').click()");waitJS(s,"document.getElementById('android-cell-1-0').textContent===''");assertEquals("\"\"",js(s,"sheetStudio.getCell('A2').raw"));
            // First Back may dismiss IME; subsequent Back opens the app exit dialog.
            for(int i=0;i<3;i++){UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();SystemClock.sleep(500);if("true".equals(js(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')")))break;}
            waitJS(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')");js(s,"document.querySelector('[data-close]').click()");waitJS(s,"!document.querySelector('.backdrop')");assertFalse(activity.get().isFinishing());assertEquals("\"123\"",js(s,"sheetStudio.getCell('A1').raw"));
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();waitJS(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')");screenshot("exit-confirmation");js(s,"document.querySelector('[data-modal-button=\"0\"]').click()");
            end=SystemClock.uptimeMillis()+10000;while(!activity.get().isFinishing()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);assertTrue("Close must finish the native Activity",activity.get().isFinishing());
        }
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);js(s,"document.querySelector('[data-mobile=more]').click()");waitJS(s,"!!document.querySelector('[data-mobile=recover]')");js(s,"document.querySelector('[data-mobile=recover]').click()");waitJS(s,"!!document.querySelector('[data-recover-document]')");js(s,"document.querySelector('[data-recover-document]').click()");waitJS(s,"sheetStudio.getCell('A1').raw==='123'");assertEquals("\"\"",js(s,"sheetStudio.getCell('A2').raw"));screenshot("recovery-after-exit");
        }
    }
}
