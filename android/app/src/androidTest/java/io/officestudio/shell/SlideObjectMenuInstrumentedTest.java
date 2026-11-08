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
public class SlideObjectMenuInstrumentedTest {
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
        CountDownLatch done=new CountDownLatch(1);String[] result={"null"};
        inst.runOnMainSync(()->activity.web.evaluateJavascript(expression,value->{result[0]=value;done.countDown();}));
        try{assertTrue("JavaScript timed out",done.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
        return result[0];
    }
    void waitFor(String expression){long end=SystemClock.uptimeMillis()+30000;do{if("true".equals(js(expression)))return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);fail("Not ready: "+expression);}
    void waitForFocus(){
        long end=SystemClock.uptimeMillis()+15000;boolean[] focused={false};
        do{inst.runOnMainSync(()->focused[0]=activity.hasWindowFocus());if(focused[0])return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);
        fail("A system window is covering the app; native click validation is unavailable");
    }
    @Test public void formatObjectMenuStaysAboveIconsAndReceivesNativeTap()throws Exception{
        js("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('새 프레젠테이션')).click()");
        waitFor("!!document.querySelector('.android-editor-tablet')");
        js("[...document.querySelectorAll('.tabs button')].find(b=>b.textContent.trim()==='삽입').click()");
        js("[...document.querySelectorAll('.ribbon button')].find(b=>b.textContent.trim()==='도형').click()");
        waitFor("!!document.querySelector('.shape-options')");
        js("[...document.querySelectorAll('.shape-options button')].find(b=>b.textContent.trim()==='사각형').click()");
        waitFor("!!document.querySelector('.editable-slide [data-element-id].selected')");
        js("(()=>{const el=document.querySelector('.editable-slide [data-element-id].selected'),r=el.getBoundingClientRect();el.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2,clientX:r.x+r.width/2,clientY:r.y+r.height/2}))})()");
        waitFor("!!document.querySelector('.object-context-menu')");
        SystemClock.sleep(300);inst.waitForIdleSync();
        assertEquals("true",js("(()=>{const m=document.querySelector('.object-context-menu').getBoundingClientRect(),f=document.querySelector('.statusbar').getBoundingClientRect();return m.bottom<=f.top-7})()"));
        js("[...document.querySelectorAll('.object-context-menu button')].find(b=>b.textContent.trim()==='개체 서식').scrollIntoView({block:'nearest'})");
        waitForFocus();SystemClock.sleep(300);inst.waitForIdleSync();
        JSONArray point=new JSONArray(js("(()=>{const b=[...document.querySelectorAll('.object-context-menu button')].find(b=>b.textContent.trim()==='개체 서식'),r=b.getBoundingClientRect();return [(r.x+r.width/2)*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
        int[] origin=new int[2];boolean[] safe={false};
        inst.runOnMainSync(()->{activity.web.getLocationOnScreen(origin);safe[0]=point.optDouble(1)<activity.web.getHeight()-activity.slideSafeInsets.bottom;});
        assertTrue("Format command must be above native navigation icons",safe[0]);
        assertTrue(UiDevice.getInstance(inst).takeScreenshot(new java.io.File(activity.getExternalFilesDir(null),"object-menu-native-open.png")));
        assertTrue(UiDevice.getInstance(inst).click(origin[0]+(int)Math.round(point.getDouble(0)),origin[1]+(int)Math.round(point.getDouble(1))));
        waitFor("!document.querySelector('.object-context-menu') && !!document.querySelector('.inspector:not([hidden])')");
        SystemClock.sleep(600);inst.waitForIdleSync();
        assertEquals("true",js("(()=>{const r=document.querySelector('.inspector').getBoundingClientRect();return r.width>0&&r.height>0})()"));
        assertEquals("false",js("!!document.querySelector('.presentation-view,.slide-sorter')"));
        assertEquals("true",js("!!document.querySelector('.editable-slide [data-element-id].selected')"));
        assertTrue(UiDevice.getInstance(inst).takeScreenshot(new java.io.File(activity.getExternalFilesDir(null),"object-menu-native-format.png")));
        System.out.println("Menu format action retained editor and selection; native bottom inset="+activity.slideSafeInsets.bottom);
    }
}
