package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourceActivity extends Activity {
    private static final int BG=0xFF080A0F,PANEL=0xFF111722,PANEL2=0xFF182130,TEXT=0xFFF4F7FF,MUTED=0xFF98A4BA,ACCENT=0xFF7C9CFF,ACCENT2=0xFF56E0C5,WARM=0xFFFFB86B,DANGER=0xFFFF7A90;
    private static final int REQ_INGEST=810;
    private BrainDatabase brain;
    private SourceCatalog catalog;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout rootBody;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        brain=new BrainDatabase(this);catalog=new SourceCatalog(this);setContentView(build());render();handleIncoming(getIntent());
    }

    private View build(){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(15),dp(12),dp(15),dp(10));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("ECHOCORE · SOURCE CORTEX",20,TEXT,true));titles.addView(text("whole-file perception → linked long-term memory",10,ACCENT2,false));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1f));
        Button brainBtn=button("OMEGA HUB",PANEL2,ACCENT);brainBtn.setOnClickListener(v->startActivity(new Intent(this,CognitiveOSActivity.class)));head.addView(brainBtn,lp(dp(108),dp(42),8,0,0,0));shell.addView(head);
        ScrollView scroll=new ScrollView(this);rootBody=new LinearLayout(this);rootBody.setOrientation(LinearLayout.VERTICAL);rootBody.setPadding(dp(14),dp(4),dp(14),dp(28));scroll.addView(rootBody);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));return shell;
    }

    private void render(){
        rootBody.removeAllViews();
        rootBody.addView(section("SOURCE INTAKE","Feed EchoCore entire documents or batches. Readable content is chunked, source-linked, and also written into the neural brain as KNOWLEDGE memories."));
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.addView(stat(String.valueOf(catalog.countSources()),"SOURCES"));stats.addView(stat(String.valueOf(brain.countType("KNOWLEDGE")),"KNOWLEDGE"));stats.addView(stat(String.valueOf(brain.count()),"BRAIN NODES"));rootBody.addView(stats,lp(-1,-2,0,0,0,10));
        Button ingest=button("＋ INGEST FULL DOCS / FILES",ACCENT,BG);ingest.setOnClickListener(v->beginIngest());rootBody.addView(ingest,lp(-1,dp(54),0,0,0,7));
        rootBody.addView(text("PDF · DOCX · PPTX · XLSX · ODT/ODS/ODP · EPUB · TXT/MD · CSV · JSON · HTML/XML · RTF · code/text. Images, audio and video become sensory attachments with available metadata.",10,MUTED,false),lp(-1,-2,0,0,0,12));

        rootBody.addView(section("ASK THE SOURCES","Search imported text directly, or summarize a document by filename."));
        EditText ask=edit("Topic, phrase, or: summarize <filename>");rootBody.addView(ask,lp(-1,dp(52),0,0,0,7));
        Button search=button("SEARCH / SUMMARIZE",PANEL2,ACCENT2);search.setOnClickListener(v->answerSource(ask.getText().toString()));rootBody.addView(search,lp(-1,dp(46),0,0,0,12));

        rootBody.addView(section("RECENT SOURCES","The original file stays linked when Android grants persistent access."));
        List<String[]> sources=catalog.recentSources(20);
        if(sources.isEmpty())rootBody.addView(cardText("No source memories yet. You can also share a file straight to EchoCore from Android's Share menu."));
        for(String[] s:sources)rootBody.addView(sourceCard(s),lp(-1,-2,0,0,0,8));
    }

    private View sourceCard(String[] s){
        long id=Long.parseLong(s[0]);String name=s[1],mime=s[2],uri=s[3];int chars=Integer.parseInt(s[5]),chunks=Integer.parseInt(s[6]);
        LinearLayout c=card();c.addView(text(name,14,TEXT,true));c.addView(text(mime+" · "+chars+" chars · "+chunks+" linked chunks",10,MUTED,false),lp(-1,-2,0,3,0,6));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button sum=button("SUMMARIZE",PANEL2,ACCENT2);sum.setOnClickListener(v->summarizeSource(id,name));row.addView(sum,new LinearLayout.LayoutParams(0,dp(41),1f));
        if(uri!=null&&!uri.isEmpty()){Button open=button("OPEN",PANEL2,ACCENT);open.setOnClickListener(v->openOriginal(uri,mime));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(41),1f);p.setMargins(dp(6),0,0,0);row.addView(open,p);}
        Button del=button("REMOVE",0xFF28161C,DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Remove source catalog?").setMessage("This removes the Source Cortex copy for "+name+". Neural memories already learned from it stay in the brain.").setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)->{catalog.deleteSource(id);render();}).show());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(41),1f);p.setMargins(dp(6),0,0,0);row.addView(del,p);
        c.addView(row);return c;
    }

    private void answerSource(String q){
        q=q==null?"":q.trim();if(q.isEmpty()){toast("Give Source Cortex a topic or filename.");return;}
        String lower=q.toLowerCase(Locale.US);
        if(lower.startsWith("summarize ")){String name=q.substring(10).trim();String[] s=catalog.findSource(name);if(s==null){dialog("Source not found","I couldn't match that filename. Try part of the filename.");return;}summarizeSource(Long.parseLong(s[0]),s[1]);return;}
        List<String[]> hits=catalog.searchChunks(q,10);if(hits.isEmpty()){dialog("Source search","No direct source-text match for “"+q+"”. The neural brain may still have related associative memories.");return;}
        StringBuilder b=new StringBuilder();int i=1;for(String[] h:hits){b.append(i++).append(". ").append(h[0]).append(" · part ").append(h[1]).append("\n").append(snippet(h[2],q,360)).append("\n\n");}
        dialog("Source search · "+hits.size()+" matches",b.toString().trim());
    }

    private void summarizeSource(long id,String name){
        io.execute(()->{List<String> chunks=catalog.chunks(id,50000);String result=chunks.isEmpty()?"This source is an attachment without extracted text.":summarize(chunks);runOnUiThread(()->dialog("Summary · "+name,result));});
    }

    private String summarize(List<String> chunks){
        Map<String,Integer> freq=new HashMap<>();for(String c:chunks)for(String w:words(c)){if(!stop(w)&&w.length()>3)freq.put(w,freq.getOrDefault(w,0)+1);}
        ArrayList<Map.Entry<String,Integer>> top=new ArrayList<>(freq.entrySet());top.sort((a,b)->b.getValue()-a.getValue());ArrayList<String> concepts=new ArrayList<>();for(int i=0;i<Math.min(8,top.size());i++)concepts.add(top.get(i).getKey());
        ArrayList<String> highlights=new ArrayList<>();Set<Integer> used=new HashSet<>();for(int k=0;k<Math.min(5,chunks.size());k++){int idx=chunks.size()==1?0:(int)Math.round(k*(chunks.size()-1)/4.0);if(used.add(idx))highlights.add(trim(chunks.get(idx),260));}
        StringBuilder b=new StringBuilder("Source coverage: ").append(chunks.size()).append(" chunks.\n");if(!concepts.isEmpty())b.append("Dominant concepts: ").append(String.join(", ",concepts)).append(".\n\n");b.append("Representative passages:\n");for(String h:highlights)b.append("• ").append(h).append("\n");b.append("\nThis is an offline extractive compression of the imported text. Ask specific phrases/topics for tighter retrieval.");return b.toString().trim();
    }

    private void beginIngest(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_INGEST);}

    private void ingest(List<Uri> uris){
        if(uris==null||uris.isEmpty())return;toast("Source Cortex is reading "+uris.size()+(uris.size()==1?" file…":" files…"));
        io.execute(()->{ArrayList<String> report=new ArrayList<>();int ok=0;DocumentImporter importer=new DocumentImporter(this,brain,catalog);for(Uri uri:uris){try{try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}DocumentImporter.Result r=importer.ingest(uri);ok++;report.add("✓ "+r.name+" · "+(r.textExtracted?r.chars+" chars / "+r.chunks+" chunks":"attachment indexed"));}catch(Exception e){report.add("✕ "+(e.getMessage()==null?e.toString():e.getMessage()));}}int done=ok;runOnUiThread(()->{dialog("Ingestion complete · "+done+"/"+uris.size(),String.join("\n",report));render();});});
    }

    private void handleIncoming(Intent intent){if(intent==null)return;String a=intent.getAction();if(Intent.ACTION_SEND.equals(a)){Uri u=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null){ArrayList<Uri>x=new ArrayList<>();x.add(u);ingest(x);return;}CharSequence t=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(t!=null&&!t.toString().trim().isEmpty()){brain.addMemoryRich(t.toString(),"REFERENCE","shared",6,0,7,6,false);toast("Shared text encoded into the neural brain.");}}else if(Intent.ACTION_SEND_MULTIPLE.equals(a)){ArrayList<Uri> u=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(u!=null)ingest(u);}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIncoming(i);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_INGEST||resultCode!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();ClipData c=data.getClipData();if(c!=null){for(int i=0;i<c.getItemCount();i++)if(c.getItemAt(i).getUri()!=null)uris.add(c.getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());ingest(uris);}
    @Override protected void onDestroy(){io.shutdownNow();catalog.close();brain.close();super.onDestroy();}

    private void openOriginal(String uri,String mime){try{Uri u=Uri.parse(uri);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,mime==null||mime.isEmpty()?"*/*":mime);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("No app can open the original source right now.");}}
    private String snippet(String text,String q,int max){String low=text.toLowerCase(Locale.US),needle=q.toLowerCase(Locale.US);int at=low.indexOf(needle);if(at<0)return trim(text,max);int start=Math.max(0,at-max/3),end=Math.min(text.length(),start+max);return (start>0?"…":"")+text.substring(start,end).trim()+(end<text.length()?"…":"");}
    private static String[] words(String s){return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]"," ").split("\\s+");}
    private static boolean stop(String w){String stops=" the a an and or but to of in on for with my i me is are was were be this that it you your about from into as at we our do did have has had what how why when where should would could can just very then than so if its they them their there which who more most ";return w.isEmpty()||stops.contains(" "+w+" ");}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}

    private View section(String title,String sub){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(title,17,TEXT,true));b.addView(text(sub,11,MUTED,false),lp(-1,-2,0,3,0,0));b.setPadding(0,dp(5),0,dp(10));return b;}
    private LinearLayout stat(String value,String label){LinearLayout c=card();c.setGravity(Gravity.CENTER);c.setPadding(dp(5),dp(10),dp(5),dp(10));c.addView(text(value,18,ACCENT2,true));c.addView(text(label,8,MUTED,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(2),0,dp(2),0);c.setLayoutParams(p);return c;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(11),dp(12),dp(11));c.setBackground(round(PANEL,15));return c;}
    private View cardText(String s){LinearLayout c=card();c.addView(text(s,12,MUTED,false));return c;}
    private EditText edit(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xFF6B7890);e.setTextColor(TEXT);e.setTextSize(14);e.setSingleLine(false);e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setBackground(round(PANEL2,12));return e;}
    private Button button(String label,int bg,int fg){Button b=new Button(this);b.setText(label);b.setTextColor(fg);b.setTextSize(10);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setStateListAnimator(null);b.setBackground(round(bg,12));return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void dialog(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Close",null).show();}
}
