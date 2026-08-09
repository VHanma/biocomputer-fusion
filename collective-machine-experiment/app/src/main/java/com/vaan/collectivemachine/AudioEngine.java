package com.vaan.collectivemachine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.Arrays;

public final class AudioEngine {
    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_SAMPLES = 320;
    public static final int HOP_SAMPLES = 80;
    public static final int FFT_N = 512;
    public static final int BARK_BANDS = 24;

    public interface Callback {
        void onComplete(double[] features, double seconds);
        void onError(String message);
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static void capture(Context context, int seconds, Callback cb) {
        if (!hasPermission(context)) {
            cb.onError("Microphone permission is required.");
            return;
        }
        new Thread(() -> {
            AudioRecord record = null;
            try {
                int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bufferSize = Math.max(min, 4096);
                record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    cb.onError("Could not initialize the microphone.");
                    return;
                }
                int target = SAMPLE_RATE * seconds;
                short[] pcm = new short[target];
                short[] temp = new short[Math.max(1024, bufferSize / 2)];
                int offset = 0;
                record.startRecording();
                while (offset < target) {
                    int want = Math.min(temp.length, target - offset);
                    int got = record.read(temp, 0, want);
                    if (got < 0) throw new IllegalStateException("AudioRecord read error: " + got);
                    System.arraycopy(temp, 0, pcm, offset, got);
                    offset += got;
                }
                record.stop();
                cb.onComplete(extractFeatures(pcm), offset / (double) SAMPLE_RATE);
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "Audio capture failed." : e.getMessage());
            } finally {
                if (record != null) {
                    try { if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop(); } catch (Exception ignored) { }
                    record.release();
                }
            }
        }, "collective-audio").start();
    }

    public static double[] extractFeatures(short[] pcm) {
        if (pcm.length < FRAME_SAMPLES) return new double[BARK_BANDS * 2 + 2];
        int frames = 1 + (pcm.length - FRAME_SAMPLES) / HOP_SAMPLES;
        double[] sum = new double[BARK_BANDS];
        double[] sumSq = new double[BARK_BANDS];
        double rmsSum = 0;
        double zcrSum = 0;
        double[] re = new double[FFT_N];
        double[] im = new double[FFT_N];

        for (int frame = 0; frame < frames; frame++) {
            Arrays.fill(re, 0);
            Arrays.fill(im, 0);
            int base = frame * HOP_SAMPLES;
            double frameEnergy = 0;
            int crossings = 0;
            short prev = pcm[base];
            for (int i = 0; i < FRAME_SAMPLES; i++) {
                double x = pcm[base + i] / 32768.0;
                double w = 0.54 - 0.46 * Math.cos((2.0 * Math.PI * i) / (FRAME_SAMPLES - 1));
                re[i] = x * w;
                frameEnergy += x * x;
                short now = pcm[base + i];
                if (i > 0 && ((prev < 0 && now >= 0) || (prev >= 0 && now < 0))) crossings++;
                prev = now;
            }
            fft(re, im);
            double[] bands = new double[BARK_BANDS];
            for (int k = 1; k <= FFT_N / 2; k++) {
                double f = (k * SAMPLE_RATE) / (double) FFT_N;
                double bark = 13.0 * Math.atan(0.00076 * f) + 3.5 * Math.atan(Math.pow(f / 7500.0, 2));
                int b = Math.max(0, Math.min(BARK_BANDS - 1, (int) Math.floor(bark)));
                double power = re[k] * re[k] + im[k] * im[k];
                bands[b] += power;
            }
            for (int b = 0; b < BARK_BANDS; b++) {
                double v = Math.log1p(bands[b]);
                sum[b] += v;
                sumSq[b] += v * v;
            }
            rmsSum += Math.sqrt(frameEnergy / FRAME_SAMPLES);
            zcrSum += crossings / (double) FRAME_SAMPLES;
        }

        double[] out = new double[BARK_BANDS * 2 + 2];
        for (int b = 0; b < BARK_BANDS; b++) {
            double mean = sum[b] / frames;
            double variance = Math.max(0, sumSq[b] / frames - mean * mean);
            out[b] = mean;
            out[BARK_BANDS + b] = Math.sqrt(variance);
        }
        out[out.length - 2] = rmsSum / frames;
        out[out.length - 1] = zcrSum / frames;
        normalize(out);
        return out;
    }

    private static void normalize(double[] x) {
        double mean = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        double sum = 0;
        for (double v : x) sum += (v - mean) * (v - mean);
        double std = Math.sqrt(sum / x.length) + 1e-9;
        for (int i = 0; i < x.length; i++) x[i] = (x[i] - mean) / std;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wLenR = Math.cos(angle);
            double wLenI = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wr = 1, wi = 0;
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j;
                    int v = i + j + len / 2;
                    double vr = re[v] * wr - im[v] * wi;
                    double vi = re[v] * wi + im[v] * wr;
                    double ur = re[u], ui = im[u];
                    re[u] = ur + vr; im[u] = ui + vi;
                    re[v] = ur - vr; im[v] = ui - vi;
                    double nwr = wr * wLenR - wi * wLenI;
                    wi = wr * wLenI + wi * wLenR;
                    wr = nwr;
                }
            }
        }
    }
}
