package my.ainsyirr.waktusolat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        final Context c = ctx.getApplicationContext();
        String a = i.getAction();
        if (Scheduler.ACTION_STOP.equals(a)) {
            c.stopService(new Intent(c, AzanService.class));
            return;
        }
        boolean test = Scheduler.ACTION_TEST.equals(a);
        if (Scheduler.ACTION_FIRE.equals(a) || test) {
            long at = i.getLongExtra("at", 0);
            if (!test) Prefs.sp(c).edit().putLong("lastFired", at).commit();
            boolean stale = System.currentTimeMillis() - at > 15 * 60000L;   // phone was off: don't blast an old azan
            String label = i.getStringExtra("label");
            long prayer = i.getLongExtra("prayer", at);
            if (stale) {
                Log.i(Prefs.TAG, "stale alarm skipped " + label);
            } else if ("remind".equals(i.getStringExtra("type"))) {
                Notifs.reminder(c, label, prayer, i.getIntExtra("min", 0));
            } else {
                try {
                    AzanService.start(c, i.getStringExtra("key"), label, prayer, test);
                } catch (Throwable t) {
                    Log.e(Prefs.TAG, "cannot start azan service", t);
                    Notifs.done(c, label, prayer);
                }
            }
            if (test) return;
        }
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { Scheduler.reschedule(c); } catch (Throwable t) { Log.e(Prefs.TAG, "reschedule", t); } finally { pr.finish(); }
        }).start();
    }
}
