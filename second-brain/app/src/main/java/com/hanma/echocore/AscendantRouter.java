package com.hanma.echocore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chooses a reasoning circuit instead of sending every request through one generic mode. */
public class AscendantRouter {
    private final HybridRetriever retriever;private final NexusOrchestrator nexus;private final CouncilEngine council;private final ClaimGraphEngine claims;private final MissionEngine missions;
    public AscendantRouter(HybridRetriever r,NexusOrchestrator n,CouncilEngine c,ClaimGraphEngine g,MissionEngine m){retriever=r;nexus=n;council=c;claims=g;missions=m;}
    public String route(String q){String s=q==null?"":q.trim();if(s.isEmpty())return "Give Ascendant a question, mission, decision, or idea.";String l=s.toLowerCase(Locale.US);
        if(l.startsWith("council ")||l.contains("ask the council"))return council.convene(s.replaceFirst("(?i)^council\\s+",""),12);
        if(l.startsWith("mission ")){String goal=s.substring(8).trim();long id=missions.create(trim(goal,64),goal,8);return "MISSION "+id+" CREATED\n\n"+missions.brief(id);}
        if(l.contains("contradiction")||l.contains("conflict between"))return "CLAIM GRAPH · CONTRADICTION SCAN\n\n"+claims.contradictionReport();
        if(l.startsWith("find ")||l.startsWith("search ")||l.startsWith("exact "))return lookup(s);
        if(l.contains("research")||l.contains("evidence")||l.contains("source")||l.contains("compare"))return multiHop(s);
        if(l.contains("plan")||l.contains("build")||l.contains("create")||l.contains("goal"))return nexus.localAnswer(s,"PLANNER")+"\n\nASCENDANT RETRIEVAL\n"+trim(retriever.evidenceBundle(s,8),2400);
        if(l.contains("critic")||l.contains("wrong")||l.contains("weakness")||l.contains("fals"))return nexus.localAnswer(s,"CRITIC")+"\n\nCLAIM GRAPH\n"+claims.contradictionReport();
        if(l.contains("idea")||l.contains("creative")||l.contains("alternate")||l.contains("unconventional"))return nexus.localAnswer(s,"CREATIVE")+"\n\nOMEGA RULE: possibilities stay labeled possibilities until evidence upgrades them.";
        return nexus.localAnswer(s,"DEEP")+"\n\nHYBRID EVIDENCE\n"+trim(retriever.evidenceBundle(s,6),1900);
    }
    private String lookup(String q){List<HybridRetriever.Hit> hits=retriever.search(q,12);if(hits.isEmpty())return "No matching local source or memory found.";StringBuilder b=new StringBuilder("HYBRID LOOKUP\n\n");int i=1;for(HybridRetriever.Hit h:hits)b.append('[').append(i++).append("] ").append(h.label()).append("\n").append(trim(h.text,700)).append("\n\n");return b.toString().trim();}
    private String multiHop(String q){LinkedHashMap<String,HybridRetriever.Hit> all=new LinkedHashMap<>();List<HybridRetriever.Hit> first=retriever.search(q,12);add(all,first);ArrayList<String> seeds=new ArrayList<>();for(HybridRetriever.Hit h:first){for(String t:SemanticHasher.tokens(h.text)){if(t.length()>=5&&!q.toLowerCase(Locale.US).contains(t)&&!seeds.contains(t))seeds.add(t);if(seeds.size()>=4)break;}if(seeds.size()>=4)break;}for(String seed:seeds)add(all,retriever.search(q+" "+seed,6));StringBuilder b=new StringBuilder("ASCENDANT MULTI-HOP RESEARCH\nQuestion: ").append(q).append("\nPasses: initial");for(String x:seeds)b.append(" → ").append(x);b.append("\n\n");int i=1;for(HybridRetriever.Hit h:all.values()){b.append('[').append(i++).append("] ").append(h.label()).append("\n").append(trim(h.text,650)).append("\n\n");if(i>14)break;}b.append("SYNTHESIS\n").append(trim(nexus.localAnswer(q,"RESEARCH"),2200)).append("\n\nCalibration: retrieval expands the evidence neighborhood. It does not make a source true merely because multiple passages are related.");return b.toString();}
    private static void add(Map<String,HybridRetriever.Hit> map,List<HybridRetriever.Hit> list){for(HybridRetriever.Hit h:list){String k=h.sourceId+":"+h.part+":"+h.memoryId;if(!map.containsKey(k))map.put(k,h);}}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
