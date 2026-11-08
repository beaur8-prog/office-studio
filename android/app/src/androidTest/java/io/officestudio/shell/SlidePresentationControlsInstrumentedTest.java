package io.officestudio.shell;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SlidePresentationControlsInstrumentedTest {
    Instrumentation inst;
    MainActivity activity;
    @Before public void launch(){
        Assume.assumeTrue(BuildConfig.STUDIO.equals("slide"));
        inst=InstrumentationRegistry.getInstrumentation();
        activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("!!document.querySelector('.android-home')");
    }
    @After public void close(){if(activity!=null&&!activity.isDestroyed())inst.runOnMainSync(()->activity.finish());}
    String js(String expression){
        CountDownLatch done=new CountDownLatch(1);String[] value={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript(expression,v->{value[0]=v;done.countDown();}));
        try{assertTrue(done.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
        return value[0];
    }
    void waitFor(String expression){long end=SystemClock.uptimeMillis()+30000;do{if("true".equals(js(expression)))return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);screenshot("presentation-tools-failure.png");System.out.println("SLIDE_TOOL_FAILURE "+js("JSON.stringify({focus:document.activeElement?.tagName,controls:document.querySelector('.android-presentation-controls')?.className,palette:!!document.querySelector('.presentation-tools'),view:{width:innerWidth,height:innerHeight,ratio:devicePixelRatio},hit:document.elementFromPoint(100,innerHeight-40)?.tagName})"));fail("Not ready: "+expression);}
    void tap(String selector)throws Exception{
        SystemClock.sleep(400);inst.waitForIdleSync();
        JSONArray point=new JSONArray(js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [(r.x+r.width/2)*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
        int[] origin=new int[2];boolean[] focused={false};
        inst.runOnMainSync(()->{activity.web.getLocationOnScreen(origin);focused[0]=activity.hasWindowFocus();});
        assertTrue("System window must not cover the app",focused[0]);
        assertTrue(UiDevice.getInstance(inst).click(origin[0]+(int)Math.round(point.getDouble(0)),origin[1]+(int)Math.round(point.getDouble(1))));
    }
    void screenshot(String name){SystemClock.sleep(600);inst.waitForIdleSync();assertTrue(UiDevice.getInstance(inst).takeScreenshot(new java.io.File(activity.getExternalFilesDir(null),name)));}
    @Test public void drawingToolbarCanHideAndReopenWithoutLosingToolOrInk()throws Exception{
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor-tablet')");
        tap(".android-editor-tablet .document-actions .button.primary");
        waitFor("!!document.querySelector('.android-presentation')");
        SystemClock.sleep(600);tap(".android-presentation");
        waitFor("!document.querySelector('.android-presentation-controls').classList.contains('android-controls-hidden')");
        screenshot("presentation-tools-before-palette.png");
        tap("[aria-label='포인터와 판서 도구']");
        waitFor("!!document.querySelector('.presentation-tools')");
        tap(".presentation-tools [aria-label='펜']");
        waitFor("document.querySelector('.presentation-ink').dataset.tool==='pen' && !!document.querySelector('.android-presentation-tools-trigger')");
        assertEquals("true",js("document.querySelector('.android-presentation-controls').classList.contains('android-controls-hidden')"));
        JSONArray points=new JSONArray(js("(()=>{const r=document.querySelector('.presentation-ink').getBoundingClientRect();return [(r.x+r.width*.25)*devicePixelRatio,(r.y+r.height*.3)*devicePixelRatio,(r.x+r.width*.5)*devicePixelRatio,(r.y+r.height*.4)*devicePixelRatio]})()"));
        int[] origin=new int[2];inst.runOnMainSync(()->activity.web.getLocationOnScreen(origin));
        assertTrue(UiDevice.getInstance(inst).swipe(origin[0]+points.getInt(0),origin[1]+points.getInt(1),origin[0]+points.getInt(2),origin[1]+points.getInt(3),12));
        waitFor("document.querySelectorAll('[data-testid=ink-stroke]').length===1");
        screenshot("presentation-tools-collapsed.png");
        tap(".android-presentation-tools-trigger");
        waitFor("!document.querySelector('.android-presentation-controls').classList.contains('android-controls-hidden')");
        screenshot("presentation-tools-expanded.png");
        tap("[aria-label='발표 도구 모음 숨기기']");
        waitFor("!!document.querySelector('.android-presentation-tools-trigger')");
        assertEquals("true",js("document.querySelector('.android-presentation-controls').classList.contains('android-controls-hidden')"));
        assertEquals("\"pen\"",js("document.querySelector('.presentation-ink').dataset.tool"));
        assertEquals("1",js("document.querySelectorAll('[data-testid=ink-stroke]').length"));
        assertFalse(activity.isFinishing());
    }
}
