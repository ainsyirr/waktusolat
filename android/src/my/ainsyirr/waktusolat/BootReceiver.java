package my.ainsyirr.waktusolat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Re-arm the next azan after reboot, app update, clock/timezone change or exact-alarm permission change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        String a = i.getAction();
        if (a == null || a.startsWith("my.ainsyirr")) return;     // only system broadcasts
        final Context c = ctx.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { Scheduler.reschedule(c); } catch (Throwable t) { Log.e(Prefs.TAG, "boot reschedule", t); } finally { pr.finish(); }
        }).start();
    }
}
