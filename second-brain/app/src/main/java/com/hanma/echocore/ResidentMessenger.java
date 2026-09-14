package com.hanma.echocore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Residents can initiate app messages. Delivery is local and private to the device. */
public final class ResidentMessenger {
    private static final String CHANNEL="echocore_resident_messages";
    private ResidentMessenger(){}
    public static long send(Context c,String residentId,String sender,String body,String kind,int priority){
        Context app=c.getApplicationContext();long id;
        try(LivingMindStore store=new LivingMindStore(app)){id=store.addMessage(residentId,sender,body,kind,priority);}catch(Throwable t){return -1;}
        notify(app,id,sender,body,priority);return id;
    }
    public static void notify(Context c,long messageId,String sender,String body,int priority){
        try{
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
            if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"Messages from Continuum residents",priority>=9?NotificationManager.IMPORTANCE_HIGH:NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("Thoughts, discoveries and questions initiated by EchoCore residents");nm.createNotificationChannel(ch);}
            Intent open=new Intent(c,ResidentInboxActivity.class);open.putExtra("message_id",messageId);PendingIntent pi=PendingIntent.getActivity(c,(int)(messageId%100000),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);Notification n=b.setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(sender==null||sender.trim().isEmpty()?"EchoCore resident":sender).setContentText(trim(body,120)).setStyle(new Notification.BigTextStyle().bigText(trim(body,900))).setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_MESSAGE).setPriority(priority>=9?Notification.PRIORITY_HIGH:Notification.PRIORITY_DEFAULT).build();nm.notify(23000+(int)(messageId%5000),n);
            try(LivingMindStore store=new LivingMindStore(c)){store.markDelivered(messageId);}
        }catch(Throwable ignored){}
    }
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
}
