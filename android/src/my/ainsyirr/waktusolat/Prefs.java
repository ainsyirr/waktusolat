package my.ainsyirr.waktusolat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Settings (written by the web UI) + cached JAKIM month tables. */
final class Prefs {
    static final String TAG = "WaktuSolat";
    private static final String API = "https://api.waktusolat.app/v2/solat/";

    private Prefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("ws", Context.MODE_PRIVATE); }

    static JSONObject settings(Context c) {
        try { return new JSONObject(sp(c).getString("settings", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void saveSettings(Context c, String json) { sp(c).edit().putString("settings", json).commit(); }

    static String zone(Context c) { return settings(c).optString("zone", "SBH07"); }

    static JSONObject next(Context c) {
        try { return new JSONObject(sp(c).getString("next", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    // ---------- month cache ----------
    private static File file(Context c, String z, int y, int m) {
        return new File(c.getFilesDir(), "m_" + z + "_" + y + "_" + m + ".json");
    }

    static void storeMonth(Context c, String z, int y, int m, String json) {
        try {
            JSONArray a = new JSONArray(json);
            if (a.length() > 0) write(file(c, z, y, m), a.toString());
        } catch (Exception e) { Log.w(TAG, "storeMonth", e); }
    }

    /** Must be called off the main thread (may hit the network). */
    static JSONArray month(Context c, String z, int y, int m) {
        File f = file(c, z, y, m);
        if (f.exists()) {
            try { return new JSONArray(read(new FileInputStream(f))); } catch (Exception e) { Log.w(TAG, "bad cache", e); }
        }
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(API + z + "?year=" + y + "&month=" + m).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(20000);
            if (h.getResponseCode() != 200) return null;
            JSONObject j = new JSONObject(read(h.getInputStream()));
            JSONArray p = j.getJSONArray("prayers");
            if (p.length() == 0) return null;
            write(f, p.toString());
            return p;
        } catch (Exception e) {
            Log.w(TAG, "fetch " + z + " " + y + "-" + m + ": " + e);
            return null;
        }
    }

    static JSONObject day(Context c, String z, int y, int m, int d) {
        JSONArray a = month(c, z, y, m);
        if (a == null) return null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("day") == d) return o;
        }
        return null;
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, String s) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
    }
}
