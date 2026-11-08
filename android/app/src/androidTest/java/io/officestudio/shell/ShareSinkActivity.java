package io.officestudio.shell;
import android.app.Activity;import android.os.Bundle;import android.content.Intent;import android.net.Uri;import java.io.*;
public class ShareSinkActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);new Thread(()->{try{Uri uri=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(new File(new File(getCacheDir(),"fixtures"),"shared-result.bin"))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}catch(Exception ignored){}finally{runOnUiThread(this::finish);}}).start();}
}
