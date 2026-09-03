package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NexusBrainActivity extends Activity {
    private static final int BG=0xFF07090D,PANEL=0xFF111722,PANEL2=0xFF192231,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B,DANGER=0xFFFF7085;
    private BrainDatabase brain;private BrainEngine engine;private SourceCatalog sources;private CognitiveStore store;private NexusOrchestrator nexus;private SecurePrefs prefs;private ModelGateway model;private DiagnosticsStore diag;
    private LinearLayout body;private TextView answerBox;private final ExecutorService work=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);diag=new DiagnosticsStore(this);
        try{brain=new BrainDatabase(this);engine=new BrainEngine(brain);sources=new SourceCatalog(this);store=new CognitiveStore(this);nexus=new NexusOrchestrator(brain,engine,sources,store);prefs=new SecurePrefs(this);model=new ModelGateway(prefs);setContentView(shell());render();diag.event("NEXUS_BRAIN","Dashboard opened");}
        catch(Throwable t){diag.error("nexus_brain_onCreate",t);showRecovery(t);}
    }

    @Override protected void onResume(){super.onResume();if(body!=null&&brain!=null)try{render();}catch(Throwable t){diag.error("nexus_render",t);}}
    @Override protected void onDestroy(){work.shutdownNow();try{if(brain!=null)brain.close();}catch(Throwable ignored){}try{if(sources!=null)sources.close();}catch(Throwable ignored){}try{if(store!=null)store.close();}catch(Throwable ignored){}super.onDestroy();}

    private View shell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(16),dp(14),dp(16),dp(10));head.addView(text("ECHOCORE Ω NEXUS",23,TEXT,true));head.addView(text("EVIDENCE-BALANCED COGNITIVE OS",10,ACCENT2,true));root.addView(head);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(3),dp(14),dp(30));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private void render(){
        body.removeAllViews();
        int memories=brain.count(),src=sources.countSources(),syn=brain.countAssociations(),projects=store.countActiveProjects();
        body.addView(section("COGNITIVE PULSE","What is remembered, where it came from, what is uncertain, and what should happen next."));
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.addView(stat(memories,"MEMORY"));stats.addView(stat(src,"SOURCES"));stats.addView(stat(syn,"SYNAPSES"));stats.addView(stat(projects,"PROJECTS"));body.addView(stats,lp(-1,-2,0,0,0,10));

        int grounded=brain.countType("REFERENCE")+brain.countType("KNOWLEDGE")+brain.countType("OBSERVATION");int personal=brain.countType("SELF")+brain.countType("BELIEF");
        LinearLayout evidence=card();evidence.addView(text("EVIDENCE HEALTH",12,ACCENT2,true));
        evidence.addView(text(src+" full sources · "+grounded+" reference/knowledge/observation traces · "+personal+" self/belief traces",11,TEXT,false),lp(-1,-2,0,5,0,4));
        String calibration=src==0?"Grounding is memory-heavy. Import sources for stronger research answers.":(personal>grounded?"Personal traces currently outweigh grounded traces. Nexus will cap their evidence weight.":"Grounded evidence is keeping pace with personal traces.");
        evidence.addView(text(calibration,10,src==0||personal>grounded?WARM:ACCENT2,false));body.addView(evidence,lp(-1,-2,0,0,0,10));

        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);Button memory=button("NEURAL MEMORY",PANEL2,ACCENT);memory.setOnClickListener(v->safeStart(MainActivity.class));Button source=button("SOURCE CORTEX",PANEL2,ACCENT2);source.setOnClickListener(v->safeStart(SourceActivity.class));nav.addView(memory,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(48),1);np.setMargins(dp(7),0,0,0);nav.addView(source,np);body.addView(nav,lp(-1,-2,0,0,0,7));
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);Button gaps=button("GAP SCAN",PANEL2,ACCENT2);gaps.setOnClickListener(v->runTextTask("Gap scan",()->nexus.knowledgeGaps()));Button consolidate=button("CONSOLIDATE",PANEL2,WARM);consolidate.setOnClickListener(v->runTextTask("Consolidation",()->engine.consolidate()));tools.addView(gaps,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);cp.setMargins(dp(7),0,0,0);tools.addView(consolidate,cp);body.addView(tools,lp(-1,-2,0,0,0,12));

        body.addView(section("ASK NEXUS","Source passages and observations are reserved space in retrieval. SELF/BELIEF nodes cannot crowd them out."));
        LinearLayout ask=card();EditText q=edit("Question, decision, target, hypothesis, or plan…",4);ask.addView(q,lp(-1,dp(112),0,0,0,8));
        Spinner mode=new Spinner(this);String[] modes={"DEEP","RESEARCH","CRITIC","CREATIVE","PLANNER","TEACHER"};mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));mode.setBackground(round(PANEL2,10));ask.addView(mode,lp(-1,dp(46),0,0,0,5));
        CheckBox useModel=new CheckBox(this);useModel.setText("Use optional AI Core when configured");useModel.setTextColor(TEXT);useModel.setChecked(model.enabled());ask.addView(useModel);
        Button think=button("THINK",ACCENT,BG);think.setOnClickListener(v->think(q.getText().toString(),mode.getSelectedItem().toString(),useModel.isChecked()));ask.addView(think,lp(-1,dp(52),0,7,0,7));
        answerBox=text("",11,TEXT,false);answerBox.setTextIsSelectable(true);answerBox.setVisibility(View.GONE);ask.addView(answerBox);body.addView(ask,lp(-1,-2,0,0,0,12));

        body.addView(section("PROJECT MIND","Goals become visible tasks instead of competing for working-memory space."));Button addProject=button("＋ NEW PROJECT",PANEL2,ACCENT2);addProject.setOnClickListener(v->newProject());body.addView(addProject,lp(-1,dp(46),0,0,0,7));
        List<String[]> plist=store.projects();if(plist.isEmpty())body.addView(cardText("No projects yet."));else for(String[] p:plist)body.addView(projectCard(p),lp(-1,-2,0,0,0,7));

        body.addView(section("HYPOTHESIS LAB","Keep claims testable and confidence adjustable."));Button addHyp=button("＋ NEW HYPOTHESIS",PANEL2,WARM);addHyp.setOnClickListener(v->newHypothesis());body.addView(addHyp,lp(-1,dp(46),0,0,0,7));
        List<String[]> hs=store.hypotheses();if(hs.isEmpty())body.addView(cardText("No hypotheses tracked."));else for(String[] h:hs)body.addView(hypothesisCard(h),lp(-1,-2,0,0,0,7));

        body.addView(section("SYSTEM HEALTH","The bridge and brain now keep their own diagnostic trail instead of failing silently."));LinearLayout health=card();health.addView(text(diag.snapshotText(),9,MUTED,false));body.addView(health,lp(-1,-2,0,0,0,10));
    }

    private void think(String q,String mode,boolean useModel){
        q=q==null?"":q.trim();if(q.isEmpty()){toast("Give Nexus something to think about.");return;}final String fq=q,fm=mode;answerBox.setVisibility(View.VISIBLE);answerBox.setText("Nexus is retrieving evidence…");store.addTurn("USER",fq,fm);
        work.execute(()->{
            String answer;
            try{
                if(useModel&&model.enabled()){
                    String context=nexus.buildGroundedContext(fq,18);String prompt="QUESTION:\n"+fq+"\n\nEVIDENCE:\n"+(context.isEmpty()?"No retrieved evidence.":context)+"\n\nAnswer in "+fm+" mode. Keep imported source, observation, personal belief, and inference distinct.";
                    String draft=model.complete(nexus.systemPrompt(fm),prompt);answer="MODEL + NEXUS EVIDENCE\n\n"+draft;
                }else answer=nexus.localAnswer(fq,fm);
            }catch(Throwable t){diag.error("nexus_think",t);answer="Nexus recovered from a reasoning error. Diagnostics captured: "+safe(t.getMessage());}
            store.addTurn("NEXUS",answer,fm);final String out=answer;runOnUiThread(()->{answerBox.setText(out);});
        });
    }

    private View projectCard(String[] p){
        long id=Long.parseLong(p[0]);LinearLayout c=card();LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.addView(text(p[1],14,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text(p[3],9,"ACTIVE".equals(p[3])?ACCENT2:MUTED,true));c.addView(top);if(!p[2].isEmpty())c.addView(text(p[2],10,MUTED,false),lp(-1,-2,0,4,0,5));
        for(String[] t:store.tasks(id)){long tid=Long.parseLong(t[0]);boolean done="DONE".equals(t[2]);Button b=button((done?"✓ ":"○ ")+trim(t[1],82),done?0xFF14261F:PANEL2,done?ACCENT2:TEXT);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->{try{store.toggleTask(tid,!done);render();}catch(Throwable x){diag.error("toggle_task",x);}});c.addView(b,lp(-1,dp(44),0,3,0,0));}
        return c;
    }

    private View hypothesisCard(String[] h){
        long id=Long.parseLong(h[0]);LinearLayout c=card();c.addView(text(h[1],12,TEXT,true));c.addView(text("Status "+h[2]+" · support "+h[3]+" · oppose "+h[4]+" · confidence "+h[5]+"/10",9,MUTED,false),lp(-1,-2,0,4,0,5));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button plus=button("＋ SUPPORT",PANEL2,ACCENT2);plus.setOnClickListener(v->{store.scoreHypothesis(id,1,0);render();});Button minus=button("＋ OPPOSE",PANEL2,DANGER);minus.setOnClickListener(v->{store.scoreHypothesis(id,0,1);render();});row.addView(plus,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(42),1);mp.setMargins(dp(6),0,0,0);row.addView(minus,mp);c.addView(row);return c;
    }

    private void newProject(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),0,dp(8),0);EditText title=edit("Project name",1),goal=edit("Finish condition / goal",3);box.addView(title);box.addView(goal,lp(-1,dp(86),0,6,0,0));
        new AlertDialog.Builder(this).setTitle("New Nexus project").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{try{String t=title.getText().toString().trim(),g=goal.getText().toString().trim();long id=store.addProject(t,g);if(id>0){int pr=8;for(String step:nexus.planSteps(g.isEmpty()?t:g))store.addTask(id,step,pr--);brain.addMemoryRich(g.isEmpty()?t:g,"GOAL","project, "+engine.autoTags(t+" "+g),9,1,8,6,true);}render();}catch(Throwable x){diag.error("new_project",x);}}).show();
    }

    private void newHypothesis(){
        EditText e=edit("Testable statement",3);new AlertDialog.Builder(this).setTitle("New hypothesis").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{try{String s=e.getText().toString().trim();long id=store.addHypothesis(s);if(id>0)brain.addMemoryRich(s,"QUESTION","hypothesis, "+engine.autoTags(s),7,0,5,8,true);render();}catch(Throwable x){diag.error("new_hypothesis",x);}}).show();
    }

    private interface TextJob{String run() throws Exception;}
    private void runTextTask(String label,TextJob job){toast(label+"…");work.execute(()->{String out;try{out=job.run();}catch(Throwable t){diag.error(label,t);out=label+" error captured in diagnostics.";}final String r=out;runOnUiThread(()->dialog(label,r));});}
    private void safeStart(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable t){diag.error("open_"+cls.getSimpleName(),t);toast("Screen error captured");}}
    private void showRecovery(Throwable t){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(24),dp(18),dp(24));x.setBackgroundColor(BG);x.addView(text("NEXUS RECOVERY MODE",22,DANGER,true));x.addView(text("The brain dashboard hit an initialization error. AutoLink can remain separate from this screen.\n\n"+safe(t.getMessage())+"\n\nOpen the main Nexus launcher again and copy diagnostics if needed.",12,TEXT,false),lp(-1,-2,0,10,0,12));Button close=button("CLOSE",PANEL2,TEXT);close.setOnClickListener(v->finish());x.addView(close,lp(-1,dp(50),0,0,0,0));setContentView(x);}

    private View section(String title,String sub){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(text(title,13,ACCENT2,true));x.addView(text(sub,10,MUTED,false),lp(-1,-2,0,2,0,6));return x;}
    private View stat(int n,String label){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setPadding(dp(4),dp(8),dp(4),dp(8));x.setBackground(round(PANEL,10));x.addView(text(String.valueOf(n),18,TEXT,true));x.addView(text(label,8,MUTED,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),0,dp(2),0);x.setLayoutParams(p);return x;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(11),dp(11),dp(11),dp(11));x.setBackground(round(PANEL,13));return x;}
    private View cardText(String s){LinearLayout c=card();c.addView(text(s,11,MUTED,false));return c;}
    private TextView text(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,b?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0,1.12f);return t;}
    private EditText edit(String hint,int lines){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xFF657086);e.setTextColor(TEXT);e.setTextSize(12);e.setSingleLine(lines<=1);e.setMinLines(lines);e.setMaxLines(Math.max(lines,5));e.setPadding(dp(9),dp(8),dp(9),dp(8));e.setBackground(round(PANEL2,10));return e;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(9);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(round(bg,10));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private void dialog(String title,String msg){try{new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Close",null).show();}catch(Throwable t){diag.error("dialog",t);}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static String trim(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,Math.max(1,n-1))+"…";}
    private static String safe(String s){return s==null?"":s;}
}
