package com.hanma.echocore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

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
import java.util.Locale;
import java.util.UUID;

public class CloudLinkService extends Service {
    public static final String KEY_ENABLED="autolink_enabled";
    public static final String KEY_REGISTERED="autolink_registered";
    public static final String KEY_DEVICE_ID="autolink_device_id";
    public static final String KEY_FORCE_REGISTER="autolink_force_register";
    private static final String KEY_SECRET="autolink_device_secret";
    private static final String BASE="https://vdvdijoniwhqorawaufi.supabase.co/functions/v1/echocore-relay";
    private static final String APP_VERSION="8.0.0";
    private static final int NOTIFICATION_ID=18434;
    private static final long NORMAL_POLL_MS=8000L;
    private static final long HEARTBEAT_MS=30000L;

    private volatile boolean running;
    private volatile boolean coreReady;
    private Thread worker;
    private BrainDatabase brain;
    private BrainEngine engine;
    private SourceCatalog sources;
    private CognitiveStore store;
    private CognitiveOrchestrator omega;
    private SecurePrefs prefs;
    private DiagnosticsStore diag;
    private int consecutiveFailures;
    private long lastHeartbeat;

    @Override public void onCreate(){
        super.onCreate();
        diag=new DiagnosticsStore(this);
        diag.serviceRestart();
        diag.setState("STARTING");
        try{
            prefs=new SecurePrefs(this);
            createChannel();
            startForeground(NOTIFICATION_ID,buildNotification("Starting secure relay…"));
            brain=new BrainDatabase(this);
            engine=new BrainEngine(brain);
            sources=new SourceCatalog(this);
            store=new CognitiveStore(this);
            omega=new NexusOrchestrator(brain,engine,sources,store);
            coreReady=true;
            diag.event("SERVICE","Core databases initialized with Nexus evidence engine");
        }catch(Throwable t){
            diag.error("service_onCreate",t);
            coreReady=false;
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!coreReady||prefs==null){stopSelf();return START_NOT_STICKY;}
        if(!prefs.getBool(KEY_ENABLED,true)){diag.setState("STOPPED");stopSelf();return START_NOT_STICKY;}
        if(!running){
            running=true;
            worker=new Thread(this::loop,"EchoCoreAutoLink-v8");
            worker.setUncaughtExceptionHandler((thread,t)->{
                try{diag.processCrash(thread.getName(),t);}catch(Throwable ignored){}
                running=false;
                stopSelf();
            });
            worker.start();
        }
        return START_STICKY;
    }

    @Override public void onDestroy(){
        running=false;
        if(worker!=null)worker.interrupt();
        diag.setState("STOPPED");
        closeQuietly();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}

    private void loop(){
        while(running&&!Thread.currentThread().isInterrupted()){
            try{
                if(!prefs.getBool(KEY_ENABLED,true))break;
                if(!networkAvailable()){
                    fail("No usable network",null);
                    sleep(backoffMs());
                    continue;
                }
                ensureRegistered();
                long now=System.currentTimeMillis();
                if(now-lastHeartbeat>=HEARTBEAT_MS){heartbeat();lastHeartbeat=now;}
                poll();
                consecutiveFailures=0;
                prefs.putInt("autolink_worker_crashes",0);
                prefs.putBool(KEY_REGISTERED,true);
                diag.setState("LIVE");
                updateNotification("Connected · " + brain.count() + " memories · " + sources.countSources() + " sources");
                sleep(NORMAL_POLL_MS);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }catch(RelayException e){
                if(e.code==401){resetCredentialsInternal();}
                fail("Relay HTTP "+e.code+": "+trim(e.getMessage(),500),e);
                sleepQuiet(backoffMs());
            }catch(Throwable t){
                if(t instanceof ThreadDeath)throw (ThreadDeath)t;
                fail("Worker: "+t.getClass().getSimpleName()+": "+safe(t.getMessage()),t);
                try{sendTelemetry("worker_error",new JSONObject().put("message",trim(safe(t.getMessage()),1500)).put("type",t.getClass().getName()));}catch(Throwable ignored){}
                sleepQuiet(backoffMs());
            }
        }
        running=false;
        diag.setState("STOPPED");
    }

