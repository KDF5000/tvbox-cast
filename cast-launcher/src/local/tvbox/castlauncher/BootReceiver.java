package local.tvbox.castlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Boot initializes the receiver; Android's default HOME selection owns the visible page. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && ReceiverService.preferences(context).getBoolean("enabled", true)) {
            try { ReceiverService.ensureStarted(context); }
            catch (RuntimeException unavailable) { Log.w("TvBoxReceiver", "Boot start restricted; open the HOME page"); }
        }
    }
}
