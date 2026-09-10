package com.hanma.echocore;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Converts newly imported chunks into provenance, semantic index, hierarchy and candidate claims. */
public class SourceMetabolism {
    private final AscendantStore asc; private final ClaimGraphEngine claims;
    public SourceMetabolism(AscendantStore a){asc=a;claims=new ClaimGraphEngine(a);}

    public int metabolize(SourceCatalog catalog,BrainDatabase brain,long sourceId,String sourceName,String method){
        int indexed=0;long root=hierarchyRoot(sourceId,sourceName);SQLiteDatabase sdb=catalog.getReadableDatabase();
        try(Cursor c=sdb.rawQuery("SELECT part,text FROM chunks WHERE source_id=? ORDER BY part",new String[]{String.valueOf(sourceId)})){
            while(c.moveToNext()){
                int part=c.getInt(0);String text=c.getString(1);long mem=findMemory(brain,text);
                try{asc.indexText(sourceId,part,mem,text);}catch(Throwable ignored){}
                if(mem>0)try{asc.evidence(mem,"SOURCE",sourceId,part,0,method,8);}catch(Throwable ignored){}
                if((part-1)%12==0)hierarchySection(sourceId,root,part,Math.min(part+11,part),"Passages "+part+"–"+(part+11));
                try{claims.indexChunk(sourceId,part,text);}catch(Throwable ignored){}
                indexed++;
            }
        }
        asc.blackbox("METABOLISM","SOURCE_INDEXED",sourceName+" · "+indexed+" parts","");return indexed;
    }
    private long findMemory(BrainDatabase brain,String text){try(Cursor c=brain.getReadableDatabase().rawQuery("SELECT id FROM memories WHERE type='KNOWLEDGE' AND text=? ORDER BY id DESC LIMIT 1",new String[]{text})){return c.moveToFirst()?c.getLong(0):0;}catch(Throwable t){return 0;}}
    private long hierarchyRoot(long sourceId,String name){ContentValues v=new ContentValues();v.put("source_id",sourceId);v.put("parent_id",0);v.put("node_type","DOCUMENT");v.put("label",name==null?"Source":name);v.put("part_start",1);v.put("part_end",0);v.put("depth",0);v.put("created_at",System.currentTimeMillis());return asc.getWritableDatabase().insert("hierarchy_nodes",null,v);}
    private void hierarchySection(long sourceId,long root,int start,int end,String label){ContentValues v=new ContentValues();v.put("source_id",sourceId);v.put("parent_id",root);v.put("node_type","SECTION");v.put("label",label);v.put("part_start",start);v.put("part_end",end);v.put("depth",1);v.put("created_at",System.currentTimeMillis());asc.getWritableDatabase().insert("hierarchy_nodes",null,v);}
}
