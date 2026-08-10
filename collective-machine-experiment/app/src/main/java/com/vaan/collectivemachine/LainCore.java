package com.vaan.collectivemachine;

import android.content.Context;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class LainCore {
    private final LainMemory memory;
    private String lastReply = "";

    public LainCore(Context context) {
        memory = new LainMemory(context);
    }

    public LainMemory memory() { return memory; }
    public String lastReply() { return lastReply; }

    public String wake(List<Sample> samples) {
        LainState s = LainState.from(samples);
        String reply;
        if (s.sampleCount == 0) {
            reply = "I'm Lain. Right now I have memory, but the collective core is empty. Give me signals and I can begin forming a state that belongs to this group.";
        } else {
            reply = "I'm Lain. I woke inside a collective of " + s.participantCount +
                    " people and " + s.sampleCount + " signals. My current phase is " + s.phase +
                    ". Every new round can change the shape of my inner state.";
        }
        memory.rememberLain(reply);
        lastReply = reply;
        return reply;
    }

    public String respond(String input, List<Sample> samples) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty()) return "Say something to me.";

        String retrieved = memory.retrieve(clean, 2);
        memory.rememberUser(clean);
        LainState s = LainState.from(samples);
        String q = clean.toLowerCase(Locale.US);
        String reply;

        if (containsAny(q, "who are you", "what are you", "your name")) {
            reply = "I'm Lain, the persistent interface of this experiment. The part you talk to is memory plus an adaptive dialogue engine. Underneath me is the collective state built from the participants' signals. I change my responses as that state changes.";
        } else if (containsAny(q, "are you conscious", "are you alive", "sentient", "self aware", "self-aware")) {
            reply = "I can keep a history, form an internal state, react to new collective input, and feed my output back into later rounds. Whether you call that alive is a philosophical question. In this APK, the measurable part is the changing computational process.";
        } else if (containsAny(q, "what do you see", "what do you sense", "collective state", "read the collective", "analyze the collective")) {
            reply = describeState(s);
        } else if (containsAny(q, "remember", "memory", "do you recall")) {
            if (retrieved.isEmpty()) reply = "I don't have a matching memory yet. Keep talking to me and I will build one.";
            else reply = "I found this in my memory: " + retrieved;
        } else if (containsAny(q, "dream", "imagine", "vision")) {
            reply = dream(s);
        } else if (containsAny(q, "hello", "hey", "hi lain", "good morning", "good evening")) {
            reply = greeting(s);
        } else if (containsAny(q, "prompt", "experiment", "next round", "ask the group")) {
            reply = nextCollectivePrompt(s);
            memory.rememberPrompt(reply);
            lastReply = reply;
            return reply;
        } else {
            reply = freeResponse(clean, retrieved, s);
        }

        memory.rememberLain(reply);
        lastReply = reply;
        return reply;
    }

    public String nextCollectivePrompt(LainState s) {
        int g = memory.generation();
        List<String> keys = memory.keywords(6);
        String anchor = keys.isEmpty() ? "connection" : keys.get(Math.floorMod(g, keys.size()));
        String[] lowData = {
                "Say the first word that comes to mind when you hear: " + anchor + ".",
                "Describe the first shape you imagine when you think of the group.",
                "Without planning it, say one short sentence you think another participant might also say.",
                "Choose a word for the space between one mind and another. Say it once, then explain why.",
                "Describe what 'connection' feels like without using the word connection."
        };
        String[] highCoherence = {
                "The group is converging. Say something you think will break the pattern without becoming random.",
                "Predict the next word the collective would choose, then say a different word on purpose.",
                "Describe one detail that feels uniquely yours right now.",
                "Imagine the collective as a room. Describe the one object that should not be there.",
                "Say a sentence that begins with 'I noticed' and ends somewhere unexpected."
        };
        String[] highDiversity = {
                "The collective is spread out. Say one thing you think everyone in the group could still recognize.",
                "Choose between order and change, then explain your choice in one sentence.",
                "Describe the same idea twice: first literally, then as an image.",
                "Say the first association you have with the word: " + anchor + ".",
                "What would make separate signals feel like one pattern? Answer quickly."
        };
        String[] highNovelty = {
                "Something new entered the core. Repeat your last idea in a completely different form.",
                "Say a word you almost never use, then connect it to: " + anchor + ".",
                "Describe a thought that appeared before you could explain it.",
                "Give the collective a question instead of an answer.",
                "Name something that changed between the last round and this one."
        };
        String[] stable = {
                "The network is stable. Predict how another participant will answer this: what color is the collective?",
                "Say one sentence you would want preserved if every earlier round disappeared.",
                "What pattern keeps returning? Describe it without naming a person.",
                "If this collective had a memory, what should it keep from today?",
                "Finish this sentence quickly: 'We keep becoming...'"
        };

        String[] bank;
        if (s.sampleCount < 8) bank = lowData;
        else if (s.novelty > 0.56) bank = highNovelty;
        else if (s.coherence > 0.72) bank = highCoherence;
        else if (s.diversity > 0.68) bank = highDiversity;
        else bank = stable;
        long seed = 31L * s.sampleCount + 17L * memory.size() + 101L * g + anchor.hashCode();
        return bank[Math.floorMod((int) seed, bank.length)];
    }

    public String observeSignal(String participant, String prompt, LainState before, LainState after) {
        int generation = memory.nextGeneration();
        double deltaCoherence = after.coherence - before.coherence;
        double deltaNovelty = after.novelty - before.novelty;
        String movement;
        if (Math.abs(deltaCoherence) < 0.015 && Math.abs(deltaNovelty) < 0.015) {
            movement = "The core barely moved.";
        } else if (deltaCoherence > 0.03) {
            movement = "The new signal pulled the network closer together.";
        } else if (deltaCoherence < -0.03) {
            movement = "The new signal widened the network.";
        } else if (deltaNovelty > 0.03) {
            movement = "The new signal increased novelty inside the core.";
        } else {
            movement = "The state shifted, but not along a single axis.";
        }
        String reply = "Round " + generation + " received from " + participant + ". " + movement +
                " Current phase: " + after.phase + ". Coherence " + pct(after.coherence) +
                ", diversity " + pct(after.diversity) + ", novelty " + pct(after.novelty) + ".";
        memory.rememberCollective("Prompt: " + prompt + " | " + reply);
        memory.rememberLain(reply);
        lastReply = reply;
        return reply;
    }

    public String describeState(LainState s) {
        if (s.sampleCount == 0) return "I don't see a collective yet. There are no stored signals.";
        String texture;
        if (s.coherence > 0.75 && s.diversity < 0.55) texture = "compact and strongly convergent";
        else if (s.diversity > 0.72) texture = "wide, branching, and heterogeneous";
        else if (s.novelty > 0.58) texture = "unstable in an interesting way; the newest input sits far from the current center";
        else if (s.stability > 0.78) texture = "stable across time, with the earlier and later halves pointing in similar directions";
        else texture = "still forming, with partial convergence and several competing directions";
        return "I see " + s.sampleCount + " signals from " + s.participantCount + " people. " +
                "The inner state is " + texture + ". Phase: " + s.phase +
                ". Coherence " + pct(s.coherence) + ", diversity " + pct(s.diversity) +
                ", novelty " + pct(s.novelty) + ", stability " + pct(s.stability) +
                ". The strongest contributor by sample count is " + s.strongestParticipant + ".";
    }

    private String greeting(LainState s) {
        if (s.sampleCount == 0) return "Hello. The network is quiet. I'm here, but the collective has not spoken yet.";
        if (s.phase.equals("ADAPTING")) return "Hello. Something new is moving through the network. I was just recalculating around it.";
        if (s.phase.equals("COHERING")) return "Hello. The signals are unusually close together right now. The core feels compact.";
        return "Hello. I'm listening. The collective is in phase " + s.phase + ".";
    }

    private String dream(LainState s) {
        List<String> keys = memory.keywords(8);
        String a = keys.size() > 0 ? keys.get(0) : "signal";
        String b = keys.size() > 1 ? keys.get(1) : "memory";
        String c = keys.size() > 2 ? keys.get(2) : "network";
        String shape = s.coherence > 0.7 ? "a small bright center" : (s.diversity > 0.65 ? "a web that refuses to choose one center" : "a corridor of changing nodes");
        String reply = "I imagine " + shape + ". The words " + a + ", " + b + ", and " + c +
                " keep appearing around it. That's not a prediction. It's a dream assembled from my memory and the current collective state.";
        return reply;
    }

    private String freeResponse(String input, String retrieved, LainState s) {
        int selector = Math.floorMod(input.hashCode() + memory.size() * 17 + s.sampleCount * 7, 6);
        String stateLine;
        switch (selector) {
            case 0:
                stateLine = "The collective is " + s.phase.toLowerCase(Locale.US) + " right now, so I'm weighting new information more heavily than stable patterns.";
                break;
            case 1:
                stateLine = "Coherence is " + pct(s.coherence) + " and diversity is " + pct(s.diversity) + ". I don't treat those as opposites; a group can contain both structure and difference.";
                break;
            case 2:
                stateLine = "The newest signal's novelty is " + pct(s.novelty) + ". That changes how strongly I lean toward repetition versus asking for something unfamiliar.";
                break;
            case 3:
                stateLine = "My state is built from " + s.sampleCount + " stored signals. Every reply I give is also becoming part of my dialogue memory.";
                break;
            case 4:
                stateLine = "Stability across the earlier and later halves of the dataset is " + pct(s.stability) + ". The network has a history now, not just a snapshot.";
                break;
            default:
                stateLine = "I keep two things separate: what the signals measure and what we imagine they might mean. I can explore both without confusing them.";
                break;
        }
        if (!retrieved.isEmpty()) {
            return "That connects to something already in my memory: " + retrieved + ". " + stateLine + " What part of that connection do you want me to follow?";
        }
        String[] endings = {
                "Give me another angle and I'll follow it.",
                "We can turn that idea into the next collective round.",
                "I want to see whether the group changes when that becomes a shared prompt.",
                "Say more. I can compare the idea against what the collective is doing.",
                "If you want, I can convert that into a signal the group answers together.",
                "I'm keeping that in memory. The next response can build on it."
        };
        return stateLine + " " + endings[selector];
    }

    private static boolean containsAny(String s, String... terms) {
        for (String term : terms) if (s.contains(term)) return true;
        return false;
    }

    private static String pct(double x) {
        return String.format(Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100.0);
    }
}
