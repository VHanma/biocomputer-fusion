package com.hanma.echocore;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns raw imported chunks into a cleaner retrieval layer while preserving the original
 * SourceCatalog text for provenance. Quality, duplicate and hierarchy metadata are rebuildable.
 */
public class SourceMetabolism {
    private final AscendantStore asc; private final ClaimGraphEngine claims;
    public SourceMetabolism(AscendantStore a){asc=a;claims=new ClaimGraphEngine(a);ensureSchema();}

    private void ensureSchema(){SQLiteDatabase d=asc.getWritableDatabase();
        d.execSQL("CREATE TABLE IF NOT EXISTS knowledge_quality(source_id INTEGER NOT NULL,part INTEGER NOT NULL,sim_hash INTEGER NOT NULL DEFAULT 0,quality INTEGER NOT NULL DEFAULT 5,duplicate_of INTEGER NOT NULL DEFAULT 0,heading TEXT NOT NULL DEFAULT '',clean_len INTEGER NOT NULL DEFAULT 0,method TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL,PRIMARY KEY(source_id,part))");
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_kq_source_quality ON knowledge_quality(source_id,quality DESC,part ASC)");
    }

    public int metabolize(SourceCatalog catalog,BrainDatabase brain,long sourceId,String sourceName,String method){
        SQLiteDatabase ad=asc.getWritableDatabase();ad.delete("knowledge_quality","source_id=?",new String[]{String.valueOf(sourceId)});ad.delete("hierarchy_nodes","source_id=?",new String[]{String.valueOf(sourceId)});
        try{ad.delete("semantic_index","source_id=?",new String[]{String.valueOf(sourceId)});ad.delete("knowledge_fts","source_id=?",new String[]{String.valueOf(sourceId)});}catch(Throwable ignored){}
        int indexed=0,duplicates=0,garbage=0,lastPart=0,headings=0;long root=hierarchyRoot(sourceId,sourceName),activeSection=0;int activeStart=0;String activeLabel="";
        Map<Long,Integer> exact=new HashMap<>();ArrayDeque<Recent> recent=new ArrayDeque<>();SQLiteDatabase sdb=catalog.getReadableDatabase();
        try(Cursor c=sdb.rawQuery("SELECT part,text FROM chunks WHERE source_id=? ORDER BY part",new String[]{String.valueOf(sourceId)})){
            while(c.moveToNext()){
                int part=c.getInt(0);lastPart=part;String raw=c.getString(1);KnowledgeCleaner.Analysis a=KnowledgeCleaner.analyze(raw);long mem=findMemory(brain,raw);int duplicateOf=0;
                if(!a.garbage){Integer same=exact.get(a.simhash);if(same!=null)duplicateOf=same;else{for(Recent r:recent){int hd=Long.bitCount(a.simhash^r.hash);double ratio=Math.min(a.cleaned.length(),r.text.length())/(double)Math.max(1,Math.max(a.cleaned.length(),r.text.length()));if(hd<=3&&ratio>.78&&KnowledgeCleaner.tokenJaccard(a.cleaned,r.text)>.88){duplicateOf=r.part;break;}}}}
                if(duplicateOf>0)duplicates++;if(a.garbage)garbage++;
                recordQuality(sourceId,part,a,duplicateOf,method);
                if(!a.garbage&&duplicateOf==0){
                    exact.put(a.simhash,part);recent.addLast(new Recent(part,a.simhash,a.cleaned));while(recent.size()>28)recent.removeFirst();
                    try{asc.indexText(sourceId,part,mem,a.cleaned);}catch(Throwable ignored){}
                    if(mem>0)try{asc.evidence(mem,"SOURCE",sourceId,part,0,method,Math.max(3,a.quality));}catch(Throwable ignored){}
                    if(a.quality>=6&&!hasClaim(sourceId,part))try{claims.indexChunk(sourceId,part,a.cleaned);}catch(Throwable ignored){}
                    indexed++;
                }
                if(!a.heading.isEmpty()&&a.quality>=5){if(activeSection>0&&part>activeStart)closeSection(activeSection,part-1);activeStart=part;activeLabel=a.heading;activeSection=hierarchySection(sourceId,root,part,part,a.heading);headings++;}
            }
        }
        if(activeSection>0&&lastPart>=activeStart)closeSection(activeSection,lastPart);
        if(headings==0&&lastPart>0)fallbackSections(sourceId,root,lastPart);
        if(root>0&&lastPart>0){ContentValues v=new ContentValues();v.put("part_end",lastPart);ad.update("hierarchy_nodes",v,"id=?",new String[]{String.valueOf(root)});}
        asc.blackbox("METABOLISM","SOURCE_REFINED",safe(sourceName)+" · indexed "+indexed+" · duplicates "+duplicates+" · low-quality "+garbage+" · headings "+headings,"");
        return indexed;
    }

