package de.polenoffline.app;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsBereit = false;
    private boolean seiteGeladen = false;
    private long letzterAbruf = 0;
    private long letztesUpdateCheck = 0;

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
        s.setBlockNetworkLoads(true);          // die Seite selbst lädt nie etwas aus dem Netz
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                seiteGeladen = true;
                kursHolen(true);
                updatePruefen(false);
            }
        });
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

        @JavascriptInterface
        public void updateSuchen() { runOnUiThread(() -> updatePruefen(true)); }

        @JavascriptInterface
        public String version() { return "1.0." + Updater.eigeneNummerSicher(MainActivity.this); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Beim Zurückkehren in die App erneut prüfen (höchstens alle 30 Minuten)
        if (seiteGeladen && System.currentTimeMillis() - letzterAbruf > 30 * 60 * 1000) kursHolen(false);
        if (seiteGeladen && System.currentTimeMillis() - letztesUpdateCheck > 3 * 60 * 60 * 1000) updatePruefen(false);
    }

    private void updatePruefen(boolean manuell) {
        letztesUpdateCheck = System.currentTimeMillis();
        Updater.pruefen(this, manuell, text -> js("window.updateStatus && updateStatus('" + text + "')"));
    }

    /** Holt den aktuellen Euro→Złoty-Kurs, wenn Internet da ist. Ohne Internet bleibt der gespeicherte Kurs. */
    private void kursHolen(boolean beimStart) {
        letzterAbruf = System.currentTimeMillis();
        js("window.kursStatus && kursStatus('laden')");
        new Thread(() -> {
            double kurs = 0; String datum = "", quelle = "";
            try {   // 1. Quelle: Europäische Zentralbank (über Frankfurter-API)
                JSONObject o = new JSONObject(laden("https://api.frankfurter.app/latest?from=EUR&to=PLN"));
                kurs = o.getJSONObject("rates").getDouble("PLN");
                datum = o.optString("date", ""); quelle = "EZB";
            } catch (Exception e1) {
                try { // 2. Quelle als Ersatz
                    JSONObject o = new JSONObject(laden("https://open.er-api.com/v6/latest/EUR"));
                    kurs = o.getJSONObject("rates").getDouble("PLN");
                    datum = ""; quelle = "ExchangeRate-API";
                } catch (Exception e2) { kurs = 0; }
            }
            if (kurs > 1 && kurs < 20) {
                js("window.kursOnline && kursOnline(" + kurs + ",'" + datum + "','" + quelle + "')");
            } else {
                js("window.kursStatus && kursStatus('offline')");
            }
        }).start();
    }

    private String laden(String adresse) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(adresse).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "PolenOffline-App");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder(); String z;
            while ((z = r.readLine()) != null) sb.append(z);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
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
