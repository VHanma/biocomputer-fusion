package com.vaan.collectivemachine;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQ_MIC = 40;
    private static final int REQ_EXPORT = 501;
    private static final int REQ_IMPORT = 502;

    private static final String[] PROMPTS = {
            "Haruto Sato", "Yuto Suzuki", "Sota Takahashi", "Yuki Tanaka",
            "Hayato Watanabe", "Haruki Ito", "Kaito Yamamoto", "Ryota Nakamura",
            "Daiki Kobayashi", "Takumi Kato", "Riku Yoshida", "Kenta Yamada",
            "Shota Sasaki", "Ren Yamaguchi", "Tsubasa Matsumoto", "Naoki Inoue",
            "Yuma Kimura", "Hiroto Hayashi", "Keita Shimizu", "Sho Yamazaki",
            "Akira Mori", "Toma Abe", "Kohei Ikeda", "Ryo Hashimoto",
            "Yui Sato", "Aoi Suzuki", "Hina Takahashi", "Rin Tanaka",
            "Sakura Watanabe", "Mio Ito", "Yuna Yamamoto", "Mei Nakamura",
            "Akari Kobayashi", "Nanami Kato", "Riko Yoshida", "Miku Yamada",
            "Ayaka Sasaki", "Noa Yamaguchi", "Koharu Matsumoto", "Nana Inoue",
            "Honoka Kimura", "Mao Hayashi", "Rina Shimizu", "Misaki Yamazaki",
            "Emi Mori", "Kana Abe", "Yuri Ikeda", "Reina Hashimoto",
            "Shin Sato", "Jun Suzuki", "Ken Takahashi", "Kai Tanaka",
            "Toru Watanabe", "Rei Ito", "Haru Yamamoto", "Rui Nakamura",
            "Nao Kobayashi", "Aki Kato", "Maki Yoshida", "Aya Yamada",
            "Sora Sasaki", "Hikari Yamaguchi", "Kaori Matsumoto", "Minato Inoue"
    };

    private final int bg = Color.rgb(5, 7, 18);
    private final int panel = Color.rgb(15, 19, 38);
    private final int text = Color.rgb(235, 238, 248);
    private final int muted = Color.rgb(155, 167, 196);
    private final int gold = Color.rgb(230, 210, 93);
    private final int green = Color.rgb(69, 235, 164);
    private final int blue = Color.rgb(112, 142, 255);

    private Store store;
    private final List<Sample> samples = new ArrayList<>();
    private ModelEngine.Prototype prototype;
    private FrameLayout content;

    private String activeSession = "SESSION-1";
    private int activeRound = 0;
    private String lastParticipant = "Participant 1";

    private EditText participantInput;
    private EditText sessionInput;
    private EditText roundInput;
    private EditText labelInput;
    private EditText tagInput;
    private EditText typedInput;
    private TextView collectStatus;
    private TextView collectStats;
    private DrawingPad drawingPad;

    private final List<Long> tapTimes = new ArrayList<>();
    private Button tapButton;
    private Button reactionButton;
    private long reactionGoTime = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<String> fiveRound = new ArrayList<>();
    private int fiveIndex = -1;
    private boolean pendingVoiceAfterPermission = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        samples.addAll(store.load());
        prototype = ModelEngine.build(samples);
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        activeSession = p.getString("session", "SESSION-1");
        activeRound = p.getInt("round", 0);
        lastParticipant = p.getString("participant", "Participant 1");
        buildShell();
        showCollect();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = tv("COLLECTIVE MACHINE EXPERIMENT", 21, gold, true);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        root.addView(title);
        TextView sub = tv("signals → personal baselines → synchronized rounds → collective core → feedback → repeat", 11, muted, false);
        sub.setPadding(0, dp(3), 0, dp(9));
        root.addView(sub);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        String[] names = {"COLLECT", "SYNC", "CORE", "LOOP", "LAB"};
        View.OnClickListener[] actions = {
                v -> showCollect(), v -> showSync(), v -> showCore(), v -> showLoop(), v -> showLab()
        };
        for (int i = 0; i < names.length; i++) {
            Button b = button(names[i]);
            b.setTextSize(10f);
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setOnClickListener(actions[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(2), 0, dp(2), dp(7));
            tabs.addView(b, lp);
        }
        root.addView(tabs);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void showCollect() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(11), dp(10), dp(11), dp(40));
        box.addView(sectionTitle("MULTIMODAL SIGNAL COLLECTION"));

        collectStats = tv(statsText(), 13, text, false);
        collectStats.setBackground(panelDrawable());
        collectStats.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.addView(collectStats, matchWrap());

        box.addView(label("Participant"));
        participantInput = input(lastParticipant);
        box.addView(participantInput, matchWrap());

        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = column();
        left.addView(label("Session code"));
        sessionInput = input(activeSession);
        left.addView(sessionInput, matchWrap());
        LinearLayout right = column();
        right.addView(label("Round"));
        roundInput = input(String.valueOf(activeRound));
        roundInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        right.addView(roundInput, matchWrap());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0,0,dp(5),0);
        sr.addView(left, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f);
        sr.addView(right, half2);
        box.addView(sr, matchWrap());

        box.addView(label("Prompt / repeated class label"));
        labelInput = input("");
        labelInput.setHint("repeat the same label across rounds for prediction tests");
        box.addView(labelInput, matchWrap());

        box.addView(label("Intent / state tag (optional)"));
        tagInput = input("");
        tagInput.setHint("focused, calm, image A, target 1, etc.");
        box.addView(tagInput, matchWrap());

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.HORIZONTAL);
        Button five = button("5-NAME ROUND");
        five.setOnClickListener(v -> startFiveRound());
        Button nextPrompt = button("NEXT PROMPT");
        nextPrompt.setOnClickListener(v -> advanceFiveRound());
        names.addView(five, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(46), 1f); np.setMargins(dp(5),0,0,0);
        names.addView(nextPrompt, np);
        box.addView(names, spacedButton());

        Button voice = button("VOICE 3 SEC → BARK SIGNAL");
        voice.setTextColor(Color.BLACK);
        voice.setBackground(fillDrawable(green));
        voice.setOnClickListener(v -> prepareVoice());
        box.addView(voice, spacedButton());

        tapButton = button("TAP RHYTHM: TAP 8 TIMES");
        tapButton.setOnClickListener(v -> handleTap());
        box.addView(tapButton, spacedButton());

        reactionButton = button("REACTION SIGNAL: START");
        reactionButton.setOnClickListener(v -> handleReaction());
        box.addView(reactionButton, spacedButton());

        Button motion = button("MOTION SIGNAL: ACCELEROMETER 3 SEC");
        motion.setOnClickListener(v -> captureMotion());
        box.addView(motion, spacedButton());

        box.addView(label("Typed response signal"));
        typedInput = input("");
        typedInput.setHint("type a word, thought, choice, description, or answer");
        box.addView(typedInput, matchWrap());
        Button saveText = button("SAVE TYPED SIGNAL");
        saveText.setOnClickListener(v -> saveTyped());
        box.addView(saveText, spacedButton());

        box.addView(sectionTitle("DRAWING SIGNAL"));
        box.addView(tv("Draw a symbol, shape, line, or spontaneous mark. The path itself becomes a 50-value signal.", 12, muted, false));
        drawingPad = new DrawingPad(this);
        box.addView(drawingPad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        LinearLayout drawBtns = new LinearLayout(this); drawBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button saveDraw = button("SAVE DRAWING"); saveDraw.setOnClickListener(v -> saveDrawing());
        Button clearDraw = button("CLEAR"); clearDraw.setOnClickListener(v -> drawingPad.clear());
        drawBtns.addView(saveDraw, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(0, dp(46), 0.55f); cdp.setMargins(dp(5),0,0,0); drawBtns.addView(clearDraw, cdp);
        box.addView(drawBtns, spacedButton());

        collectStatus = tv("Use the same session and round for people participating in one synchronized trial.", 13, muted, false);
        collectStatus.setPadding(0, dp(12), 0, dp(12));
        box.addView(collectStatus);

        Button nextRound = button("FINISH ROUND → NEXT ROUND");
        nextRound.setOnClickListener(v -> { syncContextFromInputs(); activeRound++; saveContext(); showCollect(); });
        box.addView(nextRound, spacedButton());

        box.addView(infoCard("Every modality is compressed into 50 normalized values. Earlier samples from the same person + modality become that person's baseline. The collective core is built from changes relative to those baselines, not only raw averages."));
        scroll.addView(box);
        content.addView(scroll);
        if (fiveIndex >= 0 && fiveIndex < fiveRound.size()) labelInput.setText(fiveRound.get(fiveIndex));
    }

    private void prepareVoice() {
        syncContextFromInputs();
        if (!AudioEngine.hasPermission(this)) {
            pendingVoiceAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startVoice();
    }

    private void startVoice() {
        final SignalContext ctx = readSignalContext("voice");
        if (ctx == null) return;
        collectStatus.setText("LISTENING… 3 seconds");
        AudioEngine.capture(this, 3, new AudioEngine.Callback() {
            @Override public void onComplete(double[] features, double seconds) {
                runOnUiThread(() -> {
                    addSignal(ctx, SignalEngine.fit(features));
                    collectStatus.setText("Voice captured: " + String.format(java.util.Locale.US, "%.1fs", seconds) + ". Raw audio discarded after features.");
                    if (!fiveRound.isEmpty()) advanceFiveRound();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> collectStatus.setText("Voice error: " + message));
            }
        });
    }

    private void handleTap() {
        syncContextFromInputs();
        long now = System.currentTimeMillis();
        if (tapTimes.isEmpty()) {
            tapTimes.add(now);
            tapButton.setText("TAP 2 / 8");
            collectStatus.setText("Tap naturally. Seven more taps.");
            return;
        }
        tapTimes.add(now);
        int count = tapTimes.size();
        if (count < 8) {
            tapButton.setText("TAP " + (count + 1) + " / 8");
        } else {
            SignalContext ctx = readSignalContext("tap");
            if (ctx != null) addSignal(ctx, SignalEngine.tapFeatures(tapTimes));
            tapTimes.clear();
            tapButton.setText("TAP RHYTHM: TAP 8 TIMES");
            collectStatus.setText("Tap rhythm stored as a timing signal.");
        }
    }

    private void handleReaction() {
        syncContextFromInputs();
        if (reactionGoTime > 0) {
            long ms = System.currentTimeMillis() - reactionGoTime;
            SignalContext ctx = readSignalContext("reaction");
            if (ctx != null) addSignal(ctx, SignalEngine.reactionFeatures(ms));
            reactionGoTime = 0;
            reactionButton.setText("REACTION SIGNAL: START");
            collectStatus.setText("Reaction stored: " + ms + " ms.");
            return;
        }
        reactionButton.setEnabled(false);
        reactionButton.setText("WAIT…");
        collectStatus.setText("Wait for GO. Then tap the same button.");
        long delay = 1100L + new Random().nextInt(2200);
        handler.postDelayed(() -> {
            reactionGoTime = System.currentTimeMillis();
            reactionButton.setEnabled(true);
            reactionButton.setText("GO! TAP NOW");
        }, delay);
    }

    private void captureMotion() {
        syncContextFromInputs();
        final SignalContext ctx = readSignalContext("motion");
        if (ctx == null) return;
        collectStatus.setText("CAPTURING MOTION… move the phone for 3 seconds");
        SensorEngine.captureMotion(this, 3000, new SensorEngine.Callback() {
            @Override public void onComplete(double[] features, int points) {
                runOnUiThread(() -> {
                    addSignal(ctx, features);
                    collectStatus.setText("Motion signal stored from " + points + " accelerometer points.");
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> collectStatus.setText("Motion error: " + message));
            }
        });
    }

    private void saveTyped() {
        syncContextFromInputs();
        String raw = typedInput.getText().toString().trim();
        if (raw.isEmpty()) { collectStatus.setText("Type a response first."); return; }
        SignalContext ctx = readSignalContext("text");
        if (ctx == null) return;
        addSignal(ctx, SignalEngine.textFeatures(raw));
        typedInput.setText("");
        collectStatus.setText("Typed response transformed into a local feature signal and stored.");
    }

    private void saveDrawing() {
        syncContextFromInputs();
        if (!drawingPad.hasSignal()) { collectStatus.setText("Draw a longer path first."); return; }
        SignalContext ctx = readSignalContext("drawing");
        if (ctx == null) return;
        addSignal(ctx, drawingPad.extractFeatures());
        drawingPad.clear();
        collectStatus.setText("Drawing path stored as a normalized signal.");
    }

    private void showSync() {
        syncContextFromInputsIfPresent();
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(11), dp(10), dp(11), dp(35));
        box.addView(sectionTitle("SYNCHRONIZED ROUND"));
        CollectiveEngine.State state = CollectiveEngine.stateForRound(samples, activeSession, activeRound);
        TextView summary = tv(CollectiveEngine.stateSummary(state), 13, text, false);
        summary.setBackground(panelDrawable()); summary.setPadding(dp(11),dp(11),dp(11),dp(11));
        box.addView(summary, matchWrap());
        box.addView(infoCard("Protocol: give every participant the same session code and round number. Collect one or more signals from each person. Earlier rounds establish personal baselines. Current-round residuals are combined into the collective core."));

        Button baseline = button("SHOW PERSONAL BASELINES");
        TextView result = tv("", 12, text, false); result.setPadding(0,dp(8),0,dp(8));
        baseline.setOnClickListener(v -> result.setText(CollectiveEngine.baselineReport(samples, activeSession)));
        box.addView(baseline, spacedButton());
        Button control = button("RUN SHUFFLED-ROUND CONTROL");
        control.setOnClickListener(v -> result.setText(CollectiveEngine.shuffledControlReport(samples, activeSession)));
        box.addView(control, spacedButton());
        Button predict = button("COLLECTIVE VS PERSONAL PREDICTION");
        predict.setOnClickListener(v -> result.setText(CollectiveEngine.collectiveVsPersonalPrediction(samples, activeSession)));
        box.addView(predict, spacedButton());
        box.addView(result, matchWrap());

        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = button("← ROUND"); prev.setOnClickListener(v -> { if (activeRound > 0) activeRound--; saveContext(); showSync(); });
        Button next = button("ROUND →"); next.setOnClickListener(v -> { activeRound++; saveContext(); showSync(); });
        nav.addView(prev, new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,dp(46),1f); nlp.setMargins(dp(5),0,0,0); nav.addView(next,nlp);
        box.addView(nav, spacedButton());
        Button collect = button("COLLECT SIGNALS FOR THIS ROUND"); collect.setOnClickListener(v -> showCollect()); box.addView(collect, spacedButton());
        scroll.addView(box); content.addView(scroll);
    }

    private void showCore() {
        syncContextFromInputsIfPresent();
        content.removeAllViews();
        LinearLayout box = column(); box.setPadding(dp(7),dp(7),dp(7),dp(7));
        CollectiveEngine.State state = CollectiveEngine.stateForRound(samples, activeSession, activeRound);
        TextView top = tv("REAL-DATA INNER VIEW\nGreen core shape = current metrics. Node color = modality. Node size = participant influence.", 11, muted, false);
        box.addView(top);
        CollectiveView view = new CollectiveView(this);
        view.setData(samples, state);
        box.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        Button refresh = button("REBUILD ROUND CORE");
        refresh.setOnClickListener(v -> { prototype = ModelEngine.build(samples); view.setData(samples, CollectiveEngine.stateForRound(samples, activeSession, activeRound)); });
        box.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(47)));
        content.addView(box);
    }

    private void showLoop() {
        syncContextFromInputsIfPresent();
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(10),dp(8),dp(10),dp(35));
        box.addView(sectionTitle("RECURSIVE FEEDBACK LOOP"));
        CollectiveEngine.State state = CollectiveEngine.stateForRound(samples, activeSession, activeRound);
        box.addView(tv("The current group state is turned back into a generated visual + tone pattern. Participants react to it. Their responses become another signal in the next cycle.", 12, muted, false));
        FeedbackView feedback = new FeedbackView(this); feedback.setState(state);
        box.addView(feedback, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        Button tone = button("PLAY CURRENT CORE TONES"); tone.setOnClickListener(v -> FeedbackAudio.play(state)); box.addView(tone, spacedButton());

        box.addView(label("Who is responding to the collective output?"));
        EditText responder = input(lastParticipant); box.addView(responder, matchWrap());
        TextView loopStatus = tv("Choose the response that best records the participant's reaction.", 12, muted, false);
        box.addView(loopStatus);
        LinearLayout responses = new LinearLayout(this); responses.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"MATCH", "NEUTRAL", "DIFFERENT"};
        for (String name : names) {
            Button b = button(name); b.setTextSize(10f);
            b.setOnClickListener(v -> {
                if (state.signals == 0) { loopStatus.setText("Collect at least one signal in this round first."); return; }
                String person = clean(responder.getText().toString(), "Participant 1");
                lastParticipant = person; saveContext();
                Sample s = new Sample(person, "feedback-" + name.toLowerCase(java.util.Locale.US), System.currentTimeMillis(),
                        SignalEngine.feedbackFeatures(name, state.core), "feedback", activeSession, activeRound, name);
                samples.add(s); store.save(samples); prototype = ModelEngine.build(samples);
                loopStatus.setText("Feedback response stored: " + person + " → " + name + ". Advance the round, expose the group to the new output, then collect again.");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(48),1f); lp.setMargins(dp(2),0,dp(2),0); responses.addView(b,lp);
        }
        box.addView(responses, spacedButton());
        Button cycle = button("ADVANCE LOOP ROUND → COLLECT AGAIN");
        cycle.setTextColor(Color.BLACK); cycle.setBackground(fillDrawable(green));
        cycle.setOnClickListener(v -> { activeRound++; saveContext(); showCollect(); });
        box.addView(cycle, spacedButton());
        box.addView(infoCard("Recursive cycle\nHumans → signals → baseline correction → collective core → generated feedback → human response → new signals → updated core → repeat"));
        scroll.addView(box); content.addView(scroll);
    }

    private void showLab() {
        syncContextFromInputsIfPresent();
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(11),dp(10),dp(11),dp(40));
        box.addView(sectionTitle("EXPERIMENT LAB"));
        box.addView(infoCard("V2 CHANNELS\n• voice: 16 kHz, 20 ms Hamming frames, 5 ms hop, 24 Bark bands\n• tap rhythm\n• reaction timing\n• typed-response pattern\n• accelerometer movement\n• drawing path\n• feedback response\n\nMODEL LAYERS\n• person + modality baselines\n• synchronized round residuals\n• collective latent core\n• coherence / dispersion / novelty / stability\n• influence scores\n• shuffled-round control\n• recursive visual + audio feedback"));
        TextView result = tv("Choose an analysis.", 13, text, false);
        result.setBackground(panelDrawable()); result.setPadding(dp(11),dp(11),dp(11),dp(11));
        Button held = button("1/3 HELD-OUT CLASSIFICATION"); held.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.heldOut(samples)))); box.addView(held,spacedButton());
        Button unseen = button("ORIGINAL MODEL ON LATER UNSEEN DATA"); unseen.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.unseen(samples)))); box.addView(unseen,spacedButton());
        Button inc = button("4-STAGE INCREMENTAL RETRAIN"); inc.setOnClickListener(v -> result.setText(ModelEngine.incrementalReport(samples))); box.addView(inc,spacedButton());
        Button control = button("SHUFFLED SYNCHRONIZATION CONTROL"); control.setOnClickListener(v -> result.setText(CollectiveEngine.shuffledControlReport(samples, activeSession))); box.addView(control,spacedButton());
        Button prediction = button("COLLECTIVE VS PERSONAL MODEL"); prediction.setOnClickListener(v -> result.setText(CollectiveEngine.collectiveVsPersonalPrediction(samples, activeSession))); box.addView(prediction,spacedButton());
        box.addView(result, matchWrap());

        Button export = button("EXPORT COLLECTIVE CAPSULE V2 (.JSON)"); export.setOnClickListener(v -> exportCapsule()); box.addView(export,spacedButton());
        Button imp = button("IMPORT + MERGE V1/V2 CAPSULE"); imp.setOnClickListener(v -> importCapsule()); box.addView(imp,spacedButton());
        box.addView(tv("Capsules preserve session, round, participant, modality, tag, timestamp, and feature vectors. Duplicate signal IDs are ignored when merging.", 12, muted, false));
        Button erase = button("ERASE V2 LOCAL DATASET"); erase.setTextColor(Color.rgb(255,180,180));
        erase.setOnClickListener(v -> { samples.clear(); store.clear(); prototype = ModelEngine.build(samples); Toast.makeText(this,"V2 dataset erased",Toast.LENGTH_SHORT).show(); showLab(); });
        box.addView(erase,spacedButton());
        scroll.addView(box); content.addView(scroll);
    }

    private void addSignal(SignalContext ctx, double[] features) {
        Sample s = new Sample(ctx.participant, ctx.label, System.currentTimeMillis(), SignalEngine.fit(features),
                ctx.modality, ctx.session, ctx.round, ctx.tag);
        samples.add(s);
        store.save(samples);
        prototype = ModelEngine.build(samples);
        lastParticipant = ctx.participant;
        activeSession = ctx.session;
        activeRound = ctx.round;
        saveContext();
        if (collectStats != null) collectStats.setText(statsText());
    }

    private SignalContext readSignalContext(String modality) {
        if (participantInput == null || sessionInput == null || roundInput == null || labelInput == null) return null;
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String session = clean(sessionInput.getText().toString(), "SESSION-1");
        int round = parseRound(roundInput.getText().toString());
        String label = labelInput.getText().toString().trim();
        if (label.isEmpty()) label = modality + "-signal";
        String tag = tagInput == null ? "" : tagInput.getText().toString().trim();
        return new SignalContext(participant,label,modality,session,round,tag);
    }

    private static final class SignalContext {
        final String participant, label, modality, session, tag;
        final int round;
        SignalContext(String participant, String label, String modality, String session, int round, String tag) {
            this.participant=participant; this.label=label; this.modality=modality; this.session=session; this.round=round; this.tag=tag;
        }
    }

    private void startFiveRound() {
        List<String> all = new ArrayList<>(Arrays.asList(PROMPTS));
        Collections.shuffle(all, new Random(System.nanoTime()));
        fiveRound = new ArrayList<>(all.subList(0,5));
        fiveIndex = 0;
        if (labelInput != null) labelInput.setText(fiveRound.get(0));
        if (collectStatus != null) collectStatus.setText("5-name round started. Prompt 1 of 5.");
    }

    private void advanceFiveRound() {
        if (fiveRound.isEmpty()) { if (collectStatus != null) collectStatus.setText("No active 5-name round."); return; }
        fiveIndex++;
        if (fiveIndex >= fiveRound.size()) {
            fiveRound.clear(); fiveIndex=-1;
            if (labelInput != null) labelInput.setText("");
            if (collectStatus != null) collectStatus.setText("5-name round complete.");
        } else {
            if (labelInput != null) labelInput.setText(fiveRound.get(fiveIndex));
            if (collectStatus != null) collectStatus.setText("Prompt " + (fiveIndex+1) + " of 5.");
        }
    }

    private void syncContextFromInputs() {
        if (participantInput != null) lastParticipant = clean(participantInput.getText().toString(), lastParticipant);
        if (sessionInput != null) activeSession = clean(sessionInput.getText().toString(), activeSession);
        if (roundInput != null) activeRound = parseRound(roundInput.getText().toString());
        saveContext();
    }

    private void syncContextFromInputsIfPresent() {
        if (participantInput != null || sessionInput != null || roundInput != null) syncContextFromInputs();
    }

    private void saveContext() {
        getPreferences(MODE_PRIVATE).edit()
                .putString("session", activeSession)
                .putInt("round", activeRound)
                .putString("participant", lastParticipant)
                .apply();
    }

    private void exportCapsule() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "collective-machine-capsule-v2.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importCapsule() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                String payload = store.exportPayload(samples);
                try (OutputStreamWriter w = new OutputStreamWriter(getContentResolver().openOutputStream(uri))) { w.write(payload); }
                Toast.makeText(this,"Capsule V2 exported",Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line; while ((line=r.readLine()) != null) sb.append(line).append('\n');
                }
                int added = store.importPayload(sb.toString(), samples);
                prototype = ModelEngine.build(samples);
                Toast.makeText(this,"Merged " + added + " new signals",Toast.LENGTH_LONG).show();
                showLab();
            }
        } catch (Exception e) {
            Toast.makeText(this,"File error: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingVoiceAfterPermission) startVoice();
            else if (!ok && collectStatus != null) collectStatus.setText("Microphone permission was not granted.");
            pendingVoiceAfterPermission = false;
        }
    }

    private String statsText() {
        Set<String> people = new HashSet<>();
        Set<String> modalities = new HashSet<>();
        int roundSignals = 0;
        for (Sample s : samples) {
            people.add(s.participant); modalities.add(s.modality);
            if (activeSession.equals(s.session) && activeRound == s.round) roundSignals++;
        }
        return "All signals: " + samples.size() + "   People: " + people.size() + "   Modalities: " + modalities.size() +
                "\nCurrent: " + activeSession + " / round " + activeRound + " / " + roundSignals + " signals";
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView tv(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0f,1.08f);
        return t;
    }

    private TextView sectionTitle(String value) {
        TextView t = tv(value, 17, gold, true);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
        t.setPadding(0,dp(7),0,dp(9));
        return t;
    }

    private TextView label(String value) {
        TextView t = tv(value, 12, muted, true);
        t.setPadding(0,dp(9),0,dp(3));
        return t;
    }

    private TextView infoCard(String value) {
        TextView t = tv(value, 12, text, false);
        t.setBackground(panelDrawable());
        t.setPadding(dp(11),dp(11),dp(11),dp(11));
        LinearLayout.LayoutParams lp = matchWrap(); lp.setMargins(0,dp(8),0,dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private EditText input(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(text);
        e.setHintTextColor(Color.rgb(105,116,145));
        e.setTextSize(14f);
        e.setSingleLine(true);
        e.setBackground(fillDrawable(Color.rgb(24,29,53)));
        e.setPadding(dp(10),0,dp(10),0);
        e.setMinHeight(dp(46));
        return e;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(text);
        b.setTextSize(12f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(fillDrawable(Color.rgb(30,38,68)));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams spacedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0,dp(7),0,0);
        return lp;
    }

    private GradientDrawable panelDrawable() { return fillDrawable(panel); }

    private GradientDrawable fillDrawable(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.argb(90,95,115,180));
        return g;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private int parseRound(String s) { try { return Math.max(0,Integer.parseInt(s.trim())); } catch (Exception e) { return 0; } }
    private String clean(String s, String fallback) { return TextUtils.isEmpty(s == null ? "" : s.trim()) ? fallback : s.trim(); }
}
