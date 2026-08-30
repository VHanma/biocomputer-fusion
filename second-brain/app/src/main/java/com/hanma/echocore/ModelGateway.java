package com.hanma.echocore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ModelGateway {
    private final SecurePrefs prefs;

    public ModelGateway(SecurePrefs prefs){this.prefs=prefs;}
    public boolean enabled(){return prefs.getBool("model_enabled",false) && !prefs.get("endpoint","").trim().isEmpty();}
    public String endpoint(){return prefs.get("endpoint","");}
    public String model(){return prefs.get("model","local-model");}

    public String complete(String system,String user) throws Exception{
        String endpoint=endpoint().trim();if(endpoint.isEmpty())throw new Exception("No model endpoint configured.");
        URL url=new URL(endpoint);HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(90000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        String key=prefs.getSecret("api_key");if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
        JSONObject body=new JSONObject();body.put("model",model());body.put("temperature",0.25);body.put("max_tokens",1600);
        JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",system));messages.put(new JSONObject().put("role","user").put("content",user));body.put("messages",messages);
        try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String raw=read(in);
        if(code<200||code>=300)throw new Exception("Model endpoint returned HTTP "+code+": "+trim(raw,500));
        String parsed=parseText(raw);if(parsed.trim().isEmpty())throw new Exception("Model response contained no readable text.");return parsed.trim();
    }

    private String parseText(String raw){
        try{
            JSONObject o=new JSONObject(raw);
            if(o.has("output_text"))return o.optString("output_text","");
            JSONArray choices=o.optJSONArray("choices");
            if(choices!=null&&choices.length()>0){JSONObject ch=choices.optJSONObject(0);if(ch!=null){JSONObject m=ch.optJSONObject("message");if(m!=null){Object content=m.opt("content");if(content instanceof String)return (String)content;if(content instanceof JSONArray){StringBuilder b=new StringBuilder();JSONArray a=(JSONArray)content;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null){String t=p.optString("text",p.optString("content",""));if(!t.isEmpty())b.append(t);}}return b.toString();}}String t=ch.optString("text","");if(!t.isEmpty())return t;}}
            JSONArray output=o.optJSONArray("output");if(output!=null){StringBuilder b=new StringBuilder();for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null)continue;JSONArray content=item.optJSONArray("content");if(content==null)continue;for(int j=0;j<content.length();j++){JSONObject p=content.optJSONObject(j);if(p!=null){String t=p.optString("text",p.optString("output_text",""));if(!t.isEmpty())b.append(t);}}}return b.toString();}
            return o.optString("text","");
        }catch(Exception e){return raw==null?"":raw;}
    }

    private static String read(InputStream in) throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line).append('\n');if(b.length()>2_000_000)break;}}return b.toString();}
    private static String trim(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n)+"…";}
}
