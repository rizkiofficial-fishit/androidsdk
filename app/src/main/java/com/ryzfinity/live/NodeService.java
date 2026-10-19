package com.ryzfinity.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Menjalankan server RYZFinity (Node.js) di dalam app, plus alerts.html di background supaya suara tetap bunyi. */
public class NodeService extends Service {
    static final int PORT = 3000;
    static final String BASE = "http://127.0.0.1:" + PORT;
    static final String ACTION_ALERT_ON = "com.ryzfinity.live.ALERT_ON";
    static final String ACTION_ALERT_OFF = "com.ryzfinity.live.ALERT_OFF";
    static final String ACTION_QUIT = "com.ryzfinity.live.QUIT";
    private static final String CH = "ry_server";
    private static final String TAG = "RYZ";
    private static volatile boolean nodeStarted = false;

    private native int startNode(String[] args, String logPath);

    private static boolean libsLoaded = false;

    private boolean loadLibs() {
        if (libsLoaded) return true;
        try {
            System.loadLibrary("node");
            Diag.log(this, "libnode dimuat");
            System.loadLibrary("native-lib");
            Diag.log(this, "native-lib dimuat");
            libsLoaded = true;
            return true;
        } catch (Throwable t) {
            Diag.log(this, "FAIL muat library: " + t);
            return false;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean attached = false;
    private PowerManager.WakeLock wl;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (ACTION_QUIT.equals(act)) {
            stopForeground(true);
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
            return START_NOT_STICKY;
        }
        Diag.log(this, "service start");
        try { goForeground(); } catch (Throwable t) { Diag.log(this, "FAIL foreground: " + t); Diag.crash(this, Thread.currentThread(), t); }
        if (ACTION_ALERT_OFF.equals(act)) {
            prefs().edit().putBoolean("alert", false).apply();
            main.post(this::destroyWeb);
            return START_STICKY;
        }
        if (ACTION_ALERT_ON.equals(act)) prefs().edit().putBoolean("alert", true).apply();
        bootNode();
        new Thread(this::waitThenAlert, "ry-wait").start();
        return START_STICKY;
    }

    private SharedPreferences prefs() { return getSharedPreferences("ry", MODE_PRIVATE); }

    // ---------- Node ----------
    private synchronized void bootNode() {
        if (nodeStarted) return;
        nodeStarted = true;
        new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), "nodejs-project");
                Diag.log(this, "ekstrak server...");
                ensureProject(dir);
                Diag.log(this, "ekstrak selesai");
                if (!loadLibs()) { nodeStarted = false; return; }
                File log = new File(getFilesDir(), "node.log");
                if (log.exists() && log.length() > 300_000) log.delete();
                Os.setenv("PORT", String.valueOf(PORT), true);
                Os.setenv("HOST", "127.0.0.1", true);
                Os.setenv("HOME", getFilesDir().getAbsolutePath(), true);
                Os.setenv("TMPDIR", getCacheDir().getAbsolutePath(), true);
                Os.setenv("NODE_ENV", "production", true);
                Os.setenv("NODE_OPTIONS", "--max-old-space-size=384", true);
                final String[] args = {"node", new File(dir, "server.js").getAbsolutePath()};
                final String lp = log.getAbsolutePath();
                Diag.log(this, "memanggil node::Start");
                Thread t = new Thread(null, () -> {
                    int code = startNode(args, lp);
                    Diag.log(this, "node berhenti, kode " + code);
                    Log.e(TAG, "node berhenti, kode " + code);
                }, "node", 2 * 1024 * 1024);
                t.start();
            } catch (Throwable e) {
                Log.e(TAG, "gagal start node", e);
                Diag.log(this, "FAIL boot: " + e);
                Diag.crash(this, Thread.currentThread(), e);
                nodeStarted = false;
            }
        }, "ry-boot").start();
    }

    /** Ekstrak server dari assets sekali per instal/update. File data dan upload tidak ditimpa. */
    private void ensureProject(File dir) throws Exception {
        long stamp = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        SharedPreferences sp = prefs();
        if (dir.exists() && sp.getLong("extracted", 0) == stamp) return;
        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        try (InputStream in = getAssets().open("nodejs-project.zip"); ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(dir, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) continue; // cegah zip-slip
                if (e.isDirectory()) { f.mkdirs(); continue; }
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
        sp.edit().putLong("extracted", stamp).apply();
    }

    private void waitThenAlert() {
        for (int i = 0; i < 90; i++) {
            if (MainActivity.ping()) break;
            try { Thread.sleep(700); } catch (InterruptedException ignored) { }
        }
        if (prefs().getBoolean("alert", true)) main.post(this::initWeb);
    }

    // ---------- Alert di background ----------
    private void initWeb() {
        if (web != null) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (wl == null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ryzfinity:alerts");
            wl.acquire();
        }
        web = new WebView(getApplicationContext());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(BASE + "/alerts.html");
        if (Build.VERSION.SDK_INT >= 26 && Settings.canDrawOverlays(this)) {
            try {
                WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                wm.addView(web, lp);
                attached = true;
            } catch (Exception ignored) { }
        }
    }

    private void destroyWeb() {
        if (web == null) return;
        if (attached) {
            try { ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(web); } catch (Exception ignored) { }
            attached = false;
        }
        web.destroy();
        web = null;
        if (wl != null && wl.isHeld()) wl.release();
        wl = null;
    }

    // ---------- Notifikasi ----------
    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Server RYZFinity", NotificationManager.IMPORTANCE_LOW));
            nb = new Notification.Builder(this, CH);
        } else {
            nb = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Intent q = new Intent(this, NodeService.class);
        q.setAction(ACTION_QUIT);
        PendingIntent quit = PendingIntent.getService(this, 1, q, PendingIntent.FLAG_IMMUTABLE);
        nb.setContentTitle("RYZFinity aktif")
                .setContentText("Server dan alert suara jalan di HP ini")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Keluar", quit);
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        destroyWeb();
        super.onDestroy();
    }
}
