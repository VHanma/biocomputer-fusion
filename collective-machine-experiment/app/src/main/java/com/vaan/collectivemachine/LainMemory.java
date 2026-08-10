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

public final class LainMemory {
    public static final class Item {
        public final String speaker;
        public final String text;
        public final long timestamp;
        public final String kind;

        Item(String speaker, String text, long timestamp, String kind) {
            this.speaker = speaker;
            this.text = text;
            this.timestamp = timestamp;
            this.kind = kind;
        }
    }

    private static final String PREF = "lain_memory_store";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_GENERATION = "generation";
    private static final int MAX_ITEMS = 400;

    private final SharedPreferences prefs;
    private final List<Item> items = new ArrayList<>();

    public LainMemory(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public synchronized void rememberUser(String text) {
        append("YOU", text, "dialogue");
    }

    public synchronized void rememberLain(String text) {
        append("LAIN", text, "dialogue");
    }

    public synchronized void rememberCollective(String text) {
        append("COLLECTIVE", text, "state");
    }

    public synchronized void rememberPrompt(String text) {
        append("LAIN", text, "prompt");
    }

    public synchronized int generation() {
        return prefs.getInt(KEY_GENERATION, 0);
    }

    public synchronized int nextGeneration() {
        int g = generation() + 1;
        prefs.edit().putInt(KEY_GENERATION, g).apply();
        return g;
    }

    public synchronized int size() { return items.size(); }

    public synchronized List<Item> snapshot() {
        return new ArrayList<>(items);
    }

    public synchronized String recentTranscript(int maxItems) {
        if (items.isEmpty()) return "No memory yet.";
        int from = Math.max(0, items.size() - Math.max(1, maxItems));
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < items.size(); i++) {
            Item x = items.get(i);
            sb.append(x.speaker).append(": ").append(x.text).append('\n');
        }
        return sb.toString().trim();
    }

    public synchronized String retrieve(String query, int maxItems) {
        Set<String> q = tokens(query);
        if (q.isEmpty()) return "";
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Set<String> t = tokens(item.text);
            if (t.isEmpty()) continue;
            int overlap = 0;
            for (String token : q) if (t.contains(token)) overlap++;
            if (overlap == 0) continue;
            double recency = (i + 1.0) / Math.max(1.0, items.size());
            double score = overlap * 2.0 + recency;
            if ("YOU".equals(item.speaker)) score += 0.35;
            scored.add(new Scored(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        StringBuilder sb = new StringBuilder();
        int n = Math.min(Math.max(1, maxItems), scored.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(scored.get(i).item.speaker).append(": ").append(scored.get(i).item.text);
        }
        return sb.toString();
    }

    public synchronized List<String> keywords(int max) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (Item item : items) {
            for (String token : tokens(item.text)) {
                if (token.length() < 4 || STOP.contains(token)) continue;
                counts.put(token, counts.getOrDefault(token, 0) + 1);
            }
        }
        List<String> words = new ArrayList<>(counts.keySet());
        words.sort((a, b) -> Integer.compare(counts.getOrDefault(b, 0), counts.getOrDefault(a, 0)));
        if (words.size() > max) return new ArrayList<>(words.subList(0, max));
        return words;
    }

    public synchronized void clear() {
        items.clear();
        prefs.edit().remove(KEY_ITEMS).remove(KEY_GENERATION).apply();
    }

    private void append(String speaker, String text, String kind) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) return;
        items.add(new Item(speaker, clean, System.currentTimeMillis(), kind));
        while (items.size() > MAX_ITEMS) items.remove(0);
        save();
    }

    private void load() {
        items.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                items.add(new Item(
                        o.optString("speaker", "UNKNOWN"),
                        o.optString("text", ""),
                        o.optLong("timestamp", 0),
                        o.optString("kind", "dialogue")
                ));
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject o = new JSONObject();
                o.put("speaker", item.speaker);
                o.put("text", item.text);
                o.put("timestamp", item.timestamp);
                o.put("kind", item.kind);
                a.put(o);
            }
            prefs.edit().putString(KEY_ITEMS, a.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static Set<String> tokens(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        String[] parts = raw.toLowerCase(Locale.US).replaceAll("[^a-z0-9']+", " ").trim().split("\\s+");
        for (String p : parts) if (!p.isEmpty() && !STOP.contains(p)) out.add(p);
        return out;
    }

    private static final Set<String> STOP = new HashSet<>();
    static {
        String[] s = {"the","and","that","this","with","from","have","what","when","where","who","why","how","you","your","yours","are","was","were","will","would","could","should","into","about","just","like","they","them","their","there","then","than","for","but","not","its","our","out","all","can","does","did","been","being","too","very","more","some","any","one","two","a","an","to","of","in","on","is","it","i","me","my","we","us"};
        java.util.Collections.addAll(STOP, s);
    }

    private static final class Scored {
        final Item item;
        final double score;
        Scored(Item item, double score) { this.item = item; this.score = score; }
    }
}
