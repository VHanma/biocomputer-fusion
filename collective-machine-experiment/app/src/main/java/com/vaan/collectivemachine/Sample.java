package com.vaan.collectivemachine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class Sample {
    public final String id;
    public final String participant;
    public final String label;
    public final long timestamp;
    public final double[] features;
    public final String modality;
    public final String session;
    public final int round;
    public final String tag;

    public Sample(String participant, String label, long timestamp, double[] features) {
        this(UUID.randomUUID().toString(), participant, label, timestamp, features,
                "voice", "default", 0, "");
    }

    public Sample(String participant, String label, long timestamp, double[] features,
                  String modality, String session, int round, String tag) {
        this(UUID.randomUUID().toString(), participant, label, timestamp, features,
                modality, session, round, tag);
    }

    public Sample(String id, String participant, String label, long timestamp, double[] features) {
        this(id, participant, label, timestamp, features, "voice", "legacy", 0, "");
    }

    public Sample(String id, String participant, String label, long timestamp, double[] features,
                  String modality, String session, int round, String tag) {
        this.id = id;
        this.participant = participant == null ? "Unknown" : participant;
        this.label = label == null ? "Unlabeled" : label;
        this.timestamp = timestamp;
        this.features = features == null ? new double[SignalEngine.FEATURES] : SignalEngine.fit(features);
        this.modality = modality == null || modality.trim().isEmpty() ? "unknown" : modality;
        this.session = session == null || session.trim().isEmpty() ? "default" : session;
        this.round = Math.max(0, round);
        this.tag = tag == null ? "" : tag;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("participant", participant);
        o.put("label", label);
        o.put("timestamp", timestamp);
        o.put("modality", modality);
        o.put("session", session);
        o.put("round", round);
        o.put("tag", tag);
        JSONArray f = new JSONArray();
        for (double v : features) f.put(v);
        o.put("features", f);
        return o;
    }

    public static Sample fromJson(JSONObject o) throws JSONException {
        JSONArray f = o.getJSONArray("features");
        double[] features = new double[f.length()];
        for (int i = 0; i < f.length(); i++) features[i] = f.getDouble(i);
        return new Sample(
                o.getString("id"),
                o.optString("participant", "Unknown"),
                o.optString("label", "Unlabeled"),
                o.optLong("timestamp", System.currentTimeMillis()),
                features,
                o.optString("modality", "voice"),
                o.optString("session", o.has("modality") ? "default" : "legacy"),
                o.optInt("round", 0),
                o.optString("tag", "")
        );
    }
}
