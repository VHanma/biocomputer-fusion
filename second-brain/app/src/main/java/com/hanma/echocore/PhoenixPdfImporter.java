package com.hanma.echocore;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Phoenix v17 PDF importer.
 *
 * Reliability rule: never load a complete PDF object graph into the Java heap.
 * Android PdfRenderer opens the file natively; Phoenix renders and OCRs one page,
 * commits that page, recycles the bitmap, and only then advances.
 */
public class PhoenixPdfImporter {
    private static final int CHUNK=2700,OVERLAP=150;
    private static final long MAX_BYTES=512L*1024L*1024L;
    private static final double FIRST_PASS_PIXELS=1_450_000.0;
    private static final double RETRY_PIXELS=2_800_000.0;
    private final Context context;
    private final BrainDatabase brain;
    private final SourceCatalog catalog;
    private final AscendantStore asc;
    private final ImportStateStore state;

    public PhoenixPdfImporter(Context c,BrainDatabase b,SourceCatalog s,AscendantStore a,ImportStateStore q){
        context=c.getApplicationContext();brain=b;catalog=s;asc=a;state=q;
    }

    public DocumentImporter.Result ingest(Uri uri,String fingerprint,boolean recoveryMode) throws Exception {
        Meta meta=meta(uri);
        JSONObject job=state.job(uri.toString());
        long sourceId=job.optLong("source_id",0),parent=job.optLong("parent_memory_id",0);
        int pageDone=(int)job.optLong("checkpoint_value",0),chunks=(int)job.optLong("chunks",0);
        long chars=job.optLong("chars",0);

        if(sourceId<=0){
            sourceId=catalog.startSource(meta.name,meta.mime,uri.toString(),meta.size);
            parent=brain.addMemoryRich("SOURCE: "+meta.name+"\nKind: PDF\nType: "+meta.mime+"\nImport mode: Phoenix native page renderer + OCR checkpoints","SOURCE","source, pdf, phoenix",8,0,9,7,false);
            state.checkpoint(uri.toString(),"PDF_PAGE",0,sourceId,parent,0,0);
        }else catalog.markIngesting(sourceId);

        final long sid=sourceId,pid=parent;
        File tmp=File.createTempFile("phoenix_",".pdf",context.getCacheDir());
        VisionOcrEngine ocr=null;
        ParcelFileDescriptor pfd=null;
        PdfRenderer renderer=null;
        try{
            copy(uri,tmp);
            memoryGuard("before opening PDF");
            pfd=ParcelFileDescriptor.open(tmp,ParcelFileDescriptor.MODE_READ_ONLY);
            renderer=new PdfRenderer(pfd);
            int pages=renderer.getPageCount();
            if(pages<=0)throw new Exception("PDF contains no renderable pages");
            ocr=new VisionOcrEngine(context);
            asc.blackbox("PHOENIX","PDF_OPEN",meta.name+" · "+pages+" pages · page-stream mode",state.state());

            for(int page=Math.max(1,pageDone+1);page<=pages;page++){
                if(Thread.currentThread().isInterrupted()||state.cancelRequested())throw new InterruptedException("Phoenix cancelled");
                memoryGuard("before page "+page);

                String text="";
                Throwable firstErr=null;
                try{
                    text=ocrPage(renderer,page-1,ocr,FIRST_PASS_PIXELS);
                }catch(Throwable t){
                    firstErr=t;
                    asc.blackbox("PHOENIX","PAGE_OCR_RETRY",meta.name+" · page "+page+" · "+shortErr(t),state.state());
                    System.gc();
                }

                // If the conservative pass produced very little text, retry that page only
                // at a larger pixel budget. The first bitmap has already been recycled.
                if((text==null||text.trim().length()<24) && firstErr==null){
                    try{
                        memoryGuard("before high-res retry page "+page);
                        String hi=ocrPage(renderer,page-1,ocr,RETRY_PIXELS);
                        if(hi!=null&&hi.length()>(text==null?0:text.length()))text=hi;
                    }catch(Throwable retryErr){
                        asc.blackbox("PHOENIX","PAGE_HIGHRES_SKIPPED",meta.name+" · page "+page+" · "+shortErr(retryErr),state.state());
                    }
                }else if(firstErr!=null){
                    // One final conservative attempt after GC. If it fails, skip this page,
                    // checkpoint it, and continue instead of killing the whole document.
                    try{
                        memoryGuard("before recovery retry page "+page);
                        text=ocrPage(renderer,page-1,ocr,FIRST_PASS_PIXELS*0.72);
                    }catch(Throwable retryErr){
                        text="";
                        asc.blackbox("PHOENIX","PAGE_SKIPPED",meta.name+" · page "+page+" · "+shortErr(retryErr),state.state());
                    }
                }

                if(text==null)text="";
                int before=chunks;
                int[] out=emitPage(sid,pid,page,text,chunks);
                chunks=out[0];chars+=out[1];
                state.checkpoint(uri.toString(),"PDF_PAGE",page,sid,pid,chars,chunks);
                asc.blackbox("PHOENIX","PAGE_COMMIT",meta.name+" · page "+page+" · +"+(chunks-before)+" chunks · "+out[1]+" chars",state.state());

                // Keep the isolated process flat during huge merged books.
                if(page%6==0)System.gc();
            }

            if(chunks<=0||chars<=0){
                catalog.markFailed(sid,"No readable content extracted by page renderer/OCR.");
                throw new Exception("No readable content extracted from PDF");
            }
            catalog.finishSource(sid,(int)Math.min(Integer.MAX_VALUE,chars),chunks,pid);

            DocumentImporter.Result r=new DocumentImporter.Result();
            r.name=meta.name;r.mime=meta.mime;r.sizeBytes=meta.size;r.sourceId=sid;
            r.chars=(int)Math.min(Integer.MAX_VALUE,chars);r.chunks=chunks;r.textExtracted=true;
            r.note="Phoenix imported PDF page-by-page with bounded native rendering and OCR";
            long sum=brain.addMemoryRich("IMPORT RESULT: "+meta.name+"\nPhoenix completed "+r.chunks+" chunks using bounded page-stream OCR.","REFERENCE","source-import, phoenix, pdf",7,0,9,5,false);
            if(pid>0&&sum>0)brain.reinforceAssociation(pid,sum,"IMPORT-RESULT",2);
            return r;
        }catch(Exception e){
            catalog.markFailed(sid,shortErr(e));throw e;
        }finally{
            try{if(renderer!=null)renderer.close();}catch(Throwable ignored){}
            try{if(pfd!=null)pfd.close();}catch(Throwable ignored){}
            try{if(ocr!=null)ocr.close();}catch(Throwable ignored){}
            try{tmp.delete();}catch(Throwable ignored){}
            System.gc();
        }
    }

