package com.vaan.collectivemachine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Store {
    private static final String PREF = "collective_machine_store";
    private static final String KEY = "samples_json";
    private final SharedPreferences prefs;

    public Store(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<Sample> load() {
        List<Sample> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(Sample.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public void save(List<Sample> samples) {
        JSONArray a = new JSONArray();
        try {
            for (Sample s : samples) a.put(s.toJson());
            prefs.edit().putString(KEY, a.toString()).apply();
        } catch (JSONException ignored) { }
    }

    public String exportPayload(List<Sample> samples) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "CollectiveMachineCapsule-1");
        root.put("createdAt", System.currentTimeMillis());
        JSONObject protocol = new JSONObject();
        protocol.put("sampleRateHz", AudioEngine.SAMPLE_RATE);
        protocol.put("frameMs", 20);
        protocol.put("hopMs", 5);
        protocol.put("window", "Hamming");
        protocol.put("barkBands", AudioEngine.BARK_BANDS);
        protocol.put("rawAudioStored", false);
        root.put("protocol", protocol);
        JSONArray a = new JSONArray();
        for (Sample s : samples) a.put(s.toJson());
        root.put("samples", a);
        return root.toString(2);
    }

    public int importPayload(String raw, List<Sample> target) throws JSONException {
        JSONObject root = new JSONObject(raw);
        if (!"CollectiveMachineCapsule-1".equals(root.optString("format"))) {
            throw new JSONException("Unsupported capsule format");
        }
        Set<String> ids = new HashSet<>();
        for (Sample s : target) ids.add(s.id);
        JSONArray a = root.getJSONArray("samples");
        int added = 0;
        for (int i = 0; i < a.length(); i++) {
            Sample s = Sample.fromJson(a.getJSONObject(i));
            if (ids.add(s.id)) {
                target.add(s);
                added++;
            }
        }
        save(target);
        return added;
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
