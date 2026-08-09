package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CollectiveView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Sample> samples = new ArrayList<>();
    private ModelEngine.Prototype prototype;
    private final long start = System.currentTimeMillis();

    public CollectiveView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(4, 6, 18));
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setData(List<Sample> samples, ModelEngine.Prototype prototype) {
        this.samples = new ArrayList<>(samples);
        this.prototype = prototype;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f + 18;
        float min = Math.min(w, h);
        double phase = (System.currentTimeMillis() - start) / 900.0;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(4, 6, 18)); c.drawRect(0, 0, w, h, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f);
        for (int r = 1; r <= 5; r++) {
            p.setColor(Color.argb(42, 88, 118, 255));
            c.drawCircle(cx, cy, min * (0.08f + r * 0.065f), p);
        }
        int shown = Math.min(220, samples.size());
        for (int i = 0; i < shown; i++) {
            Sample s = samples.get(samples.size() - 1 - i);
            int hash = s.id.hashCode();
            double a = ((hash & 0xffff) / 65535.0) * Math.PI * 2.0 + phase * 0.02;
            float rr = min * (0.26f + 0.19f * (((hash >>> 16) & 255) / 255f));
            float x = cx + (float) Math.cos(a) * rr;
            float y = cy + (float) Math.sin(a) * rr;
            float bend = min * 0.055f;
            float mx = (cx + x) / 2f + (float) Math.cos(a + Math.PI / 2) * bend;
            float my = (cy + y) / 2f + (float) Math.sin(a + Math.PI / 2) * bend;
            Path path = new Path(); path.moveTo(x, y); path.quadTo(mx, my, cx, cy);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f);
            p.setColor(Color.argb(36 + (i % 80), 100, 118, 255)); c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(180, 95, 112, 245)); c.drawCircle(x, y, 2.3f + (i % 3), p);
        }
        int labels = prototype == null ? 0 : prototype.centroids.size();
        int lobes = Math.max(3, Math.min(12, labels == 0 ? 5 : labels));
        float base = min * (0.055f + 0.008f * Math.min(8, labels));
        base *= 1.0f + 0.055f * (float) Math.sin(phase);
        Path core = new Path();
        for (int i = 0; i <= 180; i++) {
            double a = i / 180.0 * Math.PI * 2;
            double wobble = 1.0 + 0.20 * Math.sin(lobes * a + phase) + 0.07 * Math.sin((lobes + 3) * a - phase * 0.6);
            float r = (float) (base * wobble);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) core.moveTo(x, y); else core.lineTo(x, y);
        }
        core.close();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(205, 38, 235, 148)); c.drawPath(core, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(Color.argb(220, 100, 255, 190)); c.drawPath(core, p);
        text.setTextSize(25f); text.setColor(Color.rgb(232, 219, 101)); c.drawText("INNER VIEW", 24, 38, text);
        text.setTextSize(14f); text.setColor(Color.rgb(170, 192, 255));
        c.drawText("signals " + samples.size() + "   people " + countParticipants() + "   prototypes " + labels, 24, 62, text);
        c.drawText("20ms Hamming / 5ms hop / Bark features", 24, h - 24, text);
        postInvalidateDelayed(33);
    }

    private int countParticipants() {
        Set<String> s = new HashSet<>();
        for (Sample x : samples) s.add(x.participant);
        return s.size();
    }
}
