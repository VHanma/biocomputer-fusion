package com.hanma.echocore;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DiagnosticsStore {
    private static final String PREF = "echocore_nexus_diagnostics";
    private static final int MAX_LOG_CHARS = 12000;
    private final SharedPreferences prefs;

    public DiagnosticsStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void setState(String state) {
        prefs.edit().putString("state", safe(state)).putLong("state_at", System.currentTimeMillis()).apply();
    }

    public synchronized void event(String kind, String detail) {
        long now = System.currentTimeMillis();
        String line = now + " | " + safe(kind) + " | " + safe(detail);
        String old = prefs.getString("event_log", "");
        String next = old == null || old.isEmpty() ? line : old + "\n" + line;
        if (next.length() > MAX_LOG_CHARS) next = next.substring(next.length() - MAX_LOG_CHARS);
        prefs.edit().putString("event_log", next).putString("last_event", line).apply();
    }

    public synchronized void error(String where, Throwable t) {
        String summary = safe(where) + ": " + (t == null ? "unknown" : t.getClass().getSimpleName() + ": " + safe(t.getMessage()));
        String stack = "";
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            stack = sw.toString();
            if (stack.length() > 10000) stack = stack.substring(0, 10000);
        }
        int failures = prefs.getInt("failure_count", 0) + 1;
        prefs.edit()
                .putString("last_error", summary)
                .putString("last_stack", stack)
                .putLong("last_error_at", System.currentTimeMillis())
                .putInt("failure_count", failures)
                .apply();
        event("ERROR", summary);
    }

    public synchronized void relaySuccess(long latencyMs) {
        prefs.edit()
                .putLong("last_success_at", System.currentTimeMillis())
                .putLong("last_relay_ms", Math.max(0, latencyMs))
                .putInt("consecutive_failures", 0)
                .apply();
    }

    public synchronized void relayFailure(String detail, int consecutive) {
        prefs.edit()
                .putString("last_error", safe(detail))
                .putLong("last_error_at", System.currentTimeMillis())
                .putInt("consecutive_failures", Math.max(0, consecutive))
                .putInt("failure_count", prefs.getInt("failure_count", 0) + 1)
                .apply();
        event("RELAY_FAIL", detail);
    }

    public synchronized void commandDone(String action) {
        prefs.edit()
                .putString("last_command", safe(action))
                .putLong("last_command_at", System.currentTimeMillis())
                .putInt("command_count", prefs.getInt("command_count", 0) + 1)
                .apply();
        event("COMMAND", action);
    }

    public synchronized void serviceRestart() {
        prefs.edit().putInt("service_restarts", prefs.getInt("service_restarts", 0) + 1).apply();
    }

    public synchronized void processCrash(String thread, Throwable t) {
        prefs.edit().putInt("process_crashes", prefs.getInt("process_crashes", 0) + 1).apply();
        error("UNCAUGHT@" + safe(thread), t);
    }

    public String lastError() { return prefs.getString("last_error", ""); }
    public long lastSuccessAt() { return prefs.getLong("last_success_at", 0); }
    public long lastRelayMs() { return prefs.getLong("last_relay_ms", 0); }
    public int consecutiveFailures() { return prefs.getInt("consecutive_failures", 0); }
    public String state() { return prefs.getString("state", "IDLE"); }

    public JSONObject snapshotJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("state", state());
            o.put("last_success_at", lastSuccessAt());
            o.put("last_relay_ms", lastRelayMs());
            o.put("last_error", lastError());
            o.put("last_error_at", prefs.getLong("last_error_at", 0));
            o.put("consecutive_failures", consecutiveFailures());
            o.put("failure_count", prefs.getInt("failure_count", 0));
            o.put("command_count", prefs.getInt("command_count", 0));
            o.put("last_command", prefs.getString("last_command", ""));
            o.put("last_command_at", prefs.getLong("last_command_at", 0));
            o.put("service_restarts", prefs.getInt("service_restarts", 0));
            o.put("process_crashes", prefs.getInt("process_crashes", 0));
            o.put("last_event", prefs.getString("last_event", ""));
        } catch (Exception ignored) {}
        return o;
    }

    public String snapshotText() {
        StringBuilder b = new StringBuilder();
        b.append("State: ").append(state()).append('\n');
        b.append("Last relay: ").append(lastRelayMs()).append(" ms\n");
        b.append("Consecutive failures: ").append(consecutiveFailures()).append('\n');
        b.append("Last error: ").append(lastError().isEmpty() ? "none" : lastError()).append('\n');
        b.append("Commands completed: ").append(prefs.getInt("command_count", 0)).append('\n');
        b.append("Service restarts: ").append(prefs.getInt("service_restarts", 0)).append('\n');
        b.append("Process crashes: ").append(prefs.getInt("process_crashes", 0)).append("\n\n");
        b.append(prefs.getString("event_log", ""));
        return b.toString().trim();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}