package com.hanma.echocore;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CloudLinkActivity extends Activity {
    private static final int BG=0xFF07090D,PANEL=0xFF111722,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B,DANGER=0xFFFF6B7A;
    private SecurePrefs prefs;
    private DiagnosticsStore diag;
    private TextView statusTitle,statusDetail,diagText,brainOutput;
    private EditText question;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){updateStatus();handler.postDelayed(this,1500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        prefs=new SecurePrefs(this);diag=new DiagnosticsStore(this);getWindow().setStatusBarColor(BG);
        buildUi();requestNotificationPermission();autoStart();
    }

    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(20),dp(16),dp(28));body.setBackgroundColor(BG);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        body.addView(text("ECHOCORE Ω NEXUS",25,TEXT,true));
        body.addView(text("AUTOLINK v9 · DOCSAFE COGNITIVE BRIDGE",11,ACCENT2,true),lp(-1,-2,0,3,0,16));

        LinearLayout status=card();
        statusTitle=text("NEXUS · STARTING",15,WARM,true);status.addView(statusTitle);
        statusDetail=text("Initializing…",11,MUTED,false);status.addView(statusDetail,lp(-1,-2,0,5,0,0));
        body.addView(status,lp(-1,-2,0,0,0,10));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button repair=button("CONNECT / REPAIR",ACCENT,BG);repair.setOnClickListener(v->{prefs.putBool(CloudLinkService.KEY_ENABLED,true);prefs.putBool(CloudLinkService.KEY_FORCE_REGISTER,false);autoStart();toast("Nexus reconnecting…");});
        Button reset=button("RESET LINK",PANEL,WARM);reset.setOnClickListener(v->{CloudLinkService.clearCredentials(this);prefs.putBool(CloudLinkService.KEY_ENABLED,true);autoStart();diag.event("USER","Link credentials reset");toast("Fresh secure link requested");});
        row.addView(repair,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(50),1);rp.setMargins(dp(8),0,0,0);row.addView(reset,rp);body.addView(row,lp(-1,-2,0,0,0,8));

        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        Button sources=button("SOURCE CORTEX",PANEL,ACCENT2);sources.setOnClickListener(v->safeStart(SourceActivity.class));
        Button nexus=button("FULL NEXUS BRAIN",PANEL,ACCENT);nexus.setOnClickListener(v->safeStart(NexusBrainActivity.class));
        nav.addView(sources,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(52),1);np.setMargins(dp(8),0,0,0);nav.addView(nexus,np);body.addView(nav,lp(-1,-2,0,0,0,12));

        LinearLayout thinker=card();thinker.addView(text("QUICK THINK",13,ACCENT2,true));
        thinker.addView(text("Evidence-balanced local reasoning without leaving the bridge.",10,MUTED,false),lp(-1,-2,0,3,0,7));
        question=new EditText(this);question.setHint("Question, decision, goal, or idea…");question.setHintTextColor(0xFF657086);question.setTextColor(TEXT);question.setTextSize(12);question.setSingleLine(false);question.setMinLines(2);question.setMaxLines(5);question.setPadding(dp(10),dp(10),dp(10),dp(10));question.setBackground(round(BG,10));thinker.addView(question,lp(-1,-2,0,0,0,8));
        LinearLayout thinkRow=new LinearLayout(this);thinkRow.setOrientation(LinearLayout.HORIZONTAL);
        Button deep=button("DEEP THINK",ACCENT,BG);deep.setOnClickListener(v->runBrain("DEEP"));
        Button gaps=button("GAP SCAN",PANEL,ACCENT2);gaps.setOnClickListener(v->runGapScan());
        thinkRow.addView(deep,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(48),1);gp.setMargins(dp(8),0,0,0);thinkRow.addView(gaps,gp);thinker.addView(thinkRow);
        brainOutput=text("",11,TEXT,false);brainOutput.setTextIsSelectable(true);brainOutput.setVisibility(View.GONE);thinker.addView(brainOutput,lp(-1,-2,0,9,0,0));
        body.addView(thinker,lp(-1,-2,0,0,0,12));

        LinearLayout dcard=card();dcard.addView(text("LIVE DIAGNOSTICS",13,ACCENT2,true));diagText=text("",10,MUTED,false);diagText.setTextIsSelectable(true);dcard.addView(diagText,lp(-1,-2,0,6,0,8));
        LinearLayout drow=new LinearLayout(this);drow.setOrientation(LinearLayout.HORIZONTAL);
        Button copy=button("COPY DIAGNOSTICS",PANEL,TEXT);copy.setOnClickListener(v->copyDiagnostics());
        Button stop=button("STOP LINK",PANEL,DANGER);stop.setOnClickListener(v->{prefs.putBool(CloudLinkService.KEY_ENABLED,false);stopService(new Intent(this,CloudLinkService.class));diag.setState("STOPPED");updateStatus();toast("AutoLink stopped");});
        drow.addView(copy,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),1);sp.setMargins(dp(8),0,0,0);drow.addView(stop,sp);dcard.addView(drow);body.addView(dcard,lp(-1,-2,0,0,0,12));

        body.addView(text("Nexus v9 adds DocSafe streaming ingestion: PDFs are copied to disk-backed scratch space and processed page by page; Office/EPUB/ZIP and plain text are streamed into bounded SQLite chunks; large summaries are sampled; low-memory guards stop a pathological import before Android reaches process-killing heap exhaustion.",11,MUTED,false));
    }

    private void autoStart(){
        prefs.putBool(CloudLinkService.KEY_ENABLED,true);
        Intent i=new Intent(this,CloudLinkService.class);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);diag.event("USER","AutoLink start requested");}
        catch(Throwable t){diag.error("start_service",t);toast("Service start blocked. Diagnostics captured.");}
    }

    private void updateStatus(){
        if(statusTitle==null)return;
        String state=diag.state();boolean enabled=prefs.getBool(CloudLinkService.KEY_ENABLED,true);boolean registered=prefs.getBool(CloudLinkService.KEY_REGISTERED,false);String id=prefs.get(CloudLinkService.KEY_DEVICE_ID,"");
        int color=ACCENT2;String title="NEXUS · "+state;
        if(!enabled){title="NEXUS · STOPPED";color=MUTED;}else if("SAFE_MODE".equals(state)){title="NEXUS · SAFE MODE";color=DANGER;}else if(!"LIVE".equals(state)){color=WARM;}
        statusTitle.setText(title);statusTitle.setTextColor(color);
        StringBuilder b=new StringBuilder();b.append(registered?"Registered":"Registering");if(!id.isEmpty())b.append(" · device ").append(shortId(id));
        if(diag.lastRelayMs()>0)b.append(" · ").append(diag.lastRelayMs()).append(" ms");if(diag.consecutiveFailures()>0)b.append(" · retries ").append(diag.consecutiveFailures());
        statusDetail.setText(b.toString());
        String err=diag.lastError();String shortDiag="State "+state+"\nRelay "+diag.lastRelayMs()+" ms\nRetries "+diag.consecutiveFailures()+"\nLast error "+(err==null||err.isEmpty()?"none":err);
        diagText.setText(shortDiag);
    }

    private void runBrain(String mode){
        String q=question.getText().toString().trim();if(q.isEmpty()){toast("Type something first");return;}
        brainOutput.setVisibility(View.VISIBLE);brainOutput.setText("Retrieving evidence…");
        new Thread(()->{
            BrainDatabase b=null;SourceCatalog s=null;CognitiveStore c=null;
            try{
                b=new BrainDatabase(this);BrainEngine e=new BrainEngine(b);s=new SourceCatalog(this);c=new CognitiveStore(this);NexusOrchestrator o=new NexusOrchestrator(b,e,s,c);String answer=o.localAnswer(q,mode);c.addTurn("USER",q,mode);c.addTurn("NEXUS",answer,mode);runOnUiThread(()->brainOutput.setText(answer));
            }catch(Throwable t){diag.error("quick_think",t);runOnUiThread(()->brainOutput.setText("Quick Think hit an error. It was captured in diagnostics."));}
            finally{try{if(b!=null)b.close();}catch(Throwable ignored){}try{if(s!=null)s.close();}catch(Throwable ignored){}try{if(c!=null)c.close();}catch(Throwable ignored){}}
        },"NexusQuickThink").start();
    }

    private void runGapScan(){
        brainOutput.setVisibility(View.VISIBLE);brainOutput.setText("Scanning knowledge gaps…");
        new Thread(()->{
            BrainDatabase b=null;SourceCatalog s=null;CognitiveStore c=null;
            try{b=new BrainDatabase(this);BrainEngine e=new BrainEngine(b);s=new SourceCatalog(this);c=new CognitiveStore(this);NexusOrchestrator o=new NexusOrchestrator(b,e,s,c);String answer=o.knowledgeGaps();runOnUiThread(()->brainOutput.setText(answer));}
            catch(Throwable t){diag.error("gap_scan",t);runOnUiThread(()->brainOutput.setText("Gap Scan error captured in diagnostics."));}
            finally{try{if(b!=null)b.close();}catch(Throwable ignored){}try{if(s!=null)s.close();}catch(Throwable ignored){}try{if(c!=null)c.close();}catch(Throwable ignored){}}
        },"NexusGapScan").start();
    }

    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)try{requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},81);}catch(Throwable ignored){}}
    private void copyDiagnostics(){String s=diag.snapshotText();ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("EchoCore Nexus diagnostics",s));toast("Diagnostics copied");}
    private void safeStart(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable t){diag.error("open_"+cls.getSimpleName(),t);toast("Screen error captured in diagnostics");}}
    private String shortId(String s){return s.length()>12?s.substring(0,6)+"…"+s.substring(s.length()-6):s;}
    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(12),dp(12),dp(12),dp(12));x.setBackground(round(PANEL,14));return x;}
    private TextView text(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0,1.12f);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(10);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(round(bg,12));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
