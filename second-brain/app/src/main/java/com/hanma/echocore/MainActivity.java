package com.hanma.echocore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = 0xFF090B10;
    private static final int PANEL = 0xFF121722;
    private static final int PANEL2 = 0xFF181F2D;
    private static final int TEXT = 0xFFF4F7FF;
    private static final int MUTED = 0xFF98A4BA;
    private static final int ACCENT = 0xFF7C9CFF;
    private static final int ACCENT2 = 0xFF56E0C5;
    private static final int DANGER = 0xFFFF7A90;

    private static final int REQ_SPEECH = 700;
    private static final int REQ_EXPORT = 701;
    private static final int REQ_IMPORT = 702;

    private BrainDatabase db;
    private BrainEngine engine;
    private FrameLayout content;
    private final List<Button> navButtons = new ArrayList<>();
    private EditText talkInput;
    private LinearLayout chatStream;
    private ScrollView chatScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db = new BrainDatabase(this);
        engine = new BrainEngine(db);
        setContentView(buildShell());
        showCortex();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(8), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(8), dp(18), dp(10));
        TextView title = text("ECHOCORE", 20, TEXT, true);
        TextView sub = text("SECOND BRAIN · LOCAL MEMORY SYSTEM", 10, MUTED, true);
        header.addView(title);
        header.addView(sub);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(content, cp);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(8));
        nav.setBackgroundColor(0xFF0D1119);
        addNav(nav, "CORE", this::showCortex);
        addNav(nav, "TALK", this::showTalk);
        addNav(nav, "VAULT", this::showMemory);
        addNav(nav, "MAP", this::showGraph);
        addNav(nav, "FOCUS", this::showFocus);
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void addNav(LinearLayout nav, String label, Runnable action) {
        Button b = button(label, PANEL2, MUTED);
        b.setTextSize(10);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> {
            for (Button x : navButtons) {
                x.setTextColor(MUTED);
                x.setBackground(round(PANEL2, 12));
            }
            b.setTextColor(BG);
            b.setBackground(round(ACCENT, 12));
            action.run();
        });
        navButtons.add(b);
        nav.addView(b);
    }

    private void selectNav(int i) {
        for (int x = 0; x < navButtons.size(); x++) {
            Button b = navButtons.get(x);
            if (x == i) {
                b.setTextColor(BG); b.setBackground(round(ACCENT, 12));
            } else {
                b.setTextColor(MUTED); b.setBackground(round(PANEL2, 12));
            }
        }
    }

    private void showCortex() {
        selectNav(0);
        LinearLayout body = page();
        body.addView(sectionTitle("CORTEX CAPTURE", "Throw thoughts in fast. Structure comes after."));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard(String.valueOf(db.count()), "NODES"), weight());
        stats.addView(statCard(String.valueOf(db.countType("FOCUS")), "FOCUS"), weight());
        stats.addView(statCard(String.valueOf(db.countType("IDEA")), "IDEAS"), weight());
        body.addView(stats, margins(-1, -2, 0, 8, 0, 12));

        LinearLayout capture = card();
        EditText thought = edit("Dump a thought, realization, plan, question…", 5);
        capture.addView(thought, margins(-1, -2, 0, 0, 0, 10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Spinner type = new Spinner(this);
        String[] types = {"THOUGHT", "IDEA", "FOCUS", "INSIGHT", "QUESTION", "REFERENCE"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        type.setAdapter(adapter);
        type.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        row.addView(type, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button autoTag = button("AUTO TAG", PANEL2, ACCENT2);
        row.addView(autoTag, margins(dp(105), dp(48), 8, 0, 0, 0));
        capture.addView(row);

        EditText tags = edit("Tags, comma separated (optional)", 1);
        capture.addView(tags, margins(-1, -2, 0, 10, 0, 8));
        autoTag.setOnClickListener(v -> tags.setText(engine.autoTags(thought.getText().toString())));

        TextView importanceLabel = text("SIGNAL STRENGTH · 7/10", 11, MUTED, true);
        capture.addView(importanceLabel);
        SeekBar importance = new SeekBar(this);
        importance.setMax(9);
        importance.setProgress(6);
        importance.setProgressTintList(ColorStateList.valueOf(ACCENT));
        importance.setThumbTintList(ColorStateList.valueOf(ACCENT2));
        importance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) { importanceLabel.setText("SIGNAL STRENGTH · " + (p + 1) + "/10"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        capture.addView(importance);

        Button save = button("SAVE TO BRAIN", ACCENT, BG);
        save.setOnClickListener(v -> {
            String t = thought.getText().toString().trim();
            if (t.isEmpty()) { toast("Give the brain something to hold."); return; }
            String tg = tags.getText().toString().trim();
            if (tg.isEmpty()) tg = engine.autoTags(t);
            db.addMemory(t, type.getSelectedItem().toString(), tg, importance.getProgress() + 1);
            thought.setText(""); tags.setText(""); importance.setProgress(6);
            toast("Memory node created.");
            showCortex();
        });
        capture.addView(save, margins(-1, dp(52), 0, 12, 0, 0));
        body.addView(capture, margins(-1, -2, 0, 0, 0, 12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button recall = button("RECALL SPARK", PANEL2, ACCENT2);
        recall.setOnClickListener(v -> dialog("Recall Spark", engine.answer("recall")));
        Button today = button("COMPRESS TODAY", PANEL2, ACCENT);
        today.setOnClickListener(v -> dialog("Daily Compression", engine.answer("summarize today")));
        actions.addView(recall, weight());
        LinearLayout.LayoutParams tp = weight(); tp.setMargins(dp(8),0,0,0); actions.addView(today, tp);
        body.addView(actions, margins(-1, dp(50), 0, 0, 0, 14));

        body.addView(text("RECENT SIGNAL", 11, MUTED, true), margins(-1, -2, 0, 0, 0, 8));
        List<MemoryNode> recent = db.recent(5);
        if (recent.isEmpty()) body.addView(emptyCard("Your second brain is quiet. The first saved thought becomes node zero."));
        else for (MemoryNode m : recent) body.addView(memoryMini(m), margins(-1, -2, 0, 0, 0, 8));
        setPage(body);
    }

    private void showTalk() {
        selectNav(1);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(14), dp(4), dp(14), dp(10));
        frame.addView(sectionTitle("TALK TO YOUR BRAIN", "Ask your stored memories questions. Commands work offline."));

        chatScroll = new ScrollView(this);
        chatStream = new LinearLayout(this);
        chatStream.setOrientation(LinearLayout.VERTICAL);
        chatStream.setPadding(0, dp(4), 0, dp(8));
        chatScroll.addView(chatStream);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f);
        frame.addView(chatScroll, sp);

        addChat(false, "I’m EchoCore. Try: “remember …”, “what did I say about …”, “connect X and Y”, “summarize today”, or “what should I focus on?”");

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        String[] chips = {"RECALL", "TODAY", "FOCUS", "SURPRISE ME"};
        for (String c : chips) {
            Button b = button(c, PANEL2, MUTED);
            b.setTextSize(10);
            b.setOnClickListener(v -> sendTalk(c.toLowerCase(Locale.US)));
            quick.addView(b, margins(-2, dp(42), 0, 0, 6, 0));
        }
        hs.addView(quick);
        frame.addView(hs, margins(-1, dp(46), 0, 4, 0, 4));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        talkInput = edit("Ask EchoCore…", 2);
        talkInput.setSingleLine(false);
        talkInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputRow.addView(talkInput, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button mic = button("MIC", PANEL2, ACCENT2);
        mic.setOnClickListener(v -> startSpeech());
        inputRow.addView(mic, margins(dp(58), dp(58), 8, 0, 0, 0));
        Button send = button("SEND", ACCENT, BG);
        send.setOnClickListener(v -> sendTalk(talkInput.getText().toString()));
        inputRow.addView(send, margins(dp(72), dp(58), 8, 0, 0, 0));
        frame.addView(inputRow);
        content.removeAllViews();
        content.addView(frame);
    }

    private void sendTalk(String message) {
        String m = message == null ? "" : message.trim();
        if (m.isEmpty()) return;
        if (talkInput != null) talkInput.setText("");
        addChat(true, m);
        String reply = engine.answer(m);
        addChat(false, reply);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addChat(boolean user, String message) {
        if (chatStream == null) return;
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackground(round(user ? 0xFF24345D : PANEL2, 15));
        TextView role = text(user ? "YOU" : "ECHOCORE", 9, user ? 0xFFAFC4FF : ACCENT2, true);
        TextView body = text(message, 14, TEXT, false);
        body.setLineSpacing(0, 1.12f);
        bubble.addView(role);
        bubble.addView(body, margins(-1, -2, 0, 4, 0, 0));
        LinearLayout.LayoutParams p = margins(-1, -2, user ? 44 : 0, 4, user ? 0 : 44, 8);
        chatStream.addView(bubble, p);
    }

    private void showMemory() {
        selectNav(2);
        LinearLayout body = page();
        body.addView(sectionTitle("MEMORY VAULT", "Search, pin, export, or prune the nodes."));
        EditText search = edit("Search your memory…", 1);
        body.addView(search, margins(-1, dp(50), 0, 0, 0, 8));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        Runnable refresh = () -> renderMemoryResults(results, search.getText().toString());
        search.setOnEditorActionListener((v, actionId, event) -> { refresh.run(); return true; });
        Button find = button("SEARCH", ACCENT, BG);
        find.setOnClickListener(v -> refresh.run());
        body.addView(find, margins(-1, dp(46), 0, 0, 0, 10));

        LinearLayout io = new LinearLayout(this);
        io.setOrientation(LinearLayout.HORIZONTAL);
        Button export = button("EXPORT JSON", PANEL2, ACCENT2);
        export.setOnClickListener(v -> beginExport());
        Button imp = button("IMPORT", PANEL2, ACCENT);
        imp.setOnClickListener(v -> beginImport());
        io.addView(export, weight());
        LinearLayout.LayoutParams ip = weight(); ip.setMargins(dp(8),0,0,0); io.addView(imp, ip);
        body.addView(io, margins(-1, dp(48), 0, 0, 0, 12));

        body.addView(results);
        renderMemoryResults(results, "");
        setPage(body);
    }

    private void renderMemoryResults(LinearLayout results, String query) {
        results.removeAllViews();
        List<MemoryNode> list = db.search(query, 80);
        if (list.isEmpty()) { results.addView(emptyCard("No matching nodes.")); return; }
        for (MemoryNode m : list) {
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = text((m.pinned ? "PINNED · " : "") + m.type + " · " + m.importance + "/10", 10, m.pinned ? ACCENT2 : MUTED, true);
            top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView date = text(BrainEngine.formatDate(m.createdAt), 10, MUTED, false);
            top.addView(date);
            c.addView(top);
            TextView txt = text(m.text, 15, TEXT, false); txt.setLineSpacing(0,1.12f);
            c.addView(txt, margins(-1, -2, 0, 8, 0, 6));
            if (!m.tags.isEmpty()) c.addView(text("# " + m.tags, 11, ACCENT, false));
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            Button pin = button(m.pinned ? "UNPIN" : "PIN", PANEL2, ACCENT2);
            pin.setOnClickListener(v -> { db.setPinned(m.id, !m.pinned); renderMemoryResults(results, query); });
            Button del = button("DELETE", 0xFF28161C, DANGER);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete memory node?")
                    .setMessage(m.text)
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Delete", (d, w) -> { db.delete(m.id); renderMemoryResults(results, query); })
                    .show());
            controls.addView(pin, weight());
            LinearLayout.LayoutParams deleteParams = weight(); deleteParams.setMargins(dp(8),0,0,0); controls.addView(del, deleteParams);
            c.addView(controls, margins(-1, dp(44), 0, 10, 0, 0));
            results.addView(c, margins(-1, -2, 0, 0, 0, 9));
        }
    }

    private void showGraph() {
        selectNav(3);
        LinearLayout body = page();
        body.addView(sectionTitle("MEMORY CONSTELLATION", "Nearness is time-biased. Lines appear when nodes share meaningful words."));
        BrainGraphView graph = new BrainGraphView(this, db);
        body.addView(graph, margins(-1, dp(480), 0, 4, 0, 10));
        body.addView(emptyCard("Tap a node to read it. Bigger nodes carry stronger signal. Pinned memories swell slightly so they remain visible."));
        setPage(body);
    }

    private void showFocus() {
        selectNav(4);
        LinearLayout body = page();
        body.addView(sectionTitle("FOCUS STACK", "The thoughts you want kept in working memory."));
        LinearLayout add = card();
        EditText focus = edit("What deserves active attention?", 2);
        add.addView(focus);
        Button addBtn = button("ADD TO FOCUS", ACCENT, BG);
        addBtn.setOnClickListener(v -> {
            String s = focus.getText().toString().trim();
            if (s.isEmpty()) return;
            db.addMemory(s, "FOCUS", engine.autoTags(s), 9);
            showFocus();
        });
        add.addView(addBtn, margins(-1, dp(50), 0, 10, 0, 0));
        body.addView(add, margins(-1, -2, 0, 0, 0, 12));

        List<MemoryNode> focusItems = db.byType("FOCUS", 50);
        if (focusItems.isEmpty()) body.addView(emptyCard("Nothing is demanding the foreground right now."));
        for (MemoryNode m : focusItems) {
            LinearLayout c = card();
            c.addView(text(m.text, 15, TEXT, false));
            if (!m.tags.isEmpty()) c.addView(text(m.tags, 10, MUTED, false), margins(-1,-2,0,6,0,4));
            Button done = button("ARCHIVE AS DONE", PANEL2, ACCENT2);
            done.setOnClickListener(v -> { db.setType(m.id, "DONE"); showFocus(); });
            c.addView(done, margins(-1, dp(44), 0, 8, 0, 0));
            body.addView(c, margins(-1, -2, 0, 0, 0, 9));
        }
        setPage(body);
    }

    private void startSpeech() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to EchoCore");
        try { startActivityForResult(i, REQ_SPEECH); }
        catch (ActivityNotFoundException e) { toast("No speech-recognition service is installed on this phone."); }
    }

    private void beginExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "EchoCore-memory.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void beginImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        try {
            if (requestCode == REQ_SPEECH) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) {
                    if (talkInput != null) talkInput.setText(r.get(0));
                    sendTalk(r.get(0));
                }
            } else if (requestCode == REQ_EXPORT) {
                Uri uri = data.getData();
                if (uri != null) writeExport(uri);
            } else if (requestCode == REQ_IMPORT) {
                Uri uri = data.getData();
                if (uri != null) readImport(uri);
            }
        } catch (Exception e) {
            dialog("EchoCore file error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void writeExport(Uri uri) throws Exception {
        JSONArray arr = new JSONArray();
        for (MemoryNode m : db.recent(100000)) {
            JSONObject o = new JSONObject();
            o.put("text", m.text); o.put("type", m.type); o.put("tags", m.tags);
            o.put("importance", m.importance); o.put("createdAt", m.createdAt); o.put("pinned", m.pinned);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "EchoCore Second Brain");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("memories", arr);
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new Exception("Could not open export destination.");
            os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        toast("Brain exported.");
    }

    private void readImport(Uri uri) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        JSONObject root = new JSONObject(b.toString());
        JSONArray arr = root.getJSONArray("memories");
        int added = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            long id = db.addMemoryAt(o.optString("text"), o.optString("type", "THOUGHT"), o.optString("tags"),
                    o.optInt("importance", 5), o.optLong("createdAt", System.currentTimeMillis()), o.optBoolean("pinned", false));
            if (id > 0) added++;
        }
        toast("Imported " + added + " memory nodes.");
        showMemory();
    }

    private LinearLayout page() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(22));
        return body;
    }

    private void setPage(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        content.removeAllViews();
        content.addView(scroll);
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(title, 17, TEXT, true));
        box.addView(text(subtitle, 12, MUTED, false), margins(-1, -2, 0, 3, 0, 0));
        box.setPadding(0, dp(6), 0, dp(12));
        return box;
    }

    private LinearLayout statCard(String value, String label) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8), dp(12), dp(8), dp(12));
        c.addView(text(value, 22, ACCENT2, true));
        c.addView(text(label, 9, MUTED, true));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        c.setLayoutParams(p);
        return c;
    }

    private View memoryMini(MemoryNode m) {
        LinearLayout c = card();
        TextView meta = text((m.pinned ? "PINNED · " : "") + m.type + " · " + BrainEngine.formatDate(m.createdAt), 10, m.pinned ? ACCENT2 : MUTED, true);
        TextView body = text(m.text, 14, TEXT, false);
        c.addView(meta);
        c.addView(body, margins(-1,-2,0,5,0,0));
        if (!m.tags.isEmpty()) c.addView(text(m.tags, 10, ACCENT, false), margins(-1,-2,0,4,0,0));
        return c;
    }

    private View emptyCard(String message) {
        LinearLayout c = card();
        c.addView(text(message, 13, MUTED, false));
        return c;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(13), dp(14), dp(13));
        l.setBackground(round(PANEL, 16));
        return l;
    }

    private EditText edit(String hint, int lines) {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF69768D);
        e.setHint(hint);
        e.setTextSize(15);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(round(PANEL2, 12));
        e.setMinLines(lines);
        e.setMaxLines(Math.max(lines, 8));
        return e;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(fg);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(round(bg, 12));
        b.setStateListAnimator(null);
        return b;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private LinearLayout.LayoutParams margins(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void dialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Close", null).show();
    }
}
