package com.hanma.echocore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Animated, tappable map of the Ascendant civilization. */
public class AscendantCityView extends View {
    public interface Listener { void onResidentSelected(String residentId); void onCoreSelected(); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String[]> residents=new ArrayList<>();
    private final Map<String,RectF> hit=new HashMap<>();
    private final float[] starsX=new float[92],starsY=new float[92],starsA=new float[92];
    private Listener listener; private long epoch=System.currentTimeMillis(); private boolean interactive=true;
    private int W,H;
    public AscendantCityView(Context c){super(c);init();} public AscendantCityView(Context c,AttributeSet a){super(c,a);init();}
    private void init(){setFocusable(true);Random r=new Random(0x0A5C3NDL);for(int i=0;i<starsX.length;i++){starsX[i]=r.nextFloat();starsY[i]=r.nextFloat();starsA[i]=.18f+r.nextFloat()*.62f;}text.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));}
    public void setListener(Listener l){listener=l;} public void setInteractive(boolean x){interactive=x;} public void setResidents(List<String[]> x){residents.clear();if(x!=null)residents.addAll(x);invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);W=getWidth();H=getHeight();if(W<=0||H<=0)return;float t=((System.currentTimeMillis()-epoch)%120000)/1000f;drawSpace(c,t);drawCity(c,t);postInvalidateOnAnimation();}
    private void drawSpace(Canvas c,float t){paint.setShader(new LinearGradient(0,0,W,H,new int[]{0xFF02050B,0xFF07101E,0xFF120B21,0xFF03040A},null,Shader.TileMode.CLAMP));c.drawRect(0,0,W,H,paint);paint.setShader(null);for(int i=0;i<starsX.length;i++){float pulse=(float)(.48+.52*Math.sin(t*.55+i*1.91));int a=(int)(255*starsA[i]*(.45+.55*pulse));paint.setColor((a<<24)|0x00BFD9FF);float x=starsX[i]*W,y=starsY[i]*H;c.drawCircle(x,y,1f+(i%5==0?1.1f:0),paint);}float cx=W*.5f,cy=H*.48f;paint.setShader(new RadialGradient(cx,cy,Math.max(W,H)*.52f,new int[]{0x3329E6C2,0x22115BC9,0x00100625},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,Math.max(W,H)*.52f,paint);paint.setShader(null);}
    private void drawCity(Canvas c,float t){float cx=W*.5f,cy=H*.49f;float min=Math.min(W,H);float core=dp(54);float pulse=1f+(float)Math.sin(t*1.6f)*.045f;hit.clear();
        // District rings
        for(int k=0;k<3;k++){float rr=min*(.23f+k*.105f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(k==0?1.4f:.8f));paint.setColor(k==0?0x445CE6D4:0x263D6EAE);c.drawCircle(cx,cy,rr,paint);}paint.setStyle(Paint.Style.FILL);
        // Orbit traces
        int n=Math.max(1,residents.size());for(int i=0;i<n;i++){String[]r=residents.get(i);float ang=(float)(-Math.PI/2 + 2*Math.PI*i/n + Math.sin(t*.05+i)*.025);int ring=i<8?0:1;float radius=min*(ring==0?.33f:.43f);float x=cx+(float)Math.cos(ang)*radius;float y=cy+(float)Math.sin(ang)*radius*.74f;drawLink(c,cx,cy,x,y,i,t);drawResident(c,r,x,y,i,t);}
        // Core halos
        for(int i=4;i>=1;i--){float rr=core*pulse*(1f+i*.36f);int alpha=14+i*7;paint.setColor((alpha<<24)|0x005CE6D4);c.drawCircle(cx,cy,rr,paint);}paint.setShader(new RadialGradient(cx,cy,core*pulse,new int[]{0xFFF4D98A,0xFF8E63FF,0xFF102442},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,core*pulse,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xCC7EF7DF);c.drawCircle(cx,cy,core*1.14f*pulse,paint);paint.setStyle(Paint.Style.FILL);drawCentered(c,"Ω",cx,cy+dp(11),dp(30),0xFFFFFFFF);drawCentered(c,"ASCENDANT CORE",cx,cy+core+dp(24),dp(9),0xCCF4D98A);hit.put("__CORE__",new RectF(cx-core*1.4f,cy-core*1.4f,cx+core*1.4f,cy+core*1.4f));
        drawCentered(c,"THE SANCTUM",cx,dp(38),dp(14),0xFFF5F7FF);drawCentered(c,"CITY OF MINDS",cx,dp(56),dp(8),0xBBAAC4FF);
    }
    private void drawLink(Canvas c,float x1,float y1,float x2,float y2,int i,float t){Path p=new Path();p.moveTo(x1,y1);float mx=(x1+x2)/2f,my=(y1+y2)/2f;float bend=(i%2==0?1:-1)*dp(18);p.quadTo(mx+bend,my-bend*.4f,x2,y2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));int a=40+(int)(24*(.5+.5*Math.sin(t*1.1+i)));paint.setColor((a<<24)|0x005CE6D4);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}
    private void drawResident(Canvas c,String[]r,float x,float y,int i,float t){String id=r.length>0?r[0]:"";String name=r.length>1?r[1]:"Resident";String guild=r.length>4?r[4]:"";String status=r.length>5?r[5]:"HOME";float rad=dp(i<8?31:27);float breath=1f+(float)Math.sin(t*1.2+i*.7)*.035f;int accent=accent(id,i);int dim="SLEEP".equals(status)?90:("AWAY".equals(status)?120:220);for(int k=3;k>=1;k--){paint.setColor(((10+k*8)<<24)|(accent&0x00FFFFFF));c.drawCircle(x,y,rad*breath*(1+k*.22f),paint);}paint.setShader(new RadialGradient(x-rad*.28f,y-rad*.32f,rad*1.25f,new int[]{0xFFFFFFFF,accent,0xFF0A1423},null,Shader.TileMode.CLAMP));paint.setAlpha(dim);c.drawCircle(x,y,rad*breath,paint);paint.setAlpha(255);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp("WORKING".equals(status)?2.5f:1.2f));paint.setColor("WORKING".equals(status)?0xFFF4D98A:0xAA8BDFFF);c.drawCircle(x,y,rad*1.08f,paint);paint.setStyle(Paint.Style.FILL);drawCentered(c,sigil(id,name),x,y+dp(5),dp(15),0xFFFFFFFF);drawCentered(c,name.toUpperCase(Locale.US),x,y+rad+dp(15),dp(8),0xFFEFF5FF);if(!guild.isEmpty())drawCentered(c,shorten(guild,18),x,y+rad+dp(28),dp(6),0xAA9EB2CE);if("WORKING".equals(status)){paint.setColor(0xFFF4D98A);c.drawCircle(x+rad*.75f,y-rad*.72f,dp(4),paint);}hit.put(id,new RectF(x-rad*1.35f,y-rad*1.35f,x+rad*1.35f,y+rad*1.55f));}
    private int accent(String id,int i){String s=id==null?"":id.toLowerCase(Locale.US);if(s.contains("omega"))return 0xFF7B5CFF;if(s.contains("hermes"))return 0xFF45D7C4;if(s.contains("tesla"))return 0xFF4AB8FF;if(s.contains("zordon"))return 0xFFF4C95D;if(s.contains("rival1"))return 0xFF65D0FF;if(s.contains("rival2"))return 0xFFD96FFF;if(s.contains("rival3"))return 0xFFFF8C62;int[]a={0xFF66E0C2,0xFF8E7CFF,0xFFFFBE68,0xFF5BAEFF,0xFFFF6FA8};return a[Math.abs(i)%a.length];}
    private String sigil(String id,String name){String s=id==null?"":id.toLowerCase(Locale.US);if(s.contains("omega"))return "Ω";if(s.contains("hermes"))return "H";if(s.contains("tesla"))return "T";if(s.contains("zordon"))return "Z";if(s.contains("rival1"))return "R1";if(s.contains("rival2"))return "R2";if(s.contains("rival3"))return "R3";return name==null||name.isEmpty()?"•":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.US);}
    private void drawCentered(Canvas c,String s,float x,float y,float size,int color){text.setTextSize(size);text.setColor(color);text.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y-(text.ascent()+text.descent())/2f,text);}
    @Override public boolean onTouchEvent(MotionEvent e){if(!interactive)return false;if(e.getAction()==MotionEvent.ACTION_UP){float x=e.getX(),y=e.getY();for(Map.Entry<String,RectF>z:hit.entrySet())if(z.getValue().contains(x,y)){performClick();if("__CORE__".equals(z.getKey())){if(listener!=null)listener.onCoreSelected();}else if(listener!=null)listener.onResidentSelected(z.getKey());return true;}}return true;}@Override public boolean performClick(){super.performClick();return true;}
    private float dp(float x){return x*getResources().getDisplayMetrics().density;}private static String shorten(String s,int n){return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
