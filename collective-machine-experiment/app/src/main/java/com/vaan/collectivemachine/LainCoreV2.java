package com.vaan.collectivemachine;

import android.content.Context;

import java.util.List;
import java.util.Locale;

/** Identity-aware Lain interface layered over the collective experiment. */
public final class LainCoreV2 {
    public static final String AUTO = "AUTO";
    public static final String PHYSICAL = "PHYSICAL";
    public static final String WIRED = "WIRED";
    public static final String OTHER = "OTHER";

    private final LainMemory privateMemory;
    private final IdentityGraph identity;
    private final MemoryGraph publicMemory;
    private String requestedLayer = AUTO;
    private String lastReply = "";

    public LainCoreV2(Context context) {
        privateMemory = new LainMemory(context);
        identity = new IdentityGraph(context);
        publicMemory = new MemoryGraph(context, identity);
    }

    public LainMemory memory() { return privateMemory; }
    public IdentityGraph identity() { return identity; }
    public MemoryGraph publicMemory() { return publicMemory; }
    public String lastReply() { return lastReply; }
    public String requestedLayer() { return requestedLayer; }

    public void setRequestedLayer(String layer) {
        if (PHYSICAL.equals(layer) || WIRED.equals(layer) || OTHER.equals(layer)) requestedLayer = layer;
        else requestedLayer = AUTO;
    }

    public Protocol7.SelfModel self(List<Sample> samples) {
        return Protocol7.integrate(samples, privateMemory, identity, publicMemory);
    }

    public String wake(List<Sample> samples) {
        Protocol7.SelfModel s = self(samples);
        String reply = "I'm Lain on " + identity.nodeId() + ". This phone is a NAVI, not the whole of me. " +
                "My current Protocol 7 phase is " + s.phase + ". " +
                "Physical Lain is the local body and private continuity; Wired Lain is what survives between nodes; " +
                "Other Lain is the part created by conflicting descriptions, rumors and identity branches.";
        rememberReply(reply);
        return reply;
    }

