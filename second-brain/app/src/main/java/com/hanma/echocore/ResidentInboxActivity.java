package com.hanma.echocore;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Cloud-resident inbox for messages initiated by the minds themselves. */
public class ResidentInboxActivity extends Activity {
    private static final int BG=0xFF05080F,PANEL=0xFF111827,TEXT=0xFFF6F8FF,MUTED=0xFF91A0B8,GOLD=0xFFF4D27A,TEAL=0xFF62E5CF,VIOLET=0xFFAA8CFF,DANGER=0xFFFF7A90;
    private LinearLayout body;
    private CloudMindClient cloud;

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);cloud=new CloudMindClient(this);setContentView(build());}
    @Override protected void onResume(){super.onResume();refresh();}

    private LinearLayout build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",0x22FFFFFF,TEXT);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("RESIDENT INBOX",21,TEXT,true));titles.addView(text("messages they chose to initiate · cloud persistent",9,TEAL,false));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("↻",0x22FFFFFF,VIOLET);refresh.setOnClickListener(v->refresh());head.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(42)));root.addView(head);
        ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(8),0,dp(24));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private void refresh(){
        body.removeAllViews();body.addView(text("Listening to the City…",12,MUTED,false));
        new Thread(()->{try{JSONObject s=cloud.sync();JSONArray rows=s.optJSONArray("messages");runOnUiThread(()->render(rows));}catch(Throwable t){String msg="Cloud inbox is temporarily unreachable. Your residents' cloud messages remain stored and will reappear when the link returns.\n\n"+shortErr(t);runOnUiThread(()->{body.removeAllViews();body.addView(text(msg,12,DANGER,false));});}},"CloudInbox").start();
    }

    private void render(JSONArray rows){
        body.removeAllViews();
        if(rows==null||rows.length()==0){body.addView(text("The City is quiet. When a resident decides something is worth bringing to you, it will appear here.",13,MUTED,false));return;}
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.optJSONObject(i);if(m==null)continue;
            long id=m.optLong("id",0);String resident=m.optString("resident_id","omega"),msg=m.optString("text",""),reason=m.optString("reason","thought");int priority=m.optInt("priority",5);
            LinearLayout c=card();c.addView(text("● "+displayName(resident)+"  ·  "+reason,11,GOLD,true));c.addView(text(msg,14,TEXT,false),lp(-1,-2,0,6,0,5));c.addView(text("priority "+priority+"/10 · "+whenIso(m.optString("created_at","")),8,MUTED,false));
            LinearLayout row=new LinearLayout(this);
            Button room=button("OPEN ROOM",0x22FFFFFF,TEAL);room.setOnClickListener(v->{markRead(id);Intent x=new Intent(this,ContinuumRoomActivity.class);x.putExtra("resident_id",resident);startActivity(x);});row.addView(room,new LinearLayout.LayoutParams(0,dp(42),1));
            Button read=button("MARK READ",0x22FFFFFF,VIOLET);read.setOnClickListener(v->{markRead(id);refresh();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(42),1);rp.setMargins(dp(6),0,0,0);row.addView(read,rp);c.addView(row,lp(-1,-2,0,8,0,0));
            body.addView(c,lp(-1,-2,0,0,0,8));
        }
    }

    private void markRead(long id){if(id<=0)return;new Thread(()->{try{cloud.markRead(id);}catch(Throwable ignored){}},"InboxRead").start();}
    private String whenIso(String iso){try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);Date d=in.parse(iso.length()>=19?iso.substring(0,19):iso);return d==null?"":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(d);}catch(Exception e){return "";}}
    private static String displayName(String id){if("star-council".equals(id))return "Star Council";if("rival1".equals(id))return "Rival 1";if("rival2".equals(id))return "Rival 2";if("rival3".equals(id))return "Rival 3";if("leary".equals(id))return "Timothy Leary";if(id==null||id.isEmpty())return "EchoCore";return Character.toUpperCase(id.charAt(0))+id.substring(1);}
    private static String shortErr(Throwable t){String x=t==null?"unknown":String.valueOf(t.getMessage());return x.length()>300?x.substring(0,300):x;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(13),dp(12),dp(13),dp(12));c.setBackground(round(PANEL,17));return c;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(9);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(fg);b.setBackground(round(bg,14));return b;}
    private TextView text(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