    private void ensureRegistered() throws Exception{
        String id=prefs.get(KEY_DEVICE_ID,"");
        String secret=prefs.getSecret(KEY_SECRET);
        boolean force=prefs.getBool(KEY_FORCE_REGISTER,false);
        if(!force&&!id.isEmpty()&&!secret.isEmpty())return;
        diag.setState("REGISTERING");
        JSONObject body=new JSONObject();
        body.put("app_version",APP_VERSION);
        body.put("capabilities",caps());
        JSONObject r=post("/register",body,false);
        if(!r.optBoolean("ok"))throw new Exception(r.optString("error","register failed"));
        String newId=r.getString("device_id");
        String newSecret=r.getString("device_secret");
        prefs.put(KEY_DEVICE_ID,newId);
        prefs.putSecret(KEY_SECRET,newSecret);
        if(prefs.getSecret(KEY_SECRET).isEmpty())throw new Exception("Android Keystore could not persist AutoLink credential");
        prefs.putBool(KEY_FORCE_REGISTER,false);
        prefs.putBool(KEY_REGISTERED,true);
        diag.event("REGISTERED",shortId(newId));
        sendTelemetry("registered",new JSONObject().put("app_version",APP_VERSION));
    }

    private void heartbeat() throws Exception{
        JSONObject body=new JSONObject();
        body.put("app_version",APP_VERSION);
        body.put("capabilities",caps());
        body.put("health",healthJson());
        String err=diag.lastError();
        if(err!=null&&!err.isEmpty())body.put("last_error",trim(err,1800));
        post("/heartbeat",body,true);
    }

    private JSONObject healthJson(){
        JSONObject o=new JSONObject();
        try{
            o.put("state",diag.state());
            o.put("memories",brain==null?0:brain.count());
            o.put("synapses",brain==null?0:brain.countAssociations());
            o.put("sources",sources==null?0:sources.countSources());
            o.put("active_projects",store==null?0:store.countActiveProjects());
            o.put("commands_completed",diag.snapshotJson().optInt("command_count",0));
            o.put("failures",consecutiveFailures);
            o.put("relay_ms",diag.lastRelayMs());
            o.put("app_version",APP_VERSION);
            o.put("reasoner","NexusOrchestrator");
        }catch(Throwable ignored){}
        return o;
    }

    private void poll() throws Exception{
        JSONObject r=post("/poll",new JSONObject(),true);
        JSONArray a=r.optJSONArray("commands");
        if(a==null||a.length()==0)return;
        for(int i=0;i<a.length()&&running;i++){
            JSONObject c=a.optJSONObject(i);if(c==null)continue;
            String cid=c.optString("id","");if(cid.isEmpty())continue;
            String cached=prefs.get("cmd_resp_"+cid,"");
            if(!cached.isEmpty()){
                try{post("/respond",new JSONObject().put("command_id",cid).put("response",new JSONObject(cached)),true);continue;}
                catch(Exception invalid){prefs.remove("cmd_resp_"+cid);}
            }
            JSONObject cmd=c.optJSONObject("command");
            JSONObject resp=new JSONObject();
            String action=cmd==null?"":cmd.optString("action","");
            try{
                JSONObject payload=cmd==null?null:cmd.optJSONObject("payload");
                if(payload==null)payload=new JSONObject();
                resp.put("status","ok");
                resp.put("payload",execute(action,payload));
            }catch(Throwable t){
                resp.put("status","error");
                resp.put("payload",new JSONObject().put("message",trim(safe(t.getMessage()),3000)).put("type",t.getClass().getSimpleName()));
                diag.error("command:"+action,t);
                try{sendTelemetry("command_error",new JSONObject().put("action",action).put("message",trim(safe(t.getMessage()),1800)));}catch(Throwable ignored){}
            }
            rememberResponse(cid,resp);
            post("/respond",new JSONObject().put("command_id",cid).put("response",resp),true);
            diag.commandDone(action.isEmpty()?"unknown":action);
        }
    }

