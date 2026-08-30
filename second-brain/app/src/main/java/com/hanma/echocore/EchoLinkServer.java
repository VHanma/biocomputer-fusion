package com.hanma.echocore;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoLinkServer {
    private static final String TAG = "EchoLink";
    private static final int DEFAULT_PORT = 18432;
    private static volatile EchoLinkServer INSTANCE;

    private final Context context;
    private final BrainDatabase brain;
    private final BrainEngine engine;
    private final SourceCatalog sources;
    private final CognitiveStore store;
    private final CognitiveOrchestrator omega;
    private final SecurePrefs prefs;
    private final ModelGateway model;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running = false;
    private volatile ServerSocket server;
    private volatile Thread loopThread;

    public static synchronized EchoLinkServer getInstance(Context context, BrainDatabase brain, BrainEngine engine,
                                                          SourceCatalog sources, CognitiveStore store,
                                                          CognitiveOrchestrator omega, SecurePrefs prefs,
                                                          ModelGateway model) {
        if (INSTANCE == null) {
            INSTANCE = new EchoLinkServer(context.getApplicationContext(), brain, engine, sources, store, omega, prefs, model);
        }
        return INSTANCE;
    }

    private EchoLinkServer(Context context, BrainDatabase brain, BrainEngine engine,
                           SourceCatalog sources, CognitiveStore store,
                           CognitiveOrchestrator omega, SecurePrefs prefs,
                           ModelGateway model) {
        this.context = context;
        this.brain = brain;
        this.engine = engine;
        this.sources = sources;
        this.store = store;
        this.omega = omega;
        this.prefs = prefs;
        this.model = model;
    }

    public synchronized void start() throws Exception {
        if (running) return;
        int port = port();
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        server = s;
        running = true;
        prefs.putBool("echolink_enabled", true);
        loopThread = new Thread(this::runLoop, "EchoLinkServer");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    public synchronized void stop() {
        running = false;
        prefs.putBool("echolink_enabled", false);
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        server = null;
        Thread t = loopThread;
        if (t != null) t.interrupt();
        loopThread = null;
    }

    public boolean isRunning() { return running; }
    public String endpoint() { return "http://127.0.0.1:" + port(); }

    public int port() {
        try {
            int p = Integer.parseInt(prefs.get("echolink_port", String.valueOf(DEFAULT_PORT)).trim());
            return p >= 1024 && p <= 65535 ? p : DEFAULT_PORT;
        } catch (Exception e) { return DEFAULT_PORT; }
    }

    public void setPort(int port) {
        int p = port >= 1024 && port <= 65535 ? port : DEFAULT_PORT;
        prefs.put("echolink_port", String.valueOf(p));
    }

    public String token() {
        String t = prefs.getSecret("echolink_token");
        if (t == null || t.trim().isEmpty()) {
            t = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            prefs.putSecret("echolink_token", t);
        }
        return t;
    }

    public void regenerateToken() {
        prefs.putSecret("echolink_token", UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    public boolean permRead() { return prefs.getBool("echolink_perm_read", true); }
    public boolean permWrite() { return prefs.getBool("echolink_perm_write", false); }
    public boolean permSources() { return prefs.getBool("echolink_perm_sources", false); }
    public boolean permProjects() { return prefs.getBool("echolink_perm_projects", false); }

    public void setPermissions(boolean read, boolean write, boolean sources, boolean projects) {
        prefs.putBool("echolink_perm_read", read);
        prefs.putBool("echolink_perm_write", write);
        prefs.putBool("echolink_perm_sources", sources);
        prefs.putBool("echolink_perm_projects", projects);
    }

    public JSONObject statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("running", running);
            o.put("endpoint", endpoint());
            o.put("token", token());
            o.put("permissions", new JSONObject()
                    .put("read", permRead())
                    .put("write", permWrite())
                    .put("sources", permSources())
                    .put("projects", permProjects()));
            JSONArray caps = new JSONArray();
            caps.put("GET /ping");
            caps.put("GET /capabilities");
            caps.put("POST /brain/answer");
            caps.put("GET /brain/search?q=");
            caps.put("POST /memory/add");
            caps.put("POST /source/import_text");
            caps.put("GET /source/search?q=");
            caps.put("GET /projects");
            caps.put("POST /project/add");
            caps.put("POST /task/add");
            caps.put("POST /consolidate");
            o.put("commands", caps);
        } catch (Exception ignored) {}
        return o;
    }

    private void runLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                pool.execute(() -> handle(socket));
            } catch (Exception e) {
                if (running) Log.e(TAG, "accept failed", e);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(15000);
            Request req = readRequest(s.getInputStream());
            if (req == null) {
                writeJson(s.getOutputStream(), 400, error("bad_request", "Could not parse request."));
                return;
            }
            JSONObject body = req.body.isEmpty() ? new JSONObject() : new JSONObject(req.body);
            String token = firstNonEmpty(req.header("x-echocore-token"), req.query.get("token"), body.optString("token", ""));
            if (!"/ping".equals(req.path) && !validToken(token)) {
                writeJson(s.getOutputStream(), 401, error("unauthorized", "Missing or invalid EchoLink token."));
                return;
            }
            JSONObject response = route(req, body);
            int code = response.optBoolean("ok", true) ? 200 : response.optInt("status", 400);
            writeJson(s.getOutputStream(), code, response);
        } catch (Exception e) {
            try { writeJson(socket.getOutputStream(), 500, error("server_error", trim(e.getMessage(), 260))); }
            catch (Exception ignored) {}
        }
    }

    private JSONObject route(Request req, JSONObject body) throws Exception {
        if ("/ping".equals(req.path)) return ok().put("message", "EchoLink online").put("app", "EchoCore Omega").put("running", running);
        if ("/capabilities".equals(req.path)) return ok().put("result", statusJson());
        if ("/brain/search".equals(req.path)) {
            if (!permRead()) return denied("Read permission is disabled.");
            return ok().put("result", memoriesToJson(brain.search(req.query.get("q"), limit(req.query.get("limit"), 8))));
        }
        if ("/brain/answer".equals(req.path)) {
            if (!permRead()) return denied("Read permission is disabled.");
            String q = body.optString("question", body.optString("q", "")).trim();
            String mode = body.optString("mode", "DEEP").trim().toUpperCase(Locale.US);
            boolean useModel = body.optBoolean("use_model", false);
            if (q.isEmpty()) return error("bad_request", "Missing question.").put("status", 400);
            store.addTurn("EXTERNAL", q, mode);
            String answer = answer(q, mode, useModel);
            store.addTurn("OMEGA", answer, mode);
            return ok().put("mode", mode).put("answer", answer);
        }
        if ("/memory/add".equals(req.path)) {
            if (!permWrite()) return denied("Write permission is disabled.");
            String text = body.optString("text", "").trim();
            if (text.isEmpty()) return error("bad_request", "Missing text.").put("status", 400);
            String type = body.optString("type", "THOUGHT");
            String tags = body.optString("tags", engine.autoTags(text));
            long id = brain.addMemoryRich(text, type, tags,
                    clamp(body.optInt("importance", engine.inferImportance(text)), 1, 10),
                    clamp(body.optInt("valence", engine.inferValence(text)), -5, 5),
                    clamp(body.optInt("confidence", 7), 1, 10),
                    clamp(body.optInt("novelty", engine.inferNovelty(text)), 1, 10),
                    body.optBoolean("active", false));
            return ok().put("memory_id", id).put("tags", tags);
        }
        if ("/source/import_text".equals(req.path)) {
            if (!permSources()) return denied("Source permission is disabled.");
            String name = body.optString("name", "External source").trim();
            String text = body.optString("text", "");
            String mime = body.optString("mime", "text/plain");
            if (text.trim().isEmpty()) return error("bad_request", "Missing text.").put("status", 400);
            return ok().put("result", importTextSource(name, mime, text));
        }
        if ("/source/search".equals(req.path)) {
            if (!permSources()) return denied("Source permission is disabled.");
            JSONArray arr = new JSONArray();
            for (String[] row : sources.searchChunks(req.query.get("q"), limit(req.query.get("limit"), 8))) {
                arr.put(new JSONObject().put("source", row[0]).put("part", parseInt(row[1], 0)).put("text", row[2]));
            }
            return ok().put("result", arr);
        }
        if ("/projects".equals(req.path)) {
            if (!permProjects()) return denied("Project permission is disabled.");
            JSONArray arr = new JSONArray();
            for (String[] p : store.projects()) {
                long id = parseLong(p[0], 0);
                JSONObject item = new JSONObject().put("id", id).put("title", p[1]).put("goal", p[2]).put("status", p[3]);
                JSONArray tasks = new JSONArray();
                for (String[] t : store.tasks(id)) tasks.put(new JSONObject().put("id", parseLong(t[0], 0)).put("text", t[1]).put("status", t[2]).put("priority", parseInt(t[3], 0)));
                item.put("tasks", tasks);
                arr.put(item);
            }
            return ok().put("result", arr);
        }
        if ("/project/add".equals(req.path)) {
            if (!permProjects()) return denied("Project permission is disabled.");
            String title = body.optString("title", "").trim();
            String goal = body.optString("goal", "").trim();
            if (title.isEmpty()) return error("bad_request", "Missing project title.").put("status", 400);
            long id = store.addProject(title, goal);
            int pr = 8;
            for (String step : omega.planSteps(goal.isEmpty() ? title : goal)) store.addTask(id, step, pr--);
            brain.addMemoryRich(goal.isEmpty() ? title : goal, "GOAL", "project, " + engine.autoTags(title + " " + goal), 9, 1, 8, 6, true);
            return ok().put("project_id", id);
        }
        if ("/task/add".equals(req.path)) {
            if (!permProjects()) return denied("Project permission is disabled.");
            long projectId = body.optLong("project_id", 0);
            String text = body.optString("text", "").trim();
            if (projectId <= 0 || text.isEmpty()) return error("bad_request", "Missing project_id or text.").put("status", 400);
            return ok().put("task_id", store.addTask(projectId, text, clamp(body.optInt("priority", 5), 1, 10)));
        }
        if ("/consolidate".equals(req.path)) {
            if (!permWrite()) return denied("Write permission is disabled.");
            boolean full = body.optBoolean("full_autopilot", false);
            String result = full ? omega.autopilotCycle() : engine.consolidate();
            store.addTurn("OMEGA", result, full ? "AUTOPILOT" : "CONSOLIDATE");
            return ok().put("result", result);
        }
        return error("not_found", "Unknown endpoint: " + req.path).put("status", 404);
    }

    private String answer(String q, String mode, boolean useModel) {
        try {
            if (useModel && model.enabled()) {
                String context = omega.buildGroundedContext(q, 16);
                String prompt = "QUESTION:\n" + q + "\n\nEVIDENCE:\n" + (context.isEmpty() ? "No directly retrieved evidence." : context) + "\n\nReturn the strongest grounded answer for mode " + mode + ".";
                String draft = model.complete(omega.systemPrompt(mode), prompt);
                if ("DEEP".equals(mode)) return model.complete(omega.systemPrompt(mode), omega.deepReviewPrompt(q, draft, context));
                return draft;
            }
        } catch (Exception ignored) {}
        return omega.localAnswer(q, mode);
    }

    private JSONObject importTextSource(String name, String mime, String text) throws Exception {
        String cleanName = name == null || name.trim().isEmpty() ? "External source" : name.trim();
        long sourceId = sources.startSource(cleanName, mime == null || mime.trim().isEmpty() ? "text/plain" : mime.trim(), "echolink://import_text/" + safeTag(cleanName), text.length());
        long parentId = brain.addMemoryRich("SOURCE: " + cleanName + "\nTYPE: TEXT\nSIZE: " + text.length() + " chars\nORIGIN: EchoLink import", "SOURCE", "source, echolink, " + safeTag(cleanName), 8, 0, 8, 5, false);
        int part = 1, chars = 0;
        for (String piece : chunk(text, 1200)) {
            chars += piece.length();
            sources.addChunk(sourceId, part, piece);
            brain.addMemoryRich("SOURCE " + cleanName + " · part " + part + "\n" + piece, "REFERENCE", "document, source:" + safeTag(cleanName) + ", part:" + part, 7, 0, 8, 4, false);
            part++;
        }
        sources.finishSource(sourceId, chars, Math.max(0, part - 1), parentId);
        return new JSONObject().put("source_id", sourceId).put("name", cleanName).put("chars", chars).put("chunks", Math.max(0, part - 1));
    }

    private static List<String> chunk(String text, int target) {
        ArrayList<String> out = new ArrayList<>();
        String s = text == null ? "" : text.replace("\r\n", "\n").trim();
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(s.length(), i + target);
            if (end < s.length()) {
                int cut = Math.max(s.lastIndexOf("\n\n", end), Math.max(s.lastIndexOf('\n', end), s.lastIndexOf(' ', end)));
                if (cut > i + target / 2) end = cut;
            }
            String piece = s.substring(i, end).trim();
            if (!piece.isEmpty()) out.add(piece);
            i = Math.max(end, i + 1);
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
        return out;
    }

    private static JSONArray memoriesToJson(List<MemoryNode> list) throws Exception {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (MemoryNode m : list) arr.put(new JSONObject().put("id", m.id).put("text", m.text).put("type", m.type).put("tags", m.tags).put("importance", m.importance).put("valence", m.valence).put("confidence", m.confidence).put("novelty", m.novelty).put("active", m.active).put("pinned", m.pinned).put("created_at", m.createdAt));
        return arr;
    }

    private static Request readRequest(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int a=-1,b=-1,c=-1,d;
        while ((d=in.read())!=-1) {
            out.write(d);
            if (a=='\r'&&b=='\n'&&c=='\r'&&d=='\n') break;
            a=b;b=c;c=d;
            if (out.size()>32768) break;
        }
        String header = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        int sep = header.indexOf("\r\n\r\n");
        if (sep < 0) return null;
        String[] lines = header.substring(0, sep).split("\r\n");
        if (lines.length == 0) return null;
        String[] first = lines[0].split(" ");
        if (first.length < 2) return null;
        Request req = new Request();
        req.method = first[0].trim().toUpperCase(Locale.US);
        String rawPath = first[1].trim();
        int qx = rawPath.indexOf('?');
        req.path = qx >= 0 ? rawPath.substring(0, qx) : rawPath;
        if (qx >= 0) req.query = parseQuery(rawPath.substring(qx + 1));
        for (int i=1;i<lines.length;i++) { int x=lines[i].indexOf(':'); if(x>0) req.headers.put(lines[i].substring(0,x).trim().toLowerCase(Locale.US),lines[i].substring(x+1).trim()); }
        int len=parseInt(req.header("content-length"),0);
        if(len>0){byte[] body=new byte[len];int off=0;while(off<len){int n=in.read(body,off,len-off);if(n<0)break;off+=n;}req.body=new String(body,0,off,StandardCharsets.UTF_8);}else req.body="";
        return req;
    }

    private static Map<String,String> parseQuery(String raw) throws Exception {
        HashMap<String,String> out=new HashMap<>();
        if(raw==null||raw.isEmpty())return out;
        for(String p:raw.split("&")){int x=p.indexOf('=');String k=x>=0?p.substring(0,x):p;String v=x>=0?p.substring(x+1):"";out.put(URLDecoder.decode(k,"UTF-8"),URLDecoder.decode(v,"UTF-8"));}
        return out;
    }

    private boolean validToken(String provided){String actual=token();return provided!=null&&!provided.isEmpty()&&actual.equals(provided);}
    private static void writeJson(OutputStream out,int code,JSONObject body)throws Exception{byte[] bytes=body.toString(2).getBytes(StandardCharsets.UTF_8);String status=code==200?"OK":code==401?"Unauthorized":code==403?"Forbidden":code==404?"Not Found":code==500?"Internal Server Error":"Bad Request";String header="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n";out.write(header.getBytes(StandardCharsets.UTF_8));out.write(bytes);out.flush();}
    private static JSONObject ok()throws Exception{return new JSONObject().put("ok",true);}
    private static JSONObject denied(String message)throws Exception{return error("forbidden",message).put("status",403);}
    private static JSONObject error(String code,String message){try{return new JSONObject().put("ok",false).put("error",code).put("message",message==null?"":message);}catch(Exception e){return new JSONObject();}}
    private static String safeTag(String s){return(s==null?"source":s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$",""));}
    private static int limit(String x,int fallback){return Math.max(1,Math.min(50,parseInt(x,fallback)));}
    private static int parseInt(String s,int fallback){try{return Integer.parseInt(s==null?"":s.trim());}catch(Exception e){return fallback;}}
    private static long parseLong(String s,long fallback){try{return Long.parseLong(s==null?"":s.trim());}catch(Exception e){return fallback;}}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String firstNonEmpty(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v.trim();return"";}
    private static String trim(String s,int n){if(s==null)return"";s=s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}

    private static class Request {
        String method="GET",path="/",body="";
        Map<String,String> query=new HashMap<>(),headers=new HashMap<>();
        String header(String key){return headers.get(key==null?"":key.toLowerCase(Locale.US));}
    }
}
