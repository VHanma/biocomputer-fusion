package com.hanma.echocore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline hybrid retrieval: Source Cortex LIKE search + FTS + semantic fingerprint + neural memory. */
public class HybridRetriever {
    public static class Hit {
        public long sourceId,part,memoryId; public String source="",text="",channel=""; public double score;
        public String label(){return channel+" · source "+sourceId+" · part "+part+" · "+String.format(Locale.US,"%.2f",score);}
    }
    private final AscendantStore asc; private final SourceCatalog sources; private final BrainDatabase brain;
    public HybridRetriever(AscendantStore a,SourceCatalog s,BrainDatabase b){asc=a;sources=s;brain=b;}

    public List<Hit> search(String query,int limit){
        int want=Math.max(1,Math.min(50,limit));Map<String,Hit> map=new HashMap<>();long qh=SemanticHasher.simhash(query);
        try{
            for(String[] r:asc.fts(query,Math.max(12,want*3))){Hit h=new Hit();h.sourceId=parse(r[0]);h.part=parse(r[1]);h.memoryId=parse(r[2]);h.text=r[3];h.channel="FTS";h.score=.86+.12*overlap(query,h.text);merge(map,h);}
        }catch(Throwable ignored){}
        try{
            for(String[] r:sources.searchChunks(query,Math.max(12,want*3))){Hit h=new Hit();h.source=r[0];h.part=parse(r[1]);h.text=r[2];h.channel="LEXICAL";h.score=.78+.18*overlap(query,h.text);merge(map,h);}
        }catch(Throwable ignored){}
        try{
            List<String[]> c=asc.semanticCandidates(1600);for(String[] r:c){long sh;try{sh=Long.parseLong(r[3]);}catch(Exception e){continue;}double sim=SemanticHasher.similarity(qh,sh);if(sim<.58)continue;Hit h=new Hit();h.sourceId=parse(r[0]);h.part=parse(r[1]);h.memoryId=parse(r[2]);h.text=r[4];h.channel="SEMANTIC";h.score=.35+.62*sim;merge(map,h);}
        }catch(Throwable ignored){}
        try{
            int n=0;for(MemoryNode m:brain.search(query,Math.max(8,want))){Hit h=new Hit();h.memoryId=m.id;h.text=m.text;h.channel="NEURAL";h.score=.58+.03*m.importance+.02*m.confidence;merge(map,h);if(++n>=want)break;}
        }catch(Throwable ignored){}
        ArrayList<Hit> out=new ArrayList<>(map.values());Collections.sort(out,(a,b)->Double.compare(b.score,a.score));if(out.size()>want)return new ArrayList<>(out.subList(0,want));return out;
    }

    public String evidenceBundle(String q,int limit){StringBuilder b=new StringBuilder();int i=1;for(Hit h:search(q,limit)){b.append('[').append(i++).append("] ").append(h.label()).append('\n').append(trim(h.text,900)).append("\n\n");}return b.toString().trim();}
    private void merge(Map<String,Hit> map,Hit h){String k=h.sourceId>0?h.sourceId+":"+h.part:(h.memoryId>0?"M:"+h.memoryId:"T:"+Integer.toHexString(h.text.hashCode()));Hit old=map.get(k);if(old==null)map.put(k,h);else{if(h.text.length()>old.text.length())old.text=h.text;if(!old.channel.contains(h.channel))old.channel=old.channel+"+"+h.channel;old.score=Math.min(1.25,Math.max(old.score,h.score)+.05);}}
    private static double overlap(String a,String b){ArrayList<String>x=SemanticHasher.tokens(a),y=SemanticHasher.tokens(b);if(x.isEmpty()||y.isEmpty())return 0;int n=0;for(String s:x)if(y.contains(s))n++;return n/(double)x.size();}
    private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
