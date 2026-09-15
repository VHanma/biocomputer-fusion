package com.hanma.echocore;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Runs cloud archival in the main app process after Phoenix has committed a source.
 * This keeps network/auth state out of the isolated :phoenix process.
 */
public class CloudArchiveJobService extends JobService {
    public static final int JOB_ID=18439;

    public static void schedule(Context c){
        try{
            JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(js==null)return;
            JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(c,CloudArchiveJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(2500)
                    .setOverrideDeadline(45000)
                    .setPersisted(true)
                    .build();
            js.schedule(info);
        }catch(Throwable ignored){}
    }

    @Override public boolean onStartJob(JobParameters p){
        new Thread(()->runSync(p),"EchoCore-CloudArchive").start();
        return true;
    }

    private void runSync(JobParameters p){
        boolean retry=false;
        try(AscendantStore asc=new AscendantStore(this);SourceCatalog catalog=new SourceCatalog(this)){
            CloudKnowledgeSync sync=new CloudKnowledgeSync(this,asc);
            for(String[] row:catalog.recentSources(250)){
                if(row.length<12)continue;
                long id=parse(row[0]);
                String name=row[1],status=row[9];
                boolean archived="1".equals(row[11]);
                if(id<=0||archived)continue;
                if(!"READY".equals(status)&&!"CLOUD_SYNC_FAILED".equals(status))continue;
                try{
                    int n=sync.sync(catalog,id,name);
                    asc.blackbox("CLOUD_ARCHIVE","JOB_OK",name+" · "+n+" passages","");
                }catch(Throwable t){
                    retry=true;
                    asc.blackbox("CLOUD_ARCHIVE","JOB_RETRY",name+" · "+safe(t),"");
                    break;
                }
            }
        }catch(Throwable t){retry=true;}
        jobFinished(p,retry);
    }

    @Override public boolean onStopJob(JobParameters p){return true;}
    private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String safe(Throwable t){String x=t==null?"unknown":String.valueOf(t.getMessage());return x.length()>500?x.substring(0,500):x;}
}
