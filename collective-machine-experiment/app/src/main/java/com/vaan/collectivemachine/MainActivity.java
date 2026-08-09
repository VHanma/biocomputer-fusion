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

    private Store store;
    private final List<Sample> samples = new ArrayList<>();
    private ModelEngine.Prototype prototype;
    private FrameLayout content;
    private EditText participantInput;
    private EditText labelInput;
    private TextView collectStatus;
    private TextView collectStats;
    private List<String> fiveRound = new ArrayList<>();
    private int roundIndex = -1;
    private boolean pendingRecordAfterPermission = false;

    private final int bg = Color.rgb(5, 7, 18);
    private final int panel = Color.rgb(15, 19, 38);
    private final int text = Color.rgb(235, 238, 248);
    private final int muted = Color.rgb(155, 167, 196);
    private final int gold = Color.rgb(230, 210, 93);
    private final int green = Color.rgb(69, 235, 164);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        samples.addAll(store.load());
        prototype = ModelEngine.build(samples);
        buildShell();
        showCollect();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView title = tv("COLLECTIVE MACHINE EXPERIMENT", 22, gold, true);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        root.addView(title);
        TextView sub = tv("many humans → signals → prototype → retraining → inner view", 12, muted, false);
        sub.setPadding(0, dp(3), 0, dp(10));
        root.addView(sub);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        String[] names = {"COLLECT", "MODEL", "INNER", "LAB"};
        View.OnClickListener[] actions = {v -> showCollect(), v -> showModel(), v -> showInner(), v -> showLab()};
        for (int i = 0; i < names.length; i++) {
            Button b = button(names[i]);
            b.setOnClickListener(actions[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMargins(dp(3), 0, dp(3), dp(8));
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
        box.setPadding(dp(12), dp(12), dp(12), dp(30));
        box.addView(sectionTitle("SIGNAL COLLECTION"));
        collectStats = tv(statsText(), 14, text, false);
        collectStats.setBackground(panelDrawable());
        collectStats.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(collectStats, matchWrap());
        box.addView(label("Participant"));
        participantInput = input("Participant 1");
        box.addView(participantInput, matchWrap());
        box.addView(label("Prompt / class label"));
        labelInput = input("");
        labelInput.setHint("say the displayed name, or type your own phrase");
        box.addView(labelInput, matchWrap());
        Button round = button("PICK 5-NAME ROUND FROM 64");
        round.setOnClickListener(v -> startFiveRound());
        box.addView(round, spacedButton());
        Button record = button("RECORD 3-SECOND SAMPLE");
        record.setTextColor(Color.BLACK);
        record.setBackground(fillDrawable(green));
        record.setOnClickListener(v -> prepareRecord());
        box.addView(record, spacedButton());
        Button skip = button("NEXT PROMPT");
        skip.setOnClickListener(v -> advanceRound());
        box.addView(skip, spacedButton());
        collectStatus = tv("Audio is analyzed locally. Raw audio is discarded after features are extracted.", 13, muted, false);
        collectStatus.setPadding(0, dp(12), 0, dp(12));
        box.addView(collectStatus);
        box.addView(sectionTitle("WHAT EACH SAMPLE BECOMES"));
        box.addView(infoCard("16,000 Hz mono PCM\n20 ms frames\n5 ms hop\nHamming window\n512-point FFT\n24 Bark bands\nmean + variation + RMS + zero-crossing\n→ 50-number feature signal"));
        scroll.addView(box);
        content.addView(scroll);
        if (roundIndex >= 0 && roundIndex < fiveRound.size()) labelInput.setText(fiveRound.get(roundIndex));
    }

    private void startFiveRound() {
        List<String> all = new ArrayList<>(Arrays.asList(PROMPTS));
        Collections.shuffle(all, new Random(System.nanoTime()));
        fiveRound = new ArrayList<>(all.subList(0, 5));
        roundIndex = 0;
        labelInput.setText(fiveRound.get(0));
        collectStatus.setText("5-name round started. Prompt 1 of 5.");
    }

    private void advanceRound() {
        if (fiveRound.isEmpty()) { collectStatus.setText("No active 5-name round."); return; }
        roundIndex++;
        if (roundIndex >= fiveRound.size()) {
            collectStatus.setText("5-name round complete.");
            fiveRound.clear(); roundIndex = -1; labelInput.setText("");
        } else {
            labelInput.setText(fiveRound.get(roundIndex));
            collectStatus.setText("Prompt " + (roundIndex + 1) + " of 5.");
        }
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
        String participant = clean(participantInput.getText().toString(), "Participant 1");
        String prompt = labelInput.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) { collectStatus.setText("Type a prompt/class label first."); return; }
        collectStatus.setText("LISTENING… speak now for 3 seconds");
        AudioEngine.capture(this, 3, new AudioEngine.Callback() {
            @Override public void onComplete(double[] features, double seconds) {
                runOnUiThread(() -> {
                    Sample s = new Sample(participant, prompt, System.currentTimeMillis(), features);
                    samples.add(s); store.save(samples); prototype = ModelEngine.build(samples);
                    collectStatus.setText("Captured " + String.format(java.util.Locale.US, "%.1fs", seconds) + " → signal stored. Raw audio discarded.");
                    collectStats.setText(statsText());
                    if (!fiveRound.isEmpty()) advanceRound();
                });
            }
            @Override public void onError(String message) { runOnUiThread(() -> collectStatus.setText("Capture error: " + message)); }
        });
    }

    private void showModel() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(12), dp(12), dp(12), dp(30));
        box.addView(sectionTitle("PROTOTYPE MODEL"));
        box.addView(infoCard("Samples: " + samples.size() + "\nParticipants: " + participantCount() + "\nLabels: " + labelCount() + "\nCurrent prototypes: " + prototype.centroids.size()));
        TextView result = tv("Choose an experiment below.", 14, text, false);
        result.setBackground(panelDrawable()); result.setPadding(dp(12), dp(12), dp(12), dp(12));
        Button build = button("BUILD CURRENT PROTOTYPE");
        build.setOnClickListener(v -> { prototype = ModelEngine.build(samples); result.setText("Prototype rebuilt from " + samples.size() + " samples.\nClass centroids: " + prototype.centroids.size() + "\nFeature dimensions: " + prototype.featureCount); });
        box.addView(build, spacedButton());
        Button held = button("TEST 1/3 HELD-OUT ACCURACY");
        held.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.heldOut(samples))));
        box.addView(held, spacedButton());
        Button unseen = button("TEST ORIGINAL MODEL ON UNSEEN DATA");
        unseen.setOnClickListener(v -> result.setText(ModelEngine.summary(ModelEngine.unseen(samples))));
        box.addView(unseen, spacedButton());
        Button incr = button("RUN 4-STAGE INCREMENTAL RETRAIN");
        incr.setOnClickListener(v -> result.setText(ModelEngine.incrementalReport(samples)));
        box.addView(incr, spacedButton());
        box.addView(result, matchWrap());
        box.addView(tv("Model = nearest acoustic prototype centroid. This is a transparent experimental classifier, not a claim of mind-reading or identity recognition.", 12, muted, false));
        scroll.addView(box); content.addView(scroll);
    }

    private void showInner() {
        content.removeAllViews();
        LinearLayout box = column(); box.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.addView(tv("Each outer node = one human utterance signal. Curves converge toward the current class-prototype core.", 12, muted, false));
        CollectiveView view = new CollectiveView(this); view.setData(samples, prototype);
        box.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button refresh = button("REBUILD + REFRESH INNER VIEW");
        refresh.setOnClickListener(v -> { prototype = ModelEngine.build(samples); view.setData(samples, prototype); });
        box.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(box);
    }

    private void showLab() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(12), dp(12), dp(12), dp(30));
        box.addView(sectionTitle("EXPERIMENT LAB"));
        box.addView(infoCard("LOCAL COLLECTIVE PROTOCOL\n\n• 64 built-in name prompts; random 5-name rounds\n• 16 kHz microphone capture\n• 20 ms speech frames\n• 5 ms frame shift\n• Hamming window\n• Bark-scale power features\n• 1/3 testing, 2/3 training experiment\n• original-vs-unseen test\n• 4-stage incremental retraining\n• prototype / inner-view visualization\n\nThe app does not upload data and does not save raw microphone audio."));
        Button export = button("EXPORT COLLECTIVE CAPSULE (.JSON)"); export.setOnClickListener(v -> exportCapsule()); box.addView(export, spacedButton());
        Button imp = button("IMPORT + MERGE CAPSULE"); imp.setOnClickListener(v -> importCapsule()); box.addView(imp, spacedButton());
        box.addView(tv("Use capsules to combine feature datasets gathered on different phones. Duplicate sample IDs are ignored.", 12, muted, false));
        Button erase = button("ERASE LOCAL DATASET"); erase.setTextColor(Color.rgb(255, 180, 180));
        erase.setOnClickListener(v -> { samples.clear(); store.clear(); prototype = ModelEngine.build(samples); Toast.makeText(this, "Local dataset erased", Toast.LENGTH_SHORT).show(); showLab(); });
        box.addView(erase, spacedButton());
        scroll.addView(box); content.addView(scroll);
    }

    private void exportCapsule() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "collective-machine-capsule.json"); startActivityForResult(i, REQ_EXPORT);
    }

    private void importCapsule() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                String payload = store.exportPayload(samples);
                try (OutputStreamWriter w = new OutputStreamWriter(getContentResolver().openOutputStream(uri))) { w.write(payload); }
                Toast.makeText(this, "Capsule exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) { String line; while ((line = r.readLine()) != null) sb.append(line).append('\n'); }
                int added = store.importPayload(sb.toString(), samples); prototype = ModelEngine.build(samples);
                Toast.makeText(this, "Merged " + added + " new signals", Toast.LENGTH_LONG).show(); showLab();
            }
        } catch (Exception e) { Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
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

    private String statsText() { return "Signals: " + samples.size() + "    People: " + participantCount() + "    Labels: " + labelCount(); }
    private int participantCount() { Set<String> s = new HashSet<>(); for (Sample x : samples) s.add(x.participant); return s.size(); }
    private int labelCount() { Set<String> s = new HashSet<>(); for (Sample x : samples) s.add(x.label); return s.size(); }
    private String clean(String s, String fallback) { s = s == null ? "" : s.trim(); return s.isEmpty() ? fallback : s; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(bg); return l; }
    private TextView sectionTitle(String s) { TextView v = tv(s, 18, gold, true); v.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)); v.setPadding(0, dp(10), 0, dp(8)); return v; }
    private TextView label(String s) { TextView v = tv(s, 12, muted, true); v.setPadding(0, dp(12), 0, dp(4)); return v; }
    private TextView infoCard(String s) { TextView v = tv(s, 14, text, false); v.setBackground(panelDrawable()); v.setPadding(dp(12), dp(12), dp(12), dp(12)); return v; }
    private TextView tv(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private EditText input(String value) { EditText e = new EditText(this); e.setText(value); e.setTextColor(text); e.setHintTextColor(Color.rgb(105, 116, 148)); e.setSingleLine(true); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(fillDrawable(Color.rgb(25, 31, 57))); e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))); return e; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(11); b.setTextColor(text); b.setAllCaps(false); b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)); b.setBackground(fillDrawable(Color.rgb(32, 40, 73))); return b; }
    private GradientDrawable fillDrawable(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(10)); return g; }
    private GradientDrawable panelDrawable() { GradientDrawable g = fillDrawable(panel); g.setStroke(dp(1), Color.rgb(42, 54, 92)); return g; }
    private LinearLayout.LayoutParams spacedButton() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)); lp.setMargins(0, dp(8), 0, 0); return lp; }
    private LinearLayout.LayoutParams matchWrap() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(4)); return lp; }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
