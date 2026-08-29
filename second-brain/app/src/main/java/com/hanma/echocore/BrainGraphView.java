package com.hanma.echocore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BrainGraphView extends View {
    private final BrainDatabase db;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<PointNode> points = new ArrayList<>();

    static class PointNode {
        MemoryNode memory;
        float x, y, r;
        PointNode(MemoryNode memory, float x, float y, float r) {
            this.memory = memory; this.x = x; this.y = y; this.r = r;
        }
    }

    public BrainGraphView(Context context, BrainDatabase db) {
        super(context);
        this.db = db;
        setMinimumHeight((int) dp(430));
        setBackgroundColor(0xFF090B10);
        linePaint.setColor(0x337C9CFF);
        linePaint.setStrokeWidth(dp(1));
        nodePaint.setColor(0xFF7C9CFF);
        textPaint.setColor(0xFFE8EEFF);
        textPaint.setTextSize(dp(11));
    }

    public void refresh() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<MemoryNode> nodes = db.recent(32);
        points.clear();
        if (nodes.isEmpty()) {
            textPaint.setTextSize(dp(15));
            textPaint.setColor(0xFF98A4BA);
            canvas.drawText("Capture memories and the constellation grows here.", dp(22), dp(55), textPaint);
            return;
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxR = Math.max(dp(80), Math.min(getWidth(), getHeight()) * 0.40f);
        for (int i = 0; i < nodes.size(); i++) {
            MemoryNode m = nodes.get(i);
            double angle = i * 2.399963229728653;
            float radius = maxR * (float)Math.sqrt((i + 1f) / (nodes.size() + 2f));
            float wobble = ((m.id * 37) % 17 - 8) * dp(1.2f);
            float x = cx + (float)Math.cos(angle) * radius + wobble;
            float y = cy + (float)Math.sin(angle) * radius - wobble * .5f;
            float r = dp(6 + m.importance * .75f + (m.pinned ? 3 : 0));
            x = Math.max(r + dp(8), Math.min(getWidth() - r - dp(8), x));
            y = Math.max(r + dp(20), Math.min(getHeight() - r - dp(20), y));
            points.add(new PointNode(m, x, y, r));
        }

        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                int score = BrainEngine.sharedWordScore(points.get(i).memory, points.get(j).memory);
                if (score > 0 && (score > 1 || j - i < 7)) {
                    linePaint.setAlpha(Math.min(150, 38 + score * 30));
                    linePaint.setStrokeWidth(dp(score > 1 ? 1.6f : .8f));
                    canvas.drawLine(points.get(i).x, points.get(i).y, points.get(j).x, points.get(j).y, linePaint);
                }
            }
        }

        for (PointNode p : points) {
            nodePaint.setColor(colorForType(p.memory.type));
            nodePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(p.x, p.y, p.r, nodePaint);
            nodePaint.setStyle(Paint.Style.STROKE);
            nodePaint.setStrokeWidth(dp(1.5f));
            nodePaint.setColor(0xAAFFFFFF);
            canvas.drawCircle(p.x, p.y, p.r + dp(2), nodePaint);

            String label = p.memory.text.replace('\n', ' ').trim();
            if (label.length() > 18) label = label.substring(0, 17) + "…";
            textPaint.setTextSize(dp(9.5f));
            textPaint.setColor(0xFFCBD5E8);
            float tw = textPaint.measureText(label);
            canvas.drawText(label, Math.max(dp(4), Math.min(getWidth() - tw - dp(4), p.x - tw / 2f)), p.y + p.r + dp(16), textPaint);
        }
    }

    private int colorForType(String type) {
        if ("FOCUS".equals(type)) return 0xFFFFB86B;
        if ("IDEA".equals(type)) return 0xFF56E0C5;
        if ("INSIGHT".equals(type)) return 0xFFC28BFF;
        if ("QUESTION".equals(type)) return 0xFFFF7A90;
        if ("REFERENCE".equals(type)) return 0xFF9BC6FF;
        return 0xFF7C9CFF;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX(), y = event.getY();
            PointNode best = null;
            float bestD = Float.MAX_VALUE;
            for (PointNode p : points) {
                float dx = p.x - x, dy = p.y - y;
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = p; }
            }
            if (best != null && bestD < dp(45) * dp(45)) {
                Toast.makeText(getContext(), best.memory.type + " · " + best.memory.text, Toast.LENGTH_LONG).show();
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