    public int quality(long sourceId,long part){try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT quality FROM knowledge_quality WHERE source_id=? AND part=?",new String[]{String.valueOf(sourceId),String.valueOf(part)})){return c.moveToFirst()?c.getInt(0):5;}catch(Throwable t){return 5;}}
    public long duplicateOf(long sourceId,long part){try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT duplicate_of FROM knowledge_quality WHERE source_id=? AND part=?",new String[]{String.valueOf(sourceId),String.valueOf(part)})){return c.moveToFirst()?c.getLong(0):0;}catch(Throwable t){return 0;}}

    private void recordQuality(long sid,int part,KnowledgeCleaner.Analysis a,int dup,String method){ContentValues v=new ContentValues();v.put("source_id",sid);v.put("part",part);v.put("sim_hash",a.simhash);v.put("quality",a.quality);v.put("duplicate_of",dup);v.put("heading",a.heading);v.put("clean_len",a.cleaned.length());v.put("method",safe(method));v.put("updated_at",System.currentTimeMillis());asc.getWritableDatabase().insertWithOnConflict("knowledge_quality",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private boolean hasClaim(long sid,int part){try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT 1 FROM claims WHERE source_id=? AND part_start=? LIMIT 1",new String[]{String.valueOf(sid),String.valueOf(part)})){return c.moveToFirst();}catch(Throwable t){return false;}}
    private long findMemory(BrainDatabase brain,String text){try(Cursor c=brain.getReadableDatabase().rawQuery("SELECT id FROM memories WHERE type='KNOWLEDGE' AND text=? ORDER BY id DESC LIMIT 1",new String[]{text})){return c.moveToFirst()?c.getLong(0):0;}catch(Throwable t){return 0;}}
    private long hierarchyRoot(long sourceId,String name){ContentValues v=new ContentValues();v.put("source_id",sourceId);v.put("parent_id",0);v.put("node_type","DOCUMENT");v.put("label",safe(name).isEmpty()?"Source":safe(name));v.put("part_start",1);v.put("part_end",0);v.put("depth",0);v.put("created_at",System.currentTimeMillis());return asc.getWritableDatabase().insert("hierarchy_nodes",null,v);}
    private long hierarchySection(long sourceId,long root,int start,int end,String label){ContentValues v=new ContentValues();v.put("source_id",sourceId);v.put("parent_id",root);v.put("node_type","SECTION");v.put("label",safe(label));v.put("part_start",start);v.put("part_end",end);v.put("depth",1);v.put("created_at",System.currentTimeMillis());return asc.getWritableDatabase().insert("hierarchy_nodes",null,v);}
    private void closeSection(long id,int end){ContentValues v=new ContentValues();v.put("part_end",end);asc.getWritableDatabase().update("hierarchy_nodes",v,"id=?",new String[]{String.valueOf(id)});}
    private void fallbackSections(long sid,long root,int last){int span=24;for(int s=1;s<=last;s+=span){int e=Math.min(last,s+span-1);hierarchySection(sid,root,s,e,"Passages "+s+"–"+e);}}
    private static String safe(String s){return s==null?"":s.trim();}
    private static class Recent{final int part;final long hash;final String text;Recent(int p,long h,String t){part=p;hash=h;text=t;}}
}
