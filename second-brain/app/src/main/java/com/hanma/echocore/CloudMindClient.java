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

/** Thin authenticated doorway to EchoCore Cloud Mind. No model credentials live in the APK. */
public class CloudMindClient {
    private static final String MIND="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-mind";
    private static final String COUNCIL="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-council";
    private static final String RELAY="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-relay";
    private static final String KEY_ID="ascendant_device_id",KEY_SECRET="ascendant_device_secret";
    private final Context app;private final SecurePrefs prefs;
    public CloudMindClient(Context c){app=c.getApplicationContext();prefs=new SecurePrefs(app);}

    public static class Reply {public final String text,speakerId,model,routedTo;Reply(String t,String s,String m,String r){text=t;speakerId=s;model=m;routedTo=r;}}

    public synchronized void ensureRegistered() throws Exception{
        String id=prefs.get(KEY_ID,"");String sec=prefs.getSecret(KEY_SECRET);if(!id.isEmpty()&&!sec.isEmpty())return;
        JSONObject body=new JSONObject().put("app_version","19.0.0").put("capabilities",new JSONArray().put("cloud_mind_v19").put("cloud_council").put("cloud_knowledge").put("resident_messages").put("approval_gate"));
        JSONObject r=postRaw(RELAY+"/register",body,false,25000);String ni=r.optString("device_id",""),ns=r.optString("device_secret","");if(ni.isEmpty()||ns.isEmpty())throw new Exception("Cloud identity registration failed");prefs.put(KEY_ID,ni);prefs.putSecret(KEY_SECRET,ns);
    }

    public Reply speak(String residentId,String message) throws Exception{
        ensureRegistered();
        JSONObject b=new JSONObject().put("resident_id",residentId).put("message",message).put("depth",1);
        JSONObject r=postRaw(MIND+"/speak",b,true,180000);
        return reply(r,residentId);
    }

    public Reply askSource(String residentId,String sourceId,String sourceName,String question,boolean summarize) throws Exception{
        ensureRegistered();
        JSONObject b=new JSONObject()
                .put("resident_id",residentId)
                .put("source_id",sourceId)
                .put("source_name",sourceName)
                .put("question",question)
                .put("summarize",summarize);
        JSONObject r=postRaw(MIND+"/ask-source",b,true,180000);
        return reply(r,residentId);
    }

    public JSONObject council(String topic,int residents) throws Exception{
        ensureRegistered();
        JSONObject b=new JSONObject().put("topic",topic).put("residents",Math.max(3,Math.min(7,residents)));
        JSONObject r=postRaw(COUNCIL,b,true,260000);
        if(!r.optBoolean("ok",false))throw new Exception(r.optString("error","Cloud council unavailable"));
        if(r.optString("synthesis","").trim().isEmpty())throw new Exception("Cloud council returned no synthesis");
        return r;
    }

    public JSONObject sync() throws Exception{ensureRegistered();return postRaw(MIND+"/sync",new JSONObject(),true,35000);}
    public JSONObject pulse() throws Exception{ensureRegistered();return postRaw(MIND+"/pulse",new JSONObject(),true,180000);}
    public JSONObject status() throws Exception{ensureRegistered();return postRaw(MIND+"/cloud-status",new JSONObject(),true,35000);}
    public void markRead(long id)throws Exception{ensureRegistered();postRaw(MIND+"/mark-read",new JSONObject().put("id",id),true,25000);}
    public JSONObject decideProposal(String id,String decision)throws Exception{ensureRegistered();return postRaw(MIND+"/decide-proposal",new JSONObject().put("id",id).put("decision",decision),true,35000);}
    public JSONObject uploadKnowledge(String sourceId,String sourceName,JSONArray chunks,int uploaded,int total,long chars,boolean complete)throws Exception{
        ensureRegistered();
        JSONObject b=new JSONObject().put("source_id",sourceId).put("source_name",sourceName).put("chunks",chunks).put("uploaded_parts",uploaded).put("total_parts",total).put("total_chars",chars).put("complete",complete);
        return postRaw(MIND+"/upload-knowledge",b,true,60000);
    }

    private Reply reply(JSONObject r,String residentId)throws Exception{
        if(!r.optBoolean("ok",false))throw new Exception(r.optString("error","Cloud mind unavailable"));
        String text=r.optString("reply","").trim();if(text.isEmpty())throw new Exception("Cloud mind returned an empty reply");
        return new Reply(text,r.optString("speaker_id",residentId),r.optString("model",""),r.optString("routed_to",""));
    }

    private JSONObject postRaw(String url,JSONObject body,boolean auth,int readTimeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(readTimeout);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
            if(auth){c.setRequestProperty("X-Device-Id",prefs.get(KEY_ID,""));c.setRequestProperty("X-Device-Secret",prefs.getSecret(KEY_SECRET));}
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream o=c.getOutputStream()){o.write(bytes);}
            int code=c.getResponseCode();String raw=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(code<200||code>=300)throw new Exception("Cloud HTTP "+code+": "+trim(raw,700));
            return raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
        }finally{c.disconnect();}
    }

    private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line);if(b.length()>4_000_000)break;}}return b.toString();}
    private static String trim(String s,int n){s=s==null?"":s;return s.length()<=n?s:s.substring(0,n)+"…";}
}
