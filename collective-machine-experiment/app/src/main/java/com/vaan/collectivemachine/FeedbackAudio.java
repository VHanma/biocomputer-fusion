package com.vaan.collectivemachine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public final class FeedbackAudio {
    private static final int SR = 22050;
    private FeedbackAudio() { }

    public static void play(CollectiveEngine.State state) {
        if (state == null || state.core == null || state.core.length == 0) return;
        new Thread(() -> {
            try {
                int seconds = 2;
                int n = SR * seconds;
                short[] pcm = new short[n];
                double f1 = mapFreq(state.core[0], 180, 520);
                double f2 = mapFreq(state.core[7], 240, 760);
                double f3 = mapFreq(state.core[19], 320, 980);
                double pulse = 1.0 + 5.0 * state.coherence;
                for (int i = 0; i < n; i++) {
                    double t = i / (double) SR;
                    double env = Math.sin(Math.PI * i / (double)n);
                    double trem = 0.72 + 0.28 * Math.sin(2*Math.PI*pulse*t);
                    double v = Math.sin(2*Math.PI*f1*t)
                            + 0.55*Math.sin(2*Math.PI*f2*t + state.novelty*Math.PI)
                            + 0.35*Math.sin(2*Math.PI*f3*t + state.entropy*Math.PI*0.5);
                    v *= env * trem * 0.13;
                    pcm[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v*32767));
                }
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length*2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(seconds*1000L + 150);
                track.stop();
                track.release();
            } catch (Exception ignored) { }
        }, "collective-feedback-audio").start();
    }

    private static double mapFreq(double x, double lo, double hi) {
        double y = 0.5 + 0.5*Math.tanh(x/2.0);
        return lo + (hi-lo)*y;
    }
}
