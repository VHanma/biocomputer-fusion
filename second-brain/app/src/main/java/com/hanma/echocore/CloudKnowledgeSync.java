package com.hanma.echocore;

import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Uploads cleaned, nonduplicate passages to the cloud archive.
 * Source text lives in SourceCatalog; quality metadata lives in AscendantStore,
 * so this class deliberately queries the two databases separately.
 * Local raw chunk bodies are released only after the server acknowledges every eligible part.
 */
public class CloudKnowledgeSync {
    private final CloudMindClient cloud;
    private final AscendantStore asc;

    public CloudKnowledgeSync(android.content.Context c,AscendantStore a){
        cloud=new CloudMindClient(c);
        asc=a;
    }

    public int sync(SourceCatalog catalog,long sourceId,String sourceName)throws Exception{
        if(catalog.cloudArchived(sourceId))return 0;
        int total=countEligible(catalog,sourceId);
        if(total<=0)throw new Exception("No refined passages eligible for cloud archive");
        long chars=sourceChars(catalog,sourceId);
        int sent=0;
        JSONArray batch=new JSONArray();

        try(Cursor c=catalog.getReadableDatabase().rawQuery(
                "SELECT part,text FROM chunks WHERE source_id=? ORDER BY part",
                new String[]{String.valueOf(sourceId)})){
            while(c.moveToNext()){
                int part=c.getInt(0);
                String raw=c.getString(1);
                Meta m=meta(sourceId,part);
                KnowledgeCleaner.Analysis a=KnowledgeCleaner.analyze(raw);
                if(a.garbage||m.duplicateOf>0||a.cleaned.trim().isEmpty()||m.quality<4)continue;

                JSONObject x=new JSONObject()
                        .put("part",part)
                        .put("heading",m.heading)
                        .put("text",a.cleaned)
                        .put("quality",Math.max(1,Math.min(10,m.quality)))
                        .put("resident_affinity",affinity(a.cleaned+" "+m.heading));
                batch.put(x);

                if(batch.length()>=24){
                    int next=sent+batch.length();
                    boolean done=next>=total;
                    JSONObject ack=cloud.uploadKnowledge(String.valueOf(sourceId),sourceName,batch,next,total,chars,done);
                    if(!ack.optBoolean("ok",false))throw new Exception("Cloud archive rejected batch at "+next+"/"+total);
                    sent=next;
                    batch=new JSONArray();
                }
            }
        }

        if(batch.length()>0){
            int next=sent+batch.length();
            JSONObject ack=cloud.uploadKnowledge(String.valueOf(sourceId),sourceName,batch,next,total,chars,true);
            if(!ack.optBoolean("ok",false))throw new Exception("Cloud archive rejected final batch");
            sent=next;
        }
        if(sent!=total)throw new Exception("Cloud archive incomplete: "+sent+"/"+total);

        catalog.markCloudArchived(sourceId);
        asc.blackbox("CLOUD_ARCHIVE","SOURCE_SYNCED",
                sourceName+" · "+sent+" refined passages · raw chunk bodies released","");
        return sent;
    }

    private int countEligible(SourceCatalog catalog,long sid){
        int n=0;
        try(Cursor c=catalog.getReadableDatabase().rawQuery(
                "SELECT part,text FROM chunks WHERE source_id=? ORDER BY part",
                new String[]{String.valueOf(sid)})){
            while(c.moveToNext()){
                int part=c.getInt(0);
                KnowledgeCleaner.Analysis a=KnowledgeCleaner.analyze(c.getString(1));
                Meta m=meta(sid,part);
                if(!a.garbage&&m.duplicateOf==0&&m.quality>=4&&!a.cleaned.trim().isEmpty())n++;
            }
        }
        return n;
    }

    private Meta meta(long sid,int part){
        try(Cursor c=asc.getReadableDatabase().rawQuery(
                "SELECT quality,duplicate_of,heading FROM knowledge_quality WHERE source_id=? AND part=? LIMIT 1",
                new String[]{String.valueOf(sid),String.valueOf(part)})){
            if(c.moveToFirst())return new Meta(c.getInt(0),c.getLong(1),safe(c.getString(2)));
        }catch(Throwable ignored){}
        return new Meta(5,0,"");
    }

    private long sourceChars(SourceCatalog c,long sid){
        String[] r=c.sourceById(sid);
        if(r==null)return 0;
        try{return Long.parseLong(r[5]);}catch(Exception e){return 0;}
    }

    private JSONArray affinity(String t)throws Exception{
        String s=safe(t).toLowerCase(java.util.Locale.US);
        JSONArray a=new JSONArray();
        if(has(s,"hermet","alchemy","kybalion","symbol","occult","thoth","egypt","kabbal"))a.put("hermes");
        if(has(s,"tesla","coil","electric","voltage","resonan","oscillat","wireless","magnetic","circuit"))a.put("tesla");
        if(has(s,"psycholog","conscious","psychedel","cyber","leary","conditioning"))a.put("leary");
        if(has(s,"alien","ufo","uap","xeno","contact","extrater","signal","star"))a.put("star-council");
        if(has(s,"android","software","code","apk","compiler","database","camera","algorithm"))a.put("rival3");
        if(has(s,"experiment","confound","evidence","causal","measurement","artifact"))a.put("rival1");
        if(has(s,"myth","art","geometry","music","creative","fiction","analogy"))a.put("rival2");
        if(has(s,"mission","strategy","plan","dependency","checkpoint"))a.put("zordon");
        if(has(s,"network","protocol","identity","wire","distributed","cybernetic"))a.put("lain");
        if(a.length()==0)a.put("omega");
        return a;
    }

    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static class Meta{final int quality;final long duplicateOf;final String heading;Meta(int q,long d,String h){quality=q;duplicateOf=d;heading=h;}}
}
