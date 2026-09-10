package com.hanma.echocore;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;

/** Bundled on-device OCR path for images and scanned PDF pages. */
public class VisionOcrEngine implements AutoCloseable {
    private final Context context; private final TextRecognizer recognizer;
    public VisionOcrEngine(Context c){context=c.getApplicationContext();recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);}
    public String recognize(Uri uri) throws Exception {InputImage image=InputImage.fromFilePath(context,uri);Text t=Tasks.await(recognizer.process(image),90,TimeUnit.SECONDS);return t==null?"":clean(t.getText());}
    public String recognize(Bitmap bitmap) throws Exception {InputImage image=InputImage.fromBitmap(bitmap,0);Text t=Tasks.await(recognizer.process(image),90,TimeUnit.SECONDS);return t==null?"":clean(t.getText());}
    private static String clean(String s){return s==null?"":s.replace("\u0000","").replaceAll("[ \\t]+"," ").replaceAll("\\n{4,}","\n\n\n").trim();}
    @Override public void close(){try{recognizer.close();}catch(Throwable ignored){}}
}
