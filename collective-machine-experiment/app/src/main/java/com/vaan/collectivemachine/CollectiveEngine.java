package com.vaan.collectivemachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class CollectiveEngine {
    public static final class State {
        public final String session;
        public final int round;
        public final double[] core;
        public final int signals;
        public final int participants;
        public final int modalities;
        public final int baselineCovered;
        public final double coherence;
        public final double entropy;
        public final double novelty;
        public final double stability;
        public final Map<String, Double> influence;

        State(String session, int round, double[] core, int signals, int participants, int modalities,
              int baselineCovered, double coherence, double entropy, double novelty, double stability,
              Map<String, Double> influence) {
            this.session = session;
            this.round = round;
            this.core = core;
            this.signals = signals;
            this.participants = participants;
            this.modalities = modalities;
            this.baselineCovered = baselineCovered;
            this.coherence = coherence;
            this.entropy = entropy;
            this.novelty = novelty;
            this.stability = stability;
            this.influence = influence;
        }
    }

    private CollectiveEngine() { }

    public static State stateForRound(List<Sample> all, String session, int round) {
        List<Sample> current = filter(all, session, round);
        Map<String, double[]> participantVectors = participantVectors(all, current, session, round);
        double[] core = meanVectors(new ArrayList<>(participantVectors.values()));
        int baselineCovered = 0;
        for (Sample s : current) if (hasBaseline(all, s, session, round)) baselineCovered++;

        double coherence = coherence(new ArrayList<>(participantVectors.values()));
        double entropy = dispersion(new ArrayList<>(participantVectors.values()));
        double[] previous = round > 0 ? roundCore(all, session, round - 1) : new double[SignalEngine.FEATURES];
        double stability = round > 0 && countRound(all, session, round - 1) > 0
                ? Math.exp(-SignalEngine.distance(core, previous)) : 0.0;

        List<double[]> history = new ArrayList<>();
        Set<Integer> rounds = roundsBefore(all, session, round);
        for (int r : rounds) history.add(roundCore(all, session, r));
        double[] historyMean = meanVectors(history);
        double novelty = history.isEmpty() ? 0.0 : 1.0 - Math.exp(-SignalEngine.distance(core, historyMean));

        Map<String, Double> influence = new LinkedHashMap<>();
        for (String participant : participantVectors.keySet()) {
            List<double[]> others = new ArrayList<>();
            for (Map.Entry<String,double[]> e : participantVectors.entrySet()) {
                if (!participant.equals(e.getKey())) others.add(e.getValue());
            }
            double[] without = meanVectors(others);
            influence.put(participant, others.isEmpty() ? 1.0 : SignalEngine.distance(core, without));
        }
        normalizeInfluence(influence);

        Set<String> mods = new HashSet<>();
        for (Sample s : current) mods.add(s.modality);
        return new State(session, round, core, current.size(), participantVectors.size(), mods.size(),
                baselineCovered, coherence, entropy, clamp01(novelty), clamp01(stability), influence);
    }

    public static String stateSummary(State s) {
        StringBuilder sb = new StringBuilder();
        sb.append("SESSION ").append(s.session).append("  ROUND ").append(s.round).append('\n');
        sb.append("Signals: ").append(s.signals)
                .append("   People: ").append(s.participants)
                .append("   Modalities: ").append(s.modalities).append('\n');
        sb.append("Personal-baseline coverage: ").append(s.baselineCovered).append('/').append(Math.max(1, s.signals)).append('\n');
        sb.append("Collective coherence: ").append(percent(s.coherence)).append('\n');
        sb.append("Signal entropy/dispersion: ").append(percent(s.entropy)).append('\n');
        sb.append("Novelty vs earlier rounds: ").append(percent(s.novelty)).append('\n');
        sb.append("Stability vs prior round: ").append(percent(s.stability)).append('\n');
        if (!s.influence.isEmpty()) {
            sb.append("\nInfluence on current core:\n");
            List<Map.Entry<String,Double>> entries = new ArrayList<>(s.influence.entrySet());
            entries.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String,Double> e : entries) {
                sb.append("• ").append(e.getKey()).append("  ").append(percent(e.getValue())).append('\n');
            }
        }
        return sb.toString().trim();
    }

    public static String baselineReport(List<Sample> all, String session) {
        Map<String, Map<String,Integer>> counts = new LinkedHashMap<>();
        for (Sample s : all) {
            if (!session.equals(s.session)) continue;
            counts.computeIfAbsent(s.participant, k -> new LinkedHashMap<>());
            Map<String,Integer> m = counts.get(s.participant);
            m.put(s.modality, m.getOrDefault(s.modality, 0) + 1);
        }
        if (counts.isEmpty()) return "No participant baselines yet for session " + session + ".";
        StringBuilder sb = new StringBuilder("PERSONAL BASELINES\n");
        for (Map.Entry<String,Map<String,Integer>> e : counts.entrySet()) {
            sb.append(e.getKey()).append(": ");
            boolean first = true;
            for (Map.Entry<String,Integer> m : e.getValue().entrySet()) {
                if (!first) sb.append("  ");
                first = false;
                sb.append(m.getKey()).append('=').append(m.getValue());
            }
            sb.append('\n');
        }
        sb.append("A participant/modality baseline begins affecting the collective after an earlier sample exists.");
        return sb.toString();
    }

    public static String shuffledControlReport(List<Sample> all, String session) {
        List<Integer> rounds = new ArrayList<>(roundsBefore(all, session, Integer.MAX_VALUE));
        Collections.sort(rounds);
        List<List<double[]>> groups = new ArrayList<>();
        List<double[]> pool = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        double realSum = 0;
        int usable = 0;
        for (int r : rounds) {
            List<Sample> rs = filter(all, session, r);
            Map<String,double[]> pv = participantVectors(all, rs, session, r);
            List<double[]> v = new ArrayList<>(pv.values());
            if (v.size() < 2) continue;
            groups.add(v);
            sizes.add(v.size());
            pool.addAll(v);
            realSum += coherence(v);
            usable++;
        }
        if (usable < 2 || pool.size() < 4) return "Need at least 2 synchronized multi-person rounds for the shuffled control.";
        double real = realSum / usable;
        Random random = new Random(684350L + pool.size());
        double shuffled = 0;
        int permutations = 100;
        for (int p = 0; p < permutations; p++) {
            List<double[]> copy = new ArrayList<>(pool);
            Collections.shuffle(copy, random);
            int at = 0;
            double sum = 0;
            for (int size : sizes) {
                List<double[]> g = new ArrayList<>();
                for (int i = 0; i < size && at < copy.size(); i++) g.add(copy.get(at++));
                sum += coherence(g);
            }
            shuffled += sum / sizes.size();
        }
        shuffled /= permutations;
        double delta = real - shuffled;
        return "SHUFFLED CONTROL\nReal synchronized coherence: " + percent(real) +
                "\nShuffled-round coherence: " + percent(shuffled) +
                "\nDifference: " + signedPercent(delta) +
                "\n\nPositive means the actual round grouping was more internally aligned than 100 random regroupings. This is an experimental statistic, not proof of a hidden causal mechanism.";
    }

    public static String collectiveVsPersonalPrediction(List<Sample> all, String session) {
        List<Sample> ordered = new ArrayList<>();
        for (Sample s : all) if (session.equals(s.session)) ordered.add(s);
        ordered.sort(Comparator.comparingLong(s -> s.timestamp));
        if (ordered.size() < 12) return "Need at least 12 session samples for a useful collective-vs-personal prediction test.";
        int cut = Math.max(4, (int)Math.floor(ordered.size() * (2.0/3.0)));
        List<Sample> train = new ArrayList<>(ordered.subList(0, cut));
        List<Sample> test = new ArrayList<>(ordered.subList(cut, ordered.size()));

        ModelEngine.Prototype group = ModelEngine.build(train);
        int groupCorrect = 0, groupTotal = 0;
        int personalCorrect = 0, personalTotal = 0;
        for (Sample t : test) {
            if (group.centroids.containsKey(t.label)) {
                String pred = ModelEngine.classify(group, t.features);
                groupTotal++;
                if (t.label.equals(pred)) groupCorrect++;
            }
            List<Sample> personalTrain = new ArrayList<>();
            for (Sample s : train) if (s.participant.equals(t.participant)) personalTrain.add(s);
            ModelEngine.Prototype personal = ModelEngine.build(personalTrain);
            if (personal.centroids.containsKey(t.label)) {
                String pred = ModelEngine.classify(personal, t.features);
                personalTotal++;
                if (t.label.equals(pred)) personalCorrect++;
            }
        }
        if (groupTotal == 0 && personalTotal == 0) return "The held-out labels were not represented in training. Repeat labels across rounds to run this test.";
        double ga = groupTotal == 0 ? 0 : groupCorrect/(double)groupTotal;
        double pa = personalTotal == 0 ? 0 : personalCorrect/(double)personalTotal;
        return "COLLECTIVE VS PERSONAL PREDICTION\nCollective model: " + groupCorrect + "/" + groupTotal + "  " + percent(ga) +
                "\nPersonal models: " + personalCorrect + "/" + personalTotal + "  " + percent(pa) +
                "\nDifference: " + signedPercent(ga-pa) +
                "\n\nThis asks whether the pooled group model predicts held-out labels better than participant-only models on comparable available cases.";
    }

    public static double[] baselineFor(List<Sample> all, Sample current, String session, int round) {
        List<double[]> vectors = new ArrayList<>();
        for (Sample s : all) {
            if (!session.equals(s.session)) continue;
            if (!current.participant.equals(s.participant)) continue;
            if (!current.modality.equals(s.modality)) continue;
            if (s.round >= round || s.timestamp >= current.timestamp) continue;
            vectors.add(s.features);
        }
        return meanVectors(vectors);
    }

    public static List<Sample> filter(List<Sample> all, String session, int round) {
        List<Sample> out = new ArrayList<>();
        for (Sample s : all) if (session.equals(s.session) && s.round == round) out.add(s);
        return out;
    }

    private static Map<String,double[]> participantVectors(List<Sample> all, List<Sample> current, String session, int round) {
        Map<String,List<double[]>> byPerson = new LinkedHashMap<>();
        for (Sample s : current) {
            double[] baseline = baselineFor(all, s, session, round);
            boolean has = hasBaseline(all, s, session, round);
            double[] residual = new double[SignalEngine.FEATURES];
            for (int i = 0; i < residual.length; i++) residual[i] = s.features[i] - (has ? baseline[i] : 0.0);
            SignalEngine.normalize(residual);
            byPerson.computeIfAbsent(s.participant, k -> new ArrayList<>()).add(residual);
        }
        Map<String,double[]> result = new LinkedHashMap<>();
        for (Map.Entry<String,List<double[]>> e : byPerson.entrySet()) result.put(e.getKey(), meanVectors(e.getValue()));
        return result;
    }

    private static boolean hasBaseline(List<Sample> all, Sample current, String session, int round) {
        for (Sample s : all) {
            if (session.equals(s.session) && current.participant.equals(s.participant) && current.modality.equals(s.modality)
                    && s.round < round && s.timestamp < current.timestamp) return true;
        }
        return false;
    }

    private static double[] roundCore(List<Sample> all, String session, int round) {
        List<Sample> rs = filter(all, session, round);
        return meanVectors(new ArrayList<>(participantVectors(all, rs, session, round).values()));
    }

    private static int countRound(List<Sample> all, String session, int round) {
        int n = 0; for (Sample s : all) if (session.equals(s.session) && s.round == round) n++; return n;
    }

    private static Set<Integer> roundsBefore(List<Sample> all, String session, int round) {
        Set<Integer> out = new HashSet<>();
        for (Sample s : all) if (session.equals(s.session) && s.round < round) out.add(s.round);
        return out;
    }

    public static double[] meanVectors(List<double[]> vectors) {
        double[] out = new double[SignalEngine.FEATURES];
        if (vectors == null || vectors.isEmpty()) return out;
        for (double[] v : vectors) {
            double[] fitted = SignalEngine.fit(v);
            for (int i = 0; i < out.length; i++) out[i] += fitted[i];
        }
        for (int i = 0; i < out.length; i++) out[i] /= vectors.size();
        return out;
    }

    private static double coherence(List<double[]> vectors) {
        if (vectors.size() < 2) return 0;
        double sum = 0; int n = 0;
        for (int i = 0; i < vectors.size(); i++) {
            for (int j = i + 1; j < vectors.size(); j++) {
                sum += (SignalEngine.cosine(vectors.get(i), vectors.get(j)) + 1.0) * 0.5;
                n++;
            }
        }
        return clamp01(sum / Math.max(1, n));
    }

    private static double dispersion(List<double[]> vectors) {
        if (vectors.size() < 2) return 0;
        double[] mean = meanVectors(vectors);
        double sum = 0;
        for (double[] v : vectors) {
            double d = SignalEngine.distance(v, mean);
            sum += d*d;
        }
        double rms = Math.sqrt(sum / vectors.size());
        return clamp01(1.0 - Math.exp(-rms));
    }

    private static void normalizeInfluence(Map<String,Double> influence) {
        double max = 0;
        for (double v : influence.values()) max = Math.max(max, v);
        if (max < 1e-12) {
            for (String k : new ArrayList<>(influence.keySet())) influence.put(k, 0.0);
            return;
        }
        for (String k : new ArrayList<>(influence.keySet())) influence.put(k, clamp01(influence.get(k) / max));
    }

    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    private static String percent(double x) { return String.format(java.util.Locale.US, "%.1f%%", clamp01(x)*100.0); }
    private static String signedPercent(double x) { return String.format(java.util.Locale.US, "%+.1f%%", x*100.0); }
}
