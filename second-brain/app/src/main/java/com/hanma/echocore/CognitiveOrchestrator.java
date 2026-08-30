package com.hanma.echocore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CognitiveOrchestrator {
    public static class Evidence {
        public String id,label,text,kind;
        public int confidence;
        Evidence(String id,String label,String text,String kind,int confidence){this.id=id;this.label=label;this.text=text;this.kind=kind;this.confidence=confidence;}
    }

    private final BrainDatabase brain;
    private final BrainEngine engine;
    private final SourceCatalog sources;
    private final CognitiveStore store;

    public CognitiveOrchestrator(BrainDatabase brain,BrainEngine engine,SourceCatalog sources,CognitiveStore store){this.brain=brain;this.engine=engine;this.sources=sources;this.store=store;}

    public String localAnswer(String question,String mode){
        String q=safe(question);String m=mode==null?"DEEP":mode.toUpperCase(Locale.US);if(q.isEmpty())return "Give Omega a question, problem, decision, goal, or idea.";
        if(m.equals("PLANNER"))return planner(q);
        if(m.equals("CREATIVE"))return creative(q);
        if(m.equals("CRITIC"))return critic(q);
        if(m.equals("TEACHER"))return teacher(q);
        if(m.equals("RESEARCH"))return research(q);
        return deep(q);
    }

    public List<Evidence> retrieve(String q,int limit){
        ArrayList<Evidence> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        List<MemoryNode> memories=brain.search(q,Math.min(12,Math.max(4,limit)));
        int mi=1;for(MemoryNode n:memories){String key="m:"+n.id;if(!seen.add(key))continue;out.add(new Evidence("M"+mi++,n.type,trim(n.text,900),"MEMORY",n.confidence));if(out.size()>=limit)break;}
        ArrayList<String[]> sourceHits=new ArrayList<>(sources.searchChunks(q,Math.min(12,Math.max(4,limit))));
        if(sourceHits.size()<3){for(String token:significant(q)){if(sourceHits.size()>=12)break;for(String[] h:sources.searchChunks(token,4)){String k=h[0]+"#"+h[1];boolean exists=false;for(String[] x:sourceHits)if((x[0]+"#"+x[1]).equals(k)){exists=true;break;}if(!exists)sourceHits.add(h);}}}
        int si=1;for(String[] h:sourceHits){String key="s:"+h[0]+":"+h[1];if(!seen.add(key))continue;out.add(new Evidence("S"+si++,h[0]+" · part "+h[1],trim(h[2],1100),"SOURCE",8));if(out.size()>=limit)break;}
        return out;
    }

    public String buildGroundedContext(String q,int limit){
        List<Evidence> ev=retrieve(q,limit);StringBuilder b=new StringBuilder();for(Evidence e:ev)b.append("[").append(e.id).append("] ").append(e.kind).append(" · ").append(e.label).append(" · confidence ").append(e.confidence).append("/10\n").append(e.text).append("\n\n");return b.toString().trim();
    }

    public String systemPrompt(String mode){
        return "You are EchoCore Omega, a private second-brain reasoning layer. Ground claims in the supplied EVIDENCE. Distinguish stored facts, user beliefs, source text, inference, uncertainty, and speculation. Cite evidence IDs like [M1] or [S2] for factual claims. If the evidence is insufficient, say so plainly. Look for contradictions and missing evidence. Mode="+mode+". Do not invent citations or pretend that imported material says something absent from the evidence.";
    }

    public String deepReviewPrompt(String question,String draft,String context){
        return "QUESTION:\n"+question+"\n\nEVIDENCE:\n"+context+"\n\nDRAFT:\n"+draft+"\n\nAudit the draft for unsupported claims, ignored counterevidence, uncertainty, and citation mistakes. Then return a corrected final answer. Keep evidence citations in [M#]/[S#] form and label inference when needed.";
    }

    public String knowledgeGaps(){
        int beliefs=brain.countType("BELIEF"),questions=brain.countType("QUESTION"),refs=brain.countType("REFERENCE")+brain.countType("KNOWLEDGE"),src=sources.countSources();
        StringBuilder b=new StringBuilder("KNOWLEDGE-GAP SCAN\n");
        if(src==0)b.append("• No full sources are indexed yet. Imported evidence would dramatically improve grounding.\n");
        if(beliefs>0&&refs<beliefs)b.append("• Stored beliefs outnumber evidence/reference traces. Pick the strongest beliefs and attach supporting or opposing sources.\n");
        if(questions<Math.max(2,beliefs/3))b.append("• Few explicit QUESTION nodes exist. The brain may be answering faster than it is testing itself.\n");
        List<MemoryNode> strong=brain.strongest(20);Map<String,Integer> f=frequency(strong);List<String> top=top(f,5);if(!top.isEmpty())b.append("• Attention is concentrated around ").append(String.join(", ",top)).append(". Ask what important domain is missing from that cluster.\n");
        b.append("• Calibration question: what observation would change one of your highest-confidence beliefs?\n");
        b.append("• Provenance question: which important memory came from experience, which from a document, and which is only an inference?");return b.toString();
    }

    public String autopilotCycle(){
        String consolidation=engine.consolidate();String tension=engine.answer("contradictions");String gaps=knowledgeGaps();
        List<MemoryNode> strong=brain.strongest(6);String seed=strong.isEmpty()?"No dominant trace yet.":trim(strong.get(0).text,180);
        String insight="AUTOPILOT reflection: strongest current trace is “"+seed+"”. Next useful move: test it against a source, a counterexample, or an active goal before increasing confidence.";
        brain.addMemoryRich(insight,"INSIGHT",engine.autoTags(insight)+", autopilot",7,0,6,7,false);
        return "OMEGA AUTOPILOT CYCLE\n\n"+consolidation+"\n\n"+tension+"\n\n"+gaps+"\n\nNew reflection encoded:\n"+insight;
    }

    public List<String> planSteps(String goal){
        ArrayList<String> x=new ArrayList<>();String g=safe(goal);if(g.isEmpty())return x;
        x.add("Define the finish condition for: "+g);
        x.add("Collect the strongest existing memories and source evidence related to the goal.");
        x.add("Identify the biggest unknown, dependency, or failure point.");
        x.add("Choose the smallest concrete action that reduces that uncertainty.");
        x.add("Run the action and capture the result as OBSERVATION or REFERENCE evidence.");
        x.add("Compare result vs prediction; update confidence instead of merely adding another opinion.");
        x.add("Consolidate what worked into a PROCEDURE or SKILL memory and select the next bottleneck.");
        return x;
    }

    private String deep(String q){
        List<Evidence> ev=retrieve(q,12);if(ev.isEmpty())return "I do not have enough stored evidence for that yet. Import a source, capture an observation, or ask me to plan how to investigate it.";
        StringBuilder b=new StringBuilder("OMEGA DEEP THINK\n\nSynthesis: ");b.append(synthesis(ev,q)).append("\n\nEvidence:\n");for(Evidence e:ev)b.append("[").append(e.id).append("] ").append(e.label).append(" · ").append(trim(e.text,220)).append("\n");
        boolean sourcePresent=false;for(Evidence e:ev)if(e.kind.equals("SOURCE")){sourcePresent=true;break;}
        b.append("\nConfidence: ").append(confidence(ev)).append("/10. ").append(sourcePresent?"Imported source evidence is present.":"This is currently dominated by personal-memory evidence rather than imported source material.");
        b.append("\n\nCounter-check: ").append(counterCheck(ev));return b.toString();
    }

    private String research(String q){
        List<Evidence> all=retrieve(q,16);ArrayList<Evidence> ev=new ArrayList<>();for(Evidence e:all)if(e.kind.equals("SOURCE"))ev.add(e);if(ev.isEmpty())return "RESEARCH MODE\nNo matching imported document passages. Feed Source Cortex relevant files first.";
        StringBuilder b=new StringBuilder("RESEARCH BRIEF\nQuestion: ").append(q).append("\n\nWhat the imported sources contain:\n");for(Evidence e:ev)b.append("[").append(e.id).append("] ").append(e.label).append("\n").append(trim(e.text,360)).append("\n\n");b.append("Cross-source theme: ").append(synthesis(ev,q)).append("\n\nCaution: this local pass retrieves and compresses text. It does not automatically prove the claims inside the documents.");return b.toString();
    }

    private String critic(String q){
        List<Evidence> ev=retrieve(q,14);if(ev.isEmpty())return "CRITIC MODE\nThere is not enough evidence to attack this idea intelligently yet.";
        StringBuilder b=new StringBuilder("ADVERSARIAL REVIEW\n");int weak=0;for(Evidence e:ev){if(e.confidence<=5||containsUncertain(e.text)){b.append("• Weak/uncertain trace [").append(e.id).append("]: ").append(trim(e.text,180)).append("\n");weak++;if(weak>=4)break;}}
        if(weak==0)b.append("• The retrieved traces are mostly high-confidence, so the bigger risk is shared-source bias rather than explicit uncertainty.\n");b.append("• Look for evidence that predicts the opposite outcome.\n• Separate direct observation from interpretation.\n• Check whether repeated memories are independent evidence or the same claim rehearsed many times.\n• Best falsification question: what result would make this conclusion wrong?");return b.toString();
    }

    private String creative(String q){
        List<Evidence> ev=retrieve(q,16);if(ev.size()<2)return "CREATIVE MODE\nGive me more memory or sources so I have distant pieces to recombine.";Evidence a=ev.get(0),b=ev.get(ev.size()-1);for(Evidence x:ev)if(shared(a.text,x.text)==0){b=x;break;}
        String idea="Take the mechanism or principle in ["+a.id+"] and transplant it into the problem represented by ["+b.id+"]. Then deliberately change one constraint and ask what new behavior appears.";
        return "CREATIVE SYNTHESIS\n["+a.id+"] "+trim(a.text,220)+"\n\n["+b.id+"] "+trim(b.text,220)+"\n\nNovel bridge:\n"+idea+"\n\nExperiment: write one concrete version of that hybrid, then store the result as IDEA so EchoCore can test it against later evidence.";
    }

    private String planner(String q){StringBuilder b=new StringBuilder("EXECUTIVE PLAN\nGoal: ").append(q).append("\n\n");int i=1;for(String s:planSteps(q))b.append(i++).append(". ").append(s).append("\n");b.append("\nWorking-memory rule: keep only the current bottleneck plus the next few actions active. The rest belongs in long-term memory.");return b.toString().trim();}

    private String teacher(String q){
        List<Evidence> ev=retrieve(q,12);if(ev.isEmpty())return "TEACHER MODE\nI need source material or memories about that topic first.";String theme=synthesis(ev,q);StringBuilder b=new StringBuilder("TEACHER MODE\nCore explanation: ").append(theme).append("\n\nTeach-back prompts:\n1. Explain the central mechanism in your own words.\n2. Give one example and one counterexample.\n3. Which evidence item would you check first if challenged?\n4. What prediction follows if your explanation is correct?\n\nEvidence anchors: ");for(int i=0;i<Math.min(5,ev.size());i++){if(i>0)b.append(", ");b.append("[").append(ev.get(i).id).append("]");}return b.toString();
    }

    private String synthesis(List<Evidence> ev,String q){Map<String,Integer> f=new HashMap<>();for(Evidence e:ev)for(String w:significant(e.text+" "+q))f.put(w,f.getOrDefault(w,0)+Math.max(1,e.confidence/3));List<String> t=top(f,7);String lead=ev.isEmpty()?"":trim(bestSentence(ev.get(0).text,q),240);return (lead.isEmpty()?"The strongest retrieved cluster":lead)+" The recurring concepts across retrieved evidence are "+(t.isEmpty()?"diffuse":String.join(", ",t))+".";}
    private String counterCheck(List<Evidence> ev){int neg=0,unc=0;for(Evidence e:ev){if(containsNegation(e.text))neg++;if(containsUncertain(e.text)||e.confidence<=5)unc++;}if(neg>0||unc>0)return "Retrieved evidence includes "+neg+" negatively framed/opposing traces and "+unc+" uncertain or lower-confidence traces. Inspect those before hardening the conclusion.";return "No obvious opposing wording surfaced in this retrieval. That is not proof of agreement; it may simply mean counterevidence has not been stored.";}
    private int confidence(List<Evidence> ev){if(ev.isEmpty())return 1;double x=0;int sourcesN=0;for(Evidence e:ev){x+=e.confidence;if(e.kind.equals("SOURCE"))sourcesN++;}double base=x/ev.size();if(sourcesN>=2)base+=.7;return Math.max(1,Math.min(10,(int)Math.round(base)));}

    private static String bestSentence(String text,String q){String[] ss=text.replace('\n',' ').split("(?<=[.!?])\\s+");Set<String> query=new HashSet<>(significant(q));int best=-1,score=-1;for(int i=0;i<ss.length;i++){int s=0;for(String w:significant(ss[i]))if(query.contains(w))s++;if(s>score){score=s;best=i;}}return best>=0?ss[best]:trim(text,240);}
    private static int shared(String a,String b){Set<String>x=new HashSet<>(significant(a));Set<String>y=new HashSet<>(significant(b));x.retainAll(y);return x.size();}
    private static boolean containsNegation(String s){String q=" "+s.toLowerCase(Locale.US)+" ";return q.contains(" not ")||q.contains(" never ")||q.contains(" false ")||q.contains(" wrong ")||q.contains(" opposite ")||q.contains(" failed ");}
    private static boolean containsUncertain(String s){String q=s.toLowerCase(Locale.US);return q.contains("maybe")||q.contains("might")||q.contains("possibly")||q.contains("uncertain")||q.contains("hypothesis")||q.contains("could be");}
    private static Map<String,Integer> frequency(List<MemoryNode> list){Map<String,Integer> f=new HashMap<>();for(MemoryNode m:list)for(String w:significant(m.text+" "+m.tags))f.put(w,f.getOrDefault(w,0)+1);return f;}
    private static List<String> top(Map<String,Integer> f,int n){ArrayList<Map.Entry<String,Integer>> e=new ArrayList<>(f.entrySet());e.sort((a,b)->b.getValue()-a.getValue());ArrayList<String>x=new ArrayList<>();for(int i=0;i<Math.min(n,e.size());i++)x.add(e.get(i).getKey());return x;}
    private static List<String> significant(String s){ArrayList<String>x=new ArrayList<>();String stop=" the a an and or but to of in on for with from into this that these those your you my me are is was were be been being about what when where why how can could would should have has had not its it's their there then than also very more most just ";for(String w:s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]"," ").split("\\s+")){if(w.length()>3&&!stop.contains(" "+w+" ")&&!x.contains(w))x.add(w);}return x;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static String trim(String s,int n){if(s==null)return "";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
}
