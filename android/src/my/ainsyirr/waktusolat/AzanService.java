package my.ainsyirr.waktusolat;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONObject;

/** Foreground service that plays the azan; started by the alarm even when the app is closed. */
public class AzanService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    static volatile String playing = null;

    private MediaPlayer mp;
    private PowerManager.WakeLock wl;
    private AudioFocusRequest focus;
    private String label = "";
    private long prayer;
    private boolean finished;

    static void start(Context c, String key, String label, long prayer, boolean test) {
        Intent it = new Intent(c, AzanService.class)
                .putExtra("key", key).putExtra("label", label).putExtra("prayer", prayer).putExtra("test", test);
        c.startForegroundService(it);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent it, int flags, int startId) {
        if (it == null) { stopSelf(); return START_NOT_STICKY; }
        releasePlayer();
        finished = false;
        String key = it.getStringExtra("key");
        label = it.getStringExtra("label");
        if (label == null) label = "";
        prayer = it.getLongExtra("prayer", System.currentTimeMillis());
        boolean test = it.getBooleanExtra("test", false);

        Notification n = Notifs.playing(this, label, prayer, test);
        if (Build.VERSION.SDK_INT >= 29) startForeground(Notifs.ID_PLAY, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(Notifs.ID_PLAY, n);
        playing = label;

        if (wl == null) {
            wl = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "waktusolat:azan");
            wl.setReferenceCounted(false);
        }
        wl.acquire(6 * 60000L);

        AudioManager am = getSystemService(AudioManager.class);
        int mode = am.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            finish(true);                       // on a call: notification only, no sound
            return START_NOT_STICKY;
        }

        JSONObject s = Prefs.settings(this);
        String voice = s.optString("voice", "azan.mp3");
        String subuh = s.optString("subuh", "azan_subuh.mp3");
        String file = test ? "azan_pendek.mp3" : ("fajr".equals(key) && !"same".equals(subuh) ? subuh : voice);
        float vol = (float) Math.max(0, Math.min(1, s.optDouble("vol", 0.9)));
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            mp = new MediaPlayer();
            mp.setAudioAttributes(aa);
            if ("chime".equals(file)) {
                mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            } else {
                AssetFileDescriptor fd = getAssets().openFd("www/audio/" + file);
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            mp.setVolume(vol, vol);
            mp.setOnCompletionListener(this);
            mp.setOnErrorListener(this);
            mp.prepare();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(aa).build();
            am.requestAudioFocus(focus);
            mp.start();
        } catch (Exception e) {
            Log.e(Prefs.TAG, "azan play failed", e);
            finish(true);
        }
        return START_NOT_STICKY;
    }

    private void finish(boolean showDone) {
        if (finished) return;
        finished = true;
        if (showDone) Notifs.done(this, label, prayer);
        stopSelf();
    }

    @Override public void onCompletion(MediaPlayer m) { finish(true); }

    @Override public boolean onError(MediaPlayer m, int what, int extra) {
        Log.e(Prefs.TAG, "MediaPlayer error " + what + "/" + extra);
        finish(true);
        return true;
    }

    private void releasePlayer() {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) {}
            mp.release();
            mp = null;
        }
        if (focus != null) {
            getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    @Override
    public void onDestroy() {
        playing = null;
        releasePlayer();
        if (wl != null && wl.isHeld()) wl.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
