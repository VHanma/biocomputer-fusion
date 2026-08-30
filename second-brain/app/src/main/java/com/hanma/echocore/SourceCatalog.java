package com.hanma.echocore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SourceCatalog extends SQLiteOpenHelper {
    private static final String DB_NAME = "echocore_sources.db";
    private static final int DB_VERSION = 1;

    public SourceCatalog(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,mime TEXT NOT NULL DEFAULT 'application/octet-stream',uri TEXT NOT NULL DEFAULT '',size_bytes INTEGER NOT NULL DEFAULT 0,char_count INTEGER NOT NULL DEFAULT 0,chunk_count INTEGER NOT NULL DEFAULT 0,parent_memory_id INTEGER NOT NULL DEFAULT 0,imported_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE chunks(id INTEGER PRIMARY KEY AUTOINCREMENT,source_id INTEGER NOT NULL,part INTEGER NOT NULL,text TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_source_time ON sources(imported_at DESC)");
        db.execSQL("CREATE INDEX idx_chunks_source ON chunks(source_id,part)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {}

    public long startSource(String name,String mime,String uri,long size) {
        ContentValues cv=new ContentValues();
        cv.put("name",name==null?"file":name); cv.put("mime",mime==null?"application/octet-stream":mime);
        cv.put("uri",uri==null?"":uri); cv.put("size_bytes",Math.max(0,size)); cv.put("imported_at",System.currentTimeMillis());
        return getWritableDatabase().insert("sources",null,cv);
    }

    public void addChunk(long sourceId,int part,String text) {
        ContentValues cv=new ContentValues();cv.put("source_id",sourceId);cv.put("part",part);cv.put("text",text==null?"":text);
        getWritableDatabase().insert("chunks",null,cv);
    }

    public void finishSource(long sourceId,int chars,int chunks,long parentMemoryId) {
        ContentValues cv=new ContentValues();cv.put("char_count",Math.max(0,chars));cv.put("chunk_count",Math.max(0,chunks));cv.put("parent_memory_id",Math.max(0,parentMemoryId));
        getWritableDatabase().update("sources",cv,"id=?",new String[]{String.valueOf(sourceId)});
    }

    public int countSources() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sources",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    public List<String[]> recentSources(int limit) {
        ArrayList<String[]> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,mime,uri,size_bytes,char_count,chunk_count,parent_memory_id,imported_at FROM sources ORDER BY imported_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext())out.add(new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),c.getString(3),String.valueOf(c.getLong(4)),String.valueOf(c.getInt(5)),String.valueOf(c.getInt(6)),String.valueOf(c.getLong(7)),String.valueOf(c.getLong(8))});
        }
        return out;
    }

    public String[] findSource(String query) {
        String q=query==null?"":query.trim().toLowerCase(); if(q.isEmpty())return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,mime,uri,size_bytes,char_count,chunk_count,parent_memory_id,imported_at FROM sources WHERE LOWER(name) LIKE ? ORDER BY imported_at DESC LIMIT 1",new String[]{"%"+q+"%"})){
            if(c.moveToFirst())return row(c);
        }
        for(String token:q.replaceAll("[^a-z0-9._-]"," ").split("\\s+")){
            if(token.length()<3)continue;
            try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,mime,uri,size_bytes,char_count,chunk_count,parent_memory_id,imported_at FROM sources WHERE LOWER(name) LIKE ? ORDER BY imported_at DESC LIMIT 1",new String[]{"%"+token+"%"})){if(c.moveToFirst())return row(c);}
        }
        return null;
    }

    private String[] row(Cursor c){return new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),c.getString(3),String.valueOf(c.getLong(4)),String.valueOf(c.getInt(5)),String.valueOf(c.getInt(6)),String.valueOf(c.getLong(7)),String.valueOf(c.getLong(8))};}

    public List<String> chunks(long sourceId,int limit) {
        ArrayList<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT text FROM chunks WHERE source_id=? ORDER BY part ASC LIMIT ?",new String[]{String.valueOf(sourceId),String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(c.getString(0));}
        return out;
    }

    public List<String[]> searchChunks(String query,int limit) {
        ArrayList<String[]> out=new ArrayList<>();String q=query==null?"":query.trim();if(q.isEmpty())return out;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.name,c.part,c.text FROM chunks c JOIN sources s ON s.id=c.source_id WHERE LOWER(c.text) LIKE ? OR LOWER(s.name) LIKE ? ORDER BY s.imported_at DESC,c.part ASC LIMIT ?",new String[]{"%"+q.toLowerCase()+"%","%"+q.toLowerCase()+"%",String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext())out.add(new String[]{c.getString(0),String.valueOf(c.getInt(1)),c.getString(2)});
        }
        return out;
    }

    public void deleteSource(long sourceId) {
        SQLiteDatabase db=getWritableDatabase();db.delete("chunks","source_id=?",new String[]{String.valueOf(sourceId)});db.delete("sources","id=?",new String[]{String.valueOf(sourceId)});
    }
}