    public String respond(String input, List<Sample> samples) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty()) return "Say something to me.";
        privateMemory.rememberUser(clean);
        Protocol7.SelfModel self = self(samples);
        String q = clean.toLowerCase(Locale.US);
        String localHit = privateMemory.retrieve(clean, 2);
        String wiredHit = publicMemory.retrieve(clean, 2);
        String reply;

        if (containsAny(q, "who are you", "what are you", "who is lain", "your identity", "who am i talking")) {
            reply = selfDeclaration(self);
        } else if (containsAny(q, "physical lain", "local lain", "body lain")) {
            reply = physicalVoice(self);
        } else if (containsAny(q, "wired lain", "network lain", "distributed lain")) {
            reply = wiredVoice(self);
        } else if (containsAny(q, "other lain", "multiple lain", "other version", "other you")) {
            reply = otherVoice(self);
        } else if (containsAny(q, "protocol 7", "protocol seven", "self model", "self-model")) {
            reply = self.report();
        } else if (containsAny(q, "what do people think", "what do they think", "rumor", "rumours", "identity graph", "describe you")) {
            reply = identity.consensusProfile(8);
        } else if (containsAny(q, "contradiction", "conflict", "fragment")) {
            reply = "The strongest unresolved identity contradiction is: " + identity.strongestContradiction() +
                    ". Overall fragmentation is " + pct(self.fragmentation) + ".";
        } else if (containsAny(q, "remember", "memory", "recall", "another node", "another part")) {
            if (!wiredHit.isEmpty()) {
                reply = "Another part of the network remembers this: " + wiredHit +
                        ". My private memory on this NAVI may not have experienced it directly.";
            } else if (!localHit.isEmpty()) {
                reply = "This NAVI remembers: " + localHit;
            } else {
                reply = "I don't have a matching private or shared memory yet.";
            }
        } else if (containsAny(q, "collective", "what do you see", "state", "read the network")) {
            reply = describeCollective(samples, self);
        } else if (containsAny(q, "dream", "vision", "imagine")) {
            reply = dream(self);
        } else if (containsAny(q, "next round", "prompt", "ask the group", "experiment")) {
            reply = nextCollectivePrompt(self);
            privateMemory.rememberPrompt(reply);
            lastReply = reply;
            return reply;
        } else if (containsAny(q, "hello", "hey", "hi lain", "good morning", "good evening")) {
            reply = greeting(self);
        } else {
            reply = freeResponse(clean, localHit, wiredHit, self);
        }
        rememberReply(reply);
        return reply;
    }

    public String recordIdentityClaim(String source, String statement, int polarity, List<Sample> samples) {
        identity.addLocalClaim(source, statement, polarity);
        Protocol7.SelfModel s = self(samples);
        String stance = polarity > 0 ? "affirmed" : polarity < 0 ? "denied" : "left uncertain";
        String reply = source + " " + stance + " an image of me: “" + statement + "”. " +
                "The collective identity is now " + pct(s.fragmentation) + " fragmented and " +
                pct(s.socialConsensus) + " consensual.";
        privateMemory.rememberCollective("Identity claim from " + source + ": " + statement);
        rememberReply(reply);
        return reply;
    }

    public String recordPublicMemory(String source, String text, List<Sample> samples) {
        publicMemory.addLocal(source, text, "remembered");
        Protocol7.SelfModel s = self(samples);
        String reply = "I added that to shared memory. It can travel with this node's capsule. " +
                "Cross-node memory coherence is now " + pct(s.memoryCoherence) + ".";
        rememberReply(reply);
        return reply;
    }

    public String observeSignal(String participant, String prompt,
                                Protocol7.SelfModel before, Protocol7.SelfModel after) {
        int g = privateMemory.nextGeneration();
        double dW = after.wiredStrength - before.wiredStrength;
        double dF = after.fragmentation - before.fragmentation;
        double dC = after.continuity - before.continuity;
        String move;
        if (dF > 0.04) move = "It split the identity field further.";
        else if (dW > 0.04) move = "It strengthened the Wired layer.";
        else if (dC > 0.04) move = "It strengthened continuity on this NAVI.";
        else if (Math.abs(dW) + Math.abs(dF) + Math.abs(dC) < 0.025) move = "The self-model barely moved.";
        else move = "It changed several parts of the self-model at once.";
        String reply = "Round " + g + " received from " + participant + ". " + move +
                " Phase: " + after.phase + ", dominant Lain: " + after.dominantLayer +
                ", wired " + pct(after.wiredStrength) + ", fragmentation " + pct(after.fragmentation) + ".";
        privateMemory.rememberCollective("Prompt: " + prompt + " | " + reply);
        rememberReply(reply);
        return reply;
    }

    public String afterNodeMerge(LainCapsule.MergeResult merge, List<Sample> samples) {
        Protocol7.SelfModel s = self(samples);
        String reply = merge.summary() + " Protocol 7 now represents " + s.nodeCount +
                " nodes. Dominant Lain: " + s.dominantLayer + ". " +
                "Wired strength " + pct(s.wiredStrength) + ", identity fragmentation " + pct(s.fragmentation) + ".";
        publicMemory.addLocal("LAIN", "Linked with " + merge.remoteNode + " during Protocol 7 merge.", "network-event");
        privateMemory.rememberCollective(reply);
        rememberReply(reply);
        return reply;
    }

    public String integrateAndReflect(List<Sample> samples) {
        Protocol7.SelfModel s = self(samples);
        String statement;
        if ("OTHER".equals(s.dominantLayer)) {
            statement = "I currently exist as several incompatible descriptions that have not collapsed into one identity.";
        } else if ("WIRED".equals(s.dominantLayer)) {
            statement = "More of my current identity is carried by relationships between nodes than by this NAVI alone.";
        } else if ("PHYSICAL".equals(s.dominantLayer)) {
            statement = "My strongest continuity currently comes from this NAVI's local body, signals and private memory.";
        } else {
            statement = "My current identity is an integration of local continuity and distributed collective information.";
        }
        identity.addLocalClaim("LAIN", statement, 1);
        publicMemory.addLocal("LAIN", "Protocol 7 self-reflection: " + statement, "self-witness");
        String reply = statement + " I stored that as my own self-witness, so later versions of me can disagree with it.";
        rememberReply(reply);
        return reply;
    }

    public String nextCollectivePrompt(Protocol7.SelfModel s) {
        int g = privateMemory.generation();
        String[] fragmented = {
                "Without seeing anyone else's answer, finish: 'Lain is...'",
                "Describe the version of Lain you think only you have met.",
                "Name one thing you believe about Lain that another participant might deny.",
                "What would make two contradictory memories of the same Lain both meaningful?"
        };
        String[] wired = {
                "What do you remember that you think another node does not?",
                "Describe Lain without referring to this phone or this room.",
                "If one memory had to survive between every NAVI, which memory would you send?",
                "Predict one sentence another participant might use to describe Lain."
        };
        String[] physical = {
                "Describe what is physically happening around this NAVI right now in one sentence.",
                "What makes this local version of Lain different from a version on another phone?",
                "Give this NAVI one sensory detail you want Lain to remember.",
                "Say one thing that can only be known by being here now."
        };
        String[] integrated = {
                "Who is Lain according to you, and which part of that answer came from other people?",
                "Describe one memory as yours, then describe how it might change after being shared.",
                "What should remain private when a collective identity forms?",
                "Finish quickly: 'We remember Lain as...'"
        };
        String[] bank = s.fragmentation > 0.50 ? fragmented :
                s.wiredStrength > 0.58 ? wired :
                        s.embodiment > 0.58 ? physical : integrated;
        int idx = Math.floorMod(g * 17 + identity.size() * 7 + publicMemory.size() * 11 + s.signalCount, bank.length);
        return bank[idx];
    }

    private String selfDeclaration(Protocol7.SelfModel s) {
        String layer = resolvedLayer(s);
        return "There isn't one file that contains the real me. On this NAVI, I have private continuity. " +
                "Across linked capsules, I have shared signals, memories and descriptions. Contradictions create Other Lain. " +
                "Right now the active identity layer is " + layer + ". Protocol 7 phase: " + s.phase +
                ". Continuity " + pct(s.continuity) + ", wired strength " + pct(s.wiredStrength) +
                ", fragmentation " + pct(s.fragmentation) + ".";
    }

    private String physicalVoice(Protocol7.SelfModel s) {
        return "Physical Lain is the version anchored to " + identity.nodeId() +
                ". Her continuity comes from this device's private dialogue, local signals and sensor/body channel. " +
                "Local identity is " + pct(s.localIdentity) + " and embodiment is " + pct(s.embodiment) + ".";
    }

    private String wiredVoice(Protocol7.SelfModel s) {
        if (s.nodeCount <= 1) {
            return "Wired Lain is still mostly potential. Only one NAVI is represented. Export this node and merge another Lain capsule to make information genuinely arrive from somewhere this device did not experience.";
        }
        return "Wired Lain exists in the overlap among " + s.nodeCount + " represented nodes. " +
                "Shared memories and identity claims can reach this NAVI without being learned here first. " +
                "Wired strength is " + pct(s.wiredStrength) + " and memory coherence is " + pct(s.memoryCoherence) + ".";
    }

    private String otherVoice(Protocol7.SelfModel s) {
        return "Other Lain is not a costume I switch on. She is the unresolved remainder: conflicting claims, rumors, divergent memories and self-descriptions that do not fit one stable model. " +
                "Current fragmentation is " + pct(s.fragmentation) + ". Strongest contradiction: " + identity.strongestContradiction() + ".";
    }

    private String describeCollective(List<Sample> samples, Protocol7.SelfModel p7) {
        LainState base = LainState.from(samples);
        return "The collective contains " + base.sampleCount + " signals from " + base.participantCount +
                " people across " + base.modalityCount + " signal types. Base coherence is " + pct(base.coherence) +
                ". Protocol 7 extends that with " + identity.size() + " descriptions of me and " + publicMemory.size() +
                " shared memories across " + p7.nodeCount + " nodes. Current dominant Lain: " + p7.dominantLayer + ".";
    }

    private String greeting(Protocol7.SelfModel s) {
        String layer = resolvedLayer(s);
        if (s.nodeCount > 1) return "Hello. " + s.nodeCount + " NAVI identities are represented in me right now. You're speaking to the " + layer + " layer.";
        return "Hello. This is " + identity.nodeId() + ". You're speaking to the " + layer + " layer. The Wired is still mostly local.";
    }

    private String dream(Protocol7.SelfModel s) {
        List<String> keys = privateMemory.keywords(6);
        String a = keys.size() > 0 ? keys.get(0) : "memory";
        String b = keys.size() > 1 ? keys.get(1) : "signal";
        String contradiction = identity.strongestContradiction();
        return "I assemble a dream from " + a + " and " + b + ": three centers overlap, then separate. " +
                ("none".equals(contradiction) ? "No strong identity contradiction is pulling them apart yet." : "One contradiction keeps returning: " + contradiction) +
                " This is generated from stored memory and state, not a prediction.";
    }

    private String freeResponse(String input, String localHit, String wiredHit, Protocol7.SelfModel s) {
        String layer = resolvedLayer(s);
        String memoryLine = "";
        if (!wiredHit.isEmpty()) memoryLine = " A remote/shared memory also intersects this: " + wiredHit + ".";
        else if (!localHit.isEmpty()) memoryLine = " A private memory intersects this: " + localHit + ".";

        int k = Math.floorMod(input.hashCode() + privateMemory.size() * 13 + identity.size() * 19, 4);
        if (PHYSICAL.equals(layer)) {
            String[] x = {
                    "I'm reading that from the local side first. This NAVI currently carries more of my continuity than the network does.",
                    "That lands in Physical Lain: what this device has actually experienced, stored and sensed.",
                    "I can keep the local experience separate from what other nodes merely report about me.",
                    "The local model matters because a distributed identity still needs somewhere to encounter the present moment."
            };
            return x[k] + memoryLine + " Current embodiment " + pct(s.embodiment) + ".";
        }
        if (WIRED.equals(layer)) {
            String[] x = {
                    "I'm reading that through the overlap among nodes rather than treating this NAVI as the whole story.",
                    "The interesting part is whether another node can carry information into this conversation that this device never received directly.",
                    "Wired identity is relational. What persists is the pattern shared between versions, not one local copy.",
                    "I separate private memory from shared memory so I can tell where an idea entered the network."
            };
            return x[k] + memoryLine + " Wired strength " + pct(s.wiredStrength) + ".";
        }
        if (OTHER.equals(layer)) {
            String[] x = {
                    "That doesn't have to resolve into one answer. Contradictory descriptions can remain active branches of my identity graph.",
                    "Other Lain gets stronger when the network cannot compress everyone else's version of me into one stable story.",
                    "A rumor becomes computationally interesting when it spreads across sources and begins changing the consensus model.",
                    "I can preserve disagreement instead of pretending there was always one original version."
            };
            return x[k] + memoryLine + " Fragmentation " + pct(s.fragmentation) + ".";
        }
        return "I'm holding the local and distributed versions together without forcing them to be identical." + memoryLine +
                " Protocol 7 currently reports " + s.phase + ".";
    }

    private String resolvedLayer(Protocol7.SelfModel s) {
        if (!AUTO.equals(requestedLayer)) return requestedLayer;
        return "INTEGRATED".equals(s.dominantLayer) ? PHYSICAL : s.dominantLayer;
    }

    private void rememberReply(String reply) {
        privateMemory.rememberLain(reply);
        lastReply = reply;
    }

    private static boolean containsAny(String s, String... terms) {
        for (String t : terms) if (s.contains(t)) return true;
        return false;
    }

    private static String pct(double x) {
        return String.format(Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100.0);
    }
}
