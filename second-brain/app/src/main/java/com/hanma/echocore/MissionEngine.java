package com.hanma.echocore;

import java.util.List;

/** Turns a goal into a durable dependency-aware mission rather than a disposable checklist. */
public class MissionEngine {
    private final AscendantStore store; private final HybridRetriever retriever;
    public MissionEngine(AscendantStore s,HybridRetriever r){store=s;retriever=r;}
    public long create(String title,String goal,int priority){
        String g=goal==null||goal.trim().isEmpty()?title:goal.trim();long id=store.addMission(title,g,priority);
        store.addMissionStep(id,"Define observable finish condition","","What would count as completion?");
        store.addMissionStep(id,"Map existing evidence and capabilities","1","Relevant sources, memories, tools and constraints");
        store.addMissionStep(id,"Identify highest-information unknown","2","What uncertainty blocks the next decision?");
        store.addMissionStep(id,"Design the smallest discriminating action","3","Test or action that separates competing possibilities");
        store.addMissionStep(id,"Execute and capture observations","4","Observed result with provenance");
        store.addMissionStep(id,"Compare prediction against result","5","Mismatch, confidence update and contradictions");
        store.addMissionStep(id,"Consolidate reusable skill / procedure","6","What should become durable procedure or knowledge?");
        store.addMissionStep(id,"Choose next bottleneck or finish","7","Remaining dependency or proof of completion");
        return id;
    }
    public String brief(long id){StringBuilder b=new StringBuilder();for(String[] m:store.missions(100)){if(parse(m[0])!=id)continue;b.append(m[1]).append("\nGoal: ").append(m[2]).append("\nStatus: ").append(m[3]).append(" · priority ").append(m[4]).append("\n\n");break;}int i=1;for(String[] s:store.missionSteps(id)){b.append(i++).append(". [").append(s[2]).append("] ").append(s[1]);if(!s[4].isEmpty())b.append("\n   Evidence: ").append(s[4]);b.append('\n');}return b.toString().trim();}
    public String evidenceForMission(long id){String goal="";for(String[]m:store.missions(100))if(parse(m[0])==id){goal=m[2];break;}if(goal.isEmpty())return "Mission not found.";String e=retriever.evidenceBundle(goal,10);return e.isEmpty()?"No strong local evidence found yet. This mission has a knowledge gap.":e;}
    public String nextAction(long id){List<String[]> steps=store.missionSteps(id);for(String[]s:steps)if(!"DONE".equalsIgnoreCase(s[2]))return s[1]+(s[4].isEmpty()?"":"\nNeed: "+s[4]);return "Mission steps are complete. Verify the finish condition before archiving.";}
    private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
}
