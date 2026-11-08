package io.officestudio.shell;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.provider.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.core.view.*;
import androidx.core.graphics.Insets;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Offline packaged editor. Only the main, app-owned HTTPS origin can use file services. */
public class MainActivity extends Activity {
    static final String ORIGIN = "https://appassets.androidplatform.net";
    static final int OPEN = 2101, SAVE = 2102, CHOOSE = 2103, SHARE = 2104;
    static final long LIMIT = 128L * 1024 * 1024;
    WebView web;
    FrameLayout root;
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final Map<String, ImportFile> imports = new ConcurrentHashMap<>();
    Transfer transfer;
    Pending pending;
    ValueCallback<Uri[]> chooser;
    Uri incoming;
    boolean ready, destroyed, immersive;
    Insets slideSafeInsets = Insets.NONE;
    String insetStyle;
    Integer orientationBefore;
    int hiddenSystemBarTypes;
    View fullscreen;
    WebChromeClient.CustomViewCallback fullscreenCallback;
    String shareAction;
    BroadcastReceiver shareReceiver;
    static class Pending { String id, method, transferToken; JavaScriptReplyProxy reply; File file; Pending(String i, String m, JavaScriptReplyProxy r, File f) { id=i;method=m;reply=r;file=f; } }
    static class Transfer { String token; File file; OutputStream out; long size, written; String mime; }
    static class ImportFile { File file; String name,mime; long size; }
    interface Job { void run() throws Exception; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (BuildConfig.STUDIO.equals("slide")) {
            Window window = getWindow();
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new FrameLayout(this);web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            if (BuildConfig.STUDIO.equals("slide")) {
                // The WebView fills the window, including the camera cutout.
                // Only controls receive safe insets through CSS. Padding the
                // native parent would leave a light strip beside a landscape show.
                slideSafeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(0, 0, 0, keyboard.bottom);
                publishSlideInsets(false);
                return WindowInsetsCompat.CONSUMED;
            }
            Insets b=insets.getInsets((WindowInsetsCompat.Type.systemBars()&~hiddenSystemBarTypes)|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(b.left,b.top,b.right,Math.max(b.bottom,keyboard.bottom));
            return insets;
        });
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
        shareAction=getPackageName()+".SHARE_TARGET."+UUID.randomUUID();
        shareReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(shareAction.equals(i.getAction())&&pending!=null&&pending.method.equals("share")&&Objects.equals(pending.transferToken,i.getStringExtra("transferToken"))){Pending p=pending;pending=null;success(p.id,p.reply,obj("completed",true));}}};
        if(Build.VERSION.SDK_INT>=33)registerReceiver(shareReceiver,new IntentFilter(shareAction),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(shareReceiver,new IntentFilter(shareAction));
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);settings.setTextZoom(100);
        web.setBackgroundColor(0xfff5f7fb);
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){showWebViewUpdate();return;}
        try {android.content.pm.PackageInfo w=WebView.getCurrentWebViewPackage();if(w!=null&&Integer.parseInt(w.versionName.split("\\.")[0])<120){showWebViewUpdate();return;}}catch(Exception ignored){}
        WebViewCompat.addWebMessageListener(web,"OfficeNative",Collections.singleton(ORIGIN),(view,message,origin,main,reply)->{
            if(!main||!"https".equals(origin.getScheme())||!"appassets.androidplatform.net".equals(origin.getHost())||(origin.getPort()!=-1&&origin.getPort()!=443))return;
            try{JSONObject req=new JSONObject(message.getData());String id=req.getString("id");if(id.length()>100)throw new IOException("Invalid request");try{handle(req,id,reply);}catch(Exception e){failure(id,reply,e);}}catch(Exception e){/* Malformed messages never gain file access. */}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                Uri u=req.getUrl();if(ORIGIN.equals(u.getScheme()+"://"+u.getHost())&&u.getPath()!=null&&u.getPath().startsWith("/assets/")){
                    try{String name=u.getPath().substring(8);if(name.contains("..")||name.contains("\\")||name.startsWith("/"))throw new IOException();String mime=assetMime(name);WebResourceResponse response=new WebResourceResponse(mime,"UTF-8",getAssets().open(name));response.setResponseHeaders(Collections.singletonMap("Cache-Control","no-store"));return response;}catch(IOException e){return blocked(404);}
                }
                if("data".equals(u.getScheme())||"blob".equals(u.getScheme()))return null;
                return blocked(403);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                Uri u=req.getUrl();if(ORIGIN.equals(u.getScheme()+"://"+u.getHost()))return false;
                if(req.isForMainFrame()&&req.hasGesture()&&(u.getScheme().equals("https")||u.getScheme().equals("mailto")))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                return true;
            }
            @Override public void onPageFinished(WebView view,String url){web.evaluateJavascript("window.__studioHostConfig="+obj("studio",BuildConfig.STUDIO,"version",BuildConfig.VERSION_NAME)+";",null);publishSlideInsets(true);ViewCompat.requestApplyInsets(root);}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(chooser!=null||pending!=null){callback.onReceiveValue(null);return true;}chooser=callback;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{startActivityForResult(intent,CHOOSE);}catch(Exception e){chooser=null;callback.onReceiveValue(null);}return true;
            }
            @Override public void onPermissionRequest(PermissionRequest req){req.deny();}
            @Override public boolean onJsAlert(WebView v,String u,String text,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(text).setPositiveButton("확인",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onJsConfirm(WebView v,String u,String text,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(text).setPositiveButton("확인",(d,w)->result.confirm()).setNegativeButton("취소",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public void onShowCustomView(View v,CustomViewCallback callback){if(fullscreen!=null){callback.onCustomViewHidden();return;}fullscreen=v;fullscreenCallback=callback;web.setVisibility(View.GONE);root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        incoming=intentUri(getIntent());
        web.loadUrl(ORIGIN+"/assets/public/index.html");
    }
    static String assetMime(String n){String s=n.toLowerCase(Locale.ROOT);if(s.endsWith(".wasm"))return "application/wasm";if(s.endsWith(".js")||s.endsWith(".mjs"))return "application/javascript";if(s.endsWith(".html"))return "text/html";if(s.endsWith(".css"))return "text/css";if(s.endsWith(".json"))return "application/json";if(s.endsWith(".woff2"))return "font/woff2";if(s.endsWith(".ttf"))return "font/ttf";String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(s));return m==null?"application/octet-stream":m;}
    static WebResourceResponse blocked(int code){return new WebResourceResponse("text/plain","UTF-8",code,"Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    void showWebViewUpdate(){new AlertDialog.Builder(this).setTitle("Android System WebView 업데이트 필요").setMessage("이 앱은 최신 Android System WebView가 필요합니다. Play 스토어에서 WebView를 업데이트한 뒤 다시 실행해 주세요.").setPositiveButton("업데이트",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.google.android.webview")));}catch(Exception ignored){}}).setNegativeButton("닫기",(d,w)->finish()).setCancelable(false).show();}
    static JSONObject obj(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    void success(String id,JavaScriptReplyProxy reply,Object value){runOnUiThread(()->{if(!destroyed)try{reply.postMessage(obj("id",id,"result",value==null?JSONObject.NULL:value).toString());}catch(Exception ignored){}});}
    void failure(String id,JavaScriptReplyProxy reply,Exception e){runOnUiThread(()->{if(!destroyed)try{reply.postMessage(obj("id",id,"error",e.getMessage()==null?"파일 작업을 완료하지 못했습니다":e.getMessage()).toString());}catch(Exception ignored){}});}
    void work(String id,JavaScriptReplyProxy reply,Job job){io.execute(()->{try{job.run();}catch(Exception e){failure(id,reply,e);}});}
    void handle(JSONObject req,String id,JavaScriptReplyProxy reply)throws Exception {
        String method=req.getString("method");JSONObject p=req.optJSONObject("params");if(p==null)p=new JSONObject();final JSONObject a=p;
        switch(method){
            case "ready":ready=true;success(id,reply,obj("studio",BuildConfig.STUDIO,"version",BuildConfig.VERSION_NAME));deliverIncoming();break;
            case "open":if(pending!=null||chooser!=null)throw new IOException("다른 파일 작업이 진행 중입니다");pending=new Pending(id,"open",reply,null);try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),OPEN);}catch(Exception e){pending=null;failure(id,reply,e);}break;
            case "read":work(id,reply,()->{ImportFile f=imports.get(a.getString("token"));if(f==null)throw new IOException("파일 요청이 만료되었습니다");long offset=a.getLong("offset");if(offset<0||offset>f.size)throw new IOException("잘못된 파일 범위입니다");try(RandomAccessFile input=new RandomAccessFile(f.file,"r")){input.seek(offset);byte[] bytes=new byte[(int)Math.min(32768,f.size-offset)];input.readFully(bytes);success(id,reply,obj("chunk",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),"offset",offset,"done",offset+bytes.length==f.size));}});break;
            case "release":work(id,reply,()->{ImportFile f=imports.remove(a.getString("token"));if(f!=null)f.file.delete();success(id,reply,true);});break;
            case "begin":work(id,reply,()->{if(transfer!=null||pending!=null||chooser!=null)throw new IOException("다른 파일 작업이 진행 중입니다");long length=a.getLong("bytes");if(length<0||length>LIMIT)throw new IOException("Android 파일은 128 MiB 이하여야 합니다");Transfer t=new Transfer();t.token=UUID.randomUUID().toString();t.size=length;t.mime=mime(a.optString("mime"));File folder=new File(getCacheDir(),"exports/"+t.token);if(!folder.mkdirs())throw new IOException("임시 저장 공간을 만들지 못했습니다");t.file=new File(folder,cleanName(a.getString("name")));t.out=new FileOutputStream(t.file);transfer=t;success(id,reply,obj("token",t.token));});break;
            case "append":work(id,reply,()->{Transfer t=transfer;if(t==null||!t.token.equals(a.getString("token")))throw new IOException("저장 요청이 만료되었습니다");String encoded=a.getString("chunk");if(encoded.length()>50000||a.getLong("offset")!=t.written)throw new IOException("저장 데이터 순서가 올바르지 않습니다");byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);if(t.written+bytes.length>t.size)throw new IOException("저장 파일 크기가 일치하지 않습니다");t.out.write(bytes);t.written+=bytes.length;success(id,reply,t.written);});break;
            case "save":case "share":work(id,reply,()->{Transfer t=transfer;if(t==null||!t.token.equals(a.getString("token"))||t.size!=t.written)throw new IOException("저장 파일을 완성하지 못했습니다");t.out.close();transfer=null;runOnUiThread(()->startOutput(id,reply,t,method));});break;
            case "abort":work(id,reply,()->{Transfer t=transfer;if(t!=null&&t.token.equals(a.optString("token"))){t.out.close();t.file.delete();transfer=null;}success(id,reply,true);});break;
            case "sheetFullscreen":if(!BuildConfig.STUDIO.equals("sheet"))throw new IOException("지원하지 않는 Android 요청입니다");boolean enabled=a.optBoolean("enabled");hiddenSystemBarTypes=enabled?((a.optBoolean("hideStatusBar",true)?WindowInsetsCompat.Type.statusBars():0)|(a.optBoolean("hideNavigationBar",false)?WindowInsetsCompat.Type.navigationBars():0)):0;WindowInsetsControllerCompat sheetBars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());sheetBars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);sheetBars.hide(hiddenSystemBarTypes);sheetBars.show(WindowInsetsCompat.Type.systemBars()&~hiddenSystemBarTypes);ViewCompat.requestApplyInsets(root);success(id,reply,obj("enabled",enabled));break;
            case "systemBars":if(!BuildConfig.STUDIO.equals("slide"))throw new IOException("지원하지 않는 Android 요청입니다");setSlideImmersive(a.optBoolean("hidden"));success(id,reply,true);break;
            case "orientation":if(!BuildConfig.STUDIO.equals("slide"))throw new IOException("지원하지 않는 Android 요청입니다");String direction=a.getString("orientation");if(direction.equals("unlock")){if(orientationBefore!=null){setRequestedOrientation(orientationBefore);orientationBefore=null;}}else{if(!direction.equals("landscape")&&!direction.equals("portrait"))throw new IOException("잘못된 화면 방향입니다");if(orientationBefore==null)orientationBefore=getRequestedOrientation();setRequestedOrientation(direction.equals("landscape")?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);}success(id,reply,true);break;
            case "clipboard":android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(a.optString("html").isEmpty()?ClipData.newPlainText("Studio",a.optString("text")):ClipData.newHtmlText("Studio",a.optString("text"),a.optString("html")));success(id,reply,true);break;
            case "readClipboard":android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=cm.getPrimaryClip();String text=clip!=null&&clip.getItemCount()>0?clip.getItemAt(0).coerceToText(this).toString():"";if(text.length()>1024*1024)throw new IOException("클립보드 내용이 너무 큽니다");success(id,reply,text);break;
            case "close":success(id,reply,true);finish();break;
            case "print":if(pending!=null)throw new IOException("다른 파일 작업이 진행 중입니다");pending=new Pending(id,"print",reply,null);PrintDocumentAdapter delegate=web.createPrintDocumentAdapter(getString(R.string.app_name));PrintDocumentAdapter adapter=new PrintDocumentAdapter(){@Override public void onStart(){delegate.onStart();}@Override public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal signal,LayoutResultCallback cb,Bundle extras){delegate.onLayout(old,next,signal,cb,extras);}@Override public void onWrite(android.print.PageRange[] pages,ParcelFileDescriptor dest,CancellationSignal signal,WriteResultCallback cb){delegate.onWrite(pages,dest,signal,cb);}@Override public void onFinish(){delegate.onFinish();if(pending!=null&&pending.method.equals("print")){Pending x=pending;pending=null;success(x.id,x.reply,obj("completed",true));}}};((PrintManager)getSystemService(PRINT_SERVICE)).print(getString(R.string.app_name),adapter,new PrintAttributes.Builder().build());break;
            default:failure(id,reply,new IOException("지원하지 않는 Android 요청입니다"));
        }
    }
    void startOutput(String id,JavaScriptReplyProxy reply,Transfer t,String method){
        if(pending!=null){t.file.delete();failure(id,reply,new IOException("다른 파일 작업이 진행 중입니다"));return;}pending=new Pending(id,method,reply,t.file);pending.transferToken=t.token;
        try{if(method.equals("save"))startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(t.mime).putExtra(Intent.EXTRA_TITLE,t.file.getName()),SAVE);
        else{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",t.file);Intent send=new Intent(Intent.ACTION_SEND).setType(t.mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newUri(getContentResolver(),t.file.getName(),uri));Intent chosen=new Intent(shareAction).setPackage(getPackageName()).putExtra("transferToken",t.token);int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);PendingIntent receiver=PendingIntent.getBroadcast(this,t.token.hashCode(),chosen,flags);startActivityForResult(Intent.createChooser(send,"파일 공유",receiver.getIntentSender()),SHARE);}}
        catch(Exception e){pending=null;t.file.delete();failure(id,reply,e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==CHOOSE&&chooser!=null){ValueCallback<Uri[]> cb=chooser;chooser=null;cb.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);return;}
        Pending p=pending;if(p==null)return;
        if(request==OPEN&&p.method.equals("open")){pending=null;if(result!=RESULT_OK||data==null||data.getData()==null){success(p.id,p.reply,obj("cancelled",true));return;}work(p.id,p.reply,()->success(p.id,p.reply,importUri(data.getData())));}
        else if(request==SAVE&&p.method.equals("save")){pending=null;if(result!=RESULT_OK||data==null||data.getData()==null){p.file.delete();success(p.id,p.reply,obj("cancelled",true));return;}Uri uri=data.getData();work(p.id,p.reply,()->{try(InputStream input=new FileInputStream(p.file);OutputStream output=getContentResolver().openOutputStream(uri,"w")){if(output==null)throw new IOException("저장 위치를 열지 못했습니다");byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);output.flush();}finally{p.file.delete();}success(p.id,p.reply,obj("completed",true));});}
        else if(request==SHARE&&p.method.equals("share")){
            // Chooser completion and its selected-target broadcast are separate
            // asynchronous deliveries. Let the positive selection arrive before
            // treating an unselected chooser dismissal as cancellation.
            new Handler(Looper.getMainLooper()).postDelayed(()->{if(!destroyed&&pending==p){pending=null;success(p.id,p.reply,obj("cancelled",true));}},2000);
        }
    }
    JSONObject importUri(Uri uri)throws Exception {
        if(!"content".equals(uri.getScheme()))throw new IOException("Android 파일 선택기에서 파일을 선택해 주세요");if(imports.size()>3)throw new IOException("읽기 대기 파일이 너무 많습니다");String name="문서",type=mime(getContentResolver().getType(uri));
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){name=c.getString(0);if(!c.isNull(1)&&c.getLong(1)>LIMIT)throw new IOException("Android 파일은 128 MiB 이하여야 합니다");}}
        File dir=new File(getCacheDir(),"imports");dir.mkdirs();File temp=File.createTempFile("open-",".bin",dir);long length=0;
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("파일을 열지 못했습니다");byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){length+=n;if(length>LIMIT)throw new IOException("Android 파일은 128 MiB 이하여야 합니다");out.write(bytes,0,n);}}
        catch(Exception e){temp.delete();throw e;}
        String token=UUID.randomUUID().toString();ImportFile f=new ImportFile();f.file=temp;f.size=length;f.name=cleanName(name);f.mime=type;imports.put(token,f);return obj("token",token,"name",f.name,"mime",type,"bytes",length,"requestId",UUID.randomUUID().toString());
    }
    static String cleanName(String value){String n=value.replaceAll("[\\\\/\\x00-\\x1f]","_").trim();if(n.isEmpty()||n.equals(".")||n.equals(".."))n="문서";if(n.length()>180)n=n.substring(n.length()-180);return n;}
    static String mime(String value){return value!=null&&value.matches("[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+")?value:"application/octet-stream";}
    static Uri intentUri(Intent i){if(i==null)return null;if(Intent.ACTION_SEND.equals(i.getAction()))return i.getParcelableExtra(Intent.EXTRA_STREAM);if(Intent.ACTION_VIEW.equals(i.getAction()))return i.getData();return null;}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);Uri u=intentUri(i);if(u!=null){if(incoming!=null){Toast.makeText(this,"다른 파일을 읽고 있습니다. 잠시 뒤 다시 열어 주세요",Toast.LENGTH_LONG).show();return;}incoming=u;deliverIncoming();}}
    void deliverIncoming(){if(!ready||incoming==null)return;Uri uri=incoming;incoming=null;io.execute(()->{try{JSONObject f=importUri(uri);runOnUiThread(()->{if(!destroyed)web.evaluateJavascript("window.OfficeStudioHost&&window.OfficeStudioHost.receive("+f+")",null);});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"파일을 열지 못했습니다. 앱의 열기에서 다시 선택해 주세요",Toast.LENGTH_LONG).show());}});}
    void publishSlideInsets(boolean force) {
        if (!BuildConfig.STUDIO.equals("slide") || web == null || destroyed) return;
        float density = getResources().getDisplayMetrics().density;
        String[] edges = {"top", "right", "bottom", "left"};
        int[] pixels = {slideSafeInsets.top, slideSafeInsets.right, slideSafeInsets.bottom, slideSafeInsets.left};
        StringBuilder script = new StringBuilder("(()=>{const s=document.documentElement.style;");
        for (int i=0;i<edges.length;i++) script.append("s.setProperty('--studio-safe-area-inset-").append(edges[i]).append("','").append(pixels[i]/density).append("px');");
        script.append("})()");
        String next = script.toString();
        if (force || !next.equals(insetStyle)) { insetStyle = next; web.evaluateJavascript(next, null); }
    }
    void setSlideImmersive(boolean hidden) {
        if (!BuildConfig.STUDIO.equals("slide")) return;
        immersive = hidden;
        // Fill both the WebView and its parent so transitions/rotation cannot
        // expose the light editor window background around a presentation.
        int background = hidden ? android.graphics.Color.BLACK : 0xfff5f7fb;
        root.setBackgroundColor(background);
        web.setBackgroundColor(background);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (hidden) bars.hide(WindowInsetsCompat.Type.systemBars());
        else bars.show(WindowInsetsCompat.Type.systemBars());
        ViewCompat.requestApplyInsets(root);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && immersive && root != null && web != null) setSlideImmersive(true);
    }
    void hideFullscreen(){if(fullscreen==null)return;root.removeView(fullscreen);fullscreen=null;web.setVisibility(View.VISIBLE);if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}
    @Override public void onBackPressed(){if(fullscreen!=null){hideFullscreen();return;}if(web==null){finish();return;}web.evaluateJavascript("window.OfficeStudioHost?window.OfficeStudioHost.back():false",value->{if("false".equals(value)||"null".equals(value))finish();});}
    @Override protected void onPause(){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'));window.dispatchEvent(new Event('pagehide'));",null);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;try{unregisterReceiver(shareReceiver);}catch(Exception ignored){}if(chooser!=null)chooser.onReceiveValue(null);io.execute(()->{if(transfer!=null)try{transfer.out.close();transfer.file.delete();}catch(Exception ignored){}for(ImportFile f:imports.values())f.file.delete();imports.clear();});io.shutdown();if(web!=null){root.removeView(web);web.destroy();}super.onDestroy();}
}
