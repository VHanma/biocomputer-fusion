package com.hanma.echocore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LivingBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){LivingPulseJobService.schedule(context);}
}
