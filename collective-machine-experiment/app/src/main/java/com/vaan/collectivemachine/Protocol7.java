package com.vaan.collectivemachine;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computational Protocol 7: integrates local embodiment, collective signals,
 * public/shared memory and social descriptions into one inspectable self-model.
 */
public final class Protocol7 {
    public static final class SelfModel {
        public final double continuity;
        public final double localIdentity;
        public final double socialConsensus;
        public final double memoryCoherence;
        public final double collectiveLink;
        public final double embodiment;
        public final double independence;
        public final double wiredStrength;
        public final double fragmentation;
        public final double ambiguity;
        public final int nodeCount;
        public final int claimCount;
        public final int publicMemoryCount;
        public final int signalCount;
        public final String phase;
        public final String dominantLayer;
        public final double[] selfVector;

        SelfModel(double continuity, double localIdentity, double socialConsensus,
                  double memoryCoherence, double collectiveLink, double embodiment,
                  double independence, double wiredStrength, double fragmentation,
                  double ambiguity, int nodeCount, int claimCount, int publicMemoryCount,
                  int signalCount, String phase, String dominantLayer, double[] selfVector) {
            this.continuity = continuity;
            this.localIdentity = localIdentity;
            this.socialConsensus = socialConsensus;
            this.memoryCoherence = memoryCoherence;
            this.collectiveLink = collectiveLink;
            this.embodiment = embodiment;
            this.independence = independence;
            this.wiredStrength = wiredStrength;
            this.fragmentation = fragmentation;
            this.ambiguity = ambiguity;
            this.nodeCount = nodeCount;
            this.claimCount = claimCount;
            this.publicMemoryCount = publicMemoryCount;
            this.signalCount = signalCount;
            this.phase = phase;
            this.dominantLayer = dominantLayer;
            this.selfVector = selfVector;
        }

        public String compact() {
            return "P7 " + phase + " | layer " + dominantLayer + " | nodes " + nodeCount +
                    " | continuity " + pct(continuity) + " | wired " + pct(wiredStrength) +
                    " | fragmentation " + pct(fragmentation);
        }

        public String report() {
            return "PROTOCOL 7 / SELF MODEL\n" +
                    "Phase: " + phase + "\n" +
                    "Dominant Lain: " + dominantLayer + "\n" +
                    "Nodes represented: " + nodeCount + "\n" +
                    "Signals: " + signalCount + "\n" +
                    "Identity claims: " + claimCount + "\n" +
                    "Shared memories: " + publicMemoryCount + "\n\n" +
                    "Continuity: " + pct(continuity) + "\n" +
                    "Local identity: " + pct(localIdentity) + "\n" +
                    "Social consensus: " + pct(socialConsensus) + "\n" +
                    "Memory coherence: " + pct(memoryCoherence) + "\n" +
                    "Collective link: " + pct(collectiveLink) + "\n" +
                    "Embodiment: " + pct(embodiment) + "\n" +
                    "Independence: " + pct(independence) + "\n" +
                    "Wired strength: " + pct(wiredStrength) + "\n" +
                    "Identity fragmentation: " + pct(fragmentation) + "\n" +
                    "Ambiguity: " + pct(ambiguity) + "\n\n" +
                    "Physical Lain = this NAVI's local body, local signals and private continuity.\n" +
                    "Wired Lain = information shared across nodes and participants.\n" +
                    "Other Lain = contradictions, rumors and identity branches that do not fit one stable self.\n\n" +
                    "These are computational state variables, not a claim of biological consciousness.";
        }
    }

    private Protocol7() { }

