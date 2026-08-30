package com.hanma.echocore;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class CloudLinkService extends Service {
    public static final String PREF="echocore_omega_settings";
    public static final String KEY_URI="cloudlink_uri";
    public static final String KEY_ENABLED="cloudlink_enabled";
    public static final String KEY_MAILBOX="cloudlink_mailbox_id";
    private volatile boolean running;
    private Thread worker;
    private BrainDatabase brain;
    private BrainEngine engine;
    private SourceCatalog sources;
    private CognitiveStore store;
    private CognitiveOrchestrator omega;

    @Override public void onCreate(){super.onCreate();brain=new BrainDatabase(this);engine=new BrainEngine(brain);sources=new SourceCatalog(this);store=new CognitiveStore(this);omega=new CognitiveOrchestrator(brain,engine,sources,store);createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){startForeground(18433,new android.app.Notification.Builder(this,Build.VERSION.SDK_INT>=26?"echocore_cloudlink":null).setContentTitle("EchoCore CloudLink").setContentText("Shared brain mailbox active").setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).build());if(!running){running=true;worker=new Thread(this::loop,"EchoCoreCloudLink");worker.start();}return START_STICKY;}
    @Override public void onDestroy(){running=false;if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("echocore_cloudlink","EchoCore CloudLink",NotificationManager.IMPORTANCE_LOW));}}
    private void loop(){while(running){try{syncOnce();Thread.sleep(5000);}catch(InterruptedException e){break;}catch(Exception e){try{Thread.sleep(8000);}catch(Exception ignored){break;}}}}

    private void syncOnce() throws Exception{
        android.content.SharedPreferences p=getSharedPreferences(PREF,MODE_PRIVATE);if(!p.getBoolean(KEY_ENABLED,false))return;String rawUri=p.getString(KEY_URI,"");if(rawUri==null||rawUri.isEmpty())return;Uri uri=Uri.parse(rawUri);JSONObject box=readBox(uri);if(box==null||!"echocore-cloudlink-v1".equals(box.optString("protocol")))return;String mailbox=box.optString("mailbox_id","");if(mailbox.isEmpty())return;p.edit().putString(KEY_MAILBOX,mailbox).apply();
        JSONObject device=box.optJSONObject("device");if(device==null){device=new JSONObject();box.put("device",device);}device.put("status","online");device.put("last_seen",System.currentTimeMillis());device.put("app_version","6.0.0");device.put("capabilities",new JSONArray(new String[]{"brain_answer","brain_search","memory_add","source_import_text","source_search","projects_list","project_add","task_add","consolidate","capabilities"}));
        JSONArray commands=box.optJSONArray("commands");if(commands==null){commands=new JSONArray();box.put("commands",commands);}JSONArray responses=box.optJSONArray("responses");if(responses==null){responses=new JSONArray();box.put("responses",responses);}Set<String> responded=new HashSet<>();for(int i=0;i<responses.length();i++){JSONObject r=responses.optJSONObject(i);if(r!=null)responded.add(r.optString("command_id",""));}
        for(int i=0;i<commands.length();i++){JSONObject c=commands.optJSONObject(i);if(c==null)continue;String id=c.optString("id","");if(id.isEmpty()||responded.contains(id)||!"queued".equals(c.optString("status","queued")))continue;JSONObject out=new JSONObject();out.put("command_id",id);out.put("created_at",System.currentTimeMillis());try{JSONObject payload=c.optJSONObject("payload");if(payload==null)payload=new JSONObject();out.put("status","ok");out.put("payload",execute(c.optString("action",""),payload));c.put("status","completed");}catch(Exception e){out.put("status","error");out.put("payload",new JSONObject().put("message",safe(e)));c.put("status","failed");}responses.put(out);}
        box.put("revision",box.optLong("revision",0)+1);box.put("updated_at",System.currentTimeMillis());writeBox(uri,box);
    }

    private Object execute(String action,JSONObject payload) throws Exception{
        switch(action){
            case "brain_answer":{String q=payload.optString("question",payload.optString("q",""));String mode=payload.optString("mode","DEEP").toUpperCase(Locale.US);String a=omega.localAnswer(q,mode);store.addTurn("CLOUD",q,mode);store.addTurn("OMEGA",a,mode);return new JSONObject().put("answer",a).put("mode",mode);}
            case "brain_search":{JSONArray a=new JSONArray();for(MemoryNode m:brain.search(payload.optString("q",""),Math.max(1,Math.min(50,payload.optInt("limit",8)))))a.put(new JSONObject().put("id",m.id).put("text",m.text).put("type",m.type).put("tags",m.tags).put("importance",m.importance));return a;}
            case "memory_add":{String text=payload.optString("text","").trim();if(text.isEmpty())throw new Exception("Missing text");long id=brain.addMemoryRich(text,payload.optString("type","THOUGHT"),payload.optString("tags",engine.autoTags(text)),payload.optInt("importance",engine.inferImportance(text)),payload.optInt("valence",engine.inferValence(text)),payload.optInt("confidence",7),payload.optInt("novelty",engine.inferNovelty(text)),payload.optBoolean("active",false));return new JSONObject().put("memory_id",id);}
            case "source_import_text":return importText(payload.optString("name","Cloud source"),payload.optString("text",""));
            case "source_search":{JSONArray a=new JSONArray();for(String[] r:sources.searchChunks(payload.optString("q",""),Math.max(1,Math.min(50,payload.optInt("limit",8)))))a.put(new JSONObject().put("source",r[0]).put("part",r[1]).put("text",r[2]));return a;}
            case "projects_list":{JSONArray a=new JSONArray();for(String[] pr:store.projects()){long id=Long.parseLong(pr[0]);JSONObject o=new JSONObject().put("id",id).put("title",pr[1]).put("goal",pr[2]).put("status",pr[3]);JSONArray ts=new JSONArray();for(String[] t:store.tasks(id))ts.put(new JSONObject().put("id",t[0]).put("text",t[1]).put("status",t[2]).put("priority",t[3]));o.put("tasks",ts);a.put(o);}return a;}
            case "project_add":{String title=payload.optString("title","").trim();if(title.isEmpty())throw new Exception("Missing title");String goal=payload.optString("goal","");long id=store.addProject(title,goal);int pr=8;for(String s:omega.planSteps(goal.isEmpty()?title:goal))store.addTask(id,s,pr--);return new JSONObject().put("project_id",id);}
            case "task_add":{long id=store.addTask(payload.optLong("project_id",0),payload.optString("text",""),payload.optInt("priority",5));return new JSONObject().put("task_id",id);}
            case "consolidate":return new JSONObject().put("result",payload.optBoolean("full_autopilot",false)?omega.autopilotCycle():engine.consolidate());
            case "capabilities":return new JSONArray(new String[]{"brain_answer","brain_search","memory_add","source_import_text","source_search","projects_list","project_add","task_add","consolidate","capabilities"});
            default:throw new Exception("Unknown action: "+action);
        }
    }

    private JSONObject importText(String name,String text) throws Exception{if(text==null||text.trim().isEmpty())throw new Exception("Missing source text");long sid=sources.startSource(name,"text/plain","cloudlink://"+UUID.randomUUID(),text.length());long parent=brain.addMemoryRich("SOURCE: "+name+"\nORIGIN: CloudLink","SOURCE","source, cloudlink, "+engine.autoTags(name),8,0,8,5,false);int part=1,chars=0;for(String piece:chunk(text,1200)){sources.addChunk(sid,part,piece);brain.addMemoryRich("SOURCE "+name+" · part "+part+"\n"+piece,"REFERENCE","document, cloudlink, part:"+part,7,0,8,4,false);chars+=piece.length();part++;}sources.finishSource(sid,chars,part-1,parent);return new JSONObject().put("source_id",sid).put("chunks",part-1).put("chars",chars);}
    private static java.util.List<String> chunk(String text,int target){java.util.ArrayList<String> out=new java.util.ArrayList<>();String s=text.replace("\r\n","\n").trim();int i=0;while(i<s.length()){int end=Math.min(s.length(),i+target);if(end<s.length()){int cut=s.lastIndexOf(' ',end);if(cut>i+target/2)end=cut;}String p=s.substring(i,end).trim();if(!p.isEmpty())out.add(p);i=Math.max(end,i+1);}return out;}
    private JSONObject readBox(Uri uri)throws Exception{ContentResolver cr=getContentResolver();try(InputStream in=cr.openInputStream(uri)){if(in==null)return null;BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);return new JSONObject(b.toString());}}
    private void writeBox(Uri uri,JSONObject box)throws Exception{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception("Mailbox is not writable");out.write(box.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}}
    private static String safe(Exception e){String s=e.getMessage();return s==null?e.toString():s;}
}
