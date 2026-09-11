package com.hanma.echocore;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Read-only knowledge library physically bundled in the APK.
 * Public-domain full text is stored as compressed per-passage blobs. Modern/private material
 * is represented as original derived knowledge maps so the APK does not republish raw private files.
 */
public final class CivilizationVault implements AutoCloseable {
    private static final String ASSET="civilization_vault.db", FILE="civilization_vault_v14.db";
    private final SQLiteDatabase db;

    public static synchronized boolean install(Context c){
        if(c==null)return false;File dst=new File(c.getFilesDir(),FILE);
        try{
            if(dst.exists()&&dst.length()>65536)return true;
            File tmp=new File(c.getFilesDir(),FILE+".tmp");
            try(InputStream in=c.getAssets().open(ASSET);FileOutputStream out=new FileOutputStream(tmp)){
                byte[] b=new byte[64*1024];int n;while((n=in.read(b))>0)out.write(b,0,n);out.getFD().sync();
            }
            if(dst.exists())dst.delete();if(!tmp.renameTo(dst)){copy(tmp,dst);tmp.delete();}return dst.length()>65536;
        }catch(Throwable t){return false;}
    }

    public CivilizationVault(Context c){install(c);File f=new File(c.getFilesDir(),FILE);db=SQLiteDatabase.openDatabase(f.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);}
    /** Open after initial install when only an AscendantStore is available. */
    public CivilizationVault(AscendantStore city){File parent=new File(city.getReadableDatabase().getPath()).getParentFile();File appFiles=new File(parent.getParentFile(),"files");File f=new File(appFiles,FILE);db=SQLiteDatabase.openDatabase(f.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);}

    public JSONObject stats(){JSONObject o=new JSONObject();try{put(o,"sources",scalar("SELECT COUNT(*) FROM sources"));put(o,"passages",scalar("SELECT COUNT(*) FROM passages"));put(o,"terms",scalar("SELECT COUNT(*) FROM terms"));put(o,"chars",scalar("SELECT COALESCE(SUM(char_count),0) FROM passages"));put(o,"public_domain_fulltext",scalar("SELECT COUNT(*) FROM sources WHERE source_type='PUBLIC_DOMAIN_FULLTEXT'"));put(o,"knowledge_maps",scalar("SELECT COUNT(*) FROM sources WHERE source_type='KNOWLEDGE_MAP'"));}catch(Throwable ignored){}return o;}

    public String context(String resident,String query,int limit,int maxChars){
        String id=safe(resident);List<String> toks=tokens(query);ArrayList<Hit> hits=new ArrayList<>();
        if(toks.isEmpty())toks.add(id.isEmpty()?"knowledge":id);
        StringBuilder sql=new StringBuilder("SELECT p.id,p.title,p.domain,p.residents,p.text_gz,p.priority,s.title,s.author,s.source_type,s.rights,s.provenance,s.url,SUM(t.weight) sc FROM terms t JOIN passages p ON p.id=t.passage_id JOIN sources s ON s.id=p.source_id WHERE t.term IN (");
        String[] args=new String[toks.size()];for(int i=0;i<toks.size();i++){if(i>0)sql.append(',');sql.append('?');args[i]=toks.get(i);}sql.append(") GROUP BY p.id ORDER BY (sc + p.priority*4) DESC LIMIT ").append(Math.max(4,Math.min(40,limit*4)));
        try(Cursor c=db.rawQuery(sql.toString(),args)){while(c.moveToNext()){
            String residents=c.getString(3);int affinity=affinity(residents,id);if(affinity<0)continue;String text=ungzip(c.getBlob(4));double score=c.getDouble(12)+affinity;hits.add(new Hit(c.getLong(0),c.getString(1),c.getString(2),residents,text,c.getInt(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),score));
        }}catch(Throwable ignored){}
        hits.sort((a,b)->Double.compare(b.score,a.score));StringBuilder out=new StringBuilder();int n=0;
        for(Hit h:hits){if(n>=Math.max(1,limit))break;String block="[V"+(n+1)+"] "+h.sourceTitle+(h.author==null||h.author.isEmpty()?"":" · "+h.author)+"\n"+h.sourceType+" · "+h.rights+" · "+h.domain+"\n"+trim(h.text,1150)+"\n\n";if(out.length()+block.length()>maxChars&&n>=2)break;out.append(block);n++;}
        if(n==0)return fallback(id,maxChars);return trim(out.toString(),maxChars);
    }

