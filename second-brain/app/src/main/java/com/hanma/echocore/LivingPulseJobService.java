package com.hanma.echocore;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/** Periodic low-duty pulse so residents can reflect and message without keeping a hot service alive. */
public class LivingPulseJobService extends JobService {
    public static final int JOB_ID=18438;
    public static void schedule(Context c){try{JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(js==null)return;JobInfo.Builder b=new JobInfo.Builder(JOB_ID,new ComponentName(c,LivingPulseJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15L*60L*1000L);js.schedule(b.build());}catch(Throwable ignored){}}
    @Override public boolean onStartJob(JobParameters p){new Thread(()->{AetherLatticeEngine e=null;try{e=new AetherLatticeEngine(this);e.pulse();}catch(Throwable t){try(LivingMindStore s=new LivingMindStore(this)){s.ledger("omega","LATTICE_ERROR",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}}finally{if(e!=null)e.close();jobFinished(p,false);}},"Aether-Lattice-Pulse").start();return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}
}
