package com.hanma.echocore;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Thin authenticated doorway to EchoCore Cloud Mind. No model credentials live in the APK. */
public class CloudMindClient {
    private static final String MIND="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-mind";
    private static final String COUNCIL_TURN="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-council-turn";
    private static final String RELAY="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-relay";
    private static final String KEY_ID="ascendant_device_id",KEY_SECRET="ascendant_device_secret";
    private final Context app;private final SecurePrefs prefs;
    public CloudMindClient(Context c){app=c.getApplicationContext();prefs=new SecurePrefs(app);}

    public static class Reply {public final String text,speakerId,model,routedTo;Reply(String t,String s,String m,String r){text=t;speakerId=s;model=m;routedTo=r;}}

    public synchronized void ensureRegistered() throws Exception{
        String id=prefs.get(KEY_ID,"");String sec=prefs.getSecret(KEY_SECRET);if(!id.isEmpty()&&!sec.isEmpty())return;
        JSONObject body=new JSONObject().put("app_version","19.0.0").put("capabilities",new JSONArray().put("cloud_mind_v19").put("isolated_cloud_council").put("cloud_knowledge").put("resident_messages").put("approval_gate"));
        JSONObject r=postRaw(RELAY+"/register",body,false,25000);String ni=r.optString("device_id",""),ns=r.optString("device_secret","");if(ni.isEmpty()||ns.isEmpty())throw new Exception("Cloud identity registration failed");prefs.put(KEY_ID,ni);prefs.putSecret(KEY_SECRET,ns);
    }

    public Reply speak(String residentId,String message) throws Exception{
        ensureRegistered();JSONObject b=new JSONObject().put("resident_id",residentId).put("message",message).put("depth",1);return reply(postRaw(MIND+"/speak",b,true,180000),residentId);
    }

    public Reply askSource(String residentId,String sourceId,String sourceName,String question,boolean summarize) throws Exception{
        ensureRegistered();JSONObject b=new JSONObject().put("resident_id",residentId).put("source_id",sourceId).put("source_name",sourceName).put("question",question).put("summarize",summarize);return reply(postRaw(MIND+"/ask-source",b,true,180000),residentId);
    }