    private Object execute(String action,JSONObject p) throws Exception{
        switch(action){
            case "health": return healthJson().put("diagnostics",diag.snapshotJson());
            case "diagnostics": return diag.snapshotJson();
            case "brain_stats": return new JSONObject()
                    .put("memories",brain.count()).put("synapses",brain.countAssociations())
                    .put("sources",sources.countSources()).put("active_projects",store.countActiveProjects())
                    .put("hypotheses",store.hypotheses().size());
            case "brain_snapshot": return brainSnapshot(Math.max(3,Math.min(20,p.optInt("limit",10))));
            case "recent_memories": return memoriesJson(brain.recent(Math.max(1,Math.min(50,p.optInt("limit",12)))));
            case "knowledge_gaps": return new JSONObject().put("report",omega.knowledgeGaps());
            case "brain_answer":{
                String q=p.optString("question",p.optString("q",""));
                String m=p.optString("mode","DEEP").toUpperCase(Locale.US);
                String answer=omega.localAnswer(q,m);
                store.addTurn("REMOTE",q,m);store.addTurn("NEXUS",answer,m);
                return new JSONObject().put("answer",answer).put("mode",m).put("reasoner","NexusOrchestrator");
            }
            case "brain_search": return memoriesJson(brain.search(p.optString("q",""),Math.max(1,Math.min(50,p.optInt("limit",8)))));
            case "memory_add":{
                String t=p.optString("text","").trim();if(t.isEmpty())throw new Exception("Missing text");
                long id=brain.addMemoryRich(t,p.optString("type","THOUGHT"),p.optString("tags",engine.autoTags(t)),p.optInt("importance",engine.inferImportance(t)),p.optInt("valence",engine.inferValence(t)),p.optInt("confidence",7),p.optInt("novelty",engine.inferNovelty(t)),p.optBoolean("active",false));
                return new JSONObject().put("memory_id",id);
            }
            case "source_import_text": return importText(p.optString("name","Remote source"),p.optString("text",""));
            case "source_search":{
                JSONArray a=new JSONArray();
                for(String[] row:sources.searchChunks(p.optString("q",""),Math.max(1,Math.min(50,p.optInt("limit",8)))))a.put(new JSONObject().put("source",row[0]).put("part",row[1]).put("text",row[2]));
                return a;
            }
            case "sources_list":{
                JSONArray a=new JSONArray();
                for(String[] row:sources.recentSources(Math.max(1,Math.min(50,p.optInt("limit",20)))))a.put(new JSONObject().put("id",row[0]).put("name",row[1]).put("mime",row[2]).put("size_bytes",row[4]).put("chars",row[5]).put("chunks",row[6]).put("imported_at",row[8]));
                return a;
            }
            case "projects_list": return projectsJson();
            case "project_add":{
                String title=p.optString("title","").trim();if(title.isEmpty())throw new Exception("Missing title");
                String goal=p.optString("goal","");long id=store.addProject(title,goal);int priority=8;
                for(String step:omega.planSteps(goal.isEmpty()?title:goal))store.addTask(id,step,priority--);
                return new JSONObject().put("project_id",id);
            }
            case "task_add": return new JSONObject().put("task_id",store.addTask(p.optLong("project_id",0),p.optString("text",""),p.optInt("priority",5)));
            case "task_toggle":{store.toggleTask(p.optLong("task_id",0),p.optBoolean("done",true));return new JSONObject().put("ok",true);}
            case "project_status":{store.setProjectStatus(p.optLong("project_id",0),p.optString("status","ACTIVE"));return new JSONObject().put("ok",true);}
            case "hypotheses_list":{
                JSONArray a=new JSONArray();for(String[] h:store.hypotheses())a.put(new JSONObject().put("id",h[0]).put("statement",h[1]).put("status",h[2]).put("support",h[3]).put("oppose",h[4]).put("confidence",h[5]));return a;
            }
            case "consolidate": return new JSONObject().put("result",p.optBoolean("full_autopilot",false)?omega.autopilotCycle():engine.consolidate());
            case "capabilities": return caps();
            default: throw new Exception("Unknown action: "+action);
        }
    }

