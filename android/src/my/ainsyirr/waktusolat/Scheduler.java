package my.ainsyirr.waktusolat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/** Keeps exactly one OS alarm armed: the next azan (or reminder). Re-armed after every fire, boot, time change. */
final class Scheduler {
    static final String ACTION_FIRE = "my.ainsyirr.waktusolat.FIRE";
    static final String ACTION_RETRY = "my.ainsyirr.waktusolat.RETRY";
    static final String ACTION_TEST = "my.ainsyirr.waktusolat.TEST";
    static final String ACTION_STOP = "my.ainsyirr.waktusolat.STOP";
    static final TimeZone MYT = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private Scheduler() {}

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    /** Off the main thread only (may download the month table). */
    static synchronized void reschedule(Context c) {
        JSONObject s = Prefs.settings(c);
        String zone = s.optString("zone", "SBH07");
        JSONObject onj = s.optJSONObject("on");
        boolean[] on = new boolean[5];
        for (int i = 0; i < 5; i++) on[i] = onj == null || onj.optBoolean(Core.KEYS[i], true);
        int remind = s.optInt("remind", 0);
        long now = System.currentTimeMillis();
        long after = Prefs.sp(c).getLong("lastFired", 0);

        long[][] days = new long[3][];
        boolean[] fri = new boolean[3];
        Calendar cal = Calendar.getInstance(MYT);
        cal.setTimeInMillis(now);
        for (int d = 0; d < 3; d++) {
            Calendar x = (Calendar) cal.clone();
            x.add(Calendar.DAY_OF_MONTH, d);
            fri[d] = x.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
            JSONObject e = Prefs.day(c, zone, x.get(Calendar.YEAR), x.get(Calendar.MONTH) + 1, x.get(Calendar.DAY_OF_MONTH));
            if (e == null) continue;
            days[d] = new long[5];
            for (int i = 0; i < 5; i++) days[d][i] = e.optLong(Core.KEYS[i]);
        }
        // keep next month cached so a month boundary never needs the network at night
        if (cal.get(Calendar.DAY_OF_MONTH) >= 20) {
            Calendar nm = (Calendar) cal.clone();
            nm.set(Calendar.DAY_OF_MONTH, 1);
            nm.add(Calendar.MONTH, 1);
            Prefs.month(c, zone, nm.get(Calendar.YEAR), nm.get(Calendar.MONTH) + 1);
        }

        AlarmManager am = c.getSystemService(AlarmManager.class);
        Intent retryIt = new Intent(c, AlarmReceiver.class).setAction(ACTION_RETRY);
        PendingIntent retry = PendingIntent.getBroadcast(c, 2, retryIt, FLAGS);
        long[] p = Core.pick(now, after, days, on, remind);
        if (p == null) {
            // no data (offline) or every azan switched off: try again later
            am.cancel(PendingIntent.getBroadcast(c, 1, new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE), FLAGS));
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 60 * 60000L, retry);
            Prefs.sp(c).edit().putString("next", "{}").commit();
            Log.i(Prefs.TAG, "nothing to schedule; retry in 1h");
            return;
        }
        am.cancel(retry);
        int idx = (int) p[2];
        String label = Core.label(idx, fri[(int) p[4]]);
        String type = p[3] == Core.AZAN ? "azan" : "remind";
        Intent it = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE)
                .putExtra("at", p[0]).putExtra("prayer", p[1]).putExtra("key", Core.KEYS[idx])
                .putExtra("label", label).putExtra("type", type).putExtra("min", remind);
        setExact(c, p[0], PendingIntent.getBroadcast(c, 1, it, FLAGS));
        try {
            JSONObject n = new JSONObject().put("at", p[0]).put("prayer", p[1]).put("key", Core.KEYS[idx])
                    .put("label", label).put("type", type).put("zone", zone);
            Prefs.sp(c).edit().putString("next", n.toString()).commit();
        } catch (Exception ignored) {}
        Log.i(Prefs.TAG, "armed " + type + " " + label + " at " + p[0]);
    }

    static void setExact(Context c, long at, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (canExact(c)) {
            Intent show = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent showPi = PendingIntent.getActivity(c, 9, show, FLAGS);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void testInOneMinute(Context c) {
        long at = System.currentTimeMillis() + 60000L;
        Intent it = new Intent(c, AlarmReceiver.class).setAction(ACTION_TEST)
                .putExtra("at", at).putExtra("prayer", at).putExtra("key", "dhuhr").putExtra("label", "Ujian").putExtra("type", "azan");
        setExact(c, at, PendingIntent.getBroadcast(c, 3, it, FLAGS));
    }

    static void rescheduleAsync(final Context c) {
        new Thread(() -> {
            try { reschedule(c.getApplicationContext()); } catch (Throwable t) { Log.e(Prefs.TAG, "reschedule", t); }
        }).start();
    }
}
