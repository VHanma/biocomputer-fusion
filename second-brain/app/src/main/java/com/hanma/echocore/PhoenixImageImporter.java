package com.hanma.echocore;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/** Standalone image ingestion through the same evidence pipeline. */
public class PhoenixImageImporter {
    private final android.content.Context context;private final BrainDatabase brain;private final SourceCatalog catalog;private final AscendantStore asc;
    public PhoenixImageImporter(android.content.Context c,BrainDatabase b,SourceCatalog s,AscendantStore a){context=c.getApplicationContext();brain=b;catalog=s;asc=a;}
    public DocumentImporter.Result ingest(Uri uri) throws Exception {String name="image",mime="image/*";long size=0;ContentResolver cr=context.getContentResolver();String mt=cr.getType(uri);if(mt!=null)mime=mt;try(Cursor c=cr.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE);if(ni>=0&&!c.isNull(ni))name=c.getString(ni);if(si>=0&&!c.isNull(si))size=c.getLong(si);}}catch(Throwable ignored){}
        long sid=catalog.startSource(name,mime,uri.toString(),size);long parent=brain.addMemoryRich("SOURCE: "+name+"\nKind: IMAGE\nImport mode: Phoenix Vision OCR","SOURCE","source, image, ocr, phoenix",8,0,9,7,false);String text="";try(VisionOcrEngine o=new VisionOcrEngine(context)){text=o.recognize(uri);}int chunks=0,chars=0;if(text!=null&&!text.trim().isEmpty()){String clean=text.trim();int pos=0;ClaimGraphEngine cg=new ClaimGraphEngine(asc);while(pos<clean.length()){int end=Math.min(clean.length(),pos+2600);String piece=clean.substring(pos,end).trim();if(!piece.isEmpty()){int part=++chunks;catalog.addChunk(sid,part,piece);long id=brain.addMemoryRich(piece,"KNOWLEDGE","document, image-ocr, part:"+part,6,0,7,7,false);if(parent>0&&id>0)brain.reinforceAssociation(parent,id,"SOURCE-PART",3);asc.indexText(sid,part,id,piece);if(id>0)asc.evidence(id,"SOURCE",sid,part,0,"VISION_OCR",7);cg.indexChunk(sid,part,piece);chars+=piece.length();}pos=end;}}
        catalog.finishSource(sid,chars,chunks,parent);DocumentImporter.Result r=new DocumentImporter.Result();r.name=name;r.mime=mime;r.sizeBytes=size;r.sourceId=sid;r.chars=chars;r.chunks=chunks;r.textExtracted=chunks>0;r.note=chunks>0?"Image OCR indexed into Source Cortex":"Image indexed; OCR found no readable text";return r;}
}
