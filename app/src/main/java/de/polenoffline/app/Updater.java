package de.polenoffline.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Automatisches Update: fragt beim Öffnen bei GitHub nach der neuesten Version.
 * Ist eine neuere da, wird die APK im Hintergrund geladen und Android zeigt
 * den Installieren-Dialog (ein Tippen genügt – ganz ohne Tippen erlaubt Android es nicht).
 */
public class Updater {
    static final String API = "https://api.github.com/repos/robertpahl1987-creator/polen-offline/releases/latest";
    private static boolean laeuft = false;

    public interface Status { void melde(String text); }

    public static void pruefen(Activity a, boolean manuell, Status status) {
        if (laeuft) return;
        laeuft = true;
        new Thread(() -> {
            try {
                int eigene = eigeneNummer(a);
                JSONObject rel = new JSONObject(laden(API));
                int neueste = nummerAus(rel.optString("tag_name", ""));
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject o = assets.getJSONObject(i);
                    if (o.optString("name").endsWith(".apk")) { apkUrl = o.getString("browser_download_url"); break; }
                }
                if (neueste <= eigene || apkUrl == null) {
                    status.melde("aktuell:" + versionText(eigene));
                    if (manuell) toast(a, "Du hast schon die neueste Version (1.0." + eigene + ").");
                    return;
                }
                // Erlaubnis "Unbekannte Apps installieren" für diese App nötig
                if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                    status.melde("erlaubnis:1.0." + neueste);
                    toast(a, "Update 1.0." + neueste + " verfügbar. Bitte einmal „Aus dieser Quelle zulassen“ aktivieren und zurückkehren.");
                    a.runOnUiThread(() -> a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.getPackageName()))));
                    return;
                }
                status.melde("lade:1.0." + neueste);
                toast(a, "Neue Version 1.0." + neueste + " wird geladen …");
                installieren(a, apkUrl);
                status.melde("bereit:1.0." + neueste);
            } catch (Exception e) {
                status.melde("offline:" + versionText(eigeneNummerSicher(a)));
                if (manuell) toast(a, "Update-Prüfung nicht möglich – kein Internet?");
            } finally {
                laeuft = false;
            }
        }).start();
    }

    private static void installieren(Activity a, String url) throws Exception {
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(a.getPackageName());
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            HttpURLConnection c = verbindung(url);
            try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("update.apk", 0, -1)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            } finally { c.disconnect(); }
            Intent i = new Intent(a, UpdateReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(a, id, i, flags);
            s.commit(pend.getIntentSender());
        }
    }

    static int eigeneNummer(Activity a) throws Exception {
        return nummerAus(a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName);
    }
    static int eigeneNummerSicher(Activity a) { try { return eigeneNummer(a); } catch (Exception e) { return 0; } }
    static String versionText(int n) { return "1.0." + n; }

    /** "v1.0.7" oder "1.0.7" → 7 */
    static int nummerAus(String v) {
        try { String[] t = v.replace("v", "").trim().split("\\."); return Integer.parseInt(t[t.length - 1]); }
        catch (Exception e) { return 0; }
    }

    private static HttpURLConnection verbindung(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "PolenOffline-App");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        return c;
    }

    private static String laden(String url) throws Exception {
        HttpURLConnection c = verbindung(url);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder(); String z;
            while ((z = r.readLine()) != null) sb.append(z);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private static void toast(Activity a, String t) {
        a.runOnUiThread(() -> Toast.makeText(a, t, Toast.LENGTH_LONG).show());
    }
}
