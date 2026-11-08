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
        screenshot("failed-wait");fail("Timed out: "+expression+"; body="+js(s,"document.body.innerText.slice(-900)")+"; touches="+js(s,"JSON.stringify(window.__qaTouches)"));
    }
    void ready(ActivityScenario<MainActivity> s) throws Exception {
        assertTrue("Never use the user's production package",InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName().endsWith(".qa"));
        waitJS(s,"!!window.sheetStudio && document.body.classList.contains('android-fullscreen')");
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // First-boot SystemUI can render the immersive guide after the page is
        // ready and return focus slowly on a cold CI emulator. Keep checking
        // the real guide throughout readiness instead of missing it once.
        long end=SystemClock.uptimeMillis()+30000;boolean[] focused={false};
        do{
            androidx.test.uiautomator.UiObject2 guide=device.findObject(androidx.test.uiautomator.By.text("Got it"));
            if(guide!=null)guide.click();device.waitForIdle(1000);
            s.onActivity(a->focused[0]=a.hasWindowFocus());if(!focused[0])SystemClock.sleep(200);
        }while(!focused[0]&&SystemClock.uptimeMillis()<end);
        if(!focused[0])screenshot("startup-window-focus");
        assertTrue("Wait for the immersive guide to return window focus",focused[0]);
        waitJS(s,"!document.querySelector('.workbook-busy') && !!document.getElementById('android-cell-0-0')");
        // Insets and WebView focus can still settle after the OS guide closes.
        String before=js(s,"JSON.stringify([innerWidth,innerHeight,document.querySelector('#grid').getBoundingClientRect().top])");
        do{SystemClock.sleep(350);String after=js(s,"JSON.stringify([innerWidth,innerHeight,document.querySelector('#grid').getBoundingClientRect().top])");if(before.equals(after))break;before=after;}while(SystemClock.uptimeMillis()<end);
    }
    void waitNativeViewer(ActivityScenario<MainActivity> s,boolean expected) {
        long end=SystemClock.uptimeMillis()+10000;boolean[] ready={false};
        do{s.onActivity(a->ready[0]=a.sheetViewer==expected);if(!ready[0])SystemClock.sleep(50);}while(!ready[0]&&SystemClock.uptimeMillis()<end);
        assertTrue("Native viewer acknowledgement",ready[0]);
    }
    void waitIme(ActivityScenario<MainActivity> s) throws Exception {
        long end=SystemClock.uptimeMillis()+15000;boolean[] showing={false};
        do{s.onActivity(a->{WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(a.root);showing[0]=insets!=null&&insets.isVisible(WindowInsetsCompat.Type.ime());});if(!showing[0])SystemClock.sleep(100);}while(!showing[0]&&SystemClock.uptimeMillis()<end);
        assertTrue("Real IME visible",showing[0]);
    }
    void settle(ActivityScenario<MainActivity> s) throws Exception {
        js(s,"window.__qaPainted=false;sheetStudio.whenIdle().then(()=>window.__qaPainted=true)");
        waitJS(s,"window.__qaPainted && !document.querySelector('.workbook-busy')");SystemClock.sleep(150);
    }
    void screenshot(String name){
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"sheet-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(dir,name+".png")));
    }
    void tap(ActivityScenario<MainActivity> s,String selector) throws Exception {
        js(s,"window.__qaTouches=[];for(const type of ['pointerdown','pointerup','pointercancel'])document.addEventListener(type,e=>{window.__qaTouches.push({type:e.type,pointer:e.pointerType,target:e.target.id,cell:e.target.closest('.cell')?.getAttribute('data-r'),active:document.activeElement.id,height:innerHeight});},{capture:true,once:true});");
        JSONObject p=new JSONObject(js(s,"(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth}})()"));
        int[] point=new int[2];
        s.onActivity(a->{try{int[] origin=new int[2];a.web.getLocationOnScreen(origin);float scale=a.web.getWidth()/(float)p.getDouble("width");point[0]=origin[0]+(int)(p.getDouble("x")*scale);point[1]=origin[1]+(int)(p.getDouble("y")*scale);}catch(Exception e){throw new AssertionError(e);}});
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(point[0],point[1]));
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
            ready(s);assertFullWindow(s);settle(s);screenshot("portrait-full-window");
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitJS(s,"innerWidth>innerHeight");SystemClock.sleep(400);assertFullWindow(s);settle(s);screenshot("landscape-full-window");
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        }
    }
    @Test public void liveDraftEnterKeepsImeAndCancelRestores() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);tap(s,"#android-cell-0-0");waitJS(s,"document.activeElement.id==='formula'");
            setDraft(s,"123");waitJS(s,"document.getElementById('android-cell-0-0').textContent==='123'");assertEquals("\"\"",js(s,"(sheetStudio.getCell('A1')?.raw??'')"));assertEquals("0",js(s,"sheetStudio.historyStats().undo"));
            long end=SystemClock.uptimeMillis()+10000;boolean[] ime={false};do{s.onActivity(a->{WindowInsetsCompat i=ViewCompat.getRootWindowInsets(a.root);ime[0]=i!=null&&i.isVisible(WindowInsetsCompat.Type.ime());});if(!ime[0])SystemClock.sleep(150);}while(!ime[0]&&SystemClock.uptimeMillis()<end);assertTrue("Touch entry must open a real IME",ime[0]);
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ENTER);
            waitJS(s,"sheetStudio.getSelection().r===1 && sheetStudio.getCell('A1')?.raw==='123' && document.activeElement.id==='formula'");
            s.onActivity(a->assertTrue("Enter must keep the real keyboard visible",ViewCompat.getRootWindowInsets(a.root).isVisible(WindowInsetsCompat.Type.ime())));
            setDraft(s,"456");waitJS(s,"document.getElementById('android-cell-1-0').textContent==='456'");settle(s);screenshot("live-entry-next-cell");js(s,"document.querySelector('[data-action=cancel-edit]').click()");waitJS(s,"document.getElementById('android-cell-1-0').textContent===''");assertEquals("\"\"",js(s,"(sheetStudio.getCell('A2')?.raw??'')"));
        }
    }
    @Test public void backCloseFinishesActivityAndRecoverySurvives() throws Exception {
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);s.onActivity(activity::set);
            js(s,"(()=>{const book=sheetStudio.getWorkbook();book.name='EXIT_QA';const d=new DataTransfer();d.items.add(new File([JSON.stringify(book)],'exit.sheet.json'));const i=document.querySelector('#file-input');i.files=d.files;i.dispatchEvent(new Event('change',{bubbles:true}));})()");
            waitJS(s,"sheetStudio.getWorkbook().name==='EXIT_QA' && !document.querySelector('.workbook-busy')");tap(s,"#android-cell-0-0");waitJS(s,"document.activeElement.id==='formula'");setDraft(s,"123");js(s,"document.querySelector('[data-action=commit-edit]').click()");waitJS(s,"sheetStudio.getCell('A1')?.raw==='123' && !document.body.classList.contains('android-cell-editing')");
            // First Back may dismiss IME; subsequent Back opens the app exit dialog.
            for(int i=0;i<3;i++){UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();SystemClock.sleep(500);if("true".equals(js(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')")))break;}
            waitJS(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')");js(s,"document.querySelector('[data-close]').click()");waitJS(s,"!document.querySelector('.backdrop')");assertFalse(activity.get().isFinishing());assertEquals("\"123\"",js(s,"sheetStudio.getCell('A1')?.raw"));
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();waitJS(s,"!!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')");settle(s);screenshot("exit-confirmation");js(s,"document.querySelector('[data-modal-button=\"0\"]').click()");
            long end=SystemClock.uptimeMillis()+10000;while(!activity.get().isFinishing()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);assertTrue("Close must finish the native Activity",activity.get().isFinishing());
        }
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);js(s,"document.querySelector('[data-mobile=more]').click()");waitJS(s,"!!document.querySelector('[data-mobile=recover]')");js(s,"document.querySelector('[data-mobile=recover]').click()");waitJS(s,"!!document.querySelector('[data-recover-document]')");js(s,"[...document.querySelectorAll('[data-recover-document]')].find(el=>el.textContent.includes('EXIT_QA')).click()");waitJS(s,"sheetStudio.getCell('A1')?.raw==='123'");assertEquals("\"\"",js(s,"(sheetStudio.getCell('A2')?.raw??'')"));settle(s);screenshot("recovery-after-exit");
        }
    }
    @Test public void viewerUsesWholeViewportAndHardwareEscapeReturnsToEditingWithoutClosing() throws Exception {
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);s.onActivity(activity::set);
            String before=js(s,"JSON.stringify({book:sheetStudio.getWorkbook(),history:sheetStudio.historyStats(),dirty:sheetStudio.isDirty()})");
            tap(s,".android-header [data-mobile=mode]");waitJS(s,"sheetStudio.isViewer() && document.body.classList.contains('viewer-mode')");settle(s);
            assertEquals("true",js(s,"(()=>{const r=document.querySelector('#grid-shell').getBoundingClientRect();return r.top===0 && Math.abs(r.height-innerHeight)<1})()"));
            screenshot("viewer-whole-window");
            js(s,"delete window.OfficeStudioHost");
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ESCAPE);
            waitJS(s,"!sheetStudio.isViewer() && !document.body.classList.contains('viewer-mode')");settle(s);
            assertFalse("Escape must not finish Activity",activity.get().isFinishing());
            assertEquals(before,js(s,"JSON.stringify({book:sheetStudio.getWorkbook(),history:sheetStudio.historyStats(),dirty:sheetStudio.isDirty()})"));
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ESCAPE);
            assertFalse("Escape key-up/repeat must not become Back",activity.get().isFinishing());
            tap(s,".android-header [data-mobile=mode]");waitJS(s,"sheetStudio.isViewer()");
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();waitJS(s,"!sheetStudio.isViewer()");
            assertFalse("Missing generic host bridge must never finish the viewer",activity.get().isFinishing());
            s.onActivity(a->androidx.core.view.WindowCompat.getInsetsController(a.getWindow(),a.getWindow().getDecorView()).hide(WindowInsetsCompat.Type.ime()));
        }
    }

    @Test public void fullscreenEntryKeepsImeAndEscapePreservesUncommittedDraft() throws Exception {
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);s.onActivity(activity::set);tap(s,".android-header [data-mobile=mode]");waitJS(s,"sheetStudio.isViewer()");settle(s);SystemClock.sleep(400);
            tap(s,"#android-cell-0-0");waitJS(s,"document.activeElement.id==='formula'");waitIme(s);setDraft(s,"111");
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ENTER);
            waitJS(s,"sheetStudio.getSelection().r===1 && sheetStudio.getCell('A1')?.raw==='111' && sheetStudio.isViewer() && document.activeElement.id==='formula'");
            s.onActivity(a->assertTrue("Viewer Enter retains IME",ViewCompat.getRootWindowInsets(a.root).isVisible(WindowInsetsCompat.Type.ime())));
            setDraft(s,"222");String before=js(s,"JSON.stringify({book:sheetStudio.getWorkbook(),history:sheetStudio.historyStats(),dirty:sheetStudio.isDirty()})");
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ESCAPE);
            waitJS(s,"!sheetStudio.isViewer() && document.querySelector('#formula').value==='222'");
            assertFalse(activity.get().isFinishing());assertEquals(before,js(s,"JSON.stringify({book:sheetStudio.getWorkbook(),history:sheetStudio.historyStats(),dirty:sheetStudio.isDirty()})"));
            assertEquals("true",js(s,"!document.querySelector('[role=dialog][aria-label=\"앱을 닫을까요?\"]')"));
            js(s,"document.querySelector('[data-action=cancel-edit]').click()");
        }
    }

    @Test public void keyboardBackKeyUpAndLegacyCloseCannotCloseAfterViewerExit() throws Exception {
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);s.onActivity(activity::set);tap(s,".android-header [data-mobile=mode]");waitJS(s,"sheetStudio.isViewer()");
            waitNativeViewer(s,true);s.onActivity(a->assertTrue("Viewer state reaches native host",a.sheetViewer));
            js(s,"window.__legacyInViewer=false;OfficeStudioHost.request('close').then(()=>window.__legacyInViewer=true)");waitJS(s,"window.__legacyInViewer");assertEquals("true",js(s,"sheetStudio.isViewer()"));assertFalse(activity.get().isFinishing());
            s.onActivity(a->assertTrue(a.web.onKeyPreIme(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK))));
            waitJS(s,"!sheetStudio.isViewer()");waitNativeViewer(s,false);
            s.onActivity(a->{assertFalse(a.sheetViewer);assertTrue(a.web.onKeyPreIme(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));assertFalse(a.sheetBackKeyDown);});
            assertFalse(activity.get().isFinishing());assertEquals("true",js(s,"!document.querySelector('.backdrop')"));
            js(s,"window.__legacyCloseDone=false;OfficeStudioHost.request('close').then(()=>window.__legacyCloseDone=true)");waitJS(s,"window.__legacyCloseDone");
            assertFalse("Legacy close callback is not confirmed close",activity.get().isFinishing());assertEquals("true",js(s,"!document.querySelector('.backdrop')"));
        }
    }
    @Test public void scrollingAndEnterDoNotPaintCalculationPlaceholders() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            ready(s);js(s,"(()=>{const book=sheetStudio.getWorkbook();book.name='SMOOTH_QA';book.sheets[0].cells={};for(let r=1;r<=1500;r++){book.sheets[0].cells['A'+r]={raw:String(r)};book.sheets[0].cells['B'+r]={raw:'=A'+r+'*2'};}const d=new DataTransfer();d.items.add(new File([JSON.stringify(book)],'smooth.sheet.json'));const i=document.querySelector('#file-input');i.files=d.files;i.dispatchEvent(new Event('change',{bubbles:true}));})()");
            waitJS(s,"sheetStudio.getWorkbook().name==='SMOOTH_QA' && !document.querySelector('.workbook-busy')");settle(s);
            js(s,"window.__smooth={frames:0,bad:0,active:true};(()=>{function frame(){if(!__smooth.active)return;__smooth.frames++;__smooth.bad+=[...document.querySelectorAll('#cells .cell')].filter(c=>c.textContent.includes('계산 중')).length;requestAnimationFrame(frame);}requestAnimationFrame(frame);})()");
            tap(s,"#android-cell-0-0");waitJS(s,"document.activeElement.id==='formula'");setDraft(s,"10");UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_ENTER);waitJS(s,"sheetStudio.getCell('A1')?.raw==='10' && sheetStudio.getSelection().r===1");
            for(int top:new int[]{1250,5000,17500,0}){js(s,"document.querySelector('#grid').scrollTop="+top);SystemClock.sleep(120);}
            settle(s);waitJS(s,"__smooth.frames>=10");assertEquals("0",js(s,"__smooth.bad"));js(s,"__smooth.active=false;document.querySelector('[data-action=cancel-edit]').click()");
        }
    }

    void mouseActionButton(MotionEvent event) {
        // Framework setter is outside the public SDK. Instrumentation alone is
        // started with --no-hidden-api-checks; the shipping app is unchanged.
        try{MotionEvent.class.getMethod("setActionButton",int.class).invoke(event,MotionEvent.BUTTON_PRIMARY);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    void mouse(ActivityScenario<MainActivity> scenario,int action,double x,double y,long downTime) {
        // Dispatch a real mouse MotionEvent through the native WebView. Global
        // injection is remapped by InputDispatcher after `wm size` + rotation;
        // it was landing far outside the requested heading on this emulator.
        scenario.onActivity(a->{
            float scale=a.web.getWidth()/(float)1920;
            MotionEvent.PointerProperties property=new MotionEvent.PointerProperties();property.id=0;property.toolType=MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=(float)x*scale;coords.y=(float)y*scale;coords.pressure=action==MotionEvent.ACTION_UP?0:1;coords.size=1;
            MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{property},new MotionEvent.PointerCoords[]{coords},0,action==MotionEvent.ACTION_UP?0:MotionEvent.BUTTON_PRIMARY,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
            try{
                // Chromium consumes DOWN/UP to retain the stream, but generates
                // mouse button DOM events from BUTTON_PRESS/BUTTON_RELEASE.
                // https://chromium.googlesource.com/chromium/src/+/main/ui/android/java/src/org/chromium/ui/base/EventForwarder.java
                if(action==MotionEvent.ACTION_UP){event.setAction(MotionEvent.ACTION_BUTTON_RELEASE);mouseActionButton(event);a.web.dispatchGenericMotionEvent(event);event.setAction(MotionEvent.ACTION_UP);}
                a.web.dispatchTouchEvent(event);
                if(action==MotionEvent.ACTION_DOWN){event.setAction(MotionEvent.ACTION_BUTTON_PRESS);mouseActionButton(event);a.web.dispatchGenericMotionEvent(event);}
            }finally{event.recycle();}
        });
    }
    @Test public void tabletNumbersDoNotShowTailDigitsAndMouseResizeCommitsOnce() throws Exception {
        assertTrue(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName().endsWith(".qa"));
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // Only this fresh QA emulator is resized; restore its display for other cases.
        device.executeShellCommand("wm size 1920x1200");device.executeShellCommand("wm density 160");
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));ready(s);
            waitJS(s,"innerWidth===1920 && innerHeight>=600");
            js(s,"window.dispatchEvent(new CustomEvent('sheet-studio-android-input',{detail:{hardwareKeyboard:true}}))");
            waitJS(s,"document.body.classList.contains('android-desktop-input')");
            js(s,"(()=>{const book=sheetStudio.getWorkbook();book.name='WIDTH_QA';const sh=book.sheets[0];sh.cells={A1:{raw:'123456789',style:{numberFormat:'0'}},A2:{raw:'1234567',style:{format:'currency'}},A3:{raw:'=A1*10',style:{numberFormat:'0'}},B1:{raw:'123456789012'}};sh.columnWidths={0:64,1:64};for(let r=100;r<100100;r++)sh.cells['C'+r]={raw:String(r)};const d=new DataTransfer();d.items.add(new File([JSON.stringify(book)],'width.sheet.json'));const i=document.querySelector('#file-input');i.files=d.files;i.dispatchEvent(new Event('change',{bubbles:true}));})()");
            waitJS(s,"sheetStudio.getWorkbook().name==='WIDTH_QA' && !document.querySelector('.workbook-busy')");settle(s);
            assertEquals("true",js(s,"/^#+$/.test(document.querySelector('#android-cell-0-0').textContent) && /^#+$/.test(document.querySelector('#android-cell-1-0').textContent) && /^#+$/.test(document.querySelector('#android-cell-2-0').textContent)"));
            assertEquals("true",js(s,"/E\\+11$/.test(document.querySelector('#android-cell-0-1').textContent)"));
            assertEquals("\"123456789\"",js(s,"document.querySelector('#formula').value"));
            screenshot("tablet-number-overflow");
            JSONObject p=new JSONObject(js(s,"(()=>{const r=document.querySelector('[data-resize-col=\"0\"]').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})()"));
            js(s,"window.__qaTouches=[];for(const type of ['pointerdown','pointermove','pointerup'])document.addEventListener(type,e=>__qaTouches.push({type,pointer:e.pointerType,x:e.clientX,y:e.clientY,target:e.target.outerHTML.slice(0,150)}),{capture:true})");
            long down=SystemClock.uptimeMillis();mouse(s,MotionEvent.ACTION_DOWN,p.getDouble("x"),p.getDouble("y"),down);
            waitJS(s,"__qaTouches.some(e=>e.type==='pointerdown' && e.pointer==='mouse') && document.body.dataset.dimensionResize==='c'");settle(s);
            js(s,"window.__widthRenders=0;window.__widthObserver=new MutationObserver(rs=>__widthRenders+=rs.length);__widthObserver.observe(document.querySelector('#cells'),{childList:true})");
            for(int n=1;n<=20;n++){mouse(s,MotionEvent.ACTION_MOVE,p.getDouble("x")+3*n,p.getDouble("y"),down);SystemClock.sleep(16);}
            waitJS(s,"document.querySelector('.dimension-guide-label').textContent==='열 너비: 124px'");
            assertEquals("64",js(s,"sheetStudio.getWorkbook().sheets[0].columnWidths[0]"));// A worker result or initial focus/viewport transition may repaint once.
            // Reject per-move redraws while requiring unchanged model dimensions.
            int redraws=Integer.parseInt(js(s,"__widthRenders"));System.out.println("TABLET_DRAG moves=20 gridRedraws="+redraws);
            assertTrue("Dragging must not redraw once for every native move: "+redraws,redraws<=2);
            screenshot("tablet-mouse-guide");js(s,"__widthObserver.disconnect()");
            mouse(s,MotionEvent.ACTION_UP,p.getDouble("x")+60,p.getDouble("y"),down);
            waitJS(s,"sheetStudio.getWorkbook().sheets[0].columnWidths[0]===124");settle(s);
            assertEquals("\"123456789\"",js(s,"document.querySelector('#android-cell-0-0').textContent"));
            assertEquals("true",js(s,"sheetStudio.getCell('A1').raw==='123456789' && !document.body.dataset.dimensionResize"));
            js(s,"document.querySelector('#undo').click()");settle(s);assertEquals("64",js(s,"sheetStudio.getWorkbook().sheets[0].columnWidths[0]"));
            js(s,"document.querySelector('#redo').click()");settle(s);assertEquals("124",js(s,"sheetStudio.getWorkbook().sheets[0].columnWidths[0]"));
        }finally{device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset");}
    }

}
