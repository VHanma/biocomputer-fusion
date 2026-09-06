package com.hanma.echocore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.OpenableColumns;

/**
 * Persistent single-consumer import engine. The UI only queues work; destroying
 * SourceActivity no longer cancels document ingestion.
 */
public class DocumentImportService extends Service {
    public static final String ACTION_RUN="com.hanma.echocore.DOCFLOW_RUN";
    public static final String ACTION_CANCEL="com.hanma.echocore.DOCFLOW_CANCEL";
    private static final String CHANNEL="echocore_docflow";
    private static final int NOTIFY_ID=18435;

    private ImportStateStore state;
    private DiagnosticsStore diag;
    private volatile boolean running;
    private Thread worker;

    public static void start(Context c){
        Intent i=new Intent(c,DocumentImportService.class).setAction(ACTION_RUN);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    public static void cancel(Context c){
        Intent i=new Intent(c,DocumentImportService.class).setAction(ACTION_CANCEL);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }

    @Override public void onCreate(){
        super.onCreate();
        state=new ImportStateStore(this);diag=new DiagnosticsStore(this);createChannel();
        startForeground(NOTIFY_ID,notification("Document engine ready"));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_RUN:intent.getAction();
        if(ACTION_CANCEL.equals(action)){
            state.requestCancel();
            if(worker!=null)worker.interrupt();
            update("Cancelling at safe boundary…");
            return START_NOT_STICKY;
        }
        state.clearCancel();
        if(!running){
            running=true;
            worker=new Thread(this::processQueue,"EchoCore-DocFlow-v10");
            worker.setUncaughtExceptionHandler((t,e)->{
                try{diag.processCrash(t.getName(),e);state.setError("Importer worker error: "+safe(e));}catch(Throwable ignored){}
                running=false;stopSelf();
            });
            worker.start();
        }
        return START_STICKY;
    }

    private void processQueue(){
        try{
            while(running&&!Thread.currentThread().isInterrupted()){
                if(state.cancelRequested()){state.cancelQueue();diag.event("DOCFLOW","Queue cancelled by user");break;}
                String uriText=state.peek();
                if(uriText.isEmpty())break;
                Uri uri=Uri.parse(uriText);String name=safeName(uri);
                state.markRunning(uriText,name);update("Importing · "+name);

                boolean success=false;String line="";Throwable last=null;
                for(int attempt=1;attempt<=2&&!success;attempt++){
                    BrainDatabase brain=null;SourceCatalog catalog=null;
                    try{
                        brain=new BrainDatabase(this);catalog=new SourceCatalog(this);
                        DocumentImporter importer=new DocumentImporter(this,brain,catalog);
                        DocumentImporter.Result r=importer.ingest(uri);
                        success=true;
                        line=(r.partial?"△ ":"✓ ")+r.name+" · "+r.chars+" chars · "+r.chunks+" chunks"+(r.partial?" · partial":"");
                        diag.event(r.partial?"IMPORT_PARTIAL":"IMPORT_OK",trim(line,220));
                    }catch(OutOfMemoryError oom){
                        last=oom;diag.event("IMPORT_OOM","Caught heap exhaustion in "+trim(name,100));
                        line="✕ "+name+" · Android heap exhausted; queue continued";
                        break;
                    }catch(Throwable t){
                        if(t instanceof ThreadDeath)throw (ThreadDeath)t;
                        if(t instanceof InterruptedException){Thread.currentThread().interrupt();break;}
                        last=t;diag.error("docflow:"+trim(name,80)+":attempt"+attempt,t);
                        if(attempt<2){state.progress("Retrying "+name+" after parser error…");sleepQuiet(700);}
                    }finally{
                        try{if(catalog!=null)catalog.close();}catch(Throwable ignored){}
                        try{if(brain!=null)brain.close();}catch(Throwable ignored){}
                    }
                }

                if(Thread.currentThread().isInterrupted()){
                    if(state.cancelRequested())state.cancelQueue();
                    break;
                }
                if(!success&&line.isEmpty())line="✕ "+name+" · "+safe(last);
                state.completeCurrent(uriText,success,line);
                update(success?"Finished · "+name:"Skipped failed file · "+name);
                pressureRelief();
            }
        }catch(Throwable t){
            diag.error("docflow_loop",t);state.setError("Document engine: "+safe(t));
        }finally{
            running=false;
            if(state.pending().isEmpty()&&!"CANCELLED".equals(state.state()))state.progress("Batch complete");
            stopForeground(false);stopSelf();
        }
    }

    private void pressureRelief(){
        try{System.gc();Thread.sleep(220);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private void sleepQuiet(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}

    private String safeName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&c.getString(i)!=null)return trim(c.getString(i),110);}
        }catch(Throwable ignored){}
        String s=uri==null?"file":uri.getLastPathSegment();return trim(s==null?"file":s,110);
    }

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"EchoCore document imports",NotificationManager.IMPORTANCE_LOW));}}
    private Notification notification(String text){
        Intent open=new Intent(this,SourceActivity.class);PendingIntent pi=PendingIntent.getActivity(this,91,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("EchoCore DocFlow").setContentText(trim(text,90)).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void update(String text){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFY_ID,notification(text));}

    @Override public void onTimeout(int startId,int fgsType){
        state.setError("Android paused the long import service. Queue preserved; reopen Source Cortex to resume.");
        diag.event("DOCFLOW_TIMEOUT","Foreground-service time window reached; queue preserved");
        running=false;if(worker!=null)worker.interrupt();stopSelf();
    }

    @Override public void onDestroy(){running=false;if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}

    private static String safe(Throwable t){if(t==null)return "unknown error";String m=t.getMessage();return trim(m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m,240);}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
}
