package my.ainsyirr.waktusolat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Notifs {
    static final int ID_PLAY = 1, ID_DONE = 2, ID_REMIND = 3;
    private static final int ACCENT = 0xFFD9A85B;

    private Notifs() {}

    static void ensure(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel a = new NotificationChannel("azan", "Azan", NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("Ditunjukkan ketika azan berbunyi (dengan butang Hentikan)");
        a.setSound(null, null);
        a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel r = new NotificationChannel("remind", "Peringatan sebelum waktu", NotificationManager.IMPORTANCE_HIGH);
        r.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        NotificationChannel d = new NotificationChannel("done", "Telah masuk waktu", NotificationManager.IMPORTANCE_DEFAULT);
        d.setSound(null, null);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(r);
        nm.createNotificationChannel(d);
    }

    static String time(Context c, long ms) {
        boolean h12 = Prefs.settings(c).optInt("fmt", 24) == 12;
        SimpleDateFormat f = new SimpleDateFormat(h12 ? "h:mm a" : "HH:mm", Locale.US);
        f.setTimeZone(Scheduler.MYT);
        return f.format(new Date(ms));
    }

    static String place(Context c) {
        String p = Prefs.settings(c).optString("place", "");
        String z = Prefs.zone(c);
        return p.isEmpty() ? z : p + " (" + z + ")";
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, Scheduler.FLAGS);
    }

    static Notification playing(Context c, String label, long prayer, boolean test) {
        ensure(c);
        PendingIntent stop = PendingIntent.getBroadcast(c, 4,
                new Intent(c, AlarmReceiver.class).setAction(Scheduler.ACTION_STOP), Scheduler.FLAGS);
        Notification.Action act = new Notification.Action.Builder(
                Icon.createWithResource(c, R.drawable.ic_stat), "Hentikan azan", stop).build();
        return new Notification.Builder(c, "azan")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(ACCENT)
                .setContentTitle(test ? "Ujian azan" : "Telah masuk waktu " + label)
                .setContentText(time(c, prayer) + " · " + place(c))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .setDeleteIntent(stop)
                .addAction(act)
                .build();
    }

    static void done(Context c, String label, long prayer) {
        ensure(c);
        Notification n = new Notification.Builder(c, "done")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(ACCENT)
                .setContentTitle("Telah masuk waktu " + label)
                .setContentText(time(c, prayer) + " · " + place(c))
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .setTimeoutAfter(60 * 60000L)
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_DONE, n);
    }

    static void reminder(Context c, String label, long prayer, int min) {
        ensure(c);
        Notification n = new Notification.Builder(c, "remind")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(ACCENT)
                .setContentTitle(label + " dalam " + min + " minit")
                .setContentText("Masuk waktu " + time(c, prayer) + " · " + place(c))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .setTimeoutAfter((min + 5) * 60000L)
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_REMIND, n);
    }
}
