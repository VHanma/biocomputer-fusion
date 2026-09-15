package com.hanma.echocore;

import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Cloud-native multi-resident deliberation with relevant speaker selection and paced calls. */
public class CouncilEngine {
    private final AscendantStore city;
    private final ResidentMindEngine minds;

    public CouncilEngine(AscendantStore c,HybridRetriever r,NexusOrchestrator n){
        city=c;FoundationalArchive.install(city);ResidentExpansion.install(city);
        Context ctx=CrashShieldApp.context();ResidentMindEngine x=null;
        try{if(ctx!=null)x=new ResidentMindEngine(ctx,c,r,n);}catch(Throwable ignored){}
        minds=x;
    }

    public String convene(String topic,int maxResidents){
        String q=topic==null?"":topic.trim();if(q.isEmpty())return "Give the council a problem, goal, or question.";
        if(minds==null)return "The Council's cloud minds are reconnecting. I won't replace them with scripted local fragments.";

        long session=city.startCouncil(q);
        List<String[]> selected=selectResidents(q,Math.max(3,Math.min(6,maxResidents)));
        StringBuilder transcript=new StringBuilder();int used=0;
        for(String[] r:selected){
            if(r==null||r.length<7)continue;if("AWAY".equalsIgnoreCase(r[5])||"SLEEP".equalsIgnoreCase(r[5]))continue;
            city.setResidentStatus(r[0],"WORKING");
            try{
                String voice=minds.speak(r[0],councilPrompt(q,r[0]));
                if(unavailable(voice)){sleep(1450);voice=minds.speak(r[0],councilPrompt(q,r[0]));}
                if(unavailable(voice))continue;
                used++;
                city.addCouncilMessage(session,r[0],"VOICE",voice);
                city.addResidentMemory(r[0],"Council topic: "+q+"\nMy contribution: "+trim(voice,2600),"COUNCIL",7,false);
                transcript.append(r[1]).append(": ").append(voice).append("\n\n");
            }finally{city.setResidentStatus(r[0],"HOME");}
            sleep(1250);
        }

        if(used==0){String fail="The Council link is temporarily congested. I preserved the question rather than inventing a local synthesis.";city.finishCouncil(session,fail);return fail;}
        String synthesis=minds.synthesizeCouncil(q,selected,transcript.toString());
        city.addCouncilMessage(session,"omega","SYNTHESIS",synthesis);
        city.addResidentMemory("omega","Council synthesis for: "+q+"\n"+trim(synthesis,3200),"SYNTHESIS",8,true);
        city.finishCouncil(session,synthesis);
        return synthesis;
    }

    public String speak(String residentId,String topic){
        String[] r=city.resident(residentId);if(r==null)return "Resident not found.";
        String q=topic==null?"":topic.trim();city.setResidentStatus(r[0],"WORKING");
        try{return minds!=null?minds.speak(r[0],q):"The cloud mind link is reconnecting. I won't substitute a scripted local answer.";}
        finally{city.setResidentStatus(r[0],"HOME");}
    }

    private List<String[]> selectResidents(String q,int cap){
        String s=q.toLowerCase(Locale.US);Set<String> ids=new LinkedHashSet<>();
        if(has(s,"hermet","alchemy","kybalion","symbol","ancient","history","source","text","egypt","occult","kabbal"))ids.add("hermes");
        if(has(s,"tesla","electric","coil","circuit","frequency","resonan","oscillat","field","wireless","invent","energy","mechanism"))ids.add("tesla");
        if(has(s,"apk","android","software","code","app","database","compiler","camera","build","architecture"))ids.add("rival3");
        if(has(s,"evidence","experiment","test","confound","artifact","fals","measure","claim","proof"))ids.add("rival1");
        if(has(s,"creative","alternate","myth","fiction","story","art","strange","unconventional","analogy"))ids.add("rival2");
        if(has(s,"alien","ufo","uap","uso","contact","xeno","unknown signal","star"))ids.add("star-council");
        if(has(s,"leary","psychedel","conditioning","consciousness","circuit model","psychology"))ids.add("leary");
        if(has(s,"lain","wire","network","digital identity","protocol","distributed","cyber"))ids.add("lain");
        if(has(s,"mission","plan","roadmap","strategy","dependency","coordinate","execute"))ids.add("zordon");

        // Every council gets rigor, divergent possibility, and broad synthesis support.
        ids.add("rival1");ids.add("rival2");ids.add("sol");ids.add("rival3");ids.add("hermes");ids.add("tesla");ids.add("zordon");ids.add("star-council");ids.add("leary");ids.add("lain");
        List<String[]> out=new ArrayList<>();for(String id:ids){String[] r=city.resident(id);if(r!=null)out.add(r);if(out.size()>=cap)break;}return out;
    }

    private String councilPrompt(String q,String id){
        return "Council question: "+q+"\n\nGive your own considered contribution from your actual specialty and lived continuity. Do not summarize other residents. Do not print retrieval metadata. Disagree when your judgment differs. Be concise enough that other minds can respond.";
    }
    private static boolean unavailable(String x){String s=x==null?"":x.toLowerCase(Locale.US);return s.contains("cloud mind is temporarily unreachable")||s.contains("connection to my full mind is interrupted")||s.contains("transmission path is down")||s.contains("link is quiet right now");}
    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
}
