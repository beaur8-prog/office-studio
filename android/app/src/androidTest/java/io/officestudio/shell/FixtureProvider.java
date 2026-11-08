package io.officestudio.shell;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

/** Disposable external document provider installed only by the private QA APK. */
public class FixtureProvider extends ContentProvider {
    private File file(Uri uri) {
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Invalid fixture name");
        File directory=new File(getContext().getCacheDir(),"fixtures");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create fixture directory");
        return new File(directory,name);
    }
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String name,Bundle extras){
        if(!"create".equals(method))throw new IllegalArgumentException("Unknown fixture operation");
        Uri uri=Uri.parse("content://"+getContext().getPackageName()+".fixtures/"+name);
        try(OutputStream out=new FileOutputStream(file(uri))){out.write(extras.getByteArray("bytes"));}
        catch(IOException e){throw new IllegalStateException(e);}
        Bundle result=new Bundle();result.putParcelable("uri",uri);return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(mode.contains("w")&&"read-only-original.bin".equals(uri.getLastPathSegment()))throw new FileNotFoundException("Document provider is read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);File source=file(uri);if(!source.isFile())return cursor;
        Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=source.getName();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=source.length();}cursor.addRow(row);return cursor;
    }
    @Override public String getType(Uri uri){String name=uri.getLastPathSegment();if(name.endsWith(".csv"))return "text/csv";if(name.endsWith(".txt"))return "text/plain";if(name.endsWith(".pptx"))return "application/vnd.openxmlformats-officedocument.presentationml.presentation";return "application/octet-stream";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
