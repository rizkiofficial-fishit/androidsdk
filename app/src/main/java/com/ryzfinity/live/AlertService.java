package com.ryzfinity.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Menjalankan alerts.html di background supaya suara alert dan TTS tetap bunyi saat TikTok dibuka. */
public class AlertService extends Service {
    static final String ACTION_STOP = "com.ryzfinity.live.STOP";
    private static final String CH = "ry_alert";
    private WebView web;
    private boolean attached = false;
    private PowerManager.WakeLock wl;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (web == null) new Handler(Looper.getMainLooper()).post(this::initWeb);
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Alert live", NotificationManager.IMPORTANCE_LOW));
            nb = new Notification.Builder(this, CH);
        } else {
            nb = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, AlertService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent ps = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        nb.setContentTitle("RYZFinity alert aktif")
                .setContentText("Suara alert dan TTS jalan di background")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", ps);
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }
    }

    private void initWeb() {
        String base = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.KEY, "");
        if (base.isEmpty()) { stopSelf(); return; }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ryzfinity:alerts");
        wl.acquire();
        web = new WebView(getApplicationContext());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(base + "/alerts.html");
        // Kalau izin overlay diberikan, WebView dipasang 1 piksel tak terlihat supaya tidak di-pause sistem.
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

    @Override
    public void onDestroy() {
        if (web != null) {
            if (attached) {
                try { ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(web); } catch (Exception ignored) { }
            }
            web.destroy();
            web = null;
        }
        if (wl != null && wl.isHeld()) wl.release();
        super.onDestroy();
    }
}
