package com.vaan.collectivemachine;

import java.util.ArrayList;
import java.util.List;

public final class SignalEngine {
    public static final int FEATURES = 50;

    private SignalEngine() { }

    public static double[] fit(double[] src) {
        double[] out = new double[FEATURES];
        if (src == null || src.length == 0) return out;
        if (src.length == FEATURES) {
            System.arraycopy(src, 0, out, 0, FEATURES);
            return out;
        }
        if (src.length == 1) {
            for (int i = 0; i < FEATURES; i++) out[i] = src[0];
            normalize(out);
            return out;
        }
        for (int i = 0; i < FEATURES; i++) {
            double pos = i * (src.length - 1.0) / (FEATURES - 1.0);
            int a = (int) Math.floor(pos);
            int b = Math.min(src.length - 1, a + 1);
            double t = pos - a;
            out[i] = src[a] * (1.0 - t) + src[b] * t;
        }
        normalize(out);
        return out;
    }

    public static double[] textFeatures(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        double[] f = new double[FEATURES];
        if (s.isEmpty()) return f;
        int letters = 0, digits = 0, spaces = 0, punctuation = 0, vowels = 0;
        int words = s.split("\\s+").length;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else if (Character.isWhitespace(c)) spaces++;
            else punctuation++;
            if ("aeiou".indexOf(c) >= 0) vowels++;
            int h1 = Math.floorMod((c * 31) + i * 17, 32);
            f[h1] += 1.0;
            if (i + 1 < s.length()) {
                int h2 = Math.floorMod((c * 131) + s.charAt(i + 1) * 17, 32);
                f[h2] += 0.6;
            }
        }
        double n = Math.max(1.0, s.length());
        for (int i = 0; i < 32; i++) f[i] /= n;
        f[32] = Math.log1p(s.length());
        f[33] = Math.log1p(words);
        f[34] = letters / n;
        f[35] = digits / n;
        f[36] = spaces / n;
        f[37] = punctuation / n;
        f[38] = vowels / Math.max(1.0, letters);
        f[39] = words == 0 ? 0 : letters / (double) words;
        for (int i = 40; i < FEATURES; i++) {
            double phase = (i - 39) * 0.37;
            f[i] = Math.sin(s.hashCode() * 0.0001 + phase) * 0.25 +
                    Math.cos(s.length() * 0.13 + phase * 0.7) * 0.15;
        }
        normalize(f);
        return f;
    }

    public static double[] tapFeatures(List<Long> tapTimesMs) {
        double[] f = new double[FEATURES];
        if (tapTimesMs == null || tapTimesMs.size() < 3) return f;
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < tapTimesMs.size(); i++) {
            intervals.add(Math.max(1.0, tapTimesMs.get(i) - tapTimesMs.get(i - 1)));
        }
        double mean = mean(intervals);
        double sd = sd(intervals, mean);
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (double v : intervals) { min = Math.min(min, v); max = Math.max(max, v); }
        for (int i = 0; i < 24; i++) {
            double pos = i * (intervals.size() - 1.0) / 23.0;
            int a = (int) Math.floor(pos), b = Math.min(intervals.size() - 1, a + 1);
            double t = pos - a;
            double v = intervals.get(a) * (1 - t) + intervals.get(b) * t;
            f[i] = (v - mean) / (sd + 1e-6);
        }
        f[24] = Math.log1p(mean);
        f[25] = Math.log1p(sd);
        f[26] = sd / Math.max(1.0, mean);
        f[27] = Math.log1p(min);
        f[28] = Math.log1p(max);
        f[29] = Math.log1p(max - min);
        for (int lag = 1; lag <= 10; lag++) f[29 + lag] = autocorrelation(intervals, lag);
        for (int i = 40; i < FEATURES; i++) {
            double k = i - 39;
            f[i] = Math.sin(k * mean * 0.003) + 0.5 * Math.cos(k * sd * 0.004);
        }
        normalize(f);
        return f;
    }

    public static double[] reactionFeatures(long reactionMs) {
        double[] f = new double[FEATURES];
        double x = Math.max(50.0, Math.min(3000.0, reactionMs));
        double z = (Math.log(x) - Math.log(300.0));
        for (int i = 0; i < FEATURES; i++) {
            double k = i + 1.0;
            f[i] = Math.sin(z * k * 0.7) + 0.5 * Math.cos(z * k * 0.31) +
                    0.15 * Math.sin((x / 1000.0) * k);
        }
        normalize(f);
        return f;
    }

    public static double[] motionFeatures(List<double[]> xyz) {
        double[] f = new double[FEATURES];
        if (xyz == null || xyz.size() < 8) return f;
        double[] mean = new double[4], sq = new double[4], max = new double[4];
        for (double[] p : xyz) {
            double mag = Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]);
            double[] v = {p[0], p[1], p[2], mag};
            for (int j = 0; j < 4; j++) {
                mean[j] += v[j];
                sq[j] += v[j] * v[j];
                max[j] = Math.max(max[j], Math.abs(v[j]));
            }
        }
        int n = xyz.size();
        int idx = 0;
        for (int j = 0; j < 4; j++) {
            mean[j] /= n;
            double variance = Math.max(0, sq[j] / n - mean[j] * mean[j]);
            f[idx++] = mean[j];
            f[idx++] = Math.sqrt(variance);
            f[idx++] = max[j];
        }
        int bins = 19;
        for (int b = 0; b < bins; b++) {
            int from = b * n / bins;
            int to = Math.max(from + 1, (b + 1) * n / bins);
            to = Math.min(n, to);
            double sum = 0;
            for (int i = from; i < to; i++) {
                double[] p = xyz.get(i);
                sum += Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]);
            }
            f[idx++] = sum / Math.max(1, to - from);
        }
        for (int lag = 1; idx < FEATURES; lag++) {
            f[idx++] = motionAutocorrelation(xyz, lag);
        }
        normalize(f);
        return f;
    }

    public static double[] feedbackFeatures(String response, double[] core) {
        double code = "MATCH".equals(response) ? 1.0 : ("DIFFERENT".equals(response) ? -1.0 : 0.0);
        double[] f = new double[FEATURES];
        double[] c = fit(core);
        f[0] = code;
        f[1] = "NEUTRAL".equals(response) ? 1.0 : 0.0;
        f[2] = "MATCH".equals(response) ? 1.0 : 0.0;
        f[3] = "DIFFERENT".equals(response) ? 1.0 : 0.0;
        for (int i = 4; i < FEATURES; i++) f[i] = c[i] * (0.5 + 0.5 * Math.abs(code)) + code * 0.15;
        normalize(f);
        return f;
    }

    public static void normalize(double[] x) {
        if (x == null || x.length == 0) return;
        double mean = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        double sum = 0;
        for (double v : x) sum += (v - mean) * (v - mean);
        double sd = Math.sqrt(sum / x.length);
        if (sd < 1e-9) return;
        for (int i = 0; i < x.length; i++) x[i] = (x[i] - mean) / sd;
    }

    public static double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i];
        }
        if (aa < 1e-12 || bb < 1e-12) return 0;
        return dot / Math.sqrt(aa * bb);
    }

    public static double distance(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        if (n == 0) return 0;
        double d = 0;
        for (int i = 0; i < n; i++) { double z = a[i] - b[i]; d += z * z; }
        return Math.sqrt(d / n);
    }

    private static double mean(List<Double> v) {
        double s = 0; for (double x : v) s += x; return s / Math.max(1, v.size());
    }

    private static double sd(List<Double> v, double mean) {
        double s = 0; for (double x : v) { double z = x - mean; s += z*z; }
        return Math.sqrt(s / Math.max(1, v.size()));
    }

    private static double autocorrelation(List<Double> v, int lag) {
        if (v.size() <= lag + 1) return 0;
        double m = mean(v), num = 0, den = 0;
        for (int i = 0; i < v.size(); i++) {
            double a = v.get(i) - m;
            den += a * a;
            if (i + lag < v.size()) num += a * (v.get(i + lag) - m);
        }
        return den < 1e-9 ? 0 : num / den;
    }

    private static double motionAutocorrelation(List<double[]> xyz, int lag) {
        if (xyz.size() <= lag + 2) return 0;
        double mean = 0;
        for (double[] p : xyz) mean += Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]);
        mean /= xyz.size();
        double num = 0, den = 0;
        for (int i = 0; i < xyz.size(); i++) {
            double[] p = xyz.get(i);
            double a = Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]) - mean;
            den += a*a;
            if (i + lag < xyz.size()) {
                double[] q = xyz.get(i + lag);
                double b = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2]) - mean;
                num += a*b;
            }
        }
        return den < 1e-9 ? 0 : num / den;
    }
}
