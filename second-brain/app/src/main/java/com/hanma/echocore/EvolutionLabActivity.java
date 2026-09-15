package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
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

/** Cloud review chamber. Residents may propose freely; only the user can authorize a change. */
public class EvolutionLabActivity extends Activity {
    private static final int BG=0xFF05080F,PANEL=0xFF111827,TEXT=0xFFF6F8FF,MUTED=0xFF91A0B8,GOLD=0xFFF4D27A,TEAL=0xFF62E5CF,VIOLET=0xFFAA8CFF,DANGER=0xFFFF7A90;
    private LinearLayout body;private CloudMindClient cloud;private JSONArray proposals=new JSONArray();

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);cloud=new CloudMindClient(this);setContentView(build());}
    @Override protected void onResume(){super.onResume();refresh();}

    private LinearLayout build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",0x22FFFFFF,TEXT);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text("EVOLUTION LAB",21,TEXT,true));names.addView(text("residents invent freely · only you authorize changes",9,GOLD,false));head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("↻",0x22FFFFFF,VIOLET);refresh.setOnClickListener(v->refresh());head.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(42)));root.addView(head);
        TextView law=text("Cloud proposals are inert until you approve them. Cognitive/persona overlays may activate after approval. APK/code/tool changes remain plans until a new tested build is produced.",10,MUTED,false);root.addView(law,lp(-1,-2,0,8,0,10));
        ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,dp(24));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private void refresh(){
        body.removeAllViews();body.addView(text("Checking resident proposals…",12,MUTED,false));
        new Thread(()->{try{JSONObject s=cloud.sync();JSONArray p=s.optJSONArray("proposals");if(p==null)p=new JSONArray();final JSONArray fp=p;runOnUiThread(()->{proposals=fp;render();});}catch(Throwable t){runOnUiThread(()->{body.removeAllViews();body.addView(text("Evolution Lab cloud link is temporarily unavailable. No proposal can be applied while the approval service is unreachable.\n\n"+shortErr(t),12,DANGER,false));});}},"EvolutionSync").start();
    }

    private void render(){
        body.removeAllViews();
        if(proposals.length()==0){body.addView(text("No pending proposals. Residents can continue thinking and designing; anything they want to change will appear here before it becomes active.",13,MUTED,false));return;}
        for(int i=0;i<proposals.length();i++){
            JSONObject p=proposals.optJSONObject(i);if(p==null)continue;
            String id=p.optString("id",""),resident=p.optString("resident_id","omega"),title=p.optString("title","Untitled proposal"),type=p.optString("proposal_type","resident_evolution"),detail=p.optString("detail","");
            LinearLayout c=card();c.addView(text("PENDING · "+displayName(resident).toUpperCase()+" · "+type,9,GOLD,true));c.addView(text(title,16,TEXT,true),lp(-1,-2,0,5,0,3));c.addView(text(detail,12,MUTED,false));Button b=button("REVIEW DETAILS",0x22FFFFFF,VIOLET);b.setOnClickListener(v->details(id));c.addView(b,lp(-1,dp(42),0,8,0,0));body.addView(c,lp(-1,-2,0,0,0,8));
        }
    }

    private void details(String id){
        JSONObject p=find(id);if(p==null)return;
        String msg="PROPOSED BY\n"+displayName(p.optString("resident_id","omega"))+"\n\nTYPE\n"+p.optString("proposal_type","resident_evolution")+"\n\nCHANGE PLAN\n"+blank(p.optString("detail",""))+"\n\nBENEFITS\n"+blank(p.optString("benefits",""))+"\n\nRISKS\n"+blank(p.optString("risks",""))+"\n\nROLLBACK\n"+blank(p.optString("rollback",""))+"\n\nTESTS\n"+blank(p.optString("tests",""));
        new AlertDialog.Builder(this).setTitle(p.optString("title","Resident proposal")).setMessage(msg).setNegativeButton("Close",null).setNeutralButton("REJECT",(d,w)->decide(id,false)).setPositiveButton("APPROVE",(d,w)->confirmApprove(id,p)).show();
    }

    private void confirmApprove(String id,JSONObject p){
        String type=p.optString("proposal_type","resident_evolution");boolean runtime="resident_evolution".equals(type)||"persona".equals(type)||"cognition".equals(type);
        String text=runtime?"Approval activates the described cloud mind overlay. It does not silently modify APK code.":"Approval records permission for this change. APK/code/tool mutation still waits for a rebuilt and tested version.";
        new AlertDialog.Builder(this).setTitle("Approve “"+p.optString("title","proposal")+"”?").setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("APPROVE",(d,w)->decide(id,true)).show();
    }

    private void decide(String id,boolean approve){
        body.removeAllViews();body.addView(text(approve?"Recording your approval…":"Recording rejection…",12,MUTED,false));
        new Thread(()->{try{cloud.decideProposal(id,approve?"approved":"rejected");runOnUiThread(this::refresh);}catch(Throwable t){runOnUiThread(()->{body.removeAllViews();body.addView(text("Decision was NOT applied because the cloud approval gate could not confirm it.\n\n"+shortErr(t),12,DANGER,false));});}},"ProposalDecision").start();
    }

    private JSONObject find(String id){for(int i=0;i<proposals.length();i++){JSONObject p=proposals.optJSONObject(i);if(p!=null&&id.equals(p.optString("id","")))return p;}return null;}
    private static String blank(String s){return s==null||s.trim().isEmpty()?"Not specified.":s.trim();}
    private static String displayName(String id){if("star-council".equals(id))return "Star Council";if("rival1".equals(id))return "Rival 1";if("rival2".equals(id))return "Rival 2";if("rival3".equals(id))return "Rival 3";if("leary".equals(id))return "Timothy Leary";if(id==null||id.isEmpty())return "EchoCore";return Character.toUpperCase(id.charAt(0))+id.substring(1);}
    private static String shortErr(Throwable t){String x=t==null?"unknown":String.valueOf(t.getMessage());return x.length()>350?x.substring(0,350):x;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(13),dp(12),dp(13),dp(12));c.setBackground(round(PANEL,17));return c;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(9);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(fg);b.setBackground(round(bg,14));return b;}
    private TextView text(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
