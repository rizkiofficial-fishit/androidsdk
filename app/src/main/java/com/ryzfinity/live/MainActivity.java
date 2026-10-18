package com.ryzfinity.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private volatile boolean closed = false;

    private static final String LOADING =
            "<html><body style='margin:0;background:#0b0a16;color:#f1eefc;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                    + "<div><div style='font-size:44px'>&#9889;</div><h2 style='margin:8px 0'>RYZFinity</h2><p style='color:#a29cc4'>Menyalakan server di HP...<br>Pertama kali bisa sampai satu menit.</p></div></body></html>";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF0B0A16);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.loadDataWithBaseURL(null, LOADING, "text/html", "utf-8", null);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        // Kalau jalan sebelumnya bermasalah, tampilkan log dulu dan jangan langsung nyalakan server.
        if (Diag.needsAttention(this)) showReport("Jalan sebelumnya bermasalah", true);
        else proceed();
    }

    private void proceed() {
        Diag.reset(this);
        Diag.log(this, "app dibuka");
        try {
            startSvc(null);
        } catch (Throwable t) {
            Diag.log(this, "FAIL start service: " + t);
            Diag.crash(this, Thread.currentThread(), t);
        }
        new Thread(() -> {
            for (int i = 0; i < 120 && !closed; i++) {
                if (ping()) {
                    Diag.log(this, "READY");
                    runOnUiThread(() -> web.loadUrl(NodeService.BASE));
                    return;
                }
                if (Diag.hasFail(this)) break;
                try { Thread.sleep(700); } catch (InterruptedException ignored) { }
            }
            if (!closed) runOnUiThread(() -> web.loadDataWithBaseURL(null,
                    "<html><body style='background:#0b0a16;color:#f1eefc;font-family:sans-serif;padding:20px'><h3>Server belum jalan</h3><p>Salin teks di bawah, kirim ke pembuat tool. Bisa juga dibuka lewat menu titik tiga, Lihat log server.</p><pre style='white-space:pre-wrap;font-size:11px;color:#c9c3ea'>"
                            + esc(Diag.report(this)) + "</pre></body></html>", "text/html", "utf-8", null));
        }, "ry-ready").start();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private void showReport(String title, final boolean runAfter) {
        final String txt = Diag.report(this);
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(txt.isEmpty() ? "(log kosong)" : txt)
                .setCancelable(!runAfter)
                .setNeutralButton("Salin log", (dl, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("log", txt));
                    Toast.makeText(this, "Log tersalin", Toast.LENGTH_SHORT).show();
                    if (runAfter) showReport(title, true);
                });
        if (runAfter) d.setPositiveButton("Coba jalankan lagi", (dl, w) -> proceed());
        else d.setPositiveButton("Tutup", null);
        d.show();
    }

    static boolean ping() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(NodeService.BASE + "/manifest.webmanifest").openConnection();
            c.setConnectTimeout(700);
            c.setReadTimeout(1500);
            int code = c.getResponseCode();
            c.disconnect();
            return code > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startSvc(String action) {
        Intent i = new Intent(this, NodeService.class);
        if (action != null) i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.main, m);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        int id = it.getItemId();
        if (id == R.id.m_alert_on) startSvc(NodeService.ACTION_ALERT_ON);
        else if (id == R.id.m_alert_off) startSvc(NodeService.ACTION_ALERT_OFF);
        else if (id == R.id.m_overlay) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        else if (id == R.id.m_reload) web.reload();
        else if (id == R.id.m_log) showReport("Log server", false);
        else if (id == R.id.m_quit) { startSvc(NodeService.ACTION_QUIT); finishAndRemoveTask(); }
        else return super.onOptionsItemSelected(it);
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        closed = true;
        super.onDestroy();
    }
}
