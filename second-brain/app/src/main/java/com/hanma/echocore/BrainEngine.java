package com.hanma.echocore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BrainEngine {
    private final BrainDatabase db;

    public BrainEngine(BrainDatabase db) { this.db = db; }

    public String answer(String input) {
        String original = input == null ? "" : input.trim();
        String q = original.toLowerCase(Locale.US);
        if (q.isEmpty()) return "Give me a thought, question, memory, goal, decision, or hunch.";

        if (q.startsWith("remember ")) return remember(original.substring(Math.min(9, original.length())).trim(), "THOUGHT");
        if (q.startsWith("goal ")) return remember(original.substring(Math.min(5, original.length())).trim(), "GOAL");
        if (q.startsWith("belief ")) return remember(original.substring(Math.min(7, original.length())).trim(), "BELIEF");
        if (q.startsWith("skill ")) return remember(original.substring(Math.min(6, original.length())).trim(), "SKILL");
        if (q.startsWith("question ")) return remember(original.substring(Math.min(9, original.length())).trim(), "QUESTION");

        if (containsAny(q,"summarize today","summary of today") || q.equals("today")) return summarizeToday();
        if (containsAny(q,"what should i focus","focus stack","working memory") || q.equals("focus")) return focusReport();
        if (q.startsWith("connect ") && q.contains(" and ")) return connectCommand(original,q);
        if (containsAny(q,"who am i","self model","what do you know about me")) return selfModel();
        if (containsAny(q,"what am i missing","blind spot","blind spots","metacognition")) return blindSpots();
        if (containsAny(q,"contradiction","conflict in my thoughts","belief conflict")) return contradictions();
        if (q.startsWith("predict ") || q.equals("predict") || q.contains("what happens next")) return predict(original.replaceFirst("(?i)^predict\\s*", "").trim());
        if (q.startsWith("imagine ") || q.startsWith("dream ") || q.equals("dream")) return imagine(original.replaceFirst("(?i)^(imagine|dream)\\s*", "").trim());
        if (containsAny(q,"wander","mind wander","background thought","default mode")) return wander();
        if (containsAny(q,"intuition","gut feeling","gut signal","hunch")) return intuition(original);
        if (containsAny(q,"sleep consolidate","consolidate","memory consolidation")) return consolidate();
        if (containsAny(q,"brain status","mind status","brain snapshot")) return brainSnapshot();
        if (q.startsWith("decide ") || q.startsWith("compare ")) return decisionSupport(original.replaceFirst("(?i)^(decide|compare)\\s*", "").trim());
        if (containsAny(q,"recall","surprise me","random memory")) return recallSpark();

        String cleaned = q.replace("what did i say about","").replace("what do i know about","")
                .replace("find memories about","").replace("find memory about","").replace("recall","").trim();
        List<MemoryNode> hits = db.search(cleaned.isEmpty() ? original : cleaned, 8);
        if (hits.isEmpty()) return "I don’t have a strong memory match yet. Say “remember …”, “goal …”, “belief …”, or capture it on Cortex and it becomes part of the network.";
        return recallAnswer(hits);
    }

    private String remember(String body, String type) {
        if (body.isEmpty()) return "Give me something to store after the command.";
        int valence = inferValence(body);
        int novelty = inferNovelty(body);
        int importance = inferImportance(body);
        long id = db.addMemoryRich(body,type,autoTags(body),importance,valence,7,novelty,"GOAL".equals(type));
        return id > 0 ? "Stored as " + type + ". I tagged it " + autoTags(body) + ", estimated salience " + importance + "/10, and added it to associative memory." : "That memory did not save.";
    }

    private String summarizeToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        List<MemoryNode> items = db.since(cal.getTimeInMillis(),60);
        if (items.isEmpty()) return "Today is still an empty page in EchoCore.";
        return summarize(items,"Today");
    }

    public String summarize(List<MemoryNode> items, String label) {
        Map<String,Integer> freq = weightedFrequency(items);
        List<Map.Entry<String,Integer>> entries = sortedEntries(freq);
        StringBuilder concepts = new StringBuilder();
        for (int i=0;i<Math.min(7,entries.size());i++) { if (i>0) concepts.append(", "); concepts.append(entries.get(i).getKey()); }
        StringBuilder b = new StringBuilder(label).append(" compressed into ").append(items.size()).append(" memory nodes.");
        if (concepts.length()>0) b.append("\nDominant concepts: ").append(concepts).append(".");
        int mood = averageValence(items);
        b.append("\nEmotional contour: ").append(moodName(mood)).append(".");
        b.append("\n\nHigh-signal memories:\n");
        items.stream().sorted((x,y)->Double.compare(y.activationScore(System.currentTimeMillis()),x.activationScore(System.currentTimeMillis())))
                .limit(5).forEach(m->b.append("• ").append(trim(m.text,150)).append("\n"));
        return b.toString().trim();
    }

    private String focusReport() {
        List<MemoryNode> focus = db.activeWorkspace(7);
        if (focus.isEmpty()) focus = db.byType("FOCUS",7);
        if (focus.isEmpty()) focus = db.byType("GOAL",7);
        if (focus.isEmpty()) return "Working memory is empty. Activate a memory or save a GOAL/FOCUS node.";
        StringBuilder b = new StringBuilder("Working memory · ").append(focus.size()).append("/7 slots\n");
        int i=1; for (MemoryNode m:focus) b.append(i++).append(". ").append(trim(m.text,125)).append(" · signal ").append((int)Math.round(m.activationScore(System.currentTimeMillis()))).append("\n");
        b.append("\nThe 7-slot cap is deliberate: EchoCore keeps a small active workspace and lets the larger vault stay long-term.");
        return b.toString().trim();
    }

    private String connectCommand(String original, String q) {
        int at = q.indexOf(" and ");
        String a = original.substring(8,at).trim();
        String b = original.substring(at+5).trim();
        return connect(a,b);
    }

    private String connect(String a, String b) {
        List<MemoryNode> left = db.search(a,5), right = db.search(b,5);
        if (left.isEmpty() || right.isEmpty()) return "I need memory on both sides of that bridge. I found " + left.size() + " for “" + a + "” and " + right.size() + " for “" + b + "”.";
        Set<String> overlap = significantWords(join(left)); overlap.retainAll(significantWords(join(right)));
        db.reinforceAssociation(left.get(0).id,right.get(0).id,"EXPLICIT BRIDGE",3);
        String bridge = overlap.isEmpty() ? "The vocabulary overlap is weak, so this is a cross-domain connection rather than an obvious one." : "Shared bridge terms: " + String.join(", ", overlap) + ".";
        return "Connection map: “"+a+"” ↔ “"+b+"”\n\n"+bridge+"\n\nA-side: "+trim(left.get(0).text,150)+"\nB-side: "+trim(right.get(0).text,150)+"\n\nI strengthened this synapse because you deliberately connected them.";
    }

    private String selfModel() {
        List<MemoryNode> self = new ArrayList<>();
        self.addAll(db.byType("SELF",20)); self.addAll(db.byType("BELIEF",20)); self.addAll(db.byType("VALUE",20)); self.addAll(db.byType("GOAL",12));
        if (self.isEmpty()) {
            List<MemoryNode> all = db.strongest(20);
            if (all.isEmpty()) return "The self-model is still blank. Save SELF, VALUE, BELIEF, and GOAL memories and it will become a structured identity map.";
            self.addAll(all);
        }
        Map<String,Integer> f = weightedFrequency(self); List<Map.Entry<String,Integer>> e = sortedEntries(f);
        ArrayList<String> top = new ArrayList<>(); for(int i=0;i<Math.min(7,e.size());i++) top.add(e.get(i).getKey());
        StringBuilder b = new StringBuilder("Current self-model\nCore recurring themes: ").append(String.join(", ",top)).append(".\n");
        List<MemoryNode> goals = db.byType("GOAL",4); if(!goals.isEmpty()) { b.append("\nActive direction:\n"); for(MemoryNode m:goals)b.append("• ").append(trim(m.text,130)).append("\n"); }
        b.append("\nThis model is revision-friendly: new evidence can strengthen, weaken, or contradict old beliefs instead of freezing identity forever.");
        return b.toString().trim();
    }

    private String blindSpots() {
        List<MemoryNode> strong = db.strongest(30);
        if (strong.size()<4) return "I need a larger sample before blind spots become meaningful. Right now the bigger blind spot is simply sparse data.";
        Map<String,Integer> f = weightedFrequency(strong); List<Map.Entry<String,Integer>> e = sortedEntries(f);
        int questions = db.countType("QUESTION"), beliefs = db.countType("BELIEF"), refs = db.countType("REFERENCE");
        StringBuilder b = new StringBuilder("Metacognitive scan\n");
        if (beliefs > questions*2+2) b.append("• You have many more beliefs than explicit questions. The brain may be closing loops faster than it opens them.\n");
        if (refs == 0 && beliefs > 2) b.append("• Beliefs exist with almost no REFERENCE nodes attached. That makes evidence provenance thin.\n");
        if (!e.isEmpty()) b.append("• Attention is heavily concentrated around ").append(joinTop(e,4)).append(". Ask what important domain is absent from that cluster.\n");
        List<MemoryNode> active = db.activeWorkspace(20); if(active.size()>7) b.append("• Working memory is overloaded: ").append(active.size()).append(" active nodes. Keep seven or fewer.\n");
        b.append("• Counter-question: what observation would make one of your strongest current beliefs change?");
        return b.toString().trim();
    }

    private String contradictions() {
        List<MemoryNode> list = db.recent(100);
        ArrayList<String> found = new ArrayList<>();
        for(int i=0;i<list.size();i++) for(int j=i+1;j<Math.min(list.size(),i+30);j++) {
            MemoryNode a=list.get(i), b=list.get(j); int overlap=sharedWordScore(a,b);
            if(overlap<2) continue;
            boolean polarity = (containsNegation(a.text) != containsNegation(b.text)) || (a.valence*b.valence < -4);
            if(polarity) { found.add("• “"+trim(a.text,90)+"” ↔ “"+trim(b.text,90)+"”"); db.reinforceAssociation(a.id,b.id,"TENSION",2); if(found.size()>=5) break; }
            if(found.size()>=5) break;
        }
        if(found.isEmpty()) return "I found no strong contradiction candidates. That means either your current nodes are coherent, or the opposing evidence has not been stored yet.";
        return "Possible cognitive tensions, not automatic verdicts:\n"+String.join("\n",found)+"\n\nThese pairs deserve inspection because they share subject matter but carry opposing wording or affect.";
    }

    private String predict(String topic) {
        List<MemoryNode> hits = topic.isEmpty() ? db.strongest(12) : db.search(topic,12);
        if(hits.isEmpty()) return "Prediction needs memory traces to extrapolate from.";
        Map<String,Integer> f = weightedFrequency(hits); List<Map.Entry<String,Integer>> e = sortedEntries(f);
        double confidence = 0; for(MemoryNode m:hits) confidence += m.confidence; confidence = Math.min(9.5, Math.max(2.0, confidence/hits.size()));
        String trend = joinTop(e,5);
        return "Prediction engine\nLikely continuation: the next related thoughts/actions will keep clustering around " + trend + ".\nConfidence from stored memory: " + String.format(Locale.US,"%.1f/10",confidence) + ".\n\nBasis: recent activation, repetition, importance, confidence, and active-workspace pressure. This is a forecast from your stored patterns, not a claim of certainty.";
    }

    private String imagine(String seed) {
        List<MemoryNode> pool = seed.isEmpty() ? db.strongest(30) : db.search(seed,20);
        if(pool.size()<2) pool = db.recent(30);
        if(pool.size()<2) return "Imagination needs at least two memory fragments to recombine.";
        MemoryNode a=pool.get(0), b=pool.get(pool.size()-1);
        for(MemoryNode x:pool) if(sharedWordScore(a,x)==0) { b=x; break; }
        db.reinforceAssociation(a.id,b.id,"IMAGINED",1);
        return "Imagination synthesis\nFragment A: "+trim(a.text,150)+"\nFragment B: "+trim(b.text,150)+"\n\nNovel fusion: What changes if the mechanism, strategy, feeling, or rule in A is transplanted into B? Try building one concrete experiment or idea from that collision.";
    }

    private String wander() {
        List<MemoryNode> pool=db.recent(60); if(pool.size()<2)return recallSpark();
        int i=(int)(Math.abs(System.nanoTime())%pool.size()); MemoryNode a=pool.get(i), b=pool.get((i+Math.max(1,pool.size()/2))%pool.size());
        for(int k=0;k<pool.size();k++){ MemoryNode candidate=pool.get((i+k)%pool.size()); if(sharedWordScore(a,candidate)==0){b=candidate;break;} }
        db.reinforceAssociation(a.id,b.id,"MIND WANDER",1);
        return "Background thought surfaced:\n“"+trim(a.text,145)+"”\nmet\n“"+trim(b.text,145)+"”\n\nAssociation prompt: if these belong to the same hidden pattern, what is the missing third idea between them?";
    }

    private String intuition(String original) {
        String topic = original.toLowerCase(Locale.US).replace("intuition","").replace("gut feeling","").replace("gut signal","").replace("hunch","").trim();
        List<MemoryNode> hits=topic.isEmpty()?db.strongest(15):db.search(topic,15); if(hits.isEmpty())return "No gut signal yet. Intuition in EchoCore is compressed pattern memory, so it needs traces.";
        double val=0, conf=0, weight=0; for(MemoryNode m:hits){double w=Math.max(1,m.importance);val+=m.valence*w;conf+=m.confidence*w;weight+=w;}
        double v=weight==0?0:val/weight, c=weight==0?0:conf/weight;
        String direction=v>1?"positive / approach":v<-1?"negative / caution":"mixed / unresolved";
        return "Gut signal: "+direction+".\nPattern valence: "+String.format(Locale.US,"%.1f",v)+" on a -5..+5 scale. Memory confidence: "+String.format(Locale.US,"%.1f/10",c)+".\n\nThe strongest trace is: “"+trim(hits.get(0).text,170)+"”\n\nThis is EchoCore compressing your own accumulated signals into a fast read.";
    }

    public String consolidate() {
        List<MemoryNode> items=db.recent(80); if(items.size()<3)return "Consolidation needs at least three memories.";
        int links=0;
        for(int i=0;i<Math.min(items.size(),35);i++) for(int j=i+1;j<Math.min(items.size(),35);j++) {
            int s=sharedWordScore(items.get(i),items.get(j)); if(s>0){db.reinforceAssociation(items.get(i).id,items.get(j).id,"CONSOLIDATED",Math.min(3,s));links++;}
        }
        Map<String,Integer> f=weightedFrequency(items); List<Map.Entry<String,Integer>> e=sortedEntries(f);
        String theme=joinTop(e,6);
        String insight="Consolidated pattern: recent memory repeatedly converges on "+theme+".";
        if(db.search(insight,2).isEmpty()) db.addMemoryRich(insight,"INSIGHT",autoTags(insight),7,1,6,7,false);
        return "Sleep-style consolidation complete.\n• Rehearsed "+items.size()+" recent nodes\n• Strengthened "+links+" associative paths\n• Dominant semantic cluster: "+theme+"\n• Created/checked a compressed INSIGHT node\n\nNothing was deleted. Weak traces simply compete less strongly for attention until reactivated.";
    }

    public String brainSnapshot() {
        List<MemoryNode> strong=db.strongest(8); int active=db.activeWorkspace(20).size();
        int mood=db.getStateInt("mood",0), energy=db.getStateInt("energy",6), curiosity=db.getStateInt("curiosity",7), load=db.getStateInt("mental_load",4);
        return "EchoCore brain snapshot\nLong-term memories: "+db.count()+"\nSynapses: "+db.countAssociations()+"\nWorking memory: "+active+"/7\nMood signal: "+moodName(mood)+" ("+mood+")\nEnergy: "+energy+"/10 · Curiosity: "+curiosity+"/10 · Mental load: "+load+"/10\nStrongest active trace: "+(strong.isEmpty()?"none yet":trim(strong.get(0).text,140));
    }

    private String decisionSupport(String body) {
        String lower=body.toLowerCase(Locale.US); int pos=lower.indexOf(" vs "); if(pos<0)pos=lower.indexOf(" or ");
        if(pos<0)return "Use: decide option A vs option B. I’ll compare the memory evidence on both sides.";
        String a=body.substring(0,pos).trim(), b=body.substring(pos+4).trim();
        List<MemoryNode> left=db.search(a,10),right=db.search(b,10); if(left.isEmpty()&&right.isEmpty())return "Neither option has enough stored memory evidence yet.";
        double sa=decisionScore(left),sb=decisionScore(right); String lean=Math.abs(sa-sb)<1.2?"close / unresolved":sa>sb?a:b;
        return "Decision workspace\n"+a+": "+String.format(Locale.US,"%.1f",sa)+" signal from "+left.size()+" memories\n"+b+": "+String.format(Locale.US,"%.1f",sb)+" signal from "+right.size()+" memories\n\nCurrent lean: "+lean+".\nI weighted salience, confidence, emotional valence, and rehearsal. Add explicit PRO/CON memories if you want a sharper comparison.";
    }

    private double decisionScore(List<MemoryNode> list) {
        if(list.isEmpty())return 0; double x=0; for(MemoryNode m:list)x+=(m.importance*.7+m.confidence*.5+m.valence*.6+Math.log1p(m.accessCount)); return x/list.size();
    }

    private String recallSpark() {
        List<MemoryNode> r=db.strongest(30); if(r.isEmpty())return "There’s nothing in the vault yet.";
        int index=(int)(Math.abs(System.nanoTime())%r.size()); MemoryNode m=r.get(index); db.touch(m.id);
        return "Recall spark:\n“"+trim(m.text,270)+"”\n\nFiled as "+m.type+suffixTags(m.tags)+" · activation "+(int)Math.round(m.activationScore(System.currentTimeMillis()))+".";
    }

    private String recallAnswer(List<MemoryNode> hits) {
        StringBuilder out=new StringBuilder("I found ").append(hits.size()).append(hits.size()==1?" memory":" memories").append(" that overlap:\n\n");
        for(int i=0;i<Math.min(5,hits.size());i++){MemoryNode m=hits.get(i);out.append("• ").append(trim(m.text,185));if(!m.tags.isEmpty())out.append(" [").append(m.tags).append("]");out.append("\n");}
        out.append("\nPattern: ").append(patternSentence(hits));
        return out.toString();
    }

    private String patternSentence(List<MemoryNode> hits) {
        Map<String,Integer> freq=weightedFrequency(hits); List<Map.Entry<String,Integer>> e=sortedEntries(freq);
        return e.isEmpty()?"the strongest signal is in the individual wording.":"the cluster keeps circling "+joinTop(e,5)+".";
    }

    public String autoTags(String text) {
        Map<String,Integer> freq=new HashMap<>(); for(String w:words(text))if(!isStop(w)&&w.length()>3)freq.put(w,freq.getOrDefault(w,0)+1);
        List<Map.Entry<String,Integer>> e=sortedEntries(freq); ArrayList<String> tags=new ArrayList<>(); for(int i=0;i<Math.min(5,e.size());i++)tags.add(e.get(i).getKey()); return String.join(", ",tags);
    }

    public int inferValence(String text) {
        String q=" "+text.toLowerCase(Locale.US)+" "; int v=0;
        String[] pos={" love "," good "," great "," win "," better "," excited "," powerful "," success "," enjoy "," happy "," progress "," useful "};
        String[] neg={" hate "," bad "," worse "," fear "," angry "," failed "," failure "," pain "," sad "," problem "," stuck "," danger "};
        for(String s:pos)if(q.contains(s))v++; for(String s:neg)if(q.contains(s))v--; return Math.max(-5,Math.min(5,v));
    }

    public int inferNovelty(String text) { int n=4; if(text.length()>120)n+=2; if(text.contains("?")||text.toLowerCase(Locale.US).contains("idea"))n+=1; return Math.min(10,n); }
    public int inferImportance(String text) { String q=text.toLowerCase(Locale.US); int n=6; if(containsAny(q,"important","must","goal","remember","critical","priority"))n+=2; if(text.length()>180)n++; return Math.min(10,n); }

    public static int sharedWordScore(MemoryNode a, MemoryNode b) {
        Set<String>x=significantWords(a.text+" "+a.tags), y=significantWords(b.text+" "+b.tags); x.retainAll(y); return x.size();
    }

    private static Map<String,Integer> weightedFrequency(List<MemoryNode> items) {
        Map<String,Integer> f=new HashMap<>(); for(MemoryNode m:items)for(String w:words(m.text+" "+m.tags))if(!isStop(w)&&w.length()>3)f.put(w,f.getOrDefault(w,0)+Math.max(1,m.importance/3)); return f;
    }

    private static List<Map.Entry<String,Integer>> sortedEntries(Map<String,Integer> f) {
        ArrayList<Map.Entry<String,Integer>> e=new ArrayList<>(f.entrySet()); e.sort((a,b)->b.getValue()-a.getValue()); return e;
    }

    private static String joinTop(List<Map.Entry<String,Integer>> e,int n) { ArrayList<String>x=new ArrayList<>();for(int i=0;i<Math.min(n,e.size());i++)x.add(e.get(i).getKey());return x.isEmpty()?"no dominant theme":String.join(", ",x); }
    private static int averageValence(List<MemoryNode> list){if(list.isEmpty())return 0;int x=0;for(MemoryNode m:list)x+=m.valence;return Math.round((float)x/list.size());}
    private static String moodName(int v){return v>=3?"strongly positive":v>=1?"positive":v<=-3?"strongly negative":v<=-1?"negative":"neutral / mixed";}
    private static boolean containsNegation(String s){String q=" "+s.toLowerCase(Locale.US)+" ";return containsAny(q," not "," never "," wrong "," false "," don’t "," dont "," cannot "," can’t ");}
    private static boolean containsAny(String s,String... needles){for(String n:needles)if(s.contains(n))return true;return false;}
    private static String join(List<MemoryNode> list){StringBuilder b=new StringBuilder();for(MemoryNode m:list)b.append(' ').append(m.text).append(' ').append(m.tags);return b.toString();}
    private static Set<String> significantWords(String s){HashSet<String>set=new HashSet<>();for(String w:words(s))if(!isStop(w)&&w.length()>3)set.add(w);return set;}
    private static String[] words(String s){return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]"," ").split("\\s+");}
    private static boolean isStop(String w){String stops=" the a an and or but to of in on for with my i me is are was were be this that it you your about from into as at we our do did have has had what how why when where should would could can just very then than so if its it's them they their there here who which also more most much many one two ";return w.isEmpty()||stops.contains(" "+w+" ");}
    private static String suffixTags(String tags){return tags==null||tags.trim().isEmpty()?"":" · "+tags;}
    private static String trim(String s,int n){if(s==null)return "";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(0,n-1)).trim()+"…";}

    public static String formatDate(long time){return new SimpleDateFormat("MMM d · h:mm a",Locale.US).format(time);}
}
