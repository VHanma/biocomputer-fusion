package com.vaan.collectivemachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ModelEngine {
    public static final class Prototype {
        public final Map<String, double[]> centroids;
        public final int trainingCount;
        public final int featureCount;
        Prototype(Map<String, double[]> centroids, int trainingCount, int featureCount) {
            this.centroids = centroids;
            this.trainingCount = trainingCount;
            this.featureCount = featureCount;
        }
    }

    public static final class Evaluation {
        public final int total;
        public final int correct;
        public final int skipped;
        public final double accuracy;
        public final String details;
        Evaluation(int total, int correct, int skipped, String details) {
            this.total = total;
            this.correct = correct;
            this.skipped = skipped;
            this.accuracy = total == 0 ? 0 : correct / (double) total;
            this.details = details;
        }
    }

    public static Prototype build(List<Sample> samples) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int dims = 0;
        for (Sample s : samples) {
            if (s.label == null || s.label.trim().isEmpty() || s.features == null || s.features.length == 0) continue;
            dims = s.features.length;
            double[] sum = sums.get(s.label);
            if (sum == null) {
                sum = new double[dims];
                sums.put(s.label, sum);
                counts.put(s.label, 0);
            }
            for (int i = 0; i < dims; i++) sum[i] += s.features[i];
            counts.put(s.label, counts.get(s.label) + 1);
        }
        Map<String, double[]> centroids = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            int n = Math.max(1, counts.get(e.getKey()));
            double[] c = e.getValue().clone();
            for (int i = 0; i < c.length; i++) c[i] /= n;
            centroids.put(e.getKey(), c);
        }
        return new Prototype(centroids, samples.size(), dims);
    }

    public static String classify(Prototype p, double[] features) {
        if (p == null || p.centroids.isEmpty()) return null;
        String best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, double[]> e : p.centroids.entrySet()) {
            double d = distance(e.getValue(), features);
            if (d < bestD) { bestD = d; best = e.getKey(); }
        }
        return best;
    }

    public static Evaluation heldOut(List<Sample> samples) {
        if (samples.size() < 3) return new Evaluation(0, 0, 0, "Need at least 3 samples.");
        List<Sample> shuffled = new ArrayList<>(samples);
        Collections.shuffle(shuffled, new Random(684350L));
        int testN = Math.max(1, shuffled.size() / 3);
        List<Sample> test = new ArrayList<>(shuffled.subList(0, testN));
        List<Sample> train = new ArrayList<>(shuffled.subList(testN, shuffled.size()));
        return evaluate(build(train), test, "1/3 held out for testing, 2/3 used for training.");
    }

    public static Evaluation unseen(List<Sample> samples) {
        if (samples.size() < 6) return new Evaluation(0, 0, 0, "Need at least 6 chronological samples.");
        List<Sample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparingLong(s -> s.timestamp));
        int cut = Math.max(2, (int) Math.floor(ordered.size() * (2.0 / 3.0)));
        return evaluate(build(new ArrayList<>(ordered.subList(0, cut))),
                new ArrayList<>(ordered.subList(cut, ordered.size())),
                "Original model tested on later samples without retraining.");
    }

    public static String incrementalReport(List<Sample> samples) {
        if (samples.size() < 8) return "Need at least 8 samples for a useful four-stage incremental run.";
        List<Sample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparingLong(s -> s.timestamp));
        int initialN = Math.max(4, (int) Math.floor(ordered.size() * 0.66));
        int incomingN = ordered.size() - initialN;
        if (incomingN < 4) { initialN = ordered.size() - 4; incomingN = 4; }
        StringBuilder sb = new StringBuilder();
        sb.append("4-STAGE INCREMENTAL RETRAIN\nInitial set: ").append(initialN)
                .append("  Incoming: ").append(incomingN).append("\n\n");
        for (int stage = 1; stage <= 4; stage++) {
            int add = (int) Math.ceil(incomingN * (stage / 4.0));
            int total = Math.min(ordered.size(), initialN + add);
            List<Sample> stageData = new ArrayList<>(ordered.subList(0, total));
            Evaluation e = heldOut(stageData);
            sb.append("Stage ").append(stage).append("  n=").append(total)
                    .append("  labels=").append(build(stageData).centroids.size())
                    .append("  held-out=").append(percent(e.accuracy)).append("\n");
        }
        Evaluation originalOnNew = evaluate(build(new ArrayList<>(ordered.subList(0, initialN))),
                new ArrayList<>(ordered.subList(initialN, ordered.size())), "");
        sb.append("\nOriginal model on unseen incoming data: ").append(percent(originalOnNew.accuracy));
        return sb.toString();
    }

    public static Evaluation evaluate(Prototype p, List<Sample> test, String details) {
        int correct = 0, total = 0, skipped = 0;
        for (Sample s : test) {
            if (!p.centroids.containsKey(s.label)) { skipped++; continue; }
            String predicted = classify(p, s.features);
            total++;
            if (s.label.equals(predicted)) correct++;
        }
        return new Evaluation(total, correct, skipped, details);
    }

    public static String summary(Evaluation e) {
        if (e.total == 0) return e.details + (e.skipped > 0 ? "\nSkipped: " + e.skipped : "");
        return "Accuracy: " + percent(e.accuracy) + "\nCorrect: " + e.correct + "/" + e.total
                + (e.skipped > 0 ? "\nSkipped unseen labels: " + e.skipped : "")
                + (e.details == null || e.details.isEmpty() ? "" : "\n\n" + e.details);
    }

    private static String percent(double x) {
        return String.format(java.util.Locale.US, "%.1f%%", x * 100.0);
    }

    private static double distance(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double d = 0;
        for (int i = 0; i < n; i++) { double z = a[i] - b[i]; d += z * z; }
        return d / Math.max(1, n);
    }
}
