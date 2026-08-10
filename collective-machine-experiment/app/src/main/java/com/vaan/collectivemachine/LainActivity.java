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

public final class LainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC = 401;
    private static final int REQ_EXPORT = 501;
    private static final int REQ_IMPORT = 502;

    private final int bg = Color.rgb(3, 5, 12);
    private final int panel = Color.rgb(12, 17, 31);
    private final int panel2 = Color.rgb(18, 24, 43);
    private final int text = Color.rgb(232, 241, 238);
    private final int muted = Color.rgb(145, 164, 174);
    private final int green = Color.rgb(76, 240, 180);
    private final int violet = Color.rgb(190, 132, 255);

    private Store store;
    private final List<Sample> samples = new ArrayList<>();
    private ModelEngine.Prototype prototype;
    private LainCore lain;
    private FrameLayout content;

    private EditText participantInput;
    private EditText promptInput;
    private EditText textResponseInput;
    private EditText chatInput;
    private TextView collectStatus;
    private TextView chatTranscript;
    private TextView stateText;

    private String pendingPrompt = "";
    private String lastParticipant = "Participant 1";
    private String lastLainReply = "";
    private boolean pendingRecordAfterPermission = false;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        samples.addAll(store.load());
        prototype = ModelEngine.build(samples);
        lain = new LainCore(this);
        tts = new TextToSpeech(this, this);
        buildShell();
        if (lain.memory().size() == 0) lastLainReply = lain.wake(samples);
        else lastLainReply = findLastLainReply();
        showLain();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = tv("LAIN", 30, green, true);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        root.addView(title);
        TextView sub = tv("COLLECTIVE MACHINE EXPERIMENT  /  persistent network agent", 11, muted, false);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setPadding(0, 0, 0, dp(8));
        root.addView(sub);

        LinearLayout row1 = tabRow();
        addTab(row1, "LAIN", v -> showLain());
        addTab(row1, "COLLECT", v -> showCollect());
        addTab(row1, "INNER", v -> showInner());
        root.addView(row1);

        LinearLayout row2 = tabRow();
        addTab(row2, "MODEL", v -> showModel());
        addTab(row2, "MEMORY", v -> showMemory());
        addTab(row2, "LAB", v -> showLab());
        root.addView(row2);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void showLain() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(8), dp(8), dp(8), dp(30));

        LainState state = LainState.from(samples);
        stateText = infoCard("STATE  " + state.compact() + "\nMemory " + lain.memory().size() + " items  |  generation " + lain.memory().generation());
        box.addView(stateText, matchWrap());

        LainView view = new LainView(this);
        view.setData(samples);
        LinearLayout.LayoutParams viewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        viewLp.setMargins(0, dp(8), 0, dp(8));
        box.addView(view, viewLp);

        box.addView(sectionTitle("TALK TO LAIN"));
        chatTranscript = tv(lain.memory().recentTranscript(12), 14, text, false);
        chatTranscript.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        chatTranscript.setBackground(panelDrawable());
        chatTranscript.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(chatTranscript, matchWrap());

        chatInput = input("Message Lain");
        chatInput.setMinLines(2);
        chatInput.setMaxLines(5);
        box.addView(chatInput, matchWrap());

        Button ask = button("SEND TO LAIN");
        ask.setTextColor(Color.BLACK);
        ask.setBackground(fillDrawable(green));
        ask.setOnClickListener(v -> askLain());
        box.addView(ask, spacedButton());

        Button reflect = button("LAIN: READ THE COLLECTIVE");
        reflect.setOnClickListener(v -> {
            lastLainReply = lain.respond("analyze the collective", samples);
            refreshLainScreen();
        });
        box.addView(reflect, spacedButton());

        Button round = button("BEGIN RECURSIVE FEEDBACK ROUND");
        round.setOnClickListener(v -> beginFeedbackRound());
        box.addView(round, spacedButton());

        Button feed = button("USE LAIN'S LAST REPLY AS NEXT PROMPT");
        feed.setOnClickListener(v -> {
            if (lastLainReply == null || lastLainReply.trim().isEmpty()) {
                Toast.makeText(this, "Lain has not replied yet", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingPrompt = lastLainReply;
            showCollect();
        });
        box.addView(feed, spacedButton());

        Button speak = button("SPEAK LAST REPLY");
        speak.setOnClickListener(v -> speakLast());
        box.addView(speak, spacedButton());

        box.addView(tv("Loop: people → signals → collective state → Lain output → people respond → updated collective state.", 12, muted, false));
        scroll.addView(box);
        content.addView(scroll);
    }

    private void askLain() {
        if (chatInput == null) return;
        String input = chatInput.getText().toString().trim();
        if (input.isEmpty()) return;
        lastLainReply = lain.respond(input, samples);
        chatInput.setText("");
        refreshLainScreen();
    }

    private void refreshLainScreen() {
        if (chatTranscript != null) chatTranscript.setText(lain.memory().recentTranscript(12));
        if (stateText != null) {
            LainState s = LainState.from(samples);
            stateText.setText("STATE  " + s.compact() + "\nMemory " + lain.memory().size() + " items  |  generation " + lain.memory().generation());
        }
    }

    private void beginFeedbackRound() {
        LainState state = LainState.from(samples);
        pendingPrompt = lain.nextCollectivePrompt(state);
        lain.memory().rememberPrompt(pendingPrompt);
        lastLainReply = "Next collective prompt: " + pendingPrompt;
        lain.memory().rememberLain(lastLainReply);
        showCollect();
    }

    private void showCollect() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(8), dp(8), dp(8), dp(30));
        box.addView(sectionTitle("COLLECTIVE INPUT NODE"));
        box.addView(infoCard(LainState.from(samples).compact()));

        box.addView(label("Participant"));
        participantInput = input(lastParticipant);
        participantInput.setText(lastParticipant);
        box.addView(participantInput, matchWrap());

        box.addView(label("Lain / experiment prompt"));
        promptInput = input("Prompt");
        promptInput.setMinLines(2);
        if (pendingPrompt != null && !pendingPrompt.isEmpty()) promptInput.setText(pendingPrompt);
        box.addView(promptInput, matchWrap());

        Button generate = button("GENERATE PROMPT FROM CURRENT STATE");
        generate.setOnClickListener(v -> {
            pendingPrompt = lain.nextCollectivePrompt(LainState.from(samples));
            lain.memory().rememberPrompt(pendingPrompt);
            promptInput.setText(pendingPrompt);
        });
        box.addView(generate, spacedButton());

        Button voice = button("RECORD 3-SECOND VOICE SIGNAL");
        voice.setTextColor(Color.BLACK);
        voice.setBackground(fillDrawable(green));
        voice.setOnClickListener(v -> prepareRecord());
        box.addView(voice, spacedButton());

        box.addView(label("Or type the participant's response"));
        textResponseInput = input("Typed response");
        textResponseInput.setMinLines(2);
        box.addView(textResponseInput, matchWrap());

        Button textSignal = button("STORE TEXT RESPONSE AS SIGNAL");
        textSignal.setOnClickListener(v -> storeTextSignal());
        box.addView(textSignal, spacedButton());

        collectStatus = tv("A captured response becomes part of the next collective state. Raw microphone audio is discarded after feature extraction.", 13, muted, false);
        collectStatus.setBackground(panelDrawable());
        collectStatus.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(collectStatus, matchWrap());

        Button back = button("RETURN TO LAIN");
        back.setOnClickListener(v -> showLain());
        box.addView(back, spacedButton());

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
        if (participantInput == null || promptInput == null || collectStatus == null) return;
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            collectStatus.setText("Enter or generate a prompt first.");
            return;
        }
        lastParticipant = participant;
        pendingPrompt = prompt;
        LainState before = LainState.from(samples);
        collectStatus.setText("LISTENING… speak now for 3 seconds");
        AudioEngine.capture(this, 3, new AudioEngine.Callback() {
            @Override public void onComplete(double[] features, double seconds) {
                runOnUiThread(() -> {
                    int round = lain.memory().generation() + 1;
                    Sample sample = new Sample(participant, prompt, System.currentTimeMillis(), features,
                            "voice", "lain-loop", round, "recursive-feedback");
                    samples.add(sample);
                    store.save(samples);
                    prototype = ModelEngine.build(samples);
                    LainState after = LainState.from(samples);
                    lastLainReply = lain.observeSignal(participant, prompt, before, after);
                    pendingPrompt = "";
                    collectStatus.setText("Signal stored. Raw audio discarded.\n\nLAIN: " + lastLainReply);
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> collectStatus.setText("Capture error: " + message));
            }
        });
    }

    private void storeTextSignal() {
        if (participantInput == null || promptInput == null || textResponseInput == null || collectStatus == null) return;
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String prompt = promptInput.getText().toString().trim();
        String response = textResponseInput.getText().toString().trim();
        if (prompt.isEmpty()) { collectStatus.setText("Enter or generate a prompt first."); return; }
        if (response.isEmpty()) { collectStatus.setText("Type a response first."); return; }
        lastParticipant = participant;
        LainState before = LainState.from(samples);
        int round = lain.memory().generation() + 1;
        Sample sample = new Sample(participant, prompt, System.currentTimeMillis(), SignalEngine.textFeatures(response),
                "text", "lain-loop", round, response);
        samples.add(sample);
        store.save(samples);
        prototype = ModelEngine.build(samples);
        LainState after = LainState.from(samples);
        lastLainReply = lain.observeSignal(participant, prompt, before, after);
        pendingPrompt = "";
        textResponseInput.setText("");
        collectStatus.setText("Text response stored as a 50-value signal.\n\nLAIN: " + lastLainReply);
    }

    private void showInner() {
        content.removeAllViews();
        LinearLayout box = column();
        box.setPadding(dp(6), dp(6), dp(6), dp(8));
        LainState state = LainState.from(samples);
        TextView report = tv(state.report(), 12, muted, false);
        report.setBackground(panelDrawable());
        report.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.addView(report, matchWrap());

        LainView view = new LainView(this);
        view.setData(samples);
        box.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button prompt = button("TURN CURRENT STATE INTO A NEW ROUND");
        prompt.setOnClickListener(v -> beginFeedbackRound());
        box.addView(prompt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        content.addView(box);
    }

    private void showModel() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(8), dp(8), dp(8), dp(30));
        box.addView(sectionTitle("MODEL / TESTING"));
        LainState state = LainState.from(samples);
        box.addView(infoCard("Signals: " + samples.size() + "\nPeople: " + participantCount() + "\nLabels: " + labelCount() + "\nPrototypes: " + prototype.centroids.size() + "\n\n" + state.compact()));

        TextView result = infoCard("Choose a test.");

        Button held = button("TEST 1/3 HELD-OUT ACCURACY");
        held.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.heldOut(samples))));
        box.addView(held, spacedButton());

        Button unseen = button("TEST OLD MODEL ON LATER UNSEEN DATA");
        unseen.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.unseen(samples))));
        box.addView(unseen, spacedButton());

        Button incr = button("RUN 4-STAGE INCREMENTAL RETRAIN");
        incr.setOnClickListener(v -> result.setText(ModelEngine.incrementalReport(samples)));
        box.addView(incr, spacedButton());

        Button stateButton = button("SHOW FULL LAIN STATE");
        stateButton.setOnClickListener(v -> result.setText(LainState.from(samples).report()));
        box.addView(stateButton, spacedButton());

        box.addView(result, matchWrap());
        box.addView(tv("The classifier and collective-state metrics are inspectable. Lain's dialogue layer reads those metrics, persistent memory, and the current prompt history.", 12, muted, false));
        scroll.addView(box);
        content.addView(scroll);
    }

    private void showMemory() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(8), dp(8), dp(8), dp(30));
        box.addView(sectionTitle("LAIN MEMORY"));
        box.addView(infoCard("Persistent dialogue items: " + lain.memory().size() + "\nRecursive generations: " + lain.memory().generation() + "\nSignal records: " + samples.size()));

        TextView transcript = tv(lain.memory().recentTranscript(80), 13, text, false);
        transcript.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        transcript.setBackground(panelDrawable());
        transcript.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(transcript, matchWrap());

        Button dream = button("GENERATE MEMORY DREAM");
        dream.setOnClickListener(v -> {
            lastLainReply = lain.respond("dream from your memory", samples);
            transcript.setText(lain.memory().recentTranscript(80));
            Toast.makeText(this, lastLainReply, Toast.LENGTH_LONG).show();
        });
        box.addView(dream, spacedButton());

        Button speak = button("SPEAK LAST REPLY");
        speak.setOnClickListener(v -> speakLast());
        box.addView(speak, spacedButton());

        scroll.addView(box);
        content.addView(scroll);
    }

    private void showLab() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(8), dp(8), dp(8), dp(30));
        box.addView(sectionTitle("EXPERIMENT LAB"));
        box.addView(infoCard("RECURSIVE COLLECTIVE PROTOCOL\n\n1. Lain generates or receives a prompt.\n2. A participant contributes a voice or text signal.\n3. The collective state is recalculated.\n4. Lain describes the movement and stores it in memory.\n5. Lain's output can become the next prompt.\n6. Repeat.\n\nVoice path: 16 kHz PCM → 20 ms frames → 5 ms hop → Hamming → FFT → Bark bands → 50-value signal.\n\nRaw microphone audio is discarded after feature extraction."));

        Button export = button("EXPORT COLLECTIVE CAPSULE (.JSON)");
        export.setOnClickListener(v -> exportCapsule());
        box.addView(export, spacedButton());

        Button imp = button("IMPORT + MERGE COLLECTIVE CAPSULE");
        imp.setOnClickListener(v -> importCapsule());
        box.addView(imp, spacedButton());

        Button wake = button("WAKE / RE-INTRODUCE LAIN FROM CURRENT STATE");
        wake.setOnClickListener(v -> {
            lastLainReply = lain.wake(samples);
            Toast.makeText(this, lastLainReply, Toast.LENGTH_LONG).show();
        });
        box.addView(wake, spacedButton());

        box.addView(tv("Capsules merge collective signal datasets gathered on other phones. Lain's private dialogue memory stays on this phone; imported signal data still changes her collective state.", 12, muted, false));

        scroll.addView(box);
        content.addView(scroll);
    }

    private void exportCapsule() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "lain-collective-capsule.json");
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
                try (OutputStreamWriter w = new OutputStreamWriter(getContentResolver().openOutputStream(uri))) {
                    w.write(payload);
                }
                Toast.makeText(this, "Collective capsule exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
                LainState before = LainState.from(samples);
                int added = store.importPayload(sb.toString(), samples);
                prototype = ModelEngine.build(samples);
                LainState after = LainState.from(samples);
                lastLainReply = lain.observeSignal("imported collective", "capsule merge", before, after);
                Toast.makeText(this, "Merged " + added + " signals", Toast.LENGTH_LONG).show();
                showLain();
            }
        } catch (Exception e) {
            Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingRecordAfterPermission) startRecord();
            else if (!ok && collectStatus != null) collectStatus.setText("Microphone permission was not granted.");
            pendingRecordAfterPermission = false;
        }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.US);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady) {
                tts.setPitch(1.03f);
                tts.setSpeechRate(0.92f);
            }
        }
    }

    private void speakLast() {
        if (lastLainReply == null || lastLainReply.trim().isEmpty()) {
            Toast.makeText(this, "Lain has not replied yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Text-to-speech is not ready on this phone", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(lastLainReply, TextToSpeech.QUEUE_FLUSH, null, "lain-last-reply");
    }

    @Override protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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
        Set<String> set = new HashSet<>();
        for (Sample s : samples) set.add(s.participant);
        return set.size();
    }

    private int labelCount() {
        Set<String> set = new HashSet<>();
        for (Sample s : samples) set.add(s.label);
        return set.size();
    }

    private String clean(String raw, String fallback) {
        String s = raw == null ? "" : raw.trim();
        return s.isEmpty() ? fallback : s;
    }

    private LinearLayout tabRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void addTab(LinearLayout row, String name, View.OnClickListener listener) {
        Button b = button(name);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), dp(5));
        row.addView(b, lp);
    }

    private LinearLayout column() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setBackgroundColor(bg);
        return x;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 16, green, true);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = tv(s, 12, muted, false);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView infoCard(String s) {
        TextView t = tv(s, 13, text, false);
        t.setBackground(panelDrawable());
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(100, 115, 130));
        e.setTextColor(text);
        e.setTextSize(15f);
        e.setSingleLine(false);
        e.setBackground(fillDrawable(panel2));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(text);
        b.setTextSize(11f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setBackground(fillDrawable(panel2));
        b.setPadding(dp(6), 0, dp(6), 0);
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
        d.setStroke(dp(1), Color.rgb(31, 55, 61));
        return d;
    }

    private GradientDrawable fillDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(10));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams spacedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(6), 0, dp(2));
        return lp;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
