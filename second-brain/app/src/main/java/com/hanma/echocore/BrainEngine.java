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

    public BrainEngine(BrainDatabase db) {
        this.db = db;
    }

    public String answer(String input) {
        String original = input == null ? "" : input.trim();
        String q = original.toLowerCase(Locale.US);
        if (q.isEmpty()) return "Give me a thought, question, memory, or command.";

        if (q.startsWith("remember ")) {
            String body = original.substring(Math.min(9, original.length())).trim();
            long id = db.addMemory(body, "THOUGHT", autoTags(body), 7);
            return id > 0 ? "Stored. I turned that into a memory node and tagged it: " + autoTags(body) : "I need something after ‘remember’.";
        }

        if (q.contains("summarize today") || q.contains("summary of today") || q.equals("today")) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            List<MemoryNode> items = db.since(cal.getTimeInMillis(), 40);
            if (items.isEmpty()) return "Today is still an empty page in EchoCore. Capture a few thoughts and I’ll compress the pattern.";
            return summarize(items, "Today");
        }

        if (q.contains("what should i focus") || q.contains("focus stack") || q.equals("focus")) {
            List<MemoryNode> focus = db.byType("FOCUS", 8);
            if (focus.isEmpty()) return "Your Focus Stack is empty. Capture something as FOCUS and I’ll keep it near the surface.";
            StringBuilder b = new StringBuilder("Your active Focus Stack:\n");
            int n = 1;
            for (MemoryNode m : focus) b.append(n++).append(". ").append(trim(m.text, 120)).append("\n");
            return b.toString().trim();
        }

        if (q.startsWith("connect ") && q.contains(" and ")) {
            int at = q.indexOf(" and ");
            String a = original.substring(8, at).trim();
            String b = original.substring(at + 5).trim();
            return connect(a, b);
        }

        String cleaned = q
                .replace("what did i say about", "")
                .replace("what do i know about", "")
                .replace("find memories about", "")
                .replace("find memory about", "")
                .replace("recall", "")
                .trim();

        if (q.equals("recall") || q.equals("surprise me") || q.contains("random memory")) {
            List<MemoryNode> r = db.recent(30);
            if (r.isEmpty()) return "There’s nothing in the vault yet.";
            int index = (int) (Math.abs(System.nanoTime()) % r.size());
            MemoryNode m = r.get(index);
            return "Recall spark:\n“" + trim(m.text, 260) + "”\n\nFiled as " + m.type + suffixTags(m.tags) + ".";
        }

        List<MemoryNode> hits = db.search(cleaned.isEmpty() ? original : cleaned, 6);
        if (hits.isEmpty()) {
            return "I don’t have a strong memory match for that yet. If it matters, say “remember …” or capture it on Cortex and it becomes part of the brain.";
        }

        StringBuilder out = new StringBuilder();
        out.append("I found ").append(hits.size()).append(hits.size() == 1 ? " memory" : " memories").append(" that overlap:\n\n");
        for (int i = 0; i < Math.min(4, hits.size()); i++) {
            MemoryNode m = hits.get(i);
            out.append("• ").append(trim(m.text, 180));
            if (!m.tags.isEmpty()) out.append("  [").append(m.tags).append("]");
            out.append("\n");
        }
        out.append("\nPattern: ").append(patternSentence(hits));
        return out.toString();
    }

    public String summarize(List<MemoryNode> items, String label) {
        Map<String, Integer> freq = new HashMap<>();
        for (MemoryNode m : items) {
            for (String w : words(m.text + " " + m.tags)) {
                if (!isStop(w)) freq.put(w, freq.getOrDefault(w, 0) + Math.max(1, m.importance / 3));
            }
        }
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder concepts = new StringBuilder();
        for (int i = 0; i < Math.min(6, entries.size()); i++) {
            if (i > 0) concepts.append(", ");
            concepts.append(entries.get(i).getKey());
        }
        StringBuilder b = new StringBuilder(label).append(" compressed into ").append(items.size()).append(" nodes.");
        if (concepts.length() > 0) b.append("\nDominant concepts: ").append(concepts).append(".");
        b.append("\n\nHigh-signal memories:\n");
        items.stream().sorted((x, y) -> y.importance - x.importance).limit(4)
                .forEach(m -> b.append("• ").append(trim(m.text, 150)).append("\n"));
        return b.toString().trim();
    }

    private String connect(String a, String b) {
        List<MemoryNode> left = db.search(a, 4);
        List<MemoryNode> right = db.search(b, 4);
        if (left.isEmpty() || right.isEmpty()) {
            return "I need memory nodes on both sides before I can build that bridge. I found " + left.size() + " for “" + a + "” and " + right.size() + " for “" + b + "”.";
        }
        Set<String> wa = significantWords(join(left));
        Set<String> wb = significantWords(join(right));
        Set<String> overlap = new HashSet<>(wa);
        overlap.retainAll(wb);
        String bridge;
        if (overlap.isEmpty()) {
            bridge = "The direct vocabulary overlap is weak, which makes this a useful cross-domain bridge. The connection is contextual rather than literal.";
        } else {
            bridge = "Shared bridge terms: " + String.join(", ", overlap) + ".";
        }
        return "Connection map: “" + a + "” ↔ “" + b + "”\n\n" + bridge +
                "\n\nA-side: " + trim(left.get(0).text, 140) +
                "\nB-side: " + trim(right.get(0).text, 140);
    }

    private String patternSentence(List<MemoryNode> hits) {
        Map<String, Integer> freq = new HashMap<>();
        for (MemoryNode m : hits) for (String w : words(m.text + " " + m.tags))
            if (!isStop(w)) freq.put(w, freq.getOrDefault(w, 0) + 1);
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        if (entries.isEmpty()) return "the strongest signal is in the individual memory wording.";
        ArrayList<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(4, entries.size()); i++) top.add(entries.get(i).getKey());
        return "the cluster keeps circling " + String.join(", ", top) + ".";
    }

    public String autoTags(String text) {
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words(text)) if (!isStop(w) && w.length() > 3) freq.put(w, freq.getOrDefault(w, 0) + 1);
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        ArrayList<String> tags = new ArrayList<>();
        for (int i = 0; i < Math.min(4, entries.size()); i++) tags.add(entries.get(i).getKey());
        return String.join(", ", tags);
    }

    public static int sharedWordScore(MemoryNode a, MemoryNode b) {
        Set<String> x = significantWords(a.text + " " + a.tags);
        Set<String> y = significantWords(b.text + " " + b.tags);
        x.retainAll(y);
        return x.size();
    }

    private static String join(List<MemoryNode> list) {
        StringBuilder b = new StringBuilder();
        for (MemoryNode m : list) b.append(' ').append(m.text).append(' ').append(m.tags);
        return b.toString();
    }

    private static Set<String> significantWords(String s) {
        HashSet<String> set = new HashSet<>();
        for (String w : words(s)) if (!isStop(w) && w.length() > 3) set.add(w);
        return set;
    }

    private static String[] words(String s) {
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").split("\\s+");
    }

    private static boolean isStop(String w) {
        String stops = " the a an and or but to of in on for with my i me is are was were be this that it you your about from into as at we our do did have has had what how why when where should would could can just very then than so ";
        return w.isEmpty() || stops.contains(" " + w + " ");
    }

    private static String suffixTags(String tags) {
        return tags == null || tags.trim().isEmpty() ? "" : " · " + tags;
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)).trim() + "…";
    }

    public static String formatDate(long time) {
        return new SimpleDateFormat("MMM d · h:mm a", Locale.US).format(time);
    }
}
