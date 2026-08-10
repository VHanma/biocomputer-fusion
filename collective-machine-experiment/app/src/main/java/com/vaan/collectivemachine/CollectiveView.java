package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class CollectiveView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Sample> roundSamples = new ArrayList<>();
    private CollectiveEngine.State state;
    private final long start = System.currentTimeMillis();

    public CollectiveView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(4, 6, 18));
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setData(List<Sample> all, CollectiveEngine.State state) {
        this.state = state;
        this.roundSamples = state == null ? new ArrayList<>() : CollectiveEngine.filter(all, state.session, state.round);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f + 18;
        float min = Math.min(w, h);
        double phase = (System.currentTimeMillis() - start) / 900.0;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(4, 6, 18)); c.drawRect(0, 0, w, h, p);

        double coherence = state == null ? 0 : state.coherence;
        double entropy = state == null ? 0 : state.entropy;
        double novelty = state == null ? 0 : state.novelty;
        for (int r = 1; r <= 5; r++) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f);
            int alpha = 28 + (int)(45 * coherence);
            p.setColor(Color.argb(alpha, 88, 118, 255));
            c.drawCircle(cx, cy, min * (0.07f + r * 0.065f), p);
        }

        int shown = Math.min(220, roundSamples.size());
        for (int i = 0; i < shown; i++) {
            Sample s = roundSamples.get(i);
            int hash = (s.participant + s.modality + s.id).hashCode();
            double a = ((hash & 0xffff) / 65535.0) * Math.PI * 2.0 + phase * 0.015;
            float influence = state == null ? 0.2f : (float) state.influence.getOrDefault(s.participant, 0.2);
            float rr = min * (0.28f + 0.16f * (((hash >>> 16) & 255) / 255f));
            float x = cx + (float) Math.cos(a) * rr;
            float y = cy + (float) Math.sin(a) * rr;
            float bend = min * (0.035f + 0.045f * influence);
            float mx = (cx + x) / 2f + (float) Math.cos(a + Math.PI / 2) * bend;
            float my = (cy + y) / 2f + (float) Math.sin(a + Math.PI / 2) * bend;
            Path path = new Path(); path.moveTo(x, y); path.quadTo(mx, my, cx, cy);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f + 2.3f * influence);
            int alpha = 50 + (int)(130 * coherence);
            p.setColor(Color.argb(alpha, 105, 127, 255)); c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(modalityColor(s.modality));
            c.drawCircle(x, y, 3f + 5f * influence, p);
        }

        double[] coreVec = state == null ? new double[SignalEngine.FEATURES] : state.core;
        int lobes = 5 + (int)Math.round(7 * novelty);
        float base = min * (0.060f + 0.038f * (float)coherence);
        Path core = new Path();
        for (int i = 0; i <= 220; i++) {
            double a = i / 220.0 * Math.PI * 2;
            double datum = coreVec[(i * 7) % coreVec.length];
            double wobble = 1.0 + (0.08 + 0.20 * entropy) * Math.sin(lobes * a + phase + datum * 0.2)
                    + 0.06 * Math.sin((lobes + 3) * a - phase * 0.6);
            float r = (float) (base * wobble);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) core.moveTo(x, y); else core.lineTo(x, y);
        }
        core.close();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(205, 38, 235, 148)); c.drawPath(core, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(Color.argb(230, 110, 255, 195)); c.drawPath(core, p);

        text.setTextSize(25f); text.setColor(Color.rgb(232, 219, 101)); c.drawText("INNER VIEW", 24, 38, text);
        text.setTextSize(13f); text.setColor(Color.rgb(170, 192, 255));
        if (state == null) {
            c.drawText("No collective state yet", 24, 62, text);
        } else {
            c.drawText("session " + state.session + "   round " + state.round + "   people " + state.participants, 24, 62, text);
            c.drawText("coherence " + pct(state.coherence) + "   entropy " + pct(state.entropy), 24, 82, text);
            c.drawText("novelty " + pct(state.novelty) + "   stability " + pct(state.stability), 24, 102, text);
        }
        c.drawText("node color = modality   node size = influence", 24, h - 24, text);
        postInvalidateDelayed(33);
    }

    private int modalityColor(String m) {
        if ("voice".equals(m)) return Color.rgb(102, 132, 255);
        if ("tap".equals(m)) return Color.rgb(255, 205, 100);
        if ("reaction".equals(m)) return Color.rgb(255, 125, 125);
        if ("text".equals(m)) return Color.rgb(188, 126, 255);
        if ("motion".equals(m)) return Color.rgb(86, 230, 223);
        if ("drawing".equals(m)) return Color.rgb(93, 241, 181);
        if ("feedback".equals(m)) return Color.rgb(255, 150, 220);
        return Color.LTGRAY;
    }

    private String pct(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100.0);
    }
}
