package com.hanma.echocore;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import java.io.InputStream;
import java.security.MessageDigest;

/** Content-addressed source vault and parser-failure memory. */
public class SourceVault {
    private final Context context; private final AscendantStore store;
    public SourceVault(Context c,AscendantStore s){context=c.getApplicationContext();store=s;}
    public String fingerprint(Uri uri) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");long total=0;try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Could not open source for fingerprinting");byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1){total+=n;if(total>768L*1024L*1024L)throw new Exception("Source exceeds 768 MB fingerprint window");md.update(b,0,n);}}StringBuilder x=new StringBuilder();for(byte z:md.digest())x.append(String.format("%02x",z&255));return x.toString();}
    public long existing(String hash){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT source_id FROM source_fingerprints WHERE hash=? AND source_id>0",new String[]{hash})){return c.moveToFirst()?c.getLong(0):0;}}
    public void record(String hash,String name,long sourceId,long size){long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("hash",hash);v.put("source_name",name==null?"source":name);v.put("source_id",sourceId);v.put("size_bytes",Math.max(0,size));v.put("status","INDEXED");v.put("first_seen",now);v.put("last_seen",now);store.getWritableDatabase().insertWithOnConflict("source_fingerprints",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);ContentValues u=new ContentValues();u.put("source_id",sourceId);u.put("last_seen",now);u.put("status","INDEXED");store.getWritableDatabase().update("source_fingerprints",u,"hash=?",new String[]{hash});}
    public boolean quarantined(String hash,String parser){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT quarantined FROM parser_quarantine WHERE fingerprint=? AND parser=?",new String[]{hash,parser})){return c.moveToFirst()&&c.getInt(0)==1;}}
    public void failure(String hash,String format,String parser,Throwable t){String key=hash+":"+parser;int failures=0;try(Cursor c=store.getReadableDatabase().rawQuery("SELECT failures FROM parser_quarantine WHERE key=?",new String[]{key})){if(c.moveToFirst())failures=c.getInt(0);}failures++;ContentValues v=new ContentValues();v.put("key",key);v.put("fingerprint",hash);v.put("format",format==null?"":format);v.put("parser",parser);v.put("failures",failures);v.put("quarantined",failures>=2?1:0);v.put("reason",safe(t));v.put("last_failure",System.currentTimeMillis());store.getWritableDatabase().insertWithOnConflict("parser_quarantine",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);store.blackbox("PHOENIX","PARSER_FAILURE",parser+" · failures="+failures+" · "+safe(t),"");}
    public void success(String hash,String parser){ContentValues v=new ContentValues();v.put("failures",0);v.put("quarantined",0);v.put("reason","");store.getWritableDatabase().update("parser_quarantine",v,"fingerprint=? AND parser=?",new String[]{hash,parser});}
    private static String safe(Throwable t){if(t==null)return "unknown";String s=t.getClass().getSimpleName()+": "+(t.getMessage()==null?"":t.getMessage());return s.length()>500?s.substring(0,500):s;}
}
