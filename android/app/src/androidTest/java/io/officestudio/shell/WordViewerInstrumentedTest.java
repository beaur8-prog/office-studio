package io.officestudio.shell;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Runs only on an isolated Word debug APK with public/synthetic QA assets. */
@RunWith(AndroidJUnit4.class)
public class WordViewerInstrumentedTest {
    String js(ActivityScenario<MainActivity> scenario,String expression) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        scenario.onActivity(activity->activity.web.evaluateJavascript(expression,value->{result.set(value);latch.countDown();}));
        assertTrue("JavaScript reply timed out",latch.await(15,TimeUnit.SECONDS));return result.get();
    }
    void waitJS(ActivityScenario<MainActivity> scenario,String expression) throws Exception {
        long end=SystemClock.uptimeMillis()+90000;
        while(SystemClock.uptimeMillis()<end){if("true".equals(js(scenario,expression)))return;SystemClock.sleep(250);}
        fail("Timed out: "+expression+"; body="+js(scenario,"document.body.innerText.slice(-1500)"));
    }
    void openFixture(ActivityScenario<MainActivity> scenario,String name) throws Exception {
        js(scenario,"fetch('/assets/qa/"+name+"').then(r=>r.blob()).then(b=>{const d=new DataTransfer();d.items.add(new File([b],'"+name+"'));const i=document.querySelector('input[type=file]');i.files=d.files;i.dispatchEvent(new Event('change',{bubbles:true}));})");
    }
    @Test public void newPageUsesRealA4AndHorizontalTypingWithHiddenStatusBar() throws Exception {
        assertEquals("word",BuildConfig.STUDIO);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitJS(scenario,"!!document.querySelector('.android-start button')");
            waitJS(scenario,"typeof window.wordAndroid.setImmersiveMode==='function'");
            SystemClock.sleep(700);
            scenario.onActivity(activity->{
                WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
                assertNotNull(insets);assertTrue(activity.immersive);
                assertFalse("Word status bar should be hidden",insets.isVisible(WindowInsetsCompat.Type.statusBars()));
                assertEquals(insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top,activity.root.getPaddingTop());
            });
            js(scenario,"[...document.querySelectorAll('.android-start button')].find(b=>b.textContent==='새 문서').click()");
            waitJS(scenario,"!!document.querySelector('.tiptap[contenteditable=true]')");
            assertEquals("true",js(scenario,"(()=>{const p=document.querySelector('.document-stack');const r=p.getBoundingClientRect();return p.offsetWidth>790&&Number(p.dataset.pageHeight)>1120&&r.width>window.innerWidth-40&&r.width<=window.innerWidth})()"));
            js(scenario,"(()=>{const e=document.querySelector('.tiptap');e.focus();document.execCommand('insertText',false,'ANDROID HORIZONTAL INPUT');})()");
            waitJS(scenario,"document.querySelector('.tiptap').textContent.includes('ANDROID HORIZONTAL INPUT')");
            assertEquals("true",js(scenario,"(()=>{const n=document.querySelector('.tiptap p').firstChild,a=document.createRange(),b=document.createRange();a.setStart(n,0);a.setEnd(n,1);b.setStart(n,1);b.setEnd(n,2);const x=a.getBoundingClientRect(),y=b.getBoundingClientRect();return Math.abs(x.y-y.y)<1&&y.x>x.x})()"));
        }
    }
    @Test public void realWebViewSwipesPinchesAndHwpViewerKeepTheSelectedPage() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitJS(scenario,"!!document.querySelector('.android-start button')");openFixture(scenario,"viewer.docx");
            waitJS(scenario,"!!document.querySelector('.android-paper-viewport')");
            js(scenario,"document.querySelector('[aria-label=\"전체 화면 뷰어\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-presentation-page .print-page')");
            UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            int w=device.getDisplayWidth(),h=device.getDisplayHeight();
            assertTrue(device.swipe(w*4/5,h/2,w/5,h/2,10));waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='2'");
            waitJS(scenario,"document.querySelector('.android-presentation-page').textContent.includes('QA PAGE 2')");
            assertTrue(device.swipe(w/5,h/2,w*4/5,h/2,10));waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='1'");
            assertTrue(device.swipe(w/2,h*3/4,w/2,h/4,10));waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='2'");
            double before=Double.parseDouble(js(scenario,"document.querySelector('.android-presentation-page').getBoundingClientRect().width"));
            pinch(w,h);
            waitJS(scenario,"document.querySelector('.android-presentation-page').getBoundingClientRect().width>"+(before*1.5));
            assertEquals("true",js(scenario,"document.querySelector('.android-presentation').dataset.page==='2'"));
            js(scenario,"document.querySelector('[aria-label=\"뷰어 닫기\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-paper-viewport')");
            openFixture(scenario,"landscape-pagination.hwp");waitJS(scenario,"!!document.querySelector('.hwp-reader [data-testid=hwp-page]')");
            js(scenario,"document.querySelector('[aria-label=\"한글 전체 화면 뷰어\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-presentation-page svg')");
            assertTrue(device.swipe(w*4/5,h/2,w/5,h/2,10));waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='2'");
            scenario.onActivity(activity->{assertTrue(activity.immersive);assertEquals(View.VISIBLE,activity.web.getVisibility());});
        }
    }
    void pinch(int width,int height){
        long down=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[2];
        for(int i=0;i<2;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        send(down,MotionEvent.ACTION_DOWN,1,props,width*.4f,height*.45f,width*.6f,height*.55f);
        send(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,width*.4f,height*.45f,width*.6f,height*.55f);
        for(int step=1;step<=12;step++){float t=step/12f;send(down,MotionEvent.ACTION_MOVE,2,props,width*(.4f-.15f*t),height*(.45f-.08f*t),width*(.6f+.15f*t),height*(.55f+.08f*t));SystemClock.sleep(16);}
        send(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,width*.25f,height*.37f,width*.75f,height*.63f);
        send(down,MotionEvent.ACTION_UP,1,props,width*.25f,height*.37f,width*.75f,height*.63f);
    }
    void send(long down,int action,int count,MotionEvent.PointerProperties[] props,float x,float y,float x2,float y2){
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){coords[i]=new MotionEvent.PointerCoords();coords[i].x=i==0?x:x2;coords[i].y=i==0?y:y2;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
    }
}
