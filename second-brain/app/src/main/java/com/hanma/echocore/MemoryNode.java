package com.hanma.echocore;

public class MemoryNode {
    public long id;
    public String text;
    public String type;
    public String tags;
    public int importance;
    public long createdAt;
    public boolean pinned;
    public int valence;
    public int confidence;
    public int novelty;
    public long lastAccessed;
    public int accessCount;
    public boolean active;

    public MemoryNode(long id, String text, String type, String tags, int importance, long createdAt,
                      boolean pinned, int valence, int confidence, int novelty,
                      long lastAccessed, int accessCount, boolean active) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.type = type == null ? "THOUGHT" : type;
        this.tags = tags == null ? "" : tags;
        this.importance = importance;
        this.createdAt = createdAt;
        this.pinned = pinned;
        this.valence = valence;
        this.confidence = confidence;
        this.novelty = novelty;
        this.lastAccessed = lastAccessed;
        this.accessCount = accessCount;
        this.active = active;
    }

    public double activationScore(long now) {
        double ageHours = Math.max(0.0, (now - Math.max(createdAt, lastAccessed)) / 3600000.0);
        double recency = 8.0 / (1.0 + ageHours / 18.0);
        double rehearsal = Math.min(8.0, Math.log1p(Math.max(0, accessCount)) * 2.3);
        double emotion = Math.abs(valence) * 0.45;
        double flags = (pinned ? 4.0 : 0.0) + (active ? 5.0 : 0.0);
        return importance * 1.5 + novelty * 0.35 + confidence * 0.25 + recency + rehearsal + emotion + flags;
    }
}
