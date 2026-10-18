package com.ryzfinity.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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

import java.io.File;
import java.io.RandomAccessFile;

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
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        web.loadDataWithBaseURL(null, LOADING, "text/html", "utf-8", null);
        startSvc(null);
        new Thread(() -> {
            for (int i = 0; i < 120 && !closed; i++) {
                if (ping()) {
                    runOnUiThread(() -> web.loadUrl(NodeService.BASE));
                    return;
                }
                try { Thread.sleep(700); } catch (InterruptedException ignored) { }
            }
            if (!closed) runOnUiThread(() -> web.loadDataWithBaseURL(null,
                    "<html><body style='background:#0b0a16;color:#f1eefc;font-family:sans-serif;padding:24px'><h3>Server belum jalan</h3><p>Buka menu titik tiga, pilih <b>Lihat log server</b>, lalu kirim isinya ke pembuat tool.</p></body></html>",
                    "text/html", "utf-8", null));
        }, "ry-ready").start();
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

    private void showLog() {
        String txt;
        try {
            File f = new File(getFilesDir(), "node.log");
            if (!f.exists()) txt = "(log kosong)";
            else {
                try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                    long len = r.length(), from = Math.max(0, len - 3500);
                    byte[] buf = new byte[(int) (len - from)];
                    r.seek(from);
                    r.readFully(buf);
                    txt = new String(buf, "UTF-8");
                }
            }
        } catch (Exception e) {
            txt = "Gagal baca log: " + e.getMessage();
        }
        new AlertDialog.Builder(this).setTitle("Log server").setMessage(txt).setPositiveButton("Tutup", null).show();
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
        else if (id == R.id.m_log) showLog();
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
