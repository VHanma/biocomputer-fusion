package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Data-driven Inner View: people -> memories/identity -> Physical/Wired/Other core. */
public final class LainNetworkView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Sample> samples = new ArrayList<>();
    private List<IdentityGraph.Claim> claims = new ArrayList<>();
    private List<MemoryGraph.MemoryNode> memories = new ArrayList<>();
    private Protocol7.SelfModel self;
    private String localNode = "";
    private long start = System.currentTimeMillis();

    public LainNetworkView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(2, 4, 10));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.2f);
        node.setStyle(Paint.Style.FILL);
        text.setColor(Color.rgb(178, 197, 205));
        text.setTextSize(24f);
        halo.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Sample> samples, Protocol7.SelfModel self,
                        IdentityGraph identity, MemoryGraph memoryGraph) {
        this.samples = samples == null ? new ArrayList<>() : new ArrayList<>(samples);
        this.self = self;
        this.claims = identity == null ? new ArrayList<>() : identity.snapshot();
        this.localNode = identity == null ? "" : identity.nodeId();
        this.memories = memoryGraph == null ? new ArrayList<>() : memoryGraph.snapshot();
        start = System.currentTimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float min = Math.min(w, h);
        double t = (System.currentTimeMillis() - start) / 1000.0;
        Protocol7.SelfModel s = self;
        if (s == null) return;

        float pulse = (float) (0.5 + 0.5 * Math.sin(t * (0.8 + s.ambiguity * 2.4)));
        halo.setColor(Color.argb((int) (18 + 30 * s.wiredStrength), 75, 130, 230));
        c.drawCircle(cx, cy, min * (0.28f + 0.035f * pulse), halo);
        halo.setColor(Color.argb((int) (10 + 30 * s.collectiveLink), 80, 240, 180));
        c.drawCircle(cx, cy, min * (0.18f + 0.025f * pulse), halo);

        drawSignalRing(c, cx, cy, min * 0.45f);
        drawMemoryRing(c, cx, cy, min * 0.34f);
        drawIdentityRing(c, cx, cy, min * 0.25f);
        drawCore(c, cx, cy, min, t);

        text.setTextSize(Math.max(18f, min * 0.025f));
        text.setColor(Color.rgb(105, 235, 182));
        c.drawText("INNER VIEW / " + s.phase, 18, 32, text);
        text.setColor(Color.rgb(155, 170, 185));
        c.drawText("P " + pct(s.localIdentity) + "   W " + pct(s.wiredStrength) +
                "   O " + pct(s.fragmentation), 18, h - 20, text);

        postInvalidateDelayed(40);
    }

    private void drawSignalRing(Canvas c, float cx, float cy, float r) {
        int max = Math.min(72, samples.size());
        int from = Math.max(0, samples.size() - max);
        for (int i = from; i < samples.size(); i++) {
            Sample s = samples.get(i);
            int idx = i - from;
            double a = 2 * Math.PI * idx / Math.max(1, max) + hashUnit(s.id) * 0.35;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            int col = modalityColor(s.modality);
            node.setColor(col);
            c.drawCircle(x, y, 3.2f, node);
            line.setColor(withAlpha(col, 72));
            line.setStrokeWidth(0.8f + (float) Math.min(2.0, Math.abs(s.features.length > 0 ? s.features[0] : 0) * 0.4));
            Path p = new Path();
            p.moveTo(x, y);
            float bend = (float) ((hashUnit(s.id + "b") - 0.5) * r * 0.35);
            p.quadTo((x + cx) / 2f + bend, (y + cy) / 2f - bend, cx, cy);
            c.drawPath(p, line);
        }
    }

    private void drawMemoryRing(Canvas c, float cx, float cy, float r) {
        int max = Math.min(36, memories.size());
        int from = Math.max(0, memories.size() - max);
        for (int i = from; i < memories.size(); i++) {
            MemoryGraph.MemoryNode m = memories.get(i);
            double a = 2 * Math.PI * (i - from) / Math.max(1, max) + 0.25;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            boolean remote = m.originNode != null && !m.originNode.equals(localNode);
            node.setColor(remote ? Color.rgb(180, 128, 245) : Color.rgb(85, 205, 230));
            c.drawCircle(x, y, 4.2f, node);
            line.setColor(Color.argb(70, 170, 140, 245));
            c.drawLine(x, y, cx, cy, line);
        }
    }

    private void drawIdentityRing(Canvas c, float cx, float cy, float r) {
        int max = Math.min(32, claims.size());
        int from = Math.max(0, claims.size() - max);
        for (int i = from; i < claims.size(); i++) {
            IdentityGraph.Claim claim = claims.get(i);
            double a = 2 * Math.PI * (i - from) / Math.max(1, max) - 0.31;
            float offset = claim.polarity == 0 ? r * 0.07f : claim.polarity < 0 ? r * 0.13f : 0;
            float x = cx + (float) Math.cos(a) * (r + offset);
            float y = cy + (float) Math.sin(a) * (r + offset);
            int col = claim.polarity > 0 ? Color.rgb(95, 238, 174) :
                    claim.polarity < 0 ? Color.rgb(245, 105, 145) : Color.rgb(230, 200, 100);
            node.setColor(col);
            c.drawCircle(x, y, claim.polarity == 0 ? 3.5f : 4.7f, node);
            line.setColor(withAlpha(col, 82));
            c.drawLine(x, y, cx, cy, line);
        }
    }

    private void drawCore(Canvas c, float cx, float cy, float min, double t) {
        float baseR = min * (0.055f + 0.035f * (float) self.continuity);
        float split = min * 0.07f * (float) self.fragmentation;
        float wobble = min * 0.006f * (float) Math.sin(t * (1.1 + self.ambiguity * 2));

        halo.setColor(Color.argb(190, 70, 235, 168));
        c.drawCircle(cx - split + wobble, cy + split * 0.25f, baseR * (0.72f + 0.38f * (float) self.localIdentity), halo);

        halo.setColor(Color.argb(175, 94, 145, 255));
        c.drawCircle(cx + split, cy + split * 0.18f - wobble, baseR * (0.68f + 0.42f * (float) self.wiredStrength), halo);

        halo.setColor(Color.argb((int) (90 + 140 * self.fragmentation), 230, 102, 215));
        c.drawCircle(cx, cy - split - wobble, baseR * (0.55f + 0.55f * (float) self.fragmentation), halo);

        halo.setColor(Color.argb(220, 218, 250, 235));
        c.drawCircle(cx, cy, Math.max(2.5f, baseR * 0.16f * (float) self.memoryCoherence), halo);
    }

    private static int modalityColor(String modality) {
        String m = modality == null ? "" : modality.toLowerCase(java.util.Locale.US);
        if (m.contains("voice")) return Color.rgb(75, 235, 175);
        if (m.contains("text")) return Color.rgb(120, 175, 255);
        if (m.contains("embodiment") || m.contains("motion")) return Color.rgb(245, 190, 90);
        if (m.contains("draw")) return Color.rgb(205, 120, 245);
        if (m.contains("tap")) return Color.rgb(95, 220, 235);
        return Color.rgb(180, 190, 200);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static double hashUnit(String s) {
        int h = s == null ? 0 : s.hashCode();
        return (Math.floorMod(h, 10000)) / 9999.0;
    }

    private static String pct(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100);
    }
}
