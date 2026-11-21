package de.polenoffline.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Empfängt die Antwort des Android-Installers und zeigt den Bestätigungsdialog an. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent bestaetigen = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (bestaetigen != null) {
                bestaetigen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(bestaetigen);
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (status != PackageInstaller.STATUS_FAILURE_ABORTED)
                Toast.makeText(ctx, "Update fehlgeschlagen" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
        }
    }
}
