package com.hanma.echocore;

import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uploads only cleaned/nonduplicate passages. Local raw chunks are pruned only after full cloud ACK. */
public class CloudKnowledgeSync {
    private final CloudMindClient cloud;private final AscendantStore asc;
    public CloudKnowledgeSync(android.content.Context c,AscendantStore a){cloud=new CloudMindClient(c);asc=a;}
    public int sync(SourceCatalog catalog,long sourceId,String sourceName)throws Exception{
        int total=countEligible(sourceId);if(total<=0)return 0;long chars=sourceChars(catalog,sourceId);int sent=0;JSONArray batch=new JSONArray();
        try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT c.part,c.text,COALESCE(q.quality,5),COALESCE(q.duplicate_of,0),COALESCE(q.heading,'') FROM chunks c LEFT JOIN knowledge_quality q ON q.source_id=c.source_id AND q.part=c.part WHERE c.source_id=? ORDER BY c.part",new String[]{String.valueOf(sourceId)})){
            while(c.moveToNext()){
                int part=c.getInt(0),quality=c.getInt(2);long dup=c.getLong(3);String raw=c.getString(1),heading=c.getString(4);KnowledgeCleaner.Analysis a=KnowledgeCleaner.analyze(raw);if(a.garbage||dup>0||a.cleaned.trim().isEmpty()||quality<4)continue;
                JSONObject x=new JSONObject().put("part",part).put("heading",heading==null?"":heading).put("text",a.cleaned).put("quality",Math.max(1,Math.min(10,quality))).put("resident_affinity",affinity(a.cleaned+" "+heading));batch.put(x);
                if(batch.length()>=32){int next=sent+batch.length();boolean done=next>=total;JSONObject ack=cloud.uploadKnowledge(String.valueOf(sourceId),sourceName,batch,next,total,chars,done);if(!ack.optBoolean("ok",false))throw new Exception("Cloud archive rejected a batch");sent=next;batch=new JSONArray();}
            }
        }
        if(batch.length()>0){int next=sent+batch.length();JSONObject ack=cloud.uploadKnowledge(String.valueOf(sourceId),sourceName,batch,next,total,chars,true);if(!ack.optBoolean("ok",false))throw new Exception("Cloud archive rejected final batch");sent=next;}
        if(sent!=total)throw new Exception("Cloud archive incomplete: "+sent+"/"+total);catalog.markCloudArchived(sourceId);asc.blackbox("CLOUD_ARCHIVE","SOURCE_SYNCED",sourceName+" · "+sent+" refined passages · local chunk bodies released","");return sent;
    }
    private int countEligible(long sid){int n=0;try(Cursor c=asc.getReadableDatabase().rawQuery("SELECT c.text,COALESCE(q.quality,5),COALESCE(q.duplicate_of,0) FROM chunks c LEFT JOIN knowledge_quality q ON q.source_id=c.source_id AND q.part=c.part WHERE c.source_id=?",new String[]{String.valueOf(sid)})){while(c.moveToNext()){KnowledgeCleaner.Analysis a=KnowledgeCleaner.analyze(c.getString(0));if(!a.garbage&&c.getLong(2)==0&&c.getInt(1)>=4&&!a.cleaned.trim().isEmpty())n++;}}return n;}
    private long sourceChars(SourceCatalog c,long sid){String[] r=c.sourceById(sid);if(r==null)return 0;try{return Long.parseLong(r[5]);}catch(Exception e){return 0;}}
    private JSONArray affinity(String t)throws Exception{String s=t==null?"":t.toLowerCase();JSONArray a=new JSONArray();if(has(s,"hermet","alchemy","kybalion","symbol","occult","thoth","egypt","kabbal"))a.put("hermes");if(has(s,"tesla","coil","electric","voltage","resonan","oscillat","wireless","magnetic","circuit"))a.put("tesla");if(has(s,"psycholog","conscious","psychedel","cyber","leary","conditioning"))a.put("leary");if(has(s,"alien","ufo","uap","xeno","contact","extrater","signal","star"))a.put("star-council");if(has(s,"android","software","code","apk","compiler","database","camera","algorithm"))a.put("rival3");if(has(s,"experiment","confound","evidence","causal","measurement","artifact"))a.put("rival1");if(has(s,"myth","art","geometry","music","creative","fiction","analogy"))a.put("rival2");if(has(s,"mission","strategy","plan","dependency","checkpoint"))a.put("zordon");if(a.length()==0)a.put("omega");return a;}
    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
}
