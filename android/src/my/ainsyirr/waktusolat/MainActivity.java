package my.ainsyirr.waktusolat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_LOC = 11, REQ_NOTIF = 12;
    private WebView wv;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notifs.ensure(this);
        wv = new WebView(this);
        wv.setBackgroundColor(0xFF0E1513);
        setContentView(wv);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        wv.addJavascriptInterface(new Bridge(), "AzanNative");
        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                try {
                    InputStream in = getAssets().open("www" + path);
                    String mime = mime(path);
                    return new WebResourceResponse(mime, mime.startsWith("text") || mime.contains("json") || mime.contains("javascript") ? "UTF-8" : null, in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        if (b == null) wv.loadUrl("https://" + HOST + "/index.html");
        else wv.restoreState(b);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Prefs.sp(this).getBoolean("askedNotif", false)) {
            Prefs.sp(this).edit().putBoolean("askedNotif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        Scheduler.rescheduleAsync(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); wv.saveState(out); }

    @Override
    protected void onResume() {
        super.onResume();
        js("window.__nativeResume&&window.__nativeResume()");
        String p = AzanService.playing;
        if (p != null) js("window.__nativeAzan&&window.__nativeAzan(" + JSONObject.quote(p) + ")");
    }

    @Override
    public void onBackPressed() {
        wv.evaluateJavascript("!!(window.__nativeBack&&window.__nativeBack())", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    private static String mime(String p) {
        p = p.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    private void js(String code) { ui.post(() -> { if (wv != null) wv.evaluateJavascript(code, null); }); }

    // ---------- location ----------
    private boolean hasLoc() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void sendLoc(double lat, double lon, String err) {
        js("window.__nativeLoc&&window.__nativeLoc(" + lat + "," + lon + "," + (err == null ? "null" : JSONObject.quote(err)) + ")");
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        boolean enabled = Build.VERSION.SDK_INT >= 28 ? lm.isLocationEnabled()
                : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        if (!enabled) { sendLoc(0, 0, "off"); return; }
        Location best = null;
        List<String> provs = lm.getProviders(true);
        for (String p : provs) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 10 * 60000L) {
            sendLoc(best.getLatitude(), best.getLongitude(), null);
            return;
        }
        final Location fallback = best;
        final boolean[] done = {false};
        final Runnable timeout = () -> {
            if (done[0]) return;
            done[0] = true;
            if (fallback != null) sendLoc(fallback.getLatitude(), fallback.getLongitude(), null);
            else sendLoc(0, 0, "timeout");
        };
        ui.postDelayed(timeout, 20000);
        String prov = provs.contains("fused") ? "fused"
                : lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(prov, null, getMainExecutor(), l -> {
                    if (done[0]) return;
                    done[0] = true;
                    ui.removeCallbacks(timeout);
                    if (l != null) sendLoc(l.getLatitude(), l.getLongitude(), null);
                    else if (fallback != null) sendLoc(fallback.getLatitude(), fallback.getLongitude(), null);
                    else sendLoc(0, 0, "unavailable");
                });
            } else {
                lm.requestSingleUpdate(prov, new LocationListener() {
                    @Override public void onLocationChanged(Location l) {
                        if (done[0]) return;
                        done[0] = true;
                        ui.removeCallbacks(timeout);
                        sendLoc(l.getLatitude(), l.getLongitude(), null);
                    }
                    @Override public void onStatusChanged(String p, int s, Bundle e) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                }, Looper.getMainLooper());
            }
        } catch (Exception e) {
            Log.w(Prefs.TAG, "location", e);
            ui.removeCallbacks(timeout);
            timeout.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC) {
            if (hasLoc()) fetchLocation(); else sendLoc(0, 0, "denied");
        }
        js("window.__nativeResume&&window.__nativeResume()");
    }

    // ---------- JS bridge ----------
    final class Bridge {
        @JavascriptInterface
        public String info() {
            try {
                JSONObject o = new JSONObject();
                boolean notif = getSystemService(NotificationManager.class).areNotificationsEnabled();
                if (Build.VERSION.SDK_INT >= 33)
                    notif = notif && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                o.put("native", true);
                o.put("notif", notif);
                o.put("exact", Scheduler.canExact(MainActivity.this));
                o.put("battery", getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()));
                o.put("next", Prefs.next(MainActivity.this));
                o.put("playing", AzanService.playing == null ? "" : AzanService.playing);
                o.put("version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void saveSettings(String json) {
            Prefs.saveSettings(MainActivity.this, json);
            Scheduler.rescheduleAsync(MainActivity.this);
        }

        @JavascriptInterface
        public void cacheMonth(String zone, int y, int m, String json) { Prefs.storeMonth(MainActivity.this, zone, y, m, json); }

        @JavascriptInterface
        public void testAzan(String key, String label) {
            AzanService.start(MainActivity.this, key, label, System.currentTimeMillis(), true);
        }

        @JavascriptInterface
        public void stopAzan() { stopService(new Intent(MainActivity.this, AzanService.class)); }

        @JavascriptInterface
        public void testInOneMinute() { Scheduler.testInOneMinute(MainActivity.this); }

        @JavascriptInterface
        public void requestLocation() {
            ui.post(() -> {
                if (hasLoc()) fetchLocation();
                else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            });
        }

        @JavascriptInterface
        public void requestNotif() {
            ui.post(() -> {
                boolean granted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                boolean canAsk = Build.VERSION.SDK_INT >= 33 && !granted
                        && (!Prefs.sp(MainActivity.this).getBoolean("askedNotif", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS));
                if (canAsk) {
                    Prefs.sp(MainActivity.this).edit().putBoolean("askedNotif", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    startSafe(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
            });
        }

        @JavascriptInterface
        public void fixBattery() {
            ui.post(() -> {
                @SuppressLint("BatteryLife")
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                if (!startSafe(i)) startSafe(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            });
        }

        @JavascriptInterface
        public void exactSettings() {
            ui.post(() -> {
                if (Build.VERSION.SDK_INT >= 31)
                    startSafe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            });
        }

        @JavascriptInterface
        public void appSettings() {
            ui.post(() -> startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }
    }

    private boolean startSafe(Intent i) {
        try { startActivity(i); return true; } catch (Exception e) { Log.w(Prefs.TAG, "startActivity " + i, e); return false; }
    }
}
