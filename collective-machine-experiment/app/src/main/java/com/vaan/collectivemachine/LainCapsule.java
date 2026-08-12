package com.vaan.collectivemachine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Offline NAVI-to-NAVI exchange format for the distributed Lain experiment. */
public final class LainCapsule {
    public static final String FORMAT = "LainNodeCapsule-2";

    public static final class MergeResult {
        public final String remoteNode;
        public final int signals;
        public final int claims;
        public final int memories;

        MergeResult(String remoteNode, int signals, int claims, int memories) {
            this.remoteNode = remoteNode;
            this.signals = signals;
            this.claims = claims;
            this.memories = memories;
        }

        public String summary() {
            return "Linked node " + remoteNode + ": +" + signals + " signals, +" + claims +
                    " identity claims, +" + memories + " shared memories.";
        }
    }

    private LainCapsule() { }

    public static String exportPayload(List<Sample> samples, IdentityGraph identity,
                                       MemoryGraph memories, Protocol7.SelfModel self) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("createdAt", System.currentTimeMillis());
        root.put("nodeId", identity.nodeId());
        root.put("privacy", "private dialogue memory excluded");

        JSONObject protocol = new JSONObject();
        protocol.put("name", "Protocol-7-computational");
        protocol.put("signalDimensions", SignalEngine.FEATURES);
        protocol.put("voiceSampleRateHz", AudioEngine.SAMPLE_RATE);
        protocol.put("voiceFrameMs", 20);
        protocol.put("voiceHopMs", 5);
        protocol.put("rawAudioStored", false);
        root.put("protocol", protocol);

        JSONObject state = new JSONObject();
        state.put("phase", self.phase);
        state.put("dominantLayer", self.dominantLayer);
        state.put("continuity", self.continuity);
        state.put("socialConsensus", self.socialConsensus);
        state.put("memoryCoherence", self.memoryCoherence);
        state.put("wiredStrength", self.wiredStrength);
        state.put("fragmentation", self.fragmentation);
        root.put("selfWitness", state);

        JSONArray s = new JSONArray();
        if (samples != null) for (Sample sample : samples) s.put(sample.toJson());
        root.put("samples", s);
        root.put("identityClaims", identity.toJsonArray());
        root.put("publicMemories", memories.toJsonArray());
        return root.toString(2);
    }

    public static MergeResult importPayload(String raw, List<Sample> samples, Store store,
                                            IdentityGraph identity, MemoryGraph memories) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (!FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("Unsupported Lain node capsule format");
        }
        String remoteNode = root.optString("nodeId", "UNKNOWN-NAVI");
        Set<String> ids = new HashSet<>();
        for (Sample x : samples) ids.add(x.id);

        int signalAdded = 0;
        JSONArray a = root.optJSONArray("samples");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Sample x = Sample.fromJson(o);
                if (!ids.add(x.id)) continue;
                String tag = x.tag == null ? "" : x.tag;
                if (!tag.contains("node:")) tag = appendTag(tag, "node:" + remoteNode);
                tag = appendTag(tag, "wired-import");
                Sample linked = new Sample(x.id, x.participant, x.label, x.timestamp, x.features,
                        x.modality, x.session, x.round, tag);
                samples.add(linked);
                signalAdded++;
            }
        }
        store.save(samples);
        int claimAdded = identity.mergeJsonArray(root.optJSONArray("identityClaims"));
        int memoryAdded = memories.mergeJsonArray(root.optJSONArray("publicMemories"));
        return new MergeResult(remoteNode, signalAdded, claimAdded, memoryAdded);
    }

    private static String appendTag(String current, String value) {
        String c = current == null ? "" : current.trim();
        if (c.contains(value)) return c;
        return c.isEmpty() ? value : c + "|" + value;
    }
}