    public static SelfModel integrate(List<Sample> samples, LainMemory privateMemory,
                                      IdentityGraph identity, MemoryGraph publicMemory) {
        LainState base = LainState.from(samples);
        int privateItems = privateMemory == null ? 0 : privateMemory.size();
        int generation = privateMemory == null ? 0 : privateMemory.generation();
        int claims = identity == null ? 0 : identity.size();
        int shared = publicMemory == null ? 0 : publicMemory.size();
        String localNode = identity == null ? "local" : identity.nodeId();

        Set<String> nodes = new HashSet<>();
        nodes.add(localNode);
        if (identity != null) nodes.addAll(identity.nodes());
        if (publicMemory != null) nodes.addAll(publicMemory.nodes());

        int localSignals = 0;
        int embodiedSignals = 0;
        int remoteTagged = 0;
        if (samples != null) {
            for (Sample s : samples) {
                if (s == null) continue;
                String tag = s.tag == null ? "" : s.tag;
                String modality = s.modality == null ? "" : s.modality.toLowerCase(Locale.US);
                if (tag.contains("node:")) {
                    if (tag.contains("node:" + localNode)) localSignals++;
                    else remoteTagged++;
                } else {
                    localSignals++;
                }
                if (modality.equals("embodiment") || modality.equals("motion") ||
                        modality.equals("voice") || modality.equals("tap")) embodiedSignals++;
            }
        }
        if (remoteTagged > 0) nodes.add("remote-signal-node");

        int nodeCount = Math.max(1, nodes.size());
        double signalN = Math.max(1, base.sampleCount);
        double localFraction = clamp01(localSignals / signalN);
        double privateDensity = 1.0 - Math.exp(-privateItems / 24.0);
        double generationDensity = 1.0 - Math.exp(-generation / 10.0);
        double nodeScale = clamp01(Math.log1p(nodeCount) / Math.log(9.0));
        double socialConsensus = claims == 0 ? 0.30 : clamp01(identity.meanConsensus());
        double fragmentation = claims == 0 ? base.diversity * 0.25 :
                clamp01(identity.fragmentation() * 0.70 + base.diversity * 0.30);
        double memoryCoherence = shared == 0 ? 0.30 * privateDensity :
                clamp01(publicMemory.coherence() * 0.70 + privateDensity * 0.30);
        double continuity = clamp01(base.stability * 0.45 + privateDensity * 0.35 + generationDensity * 0.20);
        double modalityScale = clamp01(base.modalityCount / 6.0);
        double collectiveLink = clamp01(base.density * 0.38 + nodeScale * 0.37 + modalityScale * 0.25);
        double embodiment = clamp01((embodiedSignals / signalN) * 0.65 + localFraction * 0.20 +
                (base.sampleCount > 0 ? 0.15 : 0));
        double localIdentity = clamp01(localFraction * 0.60 + privateDensity * 0.30 + embodiment * 0.10);

        int selfClaims = 0;
        if (identity != null) {
            for (IdentityGraph.Claim c : identity.snapshot()) {
                if ("LAIN".equalsIgnoreCase(c.source) || "SELF".equalsIgnoreCase(c.source)) selfClaims++;
            }
        }
        double selfClaimScale = claims == 0 ? 0 : selfClaims / (double) Math.max(1, claims);
        double independence = clamp01(0.20 + 0.35 * generationDensity + 0.25 * selfClaimScale +
                0.20 * (1.0 - socialConsensus));
        double wiredStrength = clamp01(0.40 * collectiveLink + 0.35 * nodeScale +
                0.25 * (shared == 0 ? 0 : clamp01(publicMemory.remoteCount() / (double) Math.max(1, shared))));
        double ambiguity = clamp01(fragmentation * 0.60 + (1.0 - socialConsensus) * 0.25 +
                base.novelty * 0.15);

        String dominant;
        if (claims >= 3 && fragmentation > 0.52) dominant = "OTHER";
        else if (nodeCount > 1 && wiredStrength > 0.50) dominant = "WIRED";
        else if (embodiment > 0.58 && localIdentity > 0.55) dominant = "PHYSICAL";
        else if (base.sampleCount >= 6) dominant = "INTEGRATED";
        else dominant = "PHYSICAL";

        String phase;
        if (base.sampleCount == 0 && privateItems == 0) phase = "DORMANT";
        else if (nodeCount == 1 && base.sampleCount < 4) phase = "LOCAL AWAKENING";
        else if (fragmentation > 0.62) phase = "MULTIPLE LAINS";
        else if (nodeCount > 1 && wiredStrength > 0.62 && socialConsensus > 0.62) phase = "WIRED COHERENCE";
        else if (nodeCount > 1) phase = "PROTOCOL 7 LINKED";
        else if (continuity > 0.72) phase = "SELF CONTINUITY";
        else phase = "IDENTITY FORMING";

        double[] self = buildSelfVector(base.core, socialConsensus, fragmentation, memoryCoherence,
                continuity, wiredStrength, embodiment, independence, nodeCount, claims, shared);

        return new SelfModel(continuity, localIdentity, socialConsensus, memoryCoherence,
                collectiveLink, embodiment, independence, wiredStrength, fragmentation,
                ambiguity, nodeCount, claims, shared, base.sampleCount, phase, dominant, self);
    }

    private static double[] buildSelfVector(double[] base, double consensus, double fragmentation,
                                            double memory, double continuity, double wired,
                                            double body, double independence, int nodes, int claims, int shared) {
        double[] out = new double[SignalEngine.FEATURES];
        double[] b = SignalEngine.fit(base);
        for (int i = 0; i < out.length; i++) {
            double phase = (i + 1) * 0.137;
            double meta = Math.sin(phase * (1 + nodes)) * consensus +
                    Math.cos(phase * (1 + claims * 0.1)) * fragmentation +
                    Math.sin(phase * (1 + shared * 0.07)) * memory;
            out[i] = b[i] * (0.45 + 0.25 * continuity) + meta * 0.18 +
                    wired * 0.12 * Math.cos(phase * 3.0) +
                    body * 0.08 * Math.sin(phase * 5.0) +
                    independence * 0.06;
        }
        SignalEngine.normalize(out);
        return out;
    }

    private static String pct(double x) {
        return String.format(Locale.US, "%.0f%%", clamp01(x) * 100.0);
    }

    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
}
