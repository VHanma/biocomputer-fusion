package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = 0xFF080A0F;
    private static final int PANEL = 0xFF111722;
    private static final int PANEL2 = 0xFF182130;
    private static final int TEXT = 0xFFF4F7FF;
    private static final int MUTED = 0xFF98A4BA;
    private static final int ACCENT = 0xFF7C9CFF;
    private static final int ACCENT2 = 0xFF56E0C5;
    private static final int WARM = 0xFFFFB86B;
    private static final int DANGER = 0xFFFF7A90;

    private static final int REQ_SPEECH = 700;
    private static final int REQ_EXPORT = 701;
    private static final int REQ_IMPORT = 702;

    private BrainDatabase db;
    private BrainEngine engine;
    private FrameLayout content;
    private LinearLayout chatStream;
    private ScrollView chatScroll;
    private EditText talkInput;
    private final List<Button> navButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db = new BrainDatabase(this);
        engine = new BrainEngine(db);
        setContentView(buildShell());
        showCortex();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(6), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.addView(text("ECHOCORE", 21, TEXT, true));
        title.addView(text("adaptive second brain · v2 neural architecture", 10, ACCENT2, false));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button pulse = button("PULSE", PANEL2, ACCENT2);
        pulse.setOnClickListener(v -> dialog("Brain Pulse", engine.brainSnapshot()));
        header.addView(pulse, margins(dp(72), dp(38), 8, 0, 0, 0));
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(7), dp(7), dp(7), dp(9));
        String[] labels = {"CORTEX", "TALK", "MEMORY", "SYNAPSE", "EXEC", "SELF"};
        Runnable[] routes = {this::showCortex, this::showTalk, this::showMemory, this::showSynapse, this::showExecutive, this::showSelf};
        for (int i=0;i<labels.length;i++) {
            final int x=i;
            Button b=button(labels[i],PANEL2,MUTED);
            b.setTextSize(10);
            b.setOnClickListener(v -> routes[x].run());
            navButtons.add(b);
            nav.addView(b, margins(dp(82), dp(43), 3,0,3,0));
        }
        navScroll.addView(nav);
        root.addView(navScroll);
        return root;
    }

    private void selectNav(int index) {
        for(int i=0;i<navButtons.size();i++) {
            Button b=navButtons.get(i);
            b.setTextColor(i==index?BG:MUTED);
            b.setBackground(round(i==index?ACCENT:PANEL2,12));
        }
    }

    private void showCortex() {
        selectNav(0);
        LinearLayout body=page();
        body.addView(sectionTitle("CORTEX", "Capture perception, meaning, emotion, confidence and salience in one pass."));

        LinearLayout stats=new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard(String.valueOf(db.count()),"MEMORIES"));
        stats.addView(statCard(String.valueOf(db.countAssociations()),"SYNAPSES"));
        stats.addView(statCard(db.activeWorkspace(20).size()+"/7","WORKING"));
        stats.addView(statCard(String.valueOf(db.countType("INSIGHT")),"INSIGHTS"));
        body.addView(stats, margins(-1,-2,0,0,0,12));

        LinearLayout capture=card();
        capture.addView(text("NEW NEURAL TRACE",11,ACCENT2,true));
        EditText thought=edit("What happened, what did you notice, learn, feel, decide, imagine, or want?",4);
        capture.addView(thought,margins(-1,dp(116),0,8,0,8));

        Spinner type=new Spinner(this);
        String[] types={"THOUGHT","IDEA","OBSERVATION","QUESTION","REFERENCE","SELF","VALUE","BELIEF","GOAL","FOCUS","SKILL","PROCEDURE","PRO","CON","DREAM","INSIGHT"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types);
        type.setAdapter(adapter); type.setBackground(round(PANEL2,10)); type.setPadding(dp(8),0,dp(8),0);
        capture.addView(type,margins(-1,dp(48),0,0,0,8));

        EditText tags=edit("Tags, optional. Auto-generated if blank.",1);
        capture.addView(tags,margins(-1,dp(48),0,0,0,10));

        TextView importanceLabel=text("SALIENCE · 7/10",10,MUTED,true); capture.addView(importanceLabel);
        SeekBar importance=seek(9,6); importance.setOnSeekBarChangeListener(labelListener(importanceLabel,"SALIENCE",1,"/10")); capture.addView(importance);

        TextView valenceLabel=text("FEELING · neutral (0)",10,MUTED,true); capture.addView(valenceLabel);
        SeekBar valence=seek(10,5); valence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){int v=p-5;valenceLabel.setText("FEELING · "+affectName(v)+" ("+(v>0?"+":"")+v+")");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); capture.addView(valence);

        LinearLayout signalRow=new LinearLayout(this); signalRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout confBox=new LinearLayout(this); confBox.setOrientation(LinearLayout.VERTICAL);
        TextView confidenceLabel=text("CONFIDENCE · 7/10",10,MUTED,true); confBox.addView(confidenceLabel);
        SeekBar confidence=seek(9,6); confidence.setOnSeekBarChangeListener(labelListener(confidenceLabel,"CONFIDENCE",1,"/10")); confBox.addView(confidence);
        signalRow.addView(confBox,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout novBox=new LinearLayout(this); novBox.setOrientation(LinearLayout.VERTICAL);
        TextView noveltyLabel=text("NOVELTY · 5/10",10,MUTED,true); novBox.addView(noveltyLabel);
        SeekBar novelty=seek(9,4); novelty.setOnSeekBarChangeListener(labelListener(noveltyLabel,"NOVELTY",1,"/10")); novBox.addView(novelty);
        signalRow.addView(novBox,new LinearLayout.LayoutParams(0,-2,1f));
        capture.addView(signalRow);

        CheckBox active=new CheckBox(this); active.setText("Keep in working memory"); active.setTextColor(TEXT); active.setButtonTintList(ColorStateList.valueOf(ACCENT2));
        capture.addView(active);

        Button save=button("ENCODE MEMORY",ACCENT,BG);
        save.setOnClickListener(v->{
            String t=thought.getText().toString().trim(); if(t.isEmpty()){toast("Give the cortex a signal to encode.");return;}
            String tg=tags.getText().toString().trim(); if(tg.isEmpty())tg=engine.autoTags(t);
            db.addMemoryRich(t,type.getSelectedItem().toString(),tg,importance.getProgress()+1,valence.getProgress()-5,
                    confidence.getProgress()+1,novelty.getProgress()+1,active.isChecked());
            thought.setText(""); tags.setText(""); importance.setProgress(6); valence.setProgress(5); confidence.setProgress(6); novelty.setProgress(4); active.setChecked(false);
            toast("Neural trace encoded."); showCortex();
        });
        capture.addView(save,margins(-1,dp(52),0,10,0,0));
        body.addView(capture,margins(-1,-2,0,0,0,12));

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button recall=button("RECALL",PANEL2,ACCENT2); recall.setOnClickListener(v->dialog("Recall Spark",engine.answer("recall")));
        Button wander=button("WANDER",PANEL2,ACCENT); wander.setOnClickListener(v->dialog("Default Mode",engine.answer("wander")));
        Button sleep=button("CONSOLIDATE",PANEL2,WARM); sleep.setOnClickListener(v->dialog("Consolidation",engine.consolidate()));
        actions.addView(recall,weight()); addWeighted(actions,wander,6); addWeighted(actions,sleep,6);
        body.addView(actions,margins(-1,dp(48),0,0,0,14));

        body.addView(text("STRONGEST ACTIVE TRACES",10,MUTED,true),margins(-1,-2,0,0,0,7));
        List<MemoryNode> strong=db.strongest(6);
        if(strong.isEmpty())body.addView(emptyCard("The brain is quiet. Your first capture becomes node zero."));
        else for(MemoryNode m:strong)body.addView(memoryMini(m),margins(-1,-2,0,0,0,8));
        setPage(body);
    }

    private void showTalk() {
        selectNav(1);
        LinearLayout frame=new LinearLayout(this); frame.setOrientation(LinearLayout.VERTICAL); frame.setPadding(dp(14),dp(4),dp(14),dp(8));
        frame.addView(sectionTitle("INNER DIALOGUE", "Talk to memory, intuition, prediction, imagination and metacognition."));
        chatScroll=new ScrollView(this); chatStream=new LinearLayout(this); chatStream.setOrientation(LinearLayout.VERTICAL); chatStream.setPadding(0,dp(3),0,dp(8)); chatScroll.addView(chatStream);
        frame.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1f));
        addChat(false,"I can now remember, connect, predict, imagine, wander, consolidate, inspect contradictions, surface blind spots, model you, compare decisions, and compress intuition from your own memories.");

        HorizontalScrollView quickScroll=new HorizontalScrollView(this); quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL);
        String[] chips={"RECALL","WANDER","INTUITION","PREDICT","DREAM","BLIND SPOTS","CONTRADICTIONS","BRAIN STATUS"};
        for(String chip:chips){Button b=button(chip,PANEL2,MUTED);b.setTextSize(9);b.setOnClickListener(v->sendTalk(chip.toLowerCase(Locale.US)));quick.addView(b,margins(-2,dp(40),0,0,5,0));}
        quickScroll.addView(quick); frame.addView(quickScroll,margins(-1,dp(44),0,4,0,5));

        LinearLayout inputRow=new LinearLayout(this); inputRow.setOrientation(LinearLayout.HORIZONTAL);
        talkInput=edit("Ask the second brain…",2); talkInput.setSingleLine(false); talkInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputRow.addView(talkInput,new LinearLayout.LayoutParams(0,dp(58),1f));
        Button mic=button("MIC",PANEL2,ACCENT2);mic.setOnClickListener(v->startSpeech());inputRow.addView(mic,margins(dp(58),dp(58),7,0,0,0));
        Button send=button("SEND",ACCENT,BG);send.setOnClickListener(v->sendTalk(talkInput.getText().toString()));inputRow.addView(send,margins(dp(70),dp(58),7,0,0,0));
        frame.addView(inputRow);
        content.removeAllViews(); content.addView(frame);
    }

    private void sendTalk(String message) {
        String m=message==null?"":message.trim(); if(m.isEmpty())return; if(talkInput!=null)talkInput.setText("");
        addChat(true,m); addChat(false,engine.answer(m)); if(chatScroll!=null)chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addChat(boolean user,String message) {
        if(chatStream==null)return; LinearLayout bubble=card(); bubble.setBackground(round(user?0xFF24345D:PANEL2,15));
        bubble.addView(text(user?"YOU":"ECHOCORE",9,user?0xFFAFC4FF:ACCENT2,true)); TextView body=text(message,14,TEXT,false);body.setLineSpacing(0,1.12f);bubble.addView(body,margins(-1,-2,0,4,0,0));
        chatStream.addView(bubble,margins(-1,-2,user?42:0,4,user?0:42,8));
    }

    private void showMemory() {
        selectNav(2); LinearLayout body=page(); body.addView(sectionTitle("MEMORY SYSTEMS", "Episodic, semantic, procedural and prospective traces share one searchable vault."));
        EditText search=edit("Search memory, tags, or type…",1);body.addView(search,margins(-1,dp(50),0,0,0,7));
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        Runnable refresh=()->renderMemoryResults(results,search.getText().toString());
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        Button find=button("SEARCH",ACCENT,BG);find.setOnClickListener(v->refresh.run()); controls.addView(find,weight());
        Button export=button("EXPORT",PANEL2,ACCENT2);export.setOnClickListener(v->beginExport());addWeighted(controls,export,6);
        Button imp=button("IMPORT",PANEL2,ACCENT);imp.setOnClickListener(v->beginImport());addWeighted(controls,imp,6);
        body.addView(controls,margins(-1,dp(46),0,0,0,10)); body.addView(results); renderMemoryResults(results,""); setPage(body);
    }

    private void renderMemoryResults(LinearLayout results,String query) {
        results.removeAllViews(); List<MemoryNode> list=db.search(query,80); if(list.isEmpty()){results.addView(emptyCard("No matching memories."));return;}
        for(MemoryNode m:list){
            LinearLayout c=card();
            LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
            String flags=(m.pinned?"PIN · ":"")+(m.active?"ACTIVE · ":"")+m.type+" · "+m.importance+"/10";
            top.addView(text(flags,9,m.active?ACCENT2:(m.pinned?WARM:MUTED),true),new LinearLayout.LayoutParams(0,-2,1f)); top.addView(text(BrainEngine.formatDate(m.createdAt),9,MUTED,false)); c.addView(top);
            c.addView(text(m.text,15,TEXT,false),margins(-1,-2,0,7,0,4));
            if(!m.tags.isEmpty())c.addView(text("# "+m.tags,10,ACCENT,false));
            c.addView(text("feeling "+signed(m.valence)+" · confidence "+m.confidence+" · novelty "+m.novelty+" · rehearsed "+m.accessCount,9,MUTED,false),margins(-1,-2,0,3,0,5));
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            Button active=button(m.active?"RELEASE":"ACTIVATE",PANEL2,ACCENT2);active.setOnClickListener(v->{db.setActive(m.id,!m.active);renderMemoryResults(results,query);});row.addView(active,weight());
            Button pin=button(m.pinned?"UNPIN":"PIN",PANEL2,WARM);pin.setOnClickListener(v->{db.setPinned(m.id,!m.pinned);renderMemoryResults(results,query);});addWeighted(row,pin,5);
            Button del=button("DELETE",0xFF28161C,DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete memory?").setMessage(m.text).setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{db.delete(m.id);renderMemoryResults(results,query);}).show());addWeighted(row,del,5);
            c.addView(row,margins(-1,dp(42),0,7,0,0)); results.addView(c,margins(-1,-2,0,0,0,8));
        }
    }

    private void showSynapse() {
        selectNav(3); LinearLayout body=page(); body.addView(sectionTitle("SYNAPTIC NETWORK", "Associations strengthen through deliberate links, co-recall, imagination and consolidation."));
        BrainGraphView graph=new BrainGraphView(this,db);body.addView(graph,margins(-1,dp(450),0,4,0,10));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button connect=button("CONNECT X + Y",PANEL2,ACCENT);connect.setOnClickListener(v->connectDialog());row.addView(connect,weight());
        Button consolidate=button("SLEEP / CONSOLIDATE",PANEL2,WARM);consolidate.setOnClickListener(v->{dialog("Consolidation",engine.consolidate());showSynapse();});addWeighted(row,consolidate,7);body.addView(row,margins(-1,dp(46),0,0,0,12));
        body.addView(text("STRONGEST LEARNED SYNAPSES",10,MUTED,true));
        List<String> links=db.strongestAssociations(12); if(links.isEmpty())body.addView(emptyCard("Synapses appear after memories are recalled together, explicitly connected, imagined together, or consolidated."),margins(-1,-2,0,7,0,0));
        else for(String s:links)body.addView(emptyCard(s),margins(-1,-2,0,7,0,0));
        setPage(body);
    }

    private void connectDialog() {
        LinearLayout box=page(); EditText a=edit("Concept A",1),b=edit("Concept B",1);box.addView(a);box.addView(b,margins(-1,dp(48),0,8,0,0));
        new AlertDialog.Builder(this).setTitle("Build a synaptic bridge").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->dialog("Connection",engine.answer("connect "+a.getText()+" and "+b.getText()))).show();
    }

    private void showExecutive() {
        selectNav(4); LinearLayout body=page(); body.addView(sectionTitle("EXECUTIVE BRAIN", "Attention, working memory, goals, decisions, prediction and habit loops."));
        LinearLayout state=card();state.addView(text("WORKING MEMORY",11,ACCENT2,true));
        List<MemoryNode> active=db.activeWorkspace(20); state.addView(text(active.size()+" active · ideal cap 7",12,active.size()>7?DANGER:MUTED,false));
        if(active.isEmpty())state.addView(text("Activate memories in the vault or capture with ‘Keep in working memory’.",12,MUTED,false),margins(-1,-2,0,5,0,0));
        else for(MemoryNode m:active){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.addView(text("• "+m.text,13,TEXT,false),new LinearLayout.LayoutParams(0,-2,1f));Button release=button("×",PANEL2,DANGER);release.setOnClickListener(v->{db.setActive(m.id,false);showExecutive();});line.addView(release,margins(dp(38),dp(36),6,0,0,0));state.addView(line,margins(-1,-2,0,4,0,0));}
        body.addView(state,margins(-1,-2,0,0,0,10));

        LinearLayout execActions=new LinearLayout(this);execActions.setOrientation(LinearLayout.HORIZONTAL);
        Button predict=button("PREDICT",PANEL2,ACCENT);predict.setOnClickListener(v->dialog("Prediction",engine.answer("predict")));execActions.addView(predict,weight());
        Button decide=button("DECIDE",PANEL2,ACCENT2);decide.setOnClickListener(v->decisionDialog());addWeighted(execActions,decide,6);
        Button blind=button("BLIND SPOTS",PANEL2,WARM);blind.setOnClickListener(v->dialog("Metacognition",engine.answer("blind spots")));addWeighted(execActions,blind,6);
        body.addView(execActions,margins(-1,dp(45),0,0,0,12));

        body.addView(text("GOALS",10,MUTED,true)); List<MemoryNode> goals=db.byType("GOAL",10);
        if(goals.isEmpty())body.addView(emptyCard("Use ‘goal …’ in Talk or capture a GOAL memory."),margins(-1,-2,0,6,0,10));
        else for(MemoryNode g:goals){LinearLayout c=card();c.addView(text(g.text,14,TEXT,false));Button activate=button(g.active?"IN WORKING MEMORY":"ACTIVATE GOAL",PANEL2,g.active?ACCENT2:ACCENT);activate.setOnClickListener(v->{db.setActive(g.id,!g.active);showExecutive();});c.addView(activate,margins(-1,dp(40),0,7,0,0));body.addView(c,margins(-1,-2,0,6,0,0));}

        body.addView(text("HABIT LOOPS",10,MUTED,true),margins(-1,-2,0,12,0,5)); Button addHabit=button("+ CREATE HABIT LOOP",ACCENT,BG);addHabit.setOnClickListener(v->habitDialog());body.addView(addHabit,margins(-1,dp(45),0,0,0,8));
        List<String[]> habits=db.habits(); if(habits.isEmpty())body.addView(emptyCard("A habit loop stores cue → action → reward and tracks repetition."));
        for(String[] h:habits){LinearLayout c=card();c.addView(text(h[1]+" · streak "+h[5],14,ACCENT2,true));if(!h[2].isEmpty())c.addView(text("Cue: "+h[2],11,MUTED,false));if(!h[3].isEmpty())c.addView(text("Action: "+h[3],11,TEXT,false));if(!h[4].isEmpty())c.addView(text("Reward: "+h[4],11,MUTED,false));LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);Button done=button("DONE TODAY",PANEL2,ACCENT2);done.setOnClickListener(v->{db.completeHabit(Long.parseLong(h[0]));showExecutive();});r.addView(done,weight());Button delete=button("DELETE",0xFF28161C,DANGER);delete.setOnClickListener(v->{db.deleteHabit(Long.parseLong(h[0]));showExecutive();});addWeighted(r,delete,7);c.addView(r,margins(-1,dp(40),0,7,0,0));body.addView(c,margins(-1,-2,0,0,0,8));}
        setPage(body);
    }

    private void decisionDialog() {
        EditText e=edit("Option A vs Option B",1);new AlertDialog.Builder(this).setTitle("Decision workspace").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Compare",(d,w)->dialog("Decision",engine.answer("decide "+e.getText()))).show();
    }

    private void habitDialog() {
        LinearLayout box=page(); EditText name=edit("Habit name",1),cue=edit("Cue / trigger",1),action=edit("Action",1),reward=edit("Reward / completion signal",1);
        box.addView(name);box.addView(cue,margins(-1,dp(48),0,7,0,0));box.addView(action,margins(-1,dp(48),0,7,0,0));box.addView(reward,margins(-1,dp(48),0,7,0,0));
        new AlertDialog.Builder(this).setTitle("Create habit loop").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{db.addHabit(name.getText().toString(),cue.getText().toString(),action.getText().toString(),reward.getText().toString());showExecutive();}).show();
    }

    private void showSelf() {
        selectNav(5); LinearLayout body=page(); body.addView(sectionTitle("SELF MODEL + INTEROCEPTION", "Identity, values, beliefs and internal state guide what receives attention."));
        LinearLayout model=card();model.addView(text("CURRENT SELF MODEL",11,ACCENT2,true));model.addView(text(engine.answer("who am i"),13,TEXT,false),margins(-1,-2,0,6,0,0));
        Button contradiction=button("CHECK BELIEF TENSIONS",PANEL2,WARM);contradiction.setOnClickListener(v->dialog("Cognitive Tensions",engine.answer("contradictions")));model.addView(contradiction,margins(-1,dp(42),0,8,0,0));body.addView(model,margins(-1,-2,0,0,0,10));

        LinearLayout state=card();state.addView(text("INTERNAL STATE",11,ACCENT,true));
        stateSeek(state,"MOOD",-5,5,db.getStateInt("mood",0),v->db.setState("mood",String.valueOf(v)));
        stateSeek(state,"ENERGY",1,10,db.getStateInt("energy",6),v->db.setState("energy",String.valueOf(v)));
        stateSeek(state,"CURIOSITY",1,10,db.getStateInt("curiosity",7),v->db.setState("curiosity",String.valueOf(v)));
        stateSeek(state,"MENTAL LOAD",1,10,db.getStateInt("mental_load",4),v->db.setState("mental_load",String.valueOf(v)));
        body.addView(state,margins(-1,-2,0,0,0,10));

        LinearLayout add=card();add.addView(text("TEACH THE SELF MODEL",11,ACCENT2,true)); EditText statement=edit("I am… / I value… / I believe…",2);add.addView(statement,margins(-1,dp(70),0,7,0,7));
        Spinner type=new Spinner(this);String[] ts={"SELF","VALUE","BELIEF"};ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ts);type.setAdapter(a);type.setBackground(round(PANEL2,10));add.addView(type,margins(-1,dp(45),0,0,0,7));
        Button teach=button("UPDATE SELF MODEL",ACCENT,BG);teach.setOnClickListener(v->{String s=statement.getText().toString().trim();if(!s.isEmpty()){db.addMemoryRich(s,type.getSelectedItem().toString(),engine.autoTags(s),8,engine.inferValence(s),8,5,true);showSelf();}});add.addView(teach,margins(-1,dp(45),0,0,0,0));body.addView(add,margins(-1,-2,0,0,0,10));

        body.addView(text("COGNITIVE SYSTEMS ONLINE",10,MUTED,true));
        String[] modules={
                "PERCEPTION · text + voice intake becomes structured memory",
                "ATTENTION · salience and a 7-slot working workspace compete for focus",
                "EPISODIC MEMORY · timestamped events and personal traces",
                "SEMANTIC MEMORY · tags, recurring concepts and consolidated patterns",
                "PROCEDURAL MEMORY · SKILL and PROCEDURE traces",
                "AFFECT · feeling valence changes salience and intuition",
                "EXECUTIVE CONTROL · goals, decisions, mental-load tracking and focus",
                "PREDICTION · extrapolates from repeated activated patterns",
                "IMAGINATION · cross-domain recombination of distant memories",
                "DEFAULT MODE · mind-wandering resurfaces unlikely associations",
                "METACOGNITION · blind-spot and contradiction scans",
                "CONSOLIDATION · rehearsal strengthens synapses while weak traces simply recede",
                "HABIT LEARNING · cue → action → reward loops with streak reinforcement",
                "SELF MODEL · identity, values, beliefs and goals stay revisable"
        };
        for(String m:modules)body.addView(emptyCard(m),margins(-1,-2,0,5,0,0));
        setPage(body);
    }

    private interface IntSink { void accept(int value); }
    private SeekBar stateSeek(LinearLayout parent,String label,int min,int max,int current,IntSink sink) {
        TextView t=text(label+" · "+current+(min<0?"":"/10"),10,MUTED,true);parent.addView(t,margins(-1,-2,0,6,0,0));
        SeekBar s=seek(max-min,current-min);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int p,boolean from){int v=p+min;t.setText(label+" · "+v+(min<0?"":"/10"));if(from)sink.accept(v);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});parent.addView(s);return s;
    }

    private void startSpeech() {
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to EchoCore");
        try{startActivityForResult(i,REQ_SPEECH);}catch(ActivityNotFoundException e){toast("No speech-recognition service is installed.");}
    }

    private void beginExport() { Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"EchoCore-brain-v2.json");startActivityForResult(i,REQ_EXPORT); }
    private void beginImport() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,REQ_IMPORT); }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null)return;
        try{
            if(requestCode==REQ_SPEECH){ArrayList<String>r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r!=null&&!r.isEmpty()){if(talkInput!=null)talkInput.setText(r.get(0));sendTalk(r.get(0));}}
            else if(requestCode==REQ_EXPORT){Uri uri=data.getData();if(uri!=null)writeExport(uri);}
            else if(requestCode==REQ_IMPORT){Uri uri=data.getData();if(uri!=null)readImport(uri);}
        }catch(Exception e){dialog("EchoCore file error",e.getMessage()==null?e.toString():e.getMessage());}
    }

    private void writeExport(Uri uri) throws Exception {
        JSONArray arr=new JSONArray();for(MemoryNode m:db.recent(100000)){JSONObject o=new JSONObject();o.put("text",m.text);o.put("type",m.type);o.put("tags",m.tags);o.put("importance",m.importance);o.put("createdAt",m.createdAt);o.put("pinned",m.pinned);o.put("valence",m.valence);o.put("confidence",m.confidence);o.put("novelty",m.novelty);o.put("lastAccessed",m.lastAccessed);o.put("accessCount",m.accessCount);o.put("active",m.active);arr.put(o);}
        JSONObject root=new JSONObject();root.put("app","EchoCore Second Brain");root.put("version",2);root.put("exportedAt",System.currentTimeMillis());root.put("memories",arr);
        JSONArray state=new JSONArray();String[] keys={"mood","energy","curiosity","mental_load","self_name"};for(String k:keys){JSONObject o=new JSONObject();o.put("key",k);o.put("value",db.getState(k,""));state.put(o);}root.put("brainState",state);
        JSONArray habits=new JSONArray();for(String[]h:db.habits()){JSONObject o=new JSONObject();o.put("name",h[1]);o.put("cue",h[2]);o.put("action",h[3]);o.put("reward",h[4]);o.put("streak",Integer.parseInt(h[5]));habits.put(o);}root.put("habits",habits);
        try(OutputStream os=getContentResolver().openOutputStream(uri)){if(os==null)throw new Exception("Could not open export destination.");os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));}toast("Brain exported.");
    }

    private void readImport(Uri uri) throws Exception {
        StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}JSONObject root=new JSONObject(b.toString());JSONArray arr=root.getJSONArray("memories");int added=0;
        for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);long id=db.addMemoryAtRich(o.optString("text"),o.optString("type","THOUGHT"),o.optString("tags"),o.optInt("importance",5),o.optLong("createdAt",System.currentTimeMillis()),o.optBoolean("pinned",false),o.optInt("valence",0),o.optInt("confidence",7),o.optInt("novelty",5),o.optLong("lastAccessed",0),o.optInt("accessCount",0),o.optBoolean("active",false));if(id>0)added++;}
        JSONArray state=root.optJSONArray("brainState");if(state!=null)for(int i=0;i<state.length();i++){JSONObject o=state.getJSONObject(i);db.setState(o.optString("key"),o.optString("value"));}
        JSONArray habits=root.optJSONArray("habits");if(habits!=null)for(int i=0;i<habits.length();i++){JSONObject o=habits.getJSONObject(i);db.addHabit(o.optString("name"),o.optString("cue"),o.optString("action"),o.optString("reward"));}
        toast("Imported "+added+" memories.");showMemory();
    }

    private LinearLayout page(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(14),dp(4),dp(14),dp(24));return b;}
    private void setPage(LinearLayout body){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(body);content.removeAllViews();content.addView(s);}
    private View sectionTitle(String title,String subtitle){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(text(title,18,TEXT,true));box.addView(text(subtitle,12,MUTED,false),margins(-1,-2,0,3,0,0));box.setPadding(0,dp(5),0,dp(11));return box;}
    private LinearLayout statCard(String value,String label){LinearLayout c=card();c.setGravity(Gravity.CENTER);c.setPadding(dp(6),dp(11),dp(6),dp(11));c.addView(text(value,19,ACCENT2,true));c.addView(text(label,8,MUTED,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(2),0,dp(2),0);c.setLayoutParams(p);return c;}
    private View memoryMini(MemoryNode m){LinearLayout c=card();String f=(m.active?"ACTIVE · ":"")+(m.pinned?"PIN · ":"")+m.type+" · "+BrainEngine.formatDate(m.createdAt);c.addView(text(f,9,m.active?ACCENT2:(m.pinned?WARM:MUTED),true));c.addView(text(m.text,14,TEXT,false),margins(-1,-2,0,4,0,0));if(!m.tags.isEmpty())c.addView(text(m.tags,10,ACCENT,false));return c;}
    private LinearLayout emptyCard(String message){LinearLayout c=card();c.addView(text(message,12,MUTED,false));return c;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(13),dp(12),dp(13),dp(12));l.setBackground(round(PANEL,16));return l;}
    private EditText edit(String hint,int lines){EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(0xFF69768D);e.setHint(hint);e.setTextSize(14);e.setGravity(Gravity.TOP|Gravity.START);e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setBackground(round(PANEL2,12));e.setMinLines(lines);e.setMaxLines(Math.max(lines,8));return e;}
    private Button button(String label,int bg,int fg){Button b=new Button(this);b.setText(label);b.setTextColor(fg);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),0,dp(8),0);b.setBackground(round(bg,12));b.setStateListAnimator(null);return b;}
    private TextView text(String s,float sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private SeekBar seek(int max,int progress){SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(progress);s.setProgressTintList(ColorStateList.valueOf(ACCENT));s.setThumbTintList(ColorStateList.valueOf(ACCENT2));return s;}
    private SeekBar.OnSeekBarChangeListener labelListener(TextView label,String prefix,int offset,String suffix){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){label.setText(prefix+" · "+(p+offset)+suffix);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
    private GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private void addWeighted(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=weight();p.setMargins(dp(left),0,0,0);row.addView(v,p);}
    private LinearLayout.LayoutParams margins(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String affectName(int v){return v>=4?"very positive":v>=2?"positive":v==1?"slightly positive":v<=-4?"very negative":v<=-2?"negative":v==-1?"slightly negative":"neutral";}
    private String signed(int v){return v>0?"+"+v:String.valueOf(v);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void dialog(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Close",null).show();}
}
