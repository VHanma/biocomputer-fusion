package com.vaan.collectivemachine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent graph of how different people/nodes describe Lain. */
public final class IdentityGraph {
    public static final class Claim {
        public final String id;
        public final String source;
        public final String text;
        public final String key;
        public final int polarity;
        public final long timestamp;
        public final String originNode;

        Claim(String id, String source, String text, String key, int polarity,
              long timestamp, String originNode) {
            this.id = id;
            this.source = source;
            this.text = text;
            this.key = key;
            this.polarity = Math.max(-1, Math.min(1, polarity));
            this.timestamp = timestamp;
            this.originNode = originNode;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("source", source);
            o.put("text", text);
            o.put("key", key);
            o.put("polarity", polarity);
            o.put("timestamp", timestamp);
            o.put("originNode", originNode);
            return o;
        }

        static Claim fromJson(JSONObject o) {
            String text = o.optString("text", "");
            return new Claim(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("source", "Unknown"),
                    text,
                    o.optString("key", canonical(text)),
                    o.optInt("polarity", 0),
                    o.optLong("timestamp", System.currentTimeMillis()),
                    o.optString("originNode", "unknown-node")
            );
        }
    }

    public static final class Belief {
        public final String key;
        public final String example;
        public final int positive;
        public final int negative;
        public final int uncertain;
        public final int sourceCount;
        public final double consensus;
        public final int majority;

        Belief(String key, String example, int positive, int negative, int uncertain,
               int sourceCount, double consensus, int majority) {
            this.key = key;
            this.example = example;
            this.positive = positive;
            this.negative = negative;
            this.uncertain = uncertain;
            this.sourceCount = sourceCount;
            this.consensus = consensus;
            this.majority = majority;
        }

        public String line() {
            String stance = majority > 0 ? "AFFIRMED" : majority < 0 ? "DENIED" : "UNRESOLVED";
            return stance + "  " + example + "  | consensus " + pct(consensus) +
                    " | +" + positive + " / -" + negative + " / ?" + uncertain +
                    " | sources " + sourceCount;
        }
    }

    private static final String PREF = "lain_identity_graph_v2";
    private static final String KEY_NODE = "node_id";
    private static final String KEY_CLAIMS = "claims";
    private static final int MAX_CLAIMS = 1200;

    private final SharedPreferences prefs;
    private final List<Claim> claims = new ArrayList<>();
    private final String nodeId;

