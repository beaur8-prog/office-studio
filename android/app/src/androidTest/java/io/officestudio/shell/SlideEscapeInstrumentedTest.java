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
public class SlideEscapeInstrumentedTest {
    Instrumentation inst;
    MainActivity activity;
    @Before public void launch() {
        Assume.assumeTrue(BuildConfig.STUDIO.equals("slide"));
        inst=InstrumentationRegistry.getInstrumentation();
        activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("!!window.OfficeStudioHost?.escape && !!document.querySelector('button')");
    }
    @After public void close(){if(activity!=null)inst.runOnMainSync(()->activity.finish());}
    String js(String expression) {
        CountDownLatch done=new CountDownLatch(1);String[] value={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript(expression,v->{value[0]=v;done.countDown();}));
        try{assertTrue("JavaScript timed out",done.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
        return value[0];
    }
    void waitFor(String expression){long end=SystemClock.uptimeMillis()+20000;do{if("true".equals(js(expression)))return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);fail("Not ready: "+expression);}
    void tap(String selector)throws Exception {
        JSONArray xy=new JSONArray(js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [(r.x+r.width/2)*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
        int[] origin=new int[2];inst.runOnMainSync(()->activity.web.getLocationOnScreen(origin));
        assertTrue(UiDevice.getInstance(inst).click(origin[0]+(int)Math.round(xy.getDouble(0)),origin[1]+(int)Math.round(xy.getDouble(1))));
    }
    @Test public void hardwareEscapeEndsOnlyTheShowAndKeepsTheEditorAndDocument()throws Exception {
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor') && !!document.querySelector('[aria-label=\"슬라이드 쇼 시작\"]')");
        String dirty=js("sessionStorage.getItem('slide-studio-file-dirty')");
        String name=js("document.querySelector('.android-topbar strong').textContent");
        tap("[aria-label=\"슬라이드 쇼 시작\"]");
        waitFor("!!document.querySelector('.android-presentation')");
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);
        waitFor("!document.querySelector('.android-presentation') && !!document.querySelector('.android-editor')");
        assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());
        assertEquals(dirty,js("sessionStorage.getItem('slide-studio-file-dirty')"));
        assertEquals(name,js("document.querySelector('.android-topbar strong').textContent"));
        long now=SystemClock.uptimeMillis();
        inst.runOnMainSync(()->{
            assertTrue(activity.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE,1)));
            assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ESCAPE)));
        });
        assertEquals("true",js("!!document.querySelector('.android-editor')"));
        assertFalse(activity.isFinishing());
    }
    @Test public void unhandledEscapeNeverFinishesTheActivity() {
        js("window.OfficeStudioHost.escape=()=>false");
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);
        assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());
    }
}
