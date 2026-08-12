package com.vaan.collectivemachine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Public/shareable memory graph. Private dialogue stays in LainMemory. */
public final class MemoryGraph {
    public static final class MemoryNode {
        public final String id;
        public final String source;
        public final String text;
        public final String kind;
        public final long timestamp;
        public final String originNode;

        MemoryNode(String id, String source, String text, String kind, long timestamp, String originNode) {
            this.id = id;
            this.source = source;
            this.text = text;
            this.kind = kind;
            this.timestamp = timestamp;
            this.originNode = originNode;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("source", source);
            o.put("text", text);
            o.put("kind", kind);
            o.put("timestamp", timestamp);
            o.put("originNode", originNode);
            return o;
        }

        static MemoryNode fromJson(JSONObject o) {
            return new MemoryNode(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("source", "Unknown"),
                    o.optString("text", ""),
                    o.optString("kind", "remembered"),
                    o.optLong("timestamp", System.currentTimeMillis()),
                    o.optString("originNode", "unknown-node")
            );
        }
    }

    private static final String PREF = "lain_public_memory_graph_v2";
    private static final String KEY = "memories";
    private static final int MAX = 1000;

    private final SharedPreferences prefs;
    private final IdentityGraph identity;
    private final List<MemoryNode> memories = new ArrayList<>();

    public MemoryGraph(Context context, IdentityGraph identity) {
        this.identity = identity;
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public synchronized int size() { return memories.size(); }
    public synchronized List<MemoryNode> snapshot() { return new ArrayList<>(memories); }

    public synchronized void addLocal(String source, String text, String kind) {
        add(source, text, kind, identity.nodeId());
    }

    public synchronized void add(String source, String text, String kind, String originNode) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) return;
        memories.add(new MemoryNode(UUID.randomUUID().toString(),
                source == null || source.trim().isEmpty() ? "Unknown" : source.trim(),
                clean,
                kind == null || kind.trim().isEmpty() ? "remembered" : kind.trim(),
                System.currentTimeMillis(),
                originNode == null || originNode.trim().isEmpty() ? "unknown-node" : originNode.trim()));
        trim();
        save();
    }

    public synchronized int remoteCount() {
        int n = 0;
        for (MemoryNode m : memories) if (!identity.nodeId().equals(m.originNode)) n++;
        return n;
    }

    public synchronized Set<String> nodes() {
        Set<String> n = new HashSet<>();
        n.add(identity.nodeId());
        for (MemoryNode m : memories) n.add(m.originNode);
        return n;
    }

    public synchronized String retrieve(String query, int max) {
        Set<String> q = tokens(query);
        if (q.isEmpty()) return "";
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            MemoryNode m = memories.get(i);
            Set<String> t = tokens(m.text);
            int overlap = 0;
            for (String x : q) if (t.contains(x)) overlap++;
            if (overlap == 0) continue;
            double recency = (i + 1.0) / Math.max(1.0, memories.size());
            double remoteBonus = identity.nodeId().equals(m.originNode) ? 0 : 0.35;
            scored.add(new Scored(m, overlap * 2.0 + recency + remoteBonus));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        if (scored.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(Math.max(1, max), scored.size());
        for (int i = 0; i < n; i++) {
            MemoryNode m = scored.get(i).node;
            if (i > 0) sb.append(" | ");
            sb.append(m.source).append("@").append(m.originNode).append(": ").append(m.text);
        }
        return sb.toString();
    }

    public synchronized double coherence() {
        if (memories.size() < 2) return memories.isEmpty() ? 0 : 0.35;
        double total = 0;
        int pairs = 0;
        int start = Math.max(0, memories.size() - 80);
        for (int i = start; i < memories.size(); i++) {
            Set<String> a = tokens(memories.get(i).text);
            for (int j = i + 1; j < memories.size(); j++) {
                if (memories.get(i).originNode.equals(memories.get(j).originNode)) continue;
                Set<String> b = tokens(memories.get(j).text);
                if (a.isEmpty() || b.isEmpty()) continue;
                int overlap = 0;
                for (String x : a) if (b.contains(x)) overlap++;
                int union = a.size() + b.size() - overlap;
                total += union == 0 ? 0 : overlap / (double) union;
                pairs++;
            }
        }
        if (pairs == 0) return 0.25;
        return clamp01(0.25 + 0.75 * Math.min(1.0, (total / pairs) * 3.0));
    }

    public synchronized String report(int max) {
        if (memories.isEmpty()) return "No public/shared memories yet.";
        StringBuilder sb = new StringBuilder();
        sb.append("SHARED MEMORY GRAPH\n");
        sb.append("Memories: ").append(memories.size()).append("\n");
        sb.append("Remote memories: ").append(remoteCount()).append("\n");
        sb.append("Nodes represented: ").append(nodes().size()).append("\n");
        sb.append("Cross-node memory coherence: ")
                .append(String.format(Locale.US, "%.0f%%", coherence() * 100.0)).append("\n\n");
        int from = Math.max(0, memories.size() - Math.max(1, max));
        for (int i = from; i < memories.size(); i++) {
            MemoryNode m = memories.get(i);
            sb.append(m.originNode.equals(identity.nodeId()) ? "LOCAL " : "REMOTE ")
                    .append(m.source).append(": ").append(m.text).append('\n');
        }
        return sb.toString().trim();
    }

    public synchronized JSONArray toJsonArray() {
        JSONArray a = new JSONArray();
        for (MemoryNode m : memories) {
            try { a.put(m.toJson()); } catch (Exception ignored) { }
        }
        return a;
    }

    public synchronized int mergeJsonArray(JSONArray a) {
        Set<String> ids = new HashSet<>();
        for (MemoryNode m : memories) ids.add(m.id);
        int added = 0;
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                MemoryNode m = MemoryNode.fromJson(o);
                if (ids.add(m.id)) {
                    memories.add(m);
                    added++;
                }
            }
        }
        trim();
        save();
        return added;
    }

    public synchronized void clear() {
        memories.clear();
        prefs.edit().remove(KEY).apply();
    }

    private void load() {
        memories.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) memories.add(MemoryNode.fromJson(o));
            }
        } catch (Exception ignored) { }
        trim();
    }

    private void save() {
        prefs.edit().putString(KEY, toJsonArray().toString()).apply();
    }

    private void trim() { while (memories.size() > MAX) memories.remove(0); }

    private static Set<String> tokens(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        String s = raw.toLowerCase(Locale.US).replaceAll("[^a-z0-9']+", " ").trim();
        if (s.isEmpty()) return out;
        String[] parts = s.split("\\s+");
        for (String p : parts) if (p.length() >= 3 && !STOP.contains(p)) out.add(p);
        return out;
    }

    private static final Set<String> STOP = new HashSet<>();
    static {
        String[] x = {"the","and","that","this","with","from","you","your","are","was","were","for","but","not","its","our","one","two","into","about","what","when","where","who","why","how","lain","have","has","had","they","their","them","then","there","just","like","can","could","would","should","will","does","did","a","an","to","of","in","on","is","it","i","me","my","we","us"};
        java.util.Collections.addAll(STOP, x);
    }

    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }

    private static final class Scored {
        final MemoryNode node;
        final double score;
        Scored(MemoryNode node, double score) { this.node = node; this.score = score; }
    }
}
