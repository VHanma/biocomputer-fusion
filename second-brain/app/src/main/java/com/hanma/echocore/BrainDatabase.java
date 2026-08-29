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
    private static final int DB_VERSION = 1;

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
                "pinned INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_memories_created ON memories(created_at DESC)");
        db.execSQL("CREATE INDEX idx_memories_type ON memories(type)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS memories");
        onCreate(db);
    }

    public long addMemory(String text, String type, String tags, int importance) {
        if (text == null || text.trim().isEmpty()) return -1;
        ContentValues cv = new ContentValues();
        cv.put("text", text.trim());
        cv.put("type", type == null ? "THOUGHT" : type.trim().toUpperCase());
        cv.put("tags", tags == null ? "" : tags.trim());
        cv.put("importance", Math.max(1, Math.min(10, importance)));
        cv.put("created_at", System.currentTimeMillis());
        cv.put("pinned", 0);
        return getWritableDatabase().insert("memories", null, cv);
    }

    public long addMemoryAt(String text, String type, String tags, int importance, long createdAt, boolean pinned) {
        if (text == null || text.trim().isEmpty()) return -1;
        ContentValues cv = new ContentValues();
        cv.put("text", text.trim());
        cv.put("type", type == null ? "THOUGHT" : type.trim().toUpperCase());
        cv.put("tags", tags == null ? "" : tags.trim());
        cv.put("importance", Math.max(1, Math.min(10, importance)));
        cv.put("created_at", createdAt > 0 ? createdAt : System.currentTimeMillis());
        cv.put("pinned", pinned ? 1 : 0);
        return getWritableDatabase().insert("memories", null, cv);
    }

    public List<MemoryNode> recent(int limit) {
        return read("SELECT * FROM memories ORDER BY pinned DESC, created_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> since(long timestamp, int limit) {
        return read("SELECT * FROM memories WHERE created_at >= ? ORDER BY pinned DESC, importance DESC, created_at DESC LIMIT ?",
                new String[]{String.valueOf(timestamp), String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> byType(String type, int limit) {
        return read("SELECT * FROM memories WHERE type = ? ORDER BY pinned DESC, importance DESC, created_at DESC LIMIT ?",
                new String[]{type == null ? "" : type.toUpperCase(), String.valueOf(Math.max(1, limit))});
    }

    public List<MemoryNode> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return recent(limit);
        String[] raw = q.replaceAll("[^a-z0-9# ]", " ").split("\\s+");
        ArrayList<String> tokens = new ArrayList<>();
        for (String token : raw) {
            if (token.length() >= 2 && !tokens.contains(token)) tokens.add(token);
            if (tokens.size() >= 8) break;
        }
        if (tokens.isEmpty()) return recent(limit);
        StringBuilder sql = new StringBuilder("SELECT * FROM memories WHERE ");
        ArrayList<String> args = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("LOWER(text) LIKE ? OR LOWER(tags) LIKE ?");
            String like = "%" + tokens.get(i) + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY pinned DESC, importance DESC, created_at DESC LIMIT ?");
        args.add(String.valueOf(Math.max(1, limit)));
        return read(sql.toString(), args.toArray(new String[0]));
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
        getWritableDatabase().delete("memories", "id=?", new String[]{String.valueOf(id)});
    }

    public void setPinned(long id, boolean pinned) {
        ContentValues cv = new ContentValues();
        cv.put("pinned", pinned ? 1 : 0);
        getWritableDatabase().update("memories", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void setType(long id, String type) {
        ContentValues cv = new ContentValues();
        cv.put("type", type.toUpperCase());
        getWritableDatabase().update("memories", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        getWritableDatabase().delete("memories", null, null);
    }

    private List<MemoryNode> read(String sql, String[] args) {
        ArrayList<MemoryNode> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            int id = c.getColumnIndexOrThrow("id");
            int text = c.getColumnIndexOrThrow("text");
            int type = c.getColumnIndexOrThrow("type");
            int tags = c.getColumnIndexOrThrow("tags");
            int importance = c.getColumnIndexOrThrow("importance");
            int created = c.getColumnIndexOrThrow("created_at");
            int pinned = c.getColumnIndexOrThrow("pinned");
            while (c.moveToNext()) {
                out.add(new MemoryNode(c.getLong(id), c.getString(text), c.getString(type), c.getString(tags),
                        c.getInt(importance), c.getLong(created), c.getInt(pinned) == 1));
            }
        }
        return out;
    }
}