    public IdentityGraph(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_NODE, "");
        if (saved == null || saved.trim().isEmpty()) {
            saved = "NAVI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
            prefs.edit().putString(KEY_NODE, saved).apply();
        }
        nodeId = saved;
        load();
    }

    public synchronized String nodeId() { return nodeId; }
    public synchronized int size() { return claims.size(); }

    public synchronized void addLocalClaim(String source, String text, int polarity) {
        addClaim(source, text, polarity, nodeId);
    }

    public synchronized void addClaim(String source, String text, int polarity, String originNode) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) return;
        claims.add(new Claim(UUID.randomUUID().toString(), cleanSource(source), clean,
                canonical(clean), polarity, System.currentTimeMillis(), cleanNode(originNode)));
        trim();
        save();
    }

    public synchronized List<Claim> snapshot() { return new ArrayList<>(claims); }

    public synchronized Set<String> nodes() {
        Set<String> out = new HashSet<>();
        out.add(nodeId);
        for (Claim c : claims) out.add(c.originNode);
        return out;
    }

    public synchronized int remoteNodeCount() {
        Set<String> n = nodes();
        n.remove(nodeId);
        n.remove("unknown-node");
        return n.size();
    }

    public synchronized int sourceCount() {
        Set<String> s = new HashSet<>();
        for (Claim c : claims) s.add(c.source);
        return s.size();
    }

    public synchronized List<Belief> beliefs() {
        Map<String, List<Claim>> grouped = new LinkedHashMap<>();
        for (Claim c : claims) grouped.computeIfAbsent(c.key, k -> new ArrayList<>()).add(c);
        List<Belief> out = new ArrayList<>();
        for (Map.Entry<String, List<Claim>> e : grouped.entrySet()) {
            int pos = 0, neg = 0, unc = 0;
            Set<String> sources = new HashSet<>();
            String example = e.getValue().get(e.getValue().size() - 1).text;
            for (Claim c : e.getValue()) {
                sources.add(c.source + "@" + c.originNode);
                if (c.polarity > 0) pos++;
                else if (c.polarity < 0) neg++;
                else unc++;
            }
            int decisive = pos + neg;
            double consensus = decisive == 0 ? 0 : Math.abs(pos - neg) / (double) decisive;
            int majority = pos == neg ? 0 : (pos > neg ? 1 : -1);
            out.add(new Belief(e.getKey(), example, pos, neg, unc, sources.size(), consensus, majority));
        }
        out.sort((a, b) -> {
            int bySources = Integer.compare(b.sourceCount, a.sourceCount);
            if (bySources != 0) return bySources;
            return Double.compare(b.consensus, a.consensus);
        });
        return out;
    }

    public synchronized double meanConsensus() {
        List<Belief> b = beliefs();
        if (b.isEmpty()) return 0;
        double sum = 0, weight = 0;
        for (Belief x : b) {
            double w = Math.max(1, x.sourceCount);
            sum += x.consensus * w;
            weight += w;
        }
        return weight == 0 ? 0 : sum / weight;
    }

    public synchronized double fragmentation() {
        List<Belief> b = beliefs();
        if (b.isEmpty()) return 0;
        double contradiction = 0, weight = 0;
        for (Belief x : b) {
            int decisive = x.positive + x.negative;
            if (decisive == 0) continue;
            double split = 1.0 - x.consensus;
            double w = Math.max(1, x.sourceCount);
            contradiction += split * w;
            weight += w;
        }
        return weight == 0 ? 0 : clamp01(contradiction / weight);
    }

    public synchronized String profileFor(String source) {
        String who = cleanSource(source);
        List<Claim> mine = new ArrayList<>();
        for (Claim c : claims) if (c.source.equalsIgnoreCase(who)) mine.add(c);
        if (mine.isEmpty()) return who + " has not described Lain yet.";
        mine.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        StringBuilder sb = new StringBuilder();
        sb.append("LAIN ACCORDING TO ").append(who.toUpperCase(Locale.US)).append('\n');
        int n = Math.min(12, mine.size());
        for (int i = 0; i < n; i++) {
            Claim c = mine.get(i);
            sb.append(c.polarity > 0 ? "+ " : c.polarity < 0 ? "- " : "? ")
                    .append(c.text).append('\n');
        }
        return sb.toString().trim();
    }

    public synchronized String consensusProfile(int max) {
        List<Belief> b = beliefs();
        if (b.isEmpty()) return "No one has described Lain yet.";
        StringBuilder sb = new StringBuilder();
        sb.append("COLLECTIVE IMAGE OF LAIN\n");
        int n = Math.min(Math.max(1, max), b.size());
        for (int i = 0; i < n; i++) sb.append("• ").append(b.get(i).line()).append('\n');
        sb.append("\nIdentity fragmentation: ").append(pct(fragmentation()));
        return sb.toString().trim();
    }

    public synchronized String strongestContradiction() {
        Belief best = null;
        double bestScore = -1;
        for (Belief b : beliefs()) {
            if (b.positive == 0 || b.negative == 0) continue;
            double score = (1.0 - b.consensus) * Math.max(1, b.sourceCount);
            if (score > bestScore) { best = b; bestScore = score; }
        }
        return best == null ? "none" : best.line();
    }

    public synchronized JSONArray toJsonArray() {
        JSONArray a = new JSONArray();
        for (Claim c : claims) {
            try { a.put(c.toJson()); } catch (Exception ignored) { }
        }
        return a;
    }

    public synchronized int mergeJsonArray(JSONArray a) {
        Set<String> ids = new HashSet<>();
        for (Claim c : claims) ids.add(c.id);
        int added = 0;
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Claim c = Claim.fromJson(o);
                if (ids.add(c.id)) {
                    claims.add(c);
                    added++;
                }
            }
        }
        trim();
        save();
        return added;
    }

    public synchronized void clearClaims() {
        claims.clear();
        prefs.edit().remove(KEY_CLAIMS).apply();
    }

    private void load() {
        claims.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_CLAIMS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) claims.add(Claim.fromJson(o));
            }
        } catch (Exception ignored) { }
        trim();
    }

    private void save() {
        prefs.edit().putString(KEY_CLAIMS, toJsonArray().toString()).apply();
    }

    private void trim() {
        while (claims.size() > MAX_CLAIMS) claims.remove(0);
    }

    private static String canonical(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.US).trim();
        s = s.replaceAll("[^a-z0-9']+", " ").trim();
        s = s.replaceFirst("^lain('s| is| seems| feels| appears| has| likes| hates| wants| remembers)?\\s*", "");
        if (s.isEmpty()) s = "lain";
        return s;
    }

    private static String cleanSource(String source) {
        String s = source == null ? "" : source.trim();
        return s.isEmpty() ? "Unknown" : s;
    }

    private static String cleanNode(String node) {
        String s = node == null ? "" : node.trim();
        return s.isEmpty() ? "unknown-node" : s;
    }

    private static String pct(double x) {
        return String.format(Locale.US, "%.0f%%", clamp01(x) * 100.0);
    }

    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
}
