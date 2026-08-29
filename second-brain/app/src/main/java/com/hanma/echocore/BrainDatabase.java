package com.hanma.echocore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class BrainDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "echocore.db";
    private static final int DB_VERSION = 2;

    public BrainDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "text TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "importance INTEGER NOT NULL DEFAULT 5," +
                "created_at INTEGER NOT NULL," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "valence INTEGER NOT NULL DEFAULT 0," +
                "confidence INTEGER NOT NULL DEFAULT 7," +
                "novelty INTEGER NOT NULL DEFAULT 5," +
                "last_accessed INTEGER NOT NULL DEFAULT 0," +
                "access_count INTEGER NOT NULL DEFAULT 0," +
                "active INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE associations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "a_id INTEGER NOT NULL," +
                "b_id INTEGER NOT NULL," +
                "relation TEXT NOT NULL DEFAULT 'RELATED'," +
                "weight INTEGER NOT NULL DEFAULT 1," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(a_id,b_id))");
        db.execSQL("CREATE TABLE brain_state (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT NOT NULL DEFAULT ''," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE habits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "cue TEXT NOT NULL DEFAULT ''," +
                "action TEXT NOT NULL DEFAULT ''," +
                "reward TEXT NOT NULL DEFAULT ''," +
                "streak INTEGER NOT NULL DEFAULT 0," +
                "last_done INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");
        createIndexes(db);
        seedState(db);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_created ON memories(created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_active ON memories(active DESC, importance DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assoc_a ON associations(a_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assoc_b ON associations(b_id)");
    }

    private void seedState(SQLiteDatabase db) {
        putState(db, "mood", "0");
        putState(db, "energy", "6");
        putState(db, "curiosity", "7");
        putState(db, "mental_load", "4");
        putState(db, "self_name", "Me");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumn(db, "memories", "valence INTEGER NOT NULL DEFAULT 0");
            addColumn(db, "memories", "confidence INTEGER NOT NULL DEFAULT 7");
            addColumn(db, "memories", "novelty INTEGER NOT NULL DEFAULT 5");
            addColumn(db, "memories", "last_accessed INTEGER NOT NULL DEFAULT 0");
            addColumn(db, "memories", "access_count INTEGER NOT NULL DEFAULT 0");
            addColumn(db, "memories", "active INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS associations (id INTEGER PRIMARY KEY AUTOINCREMENT,a_id INTEGER NOT NULL,b_id INTEGER NOT NULL,relation TEXT NOT NULL DEFAULT 'RELATED',weight INTEGER NOT NULL DEFAULT 1,updated_at INTEGER NOT NULL,UNIQUE(a_id,b_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS brain_state (key TEXT PRIMARY KEY,value TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS habits (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,cue TEXT NOT NULL DEFAULT '',action TEXT NOT NULL DEFAULT '',reward TEXT NOT NULL DEFAULT '',streak INTEGER NOT NULL DEFAULT 0,last_done INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
            createIndexes(db);
            seedState(db);
        }
    }

    private void addColumn(SQLiteDatabase db, String table, String definition) {
        try { db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + definition); } catch (Exception ignored) {}
    }

    public long addMemory(String text, String type, String tags, int importance) {
        return addMemoryRich(text, type, tags, importance, 0, 7, 5, false);
    }

    public long addMemoryRich(String text, String type, String tags, int importance,
                              int valence, int confidence, int novelty, boolean active) {
        if (text == null || text.trim().isEmpty()) return -1;
        long now = System.currentTimeMillis();
        ContentValues cv = baseMemory(text, type, tags, importance, now, false,
                valence, confidence, novelty, active);
        return getWritableDatabase().insert("memories", null, cv);
    }

    public long addMemoryAt(String text, String type, String tags, int importance, long createdAt, boolean pinned) {
        return addMemoryAtRich(text, type, tags, importance, createdAt, pinned, 0, 7, 5, 0, 0, false);
    }

    public long addMemoryAtRich(String text, String type, String tags, int importance, long createdAt, boolean pinned,
                                int valence, int confidence, int novelty, long lastAccessed, int accessCount, boolean active) {
        if (text == null || text.trim().isEmpty()) return -1;
        long t = createdAt > 0 ? createdAt : System.currentTimeMillis();
        ContentValues cv = baseMemory(text, type, tags, importance, t, pinned,
                valence, confidence, novelty, active);
        cv.put("last_accessed", lastAccessed);
        cv.put("access_count", Math.max(0, accessCount));
        return getWritableDatabase().insert("memories", null, cv);
    }

    private ContentValues baseMemory(String text, String type, String tags, int importance, long createdAt,
                                     boolean pinned, int valence, int confidence, int novelty, boolean active) {
        ContentValues cv = new ContentValues();
        cv.put("text", text.trim());
        cv.put("type", type == null ? "THOUGHT" : type.trim().toUpperCase());
        cv.put("tags", tags == null ? "" : tags.trim());
        cv.put("importance", clamp(importance, 1, 10));
        cv.put("created_at", createdAt);
        cv.put("pinned", pinned ? 1 : 0);
        cv.put("valence", clamp(valence, -5, 5));
        cv.put("confidence", clamp(confidence, 1, 10));
        cv.put("novelty", clamp(novelty, 1, 10));
        cv.put("last_accessed", 0);
        cv.put("access_count", 0);
        cv.put("active", active ? 1 : 0);
        return cv;
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public List<MemoryNode> recent(int limit) {
        return read("SELECT * FROM memories ORDER BY pinned DESC, active DESC, created_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> since(long timestamp, int limit) {
        return read("SELECT * FROM memories WHERE created_at >= ? ORDER BY pinned DESC, active DESC, importance DESC, created_at DESC LIMIT ?",
                new String[]{String.valueOf(timestamp), String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> byType(String type, int limit) {
        return read("SELECT * FROM memories WHERE type = ? ORDER BY pinned DESC, active DESC, importance DESC, created_at DESC LIMIT ?",
                new String[]{type == null ? "" : type.toUpperCase(), String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> activeWorkspace(int limit) {
        return read("SELECT * FROM memories WHERE active=1 ORDER BY pinned DESC, importance DESC, last_accessed DESC, created_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> strongest(int limit) {
        List<MemoryNode> list = recent(Math.max(40, limit * 5));
        long now = System.currentTimeMillis();
        list.sort((a,b) -> Double.compare(b.activationScore(now), a.activationScore(now)));
        return new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
    }

    public List<MemoryNode> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return recent(limit);
        String[] raw = q.replaceAll("[^a-z0-9# ]", " ").split("\\s+");
        ArrayList<String> tokens = new ArrayList<>();
        for (String token : raw) {
            if (token.length() >= 2 && !tokens.contains(token)) tokens.add(token);
            if (tokens.size() >= 10) break;
        }
        if (tokens.isEmpty()) return recent(limit);
        StringBuilder sql = new StringBuilder("SELECT * FROM memories WHERE ");
        ArrayList<String> args = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("LOWER(text) LIKE ? OR LOWER(tags) LIKE ? OR LOWER(type) LIKE ?");
            String like = "%" + tokens.get(i) + "%";
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY pinned DESC, active DESC, importance DESC, access_count DESC, created_at DESC LIMIT ?");
        args.add(String.valueOf(Math.max(1, limit)));
        List<MemoryNode> out = read(sql.toString(), args.toArray(new String[0]));
        touchAll(out);
        wireCoactivation(out);
        return out;
    }

    public void touch(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE memories SET last_accessed=?, access_count=access_count+1 WHERE id=?",
                new Object[]{System.currentTimeMillis(), id});
    }

    public void touchAll(List<MemoryNode> nodes) {
        if (nodes == null) return;
        for (MemoryNode m : nodes) touch(m.id);
    }

    public void wireCoactivation(List<MemoryNode> nodes) {
        if (nodes == null) return;
        int n = Math.min(4, nodes.size());
        for (int i=0;i<n;i++) for (int j=i+1;j<n;j++) reinforceAssociation(nodes.get(i).id, nodes.get(j).id, "CO-ACTIVATED", 1);
    }

    public void reinforceAssociation(long x, long y, String relation, int delta) {
        if (x <= 0 || y <= 0 || x == y) return;
        long a = Math.min(x,y), b = Math.max(x,y);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.execSQL("INSERT OR IGNORE INTO associations(a_id,b_id,relation,weight,updated_at) VALUES(?,?,?,?,?)",
                new Object[]{a,b,relation == null ? "RELATED" : relation,Math.max(1,delta),now});
        db.execSQL("UPDATE associations SET weight=MIN(99,weight+?), relation=?, updated_at=? WHERE a_id=? AND b_id=?",
                new Object[]{Math.max(1,delta),relation == null ? "RELATED" : relation,now,a,b});
    }

    public List<String> strongestAssociations(int limit) {
        ArrayList<String> out = new ArrayList<>();
        String sql = "SELECT a.text,b.text,x.relation,x.weight FROM associations x JOIN memories a ON a.id=x.a_id JOIN memories b ON b.id=x.b_id ORDER BY x.weight DESC,x.updated_at DESC LIMIT ?";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(Math.max(1,limit))})) {
            while (c.moveToNext()) out.add(c.getString(0) + " ↔ " + c.getString(1) + " · " + c.getString(2) + " · " + c.getInt(3));
        }
        return out;
    }

    public int countAssociations() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM associations", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int countType(String type) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories WHERE type=?", new String[]{type.toUpperCase()})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("associations", "a_id=? OR b_id=?", new String[]{String.valueOf(id),String.valueOf(id)});
        db.delete("memories", "id=?", new String[]{String.valueOf(id)});
    }

    public void setPinned(long id, boolean pinned) { updateBool(id,"pinned",pinned); }
    public void setActive(long id, boolean active) { updateBool(id,"active",active); }

    private void updateBool(long id, String field, boolean value) {
        ContentValues cv = new ContentValues(); cv.put(field, value ? 1 : 0);
        getWritableDatabase().update("memories", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void setType(long id, String type) {
        ContentValues cv = new ContentValues(); cv.put("type", type.toUpperCase());
        getWritableDatabase().update("memories", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateAffect(long id, int valence, int confidence, int novelty, int importance) {
        ContentValues cv = new ContentValues();
        cv.put("valence", clamp(valence,-5,5)); cv.put("confidence",clamp(confidence,1,10));
        cv.put("novelty",clamp(novelty,1,10)); cv.put("importance",clamp(importance,1,10));
        getWritableDatabase().update("memories",cv,"id=?",new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("associations", null, null); db.delete("memories", null, null); db.delete("habits",null,null);
    }

    public void setState(String key, String value) { putState(getWritableDatabase(),key,value); }

    private void putState(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues(); cv.put("key",key); cv.put("value",value == null ? "" : value); cv.put("updated_at",System.currentTimeMillis());
        db.insertWithOnConflict("brain_state",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getState(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM brain_state WHERE key=?",new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }

    public int getStateInt(String key, int fallback) {
        try { return Integer.parseInt(getState(key,String.valueOf(fallback))); } catch (Exception e) { return fallback; }
    }

    public long addHabit(String name, String cue, String action, String reward) {
        if (name == null || name.trim().isEmpty()) return -1;
        ContentValues cv = new ContentValues();
        cv.put("name",name.trim()); cv.put("cue",safe(cue)); cv.put("action",safe(action)); cv.put("reward",safe(reward));
        cv.put("streak",0); cv.put("last_done",0); cv.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insert("habits",null,cv);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    public List<String[]> habits() {
        ArrayList<String[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,cue,action,reward,streak,last_done FROM habits ORDER BY streak DESC,created_at DESC",null)) {
            while (c.moveToNext()) out.add(new String[]{String.valueOf(c.getLong(0)),c.getString(1),c.getString(2),c.getString(3),c.getString(4),String.valueOf(c.getInt(5)),String.valueOf(c.getLong(6))});
        }
        return out;
    }

    public void completeHabit(long id) {
        long now = System.currentTimeMillis();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT last_done,streak FROM habits WHERE id=?",new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return;
            long last = c.getLong(0); int streak = c.getInt(1);
            long day = 86400000L;
            int next = (last > 0 && now - last < day * 2) ? streak + 1 : 1;
            ContentValues cv = new ContentValues(); cv.put("streak",next); cv.put("last_done",now);
            getWritableDatabase().update("habits",cv,"id=?",new String[]{String.valueOf(id)});
        }
    }

    public void deleteHabit(long id) { getWritableDatabase().delete("habits","id=?",new String[]{String.valueOf(id)}); }

    private List<MemoryNode> read(String sql, String[] args) {
        ArrayList<MemoryNode> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            int id = c.getColumnIndexOrThrow("id"), text = c.getColumnIndexOrThrow("text"), type = c.getColumnIndexOrThrow("type"),
                    tags = c.getColumnIndexOrThrow("tags"), importance = c.getColumnIndexOrThrow("importance"), created = c.getColumnIndexOrThrow("created_at"),
                    pinned = c.getColumnIndexOrThrow("pinned"), valence = c.getColumnIndexOrThrow("valence"), confidence = c.getColumnIndexOrThrow("confidence"),
                    novelty = c.getColumnIndexOrThrow("novelty"), lastAccessed = c.getColumnIndexOrThrow("last_accessed"), accessCount = c.getColumnIndexOrThrow("access_count"),
                    active = c.getColumnIndexOrThrow("active");
            while (c.moveToNext()) out.add(new MemoryNode(c.getLong(id),c.getString(text),c.getString(type),c.getString(tags),c.getInt(importance),
                    c.getLong(created),c.getInt(pinned)==1,c.getInt(valence),c.getInt(confidence),c.getInt(novelty),c.getLong(lastAccessed),c.getInt(accessCount),c.getInt(active)==1));
        }
        return out;
    }
}
