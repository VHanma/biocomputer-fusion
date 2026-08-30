package com.hanma.echocore;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CloudLinkActivity extends Activity {
    private static final int BG=0xFF07090D,PANEL=0xFF111722,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B;
    private SecurePrefs prefs; private LinearLayout body;
    @Override protected void onCreate(Bundle s){super.onCreate(s);prefs=new SecurePrefs(this);getWindow().setStatusBarColor(BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(20),dp(16),dp(20));body.setBackgroundColor(BG);setContentView(body);autoStart();render();}
    @Override protected void onResume(){super.onResume();render();}
    private void autoStart(){prefs.putBool(CloudLinkService.KEY_ENABLED,true);Intent i=new Intent(this,CloudLinkService.class);try{if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception ignored){}}
    private void render(){body.removeAllViews();body.addView(text("ECHOCORE Ω AUTOLINK",25,TEXT,true));body.addView(text("ONE-TAP CLOUD BRIDGE",11,ACCENT2,true),lp(-1,-2,0,3,0,18));String id=prefs.get(CloudLinkService.KEY_DEVICE_ID,"");boolean live=prefs.getBool(CloudLinkService.KEY_REGISTERED,false)&&prefs.getBool(CloudLinkService.KEY_ENABLED,true);LinearLayout c=card();c.addView(text(live?"AUTOLINK · LIVE":"AUTOLINK · CONNECTING",14,live?ACCENT2:WARM,true));c.addView(text(live?"This phone is registered with the EchoCore relay.":"No Dropbox, Drive mailbox, endpoint, token copy, or port setup required.",12,TEXT,false),lp(-1,-2,0,6,0,3));if(!id.isEmpty())c.addView(text("Device: "+shortId(id),9,MUTED,false));body.addView(c,lp(-1,-2,0,0,0,12));Button retry=button(live?"RECONNECT / REFRESH":"CONNECT NOW",ACCENT,BG);retry.setOnClickListener(v->{prefs.putBool(CloudLinkService.KEY_ENABLED,true);prefs.putBool(CloudLinkService.KEY_FORCE_REGISTER,id.isEmpty());autoStart();Toast.makeText(this,"AutoLink connecting…",Toast.LENGTH_SHORT).show();});body.addView(retry,lp(-1,dp(52),0,0,0,8));Button omega=button("OPEN OMEGA BRAIN",PANEL,ACCENT2);omega.setOnClickListener(v->startActivity(new Intent(this,CognitiveOSActivity.class)));body.addView(omega,lp(-1,dp(52),0,0,0,12));body.addView(text("AutoLink creates its own encrypted device credential and polls the public EchoCore relay over HTTPS. The secret stays inside Android encrypted preferences. The relay only carries commands and responses addressed to this device.",12,MUTED,false));}
    private String shortId(String s){return s.length()>12?s.substring(0,6)+"…"+s.substring(s.length()-6):s;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(12),dp(12),dp(12));x.setBackground(round(PANEL,14));return x;}
    private TextView text(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,b?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0,1.12f);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(round(bg,12));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
