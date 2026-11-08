package io.officestudio.shell;

import android.app.Activity;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Executor;

/** One immutable document in its own renderer; the editor WebView stays intact. */
final class WordPrintSession {
    interface Result { void finish(String status, Exception error); }
    final Activity activity;
    final File file;
    final int widthMils, heightMils, pages;
    final Executor io;
    final Result result;
    final Handler handler = new Handler(Looper.getMainLooper());
    WebView renderer;
    PrintJob job;
    volatile boolean closed;
    boolean polling, launched;
    long deadline;

    WordPrintSession(Activity activity, File file, int width, int height, int pages, Executor io, Result result) {
        this.activity=activity; this.file=file; widthMils=width; heightMils=height; this.pages=pages; this.io=io; this.result=result;
    }
    void start() {
        io.execute(() -> {
            try {
                String html = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                handler.post(() -> { if (!closed) load(html); });
            } catch (Exception error) { handler.post(() -> finish(null,error)); }
        });
    }
    void load(String html) {
        try {
            renderer=new WebView(activity);
            WebSettings settings=renderer.getSettings();
            settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
            settings.setSupportMultipleWindows(false); settings.setTextZoom(100);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            renderer.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    Uri uri=request.getUrl();
                    if ("data".equals(uri.getScheme())) return null;
                    if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) &&
                        (uri.getPort()==-1 || uri.getPort()==443) && uri.getPath()!=null && uri.getPath().startsWith("/assets/")) {
                        String name=uri.getPath().substring(8);
                        if (!name.contains("..") && !name.contains("\\") && !name.startsWith("/")) {
                            try { return new WebResourceResponse(MainActivity.assetMime(name),"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),activity.getAssets().open(name)); }
                            catch (IOException ignored) { return MainActivity.blocked(404); }
                        }
                    }
                    return MainActivity.blocked(403);
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) finish(null,new IOException("인쇄 문서를 읽지 못했습니다."));
                }
                @Override public void onPageFinished(WebView view, String url) {
                    if (!polling && !closed) { polling=true; deadline=SystemClock.uptimeMillis()+30000; ready(); }
                }
            });
            renderer.loadDataWithBaseURL(MainActivity.ORIGIN+"/assets/public/index.html",html,"text/html","UTF-8",null);
        } catch (Exception error) { finish(null,error); }
    }
    void ready() {
        if (closed || launched) return;
        String script="(()=>{const images=[...document.images];if(images.some(i=>i.complete&&!i.naturalWidth)||[...document.fonts].some(f=>f.status==='error'))return 'failed';return document.readyState==='complete'&&document.fonts.status==='loaded'&&images.every(i=>i.complete)&&document.querySelectorAll('.android-print-sheet').length==="+pages+"?'ready':'waiting'})()";
        renderer.evaluateJavascript(script,value -> {
            if (closed) return;
            if ("\"ready\"".equals(value)) launch();
            else if ("\"failed\"".equals(value)) finish(null,new IOException("인쇄할 글꼴이나 그림을 읽지 못했습니다."));
            else if (SystemClock.uptimeMillis()>=deadline) finish(null,new IOException("인쇄 문서 준비 시간이 초과됐습니다. 다시 시도해 주세요."));
            else handler.postDelayed(this::ready,100);
        });
    }
    PrintAttributes.MediaSize media() {
        PrintAttributes.MediaSize[] standard={PrintAttributes.MediaSize.ISO_A4,PrintAttributes.MediaSize.ISO_A3,PrintAttributes.MediaSize.ISO_A5,PrintAttributes.MediaSize.ISO_B5,PrintAttributes.MediaSize.JIS_B5,PrintAttributes.MediaSize.NA_LETTER,PrintAttributes.MediaSize.NA_LEGAL,PrintAttributes.MediaSize.NA_TABLOID};
        for (PrintAttributes.MediaSize item:standard) {
            PrintAttributes.MediaSize size=widthMils>heightMils?item.asLandscape():item.asPortrait();
            if (Math.abs(size.getWidthMils()-widthMils)<=5 && Math.abs(size.getHeightMils()-heightMils)<=5) return size;
        }
        return new PrintAttributes.MediaSize("WORD_DOCUMENT","문서",widthMils,heightMils);
    }
    void launch() {
        if (closed || launched) return; launched=true;
        try {
            String title=file.getName().replaceFirst("(?i)\\.html$","");
            PrintDocumentAdapter delegate=renderer.createPrintDocumentAdapter(title);
            PrintDocumentAdapter adapter=new PrintDocumentAdapter() {
                @Override public void onStart() { delegate.onStart(); }
                @Override public void onLayout(PrintAttributes old, PrintAttributes next, CancellationSignal signal, LayoutResultCallback callback, Bundle extras) {
                    if (closed || signal.isCanceled()) { callback.onLayoutCancelled(); return; }
                    PrintAttributes.MediaSize paper=next.getMediaSize();
                    if (paper==null) paper=media();
                    PrintAttributes.Margins margins=next.getMinMargins();
                    int horizontal=margins==null?0:margins.getLeftMils()+margins.getRightMils();
                    int vertical=margins==null?0:margins.getTopMils()+margins.getBottomMils();
                    double width=(paper.getWidthMils()-horizontal)*.096-.5, height=(paper.getHeightMils()-vertical)*.096-.5;
                    if (width<=0 || height<=0) { callback.onLayoutFailed("선택한 용지의 인쇄 영역이 너무 작습니다."); return; }
                    // Every original page stays one output sheet. Display zoom,
                    // editor controls and source-page orientation do not leak in.
                    String script="(()=>{const w="+width+",h="+height+";document.querySelectorAll('.android-print-sheet').forEach(p=>{const s=p.firstElementChild,x=Number(p.dataset.sourceWidth),y=Number(p.dataset.sourceHeight),z=Math.min(1,w/x,h/y);p.style.width=w+'px';p.style.height=h+'px';s.style.transform='scale('+z+')';s.style.left=(w-x*z)/2+'px';s.style.top=(h-y*z)/2+'px'});void document.body.offsetHeight;return true})()";
                    renderer.evaluateJavascript(script,value -> {
                        if (closed || signal.isCanceled()) callback.onLayoutCancelled();
                        else if (!"true".equals(value)) callback.onLayoutFailed("인쇄 용지를 준비하지 못했습니다.");
                        else delegate.onLayout(old,next,signal,callback,extras);
                    });
                }
                @Override public void onWrite(PageRange[] ranges, ParcelFileDescriptor destination, CancellationSignal signal, WriteResultCallback callback) {
                    if (closed || signal.isCanceled()) callback.onWriteCancelled(); else delegate.onWrite(ranges,destination,signal,callback);
                }
                @Override public void onFinish() {
                    try { delegate.onFinish(); }
                    finally {
                        String status=job!=null&&job.isCancelled()?"cancelled":job!=null&&(job.isQueued()||job.isStarted()||job.isCompleted())?"submitted":"closed";
                        if (job!=null&&job.isFailed()) finish(null,new IOException("시스템에서 인쇄 요청을 완료하지 못했습니다.")); else finish(status,null);
                    }
                }
            };
            PrintAttributes attributes=new PrintAttributes.Builder().setMediaSize(media()).setMinMargins(PrintAttributes.Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build();
            job=((PrintManager)activity.getSystemService(Activity.PRINT_SERVICE)).print(title,adapter,attributes);
            if (job==null) finish(null,new IOException("시스템 인쇄창을 열지 못했습니다."));
        } catch (Exception error) { finish(null,error); }
    }
    void finish(String status, Exception error) {
        if (closed) return; closed=true; handler.removeCallbacksAndMessages(null);
        if (renderer!=null) { renderer.stopLoading(); renderer.destroy(); renderer=null; }
        file.delete(); File folder=file.getParentFile(); if(folder!=null)folder.delete(); result.finish(status,error);
    }
    void close() { finish(null,new IOException("인쇄 준비를 종료했습니다.")); }
}
