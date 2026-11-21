package de.polenoffline.app;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.Set;

/**
 * Echte Satz-Übersetzung Polnisch ↔ Deutsch direkt auf dem Handy (Google ML Kit).
 * Das Sprachpaket wird EINMAL mit Internet geladen (~60 MB), danach läuft alles offline.
 */
public class KiUebersetzer {
    public interface Meldung { void status(String s); }
    public interface Antwort { void fertig(String text, boolean ok); }

    private final Translator plDe, dePl;
    private volatile boolean bereit = false, laedt = false;
    private final Meldung meldung;

    public KiUebersetzer(Meldung m) {
        meldung = m;
        plDe = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.POLISH).setTargetLanguage(TranslateLanguage.GERMAN).build());
        dePl = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.GERMAN).setTargetLanguage(TranslateLanguage.POLISH).build());
    }

    public boolean istBereit() { return bereit; }

    /** Prüft, ob das Paket schon da ist; sonst lädt es (nurWlan = automatisch nur im WLAN). */
    public void vorbereiten(boolean nurWlan) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel.class)
            .addOnSuccessListener(set -> {
                if (hat(set, TranslateLanguage.POLISH) && hat(set, TranslateLanguage.GERMAN)) {
                    bereit = true; meldung.status("bereit");
                } else laden(nurWlan);
            })
            .addOnFailureListener(e -> laden(nurWlan));
    }

    private boolean hat(Set<TranslateRemoteModel> set, String sprache) {
        for (TranslateRemoteModel m : set) if (sprache.equals(m.getLanguage())) return true;
        return false;
    }

    private void laden(boolean nurWlan) {
        if (laedt && nurWlan) return;   // manuelles Laden darf immer starten
        laedt = true;
        meldung.status(nurWlan ? "wartet_wlan" : "laedt");
        DownloadConditions.Builder b = new DownloadConditions.Builder();
        if (nurWlan) b.requireWifi();
        DownloadConditions c = b.build();
        plDe.downloadModelIfNeeded(c)
            .continueWithTask(t -> { if (!t.isSuccessful()) throw t.getException(); return dePl.downloadModelIfNeeded(c); })
            .addOnSuccessListener(v -> { laedt = false; bereit = true; meldung.status("bereit"); })
            .addOnFailureListener(e -> { laedt = false; meldung.status(nurWlan ? "wartet_wlan" : "fehler"); });
    }

    /** Übersetzt Zeile für Zeile (behält Absätze bei). */
    public void uebersetzen(String text, boolean vonPolnisch, Antwort a) {
        if (!bereit) { a.fertig("", false); return; }
        Translator t = vonPolnisch ? plDe : dePl;
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                String[] zeilen = text.split("\n");
                for (int i = 0; i < zeilen.length; i++) {
                    String z = zeilen[i].trim();
                    if (!z.isEmpty()) sb.append(Tasks.await(t.translate(z)));
                    if (i < zeilen.length - 1) sb.append("\n");
                }
                a.fertig(sb.toString(), true);
            } catch (Exception e) { a.fertig("", false); }
        }).start();
    }
}