    private JSONObject brainSnapshot(int limit) throws Exception{
        JSONObject o=new JSONObject();
        o.put("stats",new JSONObject().put("memories",brain.count()).put("synapses",brain.countAssociations()).put("sources",sources.countSources()).put("active_projects",store.countActiveProjects()));
        o.put("strongest",memoriesJson(brain.strongest(limit)));
        o.put("projects",projectsJson());
        o.put("knowledge_gaps",omega.knowledgeGaps());
        o.put("reasoner","NexusOrchestrator");
        return o;
    }

    private JSONArray memoriesJson(java.util.List<MemoryNode> list) throws Exception{
        JSONArray a=new JSONArray();
        for(MemoryNode x:list)a.put(new JSONObject().put("id",x.id).put("text",x.text).put("type",x.type).put("tags",x.tags).put("importance",x.importance).put("confidence",x.confidence).put("novelty",x.novelty).put("active",x.active).put("pinned",x.pinned).put("access_count",x.accessCount));
        return a;
    }

    private JSONArray projectsJson() throws Exception{
        JSONArray a=new JSONArray();
        for(String[] pr:store.projects()){
            long id=Long.parseLong(pr[0]);
            JSONObject o=new JSONObject().put("id",id).put("title",pr[1]).put("goal",pr[2]).put("status",pr[3]);
            JSONArray tasks=new JSONArray();
            for(String[] t:store.tasks(id))tasks.put(new JSONObject().put("id",t[0]).put("text",t[1]).put("status",t[2]).put("priority",t[3]));
            o.put("tasks",tasks);a.put(o);
        }
        return a;
    }

    private JSONObject importText(String name,String text) throws Exception{
        if(text==null||text.trim().isEmpty())throw new Exception("Missing source text");
        if(text.length()>2_000_000)throw new Exception("Remote text source exceeds 2,000,000 characters");
        long sid=sources.startSource(name,"text/plain","autolink://"+UUID.randomUUID(),text.length());
        long parent=brain.addMemoryRich("SOURCE: "+name+"\nORIGIN: AutoLink","SOURCE","source, autolink, "+engine.autoTags(name),8,0,8,5,false);
        int part=1,chars=0;
        for(String piece:chunk(text,1800)){
            sources.addChunk(sid,part,piece);
            long child=brain.addMemoryRich("SOURCE "+name+" · part "+part+"\n"+piece,"REFERENCE","document, autolink, part:"+part,7,0,8,4,false);
            brain.reinforceAssociation(parent,child,"SOURCE-PART",2);
            chars+=piece.length();part++;
        }
        sources.finishSource(sid,chars,part-1,parent);
        return new JSONObject().put("source_id",sid).put("chunks",part-1).put("chars",chars).put("parent_memory_id",parent);
    }

