package com.hanma.echocore;

import android.database.Cursor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline hybrid retrieval with quality weighting and duplicate suppression. */
public class HybridRetriever {
    public static class Hit {public long sourceId,part,memoryId;public String source="",text="",channel="";public double score;public int quality=5;public String label(){return channel+" · source "+sourceId+" · part "+part+" · q"+quality+" · "+String.format(Locale.US,"%.2f",score);}}
    private final AscendantStore asc;private final SourceCatalog sources;private final BrainDatabase brain;
    public HybridRetriever(AscendantStore a,SourceCatalog s,BrainDatabase b){asc=a;sources=s;brain=b;ensureQualitySchema();}

    public List<Hit> search(String query,int limit){
        int want=Math.max(1,Math.min(50,limit));Map<String,Hit> map=new HashMap<>();long qh=SemanticHasher.simhash(query);
        try{for(String[] r:asc.fts(query,Math.max(18,want*4))){Hit h=new Hit();h.sourceId=parse(r[0]);h.part=parse(r[1]);h.memoryId=parse(r[2]);h.text=r[3];h.channel="FTS";if(!qualityGate(h))continue;h.score=.78+.14*overlap(query,h.text)+qualityBonus(h.quality);merge(map,h);}}catch(Throwable ignored){}
        try{for(String[] r:sources.searchChunks(query,Math.max(18,want*4))){Hit h=new Hit();h.source=r[0];h.part=parse(r[1]);h.text=KnowledgeCleaner.clean(r[2]);if(h.text.length()<18)continue;h.channel="LEXICAL";h.score=.68+.18*overlap(query,h.text);merge(map,h);}}catch(Throwable ignored){}
        try{List<String[]> c=asc.semanticCandidates(2600);for(String[] r:c){long sh;try{sh=Long.parseLong(r[3]);}catch(Exception e){continue;}double sim=SemanticHasher.similarity(qh,sh);if(sim<.56)continue;Hit h=new Hit();h.sourceId=parse(r[0]);h.part=parse(r[1]);h.memoryId=parse(r[2]);h.text=r[4];h.channel="SEMANTIC";if(!qualityGate(h))continue;h.score=.31+.60*sim+qualityBonus(h.quality);merge(map,h);}}catch(Throwable ignored){}
        try{int n=0;for(MemoryNode m:brain.search(query,Math.max(12,want*2))){String clean=KnowledgeCleaner.clean(m.text);if(clean.length()<18)continue;Hit h=new Hit();h.memoryId=m.id;h.text=clean;h.channel="NEURAL";h.score=.50+.025*m.importance+.018*m.confidence;merge(map,h);if(++n>=want*2)break;}}catch(Throwable ignored){}
        ArrayList<Hit> out=new ArrayList<>(map.values());Collections.sort(out,(a,b)->Double.compare(b.score,a.score));if(out.size()>want)return new ArrayList<>(out.subList(0,want));return out;
    }
    public String evidenceBundle(String q,int limit){StringBuilder b=new StringBuilder();int i=1;for(Hit h:search(q,limit)){b.append('[').append(i++).append("] ").append(h.label()).append('\n').append(trim(h.text,900)).append("\n\n");}return b.toString().trim();}

    private boolean qualityGate(Hit h){if(h.sourceId<=0||h.part<=0)return true;try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT quality,duplicate_of FROM knowledge_quality WHERE source_id=? AND part=?",new String[]{String.valueOf(h.sourceId),String.valueOf(h.part)})){if(!c.moveToFirst())return true;h.quality=c.getInt(0);long dup=c.getLong(1);return h.quality>=3&&dup<=0;}catch(Throwable t){return true;}}
    private void ensureQualitySchema(){try{asc.getWritableDatabase().execSQL("CREATE TABLE IF NOT EXISTS knowledge_quality(source_id INTEGER NOT NULL,part INTEGER NOT NULL,sim_hash INTEGER NOT NULL DEFAULT 0,quality INTEGER NOT NULL DEFAULT 5,duplicate_of INTEGER NOT NULL DEFAULT 0,heading TEXT NOT NULL DEFAULT '',clean_len INTEGER NOT NULL DEFAULT 0,method TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL,PRIMARY KEY(source_id,part))");}catch(Throwable ignored){}}
    private static double qualityBonus(int q){return Math.max(-.08,Math.min(.12,(q-5)*.025));}
    private void merge(Map<String,Hit> map,Hit h){String normalized=h.text==null?"":KnowledgeCleaner.clean(h.text).toLowerCase(Locale.US).replaceAll("\\s+"," ");String k=h.sourceId>0?h.sourceId+":"+h.part:"T:"+Long.toHexString(SemanticHasher.simhash(normalized));Hit old=map.get(k);if(old==null)map.put(k,h);else{if(h.text.length()>old.text.length())old.text=h.text;if(!old.channel.contains(h.channel))old.channel=old.channel+"+"+h.channel;old.score=Math.min(1.35,Math.max(old.score,h.score)+.05);old.quality=Math.max(old.quality,h.quality);}}
    private static double overlap(String a,String b){ArrayList<String>x=SemanticHasher.tokens(a),y=SemanticHasher.tokens(b);if(x.isEmpty()||y.isEmpty())return 0;int n=0;for(String s:x)if(y.contains(s))n++;return n/(double)x.size();}
    private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
