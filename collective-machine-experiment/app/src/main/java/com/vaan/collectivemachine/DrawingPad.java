package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class DrawingPad extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<float[]> points = new ArrayList<>();

    public DrawingPad(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(9, 12, 27));
        paint.setColor(Color.rgb(92, 241, 181));
        paint.setStrokeWidth(5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(Color.argb(45, 120, 145, 220));
        grid.setStrokeWidth(1f);
    }

    public void clear() {
        points.clear();
        path.reset();
        invalidate();
    }

    public boolean hasSignal() { return points.size() >= 8; }

    public double[] extractFeatures() {
        double[] f = new double[SignalEngine.FEATURES];
        if (points.size() < 2 || getWidth() <= 0 || getHeight() <= 0) return f;
        float w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        for (int i = 0; i < 20; i++) {
            double pos = i * (points.size() - 1.0) / 19.0;
            int a = (int) Math.floor(pos), b = Math.min(points.size() - 1, a + 1);
            double t = pos - a;
            double x = points.get(a)[0] * (1-t) + points.get(b)[0] * t;
            double y = points.get(a)[1] * (1-t) + points.get(b)[1] * t;
            f[i] = x / w;
            f[20 + i] = y / h;
        }
        double length = 0;
        float minX = w, maxX = 0, minY = h, maxY = 0;
        double turn = 0;
        for (int i = 0; i < points.size(); i++) {
            float[] p = points.get(i);
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            if (i > 0) {
                float[] q = points.get(i-1);
                length += Math.hypot(p[0]-q[0], p[1]-q[1]);
            }
            if (i > 1) {
                float[] a = points.get(i-2), b = points.get(i-1);
                double a1 = Math.atan2(b[1]-a[1], b[0]-a[0]);
                double a2 = Math.atan2(p[1]-b[1], p[0]-b[0]);
                double d = a2-a1;
                while (d > Math.PI) d -= Math.PI*2;
                while (d < -Math.PI) d += Math.PI*2;
                turn += Math.abs(d);
            }
        }
        f[40] = length / Math.max(1.0, Math.hypot(w, h));
        f[41] = minX / w; f[42] = maxX / w;
        f[43] = minY / h; f[44] = maxY / h;
        f[45] = (maxX-minX) / w;
        f[46] = (maxY-minY) / h;
        f[47] = turn / Math.max(1, points.size());
        f[48] = points.size() / 500.0;
        float[] first = points.get(0), last = points.get(points.size()-1);
        f[49] = Math.hypot(last[0]-first[0], last[1]-first[1]) / Math.max(1.0, Math.hypot(w, h));
        SignalEngine.normalize(f);
        return f;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        for (int i = 1; i < 4; i++) {
            canvas.drawLine(w*i/4f, 0, w*i/4f, h, grid);
            canvas.drawLine(0, h*i/4f, w, h*i/4f, grid);
        }
        canvas.drawPath(path, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            path.moveTo(x, y);
            points.add(new float[]{x,y});
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            path.lineTo(x, y);
            points.add(new float[]{x,y});
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            path.lineTo(x, y);
            points.add(new float[]{x,y});
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
