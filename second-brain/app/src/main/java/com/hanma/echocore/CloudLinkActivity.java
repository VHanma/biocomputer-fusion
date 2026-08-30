package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class CloudLinkActivity extends Activity {
    private static final int REQ_FILE=9901;
    private static final int BG=0xFF07090D,PANEL=0xFF111722,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B;
    private SecurePrefs prefs;
    private LinearLayout body;

    @Override protected void onCreate(Bundle state){super.onCreate(state);prefs=new SecurePrefs(this);getWindow().setStatusBarColor(BG);setContentView(shell());render();}
    @Override protected void onResume(){super.onResume();if(body!=null)render();}

    private LinearLayout shell(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(16),dp(20),dp(16),dp(20));body=root;return root;}
    private void render(){body.removeAllViews();body.addView(text("ECHOCORE Ω LINK",25,TEXT,true));body.addView(text("CLOUDLINK BRIDGE · shared brain mailbox",11,ACCENT2,true),lp(-1,-2,0,3,0,18));
        String uri=prefs.get(CloudLinkService.KEY_URI,"");boolean enabled=prefs.getBool(CloudLinkService.KEY_ENABLED,false);String mid=prefs.get(CloudLinkService.KEY_MAILBOX,"");
        LinearLayout card=card();card.addView(text(enabled?"CLOUDLINK · LIVE":"CLOUDLINK · OFF",13,enabled?ACCENT2:WARM,true));card.addView(text(uri.isEmpty()?"No cloud mailbox selected":"Mailbox ready",12,TEXT,false),lp(-1,-2,0,6,0,2));if(!mid.isEmpty())card.addView(text("Mailbox ID: "+mid,9,MUTED,false));body.addView(card,lp(-1,-2,0,0,0,12));
        Button choose=button(uri.isEmpty()?"CREATE CLOUD MAILBOX":"CHANGE CLOUD MAILBOX",PANEL,ACCENT);choose.setOnClickListener(v->createMailbox());body.addView(choose,lp(-1,dp(52),0,0,0,8));
        Button toggle=button(enabled?"STOP LIVE SYNC":"START LIVE SYNC",enabled?0xFF2B1820:ACCENT,enabled?WARM:BG);toggle.setOnClickListener(v->toggleSync(!enabled));body.addView(toggle,lp(-1,dp(52),0,0,0,8));
        Button omega=button("OPEN OMEGA BRAIN",PANEL,ACCENT2);omega.setOnClickListener(v->startActivity(new Intent(this,CognitiveOSActivity.class)));body.addView(omega,lp(-1,dp(52),0,0,0,14));
        body.addView(text("How to link with ChatGPT: create the mailbox and save it inside Dropbox, Google Drive, OneDrive, or another Android document provider. Keep Live Sync on. The same JSON file becomes the shared command/response channel. No cloud password or API key is stored in EchoCore.",12,MUTED,false));
    }

    private void createMailbox(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"EchoCore-CloudLink-Mailbox.json");startActivityForResult(i,REQ_FILE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_FILE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}try{JSONObject box=new JSONObject();String id=UUID.randomUUID().toString();box.put("protocol","echocore-cloudlink-v1");box.put("mailbox_id",id);box.put("revision",1);box.put("created_at",System.currentTimeMillis());box.put("updated_at",System.currentTimeMillis());box.put("device",new JSONObject().put("status","ready").put("last_seen",0).put("app_version","6.0.0").put("capabilities",new JSONArray()));box.put("commands",new JSONArray());box.put("responses",new JSONArray());try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception("Mailbox not writable");out.write(box.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}prefs.put(CloudLinkService.KEY_URI,uri.toString());prefs.put(CloudLinkService.KEY_MAILBOX,id);Toast.makeText(this,"CloudLink mailbox created",Toast.LENGTH_SHORT).show();render();}catch(Exception e){new AlertDialog.Builder(this).setTitle("Mailbox error").setMessage(e.getMessage()).setPositiveButton("Close",null).show();}}
    private void toggleSync(boolean on){if(on&&prefs.get(CloudLinkService.KEY_URI,"").isEmpty()){Toast.makeText(this,"Create a cloud mailbox first",Toast.LENGTH_SHORT).show();return;}prefs.putBool(CloudLinkService.KEY_ENABLED,on);Intent svc=new Intent(this,CloudLinkService.class);try{if(on){if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);}else stopService(svc);}catch(Exception e){Toast.makeText(this,"Sync error: "+e.getMessage(),Toast.LENGTH_LONG).show();}render();}

    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(12),dp(12),dp(12));x.setBackground(round(PANEL,14));return x;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0,1.12f);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(round(bg,12));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
