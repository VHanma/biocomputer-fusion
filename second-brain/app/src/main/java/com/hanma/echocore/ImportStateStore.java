package com.hanma.echocore;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small persistent queue/state journal for the document import engine. */
public class ImportStateStore {
    private static final String PREF="echocore_docflow_state";
    private static final int MAX_REPORT=12000;
    private final SharedPreferences p;

    public ImportStateStore(Context c){p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public synchronized void enqueue(List<Uri> uris){
        if(uris==null||uris.isEmpty())return;
        ArrayList<String> q=pending();
        Set<String> seen=new HashSet<>(q);
        boolean fresh=q.isEmpty()&&!"RUNNING".equals(state());
        for(Uri u:uris){if(u==null)continue;String s=u.toString();if(seen.add(s))q.add(s);}
        SharedPreferences.Editor e=p.edit().putString("queue",toJson(q)).putBoolean("cancel",false);
        if(fresh)e.putInt("done",0).putInt("failed",0).putString("report","");
        e.putInt("total",q.size()+p.getInt("done",0)+p.getInt("failed",0)).putString("state","QUEUED").putLong("updated",System.currentTimeMillis()).apply();
    }

    public synchronized ArrayList<String> pending(){
        ArrayList<String> out=new ArrayList<>();String raw=p.getString("queue","[]");
        try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(!s.isEmpty())out.add(s);}}catch(Exception ignored){}
        return out;
    }

    public synchronized String peek(){ArrayList<String> q=pending();return q.isEmpty()?"":q.get(0);}

    public synchronized void markRunning(String uri,String name){
        p.edit().putString("state","RUNNING").putString("current_uri",safe(uri)).putString("current_name",safe(name)).putString("status","Importing "+safe(name)).putLong("updated",System.currentTimeMillis()).apply();
    }

    public synchronized void progress(String status){p.edit().putString("status",safe(status)).putLong("updated",System.currentTimeMillis()).apply();}

    public synchronized void completeCurrent(String uri,boolean success,String line){
        ArrayList<String> q=pending();q.remove(uri);
        int done=p.getInt("done",0),failed=p.getInt("failed",0);if(success)done++;else failed++;
        String old=p.getString("report","");String next=(old==null||old.isEmpty())?safe(line):old+"\n"+safe(line);if(next.length()>MAX_REPORT)next=next.substring(next.length()-MAX_REPORT);
        String nextState=q.isEmpty()?"DONE":"QUEUED";
        p.edit().putString("queue",toJson(q)).putInt("done",done).putInt("failed",failed).putString("report",next).putString("state",nextState).putString("current_uri","").putString("current_name","").putString("status",q.isEmpty()?"Batch complete":"Continuing queue").putLong("updated",System.currentTimeMillis()).apply();
    }

    public synchronized void setError(String msg){p.edit().putString("last_error",safe(msg)).putString("status",safe(msg)).putLong("updated",System.currentTimeMillis()).apply();}
    public synchronized void requestCancel(){p.edit().putBoolean("cancel",true).putString("status","Cancelling at safe boundary…").putLong("updated",System.currentTimeMillis()).apply();}
    public boolean cancelRequested(){return p.getBoolean("cancel",false);}
    public synchronized void clearCancel(){p.edit().putBoolean("cancel",false).apply();}

    public synchronized void cancelQueue(){p.edit().putString("queue","[]").putString("state","CANCELLED").putString("status","Cancelled").putString("current_uri","").putString("current_name","").putBoolean("cancel",false).putLong("updated",System.currentTimeMillis()).apply();}

    public String state(){return p.getString("state","IDLE");}
    public String status(){return p.getString("status","Ready");}
    public String currentName(){return p.getString("current_name","");}
    public int done(){return p.getInt("done",0);}
    public int failed(){return p.getInt("failed",0);}
    public int total(){return p.getInt("total",0);}
    public String report(){return p.getString("report","");}
    public long updated(){return p.getLong("updated",0);}

    public JSONObject snapshot(){JSONObject o=new JSONObject();try{o.put("state",state()).put("status",status()).put("current",currentName()).put("done",done()).put("failed",failed()).put("total",total()).put("pending",pending().size()).put("updated",updated()).put("last_error",p.getString("last_error",""));}catch(Exception ignored){}return o;}

    private static String toJson(List<String> q){JSONArray a=new JSONArray();if(q!=null)for(String s:q)a.put(s);return a.toString();}
    private static String safe(String s){return s==null?"":s.trim();}
}
