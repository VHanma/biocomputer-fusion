package com.hanma.echocore;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DocumentImporter {
    public static class Result {
        public String name = "file";
        public String mime = "application/octet-stream";
        public long sizeBytes = 0;
        public int chars = 0;
        public int chunks = 0;
        public long sourceId = 0;
        public boolean textExtracted = false;
        public String note = "";
    }

    private final Context context;
    private final BrainDatabase db;
    private final SourceCatalog catalog;

    public DocumentImporter(Context context, BrainDatabase db, SourceCatalog catalog) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.catalog = catalog;
        PDFBoxResourceLoader.init(this.context);
    }

    public Result ingest(Uri uri) throws Exception {
        Result r = metadata(uri);
        String text = extract(uri, r.mime, r.name);
        if (text == null) text = "";
        text = normalize(text);
        r.chars = text.length();
        r.textExtracted = !text.trim().isEmpty();

        long sourceId = catalog.startSource(r.name, r.mime, uri.toString(), r.sizeBytes);
        r.sourceId = sourceId;
        String kind = kindForMime(r.mime, r.name);
        String preview = r.textExtracted ? trim(text, 700) : describeBinary(uri, r);
        String sourceText = "SOURCE: " + r.name + "\n" +
                "Kind: " + kind + "\n" +
                "Type: " + r.mime + "\n" +
                (r.sizeBytes > 0 ? "Size: " + humanBytes(r.sizeBytes) + "\n" : "") +
                (r.textExtracted ? "Readable characters: " + r.chars + "\n\nPreview:\n" + preview : "\n" + preview);
        long parent = db.addMemoryRich(sourceText, "SOURCE",
                "source, " + safeTag(r.name) + ", " + kind.toLowerCase(Locale.US),
                7, 0, 9, 7, false);

        if (r.textExtracted) {
            List<String> chunks = chunk(text, 3000, 260);
            int part = 1;
            for (String piece : chunks) {
                String tags = "document, source:" + safeTag(r.name) + ", part:" + part;
                catalog.addChunk(sourceId, part, piece);
                long id = db.addMemoryRich(piece, "KNOWLEDGE", tags, 6, 0, 8, 6, false);
                if (parent > 0 && id > 0) db.reinforceAssociation(parent, id, "SOURCE-PART", 3);
                part++;
            }
            r.chunks = chunks.size();
            r.note = "Full readable text indexed into " + r.chunks + " linked memory chunks.";
        } else {
            r.chunks = 0;
            r.note = "Stored as a sensory/source attachment. EchoCore indexed its metadata; text extraction is not available for this file type yet.";
        }
        catalog.finishSource(sourceId, r.chars, r.chunks, parent);
        return r;
    }

    private Result metadata(Uri uri) {
        Result r = new Result();
        ContentResolver cr = context.getContentResolver();
        String mime = cr.getType(uri);
        if (mime != null && !mime.trim().isEmpty()) r.mime = mime;
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && c.getString(ni) != null) r.name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) r.sizeBytes = c.getLong(si);
            }
        } catch (Exception ignored) {}
        if ("application/octet-stream".equals(r.mime)) r.mime = mimeFromName(r.name);
        return r;
    }

    private String extract(Uri uri, String mime, String name) throws Exception {
        String ext = extension(name);
        if ("application/pdf".equals(mime) || "pdf".equals(ext)) return extractPdf(uri);
        if (isZipDocument(mime, ext)) return extractZipText(uri, ext);
        if (isText(mime, ext)) return extractPlain(uri, ext);
        return "";
    }

    private String extractPdf(Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Could not open PDF.");
            try (PDDocument doc = PDDocument.load(in)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(doc);
            }
        }
    }

    private String extractPlain(Uri uri, String ext) throws Exception {
        byte[] data = readAll(context.getContentResolver().openInputStream(uri), 128L * 1024L * 1024L);
        String s = decodeText(data);
        if ("html".equals(ext) || "htm".equals(ext) || "xml".equals(ext) || "svg".equals(ext)) return xmlToText(s);
        if ("rtf".equals(ext)) return rtfToText(s);
        return s;
    }

    private String extractZipText(Uri uri, String ext) throws Exception {
        StringBuilder out = new StringBuilder();
        long extractedBytes = 0;
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName().toLowerCase(Locale.US);
                if (!wantedZipEntry(n, ext)) continue;
                byte[] bytes = readAll(zip, 12L * 1024L * 1024L);
                extractedBytes += bytes.length;
                if (extractedBytes > 96L * 1024L * 1024L) throw new Exception("Document expands beyond EchoCore's 96 MB text safety window.");
                String part = decodeText(bytes);
                if (n.endsWith(".xml") || n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) part = xmlToText(part);
                if (!part.trim().isEmpty()) out.append("\n\n").append(part.trim());
            }
        }
        return out.toString();
    }

    private boolean wantedZipEntry(String n, String ext) {
        if ("docx".equals(ext)) return n.equals("word/document.xml") || n.startsWith("word/header") || n.startsWith("word/footer") || n.startsWith("word/footnotes") || n.startsWith("word/endnotes");
        if ("pptx".equals(ext)) return n.startsWith("ppt/slides/slide") && n.endsWith(".xml");
        if ("xlsx".equals(ext)) return n.equals("xl/sharedstrings.xml") || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"));
        if ("odt".equals(ext) || "ods".equals(ext) || "odp".equals(ext)) return n.equals("content.xml") || n.equals("styles.xml");
        if ("epub".equals(ext)) return n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".ncx");
        return n.endsWith(".xml") || n.endsWith(".txt") || n.endsWith(".html") || n.endsWith(".xhtml");
    }

    private String describeBinary(Uri uri, Result r) {
        String kind = kindForMime(r.mime, r.name);
        if ("AUDIO".equals(kind) || "VIDEO".equals(kind)) {
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(context, uri);
                String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                mmr.release();
                StringBuilder b = new StringBuilder("Media attachment indexed.");
                if (title != null) b.append("\nTitle: ").append(title);
                if (artist != null) b.append("\nArtist: ").append(artist);
                if (duration != null) b.append("\nDuration: ").append(formatDuration(duration));
                return b.toString();
            } catch (Exception ignored) {}
        }
        if ("IMAGE".equals(kind)) return "Image attachment indexed as a visual source. Text/scene understanding can be layered onto this source in a later perception module.";
        return "File attachment indexed as a source. EchoCore keeps its URI and metadata even when this version has no text parser for the format.";
    }

    public static List<String> chunk(String text, int target, int overlap) {
        ArrayList<String> out = new ArrayList<>();
        String s = normalize(text);
        int n = s.length();
        int start = 0;
        while (start < n) {
            int idealEnd = Math.min(n, start + target);
            int end = idealEnd;
            if (idealEnd < n) {
                int minBreak = Math.min(n, start + Math.max(900, target / 2));
                int p = lastBreak(s, minBreak, idealEnd);
                if (p > start) end = p;
            }
            String piece = s.substring(start, end).trim();
            if (!piece.isEmpty()) out.add(piece);
            if (end >= n) break;
            int next = Math.max(start + 1, end - overlap);
            while (next < n && next > start && !Character.isWhitespace(s.charAt(next - 1))) next++;
            start = Math.min(next, n);
        }
        return out;
    }

    private static int lastBreak(String s, int min, int max) {
        int best = -1;
        for (int i = max - 1; i >= min; i--) {
            char c = s.charAt(i);
            if (c == '\n') return i + 1;
            if (best < 0 && (c == '.' || c == '!' || c == '?') && i + 1 < s.length() && Character.isWhitespace(s.charAt(i + 1))) best = i + 1;
        }
        return best;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{3,}", "  ")
                .replaceAll("\\n{4,}", "\n\n\n").trim();
    }

    private static String xmlToText(String xml) {
        String s = xml.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)</(p|div|h[1-6]|tr|li|w:p|a:p|text:p|text:h)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        return unescapeXml(s);
    }

    private static String unescapeXml(String s) {
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static String rtfToText(String s) {
        return s.replaceAll("\\\\'[0-9a-fA-F]{2}", " ")
                .replaceAll("\\\\[a-zA-Z]+-?\\d* ?", " ")
                .replaceAll("[{}]", " ");
    }

    private static byte[] readAll(InputStream in, long max) throws Exception {
        if (in == null) throw new Exception("Could not open selected file.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max) throw new Exception("File text stream exceeds " + humanBytes(max) + ".");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String decodeText(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF)
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE)
            return new String(data, Charset.forName("UTF-16LE"));
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF)
            return new String(data, Charset.forName("UTF-16BE"));
        return new String(data, StandardCharsets.UTF_8);
    }

    private static boolean isText(String mime, String ext) {
        return mime != null && (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")) ||
                ext.matches("txt|md|markdown|csv|tsv|json|xml|html|htm|rtf|log|ini|yaml|yml|java|kt|py|js|ts|css|sql|svg|srt|vtt|ass|ssa|tex|rst|toml|properties|gradle|sh|bat|ps1|c|cpp|h|hpp|go|rs|swift");
    }

    private static boolean isZipDocument(String mime, String ext) {
        return ext.matches("docx|pptx|xlsx|odt|ods|odp|epub|zip") ||
                (mime != null && (mime.contains("openxmlformats") || mime.contains("opendocument") || mime.contains("epub")));
    }

    private static String kindForMime(String mime, String name) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        String ext = extension(name);
        if (m.startsWith("image/") || ext.matches("png|jpg|jpeg|webp|gif|bmp|heic|heif")) return "IMAGE";
        if (m.startsWith("audio/") || ext.matches("mp3|wav|flac|m4a|aac|ogg|opus")) return "AUDIO";
        if (m.startsWith("video/") || ext.matches("mp4|mkv|webm|mov|avi|m4v")) return "VIDEO";
        if ("pdf".equals(ext) || m.contains("pdf")) return "PDF";
        if (ext.matches("docx|odt|rtf") || m.contains("word") || m.contains("document")) return "DOCUMENT";
        if (ext.matches("pptx|odp") || m.contains("presentation")) return "PRESENTATION";
        if (ext.matches("xlsx|ods|csv|tsv") || m.contains("spreadsheet") || m.contains("excel")) return "SPREADSHEET";
        if ("epub".equals(ext)) return "EBOOK";
        if (isText(m, ext)) return "TEXT";
        return "FILE";
    }

    private static String mimeFromName(String name) {
        String e = extension(name);
        if ("pdf".equals(e)) return "application/pdf";
        if (e.matches("txt|md|csv|log")) return "text/plain";
        if ("docx".equals(e)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if ("pptx".equals(e)) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if ("xlsx".equals(e)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if ("epub".equals(e)) return "application/epub+zip";
        return "application/octet-stream";
    }

    private static String extension(String name) {
        if (name == null) return "";
        int p = name.lastIndexOf('.');
        return p >= 0 && p < name.length() - 1 ? name.substring(p + 1).toLowerCase(Locale.US) : "";
    }

    private static String safeTag(String s) {
        if (s == null) return "source";
        String x = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_");
        return x.length() > 48 ? x.substring(0, 48) : x;
    }

    private static String trim(String s, int n) {
        s = s == null ? "" : s.trim();
        return s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)).trim() + "…";
    }

    private static String humanBytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static String formatDuration(String ms) {
        try {
            long sec = Long.parseLong(ms) / 1000L;
            long h = sec / 3600L, m = (sec % 3600L) / 60L, s = sec % 60L;
            return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
        } catch (Exception e) { return ms + " ms"; }
    }
}
