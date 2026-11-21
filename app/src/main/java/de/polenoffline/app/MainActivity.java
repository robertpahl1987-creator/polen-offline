package de.polenoffline.app;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsBereit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFFC8102E);

        tts = new TextToSpeech(this, status -> ttsBereit = (status == TextToSpeech.SUCCESS));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // Merkliste & Wechselkurs speichern
        s.setAllowFileAccess(true);
        s.setBlockNetworkLoads(true);          // garantiert offline
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Sprache(), "AndroidTTS");
        setContentView(web);

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    /** Brücke für "Vorlesen" – nutzt die Sprachausgabe des Handys (funktioniert offline). */
    class Sprache {
        @JavascriptInterface
        public String speak(String text, String lang) {
            if (!ttsBereit) return "nicht_bereit";
            Locale loc = lang.toLowerCase().startsWith("pl") ? new Locale("pl", "PL") : Locale.GERMANY;
            int r = tts.setLanguage(loc);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Polnische Sprachdaten fehlen. Einstellungen → Sprachausgabe → Polnisch herunterladen.",
                    Toast.LENGTH_LONG).show());
                return "fehlt";
            }
            tts.setSpeechRate(0.85f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "polen");
            return "ok";
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // Zurück-Taste: erst Großanzeige schließen bzw. zur Startseite, dann App verlassen
        web.evaluateJavascript("window.zurueck ? zurueck() : false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
