package com.hanma.echocore;

import android.content.Context;
import java.util.List;
import java.util.Locale;

/** Multi-resident deliberation. v15 routes voices through persistent Continuum minds. */
public class CouncilEngine {
    private final AscendantStore city; private final HybridRetriever retriever; private final NexusOrchestrator nexus; private final ResidentMindEngine minds;
    public CouncilEngine(AscendantStore c,HybridRetriever r,NexusOrchestrator n){city=c;retriever=r;nexus=n;FoundationalArchive.install(city);ResidentExpansion.install(city);Context ctx=CrashShieldApp.context();ResidentMindEngine x=null;try{if(ctx!=null)x=new ResidentMindEngine(ctx,c,r,n);}catch(Throwable ignored){}minds=x;}

    public String convene(String topic,int maxResidents){
        String q=topic==null?"":topic.trim();if(q.isEmpty())return "Give the council a problem, goal, or question.";
        long session=city.startCouncil(q);List<String[]> residents=city.residents();StringBuilder transcript=new StringBuilder();int used=0;
        for(String[] r:residents){if(used>=Math.max(1,Math.min(30,maxResidents)))break;if("AWAY".equalsIgnoreCase(r[5])||"SLEEP".equalsIgnoreCase(r[5]))continue;used++;city.setResidentStatus(r[0],"WORKING");String voice=residentVoice(r,q);city.addCouncilMessage(session,r[0],"VOICE",voice);city.addResidentMemory(r[0],"Council topic: "+q+"\nMy contribution: "+trim(voice,2600),"COUNCIL",7,false);city.setResidentStatus(r[0],"HOME");transcript.append(r[1]).append(": ").append(voice).append("\n\n");}
        String synthesis=minds!=null?minds.synthesizeCouncil(q,residents,transcript.toString()):localSynthesis(q,transcript.toString());city.addCouncilMessage(session,"omega","SYNTHESIS",synthesis);city.addResidentMemory("omega","Council synthesis for: "+q+"\n"+trim(synthesis,3200),"SYNTHESIS",8,true);city.finishCouncil(session,synthesis);return synthesis;
    }

    public String speak(String residentId,String topic){String[] r=city.resident(residentId);if(r==null)return "Resident not found.";String q=topic==null?"":topic.trim();city.setResidentStatus(r[0],"WORKING");try{return minds!=null?minds.speak(r[0],q):legacyNatural(r,q);}finally{city.setResidentStatus(r[0],"HOME");}}

    private String residentVoice(String[] r,String topic){if(minds!=null)return minds.speak(r[0],topic);return legacyNatural(r,topic);}

    private String legacyNatural(String[] r,String topic){String id=r[0];String q=topic==null?"":topic.trim();String base;if("rival1".equals(id))base=nexus.localAnswer(q,"CRITIC");else if("zordon".equals(id))base=nexus.localAnswer(q,"PLANNER");else if("hermes".equals(id))base=nexus.localAnswer(q,"RESEARCH");else if("rival2".equals(id)||"leary".equals(id)||"lain".equals(id))base=nexus.localAnswer(q,"CREATIVE");else base=nexus.localAnswer(q,"DEEP");String core=strip(base);if(core.length()<80){String v=vault(id,q,4,3000);core=strip(v);}if(core.isEmpty())core="I do not have a strong enough anchor yet to pretend certainty. I would rather identify the missing piece and keep thinking than fill the gap with a canned answer.";if("hermes".equals(id))return "There is a thread here worth following. "+trim(core,2100);if("tesla".equals(id))return "I would turn this into a mechanism before I trusted the abstraction. "+trim(core,2100);if("rival1".equals(id))return "The useful move is to attack the weakest assumption first. "+trim(core,2100);if("rival2".equals(id))return "I see more than one route into this. "+trim(core,2100);if("lain".equals(id))return "The boundary is probably less stable than it looks. "+trim(core,2100);return trim(core,2300);}

    private String localSynthesis(String q,String transcript){String base=strip(nexus.localAnswer(q,"DEEP"));if(base.length()<80)base="The council has useful disagreement, but the next step should be the observation or build that eliminates the largest number of competing explanations.";return trim(base,2600);}
    private String vault(String id,String q,int limit,int chars){try(CivilizationVault v=new CivilizationVault(city)){return v.context(id,q,limit,chars);}catch(Throwable t){return "";}}
    private static String strip(String s){if(s==null)return "";return s.replaceAll("(?im)^(NEXUS|OMEGA|RESEARCH|CRITIC|TEACHER|CREATIVE|EXECUTIVE|CIVILIZATION VAULT|ROOT IDENTITY|ARCHIVE RECALL|CURRENT REASONING|LIVE USER EVIDENCE)[^\\n]*\\n?","").replaceAll("(?m)^\\[[MSV]\\d+\\]\\s*","").replaceAll("\\n{3,}","\\n\\n").trim();}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
}
