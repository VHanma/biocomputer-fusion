package com.hanma.echocore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Cross-process persistent queue/state journal shared by the City and :phoenix. */
public class ImportStateStore extends SQLiteOpenHelper {
    private static final String DB="echocore_phoenix_queue.db"; private static final int VER=1; private static final int MAX_REPORT=12000;
    public ImportStateStore(Context c){super(c.getApplicationContext(),DB,null,VER);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE TABLE queue(uri TEXT PRIMARY KEY,pos INTEGER NOT NULL,enqueued_at INTEGER NOT NULL,name TEXT NOT NULL DEFAULT '',checkpoint_kind TEXT NOT NULL DEFAULT '',checkpoint_value INTEGER NOT NULL DEFAULT 0,source_id INTEGER NOT NULL DEFAULT 0,parent_memory_id INTEGER NOT NULL DEFAULT 0,chars INTEGER NOT NULL DEFAULT 0,chunks INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE INDEX idx_queue_pos ON queue(pos)");
        put(d,"state","IDLE");put(d,"status","Ready");put(d,"cancel","0");put(d,"done","0");put(d,"failed","0");put(d,"total","0");put(d,"report","");put(d,"updated",String.valueOf(System.currentTimeMillis()));
    }
    @Override public void onUpgrade(SQLiteDatabase d,int o,int n){}

    public synchronized void enqueue(List<Uri> uris){if(uris==null||uris.isEmpty())return;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{int pos=maxPos(d)+1;boolean fresh=countQueue(d)==0&&!"RUNNING".equals(state(d));if(fresh){put(d,"done","0");put(d,"failed","0");put(d,"report","");}for(Uri u:uris){if(u==null)continue;ContentValues v=new ContentValues();v.put("uri",u.toString());v.put("pos",pos++);v.put("enqueued_at",System.currentTimeMillis());d.insertWithOnConflict("queue",null,v,SQLiteDatabase.CONFLICT_IGNORE);}put(d,"cancel","0");put(d,"state","QUEUED");put(d,"total",String.valueOf(countQueue(d)+intMeta(d,"done",0)+intMeta(d,"failed",0)));touch(d);d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public synchronized ArrayList<String> pending(){ArrayList<String>o=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM queue ORDER BY pos",null)){while(c.moveToNext())o.add(c.getString(0));}return o;}
    public synchronized String peek(){try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM queue ORDER BY pos LIMIT 1",null)){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized void markRunning(String uri,String name){SQLiteDatabase d=getWritableDatabase();ContentValues v=new ContentValues();v.put("name",safe(name));v.put("attempts",attempts(uri)+1);d.update("queue",v,"uri=?",new String[]{safe(uri)});put(d,"state","RUNNING");put(d,"current_uri",safe(uri));put(d,"current_name",safe(name));put(d,"status","Importing "+safe(name));touch(d);}
    public synchronized void progress(String status){SQLiteDatabase d=getWritableDatabase();put(d,"status",safe(status));touch(d);}
    public synchronized void checkpoint(String uri,String kind,long value,long sourceId,long parentMemoryId,long chars,long chunks){ContentValues v=new ContentValues();v.put("checkpoint_kind",safe(kind));v.put("checkpoint_value",Math.max(0,value));v.put("source_id",Math.max(0,sourceId));v.put("parent_memory_id",Math.max(0,parentMemoryId));v.put("chars",Math.max(0,chars));v.put("chunks",Math.max(0,chunks));getWritableDatabase().update("queue",v,"uri=?",new String[]{safe(uri)});}
    public synchronized JSONObject job(String uri){JSONObject o=new JSONObject();try(Cursor c=getReadableDatabase().rawQuery("SELECT name,checkpoint_kind,checkpoint_value,source_id,parent_memory_id,chars,chunks,attempts FROM queue WHERE uri=?",new String[]{safe(uri)})){if(c.moveToFirst()){o.put("name",c.getString(0));o.put("checkpoint_kind",c.getString(1));o.put("checkpoint_value",c.getLong(2));o.put("source_id",c.getLong(3));o.put("parent_memory_id",c.getLong(4));o.put("chars",c.getLong(5));o.put("chunks",c.getLong(6));o.put("attempts",c.getInt(7));}}catch(Exception ignored){}return o;}
    public synchronized int attempts(String uri){try(Cursor c=getReadableDatabase().rawQuery("SELECT attempts FROM queue WHERE uri=?",new String[]{safe(uri)})){return c.moveToFirst()?c.getInt(0):0;}}
    public synchronized void resetCheckpoint(String uri){ContentValues v=new ContentValues();v.put("checkpoint_kind","");v.put("checkpoint_value",0);v.put("source_id",0);v.put("parent_memory_id",0);v.put("chars",0);v.put("chunks",0);getWritableDatabase().update("queue",v,"uri=?",new String[]{safe(uri)});}

    public synchronized void completeCurrent(String uri,boolean success,String line){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("queue","uri=?",new String[]{safe(uri)});int done=intMeta(d,"done",0),fail=intMeta(d,"failed",0);if(success)done++;else fail++;put(d,"done",String.valueOf(done));put(d,"failed",String.valueOf(fail));String old=get(d,"report","");String next=old.isEmpty()?safe(line):old+"\n"+safe(line);if(next.length()>MAX_REPORT)next=next.substring(next.length()-MAX_REPORT);put(d,"report",next);put(d,"state",countQueue(d)==0?"DONE":"QUEUED");put(d,"current_uri","");put(d,"current_name","");put(d,"status",countQueue(d)==0?"Batch complete":"Continuing queue");touch(d);d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public synchronized void setError(String msg){SQLiteDatabase d=getWritableDatabase();put(d,"last_error",safe(msg));put(d,"status",safe(msg));touch(d);}
    public synchronized void requestCancel(){SQLiteDatabase d=getWritableDatabase();put(d,"cancel","1");put(d,"status","Cancelling at safe boundary…");touch(d);}
    public boolean cancelRequested(){return "1".equals(get(getReadableDatabase(),"cancel","0"));}
    public synchronized void clearCancel(){put(getWritableDatabase(),"cancel","0");}
    public synchronized void cancelQueue(){SQLiteDatabase d=getWritableDatabase();d.delete("queue",null,null);put(d,"state","CANCELLED");put(d,"status","Cancelled");put(d,"current_uri","");put(d,"current_name","");put(d,"cancel","0");touch(d);}

    public String state(){return get(getReadableDatabase(),"state","IDLE");} public String status(){return get(getReadableDatabase(),"status","Ready");} public String currentName(){return get(getReadableDatabase(),"current_name","");} public int done(){return intMeta(getReadableDatabase(),"done",0);} public int failed(){return intMeta(getReadableDatabase(),"failed",0);} public int total(){return intMeta(getReadableDatabase(),"total",0);} public String report(){return get(getReadableDatabase(),"report","");} public long updated(){try{return Long.parseLong(get(getReadableDatabase(),"updated","0"));}catch(Exception e){return 0;}}
    public JSONObject snapshot(){JSONObject o=new JSONObject();try{o.put("state",state()).put("status",status()).put("current",currentName()).put("done",done()).put("failed",failed()).put("total",total()).put("pending",pending().size()).put("updated",updated()).put("last_error",get(getReadableDatabase(),"last_error","")).put("job",job(get(getReadableDatabase(),"current_uri","")));}catch(Exception ignored){}return o;}

    private static void touch(SQLiteDatabase d){put(d,"updated",String.valueOf(System.currentTimeMillis()));}
    private static void put(SQLiteDatabase d,String k,String v){ContentValues c=new ContentValues();c.put("k",k);c.put("v",v==null?"":v);d.insertWithOnConflict("meta",null,c,SQLiteDatabase.CONFLICT_REPLACE);}
    private static String get(SQLiteDatabase d,String k,String f){try(Cursor c=d.rawQuery("SELECT v FROM meta WHERE k=?",new String[]{k})){return c.moveToFirst()?c.getString(0):f;}}
    private static int intMeta(SQLiteDatabase d,String k,int f){try{return Integer.parseInt(get(d,k,String.valueOf(f)));}catch(Exception e){return f;}}
    private static int maxPos(SQLiteDatabase d){try(Cursor c=d.rawQuery("SELECT COALESCE(MAX(pos),0) FROM queue",null)){return c.moveToFirst()?c.getInt(0):0;}}
    private static int countQueue(SQLiteDatabase d){try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM queue",null)){return c.moveToFirst()?c.getInt(0):0;}}
    private static String state(SQLiteDatabase d){return get(d,"state","IDLE");}
    private static String safe(String s){return s==null?"":s.trim();}
}
