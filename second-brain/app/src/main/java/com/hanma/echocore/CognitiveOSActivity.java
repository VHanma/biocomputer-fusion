package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CognitiveOSActivity extends Activity {
    private static final int BG=0xFF07090D,PANEL=0xFF111722,PANEL2=0xFF192231,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B,DANGER=0xFFFF7A90;
    private BrainDatabase brain;private BrainEngine engine;private SourceCatalog sources;private CognitiveStore store;private CognitiveOrchestrator omega;private SecurePrefs prefs;private ModelGateway model;private EchoLinkServer echoLink;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private LinearLayout body;

    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);brain=new BrainDatabase(this);engine=new BrainEngine(brain);sources=new SourceCatalog(this);store=new CognitiveStore(this);omega=new CognitiveOrchestrator(brain,engine,sources,store);prefs=new SecurePrefs(this);model=new ModelGateway(prefs);echoLink=EchoLinkServer.getInstance(this,brain,engine,sources,store,omega,prefs,model);setContentView(shell());ensureEchoLinkState();render();}
    @Override protected void onResume(){super.onResume();ensureEchoLinkState();if(body!=null)render();}
    @Override protected void onDestroy(){super.onDestroy();work.shutdownNow();}

    private View shell(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(16),dp(12),dp(16),dp(9));head.addView(text("ECHOCORE Ω",23,TEXT,true));head.addView(text("OMEGA COGNITIVE OS · memory + sources + reasoning + executive function",10,ACCENT2,false));root.addView(head);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(3),dp(14),dp(30));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));return root;}

    private void render(){body.removeAllViews();
        body.addView(section("COGNITIVE PULSE","A second mind should know what it remembers, where it learned it, what it doubts, and what it is trying to do."));
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.addView(stat(String.valueOf(brain.count()),"MEMORY"));stats.addView(stat(String.valueOf(sources.countSources()),"SOURCES"));stats.addView(stat(String.valueOf(brain.countAssociations()),"SYNAPSES"));stats.addView(stat(String.valueOf(store.countActiveProjects()),"PROJECTS"));body.addView(stats,lp(-1,-2,0,0,0,10));
        TextView modelState=text(model.enabled()?"AI CORE · CONNECTED":"AI CORE · OFFLINE LOCAL",10,model.enabled()?ACCENT2:WARM,true);body.addView(modelState,lp(-1,-2,0,0,0,10));

        LinearLayout launch=new LinearLayout(this);launch.setOrientation(LinearLayout.HORIZONTAL);
        Button neural=button("NEURAL BRAIN",PANEL2,ACCENT);neural.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));launch.addView(neural,new LinearLayout.LayoutParams(0,dp(48),1f));
        Button src=button("SOURCE CORTEX",PANEL2,ACCENT2);src.setOnClickListener(v->startActivity(new Intent(this,SourceActivity.class)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(48),1f);sp.setMargins(dp(7),0,0,0);launch.addView(src,sp);body.addView(launch,lp(-1,-2,0,0,0,8));
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);Button auto=button("AUTOPILOT",PANEL2,WARM);auto.setOnClickListener(v->runAutopilot());tools.addView(auto,new LinearLayout.LayoutParams(0,dp(46),1f));Button gaps=button("GAP SCAN",PANEL2,ACCENT2);gaps.setOnClickListener(v->dialog("Knowledge gaps",omega.knowledgeGaps()));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(46),1f);gp.setMargins(dp(6),0,0,0);tools.addView(gaps,gp);Button settings=button("AI SETTINGS",PANEL2,ACCENT);settings.setOnClickListener(v->modelSettings());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(46),1f);mp.setMargins(dp(6),0,0,0);tools.addView(settings,mp);body.addView(tools,lp(-1,-2,0,0,0,8));
        Button link=button(echoLink.isRunning()?"ECHOLINK · LIVE":"ECHOLINK",PANEL2,echoLink.isRunning()?ACCENT2:ACCENT);link.setOnClickListener(v->echoLinkSettings());body.addView(link,lp(-1,dp(44),0,0,0,14));

        body.addView(section("ASK OMEGA","Evidence is retrieved from personal memory and full imported documents before the reasoning pass."));
        LinearLayout askCard=card();EditText ask=edit("Ask a question, solve a problem, test an idea, plan a goal…",4);askCard.addView(ask,lp(-1,dp(112),0,0,0,8));
        Spinner mode=new Spinner(this);String[] modes={"DEEP","RESEARCH","CRITIC","CREATIVE","PLANNER","TEACHER"};mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));mode.setBackground(round(PANEL2,10));askCard.addView(mode,lp(-1,dp(46),0,0,0,6));
        CheckBox useModel=new CheckBox(this);useModel.setText("Use connected AI core when available");useModel.setTextColor(TEXT);useModel.setChecked(model.enabled());askCard.addView(useModel);
        Button think=button("THINK",ACCENT,BG);think.setOnClickListener(v->think(ask.getText().toString(),mode.getSelectedItem().toString(),useModel.isChecked()));askCard.addView(think,lp(-1,dp(52),0,8,0,0));body.addView(askCard,lp(-1,-2,0,0,0,14));

        body.addView(section("PROJECT MIND","Goals get externalized into executable steps instead of competing for working-memory space."));Button addProject=button("＋ NEW PROJECT / GOAL",PANEL2,ACCENT2);addProject.setOnClickListener(v->newProjectDialog());body.addView(addProject,lp(-1,dp(47),0,0,0,8));List<String[]> projects=store.projects();if(projects.isEmpty())body.addView(cardText("No projects yet. A project can auto-generate an evidence-first seven-step plan."));else for(String[] p:projects)body.addView(projectCard(p),lp(-1,-2,0,0,0,8));

        body.addView(section("HYPOTHESIS LAB","Keep possibilities testable. Support and opposition change confidence instead of silently turning a hunch into a fact."));Button addHyp=button("＋ NEW HYPOTHESIS",PANEL2,WARM);addHyp.setOnClickListener(v->newHypothesisDialog());body.addView(addHyp,lp(-1,dp(46),0,0,0,8));List<String[]> hyps=store.hypotheses();if(hyps.isEmpty())body.addView(cardText("No hypotheses tracked."));else for(String[] h:hyps)body.addView(hypothesisCard(h),lp(-1,-2,0,0,0,8));

        body.addView(section("RECENT INNER DIALOGUE","Conversation is stored locally as episodic context."));List<String[]> turns=store.recentTurns(6);if(turns.isEmpty())body.addView(cardText("Omega has not held a dialogue yet."));else for(String[] t:turns)body.addView(turnCard(t),lp(-1,-2,0,0,0,6));
    }

    private void think(String q,String mode,boolean useModel){q=q==null?"":q.trim();if(q.isEmpty()){toast("Give Omega something to think about.");return;}store.addTurn("USER",q,mode);final String fq=q,fm=mode;toast("Thinking through memory + sources…");work.execute(()->{
        String answer;
        if(useModel&&model.enabled()){
            try{String context=omega.buildGroundedContext(fq,16);String prompt="RECENT DIALOGUE:\n"+conversationContext()+"\n\nQUESTION:\n"+fq+"\n\nEVIDENCE:\n"+(context.isEmpty()?"No directly retrieved evidence.":context)+"\n\nReturn the strongest grounded answer for mode "+fm+".";String draft=model.complete(omega.systemPrompt(fm),prompt);if("DEEP".equals(fm)){answer=model.complete(omega.systemPrompt(fm),omega.deepReviewPrompt(fq,draft,context));answer="MODEL-GROUNDED · SELF-REVIEWED\n\n"+answer;}else answer="MODEL-GROUNDED\n\n"+draft;}catch(Exception e){answer=omega.localAnswer(fq,fm)+"\n\nAI core fallback: "+safeError(e);}
        }else answer=omega.localAnswer(fq,fm);
        store.addTurn("OMEGA",answer,fm);String finalAnswer=answer;runOnUiThread(()->{dialog("Omega · "+fm,finalAnswer);render();});});}

    private String conversationContext(){StringBuilder b=new StringBuilder();for(String[] t:store.recentTurns(8)){b.append(t[0]).append(": ").append(trim(t[1],500)).append('\n');}return b.toString().trim();}

    private void runAutopilot(){toast("Running consolidation + metacognitive cycle…");work.execute(()->{String r=omega.autopilotCycle();store.addTurn("OMEGA",r,"AUTOPILOT");runOnUiThread(()->{dialog("Omega Autopilot",r);render();});});}

    private void ensureEchoLinkState(){if(echoLink==null)return;boolean enabled=prefs.getBool("echolink_enabled",false);if(enabled&&!echoLink.isRunning()){try{echoLink.start();}catch(Exception ignored){}}else if(!enabled&&echoLink.isRunning())echoLink.stop();}

    private void echoLinkSettings(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(2),dp(8),0);
        CheckBox enabled=new CheckBox(this);enabled.setText("Enable EchoLink local bridge");enabled.setTextColor(TEXT);enabled.setChecked(echoLink.isRunning()||prefs.getBool("echolink_enabled",false));box.addView(enabled);
        EditText port=edit("Port",1);port.setText(String.valueOf(echoLink.port()));box.addView(labelled("LOCAL PORT",port));
        TextView endpoint=text(echoLink.endpoint(),11,ACCENT2,true);box.addView(labelled("ENDPOINT",endpoint));
        TextView token=text(echoLink.token(),11,WARM,true);box.addView(labelled("PAIRING TOKEN",token));
        CheckBox read=new CheckBox(this);read.setText("Allow brain read / query");read.setTextColor(TEXT);read.setChecked(echoLink.permRead());box.addView(read);
        CheckBox write=new CheckBox(this);write.setText("Allow memory write / consolidation");write.setTextColor(TEXT);write.setChecked(echoLink.permWrite());box.addView(write);
        CheckBox src=new CheckBox(this);src.setText("Allow document / source access");src.setTextColor(TEXT);src.setChecked(echoLink.permSources());box.addView(src);
        CheckBox proj=new CheckBox(this);proj.setText("Allow project / task access");proj.setTextColor(TEXT);proj.setChecked(echoLink.permProjects());box.addView(proj);
        TextView note=text("EchoLink binds to 127.0.0.1 only. Any companion app, local model host, automation tool, or future ChatGPT bridge on the same device can talk to this brain using the token below in header X-EchoCore-Token. Endpoints include /capabilities, /brain/answer, /brain/search, /memory/add, /source/import_text, /source/search, /projects, /project/add, /task/add and /consolidate.",10,MUTED,false);box.addView(note,lp(-1,-2,0,8,0,0));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("EchoLink bridge").setView(box).setNegativeButton("Close",null).setNeutralButton("Regenerate token",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{echoLink.regenerateToken();token.setText(echoLink.token());});
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int p;try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){p=18432;}boolean wasRunning=echoLink.isRunning();echoLink.setPermissions(read.isChecked(),write.isChecked(),src.isChecked(),proj.isChecked());echoLink.setPort(p);prefs.putBool("echolink_enabled",enabled.isChecked());try{if(wasRunning)echoLink.stop();if(enabled.isChecked())echoLink.start();endpoint.setText(echoLink.endpoint());}catch(Exception e){toast("EchoLink start failed: "+safeError(e));}render();d.dismiss();});
        });
        d.show();
    }

    private void modelSettings(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(2),dp(8),0);CheckBox enabled=new CheckBox(this);enabled.setText("Enable optional AI model layer");enabled.setTextColor(TEXT);enabled.setChecked(prefs.getBool("model_enabled",false));box.addView(enabled);
        EditText endpoint=edit("Full OpenAI-compatible chat endpoint URL",2);endpoint.setText(prefs.get("endpoint",""));box.addView(labelled("ENDPOINT",endpoint));EditText modelName=edit("Model name",1);modelName.setText(prefs.get("model","local-model"));box.addView(labelled("MODEL",modelName));EditText key=edit("API key (optional for local servers)",1);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);String existing=prefs.getSecret("api_key");if(!existing.isEmpty())key.setHint("Stored securely · leave blank to keep current key");box.addView(labelled("API KEY",key));TextView note=text("The key is encrypted with Android Keystore. When AI Core is enabled, retrieved question context can be sent to the endpoint you configure. Offline mode sends nothing.",10,MUTED,false);box.addView(note,lp(-1,-2,0,8,0,0));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("AI Core settings").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ep=endpoint.getText().toString().trim();prefs.putBool("model_enabled",enabled.isChecked());prefs.put("endpoint",ep);prefs.put("model",modelName.getText().toString().trim().isEmpty()?"local-model":modelName.getText().toString().trim());String k=key.getText().toString();if(!k.isEmpty())prefs.putSecret("api_key",k);d.dismiss();render();toast(enabled.isChecked()?"AI Core enabled.":"Offline local brain selected.");}));d.show();}

    private void newProjectDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),0,dp(8),0);EditText title=edit("Project name",1),goal=edit("What does finished look like?",3);box.addView(title);box.addView(goal,lp(-1,dp(90),0,6,0,0));new AlertDialog.Builder(this).setTitle("New project").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String t=title.getText().toString().trim(),g=goal.getText().toString().trim();long id=store.addProject(t,g);if(id>0){int pr=8;for(String step:omega.planSteps(g.isEmpty()?t:g))store.addTask(id,step,pr--);brain.addMemoryRich((g.isEmpty()?t:g),"GOAL","project, "+engine.autoTags(t+" "+g),9,1,8,6,true);render();}}).show();}

    private View projectCard(String[] p){long id=Long.parseLong(p[0]);String title=p[1],goal=p[2],status=p[3];LinearLayout c=card();LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.addView(text(title,15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1f));top.addView(text(status,10,"ACTIVE".equals(status)?ACCENT2:MUTED,true));c.addView(top);if(!goal.isEmpty())c.addView(text(goal,12,MUTED,false),lp(-1,-2,0,4,0,7));List<String[]> tasks=store.tasks(id);for(String[] t:tasks){long tid=Long.parseLong(t[0]);boolean done="DONE".equals(t[2]);Button b=button((done?"✓ ":"○ ")+trim(t[1],90),done?0xFF14261F:PANEL2,done?ACCENT2:TEXT);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->{store.toggleTask(tid,!done);render();});c.addView(b,lp(-1,dp(43),0,0,0,4));}Button finish=button("ACTIVE".equals(status)?"ARCHIVE PROJECT":"REACTIVATE",PANEL2,"ACTIVE".equals(status)?WARM:ACCENT2);finish.setOnClickListener(v->{store.setProjectStatus(id,"ACTIVE".equals(status)?"ARCHIVED":"ACTIVE");render();});c.addView(finish,lp(-1,dp(40),0,4,0,0));return c;}

    private void newHypothesisDialog(){EditText e=edit("State the hypothesis clearly enough to test it.",3);e.setPadding(dp(10),dp(8),dp(10),dp(8));new AlertDialog.Builder(this).setTitle("New hypothesis").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Track",(d,w)->{String s=e.getText().toString().trim();if(!s.isEmpty()){store.addHypothesis(s);brain.addMemoryRich(s,"QUESTION","hypothesis, "+engine.autoTags(s),7,0,5,8,true);render();}}).show();}

    private View hypothesisCard(String[] h){long id=Long.parseLong(h[0]);String statement=h[1],status=h[2];int support=Integer.parseInt(h[3]),oppose=Integer.parseInt(h[4]),conf=Integer.parseInt(h[5]);LinearLayout c=card();c.addView(text(statement,14,TEXT,true));c.addView(text("confidence "+conf+"/10 · support "+support+" · oppose "+oppose+" · "+status,10,MUTED,false),lp(-1,-2,0,4,0,6));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button plus=button("+ SUPPORT",0xFF14261F,ACCENT2);plus.setOnClickListener(v->{store.scoreHypothesis(id,1,0);render();});row.addView(plus,new LinearLayout.LayoutParams(0,dp(41),1f));Button minus=button("+ OPPOSE",0xFF28161C,DANGER);minus.setOnClickListener(v->{store.scoreHypothesis(id,0,1);render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(41),1f);p.setMargins(dp(6),0,0,0);row.addView(minus,p);Button close=button("OPEN".equals(status)?"RESOLVE":"REOPEN",PANEL2,WARM);close.setOnClickListener(v->{store.setHypothesisStatus(id,"OPEN".equals(status)?"RESOLVED":"OPEN");render();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(41),1f);cp.setMargins(dp(6),0,0,0);row.addView(close,cp);c.addView(row);return c;}

    private View turnCard(String[] t){boolean user="USER".equalsIgnoreCase(t[0]);LinearLayout c=card();c.addView(text((user?"YOU":"OMEGA")+" · "+t[2],9,user?ACCENT:ACCENT2,true));c.addView(text(trim(t[1],260),12,TEXT,false),lp(-1,-2,0,3,0,0));return c;}

    private LinearLayout labelled(String label,View field){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(text(label,9,MUTED,true));x.addView(field,lp(-1,-2,0,2,0,7));return x;}
    private LinearLayout section(String title,String sub){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(text(title,13,TEXT,true));x.addView(text(sub,10,MUTED,false),lp(-1,-2,0,2,0,7));return x;}
    private View stat(String value,String label){LinearLayout x=card();x.setGravity(Gravity.CENTER);x.addView(text(value,17,ACCENT2,true));x.addView(text(label,8,MUTED,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(66),1f);p.setMargins(dp(2),0,dp(2),0);x.setLayoutParams(p);return x;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(11),dp(10),dp(11),dp(10));x.setBackground(round(PANEL,14));return x;}
    private View cardText(String s){LinearLayout c=card();c.addView(text(s,12,MUTED,false));return c;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0,1.1f);return t;}
    private EditText edit(String hint,int lines){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xFF68758D);e.setTextColor(TEXT);e.setTextSize(13);e.setMinLines(lines);e.setMaxLines(Math.max(lines,7));e.setBackground(round(PANEL2,11));e.setPadding(dp(10),dp(7),dp(10),dp(7));return e;}
    private Button button(String label,int bg,int fg){Button b=new Button(this);b.setText(label);b.setTextColor(fg);b.setTextSize(10);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setPadding(dp(7),0,dp(7),0);b.setBackground(round(bg,11));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private void dialog(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Close",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static String trim(String s,int n){if(s==null)return "";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
    private static String safeError(Exception e){String s=e.getMessage()==null?e.toString():e.getMessage();return trim(s,260);}
}
