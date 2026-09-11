package com.hanma.echocore;

import java.util.List;

/** Multi-resident deliberation over foundational civilization memory plus live evidence. */
public class CouncilEngine {
    private final AscendantStore city; private final HybridRetriever retriever; private final NexusOrchestrator nexus;
    public CouncilEngine(AscendantStore c,HybridRetriever r,NexusOrchestrator n){city=c;retriever=r;nexus=n;FoundationalArchive.install(city);}

    public String convene(String topic,int maxResidents){
        String q=topic==null?"":topic.trim();if(q.isEmpty())return "Give the council a problem, goal, or question.";
        long session=city.startCouncil(q);String evidence=retriever.evidenceBundle(q,12);List<String[]> residents=city.residents();StringBuilder transcript=new StringBuilder();int used=0;
        for(String[] r:residents){if(used++>=Math.max(1,Math.min(24,maxResidents)))break;if("AWAY".equalsIgnoreCase(r[5])||"SLEEP".equalsIgnoreCase(r[5]))continue;city.setResidentStatus(r[0],"WORKING");String voice=residentVoice(r,q,evidence);city.addCouncilMessage(session,r[0],"VOICE",voice);city.addResidentMemory(r[0],"Council topic: "+q+"\nContribution: "+voice,"COUNCIL",7,false);city.setResidentStatus(r[0],"HOME");transcript.append(r[1]).append(" · ").append(r[4]).append("\n").append(voice).append("\n\n");}
        String synthesis=synthesize(q,evidence,transcript.toString());city.addCouncilMessage(session,"omega","SYNTHESIS",synthesis);city.addResidentMemory("omega","Council synthesis for: "+q+"\n"+synthesis,"SYNTHESIS",8,true);city.finishCouncil(session,synthesis);return "ASCENDANT COUNCIL · FOUNDATIONAL ARCHIVE ONLINE\n\n"+transcript+"OMEGA SYNTHESIS\n"+synthesis;
    }

    public String speak(String residentId,String topic){
        String[] r=city.resident(residentId);if(r==null)return "Resident not found.";String q=topic==null?"":topic.trim();city.setResidentStatus(r[0],"WORKING");String ev=retriever.evidenceBundle(q,10);String v=residentVoice(r,q,ev);city.addResidentMemory(r[0],"Topic: "+q+"\nResponse: "+v,"DIALOGUE",7,false);city.setResidentStatus(r[0],"HOME");return r[1]+" · "+r[3]+"\nFOUNDATIONAL ARCHIVE · ONLINE\n\n"+v;
    }

    private String residentVoice(String[] r,String topic,String evidence){
        String id=r[0];String identity=FoundationalArchive.identityAnswer(id,topic);String archive=FoundationalArchive.context(city,id,topic,2600);
        if(!identity.isEmpty()){
            String live=currentEvidenceNote(evidence);return identity+"\n\n"+live;
        }
        String base;
        if("rival1".equals(id))base=nexus.localAnswer(topic,"CRITIC");
        else if("zordon".equals(id))base=nexus.localAnswer(topic,"PLANNER");
        else if("hermes".equals(id))base=nexus.localAnswer(topic,"RESEARCH");
        else if("rival2".equals(id))base=nexus.localAnswer(topic,"CREATIVE");
        else if("tesla".equals(id)||"rival3".equals(id)||"star-council".equals(id))base=nexus.localAnswer(topic,"DEEP");
        else base=nexus.localAnswer(topic,"DEEP");
        if(weak(base))base=FoundationalArchive.fallback(id,topic,archive);
        return archive+"\n\nCURRENT REASONING\n"+trim(base,2300)+specialist(id)+currentEvidenceNote(evidence);
    }

    private String specialist(String id){
        if("tesla".equals(id))return "\n\nWORKSHOP INSTINCT\nTurn the idea into mechanism, geometry, components, expected waveforms, measurable outputs and a cheap prototype with a failure criterion.";
        if("hermes".equals(id))return "\n\nARCHIVE INSTINCT\nPreserve canon, source, observation, inference and speculation as separate channels. Trace important claims to origin and compare symbolic patterns by operation, not appearance alone.";
        if("zordon".equals(id))return "\n\nCOMMAND INSTINCT\nConvert the target into dependencies, assign the right resident to each branch, preserve checkpoints and attack the bottleneck with the greatest downstream unlock value.";
        if("rival1".equals(id))return "\n\nADVERSARIAL INSTINCT\nKeep unusual hypotheses alive while forcing them through controls, confounder checks and observations that can distinguish them from competing explanations.";
        if("rival2".equals(id))return "\n\nPRISM INSTINCT\nGenerate distant architectures, fictional analogues, inversions, cross-sensory mappings and biological analogies. Transfer function, not decorative resemblance.";
        if("rival3".equals(id))return "\n\nFORGE INSTINCT\nBuild a vertical slice, make state observable, isolate failures, bound resources, add rollback and refuse feature-count theater that weakens the central experience.";
        if("star-council".equals(id))return "\n\nSTELLAR INSTINCT\nDo not assume human channels, timescales or embodiment. Characterize the signal, preserve calibration, use redundant modalities and design challenge-response stages that reduce ambiguity.";
        if("omega".equals(id))return "\n\nOMEGA INSTINCT\nFuse compatible insights without erasing contradictions. Search adjacent mechanisms when the obvious implementation fails and preserve architectures that remain useful even if one assumption changes.";
        return "";
    }

    private String synthesize(String topic,String evidence,String transcript){
        String archive=FoundationalArchive.context(city,"omega",topic,2600);String grounded=nexus.localAnswer(topic,"DEEP");if(weak(grounded))grounded=FoundationalArchive.fallback("omega",topic,archive);
        StringBuilder b=new StringBuilder();b.append(archive).append("\n\nCOUNCIL SYNTHESIS\n");b.append("Target: ").append(topic).append("\n");if(evidence==null||evidence.trim().isEmpty())b.append("Current imported evidence is sparse, so the City is reasoning from inherited archive knowledge and explicit hypotheses until new source/observation evidence arrives.\n");else b.append("Current source/memory evidence was retrieved and layered on top of the Foundational Archive.\n");b.append("Resident disagreement is retained when useful instead of being averaged away.\n\n").append(trim(grounded,2400));b.append("\n\nOMEGA NEXT MOVE\nChoose the strongest unresolved mechanism, missing dependency or differentiating observation. Turn it into a mission step, source query or prototype and feed the result back into the City.");return b.toString();
    }

    private static String currentEvidenceNote(String evidence){if(evidence==null||evidence.trim().isEmpty())return "\n\nLIVE EVIDENCE\nNo matching imported source bundle was found for this turn. Root memory remains intact; this only means the present evidence layer is thin.";return "\n\nLIVE EVIDENCE\nCurrent imported memories/sources were retrieved for this turn and remain distinct from inherited archive memory.";}
    private static boolean weak(String s){if(s==null||s.trim().isEmpty())return true;String x=s.toLowerCase();return x.contains("not enough stored evidence")||x.contains("no matching source")||x.contains("do not have enough stored evidence");}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
