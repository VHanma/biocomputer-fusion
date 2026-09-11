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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Resumable PDF importer. Native text is always tried first; OCR is a per-page fallback. */
public class PhoenixPdfImporter {
    private static final int CHUNK=2700,OVERLAP=150; private static final long MAX_BYTES=384L*1024L*1024L;
    private final Context context; private final BrainDatabase brain; private final SourceCatalog catalog; private final AscendantStore asc; private final ImportStateStore state;
    public PhoenixPdfImporter(Context c,BrainDatabase b,SourceCatalog s,AscendantStore a,ImportStateStore q){context=c.getApplicationContext();brain=b;catalog=s;asc=a;state=q;PDFBoxResourceLoader.init(context);}

    public DocumentImporter.Result ingest(Uri uri,String fingerprint,boolean recoveryMode) throws Exception {
        Meta meta=meta(uri);JSONObject job=state.job(uri.toString());long sourceId=job.optLong("source_id",0),parent=job.optLong("parent_memory_id",0);int pageDone=(int)job.optLong("checkpoint_value",0),chunks=(int)job.optLong("chunks",0);long chars=job.optLong("chars",0);
        if(sourceId<=0){sourceId=catalog.startSource(meta.name,meta.mime,uri.toString(),meta.size);parent=brain.addMemoryRich("SOURCE: "+meta.name+"\nKind: PDF\nType: "+meta.mime+"\nImport mode: Phoenix resumable page checkpoints","SOURCE","source, pdf, phoenix",8,0,9,7,false);state.checkpoint(uri.toString(),"PDF_PAGE",0,sourceId,parent,0,0);}else catalog.markIngesting(sourceId);
        final long sid=sourceId,pid=parent;File tmp=File.createTempFile("phoenix_",".pdf",context.getCacheDir());VisionOcrEngine ocr=null;ParcelFileDescriptor pfd=null;PdfRenderer renderer=null;
        try{
            copy(uri,tmp);MemoryUsageSetting mus=MemoryUsageSetting.setupMixed(6L*1024L*1024L,512L*1024L*1024L).setTempDir(context.getCacheDir());
            pfd=ParcelFileDescriptor.open(tmp,ParcelFileDescriptor.MODE_READ_ONLY);renderer=new PdfRenderer(pfd);
            try(PDDocument doc=PDDocument.load(tmp,mus)){
                int pages=doc.getNumberOfPages();if(pages<=0)throw new Exception("PDF contains no readable pages");PDFTextStripper stripper=new PDFTextStripper();stripper.setSortByPosition(true);
                for(int page=Math.max(1,pageDone+1);page<=pages;page++){
                    if(Thread.currentThread().isInterrupted()||state.cancelRequested())throw new InterruptedException("Phoenix cancelled");memoryGuard("PDF page "+page);
                    String text="";Throwable textErr=null;
                    // Native PDF text extraction is ALWAYS first, even for quarantined files. Previous builds
                    // incorrectly forced every quarantined PDF straight into OCR, creating a failure loop.
                    try{stripper.setStartPage(page);stripper.setEndPage(page);text=stripper.getText(doc);if(text!=null)text=text.trim();}catch(Throwable t){textErr=t;text="";}
                    boolean needOcr=text==null||text.length()<24||looksBroken(text);
                    if(needOcr){
                        try{if(ocr==null)ocr=new VisionOcrEngine(context);String o=ocrPage(renderer,page-1,ocr);if(o!=null&&o.trim().length()>text.length())text=o.trim();}
                        catch(Throwable t){
                            // If native extraction produced usable text, keep it even when OCR fails.
                            if(text!=null&&text.trim().length()>=24){asc.blackbox("PHOENIX","OCR_FALLBACK_FAILED",meta.name+" · page "+page+" · native text retained",state.state());}
                            else if(textErr!=null)throw new Exception("Native parser and OCR both failed on page "+page+": "+shortErr(t),t);
                            else throw t instanceof Exception?(Exception)t:new Exception(t);
                        }
                    }
                    if(text==null)text="";int before=chunks;int[] out=emitPage(sid,pid,page,text,chunks);chunks=out[0];chars+=out[1];
                    state.checkpoint(uri.toString(),"PDF_PAGE",page,sid,pid,chars,chunks);asc.blackbox("PHOENIX","PAGE_COMMIT",meta.name+" · page "+page+" · +"+(chunks-before)+" chunks",state.state());
                }
                if(chunks<=0||chars<=0){catalog.markFailed(sid,"No readable content extracted after native PDF parsing and OCR fallback.");throw new Exception("No readable content extracted from PDF");}
                catalog.finishSource(sid,(int)Math.min(Integer.MAX_VALUE,chars),chunks,pid);
            }
            DocumentImporter.Result r=new DocumentImporter.Result();r.name=meta.name;r.mime=meta.mime;r.sizeBytes=meta.size;r.sourceId=sid;r.chars=(int)Math.min(Integer.MAX_VALUE,chars);r.chunks=chunks;r.textExtracted=true;r.note="Phoenix imported PDF with native text + OCR fallback and page checkpoints";long sum=brain.addMemoryRich("IMPORT RESULT: "+meta.name+"\nPhoenix completed "+r.chunks+" chunks with resumable page checkpoints.","REFERENCE","source-import, phoenix, pdf",7,0,9,5,false);if(pid>0&&sum>0)brain.reinforceAssociation(pid,sum,"IMPORT-RESULT",2);return r;
        }catch(Exception e){catalog.markFailed(sid,shortErr(e));throw e;}
        finally{try{if(renderer!=null)renderer.close();}catch(Throwable ignored){}try{if(pfd!=null)pfd.close();}catch(Throwable ignored){}try{if(ocr!=null)ocr.close();}catch(Throwable ignored){}tmp.delete();}
    }

