package com.hanma.echocore;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Keeps resident-initiated inbox conversations alive across room replies. */
public class ThreadContinuityClient {
    private static final String BASE="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-thread";
    private static final String KEY_ID="ascendant_device_id",KEY_SECRET="ascendant_device_secret";
    private final SecurePrefs prefs;
    public ThreadContinuityClient(Context c){prefs=new SecurePrefs(c.getApplicationContext());}

    public static final class ThreadState{
        public final String threadId,residentId,context,text,reason;
        public final long messageId;
        ThreadState(String t,String r,String c,String x,String y,long m){threadId=t;residentId=r;context=c;text=x;reason=y;messageId=m;}
    }

    public ThreadState open(long messageId,String residentId)throws Exception{
        JSONObject r=post("/open",new JSONObject().put("message_id",messageId).put("resident_id",residentId));
        JSONObject m=r.optJSONObject("message");
        return new ThreadState(r.optString("thread_id",""),r.optString("resident_id",residentId),r.optString("context",""),m==null?"":m.optString("text",""),m==null?"":m.optString("reason",""),messageId);
    }

    public ThreadState prepare(long messageId,String threadId,String residentId,String userMessage)throws Exception{
        JSONObject b=new JSONObject().put("message_id",messageId).put("thread_id",threadId==null?"":threadId).put("resident_id",residentId).put("message",userMessage);
        JSONObject r=post("/prepare-reply",b);
        return new ThreadState(r.optString("thread_id",threadId),r.optString("resident_id",residentId),r.optString("context",""),"","",messageId);
    }

    public void recordReply(String threadId,String residentId,String reply)throws Exception{
        if(threadId==null||threadId.trim().isEmpty()||reply==null||reply.trim().isEmpty())return;
        post("/record-reply",new JSONObject().put("thread_id",threadId).put("resident_id",residentId).put("reply",reply));
    }

    private JSONObject post(String path,JSONObject body)throws Exception{
        String id=prefs.get(KEY_ID,""),sec=prefs.getSecret(KEY_SECRET);if(id.isEmpty()||sec.isEmpty())throw new Exception("Cloud identity unavailable");
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();try{c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Device-Id",id);c.setRequestProperty("X-Device-Secret",sec);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream o=c.getOutputStream()){o.write(bytes);}int code=c.getResponseCode();String raw=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());if(code<200||code>=300)throw new Exception("Thread HTTP "+code+": "+trim(raw,500));JSONObject r=raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);if(!r.optBoolean("ok",false))throw new Exception(r.optString("error","Thread service unavailable"));return r;}finally{c.disconnect();}}
    private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line);if(b.length()>2_000_000)break;}}return b.toString();}
    private static String trim(String s,int n){s=s==null?"":s;return s.length()<=n?s:s.substring(0,n)+"…";}
}
