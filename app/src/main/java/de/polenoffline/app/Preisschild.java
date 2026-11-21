package de.polenoffline.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Liest ein Foto ein, erkennt den Text (offline) und gibt Zeilen mit Größe/Position zurück. */
public class Preisschild {
    public interface Ergebnis { void fertig(String json); }

    public static void erkennen(Activity a, Uri uri, Ergebnis cb) {
        new Thread(() -> {
            try {
                Bitmap bmp = laden(a, uri, 2000);
                String vorschau = vorschau(bmp);
                InputImage img = InputImage.fromBitmap(bmp, 0);
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                    .addOnSuccessListener(text -> {
                        try {
                            JSONArray zeilen = new JSONArray();
                            int blockNr = 0;
                            for (Text.TextBlock b : text.getTextBlocks()) {
                                blockNr++;
                                for (Text.Line l : b.getLines()) {
                                    Rect r = l.getBoundingBox();
                                    JSONObject z = new JSONObject();
                                    z.put("t", l.getText());
                                    z.put("b", blockNr);
                                    if (r != null) { z.put("x", r.left); z.put("y", r.top); z.put("w", r.width()); z.put("h", r.height()); }
                                    zeilen.put(z);
                                }
                            }
                            JSONObject o = new JSONObject();
                            o.put("zeilen", zeilen); o.put("bild", vorschau);
                            o.put("breite", bmp.getWidth()); o.put("hoehe", bmp.getHeight());
                            cb.fertig(o.toString());
                        } catch (Exception e) { cb.fertig(fehler("Auswertung fehlgeschlagen")); }
                    })
                    .addOnFailureListener(e -> cb.fertig(fehler("Texterkennung fehlgeschlagen")));
            } catch (Exception e) {
                cb.fertig(fehler("Foto konnte nicht gelesen werden"));
            }
        }).start();
    }

    private static String fehler(String t) {
        try { return new JSONObject().put("fehler", t).toString(); } catch (Exception e) { return "{}"; }
    }

    /** Bild verkleinert laden und nach EXIF richtig drehen. */
    static Bitmap laden(Activity a, Uri uri, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = a.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap bmp;
        try (InputStream in = a.getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
        int grad = 0;
        try (InputStream in = a.getContentResolver().openInputStream(uri)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) grad = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) grad = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) grad = 270;
        } catch (Exception ignored) {}
        if (grad != 0) {
            Matrix m = new Matrix(); m.postRotate(grad);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    static String vorschau(Bitmap bmp) {
        float f = 640f / Math.max(bmp.getWidth(), bmp.getHeight());
        Bitmap k = f < 1 ? Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * f), Math.round(bmp.getHeight() * f), true) : bmp;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        k.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
