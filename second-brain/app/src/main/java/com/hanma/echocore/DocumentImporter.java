package com.hanma.echocore;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Memory-bounded source ingestion. */
public class DocumentImporter {
    public static class Result {
        public String name="file";
        public String mime="application/octet-stream";
        public long sizeBytes;
        public int chars;
        public int chunks;
        public long sourceId;
        public boolean textExtracted;
        public boolean partial;
        public String note="";
    }

    private static final int TARGET_CHARS=2800;
    private static final int OVERLAP_CHARS=180;
    private static final int IO_BUF=16*1024;
    private static final long MAX_PDF_BYTES=256L*1024L*1024L;
    private static final long MAX_PLAIN_CHARS=64L*1024L*1024L;
    private static final long MAX_ZIP_ENTRY_CHARS=10L*1024L*1024L;
    private static final long MAX_ZIP_TOTAL_CHARS=64L*1024L*1024L;
    private static final int MAX_ZIP_ENTRIES=2500;
    private static final long LOW_MEMORY_STOP=12L*1024L*1024L;

    private final Context context;
    private final BrainDatabase db;
    private final SourceCatalog catalog;

    public DocumentImporter(Context context,BrainDatabase db,SourceCatalog catalog){
        this.context=context.getApplicationContext();
        this.db=db;
        this.catalog=catalog;
        PDFBoxResourceLoader.init(this.context);
    }

    public Result ingest(Uri uri) throws Exception {
        if(uri==null)throw new Exception("No document URI supplied.");
        Result r=metadata(uri);
        String ext=extension(r.name);
        String kind=kindForMime(r.mime,r.name);
        if(r.sizeBytes>0&&"PDF".equals(kind)&&r.sizeBytes>MAX_PDF_BYTES)
            throw new Exception("PDF is larger than the 256 MB safe parser window. Split it or import its text export.");

        long sourceId=catalog.startSource(r.name,r.mime,uri.toString(),r.sizeBytes);
        r.sourceId=sourceId;
        String parentText="SOURCE: "+r.name+"\nKind: "+kind+"\nType: "+r.mime+"\n"+
                (r.sizeBytes>0?"Size: "+humanBytes(r.sizeBytes)+"\n":"")+
                "Import mode: Nexus bounded streaming";
        long parent=db.addMemoryRich(parentText,"SOURCE",
                "source, "+safeTag(r.name)+", "+kind.toLowerCase(Locale.US),7,0,9,7,false);
        ChunkSink sink=new ChunkSink(sourceId,parent,r.name);

        Exception parseFailure=null;
        try{
            if("PDF".equals(kind))extractPdf(uri,sink);
            else if(isZipDocument(r.mime,ext))extractZipText(uri,ext,sink);
            else if(isText(r.mime,ext))extractPlain(uri,ext,sink);
            else r.note=describeBinary(uri,r);
        }catch(InterruptedException stop){
            Thread.currentThread().interrupt();
            parseFailure=new Exception("Import cancelled.");
        }catch(Exception e){
            parseFailure=e;
        }

        sink.finish();
        r.chars=(int)Math.min(Integer.MAX_VALUE,sink.acceptedChars);
        r.chunks=sink.chunks;
        r.textExtracted=r.chunks>0;
        catalog.finishSource(sourceId,r.chars,r.chunks,parent);

        if(parseFailure!=null){
            if(r.chunks==0)throw parseFailure;
            r.partial=true;
            r.note="Partial import preserved: "+safeMessage(parseFailure)+" · "+r.chunks+" chunks were safely indexed.";
        }else if(r.textExtracted){
            r.note="Streamed "+r.chars+" readable characters into "+r.chunks+" linked chunks without materializing the whole document in RAM.";
        }else if(r.note==null||r.note.isEmpty()){
            r.note="Attachment indexed. No readable text parser was selected for this format.";
        }

        String summary="IMPORT RESULT: "+r.name+"\n"+r.note;
        long summaryId=db.addMemoryRich(summary,r.partial?"OBSERVATION":"REFERENCE",
                "source-import, "+safeTag(r.name),6,r.partial?-1:0,9,5,false);
        if(parent>0&&summaryId>0)db.reinforceAssociation(parent,summaryId,"IMPORT-RESULT",2);
        return r;
    }