    private JSONObject post(String path,JSONObject body,boolean auth) throws Exception{
        HttpURLConnection c=null;
        long started=SystemClock.elapsedRealtime();
        try{
            c=(HttpURLConnection)new URL(BASE+path).openConnection();
            c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(22000);c.setUseCaches(false);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Connection","close");
            if(auth){
                String id=prefs.get(KEY_DEVICE_ID,"");String secret=prefs.getSecret(KEY_SECRET);
                if(id.isEmpty()||secret.isEmpty())throw new RelayException(401,"missing local credential");
                c.setRequestProperty("X-Device-Id",id);c.setRequestProperty("X-Device-Secret",secret);
            }
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream o=c.getOutputStream()){o.write(bytes);o.flush();}
            int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();String raw=read(in,1_000_000);
            if(code<200||code>=300)throw new RelayException(code,raw.isEmpty()?c.getResponseMessage():raw);
            JSONObject out=raw.isEmpty()?new JSONObject():new JSONObject(raw);
            diag.relaySuccess(SystemClock.elapsedRealtime()-started);
            return out;
        }finally{if(c!=null)c.disconnect();}
    }

    private void sendTelemetry(String kind,JSONObject payload) throws Exception{
        if(prefs.get(KEY_DEVICE_ID,"").isEmpty()||prefs.getSecret(KEY_SECRET).isEmpty())return;
        post("/telemetry",new JSONObject().put("kind",kind).put("payload",payload),true);
    }

    private boolean networkAvailable(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return true;
            Network n=cm.getActiveNetwork();if(n==null)return false;NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c==null)return false;
            return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Throwable ignored){return true;}
    }

    private void fail(String message,Throwable t){
        consecutiveFailures++;
        diag.setState("RETRYING");
        if(t!=null)diag.error("relay",t);else diag.relayFailure(message,consecutiveFailures);
        updateNotification("Reconnecting · attempt "+consecutiveFailures);
    }

    private long backoffMs(){long v=2000L*(1L<<Math.min(5,Math.max(0,consecutiveFailures-1)));return Math.min(60000L,v);}
    private void sleep(long ms) throws InterruptedException{Thread.sleep(ms);}
    private void sleepQuiet(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}

    private void resetCredentialsInternal(){
        prefs.remove(KEY_DEVICE_ID);prefs.remove(KEY_SECRET);prefs.putBool(KEY_FORCE_REGISTER,true);prefs.putBool(KEY_REGISTERED,false);diag.event("AUTH","Credential reset after authentication failure");
    }

    public static void clearCredentials(Context context){
        SecurePrefs p=new SecurePrefs(context);p.remove(KEY_DEVICE_ID);p.remove(KEY_SECRET);p.putBool(KEY_FORCE_REGISTER,true);p.putBool(KEY_REGISTERED,false);
    }

    private void rememberResponse(String id,JSONObject resp){
        String raw=resp.toString();if(raw.length()>250000)return;
        prefs.put("cmd_resp_"+id,raw);
        String order=prefs.get("cmd_order","");ArrayList<String> ids=new ArrayList<>();
        if(!order.isEmpty())for(String x:order.split(","))if(!x.isEmpty()&&!x.equals(id))ids.add(x);
        ids.add(id);
        while(ids.size()>32){String old=ids.remove(0);prefs.remove("cmd_resp_"+old);}
        StringBuilder b=new StringBuilder();for(String x:ids){if(b.length()>0)b.append(',');b.append(x);}prefs.put("cmd_order",b.toString());
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel("echocore_autolink","EchoCore Nexus Link",NotificationManager.IMPORTANCE_LOW));}
    }

    private Notification buildNotification(String text){
        Intent i=new Intent(this,CloudLinkActivity.class);PendingIntent pi=PendingIntent.getActivity(this,18434,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"echocore_autolink").setContentTitle("EchoCore Nexus").setContentText(text).setSmallIcon(android.R.drawable.stat_notify_sync).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void updateNotification(String text){try{NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(text));}catch(Throwable ignored){}}
    private void closeQuietly(){try{if(brain!=null)brain.close();}catch(Throwable ignored){}try{if(sources!=null)sources.close();}catch(Throwable ignored){}try{if(store!=null)store.close();}catch(Throwable ignored){}}

    private JSONArray caps(){
        JSONArray a=new JSONArray();String[] x={"health","diagnostics","brain_stats","brain_snapshot","recent_memories","knowledge_gaps","brain_answer","brain_search","memory_add","source_import_text","source_search","sources_list","projects_list","project_add","task_add","task_toggle","project_status","hypotheses_list","consolidate","capabilities"};for(String s:x)a.put(s);return a;
    }

    private static java.util.List<String> chunk(String text,int target){ArrayList<String> out=new ArrayList<>();String s=text.replace("\r\n","\n").trim();int i=0;while(i<s.length()){int e=Math.min(s.length(),i+target);if(e<s.length()){int p=s.lastIndexOf('\n',e);if(p<=i+target/2)p=s.lastIndexOf(' ',e);if(p>i+target/2)e=p;}String q=s.substring(i,e).trim();if(!q.isEmpty())out.add(q);i=Math.max(e,i+1);}return out;}
    private static String read(InputStream in,int cap) throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){if(b.length()+line.length()>cap)throw new Exception("Relay response exceeded safe size");b.append(line);}}return b.toString();}
    private static String trim(String s,int n){if(s==null)return "";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1))+"…";}
    private static String safe(String s){return s==null?"":s.trim();}
    private static String shortId(String s){return s==null?"":(s.length()>12?s.substring(0,6)+"…"+s.substring(s.length()-6):s);}

    private static class RelayException extends Exception{
        final int code;RelayException(int code,String message){super(message);this.code=code;}
    }
}
