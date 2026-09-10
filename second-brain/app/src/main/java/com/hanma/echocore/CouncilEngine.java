package com.hanma.echocore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Multi-resident deliberation over one shared evidence substrate. Residents are software reasoning profiles. */
public class CouncilEngine {
    private final AscendantStore city; private final HybridRetriever retriever; private final NexusOrchestrator nexus;
    public CouncilEngine(AscendantStore c,HybridRetriever r,NexusOrchestrator n){city=c;retriever=r;nexus=n;}

    public String convene(String topic,int maxResidents){
        String q=topic==null?"":topic.trim();if(q.isEmpty())return "Give the council a problem, goal, or question.";
        long session=city.startCouncil(q);String evidence=retriever.evidenceBundle(q,10);List<String[]> residents=city.residents();StringBuilder transcript=new StringBuilder();int used=0;
        for(String[] r:residents){if(used++>=Math.max(1,Math.min(20,maxResidents)))break;if("AWAY".equalsIgnoreCase(r[5])||"SLEEP".equalsIgnoreCase(r[5]))continue;String voice=residentVoice(r,q,evidence);city.addCouncilMessage(session,r[0],"VOICE",voice);city.addResidentMemory(r[0],"Council topic: "+q+"\nContribution: "+voice,"COUNCIL",6,false);city.setResidentStatus(r[0],"HOME");transcript.append(r[1]).append(" · ").append(r[4]).append("\n").append(voice).append("\n\n");}
        String synthesis=synthesize(q,evidence,transcript.toString());city.addCouncilMessage(session,"omega","SYNTHESIS",synthesis);city.finishCouncil(session,synthesis);return "ASCENDANT COUNCIL\n\n"+transcript+"OMEGA SYNTHESIS\n"+synthesis;
    }

    public String speak(String residentId,String topic){String[] r=city.resident(residentId);if(r==null)return "Resident not found.";String q=topic==null?"":topic.trim();String ev=retriever.evidenceBundle(q,8);String v=residentVoice(r,q,ev);city.addResidentMemory(r[0],"Topic: "+q+"\nResponse: "+v,"DIALOGUE",5,false);city.setResidentStatus(r[0],"HOME");return r[1]+" · "+r[3]+"\n\n"+v;}

    private String residentVoice(String[] r,String topic,String evidence){String id=r[0],lens=r[6];String base;
        if("rival1".equals(id))base=nexus.localAnswer(topic,"CRITIC");
        else if("zordon".equals(id))base=nexus.localAnswer(topic,"PLANNER");
        else if("hermes".equals(id))base=nexus.localAnswer(topic,"RESEARCH");
        else if("rival2".equals(id))base=nexus.localAnswer(topic,"CREATIVE");
        else if("tesla".equals(id)||"rival3".equals(id))base=nexus.localAnswer(topic,"DEEP");
        else base=nexus.localAnswer(topic,"DEEP");
        String extra="";
        if("tesla".equals(id))extra="\n\nENGINEERING LENS\nMechanism: identify inputs, transformations, outputs, measurable failure modes, and the cheapest prototype that could disprove the design.";
        else if("hermes".equals(id))extra="\n\nPROVENANCE LENS\nKeep source statements, user observations, inference, and speculation in separate lanes. Trace every important claim back to its origin.";
        else if("zordon".equals(id))extra="\n\nCOMMAND LENS\nChoose the next dependency whose resolution unlocks the most downstream work. Preserve checkpoints so the mission can resume after interruption.";
        else if("rival1".equals(id))extra="\n\nADVERSARIAL LENS\nFind the strongest competing explanation and the observation that would distinguish it from the current favorite.";
        else if("rival2".equals(id))extra="\n\nEXPLORER LENS\nGenerate at least one distant architecture and one inversion of the obvious approach. Label them possibilities until evidence supports them.";
        else if("rival3".equals(id))extra="\n\nFORGE LENS\nPrefer designs with fault isolation, rollback, observable state, bounded resources, and a working smallest slice before scale.";
        else if("omega".equals(id))extra="\n\nSYNTHESIS LENS\nMerge compatible insights, keep unresolved contradictions visible, and prefer architectures that remain useful even when one assumption is wrong.";
        return "Lens: "+lens+"\n\n"+trim(base,1900)+extra;
    }

    private String synthesize(String topic,String evidence,String transcript){StringBuilder b=new StringBuilder();b.append("Topic: ").append(topic).append("\n");if(evidence==null||evidence.isEmpty())b.append("Evidence state: sparse. Council output is primarily planning/hypothesis generation, not proof.\n");else b.append("Evidence state: local source/memory evidence was retrieved before deliberation.\n");b.append("Shared rule: residents may disagree. Disagreement is retained until a test or stronger evidence resolves it.\n\n");String grounded=nexus.localAnswer(topic,"DEEP");b.append(trim(grounded,2100));b.append("\n\nCouncil action: convert the strongest unresolved point into either a mission dependency, a source query, or a falsifiable test.");return b.toString();}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
