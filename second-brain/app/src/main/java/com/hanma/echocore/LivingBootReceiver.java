package com.hanma.echocore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores low-duty living-mind jobs after reboot without forcing a hot foreground service. */
public class LivingBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        LivingPulseJobService.schedule(context);
        CloudArchiveJobService.schedule(context);
    }
}
