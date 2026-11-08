package io.officestudio.shell;

import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** System print UI and real PDF output; no private/user documents are used. */
@RunWith(AndroidJUnit4.class)
public class WordPrintInstrumentedTest {
    final WordViewerInstrumentedTest ui=new WordViewerInstrumentedTest();
    final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    File evidence() {
        File folder=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"viewer-qa");
        assertTrue(folder.isDirectory()||folder.mkdirs());return folder;
    }
    void shot(String name) { assertTrue(device.takeScreenshot(new File(evidence(),name+".png"))); }
    void until(BooleanSupplier check,long timeout,String message)throws Exception {
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end){if(check.getAsBoolean())return;SystemClock.sleep(150);}
        String stage=message.toLowerCase().replaceAll("[^a-z0-9]+","-");
        shot("print-failed-"+stage);device.dumpWindowHierarchy(new File(evidence(),"print-failed-"+stage+".xml"));
        fail(message+"; foreground="+device.getCurrentPackageName());
    }
    void printWindow(ActivityScenario<MainActivity> scenario)throws Exception {
        SystemClock.sleep(1200);
        System.out.println("PRINT-DIAGNOSTIC "+ui.js(scenario,"JSON.stringify({events:window.printKeyQA||[],body:document.body.innerText.slice(-1200),focus:document.activeElement?.outerHTML.slice(0,150)})"));
        scenario.onActivity(activity->System.out.println("PRINT-NATIVE pending="+activity.pending+" printSession="+(activity.wordPrint!=null)+" webFocus="+activity.web.hasFocus()));
        until(()->"com.android.printspooler".equals(device.getCurrentPackageName()),90000,"System print window did not open");
        until(()->{UiObject2 button=device.findObject(By.res("com.android.printspooler:id/print_button"));return button!=null&&button.isEnabled();},90000,"Print preview did not become ready");
    }
    void returned()throws Exception {
        until(()->"io.officestudio.word".equals(device.getCurrentPackageName()),30000,"Word did not regain focus after printing");
    }
    File savePdf(String filename)throws Exception {
        assertTrue(filename.matches("[A-Za-z0-9.-]+"));
        if(!device.hasObject(By.text("Save as PDF"))){
            UiObject2 destination=device.findObject(By.res("com.android.printspooler:id/destination_spinner"));assertNotNull(destination);destination.click();
            UiObject2 pdf=device.wait(Until.findObject(By.text("Save as PDF")),10000);assertNotNull(pdf);pdf.click();
        }
        until(()->{UiObject2 button=device.findObject(By.res("com.android.printspooler:id/print_button"));return button!=null&&button.isEnabled();},30000,"PDF action unavailable");
        shot("print-system-preview-"+filename);
        device.findObject(By.res("com.android.printspooler:id/print_button")).click();
        until(()->device.getCurrentPackageName()!=null&&device.getCurrentPackageName().contains("documentsui"),30000,"System PDF save picker did not open");
        if(!device.hasObject(By.text("Downloads"))){
            UiObject2 roots=device.findObject(By.desc("Show roots"));if(roots!=null){roots.click();UiObject2 downloads=device.wait(Until.findObject(By.text("Downloads")),5000);if(downloads!=null)downloads.click();}
        }
        UiObject2 name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000);assertNotNull(name);name.setText(filename);
        UiObject2 save=device.findObject(By.res("android:id/button1"));if(save==null)save=device.findObject(By.text(Pattern.compile("(?i)save")));assertNotNull("No system Save button",save);save.click();
        returned();
        until(()->{try{return device.executeShellCommand("test -s /sdcard/Download/"+filename+" && echo ready").trim().equals("ready");}catch(IOException error){return false;}},30000,"Saved PDF missing from Downloads");
        File copy=new File(evidence(),filename);
        String copied=device.executeShellCommand("cp /sdcard/Download/"+filename+" '"+copy.getAbsolutePath()+"'");
        assertTrue("Cannot copy own QA PDF: "+copied,copy.isFile()&&copy.length()>100);return copy;
    }
    void pdf(File file,int expected)throws Exception {
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(descriptor)){
            assertEquals("Each source page must remain one output page",expected,pdf.getPageCount());JSONArray sizes=new JSONArray();
            for(int n=0;n<pdf.getPageCount();n++)try(PdfRenderer.Page page=pdf.openPage(n)){sizes.put(new JSONObject().put("width",page.getWidth()).put("height",page.getHeight()));}
            try(FileWriter writer=new FileWriter(new File(evidence(),file.getName()+".json"))){writer.write(new JSONObject().put("pages",pdf.getPageCount()).put("sizes",sizes).toString(2));}
        }
    }
    @Test public void docxPrintCancelThenSystemPdfKeepsEditingHistory()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ui.waitJS(scenario,"!!document.querySelector('.android-start button')");ui.openFixture(scenario,"print.docx");ui.waitJS(scenario,"!!document.querySelector('.android-paper-viewport')");ui.dismissImmersiveGuide();
            ui.js(scenario,"document.querySelector('[aria-label=\"알림 닫기\"]')?.click();document.querySelector('.android-topbar [aria-label=\"편집 모드\"]').click()");
            ui.waitJS(scenario,"!!document.querySelector('.tiptap[contenteditable=true]')");
            ui.js(scenario,"(()=>{const e=document.querySelector('.tiptap'),p=e.querySelector('p'),r=document.createRange();e.focus();r.selectNodeContents(p);r.collapse(false);const s=getSelection();s.removeAllRanges();s.addRange(r);document.execCommand('insertText',false,' PRINT EDIT')})()");
            ui.waitJS(scenario,"document.querySelector('.tiptap').textContent.includes('PRINT EDIT')");
            ui.js(scenario,"window.printKeyQA=[];window.addEventListener('keydown',e=>window.printKeyQA.push({key:e.key,code:e.code,ctrl:e.ctrlKey,meta:e.metaKey}),true)");
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_P,KeyEvent.META_CTRL_ON));printWindow(scenario);shot("print-before-cancel");device.pressBack();returned();
            ui.waitJS(scenario,"!document.querySelector('.android-operation')&&!!document.querySelector('.tiptap[contenteditable=true]')");
            assertEquals("RESUMED",scenario.getState().name());
            assertEquals("true",ui.js(scenario,"!document.body.classList.contains('studio-printing')&&document.querySelector('.tiptap').textContent.includes('PRINT EDIT')"));
            ui.waitJS(scenario,"document.activeElement===document.querySelector('.tiptap')");
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON));ui.waitJS(scenario,"!document.querySelector('.tiptap').textContent.includes('PRINT EDIT')");
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_Y,KeyEvent.META_CTRL_ON));ui.waitJS(scenario,"document.querySelector('.tiptap').textContent.includes('PRINT EDIT')");
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_P,KeyEvent.META_CTRL_ON));printWindow(scenario);File output=savePdf("WordStudio-Print-QA-docx.pdf");
            ui.waitJS(scenario,"!document.querySelector('.android-operation')&&!!document.querySelector('.tiptap[contenteditable=true]')");pdf(output,3);
            assertEquals("true",ui.js(scenario,"document.querySelector('.tiptap').textContent.includes('PRINT EDIT')&&!!document.querySelector('.tiptap table')&&!!document.querySelector('.tiptap img[src]')"));
            scenario.onActivity(activity->{assertNull(activity.pending);assertNull(activity.wordPrint);assertFalse(activity.isFinishing());});shot("print-docx-returned");
        }
    }
    @Test public void hwpOriginalSystemPdfKeepsAllTablePagesAndReturnsToReader()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ui.waitJS(scenario,"!!document.querySelector('.android-start button')");ui.openFixture(scenario,"landscape-pagination.hwp");ui.waitJS(scenario,"!!document.querySelector('.hwp-reader [data-testid=hwp-page]')");ui.dismissImmersiveGuide();
            ui.js(scenario,"document.querySelector('[aria-label=\"한글 문서 메뉴\"]').click()");
            ui.js(scenario,"[...document.querySelectorAll('.android-sheet button')].find(b=>b.textContent==='인쇄·PDF 저장').click()");
            printWindow(scenario);File output=savePdf("WordStudio-Print-QA-hwp.pdf");
            ui.waitJS(scenario,"!document.querySelector('.android-operation')&&!!document.querySelector('.hwp-reader [data-testid=hwp-page]')");pdf(output,4);
            assertEquals("true",ui.js(scenario,"!document.querySelector('.hwp-conversion-panel')&&!document.body.classList.contains('studio-printing')"));
            scenario.onActivity(activity->{assertNull(activity.pending);assertNull(activity.wordPrint);assertFalse(activity.isFinishing());});shot("print-hwp-returned");
        }
    }
}
