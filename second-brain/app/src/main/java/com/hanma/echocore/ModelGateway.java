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

/** Frontier model gateway. Supports OpenAI Responses API and compatible chat-completions endpoints. */
public class ModelGateway {
    private final SecurePrefs prefs;
    public ModelGateway(SecurePrefs prefs){this.prefs=prefs;ensureDefaults();}
    private void ensureDefaults(){if(prefs.get("endpoint","").trim().isEmpty())prefs.put("endpoint","https://api.openai.com/v1/responses");String m=prefs.get("model","").trim();if(m.isEmpty()||"local-model".equals(m))prefs.put("model","gpt-5.6-sol");if(!prefs.getSecret("api_key").isEmpty()&&!prefs.getBool("model_enabled",false))prefs.putBool("model_enabled",true);}
    public boolean enabled(){String ep=endpoint();if(!prefs.getBool("model_enabled",false)||ep.isEmpty())return false;if(ep.contains("api.openai.com")&&prefs.getSecret("api_key").isEmpty())return false;return true;}
    public String endpoint(){String x=prefs.get("endpoint","").trim();return x.isEmpty()?"https://api.openai.com/v1/responses":x;}
    public String model(){String x=prefs.get("model","").trim();return x.isEmpty()?"gpt-5.6-sol":x;}

    public String complete(String system,String user) throws Exception{String endpoint=endpoint().trim();if(endpoint.isEmpty())throw new Exception("No model endpoint configured.");return endpoint.contains("/responses")?responses(system,user):chat(system,user);}

    private String responses(String system,String user)throws Exception{
        HttpURLConnection c=open(endpoint());JSONObject body=new JSONObject();body.put("model",model());body.put("instructions",system);body.put("input",user);body.put("max_output_tokens",2600);body.put("reasoning",new JSONObject().put("effort",prefs.get("reasoning_effort","high")));
        if(prefs.getBool("frontier_web_search",true)){JSONArray tools=new JSONArray();tools.put(new JSONObject().put("type","web_search"));body.put("tools",tools);body.put("tool_choice","auto");}
        return sendAndParse(c,body,true);
    }

    private String chat(String system,String user)throws Exception{
        HttpURLConnection c=open(endpoint());JSONObject body=new JSONObject();body.put("model",model());body.put("temperature",0.55);body.put("max_tokens",2200);JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",system));messages.put(new JSONObject().put("role","user").put("content",user));body.put("messages",messages);return sendAndParse(c,body,false);
    }

    private HttpURLConnection open(String endpoint)throws Exception{URL url=new URL(endpoint);HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");String key=prefs.getSecret("api_key");if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);return c;}
    private String sendAndParse(HttpURLConnection c,JSONObject body,boolean responseApi)throws Exception{try{byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream os=c.getOutputStream()){os.write(data);}int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String raw=read(in);if(code<200||code>=300)throw new Exception("Model endpoint returned HTTP "+code+": "+trim(raw,700));String parsed=parseText(raw);if(parsed.trim().isEmpty())throw new Exception("Model response contained no readable text.");return parsed.trim();}finally{c.disconnect();}}

    private String parseText(String raw){try{JSONObject o=new JSONObject(raw);String direct=o.optString("output_text","");if(!direct.isEmpty())return direct;JSONArray choices=o.optJSONArray("choices");if(choices!=null&&choices.length()>0){JSONObject ch=choices.optJSONObject(0);if(ch!=null){JSONObject m=ch.optJSONObject("message");if(m!=null){Object content=m.opt("content");if(content instanceof String)return (String)content;if(content instanceof JSONArray){StringBuilder b=new StringBuilder();JSONArray a=(JSONArray)content;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null){String t=p.optString("text",p.optString("content",""));if(!t.isEmpty())b.append(t);}}return b.toString();}}String t=ch.optString("text","");if(!t.isEmpty())return t;}}JSONArray output=o.optJSONArray("output");if(output!=null){StringBuilder b=new StringBuilder();for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null)continue;JSONArray content=item.optJSONArray("content");if(content==null)continue;for(int j=0;j<content.length();j++){JSONObject p=content.optJSONObject(j);if(p==null)continue;String t=p.optString("text",p.optString("output_text",""));if(!t.isEmpty()){if(b.length()>0)b.append('\n');b.append(t);}}}return b.toString();}return o.optString("text","");}catch(Exception e){return raw==null?"":raw;}}
    private static String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line).append('\n');if(b.length()>4_000_000)break;}}return b.toString();}
    private static String trim(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n)+"…";}
}
