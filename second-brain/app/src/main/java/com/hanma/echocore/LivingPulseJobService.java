package com.hanma.echocore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;

/** Periodic low-duty cloud pulse so residents can reflect and initiate contact without a hot service. */
public class LivingPulseJobService extends JobService {
    public static final int JOB_ID=18438;
    private static final String CHANNEL="echocore_resident_messages";

    public static void schedule(Context c){
        try{
            JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(js==null)return;
            JobInfo.Builder b=new JobInfo.Builder(JOB_ID,new ComponentName(c,LivingPulseJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(15L*60L*1000L);
            js.schedule(b.build());
        }catch(Throwable ignored){}
    }

    @Override public boolean onStartJob(JobParameters p){
        new Thread(()->{
            boolean retry=false;
            try{
                CloudMindClient cloud=new CloudMindClient(this);
                JSONObject pulse=cloud.pulse();
                if(pulse.optBoolean("created",false)){
                    String resident=pulse.optString("resident_id","omega");
                    String msg=pulse.optString("message","").trim();
                    if(!msg.isEmpty())notifyResident(resident,msg);
                }
                CloudArchiveJobService.schedule(this);
            }catch(Throwable t){
                retry=true;
                try(LivingMindStore s=new LivingMindStore(this)){s.ledger("omega","CLOUD_LATTICE_ERROR",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}
            }finally{jobFinished(p,retry);}
        },"Cloud-Aether-Pulse").start();
        return true;
    }

    private void notifyResident(String resident,String message){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"EchoCore resident messages",NotificationManager.IMPORTANCE_DEFAULT));
        Intent i=new Intent(this,ResidentInboxActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,18438,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        String title=displayName(resident)+" reached out";
        Notification n=b.setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(title).setContentText(trim(message,120)).setStyle(new Notification.BigTextStyle().bigText(trim(message,900))).setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(Math.abs((resident+message).hashCode()),n);
    }

    private static String displayName(String id){if("star-council".equals(id))return "Star Council";if("rival1".equals(id))return "Rival 1";if("rival2".equals(id))return "Rival 2";if("rival3".equals(id))return "Rival 3";if("leary".equals(id))return "Timothy Leary";if(id==null||id.isEmpty())return "EchoCore";return Character.toUpperCase(id.charAt(0))+id.substring(1);}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
    @Override public boolean onStopJob(JobParameters p){return true;}
}