    private void extractPdf(Uri uri,ChunkSink sink) throws Exception {
        assertMemory("before PDF copy");
        File temp=File.createTempFile("echocore_pdf_",".pdf",context.getCacheDir());
        try{
            copyUriToFile(uri,temp,MAX_PDF_BYTES);
            assertMemory("before PDF parse");
            MemoryUsageSetting mus=MemoryUsageSetting.setupMixed(8L*1024L*1024L,512L*1024L*1024L).setTempDir(context.getCacheDir());
            try(PDDocument doc=PDDocument.load(temp,mus)){
                int pages=doc.getNumberOfPages();
                PDFTextStripper stripper=new PDFTextStripper();
                stripper.setSortByPosition(true);
                SinkWriter writer=new SinkWriter(sink);
                for(int page=1;page<=pages;page++){
                    checkCancelled();
                    assertMemory("PDF page "+page);
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    stripper.writeText(doc,writer);
                    writer.flush();
                }
            }
        }finally{
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void extractPlain(Uri uri,String ext,ChunkSink sink) throws Exception {
        try(InputStream raw=context.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new Exception("Could not open selected file.");
            Reader reader=readerWithBom(raw);
            if("html".equals(ext)||"htm".equals(ext)||"xml".equals(ext)||"svg".equals(ext))streamMarkup(reader,sink,MAX_PLAIN_CHARS);
            else if("rtf".equals(ext))streamRtf(reader,sink,MAX_PLAIN_CHARS);
            else streamReader(reader,sink,MAX_PLAIN_CHARS);
        }
    }

    private void extractZipText(Uri uri,String ext,ChunkSink sink) throws Exception {
        long totalChars=0;
        int entries=0;
        try(InputStream raw=context.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new Exception("Could not open selected archive/document.");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw,IO_BUF))){
                ZipEntry e;
                while((e=zip.getNextEntry())!=null){
                    checkCancelled();
                    if(e.isDirectory())continue;
                    if(++entries>MAX_ZIP_ENTRIES)throw new Exception("Document contains too many archive entries.");
                    String n=e.getName().toLowerCase(Locale.US);
                    if(!wantedZipEntry(n,ext))continue;
                    if(e.getSize()>0&&e.getSize()>32L*1024L*1024L)throw new Exception("Archive entry is too large: "+trim(n,100));
                    EntryCounter counter=new EntryCounter(sink,MAX_ZIP_ENTRY_CHARS);
                    Reader reader=new BufferedReader(new InputStreamReader(zip,StandardCharsets.UTF_8),IO_BUF);
                    if(n.endsWith(".xml")||n.endsWith(".xhtml")||n.endsWith(".html")||n.endsWith(".htm")||n.endsWith(".ncx"))streamMarkup(reader,counter,MAX_ZIP_ENTRY_CHARS);
                    else streamReader(reader,counter,MAX_ZIP_ENTRY_CHARS);
                    totalChars+=counter.count;
                    if(totalChars>MAX_ZIP_TOTAL_CHARS)throw new Exception("Expanded readable text exceeds the 64 MB safety window.");
                    zip.closeEntry();
                    assertMemory("archive entry");
                }
            }
        }
    }

    private void streamReader(Reader reader,TextConsumer sink,long maxChars) throws Exception {
        char[] buf=new char[IO_BUF];
        long total=0;
        int n;
        while((n=reader.read(buf))!=-1){
            checkCancelled();
            total+=n;
            if(total>maxChars)throw new Exception("Readable text exceeds "+humanBytes(maxChars)+" safety window.");
            sink.accept(buf,0,n);
        }
    }

    private void streamMarkup(Reader reader,TextConsumer sink,long maxChars) throws Exception {
        char[] in=new char[IO_BUF];
        char[] out=new char[IO_BUF];
        int outN=0;
        boolean tag=false;
        long total=0;
        int n;
        while((n=reader.read(in))!=-1){
            checkCancelled();
            for(int i=0;i<n;i++){
                char c=in[i];
                if(tag){if(c=='>'){tag=false;if(outN<out.length)out[outN++]=' ';}continue;}
                if(c=='<'){tag=true;continue;}
                if(c=='&')c=' ';
                out[outN++]=c;
                total++;
                if(total>maxChars)throw new Exception("Expanded markup text exceeds "+humanBytes(maxChars)+" safety window.");
                if(outN==out.length){sink.accept(out,0,outN);outN=0;}
            }
        }
        if(outN>0)sink.accept(out,0,outN);
    }

    private void streamRtf(Reader reader,TextConsumer sink,long maxChars) throws Exception {
        char[] in=new char[IO_BUF];
        char[] out=new char[IO_BUF];
        int outN=0;
        boolean control=false;
        long total=0;
        int n;
        while((n=reader.read(in))!=-1){
            checkCancelled();
            for(int i=0;i<n;i++){
                char c=in[i];
                if(control){
                    if(c==' '||c=='\n'||c=='\r')control=false;
                    else if(c=='\\'||c=='{'||c=='}'){control=false;out[outN++]=c;total++;}
                    continue;
                }
                if(c=='\\'){control=true;continue;}
                if(c=='{'||c=='}')continue;
                out[outN++]=c;total++;
                if(total>maxChars)throw new Exception("RTF text exceeds "+humanBytes(maxChars)+" safety window.");
                if(outN==out.length){sink.accept(out,0,outN);outN=0;}
            }
        }
        if(outN>0)sink.accept(out,0,outN);
    }

    private Reader readerWithBom(InputStream raw) throws Exception {
        PushbackInputStream in=new PushbackInputStream(new BufferedInputStream(raw,IO_BUF),3);
        byte[] bom=new byte[3];
        int n=in.read(bom);
        Charset cs=StandardCharsets.UTF_8;
        int skip=0;
        if(n>=3&&(bom[0]&255)==0xEF&&(bom[1]&255)==0xBB&&(bom[2]&255)==0xBF)skip=3;
        else if(n>=2&&(bom[0]&255)==0xFF&&(bom[1]&255)==0xFE){cs=Charset.forName("UTF-16LE");skip=2;}
        else if(n>=2&&(bom[0]&255)==0xFE&&(bom[1]&255)==0xFF){cs=Charset.forName("UTF-16BE");skip=2;}
        if(n>skip)in.unread(bom,skip,n-skip);
        return new BufferedReader(new InputStreamReader(in,cs),IO_BUF);
    }

    private void copyUriToFile(Uri uri,File target,long maxBytes) throws Exception {
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(target)){
            if(in==null)throw new Exception("Could not open PDF.");
            byte[] buf=new byte[64*1024];
            long total=0;
            int n;
            while((n=in.read(buf))!=-1){
                checkCancelled();
                total+=n;
                if(total>maxBytes)throw new Exception("PDF exceeds "+humanBytes(maxBytes)+" safety window.");
                out.write(buf,0,n);
            }
            out.flush();
        }
    }

    private class ChunkSink implements TextConsumer {
        final long sourceId,parentId;
        final String sourceName;
        final StringBuilder pending=new StringBuilder(TARGET_CHARS+IO_BUF);
        long acceptedChars;
        int chunks;
        ChunkSink(long sourceId,long parentId,String sourceName){this.sourceId=sourceId;this.parentId=parentId;this.sourceName=sourceName;}
        @Override public void accept(char[] chars,int off,int len) throws Exception {
            int pos=off,end=off+len;
            while(pos<end){
                int take=Math.min(8192,end-pos);
                String clean=normalizeSmall(chars,pos,take);
                pos+=take;
                if(clean.isEmpty())continue;
                acceptedChars+=clean.length();
                pending.append(clean);
                emitReady();
            }
        }
        void emitReady() throws Exception {
            while(pending.length()>=TARGET_CHARS){
                int end=findBreak(pending,TARGET_CHARS/2,TARGET_CHARS);
                if(end<=OVERLAP_CHARS)end=TARGET_CHARS;
                String piece=pending.substring(0,end).trim();
                if(!piece.isEmpty())emit(piece);
                int keep=Math.min(OVERLAP_CHARS,end);
                String overlap=keep>0?pending.substring(end-keep,end):"";
                pending.delete(0,end);
                if(!overlap.trim().isEmpty())pending.insert(0,overlap);
            }
        }
        void finish() throws Exception {
            String piece=pending.toString().trim();
            pending.setLength(0);
            if(!piece.isEmpty())emit(piece);
        }
        void emit(String piece) throws Exception {
            checkCancelled();
            int part=++chunks;
            String tags="document, source:"+safeTag(sourceName)+", part:"+part;
            catalog.addChunk(sourceId,part,piece);
            long id=db.addMemoryRich(piece,"KNOWLEDGE",tags,6,0,8,6,false);
            if(parentId>0&&id>0)db.reinforceAssociation(parentId,id,"SOURCE-PART",3);
            if((part&15)==0)assertMemory("chunk "+part);
        }
    }

    private static class EntryCounter implements TextConsumer {
        final TextConsumer downstream;final long max;long count;
        EntryCounter(TextConsumer downstream,long max){this.downstream=downstream;this.max=max;}
        @Override public void accept(char[] c,int o,int n) throws Exception {count+=n;if(count>max)throw new Exception("Archive entry expands beyond the per-entry text window.");downstream.accept(c,o,n);}
    }

    private static class SinkWriter extends Writer {
        final TextConsumer sink;
        SinkWriter(TextConsumer sink){this.sink=sink;}
        @Override public void write(char[] cbuf,int off,int len) throws java.io.IOException {try{sink.accept(cbuf,off,len);}catch(Exception e){throw new java.io.IOException(e);}}
        @Override public void flush(){}
        @Override public void close(){}
    }

    private interface TextConsumer {void accept(char[] chars,int off,int len) throws Exception;}

    private Result metadata(Uri uri){
        Result r=new Result();ContentResolver cr=context.getContentResolver();String mime=cr.getType(uri);if(mime!=null&&!mime.trim().isEmpty())r.mime=mime;
        try(Cursor c=cr.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE);if(ni>=0&&c.getString(ni)!=null)r.name=c.getString(ni);if(si>=0&&!c.isNull(si))r.sizeBytes=c.getLong(si);}
        }catch(Exception ignored){}
        if("application/octet-stream".equals(r.mime))r.mime=mimeFromName(r.name);return r;
    }

    private String describeBinary(Uri uri,Result r){
        String kind=kindForMime(r.mime,r.name);
        if("AUDIO".equals(kind)||"VIDEO".equals(kind)){
            MediaMetadataRetriever mmr=null;
            try{mmr=new MediaMetadataRetriever();mmr.setDataSource(context,uri);String duration=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),title=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),artist=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);StringBuilder b=new StringBuilder("Media attachment indexed.");if(title!=null)b.append("\nTitle: ").append(title);if(artist!=null)b.append("\nArtist: ").append(artist);if(duration!=null)b.append("\nDuration: ").append(formatDuration(duration));return b.toString();}catch(Exception ignored){}finally{if(mmr!=null)try{mmr.release();}catch(Exception ignored){}}
        }
        if("IMAGE".equals(kind))return "Image attachment indexed as a visual source.";return "File attachment indexed with URI and metadata.";
    }

    private static String normalizeSmall(char[] src,int off,int len){
        StringBuilder b=new StringBuilder(len);boolean lastSpace=false;int newlines=0;
        for(int i=off;i<off+len;i++){char c=src[i];if(c==0)continue;if(c=='\r')c='\n';if(c=='\t'||c=='\u000B'||c=='\f')c=' ';if(c=='\n'){if(newlines<3)b.append('\n');newlines++;lastSpace=false;continue;}newlines=0;if(c==' '){if(lastSpace)continue;lastSpace=true;b.append(c);}else{lastSpace=false;b.append(c);}}
        return b.toString();
    }

    private static int findBreak(StringBuilder s,int min,int max){int m=Math.min(max,s.length()),lo=Math.min(min,m),sentence=-1;for(int i=m-1;i>=lo;i--){char c=s.charAt(i);if(c=='\n')return i+1;if(sentence<0&&(c=='.'||c=='!'||c=='?'))sentence=i+1;}return sentence>0?sentence:m;}

    private void assertMemory(String where) throws Exception {
        Runtime rt=Runtime.getRuntime();long used=rt.totalMemory()-rt.freeMemory(),available=rt.maxMemory()-used;
        if(available<24L*1024L*1024L){System.gc();used=rt.totalMemory()-rt.freeMemory();available=rt.maxMemory()-used;}
        if(available<LOW_MEMORY_STOP)throw new Exception("Memory guard paused import at "+where+" before Android could terminate the app.");
    }

    private static void checkCancelled() throws InterruptedException {if(Thread.currentThread().isInterrupted())throw new InterruptedException("Import cancelled");}
    private boolean wantedZipEntry(String n,String ext){if("docx".equals(ext))return n.equals("word/document.xml")||n.startsWith("word/header")||n.startsWith("word/footer")||n.startsWith("word/footnotes")||n.startsWith("word/endnotes");if("pptx".equals(ext))return n.startsWith("ppt/slides/slide")&&n.endsWith(".xml");if("xlsx".equals(ext))return n.equals("xl/sharedstrings.xml")||(n.startsWith("xl/worksheets/sheet")&&n.endsWith(".xml"));if("odt".equals(ext)||"ods".equals(ext)||"odp".equals(ext))return n.equals("content.xml")||n.equals("styles.xml");if("epub".equals(ext))return n.endsWith(".xhtml")||n.endsWith(".html")||n.endsWith(".htm")||n.endsWith(".ncx");return n.endsWith(".xml")||n.endsWith(".txt")||n.endsWith(".html")||n.endsWith(".xhtml")||n.endsWith(".md")||n.endsWith(".csv")||n.endsWith(".json");}
    private static boolean isText(String mime,String ext){return mime!=null&&(mime.startsWith("text/")||mime.contains("json")||mime.contains("xml"))||ext.matches("txt|md|markdown|csv|tsv|json|xml|html|htm|rtf|log|ini|yaml|yml|java|kt|py|js|ts|css|sql|svg|srt|vtt|ass|ssa|tex|rst|toml|properties|gradle|sh|bat|ps1|c|cpp|h|hpp|go|rs|swift");}
    private static boolean isZipDocument(String mime,String ext){return ext.matches("docx|pptx|xlsx|odt|ods|odp|epub|zip")||(mime!=null&&(mime.contains("openxmlformats")||mime.contains("opendocument")||mime.contains("epub")));}
    private static String kindForMime(String mime,String name){String m=mime==null?"":mime.toLowerCase(Locale.US),ext=extension(name);if(m.startsWith("image/")||ext.matches("png|jpg|jpeg|webp|gif|bmp|heic|heif"))return "IMAGE";if(m.startsWith("audio/")||ext.matches("mp3|wav|flac|m4a|aac|ogg|opus"))return "AUDIO";if(m.startsWith("video/")||ext.matches("mp4|mkv|webm|mov|avi|m4v"))return "VIDEO";if("pdf".equals(ext)||m.contains("pdf"))return "PDF";if(ext.matches("docx|odt|rtf")||m.contains("word")||m.contains("document"))return "DOCUMENT";if(ext.matches("pptx|odp")||m.contains("presentation"))return "PRESENTATION";if(ext.matches("xlsx|ods|csv|tsv")||m.contains("spreadsheet")||m.contains("excel"))return "SPREADSHEET";if("epub".equals(ext))return "BOOK";if(isText(m,ext))return "TEXT";return "FILE";}
    private static String extension(String n){if(n==null)return "";int q=n.lastIndexOf('.');return q>=0&&q<n.length()-1?n.substring(q+1).toLowerCase(Locale.US):"";}
    private static String mimeFromName(String n){String e=extension(n);if("pdf".equals(e))return "application/pdf";if("docx".equals(e))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";if("pptx".equals(e))return "application/vnd.openxmlformats-officedocument.presentationml.presentation";if("xlsx".equals(e))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";if("epub".equals(e))return "application/epub+zip";if(e.matches("txt|md|csv|json|xml|html|htm|rtf|log"))return "text/plain";return "application/octet-stream";}
    private static String safeTag(String s){String x=s==null?"source":s.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+","-");return trim(x,64);}
    private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,Math.max(1,n-1)).trim()+"…";}
    private static String safeMessage(Exception e){String m=e==null?"unknown import error":e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
    private static String humanBytes(long b){if(b>=1024L*1024L*1024L)return String.format(Locale.US,"%.1f GB",b/(1024.0*1024*1024));if(b>=1024L*1024L)return String.format(Locale.US,"%.1f MB",b/(1024.0*1024));if(b>=1024L)return String.format(Locale.US,"%.1f KB",b/1024.0);return b+" B";}
    private static String formatDuration(String ms){try{long s=Long.parseLong(ms)/1000;return String.format(Locale.US,"%d:%02d",s/60,s%60);}catch(Exception e){return ms+" ms";}}
}