    private int[] emitPage(long sid,long parent,int page,String text,int chunks) throws Exception {
        if(text==null||text.trim().isEmpty())return new int[]{chunks,0};
        String clean=text.replace("\u0000","").replaceAll("[ \\t]+"," ").replaceAll("\\n{4,}","\n\n\n").trim();
        int pos=0,chars=0;ClaimGraphEngine cg=new ClaimGraphEngine(asc);
        while(pos<clean.length()){
            int end=Math.min(clean.length(),pos+CHUNK);
            if(end<clean.length()){
                int cut=clean.lastIndexOf('\n',end);
                if(cut<pos+CHUNK/2)cut=clean.lastIndexOf('.',end);
                if(cut>pos+CHUNK/2)end=cut+1;
            }
            String piece=clean.substring(pos,end).trim();
            if(!piece.isEmpty()){
                int part=++chunks;
                catalog.addChunk(sid,part,piece);
                long id=brain.addMemoryRich(piece,"KNOWLEDGE","document, pdf, page:"+page+", part:"+part,6,0,8,6,false);
                if(parent>0&&id>0)brain.reinforceAssociation(parent,id,"SOURCE-PART",3);
                asc.indexText(sid,part,id,piece);
                if(id>0)asc.evidence(id,"SOURCE",sid,part,page,"PHOENIX_PDF_OCR",8);
                cg.indexChunk(sid,part,piece);
                chars+=piece.length();
            }
            if(end>=clean.length())break;
            pos=Math.max(pos+1,end-OVERLAP);
        }
        return new int[]{chunks,chars};
    }

    private String ocrPage(PdfRenderer renderer,int index,VisionOcrEngine ocr,double pixelBudget) throws Exception {
        PdfRenderer.Page p=renderer.openPage(index);Bitmap bm=null;
        try{
            int w=Math.max(1,p.getWidth()),h=Math.max(1,p.getHeight());
            double scale=Math.sqrt(pixelBudget/Math.max(1.0,w*(double)h));
            scale=Math.max(0.72,Math.min(2.25,scale));
            int bw=Math.max(1,(int)Math.round(w*scale)),bh=Math.max(1,(int)Math.round(h*scale));
            long px=(long)bw*(long)bh;
            if(px>3_100_000L){double shrink=Math.sqrt(3_100_000.0/px);bw=Math.max(1,(int)(bw*shrink));bh=Math.max(1,(int)(bh*shrink));}
            bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bm);canvas.drawColor(Color.WHITE);
            Matrix m=new Matrix();m.setScale(bw/(float)w,bh/(float)h);
            p.render(bm,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return ocr.recognize(bm);
        }finally{
            if(bm!=null&&!bm.isRecycled())bm.recycle();
            try{p.close();}catch(Throwable ignored){}
        }
    }

    private void copy(Uri uri,File f) throws Exception {
        try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(f)){
            if(in==null)throw new Exception("Could not open PDF");
            byte[] b=new byte[64*1024];long n=0;int r;
            while((r=in.read(b))!=-1){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                n+=r;if(n>MAX_BYTES)throw new Exception("PDF exceeds Phoenix 512 MB file window");
                out.write(b,0,r);
            }
        }
    }

    private void memoryGuard(String where) throws Exception {
        Runtime r=Runtime.getRuntime();
        long free=r.maxMemory()-(r.totalMemory()-r.freeMemory());
        if(free<54L*1024L*1024L){System.gc();free=r.maxMemory()-(r.totalMemory()-r.freeMemory());}
        if(free<28L*1024L*1024L)throw new Exception("Phoenix memory guard paused at "+where+"; checkpoint preserved");
    }

    private Meta meta(Uri u){
        Meta m=new Meta();ContentResolver cr=context.getContentResolver();String t=cr.getType(u);if(t!=null)m.mime=t;
        try(Cursor c=cr.query(u,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);
                if(n>=0&&!c.isNull(n))m.name=c.getString(n);
                if(s>=0&&!c.isNull(s))m.size=c.getLong(s);
            }
        }catch(Throwable ignored){}
        return m;
    }

    private static String shortErr(Throwable t){
        if(t==null)return "unknown error";String m=t.getMessage();
        String x=(m==null||m.trim().isEmpty())?t.getClass().getSimpleName():t.getClass().getSimpleName()+": "+m;
        return x.length()<=500?x:x.substring(0,499)+"…";
    }
    private static class Meta{String name="source.pdf",mime="application/pdf";long size;}
}
