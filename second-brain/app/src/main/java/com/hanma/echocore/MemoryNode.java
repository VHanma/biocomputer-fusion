package com.hanma.echocore;

public class MemoryNode {
    public long id;
    public String text;
    public String type;
    public String tags;
    public int importance;
    public long createdAt;
    public boolean pinned;

    public MemoryNode(long id, String text, String type, String tags, int importance, long createdAt, boolean pinned) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.type = type == null ? "THOUGHT" : type;
        this.tags = tags == null ? "" : tags;
        this.importance = importance;
        this.createdAt = createdAt;
        this.pinned = pinned;
    }
}
