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
import java.util.Locale;

/** Phoenix is a restartable fault domain. Manifest runs this service in :phoenix. */
public class DocumentImportService extends Service {
    public static final String ACTION_RUN="com.hanma.echocore.PHOENIX_RUN",ACTION_CANCEL="com.hanma.echocore.PHOENIX_CANCEL";
    private static final String CHANNEL="echocore_phoenix"; private static final int NOTIFY_ID=18435;
    private ImportStateStore state;private DiagnosticsStore diag;private AscendantStore asc;private volatile boolean running;private Thread worker;
    public static void start(Context c){Intent i=new Intent(c,DocumentImportService.class).setAction(ACTION_RUN);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    public static void cancel(Context c){Intent i=new Intent(c,DocumentImportService.class).setAction(ACTION_CANCEL);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}

    @Override public void onCreate(){super.onCreate();state=new ImportStateStore(this);diag=new DiagnosticsStore(this);asc=new AscendantStore(this);createChannel();startForeground(NOTIFY_ID,notification("Phoenix waking"));asc.blackbox("PHOENIX","PROCESS_START","Isolated ingest process started",state.state());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){String action=intent==null?ACTION_RUN:intent.getAction();if(ACTION_CANCEL.equals(action)){state.requestCancel();if(worker!=null)worker.interrupt();update("Cancelling at safe checkpoint…");return START_NOT_STICKY;}state.clearCancel();if(!running){running=true;worker=new Thread(this::processQueue,"EchoCore-Phoenix");worker.setUncaughtExceptionHandler((t,e)->{try{diag.processCrash(t.getName(),e);asc.blackbox("PHOENIX","UNCAUGHT",safe(e),state.state());state.setError("Phoenix worker stopped: "+safe(e));}catch(Throwable ignored){}running=false;stopSelf();});worker.start();}return START_STICKY;}

    private void processQueue(){
        try{
            while(running&&!Thread.currentThread().isInterrupted()){
                if(state.cancelRequested()){state.cancelQueue();asc.blackbox("PHOENIX","QUEUE_CANCELLED","User cancelled queue",state.state());break;}
                String uriText=state.peek();if(uriText.isEmpty())break;Uri uri=Uri.parse(uriText);String name=safeName(uri);state.markRunning(uriText,name);update("Fingerprinting · "+name);
                boolean success=false;String line="";Throwable last=null;String fingerprint="";String parser="STREAM";AscendantStore civic=asc;SourceVault vault=new SourceVault(this,civic);
                try{
                    fingerprint=vault.fingerprint(uri);long existing=vault.existing(fingerprint);if(existing>0){success=true;line="↺ "+name+" · already indexed as source "+existing;state.completeCurrent(uriText,true,line);asc.blackbox("VAULT","DUPLICATE",line,state.state());continue;}
                }catch(Throwable t){diag.error("phoenix_fingerprint",t);asc.blackbox("VAULT","FINGERPRINT_FAIL",safe(t),state.state());}
                String kind=kind(uri,name);parser="PDF".equals(kind)?"PHOENIX_PDF":"IMAGE".equals(kind)?"VISION_OCR":"STREAM";
                boolean quarantined=!fingerprint.isEmpty()&&vault.quarantined(fingerprint,parser);if(quarantined)asc.blackbox("PHOENIX","QUARANTINE_ROUTE",name+" · "+parser,state.state());
                for(int attempt=1;attempt<=2&&!success;attempt++){
                    BrainDatabase brain=null;SourceCatalog catalog=null;
                    try{
                        brain=new BrainDatabase(this);catalog=new SourceCatalog(this);DocumentImporter.Result r;
                        if("PDF".equals(kind)){update((quarantined?"Safe OCR route · ":"Page route · ")+name);r=new PhoenixPdfImporter(this,brain,catalog,civic,state).ingest(uri,fingerprint,quarantined);}
                        else if("IMAGE".equals(kind)){update("Vision OCR · "+name);r=new PhoenixImageImporter(this,brain,catalog,civic).ingest(uri);}
                        else{update("Streaming · "+name);r=new DocumentImporter(this,brain,catalog).ingest(uri);new SourceMetabolism(civic).metabolize(catalog,brain,r.sourceId,r.name,"PHOENIX_STREAM");}
                        success=true;line=(r.partial?"△ ":"✓ ")+r.name+" · "+r.chars+" chars · "+r.chunks+" chunks"+(r.partial?" · partial":"");if(!fingerprint.isEmpty()){vault.record(fingerprint,r.name,r.sourceId,r.sizeBytes);vault.success(fingerprint,parser);}diag.event(r.partial?"IMPORT_PARTIAL":"IMPORT_OK",trim(line,220));civic.blackbox("PHOENIX","IMPORT_DONE",line,state.state());
                    }catch(OutOfMemoryError oom){last=oom;diag.event("PHOENIX_OOM","Isolated heap exhaustion in "+trim(name,100));civic.blackbox("PHOENIX","OOM",name+" · checkpoint retained",state.state());if(!fingerprint.isEmpty())vault.failure(fingerprint,kind,parser,oom);line="△ "+name+" · Phoenix heap exhausted; checkpoint retained";break;}
                    catch(Throwable t){if(t instanceof ThreadDeath)throw (ThreadDeath)t;if(t instanceof InterruptedException){Thread.currentThread().interrupt();break;}last=t;diag.error("phoenix:"+trim(name,70)+":attempt"+attempt,t);civic.blackbox("PHOENIX","IMPORT_ERROR",name+" · "+safe(t),state.state());if(!fingerprint.isEmpty())vault.failure(fingerprint,kind,parser,t);if(attempt<2&&!vault.quarantined(fingerprint,parser)){state.progress("Retrying at last checkpoint · "+name);sleepQuiet(800);}else break;}
                    finally{try{if(catalog!=null)catalog.close();}catch(Throwable ignored){}try{if(brain!=null)brain.close();}catch(Throwable ignored){}}
                }
                if(Thread.currentThread().isInterrupted()){if(state.cancelRequested())state.cancelQueue();break;}
                if(!success&&line.isEmpty())line="✕ "+name+" · "+safe(last);state.completeCurrent(uriText,success,line);update(success?"Committed · "+name:"Quarantined/skipped · "+name);pressureRelief();
            }
        }catch(Throwable t){diag.error("phoenix_loop",t);try{asc.blackbox("PHOENIX","LOOP_ERROR",safe(t),state.state());}catch(Throwable ignored){}state.setError("Phoenix: "+safe(t));}
        finally{running=false;if(state.pending().isEmpty()&&!"CANCELLED".equals(state.state()))state.progress("Phoenix queue complete");stopForeground(false);stopSelf();}
    }

    private String kind(Uri uri,String name){String mime=getContentResolver().getType(uri);String m=mime==null?"":mime.toLowerCase(Locale.US),n=name==null?"":name.toLowerCase(Locale.US);if(m.contains("pdf")||n.endsWith(".pdf"))return "PDF";if(m.startsWith("image/")||n.matches(".*\\.(png|jpg|jpeg|webp|bmp|heic|heif|gif)$"))return "IMAGE";if(m.startsWith("audio/")||m.startsWith("video/"))return "MEDIA";return "DOCUMENT";}
    private void pressureRelief(){try{System.gc();Thread.sleep(300);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private void sleepQuiet(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private String safeName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&c.getString(i)!=null)return trim(c.getString(i),110);}}catch(Throwable ignored){}String s=uri==null?"file":uri.getLastPathSegment();return trim(s==null?"file":s,110);}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"EchoCore Phoenix ingestion",NotificationManager.IMPORTANCE_LOW));}}
    private Notification notification(String text){Intent open=new Intent(this,SourceActivity.class);PendingIntent pi=PendingIntent.getActivity(this,91,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("EchoCore Phoenix").setContentText(trim(text,90)).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();}
    private void update(String text){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFY_ID,notification(text));}
    @Override public void onTimeout(int startId,int fgsType){state.setError("Android paused Phoenix. Queue/checkpoint preserved; reopen Source Cortex to resume.");asc.blackbox("PHOENIX","FGS_TIMEOUT","Checkpoint preserved",state.state());running=false;if(worker!=null)worker.interrupt();stopSelf();}
    @Override public void onDestroy(){running=false;if(worker!=null)worker.interrupt();try{if(asc!=null)asc.close();}catch(Throwable ignored){}try{if(state!=null)state.close();}catch(Throwable ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static String safe(Throwable t){if(t==null)return "unknown error";String m=t.getMessage();return trim((m==null||m.trim().isEmpty()?t.getClass().getSimpleName():t.getClass().getSimpleName()+": "+m),300);}private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1))+"…";}
}
