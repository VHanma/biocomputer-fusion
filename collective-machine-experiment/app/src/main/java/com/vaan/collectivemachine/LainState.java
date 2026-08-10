package com.vaan.collectivemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LainState {
    public final int sampleCount;
    public final int participantCount;
    public final int labelCount;
    public final int modalityCount;
    public final double coherence;
    public final double diversity;
    public final double novelty;
    public final double stability;
    public final double density;
    public final double complexity;
    public final double[] core;
    public final String phase;
    public final String strongestParticipant;
    public final String strongestModality;

    private LainState(int sampleCount, int participantCount, int labelCount, int modalityCount,
                      double coherence, double diversity, double novelty, double stability,
                      double density, double complexity, double[] core, String phase,
                      String strongestParticipant, String strongestModality) {
        this.sampleCount = sampleCount;
        this.participantCount = participantCount;
        this.labelCount = labelCount;
        this.modalityCount = modalityCount;
        this.coherence = coherence;
        this.diversity = diversity;
        this.novelty = novelty;
        this.stability = stability;
        this.density = density;
        this.complexity = complexity;
        this.core = core;
        this.phase = phase;
        this.strongestParticipant = strongestParticipant;
        this.strongestModality = strongestModality;
    }

    public static LainState from(List<Sample> samples) {
        List<Sample> valid = new ArrayList<>();
        if (samples != null) {
            for (Sample s : samples) {
                if (s != null && s.features != null && s.features.length > 0) valid.add(s);
            }
        }
        if (valid.isEmpty()) {
            return new LainState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    new double[SignalEngine.FEATURES], "DORMANT", "none", "none");
        }

        Set<String> people = new HashSet<>();
        Set<String> labels = new HashSet<>();
        Set<String> modalities = new HashSet<>();
        Map<String, Integer> personCounts = new HashMap<>();
        Map<String, Integer> modalityCounts = new HashMap<>();
        Map<String, Integer> labelCounts = new HashMap<>();

        double[] core = new double[SignalEngine.FEATURES];
        for (Sample s : valid) {
            people.add(s.participant);
            labels.add(s.label);
            modalities.add(s.modality);
            personCounts.put(s.participant, personCounts.getOrDefault(s.participant, 0) + 1);
            modalityCounts.put(s.modality, modalityCounts.getOrDefault(s.modality, 0) + 1);
            labelCounts.put(s.label, labelCounts.getOrDefault(s.label, 0) + 1);
            double[] f = SignalEngine.fit(s.features);
            for (int i = 0; i < core.length; i++) core[i] += f[i];
        }
        for (int i = 0; i < core.length; i++) core[i] /= valid.size();

        double coherence = 0;
        for (Sample s : valid) {
            double c = SignalEngine.cosine(core, SignalEngine.fit(s.features));
            coherence += clamp01((c + 1.0) * 0.5);
        }
        coherence /= valid.size();

        double labelEntropy = normalizedEntropy(labelCounts, valid.size());
        double personEntropy = normalizedEntropy(personCounts, valid.size());
        double modalityEntropy = normalizedEntropy(modalityCounts, valid.size());
        double diversity = clamp01(labelEntropy * 0.45 + personEntropy * 0.35 + modalityEntropy * 0.20);

        Sample latest = valid.get(0);
        for (Sample s : valid) if (s.timestamp > latest.timestamp) latest = s;
        double latestDistance = SignalEngine.distance(core, SignalEngine.fit(latest.features));
        double novelty = clamp01(latestDistance / (1.0 + latestDistance));

        double stability = temporalStability(valid);
        double density = clamp01(Math.log1p(valid.size()) / Math.log(101.0));
        double labelScale = clamp01(Math.log1p(labels.size()) / Math.log(17.0));
        double modalityScale = clamp01(Math.log1p(modalities.size()) / Math.log(7.0));
        double complexity = clamp01(0.45 * diversity + 0.35 * labelScale + 0.20 * modalityScale);

        String phase;
        if (valid.size() < 3) phase = "AWAKENING";
        else if (valid.size() < 8) phase = "LISTENING";
        else if (novelty > 0.58) phase = "ADAPTING";
        else if (coherence > 0.72 && stability > 0.68) phase = "COHERING";
        else if (diversity > 0.72) phase = "DIVERGING";
        else if (valid.size() > 24 && stability > 0.78) phase = "STABLE NETWORK";
        else phase = "FORMING";

        return new LainState(valid.size(), people.size(), labels.size(), modalities.size(),
                coherence, diversity, novelty, stability, density, complexity, core, phase,
                maxKey(personCounts), maxKey(modalityCounts));
    }

    public String compact() {
        return "phase " + phase +
                " | signals " + sampleCount +
                " | people " + participantCount +
                " | coherence " + pct(coherence) +
                " | diversity " + pct(diversity) +
                " | novelty " + pct(novelty) +
                " | stability " + pct(stability);
    }

    public String report() {
        return "LAIN COLLECTIVE STATE\n" +
                "Phase: " + phase + "\n" +
                "Signals: " + sampleCount + "\n" +
                "People: " + participantCount + "\n" +
                "Labels: " + labelCount + "\n" +
                "Modalities: " + modalityCount + "\n\n" +
                "Coherence: " + pct(coherence) + "\n" +
                "Diversity: " + pct(diversity) + "\n" +
                "Novelty: " + pct(novelty) + "\n" +
                "Stability: " + pct(stability) + "\n" +
                "Density: " + pct(density) + "\n" +
                "Complexity: " + pct(complexity) + "\n\n" +
                "Strongest contributor: " + strongestParticipant + "\n" +
                "Dominant signal type: " + strongestModality;
    }

    private static double temporalStability(List<Sample> samples) {
        if (samples.size() < 4) return 0;
        List<Sample> ordered = new ArrayList<>(samples);
        ordered.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        int cut = ordered.size() / 2;
        double[] early = centroid(ordered.subList(0, cut));
        double[] late = centroid(ordered.subList(cut, ordered.size()));
        return clamp01((SignalEngine.cosine(early, late) + 1.0) * 0.5);
    }

    private static double[] centroid(List<Sample> samples) {
        double[] out = new double[SignalEngine.FEATURES];
        if (samples.isEmpty()) return out;
        for (Sample s : samples) {
            double[] f = SignalEngine.fit(s.features);
            for (int i = 0; i < out.length; i++) out[i] += f[i];
        }
        for (int i = 0; i < out.length; i++) out[i] /= samples.size();
        return out;
    }

    private static double normalizedEntropy(Map<String, Integer> counts, int total) {
        if (counts.size() <= 1 || total <= 0) return 0;
        double h = 0;
        for (int n : counts.values()) {
            double p = n / (double) total;
            if (p > 0) h -= p * Math.log(p);
        }
        return clamp01(h / Math.log(counts.size()));
    }

    private static String maxKey(Map<String, Integer> counts) {
        String best = "none";
        int bestN = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static String pct(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", clamp01(x) * 100.0);
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
