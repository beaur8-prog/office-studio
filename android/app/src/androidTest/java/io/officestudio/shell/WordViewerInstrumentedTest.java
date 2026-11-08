package io.officestudio.shell;

import android.os.SystemClock;
import android.content.res.Configuration;
import android.view.inputmethod.InputMethodManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;
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
        screenshot("failed-wait");
        fail("Timed out: "+expression+"; gestures="+js(scenario,"JSON.stringify(window.viewerGestureQA||null)")+"; body="+js(scenario,"document.body.innerText.slice(-500)"));
    }
    void openFixture(ActivityScenario<MainActivity> scenario,String name) throws Exception {
        js(scenario,"fetch('/assets/qa/"+name+"').then(r=>r.blob()).then(b=>{const d=new DataTransfer();d.items.add(new File([b],'"+name+"'));const i=document.querySelector('input[type=file]');i.files=d.files;i.dispatchEvent(new Event('change',{bubbles:true}));})");
    }
    void assertFormFactor(ActivityScenario<MainActivity> scenario) throws Exception {
        waitJS(scenario,"typeof window.__studioHostConfig?.tablet==='boolean'");
        boolean tablet="tablet".equals(InstrumentationRegistry.getArguments().getString("studioQaProfile","phone"));
        assertEquals("Native hardware form factor must match the QA profile",String.valueOf(tablet),js(scenario,"window.__studioHostConfig.tablet"));
    }
    void traceViewer(ActivityScenario<MainActivity> scenario) throws Exception {
        js(scenario,"(()=>{const v=document.querySelector('.android-presentation-viewport');window.viewerGestureQA={width:innerWidth,height:innerHeight,ratio:devicePixelRatio,viewport:[v.clientWidth,v.clientHeight,v.scrollWidth,v.scrollHeight],rect:v.getBoundingClientRect().toJSON(),events:[]};for(const type of ['pointerdown','pointermove','pointerup','pointercancel'])v.addEventListener(type,e=>{if(type==='pointerdown'&&e.isPrimary)window.viewerGestureQA.events=[];if(window.viewerGestureQA.events.length<30)window.viewerGestureQA.events.push([type,e.pointerType,e.clientX,e.clientY,v.scrollTop,performance.now(),e.timeStamp,Number(v.closest('.android-presentation').dataset.scale)])},true)})()");
    }
    void dismissImmersiveGuide(){
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // A fresh CI emulator can show a launcher ANR while our Activity is
        // already running. Close only that unrelated process's OS dialog;
        // never dismiss a Word ANR or change product behavior to hide one.
        if(device.hasObject(By.text("Pixel Launcher isn't responding"))){
            UiObject2 close=device.findObject(By.text("Close app"));
            assertNotNull("Launcher ANR has no close action",close);close.click();device.waitForIdle();
        }
        UiObject2 gotIt=device.wait(Until.findObject(By.text("Got it")),1200);
        if(gotIt!=null){gotIt.click();device.waitForIdle();}
    }
    void screenshot(String name){
        dismissImmersiveGuide();
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"viewer-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(dir,name+".png")));
    }
    @Test public void newPageUsesRealA4AndHorizontalTypingWithHiddenStatusBar() throws Exception {
        assertEquals("word",BuildConfig.STUDIO);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitJS(scenario,"!!document.querySelector('.android-start button')");
            waitJS(scenario,"typeof window.wordAndroid.setImmersiveMode==='function'");
            assertFormFactor(scenario);
            SystemClock.sleep(700);dismissImmersiveGuide();
            UiDevice keys=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertTrue(keys.pressKeyCode(KeyEvent.KEYCODE_ESCAPE));
            assertTrue(keys.pressKeyCode(KeyEvent.KEYCODE_ESCAPE));
            assertEquals("RESUMED",scenario.getState().name());
            waitJS(scenario,"!!document.querySelector('.android-start button')");
            scenario.onActivity(activity->{
                WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
                assertNotNull(insets);assertTrue(activity.immersive);
                assertFalse("Word status bar should be hidden",insets.isVisible(WindowInsetsCompat.Type.statusBars()));
                assertEquals("Document viewport must extend under the cutout; controls use CSS safe insets",0,activity.root.getPaddingTop());
            });
            js(scenario,"[...document.querySelectorAll('.android-start button')].find(b=>b.textContent==='새 문서').click()");
            waitJS(scenario,"!!document.querySelector('.tiptap[contenteditable=true]')");
            assertEquals("true",js(scenario,"(()=>{const p=document.querySelector('.document-stack');const r=p.getBoundingClientRect();return p.offsetWidth>790&&Number(p.dataset.pageHeight)>1120&&(document.querySelector('.office-shell')?r.width>790:r.width>window.innerWidth-40)&&r.width<=window.innerWidth})()"));
            js(scenario,"(()=>{const e=document.querySelector('.tiptap');e.focus();document.execCommand('insertText',false,'ANDROID HORIZONTAL INPUT');})()");
            waitJS(scenario,"document.querySelector('.tiptap').textContent.includes('ANDROID HORIZONTAL INPUT')");
            assertEquals("true",js(scenario,"(()=>{const n=document.querySelector('.tiptap p').firstChild,a=document.createRange(),b=document.createRange();a.setStart(n,0);a.setEnd(n,1);b.setStart(n,1);b.setEnd(n,2);const x=a.getBoundingClientRect(),y=b.getBoundingClientRect();return Math.abs(x.y-y.y)<1&&y.x>x.x})()"));
            assertEquals("true",js(scenario,"document.querySelector('.office-shell')?!!document.querySelector('.office-shell .ribbon'):!!document.querySelector('.android-editor-ribbon [role=tab][aria-selected=true]')"));
            js(scenario,"[...document.querySelectorAll('[aria-label=\"전체 화면 뷰어\"]')].find(b=>b.getBoundingClientRect().height>0).click()");
            waitJS(scenario,"!!document.querySelector('.android-presentation-page .print-page')&&!document.querySelector('.android-presentation-loading')");
            waitJS(scenario,"document.querySelector('.android-presentation-page').textContent.includes('ANDROID HORIZONTAL INPUT')");
            waitJS(scenario,"document.querySelector('.android-presentation-controls').hidden");
            assertEquals("true",js(scenario,"document.querySelector('.android-presentation-modebar [aria-label=\"편집 모드\"]').getBoundingClientRect().height>=48"));
            screenshot("docx-mode-switch");
            assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ESCAPE));
            waitJS(scenario,"!document.querySelector('.android-presentation')&&!!document.querySelector('.tiptap[contenteditable=true]')");
            assertEquals("true",js(scenario,"document.querySelector('.tiptap').textContent.includes('ANDROID HORIZONTAL INPUT')"));
            waitJS(scenario,"document.activeElement===document.querySelector('.tiptap')");
            assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_X));
            waitJS(scenario,"document.querySelector('.tiptap').textContent.toLowerCase().endsWith('inputx')");
            assertTrue(keys.pressKeyCode(KeyEvent.KEYCODE_ESCAPE));
            assertEquals("RESUMED",scenario.getState().name());
            waitJS(scenario,"!document.querySelector('.android-presentation')&&document.querySelector('.tiptap').textContent.toLowerCase().endsWith('inputx')");
            screenshot("new-page-horizontal-input");
        }
    }
    @Test public void realWebViewSwipesPinchesAndHwpViewerKeepTheSelectedPage() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitJS(scenario,"!!document.querySelector('.android-start button')");assertFormFactor(scenario);openFixture(scenario,"viewer.docx");
            waitJS(scenario,"!!document.querySelector('.android-paper-viewport')");
            dismissImmersiveGuide();
            js(scenario,"document.querySelector('[aria-label=\"전체 화면 뷰어\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-presentation-page .print-page')&&!document.querySelector('.android-presentation-loading')");
            dismissImmersiveGuide();
            scenario.onActivity(activity->assertTrue("Word must own input focus before real gestures",activity.hasWindowFocus()));
            // A landscape tablet's width fit makes a portrait sheet taller
            // than the screen. Test page-turn swipes at whole-page fit;
            // zoomed sheets intentionally pan inside the current page.
            js(scenario,"document.querySelector('.android-presentation-options > button').click()");
            waitJS(scenario,"document.querySelector('.android-presentation-page').getBoundingClientRect().height<=innerHeight+2");
            traceViewer(scenario);
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
            screenshot("docx-viewer-pinch");
            double keptScale=Double.parseDouble(js(scenario,"Number(document.querySelector('.android-presentation').dataset.scale)"));
            js(scenario,"document.querySelector('[aria-label=\"다음 페이지\"]').click()");
            waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='3'");
            assertEquals(keptScale,Double.parseDouble(js(scenario,"Number(document.querySelector('.android-presentation').dataset.scale)")),0.00001);
            for(int n=0;n<6;n++){js(scenario,"document.querySelector('[aria-label=\"뷰어 축소\"]').click()");SystemClock.sleep(100);}
            js(scenario,"document.querySelector('[aria-label=\"이전 페이지\"]').click()");SystemClock.sleep(100);
            js(scenario,"document.querySelector('[aria-label=\"이전 페이지\"]').click()");
            waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='1'");
            waitJS(scenario,"(()=>{const v=document.querySelector('.android-presentation-viewport').getBoundingClientRect();return [...document.querySelectorAll('.android-presentation-slot')].filter(e=>{const r=e.getBoundingClientRect();return r.bottom>v.top+4&&r.top<v.bottom-4}).length>=2})()");
            screenshot("docx-continuous-small");
            js(scenario,"document.querySelector('[aria-label=\"뷰어 닫기\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-paper-viewport')");
            openFixture(scenario,"landscape-pagination.hwp");waitJS(scenario,"!!document.querySelector('.hwp-reader [data-testid=hwp-page]')");
            js(scenario,"document.querySelector('[aria-label=\"한글 전체 화면 뷰어\"]').click()");
            waitJS(scenario,"!!document.querySelector('.android-presentation-page svg')");
            dismissImmersiveGuide();
            scenario.onActivity(activity->assertTrue("HWP viewer must own input focus",activity.hasWindowFocus()));
            traceViewer(scenario);
            screenshot("hwp-before-swipe");
            assertTrue(device.swipe(w*4/5,h/2,w/5,h/2,10));waitJS(scenario,"document.querySelector('.android-presentation').dataset.page==='2'");
            scenario.onActivity(activity->{assertTrue(activity.immersive);assertEquals(View.VISIBLE,activity.web.getVisibility());});
            waitJS(scenario,"!!document.querySelector('.android-presentation-page svg')");
            screenshot("hwp-viewer");
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ESCAPE));
            waitJS(scenario,"!document.querySelector('.android-presentation')&&!!document.querySelector('.hwp-reader [data-testid=hwp-page]')");
            assertEquals("RESUMED",scenario.getState().name());
            js(scenario,"document.querySelector('[aria-label=\"한글 원본 보기 닫기\"]').click()");
            js(scenario,"document.querySelector('.android-topbar [aria-label=\"편집 모드\"]').click()");
            waitJS(scenario,"!!document.querySelector('.tiptap[contenteditable=true]')");
            waitJS(scenario,"document.querySelector('.tiptap').textContent.includes('QA PAGE 3')");
            for(int n=0;n<8;n++){
                js(scenario,"(document.querySelector('.android-tablet-editor [aria-label=\"축소\"]')||document.querySelector('[aria-label=\"편집 축소\"]')).click()");SystemClock.sleep(120);
            }
            waitJS(scenario,"(()=>{const v=document.querySelector('.android-tablet-editor[data-active=true] .document-workspace')||document.querySelector('.android-editor-scroll');const r=v.getBoundingClientRect();return [...v.querySelectorAll('.paper')].filter(p=>{const b=p.getBoundingClientRect();return b.bottom>r.top+4&&b.top<r.bottom-4}).length>=2})()");
            screenshot("editor-continuous-small");
            String scaleExpression="(()=>{const p=document.querySelector('.android-tablet-editor[data-active=true] .scaled-document');return p?p.getBoundingClientRect().width/p.offsetWidth:Number(document.querySelector('.android-editor-scroll').dataset.scale)})()";
            double editorScale=Double.parseDouble(js(scenario,scaleExpression));
            JSONArray point=new JSONArray(js(scenario,"(()=>{const v=document.querySelector('.android-tablet-editor[data-active=true] .document-workspace')||document.querySelector('.android-editor-scroll');const r=v.getBoundingClientRect(),d=devicePixelRatio;return [(r.x+r.width/2)*d,(r.y+r.height/2)*d,Math.min(r.width*.12,r.height*.2)*d]})()"));
            pinchAt((float)point.getDouble(0),(float)point.getDouble(1),(float)point.getDouble(2));
            waitJS(scenario,scaleExpression+">"+(editorScale*1.5));
            assertEquals("true",js(scenario,"(()=>{const label=document.querySelector('.android-tablet-editor[data-active=true] .zoom-value');return !label||/^\\d+%$/.test(label.textContent.trim())})()"));
            screenshot("editor-pinch");
        }
    }
    @Test public void hardwareKeyboardEscapeInNormalModeKeepsTheActivityAndDocument() throws Exception {
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String previousIme=device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        int keyboard=-1;
        for(int id:InputDevice.getDeviceIds()){
            InputDevice input=InputDevice.getDevice(id);
            if(input!=null&&input.supportsSource(InputDevice.SOURCE_KEYBOARD)&&input.getKeyboardType()==InputDevice.KEYBOARD_TYPE_ALPHABETIC){keyboard=id;if(!input.isVirtual())break;}
        }
        assertTrue("A hardware alphabetic keyboard must be attached to the AVD",keyboard>=0);
        final int keyboardId=keyboard;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitJS(scenario,"!!document.querySelector('.android-start button')");assertFormFactor(scenario);dismissImmersiveGuide();
            AtomicReference<MainActivity> original=new AtomicReference<>();
            scenario.onActivity(activity->{
                original.set(activity);
                Configuration config=activity.getResources().getConfiguration();
                assertEquals(Configuration.KEYBOARD_QWERTY,config.keyboard);
                assertFalse("Normal tablet mode, not a desktop/multiwindow session",activity.isInMultiWindowMode());
                assertFalse(activity.isInPictureInPictureMode());
                assertNotEquals(Configuration.UI_MODE_TYPE_DESK,config.uiMode&Configuration.UI_MODE_TYPE_MASK);
            });
            InputDevice input=InputDevice.getDevice(keyboardId);
            JSONObject environment=new JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("android",android.os.Build.VERSION.RELEASE)
                .put("keyboardDeviceId",keyboardId).put("keyboardName",input.getName()).put("keyboardSources",input.getSources())
                .put("keyboardIsVirtual",input.isVirtual()).put("normalWindowMode",true).put("bluetoothRadioTested",false);
            File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"viewer-qa");
            assertTrue(dir.isDirectory()||dir.mkdirs());
            try(java.io.FileWriter writer=new java.io.FileWriter(new File(dir,"keyboard-environment.json"))){writer.write(environment.toString(2));}
            js(scenario,"[...document.querySelectorAll('.android-start button')].find(b=>b.textContent==='새 문서').click()");
            waitJS(scenario,"!!document.querySelector('.tiptap[contenteditable=true]')");
            js(scenario,"(()=>{const e=document.querySelector('.tiptap');e.focus();document.execCommand('insertText',false,'BT ESC KEEP');window.hardwareEscapeCount=0;window.addEventListener('studio-native-escape',()=>window.hardwareEscapeCount++)})()");
            for(boolean showIme:new boolean[]{false,true}){
                device.executeShellCommand("settings put secure show_ime_with_hard_keyboard "+(showIme?"1":"0"));
                js(scenario,"document.querySelector('.tiptap').focus()");
                scenario.onActivity(activity->{
                    InputMethodManager ime=(InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if(showIme)ime.showSoftInput(activity.web,InputMethodManager.SHOW_IMPLICIT);
                    else ime.hideSoftInputFromWindow(activity.web.getWindowToken(),0);
                });
                long deadline=SystemClock.uptimeMillis()+10000;AtomicReference<Boolean> shown=new AtomicReference<>(null);
                do{
                    scenario.onActivity(activity->shown.set(ViewCompat.getRootWindowInsets(activity.root).isVisible(WindowInsetsCompat.Type.ime())));
                    if(Boolean.valueOf(showIme).equals(shown.get()))break;
                    SystemClock.sleep(100);
                }while(SystemClock.uptimeMillis()<deadline);
                assertEquals("IME state must be measured before testing Escape",Boolean.valueOf(showIme),shown.get());
                js(scenario,"window.hardwareEscapeCount=0;[...document.querySelectorAll('[aria-label=\"전체 화면 뷰어\"]')].find(b=>b.getBoundingClientRect().height>0).click()");
                waitJS(scenario,"!!document.querySelector('.android-presentation')");
                long down=SystemClock.uptimeMillis();
                injectKeyboard(keyboardId,KeyEvent.KEYCODE_ESCAPE,KeyEvent.ACTION_DOWN,0,down);
                SystemClock.sleep(150);injectKeyboard(keyboardId,KeyEvent.KEYCODE_ESCAPE,KeyEvent.ACTION_DOWN,1,down);
                injectKeyboard(keyboardId,KeyEvent.KEYCODE_ESCAPE,KeyEvent.ACTION_UP,0,down);
                waitJS(scenario,"!document.querySelector('.android-presentation')&&!!document.querySelector('.tiptap[contenteditable=true]')");
                assertEquals("A held Escape must close the viewer exactly once", "1",js(scenario,"window.hardwareEscapeCount"));
                scenario.onActivity(activity->{assertSame(original.get(),activity);assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());});
                assertEquals("RESUMED",scenario.getState().name());
                waitJS(scenario,"document.activeElement===document.querySelector('.tiptap')");
                long typed=SystemClock.uptimeMillis();injectKeyboard(keyboardId,KeyEvent.KEYCODE_X,KeyEvent.ACTION_DOWN,0,typed);injectKeyboard(keyboardId,KeyEvent.KEYCODE_X,KeyEvent.ACTION_UP,0,typed);
                waitJS(scenario,"document.querySelector('.tiptap').textContent.toLowerCase().endsWith('"+(showIme?"xx":"x")+"')");
                long again=SystemClock.uptimeMillis();injectKeyboard(keyboardId,KeyEvent.KEYCODE_ESCAPE,KeyEvent.ACTION_DOWN,0,again);injectKeyboard(keyboardId,KeyEvent.KEYCODE_ESCAPE,KeyEvent.ACTION_UP,0,again);
                waitJS(scenario,"!document.querySelector('.android-presentation')&&document.querySelector('.tiptap').textContent.includes('BT ESC KEEP')");
                scenario.onActivity(activity->{assertSame(original.get(),activity);assertFalse(activity.isFinishing());});
                assertEquals("RESUMED",scenario.getState().name());
                screenshot(showIme?"hardware-escape-ime-visible":"hardware-escape-ime-hidden");
            }
        }finally{
            if(previousIme.matches("[01]"))device.executeShellCommand("settings put secure show_ime_with_hard_keyboard "+previousIme);
            else device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
        }
    }
    void injectKeyboard(int device,int code,int action,int repeat,long down){
        KeyEvent event=new KeyEvent(down,SystemClock.uptimeMillis(),action,code,repeat,0,device,code==KeyEvent.KEYCODE_ESCAPE?1:0,
            KeyEvent.FLAG_FROM_SYSTEM|(repeat>0?KeyEvent.FLAG_LONG_PRESS:0),InputDevice.SOURCE_KEYBOARD);
        assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));
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
    void pinchAt(float x,float y,float span){
        long down=SystemClock.uptimeMillis();MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[2];
        for(int i=0;i<2;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        send(down,MotionEvent.ACTION_DOWN,1,props,x-span,y,x+span,y);
        send(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,x-span,y,x+span,y);
        for(int n=1;n<=12;n++){float s=span*(1+n/12f);send(down,MotionEvent.ACTION_MOVE,2,props,x-s,y,x+s,y);SystemClock.sleep(16);}
        send(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,props,x-span*2,y,x+span*2,y);
        send(down,MotionEvent.ACTION_UP,1,props,x-span*2,y,x+span*2,y);
    }
    void send(long down,int action,int count,MotionEvent.PointerProperties[] props,float x,float y,float x2,float y2){
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){coords[i]=new MotionEvent.PointerCoords();coords[i].x=i==0?x:x2;coords[i].y=i==0?y:y2;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
    }
}