    public String sourceCatalog(String resident,int limit){String id=safe(resident);StringBuilder b=new StringBuilder();try(Cursor c=db.rawQuery("SELECT title,author,domain,residents,source_type,rights,passage_count FROM sources ORDER BY CASE source_type WHEN 'PUBLIC_DOMAIN_FULLTEXT' THEN 0 ELSE 1 END,title LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(200,limit*4)))})){int n=0;while(c.moveToNext()&&n<limit){if(affinity(c.getString(3),id)<0)continue;b.append("• ").append(c.getString(0));if(c.getString(1)!=null&&!c.getString(1).isEmpty())b.append(" · ").append(c.getString(1));b.append(" · ").append(c.getString(2)).append(" · ").append(c.getString(4)).append(" · ").append(c.getInt(6)).append(" passages\n");n++;}}return b.toString().trim();}

    public String deepConnections(String resident,String query,int limit){
        String base=context(resident,query,Math.max(6,limit),7000);if(base.isEmpty())return base;Set<String> domains=new LinkedHashSet<>();
        try(Cursor c=db.rawQuery("SELECT DISTINCT domain FROM sources WHERE residents LIKE ? OR residents LIKE '%omega%' LIMIT 20",new String[]{"%"+safe(resident)+"%"})){while(c.moveToNext())domains.add(c.getString(0));}catch(Throwable ignored){}
        StringBuilder b=new StringBuilder(base);b.append("\nCROSS-DOMAIN LATTICE\n");int n=0;for(String d:domains){if(n++>=8)break;b.append("• ").append(d).append('\n');}return b.toString();
    }

    public boolean hasSource(String key){try(Cursor c=db.rawQuery("SELECT 1 FROM sources WHERE source_key=? LIMIT 1",new String[]{key})){return c.moveToFirst();}}
    @Override public void close(){try{db.close();}catch(Throwable ignored){}}

    private long scalar(String q){try(Cursor c=db.rawQuery(q,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private static void put(JSONObject o,String k,long v){try{o.put(k,v);}catch(Exception ignored){}}
    private static int affinity(String residents,String id){String r=(residents==null?"":residents.toLowerCase(Locale.US));if(id.isEmpty())return 0;if(r.contains(id))return 45;if(r.contains("omega"))return 8;return -1;}
    private static List<String> tokens(String q){ArrayList<String> o=new ArrayList<>();String stop=" the a an and or but to of in on for with from into this that these those your you my are is was were be been being about what when where why how can could would should have has had not its their there then than also very more most just all ";for(String w:(q==null?"":q.toLowerCase(Locale.US)).replaceAll("[^a-z0-9]"," ").split("\\s+")){if(w.length()>2&&!stop.contains(" "+w+" ")&&!o.contains(w)){o.add(w);if(o.size()>=12)break;}}return o;}
    private static String ungzip(byte[] b){if(b==null)return "";try(GZIPInputStream in=new GZIPInputStream(new ByteArrayInputStream(b));ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(8192,b.length*3))){byte[] x=new byte[8192];int n;while((n=in.read(x))>0){out.write(x,0,n);if(out.size()>20000)break;}return out.toString("UTF-8");}catch(Throwable t){return "";}}
    private static String fallback(String id,int max){return trim("The Civilization Vault contains no strong term match for this exact wording. Root identity remains available. Search can be broadened through Hermetic, Tesla, ancient-civilization, consciousness, cyberculture, UAP/contact, signal, engineering and private-derived domain maps.",max);}
    private static String safe(String s){return s==null?"":s.trim().toLowerCase(Locale.US);}
    private static String trim(String s,int n){if(s==null)return "";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
    private static void copy(File a,File b)throws Exception{try(InputStream in=new java.io.FileInputStream(a);FileOutputStream out=new FileOutputStream(b)){byte[] x=new byte[65536];int n;while((n=in.read(x))>0)out.write(x,0,n);}}
    private static final class Hit{long id;String title,domain,residents,text,sourceTitle,author,sourceType,rights,provenance,url;int priority;double score;Hit(long id,String title,String domain,String residents,String text,int priority,String sourceTitle,String author,String sourceType,String rights,String provenance,String url,double score){this.id=id;this.title=title;this.domain=domain;this.residents=residents;this.text=text;this.priority=priority;this.sourceTitle=sourceTitle;this.author=author;this.sourceType=sourceType;this.rights=rights;this.provenance=provenance;this.url=url;this.score=score;}}
}
