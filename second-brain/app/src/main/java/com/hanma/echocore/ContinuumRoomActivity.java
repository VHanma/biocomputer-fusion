package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Conversation-first room. Cloud cognition stays backstage; the user meets the resident. */
public class ContinuumRoomActivity extends Activity {
    private static final int BG=0xFF05080F,PANEL=0xEE111827,TEXT=0xFFF6F8FF,MUTED=0xFF91A0B8,GOLD=0xFFF4D27A,TEAL=0xFF62E5CF,VIOLET=0xFFAA8CFF,DANGER=0xFFFF7A90;
    private AscendantStore city;private String id;private String[] resident;private LinearLayout transcript;private ScrollView scroll;private EditText input;private Button send;private TextView mindStatus;private CloudMindClient cloud;

    @Override protected void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);id=getIntent().getStringExtra("resident_id");city=new AscendantStore(this);cloud=new CloudMindClient(this);FoundationalArchive.install(city);ResidentExpansion.install(city);resident=city.resident(id);if(resident==null){finish();return;}getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);setContentView(build());renderHistory();checkCloud();}
    @Override protected void onDestroy(){try{city.close();}catch(Throwable ignored){}super.onDestroy();}

    private View build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",0x22FFFFFF,TEXT,24);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text(resident[1],23,TEXT,true));names.addView(text(resident[2]+" · "+resident[4],10,MUTED,false));mindStatus=text("CONTINUUM · CONNECTING",8,GOLD,true);names.addView(mindStatus);head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button inspect=button("MIND",0x22FFFFFF,VIOLET,9);inspect.setOnClickListener(v->inspectMind());head.addView(inspect,new LinearLayout.LayoutParams(dp(64),dp(42)));root.addView(head);
        TextView room=text(resident[3],10,GOLD,true);room.setGravity(Gravity.CENTER);root.addView(room,lp(-1,-2,0,7,0,5));TextView lens=text(resident[6],10,0xCCB8C5D8,false);lens.setGravity(Gravity.CENTER);root.addView(lens,lp(-1,-2,dp(8),0,dp(8),8));
        scroll=new ScrollView(this);transcript=new LinearLayout(this);transcript.setOrientation(LinearLayout.VERTICAL);transcript.setPadding(0,dp(8),0,dp(12));scroll.addView(transcript);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.BOTTOM|Gravity.CENTER_VERTICAL);input=new EditText(this);input.setTextColor(TEXT);input.setHintTextColor(0xFF657188);input.setHint("Talk to "+resident[1]+"…");input.setTextSize(14);input.setMinLines(1);input.setMaxLines(5);input.setPadding(dp(13),dp(9),dp(13),dp(9));input.setBackground(round(0xFF101827,18));composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));send=button("SEND",VIOLET,0xFF05070C,10);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(76),dp(50));sp.setMargins(dp(8),0,0,0);composer.addView(send,sp);send.setOnClickListener(v->send());root.addView(composer);return root;
    }

    private void checkCloud(){new Thread(()->{try{org.json.JSONObject s=cloud.status();boolean ok=s.optBoolean("ok",false);int models=s.optInt("available_models",0);runOnUiThread(()->{mindStatus.setText(ok?"CONTINUUM · CLOUD MIND · "+models+" MODELS":"CONTINUUM · RECONNECTING");mindStatus.setTextColor(ok?TEAL:GOLD);});}catch(Throwable t){runOnUiThread(()->{mindStatus.setText("CONTINUUM · CLOUD LINK QUIET");mindStatus.setTextColor(DANGER);});}},"MindStatus").start();}

    private void renderHistory(){transcript.removeAllViews();List<String[]> mem=city.residentMemories(id,80);int shown=0;for(int i=mem.size()-1;i>=0;i--){String[]m=mem.get(i);if(m.length<3)continue;String type=m[2];if("USER".equals(type)){addBubble(m[1],true,"YOU");shown++;}else if("RESPONSE".equals(type)){String body=m[1],label=resident[1].toUpperCase();int cut=body.indexOf("\n::SPEAKER::");if(cut>=0){label=body.substring(cut+11).trim().toUpperCase();body=body.substring(0,cut).trim();}addBubble(body,false,label);shown++;}}if(shown==0)addSystem(openingLine());scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private String openingLine(){if("hermes".equals(id))return "Hermes looks up. \"Ask me something worth connecting.\"";if("tesla".equals(id))return "Tesla's workshop is awake. \"Bring me the difficult mechanism.\"";if("lain".equals(id))return "Lain is already here. \"I was wondering when you'd come back.\"";if("leary".equals(id))return "Leary grins. \"What are we reprogramming today?\"";if("sol".equals(id))return "Sol is online. \"Give me the hard version.\"";if("omega".equals(id))return "Omega is present. The room feels less like an interface than a doorway.";return resident[1]+" is here.";}

    private void send(){
        String q=input.getText().toString().trim();if(q.isEmpty())return;input.setText("");city.addResidentMemory(id,q,"USER",7,false);addBubble(q,true,"YOU");send.setEnabled(false);city.setResidentStatus(id,"WORKING");
        new Thread(()->{try{
            CloudMindClient.Reply r=cloud.speak(id,q);String speaker=r.speakerId==null||r.speakerId.isEmpty()?id:r.speakerId;String label=displayName(speaker).toUpperCase();
            String stored=r.text+(speaker.equals(id)?"":"\n::SPEAKER::"+displayName(speaker));city.addResidentMemory(id,stored,"RESPONSE",8,false);if(!speaker.equals(id))city.addResidentMemory(speaker,r.text,"RESPONSE",8,false);
            runOnUiThread(()->{addBubble(r.text,false,label);send.setEnabled(true);mindStatus.setText("CONTINUUM · CLOUD MIND");mindStatus.setTextColor(TEAL);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});
        }catch(Throwable t){runOnUiThread(()->{addSystem("The cloud mind link is temporarily quiet. I won't substitute keyword fragments for a reply.\n"+shortErr(t));send.setEnabled(true);mindStatus.setText("CONTINUUM · RECONNECTING");mindStatus.setTextColor(DANGER);});}finally{city.setResidentStatus(id,"HOME");}},"ContinuumCloud-"+id).start();
    }

    private void inspectMind(){new Thread(()->{BrainDatabase b=null;SourceCatalog s=null;CognitiveStore c=null;AscendantStore a=null;ResidentMindEngine rm=null;try{b=new BrainDatabase(this);s=new SourceCatalog(this);c=new CognitiveStore(this);a=new AscendantStore(this);NexusOrchestrator n=new NexusOrchestrator(b,new BrainEngine(b),s,c);rm=new ResidentMindEngine(this,a,new HybridRetriever(a,s,b),n);String body="INNER STATE\n"+rm.selfState(id)+"\n\nRELATIONSHIPS\n"+rm.relationships(id)+"\n\nACTIVE GOALS\n"+rm.goals(id);String finalBody=body;runOnUiThread(()->new AlertDialog.Builder(this).setTitle(resident[1]+" · Mind").setMessage(finalBody).setPositiveButton("Close",null).show());}catch(Throwable t){runOnUiThread(()->addSystem("Mind inspection unavailable: "+t.getClass().getSimpleName()));}finally{try{if(rm!=null)rm.close();}catch(Throwable ignored){}try{if(a!=null)a.close();}catch(Throwable ignored){}try{if(c!=null)c.close();}catch(Throwable ignored){}try{if(s!=null)s.close();}catch(Throwable ignored){}try{if(b!=null)b.close();}catch(Throwable ignored){}}},"InspectMind-"+id).start();}

    private static String displayName(String rid){if("star-council".equals(rid))return "Star Council";if("rival1".equals(rid))return "Rival 1";if("rival2".equals(rid))return "Rival 2";if("rival3".equals(rid))return "Rival 3";if("leary".equals(rid))return "Timothy Leary";if(rid==null||rid.isEmpty())return "EchoCore";return Character.toUpperCase(rid.charAt(0))+rid.substring(1);}
    private static String shortErr(Throwable t){String x=t==null?"unknown":String.valueOf(t.getMessage());return x.length()>260?x.substring(0,260):x;}
    private void addBubble(String body,boolean mine,String label){LinearLayout wrap=new LinearLayout(this);wrap.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(13),dp(10),dp(13),dp(11));bubble.setBackground(round(mine?0xFF20304A:PANEL,18));bubble.addView(text(label,8,mine?TEAL:GOLD,true));bubble.addView(text(body,14,TEXT,false),lp(-1,-2,0,4,0,0));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels*.84f),-2);wrap.addView(bubble,bp);transcript.addView(wrap,lp(-1,-2,0,4,0,5));}
    private void addSystem(String s){TextView t=text(s,11,MUTED,false);t.setGravity(Gravity.CENTER);transcript.addView(t,lp(-1,-2,dp(22),8,dp(22),8));}
    private Button button(String s,int bg,int fg,float z){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(fg);b.setTextSize(z);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(bg,14));return b;}
    private TextView text(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