    private int[] emitPage(long sid,long parent,int page,String text,int chunks) throws Exception {if(text==null||text.trim().isEmpty())return new int[]{chunks,0};String clean=text.replace("\u0000","").replaceAll("[ \\t]+"," ").replaceAll("\\n{4,}","\n\n\n").trim();int pos=0,chars=0;ClaimGraphEngine cg=new ClaimGraphEngine(asc);while(pos<clean.length()){int end=Math.min(clean.length(),pos+CHUNK);if(end<clean.length()){int cut=clean.lastIndexOf('\n',end);if(cut<pos+CHUNK/2)cut=clean.lastIndexOf('.',end);if(cut>pos+CHUNK/2)end=cut+1;}String piece=clean.substring(pos,end).trim();if(!piece.isEmpty()){int part=++chunks;catalog.addChunk(sid,part,piece);long id=brain.addMemoryRich(piece,"KNOWLEDGE","document, pdf, page:"+page+", part:"+part,6,0,8,6,false);if(parent>0&&id>0)brain.reinforceAssociation(parent,id,"SOURCE-PART",3);asc.indexText(sid,part,id,piece);if(id>0)asc.evidence(id,"SOURCE",sid,part,page,"PHOENIX_PDF",8);cg.indexChunk(sid,part,piece);chars+=piece.length();}if(end>=clean.length())break;pos=Math.max(pos+1,end-OVERLAP);}return new int[]{chunks,chars};}

    private String ocrPage(PdfRenderer renderer,int index,VisionOcrEngine ocr) throws Exception {PdfRenderer.Page p=renderer.openPage(index);Bitmap bm=null;try{int w=p.getWidth(),h=p.getHeight();double scale=Math.min(2.0,Math.sqrt(3_200_000.0/Math.max(1.0,w*h)));scale=Math.max(1.0,scale);int bw=Math.max(1,(int)(w*scale)),bh=Math.max(1,(int)(h*scale));bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bm);canvas.drawColor(Color.WHITE);Matrix m=new Matrix();m.setScale((float)scale,(float)scale);p.render(bm,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return ocr.recognize(bm);}finally{if(bm!=null&&!bm.isRecycled())bm.recycle();p.close();}}
    private void copy(Uri uri,File f) throws Exception {try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(f)){if(in==null)throw new Exception("Could not open PDF");byte[] b=new byte[64*1024];long n=0;int r;while((r=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();n+=r;if(n>MAX_BYTES)throw new Exception("PDF exceeds Phoenix 384 MB window");out.write(b,0,r);}}}
    private void memoryGuard(String where) throws Exception {Runtime r=Runtime.getRuntime();long free=r.maxMemory()-(r.totalMemory()-r.freeMemory());if(free<30L*1024L*1024L){System.gc();free=r.maxMemory()-(r.totalMemory()-r.freeMemory());}if(free<14L*1024L*1024L)throw new Exception("Phoenix memory guard paused at "+where+"; checkpoint preserved");}
    private Meta meta(Uri u){Meta m=new Meta();ContentResolver cr=context.getContentResolver();String t=cr.getType(u);if(t!=null)m.mime=t;try(Cursor c=cr.query(u,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);if(n>=0&&!c.isNull(n))m.name=c.getString(n);if(s>=0&&!c.isNull(s))m.size=c.getLong(s);}}catch(Throwable ignored){}return m;}
    private static boolean looksBroken(String s){if(s==null||s.length()<24)return true;int bad=0,letters=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetterOrDigit(c))letters++;if(c=='�'||c==0)bad++;}return bad>3||letters<Math.max(8,s.length()/12);}
    private static String shortErr(Throwable t){if(t==null)return "unknown error";String m=t.getMessage();String x=(m==null||m.trim().isEmpty())?t.getClass().getSimpleName():t.getClass().getSimpleName()+": "+m;return x.length()<=500?x:x.substring(0,499)+"…";}
    private static class Meta{String name="source.pdf",mime="application/pdf";long size;}
}
