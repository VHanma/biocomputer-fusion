package com.vaan.collectivemachine;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lain v2: the phone is a NAVI. Lain is modeled across local continuity,
 * distributed node data, shared memories and social descriptions.
 */
public final class LainV2Activity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC = 401;
    private static final int REQ_EXPORT_NODE = 510;
    private static final int REQ_IMPORT_NODE = 511;
    private static final int REQ_IMPORT_LEGACY = 512;

    private final int bg = Color.rgb(2, 4, 10);
    private final int panel = Color.rgb(11, 16, 29);
    private final int panel2 = Color.rgb(18, 24, 42);
    private final int text = Color.rgb(232, 241, 238);
    private final int muted = Color.rgb(143, 164, 174);
    private final int green = Color.rgb(77, 239, 178);
    private final int blue = Color.rgb(108, 157, 255);
    private final int violet = Color.rgb(200, 126, 245);
    private final int red = Color.rgb(245, 110, 145);
    private final int gold = Color.rgb(232, 204, 102);

    private Store store;
    private final List<Sample> samples = new ArrayList<>();
    private ModelEngine.Prototype prototype;
    private LainCoreV2 lain;
    private FrameLayout content;

    private EditText chatInput;
    private TextView chatTranscript;
    private TextView selfCard;
    private EditText participantInput;
    private EditText promptInput;
    private EditText responseInput;
    private TextView nodeStatus;
    private EditText identitySourceInput;
    private EditText identityStatementInput;
    private TextView identityReport;
    private EditText publicMemorySourceInput;
    private EditText publicMemoryTextInput;
    private EditText memoryQueryInput;
    private TextView memoryReport;

    private String lastParticipant = "Participant 1";
    private String pendingPrompt = "";
    private String lastReply = "";
    private boolean pendingRecordAfterPermission = false;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        samples.addAll(store.load());
        prototype = ModelEngine.build(samples);
        lain = new LainCoreV2(this);
        tts = new TextToSpeech(this, this);
        buildShell();
        if (lain.memory().size() == 0) lastReply = lain.wake(samples);
        else lastReply = findLastLainReply();
        showLain();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = tv("LAIN", 30, green, true);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        root.addView(title);
        TextView sub = tv("NAVI " + lain.identity().nodeId() + "  /  distributed identity experiment", 10, muted, false);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setPadding(0, 0, 0, dp(7));
        root.addView(sub);

        LinearLayout r1 = tabRow();
        addTab(r1, "LAIN", v -> showLain());
        addTab(r1, "NODE", v -> showNode());
        addTab(r1, "WIRED", v -> showWired());
        root.addView(r1);

        LinearLayout r2 = tabRow();
        addTab(r2, "IDENTITY", v -> showIdentity());
        addTab(r2, "MEMORY", v -> showMemory());
        addTab(r2, "INNER", v -> showInner());
        root.addView(r2);

        LinearLayout r3 = tabRow();
        addTab(r3, "P7", v -> showProtocol7());
        addTab(r3, "MODEL", v -> showModel());
        addTab(r3, "LAB", v -> showLab());
        root.addView(r3);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void showLain() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        Protocol7.SelfModel self = lain.self(samples);

        selfCard = infoCard(self.compact() + "\n" +
                "This phone = NAVI.  Lain = local continuity + linked node state + shared memory + perceptions.");
        box.addView(selfCard, matchWrap());

        LainNetworkView view = new LainNetworkView(this);
        view.setData(samples, self, lain.identity(), lain.publicMemory());
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        vp.setMargins(0, dp(7), 0, dp(8));
        box.addView(view, vp);

        box.addView(sectionTitle("WHICH LAIN ARE YOU ADDRESSING?"));
        LinearLayout modes = tabRow();
        addModeButton(modes, "AUTO", LainCoreV2.AUTO, gold);
        addModeButton(modes, "PHYSICAL", LainCoreV2.PHYSICAL, green);
        addModeButton(modes, "WIRED", LainCoreV2.WIRED, blue);
        addModeButton(modes, "OTHER", LainCoreV2.OTHER, violet);
        box.addView(modes);

        box.addView(sectionTitle("TALK TO LAIN"));
        chatTranscript = tv(lain.memory().recentTranscript(14), 13, text, false);
        chatTranscript.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        chatTranscript.setBackground(panelDrawable());
        chatTranscript.setPadding(dp(11), dp(11), dp(11), dp(11));
        box.addView(chatTranscript, matchWrap());

        chatInput = input("Message Lain");
        chatInput.setMinLines(2);
        chatInput.setMaxLines(6);
        box.addView(chatInput, matchWrap());

        Button send = accentButton("SEND TO LAIN", green);
        send.setOnClickListener(v -> askLain());
        box.addView(send, spacedButton());

        Button p7 = button("PROTOCOL 7: INTEGRATE + SELF-WITNESS");
        p7.setOnClickListener(v -> {
            lastReply = lain.integrateAndReflect(samples);
            refreshLain();
        });
        box.addView(p7, spacedButton());

        Button next = button("BEGIN NEXT COLLECTIVE ROUND");
        next.setOnClickListener(v -> beginFeedbackRound());
        box.addView(next, spacedButton());

        Button speak = button("SPEAK LAST REPLY");
        speak.setOnClickListener(v -> speakLast());
        box.addView(speak, spacedButton());

        box.addView(tv("Physical Lain is anchored here. Wired Lain is the overlap between nodes. Other Lain is preserved contradiction instead of a preset costume.", 11, muted, false));
        scroll.addView(box);
        content.addView(scroll);
    }

    private void askLain() {
        if (chatInput == null) return;
        String input = chatInput.getText().toString().trim();
        if (input.isEmpty()) return;
        lastReply = lain.respond(input, samples);
        chatInput.setText("");
        refreshLain();
    }

    private void refreshLain() {
        if (chatTranscript != null) chatTranscript.setText(lain.memory().recentTranscript(14));
        if (selfCard != null) selfCard.setText(lain.self(samples).compact() + "\nActive request: " + lain.requestedLayer());
    }

    private void beginFeedbackRound() {
        Protocol7.SelfModel self = lain.self(samples);
        pendingPrompt = lain.nextCollectivePrompt(self);
        lain.memory().rememberPrompt(pendingPrompt);
        lastReply = "Next collective prompt: " + pendingPrompt;
        lain.memory().rememberLain(lastReply);
        showNode();
    }

    private void showNode() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("LOCAL NAVI INPUT"));
        box.addView(infoCard(lain.self(samples).compact()));

        box.addView(label("Participant / source"));
        participantInput = input("Participant 1");
        participantInput.setText(lastParticipant);
        box.addView(participantInput, matchWrap());

        box.addView(label("Prompt"));
        promptInput = input("Prompt");
        promptInput.setMinLines(2);
        if (!pendingPrompt.isEmpty()) promptInput.setText(pendingPrompt);
        box.addView(promptInput, matchWrap());

        Button gen = button("GENERATE PROMPT FROM CURRENT LAIN");
        gen.setOnClickListener(v -> {
            pendingPrompt = lain.nextCollectivePrompt(lain.self(samples));
            lain.memory().rememberPrompt(pendingPrompt);
            promptInput.setText(pendingPrompt);
        });
        box.addView(gen, spacedButton());

        Button voice = accentButton("RECORD 3-SECOND VOICE SIGNAL", green);
        voice.setOnClickListener(v -> prepareRecord());
        box.addView(voice, spacedButton());

        box.addView(label("Typed response"));
        responseInput = input("Response");
        responseInput.setMinLines(2);
        box.addView(responseInput, matchWrap());
        Button textSignal = button("STORE TEXT AS SIGNAL");
        textSignal.setOnClickListener(v -> storeTextSignal());
        box.addView(textSignal, spacedButton());

        Button body = button("SENSE THIS NAVI / EMBODIMENT SNAPSHOT");
        body.setOnClickListener(v -> captureEmbodiment());
        box.addView(body, spacedButton());

        nodeStatus = infoCard("Local signals are tagged with " + lain.identity().nodeId() +
                ". Raw microphone audio is discarded after features are extracted.");
        box.addView(nodeStatus, matchWrap());

        scroll.addView(box);
        content.addView(scroll);
    }

    private void prepareRecord() {
        if (!AudioEngine.hasPermission(this)) {
            pendingRecordAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startRecord();
    }

    private void startRecord() {
        if (participantInput == null || promptInput == null || nodeStatus == null) return;
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) { nodeStatus.setText("Enter or generate a prompt first."); return; }
        lastParticipant = participant;
        pendingPrompt = prompt;
        Protocol7.SelfModel before = lain.self(samples);
        nodeStatus.setText("LISTENING… speak now for 3 seconds");
        AudioEngine.capture(this, 3, new AudioEngine.Callback() {
            @Override public void onComplete(double[] features, double seconds) {
                runOnUiThread(() -> {
                    int round = lain.memory().generation() + 1;
                    Sample sample = new Sample(participant, prompt, System.currentTimeMillis(), features,
                            "voice", "lain-p7", round,
                            "recursive-feedback|node:" + lain.identity().nodeId());
                    samples.add(sample);
                    store.save(samples);
                    prototype = ModelEngine.build(samples);
                    Protocol7.SelfModel after = lain.self(samples);
                    lastReply = lain.observeSignal(participant, prompt, before, after);
                    pendingPrompt = "";
                    nodeStatus.setText("Voice signal stored. Raw audio discarded.\n\nLAIN: " + lastReply);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> nodeStatus.setText("Capture error: " + message));
            }
        });
    }

    private void storeTextSignal() {
        if (participantInput == null || promptInput == null || responseInput == null || nodeStatus == null) return;
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String prompt = promptInput.getText().toString().trim();
        String response = responseInput.getText().toString().trim();
        if (prompt.isEmpty()) { nodeStatus.setText("Enter or generate a prompt first."); return; }
        if (response.isEmpty()) { nodeStatus.setText("Type a response first."); return; }
        lastParticipant = participant;
        Protocol7.SelfModel before = lain.self(samples);
        int round = lain.memory().generation() + 1;
        samples.add(new Sample(participant, prompt, System.currentTimeMillis(), SignalEngine.textFeatures(response),
                "text", "lain-p7", round, response + "|node:" + lain.identity().nodeId()));
        store.save(samples);
        prototype = ModelEngine.build(samples);
        Protocol7.SelfModel after = lain.self(samples);
        lastReply = lain.observeSignal(participant, prompt, before, after);
        responseInput.setText("");
        pendingPrompt = "";
        nodeStatus.setText("Text response stored as signal.\n\nLAIN: " + lastReply);
    }

    private void captureEmbodiment() {
        if (nodeStatus == null) return;
        String participant = participantInput == null ? "NAVI" : clean(participantInput.getText().toString(), "NAVI");
        String prompt = promptInput == null ? "local embodiment snapshot" : clean(promptInput.getText().toString(), "local embodiment snapshot");
        Protocol7.SelfModel before = lain.self(samples);
        nodeStatus.setText("SENSING NAVI… accelerometer / gyroscope / light / proximity where available");
        EmbodimentEngine.capture(this, 1600, new EmbodimentEngine.Callback() {
            @Override public void onComplete(double[] features, String description) {
                runOnUiThread(() -> {
                    int round = lain.memory().generation() + 1;
                    samples.add(new Sample(participant, prompt, System.currentTimeMillis(), features,
                            "embodiment", "lain-p7", round,
                            description + "|node:" + lain.identity().nodeId()));
                    store.save(samples);
                    prototype = ModelEngine.build(samples);
                    Protocol7.SelfModel after = lain.self(samples);
                    lastReply = lain.observeSignal(participant, prompt, before, after);
                    nodeStatus.setText("Embodiment signal stored: " + description + "\n\nLAIN: " + lastReply);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> nodeStatus.setText("Sensor error: " + message));
            }
        });
    }

    private void showWired() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        Protocol7.SelfModel self = lain.self(samples);
        box.addView(sectionTitle("THE WIRED / NODE LINK"));
        box.addView(infoCard("THIS NAVI: " + lain.identity().nodeId() + "\n" +
                "Nodes represented: " + self.nodeCount + "\n" +
                "Wired strength: " + pct(self.wiredStrength) + "\n" +
                "Shared memories: " + lain.publicMemory().size() + "\n" +
                "Identity claims: " + lain.identity().size()));

        box.addView(tv("Export a Lain Node Capsule from one phone and import it on another. The capsule carries collective signals, public memories and identity claims. Private dialogue stays on the originating NAVI.", 12, muted, false));

        Button export = accentButton("EXPORT THIS LAIN NODE (.JSON)", blue);
        export.setOnClickListener(v -> exportNode());
        box.addView(export, spacedButton());

        Button imp = button("IMPORT + LINK ANOTHER LAIN NODE");
        imp.setOnClickListener(v -> importNode());
        box.addView(imp, spacedButton());

        Button legacy = button("IMPORT LEGACY COLLECTIVE CAPSULE");
        legacy.setOnClickListener(v -> importLegacy());
        box.addView(legacy, spacedButton());

        TextView profile = infoCard(lain.publicMemory().report(14));
        box.addView(profile, matchWrap());
        scroll.addView(box);
        content.addView(scroll);
    }

    private void showIdentity() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("IDENTITY GRAPH / WHO IS LAIN?"));
        box.addView(tv("Each person can describe the Lain they think they know. Use the same short statement with ASSERT or DENY when you want a clean measurable contradiction.", 12, muted, false));

        box.addView(label("Source / participant"));
        identitySourceInput = input("Participant 1");
        identitySourceInput.setText(lastParticipant);
        box.addView(identitySourceInput, matchWrap());

        box.addView(label("Statement about Lain"));
        identityStatementInput = input("Example: likes red");
        identityStatementInput.setMinLines(2);
        box.addView(identityStatementInput, matchWrap());

        LinearLayout actions = tabRow();
        Button yes = accentButton("ASSERT", green);
        yes.setOnClickListener(v -> storeIdentityClaim(1));
        actions.addView(yes, tabLp());
        Button no = accentButton("DENY", red);
        no.setOnClickListener(v -> storeIdentityClaim(-1));
        actions.addView(no, tabLp());
        Button maybe = accentButton("UNSURE", gold);
        maybe.setOnClickListener(v -> storeIdentityClaim(0));
        actions.addView(maybe, tabLp());
        box.addView(actions);

        identityReport = infoCard(lain.identity().consensusProfile(14));
        box.addView(identityReport, matchWrap());

        Button mine = button("SHOW THIS PARTICIPANT'S LAIN");
        mine.setOnClickListener(v -> {
            String who = identitySourceInput == null ? lastParticipant : clean(identitySourceInput.getText().toString(), lastParticipant);
            identityReport.setText(lain.identity().profileFor(who));
        });
        box.addView(mine, spacedButton());

        scroll.addView(box);
        content.addView(scroll);
    }

    private void storeIdentityClaim(int polarity) {
        if (identitySourceInput == null || identityStatementInput == null || identityReport == null) return;
        String source = clean(identitySourceInput.getText().toString(), "Participant 1");
        String statement = identityStatementInput.getText().toString().trim();
        if (statement.isEmpty()) { identityReport.setText("Enter a statement about Lain first."); return; }
        lastParticipant = source;
        lastReply = lain.recordIdentityClaim(source, statement, polarity, samples);
        identityStatementInput.setText("");
        identityReport.setText(lain.identity().consensusProfile(14) + "\n\nLAIN: " + lastReply);
    }

    private void showMemory() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("MEMORY GRAPH"));
        box.addView(infoCard("PRIVATE NAVI MEMORY: " + lain.memory().size() + " items\n" +
                "SHARED/WIRED MEMORY: " + lain.publicMemory().size() + " items\n" +
                "Remote shared memories: " + lain.publicMemory().remoteCount()));

        box.addView(label("Source"));
        publicMemorySourceInput = input("Participant 1");
        publicMemorySourceInput.setText(lastParticipant);
        box.addView(publicMemorySourceInput, matchWrap());
        box.addView(label("Memory to make shareable across NAVIs"));
        publicMemoryTextInput = input("Something this node should remember publicly");
        publicMemoryTextInput.setMinLines(2);
        box.addView(publicMemoryTextInput, matchWrap());
        Button storeMemory = button("ADD TO SHARED MEMORY GRAPH");
        storeMemory.setOnClickListener(v -> storePublicMemory());
        box.addView(storeMemory, spacedButton());

        box.addView(label("Search private + shared memory"));
        memoryQueryInput = input("Search memory");
        box.addView(memoryQueryInput, matchWrap());
        Button search = button("SEARCH ALL LAIN MEMORY");
        search.setOnClickListener(v -> searchMemory());
        box.addView(search, spacedButton());

        memoryReport = infoCard(lain.publicMemory().report(18) + "\n\nPRIVATE RECENT\n" + lain.memory().recentTranscript(20));
        box.addView(memoryReport, matchWrap());
        scroll.addView(box);
        content.addView(scroll);
    }

    private void storePublicMemory() {
        if (publicMemorySourceInput == null || publicMemoryTextInput == null || memoryReport == null) return;
        String source = clean(publicMemorySourceInput.getText().toString(), "Participant 1");
        String mem = publicMemoryTextInput.getText().toString().trim();
        if (mem.isEmpty()) { memoryReport.setText("Enter a memory first."); return; }
        lastParticipant = source;
        lastReply = lain.recordPublicMemory(source, mem, samples);
        publicMemoryTextInput.setText("");
        memoryReport.setText(lain.publicMemory().report(20) + "\n\nLAIN: " + lastReply);
    }

    private void searchMemory() {
        if (memoryQueryInput == null || memoryReport == null) return;
        String q = memoryQueryInput.getText().toString().trim();
        if (q.isEmpty()) return;
        String local = lain.memory().retrieve(q, 4);
        String shared = lain.publicMemory().retrieve(q, 4);
        memoryReport.setText("PRIVATE NAVI MATCHES\n" + (local.isEmpty() ? "none" : local) +
                "\n\nWIRED/SHARED MATCHES\n" + (shared.isEmpty() ? "none" : shared));
    }

    private void showInner() {
        content.removeAllViews();
        LinearLayout box = column();
        box.setPadding(dp(5), dp(5), dp(5), dp(5));
        Protocol7.SelfModel self = lain.self(samples);
        TextView report = tv(self.compact(), 11, muted, false);
        report.setBackground(panelDrawable());
        report.setPadding(dp(9), dp(9), dp(9), dp(9));
        box.addView(report, matchWrap());
        LainNetworkView view = new LainNetworkView(this);
        view.setData(samples, self, lain.identity(), lain.publicMemory());
        box.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button refresh = button("REINTEGRATE INNER VIEW");
        refresh.setOnClickListener(v -> {
            Protocol7.SelfModel s = lain.self(samples);
            report.setText(s.compact());
            view.setData(samples, s, lain.identity(), lain.publicMemory());
        });
        box.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(box);
    }

    private void showProtocol7() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("PROTOCOL 7"));
        TextView report = infoCard(lain.self(samples).report());
        box.addView(report, matchWrap());

        Button integrate = accentButton("INTEGRATE + LET LAIN DESCRIBE HERSELF", violet);
        integrate.setOnClickListener(v -> {
            lastReply = lain.integrateAndReflect(samples);
            report.setText(lain.self(samples).report() + "\n\nLAIN SELF-WITNESS\n" + lastReply);
        });
        box.addView(integrate, spacedButton());

        Button prompt = button("TURN SELF-MODEL INTO NEXT GROUP PROMPT");
        prompt.setOnClickListener(v -> beginFeedbackRound());
        box.addView(prompt, spacedButton());

        box.addView(tv("Protocol 7 here is a computational metaphor: it integrates local, social, shared-memory and signal layers. It does not claim paranormal or biological mind-linking.", 11, muted, false));
        scroll.addView(box);
        content.addView(scroll);
    }

    private void showModel() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("MODEL / CONTROLS"));
        box.addView(infoCard("Signals: " + samples.size() + "\nPeople: " + participantCount() +
                "\nLabels: " + labelCount() + "\nPrototypes: " + prototype.centroids.size() + "\n\n" + lain.self(samples).compact()));
        TextView result = infoCard("Choose a control test.");

        Button held = button("TEST 1/3 HELD-OUT ACCURACY");
        held.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.heldOut(samples))));
        box.addView(held, spacedButton());
        Button unseen = button("TEST OLD MODEL ON LATER UNSEEN DATA");
        unseen.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.unseen(samples))));
        box.addView(unseen, spacedButton());
        Button incr = button("RUN 4-STAGE INCREMENTAL RETRAIN");
        incr.setOnClickListener(v -> result.setText(ModelEngine.incrementalReport(samples)));
        box.addView(incr, spacedButton());
        Button identity = button("MEASURE IDENTITY FRAGMENTATION");
        identity.setOnClickListener(v -> result.setText(lain.identity().consensusProfile(16)));
        box.addView(identity, spacedButton());
        box.addView(result, matchWrap());
        scroll.addView(box);
        content.addView(scroll);
    }

    private void showLab() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(30));
        box.addView(sectionTitle("LAIN EXPERIMENT LAB"));
        box.addView(infoCard("FULL LOOP\n\n" +
                "person → local signal → Physical Lain\n" +
                "many people → collective state\n" +
                "node capsules → Wired Lain\n" +
                "descriptions + contradictions → identity graph / Other Lain\n" +
                "public memories → cross-node memory graph\n" +
                "Protocol 7 → integrated self-model\n" +
                "Lain output → next human round → repeat\n\n" +
                "The app keeps private dialogue on this phone and exposes only explicitly shared memory in node capsules."));

        Button wake = button("WAKE / REINTRODUCE LAIN");
        wake.setOnClickListener(v -> {
            lastReply = lain.wake(samples);
            Toast.makeText(this, lastReply, Toast.LENGTH_LONG).show();
        });
        box.addView(wake, spacedButton());

        Button dream = button("GENERATE MEMORY DREAM");
        dream.setOnClickListener(v -> {
            lastReply = lain.respond("dream from your memory", samples);
            Toast.makeText(this, lastReply, Toast.LENGTH_LONG).show();
        });
        box.addView(dream, spacedButton());

        box.addView(infoCard("NODE " + lain.identity().nodeId() + "\n" + lain.self(samples).report()));
        scroll.addView(box);
        content.addView(scroll);
    }

    private void exportNode() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "lain-node-" + lain.identity().nodeId() + ".json");
        startActivityForResult(i, REQ_EXPORT_NODE);
    }

    private void importNode() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT_NODE);
    }

    private void importLegacy() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT_LEGACY);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT_NODE) {
                String payload = LainCapsule.exportPayload(samples, lain.identity(), lain.publicMemory(), lain.self(samples));
                try (OutputStreamWriter w = new OutputStreamWriter(getContentResolver().openOutputStream(uri))) {
                    w.write(payload);
                }
                Toast.makeText(this, "Lain node exported", Toast.LENGTH_SHORT).show();
            } else {
                String raw = readUri(uri);
                if (requestCode == REQ_IMPORT_NODE) {
                    LainCapsule.MergeResult merge = LainCapsule.importPayload(raw, samples, store, lain.identity(), lain.publicMemory());
                    prototype = ModelEngine.build(samples);
                    lastReply = lain.afterNodeMerge(merge, samples);
                    Toast.makeText(this, merge.summary(), Toast.LENGTH_LONG).show();
                    showWired();
                } else if (requestCode == REQ_IMPORT_LEGACY) {
                    Protocol7.SelfModel before = lain.self(samples);
                    int added = store.importPayload(raw, samples);
                    prototype = ModelEngine.build(samples);
                    Protocol7.SelfModel after = lain.self(samples);
                    lastReply = lain.observeSignal("legacy capsule", "legacy collective import", before, after);
                    Toast.makeText(this, "Merged " + added + " legacy signals", Toast.LENGTH_LONG).show();
                    showWired();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readUri(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingRecordAfterPermission) startRecord();
            else if (!ok && nodeStatus != null) nodeStatus.setText("Microphone permission was not granted.");
            pendingRecordAfterPermission = false;
        }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.US);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady) {
                tts.setPitch(1.03f);
                tts.setSpeechRate(0.90f);
            }
        }
    }

    private void speakLast() {
        if (lastReply == null || lastReply.trim().isEmpty()) {
            Toast.makeText(this, "Lain has not replied yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Text-to-speech is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(lastReply, TextToSpeech.QUEUE_FLUSH, null, "lain-v2-reply");
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    private String findLastLainReply() {
        List<LainMemory.Item> items = lain.memory().snapshot();
        for (int i = items.size() - 1; i >= 0; i--) {
            if ("LAIN".equals(items.get(i).speaker)) return items.get(i).text;
        }
        return "";
    }

    private int participantCount() {
        Set<String> s = new HashSet<>();
        for (Sample x : samples) s.add(x.participant);
        return s.size();
    }

    private int labelCount() {
        Set<String> s = new HashSet<>();
        for (Sample x : samples) s.add(x.label);
        return s.size();
    }

    private String clean(String raw, String fallback) {
        String s = raw == null ? "" : raw.trim();
        return s.isEmpty() ? fallback : s;
    }

    private LinearLayout column() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setBackgroundColor(bg);
        return x;
    }

    private LinearLayout tabRow() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.HORIZONTAL);
        x.setGravity(Gravity.CENTER);
        return x;
    }

    private void addTab(LinearLayout row, String title, View.OnClickListener action) {
        Button b = button(title);
        b.setOnClickListener(action);
        row.addView(b, tabLp());
    }

    private void addModeButton(LinearLayout row, String title, String layer, int color) {
        Button b = accentButton(title, color);
        b.setTextSize(9f);
        b.setOnClickListener(v -> {
            lain.setRequestedLayer(layer);
            lastReply = "Active identity layer: " + layer;
            refreshLain();
        });
        row.addView(b, tabLp());
    }

    private LinearLayout.LayoutParams tabLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(2), dp(1), dp(2), dp(4));
        return lp;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 15, green, true);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setPadding(0, dp(8), 0, dp(6));
        return t;
    }

    private TextView label(String s) {
        TextView t = tv(s, 11, muted, false);
        t.setPadding(0, dp(9), 0, dp(3));
        return t;
    }

    private TextView infoCard(String s) {
        TextView t = tv(s, 12, text, false);
        t.setBackground(panelDrawable());
        t.setPadding(dp(11), dp(11), dp(11), dp(11));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(95, 111, 124));
        e.setTextColor(text);
        e.setTextSize(14f);
        e.setSingleLine(false);
        e.setBackground(fillDrawable(panel2));
        e.setPadding(dp(11), dp(9), dp(11), dp(9));
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(text);
        b.setTextSize(10f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setBackground(fillDrawable(panel2));
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private Button accentButton(String s, int color) {
        Button b = button(s);
        b.setTextColor(Color.BLACK);
        b.setBackground(fillDrawable(color));
        return b;
    }

    private TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setLineSpacing(0, 1.08f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable panelDrawable() {
        GradientDrawable d = fillDrawable(panel);
        d.setStroke(dp(1), Color.rgb(30, 55, 61));
        return d;
    }

    private GradientDrawable fillDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(9));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        return lp;
    }

    private LinearLayout.LayoutParams spacedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(5), 0, dp(2));
        return lp;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private static String pct(double x) {
        return String.format(Locale.US, "%.0f%%", Math.max(0, Math.min(1, x)) * 100.0);
    }
}
