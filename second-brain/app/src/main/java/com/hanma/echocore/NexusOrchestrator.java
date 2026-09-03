package com.hanma.echocore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NexusOrchestrator extends CognitiveOrchestrator {
    private final BrainDatabase brain;
    private final SourceCatalog sources;

    public NexusOrchestrator(BrainDatabase brain, BrainEngine engine, SourceCatalog sources, CognitiveStore store) {
        super(brain, engine, sources, store);
        this.brain = brain;
        this.sources = sources;
    }

    @Override public List<Evidence> retrieve(String q, int limit) {
        int cap = Math.max(4, Math.min(24, limit));
        String query = q == null ? "" : q.trim();
        boolean selfQuery = isSelfQuery(query);
        ArrayList<Evidence> sourcePool = new ArrayList<>();
        ArrayList<Evidence> memoryPool = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        ArrayList<String[]> sourceHits = new ArrayList<>(sources.searchChunks(query, 16));
        if (sourceHits.size() < 8) {
            for (String token : significant(query)) {
                if (sourceHits.size() >= 20) break;
                for (String[] h : sources.searchChunks(token, 5)) {
                    String key = normalize(h[0] + " " + h[1] + " " + h[2]);
                    if (seen.add("s:" + key)) sourceHits.add(h);
                }
            }
        }
        int si = 1;
        for (String[] h : sourceHits) {
            String text = h.length > 2 ? h[2] : "";
            String key = normalize(text);
            if (key.isEmpty() || !seen.add("sourceText:" + key)) continue;
            sourcePool.add(new Evidence("S" + si++, h[0] + " · part " + h[1], trim(text, 1200), "SOURCE", 9));
        }

        List<MemoryNode> memories = brain.search(query, 24);
        memories.sort(Comparator.comparingInt((MemoryNode n) -> memoryScore(n, selfQuery)).reversed());
        int mi = 1;
        for (MemoryNode n : memories) {
            String key = normalize(n.text);
            if (key.isEmpty() || !seen.add("memoryText:" + key)) continue;
            int confidence = calibratedConfidence(n, selfQuery);
            String label = n.type + labelSuffix(n.type);
            memoryPool.add(new Evidence("M" + mi++, label, trim(n.text, 1000), "MEMORY", confidence));
        }

        ArrayList<Evidence> out = new ArrayList<>();
        int sourceReserve = sourcePool.isEmpty() ? 0 : Math.min(sourcePool.size(), Math.max(2, cap / 2));
        for (int i = 0; i < sourceReserve && out.size() < cap; i++) out.add(sourcePool.get(i));
        for (Evidence e : memoryPool) if (out.size() < cap) out.add(e);
        for (int i = sourceReserve; i < sourcePool.size() && out.size() < cap; i++) out.add(sourcePool.get(i));
        return out;
    }

    @Override public String buildGroundedContext(String q, int limit) {
        List<Evidence> ev = retrieve(q, limit);
        StringBuilder b = new StringBuilder();
        for (Evidence e : ev) {
            b.append('[').append(e.id).append("] ").append(e.kind).append(" · ").append(e.label)
                    .append(" · calibrated confidence ").append(e.confidence).append("/10\n")
                    .append(e.text).append("\n\n");
        }
        return b.toString().trim();
    }

    @Override public String localAnswer(String question, String mode) {
        String q = question == null ? "" : question.trim();
        String m = mode == null ? "DEEP" : mode.toUpperCase(Locale.US);
        if (q.isEmpty()) return "Give Nexus a question, problem, decision, goal, or idea.";
        if ("PLANNER".equals(m) || "CREATIVE".equals(m)) return super.localAnswer(q, m);
        List<Evidence> ev = retrieve(q, "RESEARCH".equals(m) ? 18 : 14);
        if (ev.isEmpty()) return "NEXUS · " + m + "\nI do not have enough stored evidence yet. Add an observation, memory, or source instead of forcing a conclusion.";
        if ("RESEARCH".equals(m)) return research(q, ev);
        if ("CRITIC".equals(m)) return critic(q, ev);
        if ("TEACHER".equals(m)) return teacher(q, ev);
        return deep(q, ev);
    }

    @Override public String knowledgeGaps() {
        String base = super.knowledgeGaps();
        int sourcesN = sources.countSources();
        int refs = brain.countType("REFERENCE") + brain.countType("KNOWLEDGE") + brain.countType("OBSERVATION");
        int self = brain.countType("SELF") + brain.countType("BELIEF");
        StringBuilder b = new StringBuilder("NEXUS CALIBRATION SCAN\n");
        if (sourcesN == 0) b.append("• Evidence floor: there are no imported full sources yet.\n");
        if (self > refs) b.append("• Self/belief traces outnumber references + observations. Treat identity claims as context, not proof.\n");
        b.append("• Evidence balance: ").append(sourcesN).append(" sources, ").append(refs).append(" reference/knowledge/observation traces, ").append(self).append(" self/belief traces.\n\n");
        b.append(base);
        return b.toString();
    }

    private String deep(String q, List<Evidence> ev) {
        int sourceCount = 0, personalCount = 0, uncertain = 0;
        for (Evidence e : ev) {
            if ("SOURCE".equals(e.kind)) sourceCount++;
            if (e.label.startsWith("SELF") || e.label.startsWith("BELIEF") || e.label.startsWith("IDEA")) personalCount++;
            if (e.confidence <= 5) uncertain++;
        }
        Evidence lead = bestLead(ev);
        StringBuilder b = new StringBuilder("NEXUS DEEP THINK\n\n");
        b.append("Grounded center: ").append(trim(bestSentence(lead.text, q), 320)).append(" [").append(lead.id).append("]\n\n");
        b.append("Evidence map:\n");
        for (int i = 0; i < Math.min(9, ev.size()); i++) {
            Evidence e = ev.get(i);
            b.append('[').append(e.id).append("] ").append(e.label).append(" · ").append(e.confidence).append("/10 · ").append(trim(e.text, 220)).append('\n');
        }
        b.append("\nCalibration: ").append(sourceCount).append(" imported-source hits, ").append(personalCount).append(" personal/self traces, ").append(uncertain).append(" lower-confidence traces. ");
        if (sourceCount == 0) b.append("This answer is not externally grounded yet.");
        else b.append("Imported evidence is present, but source text is evidence of what the source says, not automatic proof that the source is correct.");
        b.append("\n\nNext test: find the strongest observation or source that would change the current conclusion, then update confidence instead of adding another repetition.");
        return b.toString();
    }

    private String research(String q, List<Evidence> ev) {
        ArrayList<Evidence> grounded = new ArrayList<>();
        for (Evidence e : ev) if ("SOURCE".equals(e.kind) || e.label.startsWith("REFERENCE") || e.label.startsWith("KNOWLEDGE") || e.label.startsWith("OBSERVATION")) grounded.add(e);
        if (grounded.isEmpty()) return "NEXUS RESEARCH\nNo matching source/reference/observation evidence. Personal beliefs were deliberately excluded from the research answer.";
        StringBuilder b = new StringBuilder("NEXUS RESEARCH\nQuestion: ").append(q).append("\n\nEvidence-first retrieval:\n");
        for (int i = 0; i < Math.min(12, grounded.size()); i++) {
            Evidence e = grounded.get(i);b.append('[').append(e.id).append("] ").append(e.label).append(" · ").append(trim(e.text, 340)).append("\n\n");
        }
        b.append("Rule: SELF, BELIEF, and IDEA nodes do not count as independent research evidence unless separately supported.");
        return b.toString();
    }

    private String critic(String q, List<Evidence> ev) {
        StringBuilder b = new StringBuilder("NEXUS ADVERSARIAL REVIEW\n");
        int shown = 0;
        for (Evidence e : ev) {
            boolean personal = e.label.startsWith("SELF") || e.label.startsWith("BELIEF") || e.label.startsWith("IDEA");
            if (personal || e.confidence <= 5 || containsUncertainty(e.text) || containsOpposition(e.text)) {
                b.append("• [").append(e.id).append("] ").append(e.label).append(": ").append(trim(e.text, 240)).append('\n');shown++;
                if (shown >= 7) break;
            }
        }
        if (shown == 0) b.append("• No obvious weak trace surfaced. The main remaining risk is missing counterevidence or shared-source bias.\n");
        b.append("• Ask whether repeated memories are independent observations or one claim rehearsed many times.\n");
        b.append("• Best falsifier: what concrete result would make the current answer wrong?");
        return b.toString();
    }

    private String teacher(String q, List<Evidence> ev) {
        Evidence lead = bestLead(ev);
        StringBuilder b = new StringBuilder("NEXUS TEACHER\nCore anchor: ").append(trim(bestSentence(lead.text, q), 320)).append(" [").append(lead.id).append("]\n\n");
        b.append("Teach-back:\n1. Explain the mechanism without copying the memory.\n2. Give one example and one counterexample.\n3. Name which evidence is imported source, direct observation, and personal belief.\n4. State one prediction that could be checked.\n\nAnchors: ");
        for (int i = 0; i < Math.min(6, ev.size()); i++) { if (i > 0) b.append(", "); b.append('[').append(ev.get(i).id).append(']'); }
        return b.toString();
    }

    private static Evidence bestLead(List<Evidence> ev) {
        for (Evidence e : ev) if ("SOURCE".equals(e.kind)) return e;
        for (Evidence e : ev) if (e.label.startsWith("OBSERVATION") || e.label.startsWith("REFERENCE") || e.label.startsWith("KNOWLEDGE")) return e;
        return ev.get(0);
    }

    private static int memoryScore(MemoryNode n, boolean selfQuery) {
        int base = n.importance * 5 + n.confidence * 3 + Math.min(15, n.accessCount);
        String t = n.type == null ? "" : n.type.toUpperCase(Locale.US);
        if ("OBSERVATION".equals(t)) base += 50;
        else if ("REFERENCE".equals(t) || "KNOWLEDGE".equals(t)) base += 45;
        else if ("PROCEDURE".equals(t) || "SKILL".equals(t)) base += 25;
        else if ("SELF".equals(t) || "BELIEF".equals(t)) base += selfQuery ? 10 : -20;
        else if ("IDEA".equals(t) || "DREAM".equals(t)) base -= 25;
        else if ("QUESTION".equals(t)) base -= 35;
        if (n.pinned) base += 8;if (n.active) base += 8;return base;
    }

    private static int calibratedConfidence(MemoryNode n, boolean selfQuery) {
        int c = Math.max(1, Math.min(10, n.confidence));
        String t = n.type == null ? "" : n.type.toUpperCase(Locale.US);
        if (("SELF".equals(t) || "BELIEF".equals(t)) && !selfQuery) c = Math.min(c, 5);
        if ("IDEA".equals(t) || "DREAM".equals(t)) c = Math.min(c, 4);
        if ("QUESTION".equals(t)) c = Math.min(c, 3);
        return c;
    }

    private static String labelSuffix(String type) {
        String t = type == null ? "" : type.toUpperCase(Locale.US);
        if ("SELF".equals(t) || "BELIEF".equals(t)) return " · personal trace";
        if ("IDEA".equals(t) || "DREAM".equals(t)) return " · speculative trace";
        if ("OBSERVATION".equals(t)) return " · direct trace";
        return "";
    }

    private static boolean isSelfQuery(String q) {
        String s = " " + (q == null ? "" : q.toLowerCase(Locale.US)) + " ";
        return s.contains(" you ") || s.contains(" your ") || s.contains(" echocore ") || s.contains(" nexus ") || s.contains(" self ") || s.contains(" memory ") || s.contains(" brain ");
    }

    private static List<String> significant(String s) {
        ArrayList<String> out = new ArrayList<>();
        String stop = " the a an and or but to of in on for with from into this that these those your you my me are is was were be been being about what when where why how can could would should have has had not its their there then than also very more most just ";
        for (String w : (s == null ? "" : s.toLowerCase(Locale.US)).replaceAll("[^a-z0-9]", " ").split("\\s+")) {
            if (w.length() > 3 && !stop.contains(" " + w + " ") && !out.contains(w)) out.add(w);
        }
        return out;
    }

    private static String bestSentence(String text, String q) {
        String[] ss = (text == null ? "" : text.replace('\n', ' ')).split("(?<=[.!?])\\s+");
        Set<String> query = new HashSet<>(significant(q));int best = 0, score = -1;
        for (int i = 0; i < ss.length; i++) {int s = 0;for (String w : significant(ss[i])) if (query.contains(w)) s++;if (s > score) {score = s;best = i;}}
        return ss.length == 0 ? "" : ss[Math.min(best, ss.length - 1)];
    }

    private static boolean containsUncertainty(String s) {String q = (s == null ? "" : s.toLowerCase(Locale.US));return q.contains("maybe") || q.contains("might") || q.contains("possibly") || q.contains("uncertain") || q.contains("hypothesis") || q.contains("could be");}
    private static boolean containsOpposition(String s) {String q = " " + (s == null ? "" : s.toLowerCase(Locale.US)) + " ";return q.contains(" false ") || q.contains(" wrong ") || q.contains(" failed ") || q.contains(" opposite ") || q.contains(" contradict");}
    private static String normalize(String s) {return (s == null ? "" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ").trim());}
    private static String trim(String s, int n) {if (s == null) return "";s = s.trim();return s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)).trim() + "…";}
}
