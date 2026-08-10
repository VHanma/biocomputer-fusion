package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class LainView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Sample> samples = new ArrayList<>();
    private LainState state = LainState.from(samples);

    public LainView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(3, 5, 12));
        grid.setColor(Color.argb(35, 90, 120, 170));
        grid.setStrokeWidth(1f);
        line.setColor(Color.argb(80, 80, 230, 180));
        line.setStrokeWidth(1.5f);
        node.setStyle(Paint.Style.FILL);
        corePaint.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f);
        text.setColor(Color.rgb(205, 225, 218));
        text.setTextSize(28f);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
    }

    public void setData(List<Sample> samples) {
        this.samples = samples == null ? new ArrayList<>() : new ArrayList<>(samples);
        this.state = LainState.from(this.samples);
        invalidate();
    }

    public LainState state() { return state; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        for (int i = 1; i < 8; i++) {
            float x = w * i / 8f;
            canvas.drawLine(x, 0, x, h, grid);
        }
        for (int i = 1; i < 10; i++) {
            float y = h * i / 10f;
            canvas.drawLine(0, y, w, y, grid);
        }

        float cx = w * 0.5f;
        float cy = h * 0.53f;
        float min = Math.min(w, h);
        long now = System.currentTimeMillis();
        double t = now / 1000.0;

        int n = Math.min(140, samples.size());
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(samples.size() - n + i);
            long hash = s.id == null ? i * 1009L : s.id.hashCode();
            double golden = 2.399963229728653;
            double angle = i * golden + (hash & 1023) * 0.0025;
            double cos = state.core.length == 0 ? 0 : SignalEngine.cosine(state.core, s.features);
            double similarity = Math.max(0, Math.min(1, (cos + 1) * 0.5));
            float radius = (float) (min * (0.19 + 0.25 * (1.0 - similarity)));
            radius += (float) (Math.sin(t * 0.7 + i * 0.37) * 5.0 * state.novelty);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius * 0.78f;

            int alpha = 45 + (int) (similarity * 95);
            line.setColor(Color.argb(alpha, 70, 235, 176));
            canvas.drawLine(x, y, cx, cy, line);

            int base = Math.floorMod((s.participant == null ? 0 : s.participant.hashCode()), 3);
            if (base == 0) node.setColor(Color.rgb(76, 240, 180));
            else if (base == 1) node.setColor(Color.rgb(126, 170, 255));
            else node.setColor(Color.rgb(195, 125, 255));
            float nr = 3.5f + (float) (similarity * 3.5);
            canvas.drawCircle(x, y, nr, node);
        }

        float pulse = (float) ((Math.sin(t * (1.2 + state.novelty * 2.2)) + 1.0) * 0.5);
        float coreR = (float) (min * (0.055 + state.coherence * 0.045) + pulse * 8f);

        int glowAlpha = 45 + (int) (state.coherence * 75);
        corePaint.setColor(Color.argb(glowAlpha, 40, 255, 150));
        canvas.drawCircle(cx, cy, coreR * 1.75f, corePaint);

        corePaint.setColor(Color.rgb(74, 244, 165));
        canvas.drawCircle(cx, cy, coreR, corePaint);

        corePaint.setColor(Color.rgb(200, 255, 226));
        canvas.drawCircle(cx - coreR * 0.18f, cy - coreR * 0.22f, coreR * 0.18f, corePaint);

        ring.setColor(Color.argb(120, 115, 245, 195));
        for (int i = 1; i <= 3; i++) {
            float rr = coreR * (1.8f + i * 0.65f) + (float) (Math.sin(t * 0.9 + i) * 5 * state.diversity);
            RectF oval = new RectF(cx - rr, cy - rr * 0.72f, cx + rr, cy + rr * 0.72f);
            canvas.drawOval(oval, ring);
        }

        text.setTextSize(Math.max(22f, min * 0.035f));
        text.setColor(Color.rgb(225, 239, 234));
        canvas.drawText("LAIN / INNER VIEW", 20f, 38f, text);
        text.setTextSize(Math.max(15f, min * 0.024f));
        text.setColor(Color.rgb(145, 175, 165));
        canvas.drawText(state.phase + "   signals " + state.sampleCount + "   people " + state.participantCount, 20f, 67f, text);
        canvas.drawText("coherence " + pct(state.coherence) + "   diversity " + pct(state.diversity) + "   novelty " + pct(state.novelty), 20f, 92f, text);

        postInvalidateDelayed(50);
    }

    private static String pct(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100.0);
    }
}
