package de.polenoffline.app;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;
import com.google.mlkit.nl.languageid.LanguageIdentification;

/**
 * Gesprächsmodus: Spracherkennung → (Übersetzung in JS) → Sprachausgabe.
 * Optional: Polnisch über den Handy-Lautsprecher, Deutsch in den Kopfhörer.
 */
public class Gespraech {
    public interface Js { void rufe(String code); }

    private final Activity a;
    private final TextToSpeech tts;
    private final Js js;
    private SpeechRecognizer erkenner;
    private boolean aufGeraet = true;     // erst Erkennung direkt auf dem Gerät versuchen
    private String sprache = "de-DE";
    private String autoFremd = null;   // gesetzt = automatische Spracherkennung (Deutsch oder diese Sprache)
    private MediaPlayer player;

    public Gespraech(Activity a, TextToSpeech tts, Js js) {
        this.a = a; this.tts = tts; this.js = js;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { if (id.startsWith("gespraech")) fertigGesprochen(); }
            @Override public void onError(String id) { if (id.startsWith("gespraech")) fertigGesprochen(); }
        });
    }

    private void fertigGesprochen() { js.rufe("window.sprechenFertig && sprechenFertig()"); }

    // ---------------- Zuhören ----------------
    /** Automatisch: hört auf Deutsch UND die Fremdsprache und erkennt, welche gesprochen wurde. */
    public void hoerenAuto(String fremdLang) {
        autoFremd = fremdLang;
        hoerenIntern(fremdLang);
    }

    public void hoeren(String lang) {
        autoFremd = null;
        hoerenIntern(lang);
    }

    private void hoerenIntern(String lang) {
        a.runOnUiThread(() -> {
            sprache = lang;
            stoppIntern();
            if (!SpeechRecognizer.isRecognitionAvailable(a)) {
                js.rufe("window.hoerFehler && hoerFehler('keine_erkennung')"); return;
            }
            boolean geraet = aufGeraet && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(a);
            erkenner = geraet ? SpeechRecognizer.createOnDeviceSpeechRecognizer(a) : SpeechRecognizer.createSpeechRecognizer(a);
            final boolean warGeraet = geraet;
            erkenner.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { js.rufe("window.hoerStatus && hoerStatus('bereit')"); }
                @Override public void onBeginningOfSpeech() { js.rufe("window.hoerStatus && hoerStatus('spricht')"); }
                @Override public void onRmsChanged(float db) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() { js.rufe("window.hoerStatus && hoerStatus('denkt')"); }
                @Override public void onEvent(int t, Bundle b) {}
                @Override public void onPartialResults(Bundle b) {
                    String t = erster(b);
                    if (t != null) js.rufe("window.hoerTeil && hoerTeil(" + JSONObject.quote(t) + ")");
                }
                @Override public void onResults(Bundle b) {
                    String t = erster(b);
                    if (t == null || t.trim().isEmpty()) { js.rufe("window.hoerFehler && hoerFehler('nichts')"); return; }
                    if (autoFremd == null) { melde(t, sprache); return; }
                    // 1) Android 14+: Erkenner meldet die gesprochene Sprache selbst
                    String erkannt = null;
                    if (Build.VERSION.SDK_INT >= 34) erkannt = b.getString(SpeechRecognizer.DETECTED_LANGUAGE);
                    if (erkannt != null && !erkannt.isEmpty()) { melde(t, zuordnen(erkannt)); return; }
                    // 2) sonst am Text erkennen (offline, ML Kit)
                    final String text = t;
                    LanguageIdentification.getClient().identifyLanguage(text)
                        .addOnSuccessListener(code -> melde(text, zuordnen(code)))
                        .addOnFailureListener(e -> melde(text, autoFremd));
                }
                @Override public void onError(int fehler) {
                    // Gerät kann die Sprache nicht → einmal mit der normalen Erkennung versuchen
                    if (warGeraet && (fehler == 12 || fehler == 13 || fehler == SpeechRecognizer.ERROR_CLIENT)) {
                        aufGeraet = false; hoerenIntern(sprache); return;
                    }
                    String art;
                    switch (fehler) {
                        case SpeechRecognizer.ERROR_NO_MATCH: case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: art = "nichts"; break;
                        case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: case SpeechRecognizer.ERROR_SERVER: art = "offline_paket"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: art = "mikrofon"; break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: art = "belegt"; break;
                        case 12: case 13: art = "offline_paket"; break;
                        default: art = "fehler" + fehler;
                    }
                    js.rufe("window.hoerFehler && hoerFehler('" + art + "')");
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            if (autoFremd != null) {
                ArrayList<String> beide = new ArrayList<>();
                beide.add(autoFremd); beide.add("de-DE");
                i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"de-DE"});
                if (Build.VERSION.SDK_INT >= 34) {
                    i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
                    i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, beide);
                    i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
                    i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, beide);
                }
            }
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
            try { erkenner.startListening(i); }
            catch (Exception e) { js.rufe("window.hoerFehler && hoerFehler('fehler')"); }
        });
    }

    private void melde(String text, String lang) {
        js.rufe("window.hoerErgebnis && hoerErgebnis(" + JSONObject.quote(text) + ",'" + lang + "')");
    }

    /** "de", "de-DE", "pl", "en-US" … → "de-DE" oder die Fremdsprache */
    private String zuordnen(String code) {
        String c = code == null ? "" : code.toLowerCase();
        if (c.startsWith("de")) return "de-DE";
        return autoFremd != null ? autoFremd : sprache;
    }

    public void stopp() { a.runOnUiThread(this::stoppIntern); }

    private void stoppIntern() {
        if (erkenner != null) { try { erkenner.cancel(); erkenner.destroy(); } catch (Exception ignored) {} erkenner = null; }
    }

    private String erster(Bundle b) {
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (l != null && !l.isEmpty()) ? l.get(0) : null;
    }

    // ---------------- Sprechen ----------------
    /** lautsprecher=true: über den Handy-Lautsprecher ausgeben, auch wenn Kopfhörer verbunden sind. */
    public void sprechen(String text, String lang, boolean lautsprecher) {
        a.runOnUiThread(() -> {
            String l = lang.toLowerCase();
            Locale loc = l.startsWith("pl") ? new Locale("pl", "PL") : l.startsWith("en") ? Locale.UK : Locale.GERMANY;
            tts.setLanguage(loc);
            tts.setSpeechRate(0.95f);
            AudioDeviceInfo lsp = lautsprecher ? lautsprecherWennKopfhoerer() : null;
            if (lsp == null || Build.VERSION.SDK_INT < 28) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gespraech" + System.nanoTime());
                return;
            }
            // In Datei sprechen und gezielt auf dem Lautsprecher abspielen
            File f = new File(a.getCacheDir(), "ansage.wav");
            String id = "datei" + System.nanoTime();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String u) {}
                @Override public void onError(String u) { neuerListener(); fertigGesprochen(); }
                @Override public void onDone(String u) {
                    neuerListener();
                    a.runOnUiThread(() -> abspielen(f, lsp));
                }
            });
            if (tts.synthesizeToFile(text, null, f, id) != TextToSpeech.SUCCESS) {
                neuerListener();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gespraech" + System.nanoTime());
            }
        });
    }

    private void neuerListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { if (id.startsWith("gespraech")) fertigGesprochen(); }
            @Override public void onError(String id) { if (id.startsWith("gespraech")) fertigGesprochen(); }
        });
    }

    private void abspielen(File f, AudioDeviceInfo ziel) {
        try {
            if (player != null) player.release();
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 28) player.setPreferredDevice(ziel);
            player.setOnCompletionListener(mp -> fertigGesprochen());
            player.prepare();
            player.start();
        } catch (Exception e) { fertigGesprochen(); }
    }

    /** Liefert den eingebauten Lautsprecher – aber nur, wenn gerade Kopfhörer verbunden sind. */
    private AudioDeviceInfo lautsprecherWennKopfhoerer() {
        AudioManager am = (AudioManager) a.getSystemService(Activity.AUDIO_SERVICE);
        AudioDeviceInfo lsp = null; boolean kopf = false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) lsp = d;
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_USB_HEADSET
                || (Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET))) kopf = true;
        }
        return kopf ? lsp : null;
    }

    public boolean kopfhoererDran() { return lautsprecherWennKopfhoerer() != null; }

    public void beenden() {
        stoppIntern();
        if (player != null) { player.release(); player = null; }
    }
}
