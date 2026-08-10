package com.vaan.collectivemachine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

public final class FeedbackView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private CollectiveEngine.State state;
    private final long start = System.currentTimeMillis();

    public FeedbackView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(5, 7, 18));
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setState(CollectiveEngine.State state) {
        this.state = state;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w/2f, cy = h/2f;
        float min = Math.min(w,h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(5,7,18));
        c.drawRect(0,0,w,h,p);
        if (state == null || state.signals == 0) {
            text.setTextSize(18f); text.setColor(Color.rgb(170,185,220));
            c.drawText("NO ROUND CORE YET", 24, 40, text);
            return;
        }
        double phase = (System.currentTimeMillis()-start)/1200.0;
        double[] core = state.core;
        int spokes = 16;
        for (int ring=1; ring<=4; ring++) {
            float rr = min*(0.10f + ring*0.075f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(Color.argb(45, 94, 123, 230));
            c.drawCircle(cx,cy,rr,p);
        }
        Path glyph = new Path();
        for (int i=0; i<=spokes; i++) {
            int j = i % spokes;
            double a = j/(double)spokes*Math.PI*2.0 - Math.PI/2.0;
            double v = core[j % core.length];
            double v2 = core[(j*3+7)%core.length];
            float r = min*(0.13f + 0.045f*(float)Math.tanh(Math.abs(v)) + 0.055f*(float)state.coherence);
            r *= 1f + 0.035f*(float)Math.sin(phase + j*0.7 + v2);
            float x = cx + (float)Math.cos(a)*r;
            float y = cy + (float)Math.sin(a)*r;
            if (i==0) glyph.moveTo(x,y); else glyph.lineTo(x,y);
        }
        glyph.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(130, 45, 230, 154));
        c.drawPath(glyph,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(Color.rgb(105,255,195));
        c.drawPath(glyph,p);

        for (int i=0; i<spokes; i++) {
            double a = i/(double)spokes*Math.PI*2.0 - Math.PI/2.0;
            float outer = min*(0.31f + 0.05f*(float)state.entropy);
            float x = cx + (float)Math.cos(a)*outer;
            float y = cy + (float)Math.sin(a)*outer;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            int alpha = 45 + (int)(160*Math.min(1.0, Math.abs(core[(i*2)%core.length])/2.5));
            p.setColor(Color.argb(alpha, 110, 130, 255));
            c.drawLine(cx,cy,x,y,p);
        }
        text.setTextSize(20f); text.setColor(Color.rgb(235,220,102));
        c.drawText("COLLECTIVE FEEDBACK", 20, 34, text);
        text.setTextSize(13f); text.setColor(Color.rgb(177,196,236));
        c.drawText("session " + state.session + "  round " + state.round, 20, 56, text);
        c.drawText("coherence " + pct(state.coherence) + "  novelty " + pct(state.novelty), 20, h-24, text);
        postInvalidateDelayed(33);
    }

    private String pct(double x) {
        return String.format(java.util.Locale.US, "%.0f%%", Math.max(0,Math.min(1,x))*100.0);
    }
}
