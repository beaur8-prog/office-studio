package io.officestudio.shell;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Locale;

/** Print prepared worksheet pages in an isolated view, with no app UI or bridge.
 * Each printer layout gets its own WebView delegate after HTML loading finishes.
 * Wrappers scale whole prepared pages to the actual paper and printable margins.
 */
final class SheetPrintAdapter extends PrintDocumentAdapter {
    final Activity activity;
    final FrameLayout root;
    final String title;
    final double originalWidth,originalHeight;
    final int expectedPages;
    final Runnable completed;
    final Handler handler=new Handler(Looper.getMainLooper());
    String html,error;
    Layout layout;
    boolean finished;
    static final class Layout {
        WebView view;
        PrintDocumentAdapter delegate;
        CancellationSignal signal;
        LayoutResultCallback callback;
        Runnable timeout;
        boolean resolved,closed;
    }
    SheetPrintAdapter(Activity activity,FrameLayout root,String title,String html,double widthMm,double heightMm,int pages,Runnable completed){
        if(!Double.isFinite(widthMm)||!Double.isFinite(heightMm)||widthMm<50||heightMm<50||widthMm>1500||heightMm>1500||pages<1||pages>500||!html.contains("data-sheet-print-document=\"1\"")||!html.contains("</head>"))throw new IllegalArgumentException("인쇄 문서 형식이 올바르지 않습니다");
        this.activity=activity;this.root=root;this.title=title;this.html=html;this.originalWidth=widthMm*96/25.4;this.originalHeight=heightMm*96/25.4;this.expectedPages=pages;this.completed=completed;
    }
    static PrintAttributes attributes(String name,double widthMm,double heightMm){
        PrintAttributes.MediaSize media;
        switch(name){
            case "A3":media=PrintAttributes.MediaSize.ISO_A3;break;
            case "A4":media=PrintAttributes.MediaSize.ISO_A4;break;
            case "A5":media=PrintAttributes.MediaSize.ISO_A5;break;
            case "Letter":media=PrintAttributes.MediaSize.NA_LETTER;break;
            case "Legal":media=PrintAttributes.MediaSize.NA_LEGAL;break;
            default:media=new PrintAttributes.MediaSize("sheet.document","문서 용지",(int)Math.round(Math.min(widthMm,heightMm)*1000/25.4),(int)Math.round(Math.max(widthMm,heightMm)*1000/25.4));
        }
        media=widthMm>heightMm?media.asLandscape():media.asPortrait();
        return new PrintAttributes.Builder().setMediaSize(media).setMinMargins(PrintAttributes.Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR).setResolution(new PrintAttributes.Resolution("sheet","Sheet Studio",300,300)).build();
    }
    String htmlFor(PrintAttributes attributes){
        PrintAttributes.MediaSize media=attributes.getMediaSize();PrintAttributes.Margins margins=attributes.getMinMargins();
        if(media==null||margins==null)throw new IllegalArgumentException("프린터의 용지 크기를 확인할 수 없습니다");
        double width=(media.getWidthMils()-margins.getLeftMils()-margins.getRightMils())*96.0/1000-1;
        double height=(media.getHeightMils()-margins.getTopMils()-margins.getBottomMils())*96.0/1000-1;
        if(width<=0||height<=0)throw new IllegalArgumentException("인쇄 여백이 용지보다 큽니다");
        double scale=Math.min(width/originalWidth,height/originalHeight),left=(width-originalWidth*scale)/2;
        String css=String.format(Locale.ROOT,"<style>*{-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important}@page{size:auto!important;margin:0!important}html,body{margin:0!important;padding:0!important;width:%.6fpx!important}body>.android-sheet-print-page{position:relative!important;width:%.6fpx!important;height:%.6fpx!important;overflow:hidden!important;break-inside:avoid!important;break-after:page!important;page-break-after:always!important}body>.android-sheet-print-page:last-child{break-after:auto!important;page-break-after:auto!important}.android-sheet-print-page>.print-page{position:absolute!important;left:%.6fpx!important;top:0!important;width:%.6fpx!important;height:%.6fpx!important;transform:scale(%.9f)!important;transform-origin:top left!important;break-after:auto!important;page-break-after:auto!important;margin:0!important}</style>",width,width,height,left,originalWidth,originalHeight,scale);
        return html.replace("</head>",css+"</head>");
    }
    @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes next,CancellationSignal signal,LayoutResultCallback callback,Bundle extras){
        release(layout);layout=null;
        if(finished||signal.isCanceled()){callback.onLayoutCancelled();return;}
        Layout state=new Layout();layout=state;state.signal=signal;state.callback=callback;
        try{
            String content=htmlFor(next);error=null;
            WebView view=new WebView(activity);state.view=view;
            WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(false);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setBlockNetworkImage(true);settings.setDefaultTextEncodingName("UTF-8");settings.setTextZoom(100);
            view.setFocusable(false);view.setFocusableInTouchMode(false);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);view.setAlpha(0);
            root.addView(view,0,new FrameLayout.LayoutParams(1,1));
            view.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){if("data".equals(request.getUrl().getScheme()))return null;return new WebResourceResponse("text/plain","UTF-8",403,"Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
                @Override public void onPageFinished(WebView v,String url){
                    if(finished||layout!=state||state.closed||state.resolved||state.delegate!=null||!"https://appassets.androidplatform.net/sheet-print/".equals(url))return;
                    if(signal.isCanceled()){cancel(state);return;}
                    try{
                        state.delegate=v.createPrintDocumentAdapter(title);state.delegate.onStart();
                        // Framework callback constructors are not public SDK API.
                        // Forward the original callback and cancellation signal;
                        // PrintManager serializes subsequent layout/write calls.
                        state.resolved=true;handler.removeCallbacks(state.timeout);
                        state.delegate.onLayout(oldAttributes==null?new PrintAttributes.Builder().build():oldAttributes,next,signal,callback,extras);
                    }catch(Exception failure){state.resolved=false;fail(state,failure.getMessage());}
                }
            });
            state.timeout=()->fail(state,"인쇄 문서 준비 시간이 초과되었습니다. 범위나 이미지 수를 줄이세요");handler.postDelayed(state.timeout,45000);
            signal.setOnCancelListener(()->handler.post(()->cancel(state)));
            view.loadDataWithBaseURL("https://appassets.androidplatform.net/sheet-print/",content,"text/html","UTF-8",null);
        }catch(Exception failure){state.resolved=false;fail(state,failure.getMessage());}
    }
    boolean active(Layout state){return !finished&&layout==state&&!state.closed&&!state.resolved;}
    void fail(Layout state,String message){
        if(!active(state))return;state.resolved=true;error=message==null?"인쇄 문서를 준비하지 못했습니다":message;
        state.callback.onLayoutFailed(error);release(state);
    }
    void cancel(Layout state){
        if(!active(state))return;state.resolved=true;state.callback.onLayoutCancelled();release(state);
    }
    void release(Layout state){
        if(state==null||state.closed)return;state.closed=true;
        if(state.timeout!=null)handler.removeCallbacks(state.timeout);
        if(!state.resolved&&state.callback!=null){state.resolved=true;state.callback.onLayoutCancelled();}
        if(state.delegate!=null){try{state.delegate.onFinish();}catch(Exception ignored){}state.delegate=null;}
        if(state.view!=null){root.removeView(state.view);state.view.destroy();state.view=null;}
    }
    @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal signal,WriteResultCallback callback){
        Layout state=layout;
        if(finished||state==null||state.closed||state.delegate==null||signal.isCanceled()){
            try{destination.close();}catch(Exception ignored){}
            if(signal.isCanceled())callback.onWriteCancelled();else callback.onWriteFailed("인쇄 문서가 준비되지 않았습니다");return;
        }
        state.delegate.onWrite(pages,destination,signal,callback);
    }

    @Override public void onFinish(){if(finished)return;finished=true;release(layout);layout=null;html=null;completed.run();}
}
