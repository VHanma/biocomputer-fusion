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

    public Sample(String participant, String label, long timestamp, double[] features) {
        this(UUID.randomUUID().toString(), participant, label, timestamp, features);
    }

    public Sample(String id, String participant, String label, long timestamp, double[] features) {
        this.id = id;
        this.participant = participant;
        this.label = label;
        this.timestamp = timestamp;
        this.features = features;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("participant", participant);
        o.put("label", label);
        o.put("timestamp", timestamp);
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
                features
        );
    }
}
