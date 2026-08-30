package com.hanma.echocore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class CognitiveStore extends SQLiteOpenHelper {
    private static final String DB_NAME="echocore_cognitive.db";
    private static final int DB_VERSION=1;

    public CognitiveStore(Context context){super(context,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,goal TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ACTIVE',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL DEFAULT 0,text TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',priority INTEGER NOT NULL DEFAULT 5,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE conversations(id INTEGER PRIMARY KEY AUTOINCREMENT,role TEXT NOT NULL,text TEXT NOT NULL,mode TEXT NOT NULL DEFAULT 'DEEP',created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE hypotheses(id INTEGER PRIMARY KEY AUTOINCREMENT,statement TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',support INTEGER NOT NULL DEFAULT 0,oppose INTEGER NOT NULL DEFAULT 0,confidence INTEGER NOT NULL DEFAULT 5,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_projects_status ON projects(status,updated_at DESC)");
        db.execSQL("CREATE INDEX idx_tasks_project ON tasks(project_id,status,priority DESC)");
        db.execSQL("CREATE INDEX idx_conversations_time ON conversations(created_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}

    public long addProject(String title,String goal){
        if(title==null||title.trim().isEmpty())return -1;long now=System.currentTimeMillis();ContentValues cv=new ContentValues();cv.put("title",title.trim());cv.put("goal",safe(goal));cv.put("status","ACTIVE");cv.put("created_at",now);cv.put("updated_at",now);return getWritableDatabase().insert("projects",null,cv);
    }
    public List<String[]> projects(){
        ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,goal,status,created_at,updated_at FROM projects ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,updated_at DESC",null)){while(c.moveToNext())out.add(new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),c.getString(3),String.valueOf(c.getLong(4)),String.valueOf(c.getLong(5))});}return out;
    }
    public int countActiveProjects(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM projects WHERE status='ACTIVE'",null)){return c.moveToFirst()?c.getInt(0):0;}}
    public void setProjectStatus(long id,String status){ContentValues cv=new ContentValues();cv.put("status",status);cv.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("projects",cv,"id=?",new String[]{String.valueOf(id)});}

    public long addTask(long projectId,String text,int priority){
        if(text==null||text.trim().isEmpty())return -1;long now=System.currentTimeMillis();ContentValues cv=new ContentValues();cv.put("project_id",projectId);cv.put("text",text.trim());cv.put("status","OPEN");cv.put("priority",Math.max(1,Math.min(10,priority)));cv.put("created_at",now);cv.put("updated_at",now);return getWritableDatabase().insert("tasks",null,cv);
    }
    public List<String[]> tasks(long projectId){
        ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,text,status,priority,created_at,updated_at FROM tasks WHERE project_id=? ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END,priority DESC,created_at ASC",new String[]{String.valueOf(projectId)})){while(c.moveToNext())out.add(new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),String.valueOf(c.getInt(3)),String.valueOf(c.getLong(4)),String.valueOf(c.getLong(5))});}return out;
    }
    public void toggleTask(long id,boolean done){ContentValues cv=new ContentValues();cv.put("status",done?"DONE":"OPEN");cv.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("tasks",cv,"id=?",new String[]{String.valueOf(id)});}

    public void addTurn(String role,String text,String mode){if(text==null||text.trim().isEmpty())return;ContentValues cv=new ContentValues();cv.put("role",safe(role));cv.put("text",text.trim());cv.put("mode",safe(mode));cv.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("conversations",null,cv);}
    public List<String[]> recentTurns(int limit){ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT role,text,mode,created_at FROM conversations ORDER BY id DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(0,new String[]{c.getString(0),c.getString(1),c.getString(2),String.valueOf(c.getLong(3))});}return out;}

    public long addHypothesis(String statement){if(statement==null||statement.trim().isEmpty())return -1;long now=System.currentTimeMillis();ContentValues cv=new ContentValues();cv.put("statement",statement.trim());cv.put("created_at",now);cv.put("updated_at",now);return getWritableDatabase().insert("hypotheses",null,cv);}
    public List<String[]> hypotheses(){ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,statement,status,support,oppose,confidence FROM hypotheses ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END,updated_at DESC",null)){while(c.moveToNext())out.add(new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),String.valueOf(c.getInt(3)),String.valueOf(c.getInt(4)),String.valueOf(c.getInt(5))});}return out;}
    public void scoreHypothesis(long id,int supportDelta,int opposeDelta){SQLiteDatabase db=getWritableDatabase();db.execSQL("UPDATE hypotheses SET support=MAX(0,support+?),oppose=MAX(0,oppose+?),confidence=MAX(1,MIN(10,5+support+?-oppose-?)),updated_at=? WHERE id=?",new Object[]{supportDelta,opposeDelta,supportDelta,opposeDelta,System.currentTimeMillis(),id});}
    public void setHypothesisStatus(long id,String status){ContentValues cv=new ContentValues();cv.put("status",status);cv.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("hypotheses",cv,"id=?",new String[]{String.valueOf(id)});}

    private static String safe(String s){return s==null?"":s.trim();}
}