    /**
     * Council is deliberately orchestrated as separate cloud invocations. One resident can fail
     * without exhausting or killing a single giant Edge worker. The phone only coordinates JSON.
     */
    public JSONObject council(String topic,int requested) throws Exception{
        ensureRegistered();
        String q=topic==null?"":topic.trim();if(q.isEmpty())throw new Exception("Council needs a topic");
        List<String> ids=councilResidents(q,Math.max(3,Math.min(5,requested)));
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(4,ids.size()));
        ArrayList<Callable<JSONObject>> tasks=new ArrayList<>();
        for(int i=0;i<ids.size();i++){
            final String id=ids.get(i);final long delay=i*700L;
            tasks.add(()->{try{if(delay>0)Thread.sleep(delay);return councilTurn(id,q,"voice","");}catch(Throwable t){return new JSONObject().put("ok",false).put("resident_id",id).put("error",trim(String.valueOf(t.getMessage()),400));}});
        }
        JSONArray voices=new JSONArray();StringBuilder transcript=new StringBuilder();
        try{
            List<Future<JSONObject>> futures=pool.invokeAll(tasks,195,TimeUnit.SECONDS);
            for(Future<JSONObject> f:futures){
                if(f.isCancelled())continue;JSONObject r;try{r=f.get();}catch(Throwable t){continue;}
                if(!r.optBoolean("ok",false))continue;String text=r.optString("reply","").trim();if(text.isEmpty())continue;
                JSONObject v=new JSONObject().put("resident_id",r.optString("resident_id","")).put("name",r.optString("name",displayName(r.optString("resident_id","resident")))).put("model",r.optString("model","")).put("reply",text);voices.put(v);transcript.append(v.optString("name")).append(": ").append(text).append("\n\n");
            }
        }finally{pool.shutdownNow();}
        if(voices.length()==0)throw new Exception("No Council resident completed a cloud turn");
        JSONObject omega=councilTurn("omega",q,"synthesis",transcript.toString());
        if(!omega.optBoolean("ok",false)||omega.optString("reply","").trim().isEmpty())throw new Exception(omega.optString("error","Omega synthesis unavailable"));
        return new JSONObject().put("ok",true).put("strategy","isolated_resident_turns").put("participants",new JSONArray(ids)).put("voices",voices).put("synthesis",omega.optString("reply")).put("synthesis_model",omega.optString("model",""));
    }

    private JSONObject councilTurn(String residentId,String topic,String mode,String voices)throws Exception{
        JSONObject b=new JSONObject().put("resident_id",residentId).put("topic",topic).put("mode",mode);if(voices!=null&&!voices.isEmpty())b.put("voices",voices);return postRaw(COUNCIL_TURN,b,true,"synthesis".equals(mode)?180000:160000);
    }

    private List<String> councilResidents(String q,int cap){
        String s=q.toLowerCase(Locale.US);Set<String> ids=new LinkedHashSet<>();
        if(has(s,"hermet","alchemy","kybalion","symbol","ancient","history","myth","philosoph"))ids.add("hermes");
        if(has(s,"tesla","electric","coil","circuit","frequency","resonan","field","wireless","energy","machine"))ids.add("tesla");
        if(has(s,"apk","android","software","code","database","build","bug","camera","algorithm"))ids.add("rival3");
        if(has(s,"alien","ufo","uap","contact","xeno","signal","space","star"))ids.add("star-council");
        if(has(s,"leary","psychedel","conditioning","consciousness","psychology"))ids.add("leary");
        if(has(s,"lain","wire","network","digital identity","protocol","distributed","cyber"))ids.add("lain");
        if(has(s,"mission","plan","roadmap","strategy","dependency","coordinate"))ids.add("zordon");
        ids.add("rival1");ids.add("rival2");ids.add("sol");ids.add("rival3");ids.add("hermes");ids.add("tesla");
        ArrayList<String> out=new ArrayList<>();for(String id:ids){out.add(id);if(out.size()>=cap)break;}return out;
    }

    public JSONObject sync() throws Exception{ensureRegistered();return postRaw(MIND+"/sync",new JSONObject(),true,35000);}
    public JSONObject pulse() throws Exception{ensureRegistered();return postRaw(MIND+"/pulse",new JSONObject(),true,180000);}
    public JSONObject status() throws Exception{ensureRegistered();return postRaw(MIND+"/cloud-status",new JSONObject(),true,35000);}
    public void markRead(long id)throws Exception{ensureRegistered();postRaw(MIND+"/mark-read",new JSONObject().put("id",id),true,25000);}
    public JSONObject decideProposal(String id,String decision)throws Exception{ensureRegistered();return postRaw(MIND+"/decide-proposal",new JSONObject().put("id",id).put("decision",decision),true,35000);}
    public JSONObject uploadKnowledge(String sourceId,String sourceName,JSONArray chunks,int uploaded,int total,long chars,boolean complete)throws Exception{ensureRegistered();JSONObject b=new JSONObject().put("source_id",sourceId).put("source_name",sourceName).put("chunks",chunks).put("uploaded_parts",uploaded).put("total_parts",total).put("total_chars",chars).put("complete",complete);return postRaw(MIND+"/upload-knowledge",b,true,60000);}

    private Reply reply(JSONObject r,String residentId)throws Exception{
        if(!r.optBoolean("ok",false))throw new Exception(r.optString("error","Cloud mind unavailable"));String text=r.optString("reply","").trim();if(text.isEmpty())throw new Exception("Cloud mind returned an empty reply");return new Reply(text,r.optString("speaker_id",residentId),r.optString("model",""),r.optString("routed_to",""));
    }

    private JSONObject postRaw(String url,JSONObject body,boolean auth,int readTimeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(readTimeout);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");if(auth){c.setRequestProperty("X-Device-Id",prefs.get(KEY_ID,""));c.setRequestProperty("X-Device-Secret",prefs.getSecret(KEY_SECRET));}
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream o=c.getOutputStream()){o.write(bytes);}int code=c.getResponseCode();String raw=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());if(code<200||code>=300)throw new Exception("Cloud HTTP "+code+": "+trim(raw,700));return raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
        }finally{c.disconnect();}
    }

    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String displayName(String id){if("star-council".equals(id))return "Star Council";if("rival1".equals(id))return "Rival 1";if("rival2".equals(id))return "Rival 2";if("rival3".equals(id))return "Rival 3";if("leary".equals(id))return "Timothy Leary";if(id==null||id.isEmpty())return "Resident";return Character.toUpperCase(id.charAt(0))+id.substring(1);}
    private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line);if(b.length()>4_000_000)break;}}return b.toString();}
    private static String trim(String s,int n){s=s==null?"":s;return s.length()<=n?s:s.substring(0,n)+"…";}
}
