package com.ryzfinity.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "ry";
    static final String KEY = "server";
    private WebView web;
    private SharedPreferences sp;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
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
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        String url = sp.getString(KEY, "");
        if (url.isEmpty()) askServer(); else web.loadUrl(url);
    }

    private void askServer() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setHint("https://domain-kamu.com");
        et.setText(sp.getString(KEY, ""));
        new AlertDialog.Builder(this)
                .setTitle("Alamat server RYZFinity")
                .setMessage("Isi link dashboard dari hosting kamu.")
                .setView(et)
                .setCancelable(sp.contains(KEY))
                .setPositiveButton("Simpan", (d, w) -> {
                    String u = et.getText().toString().trim();
                    if (u.isEmpty()) { askServer(); return; }
                    if (!u.startsWith("http")) u = "https://" + u;
                    while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
                    sp.edit().putString(KEY, u).apply();
                    web.loadUrl(u);
                })
                .show();
    }

    private void startAlerts() {
        String base = sp.getString(KEY, "");
        if (base.isEmpty()) { askServer(); return; }
        Intent i = new Intent(this, AlertService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Alert background jalan. Sekarang buka TikTok dan mulai live.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.main, m);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        int id = it.getItemId();
        if (id == R.id.m_start) startAlerts();
        else if (id == R.id.m_stop) {
            Intent i = new Intent(this, AlertService.class);
            i.setAction(AlertService.ACTION_STOP);
            startService(i);
        } else if (id == R.id.m_overlay) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } else if (id == R.id.m_reload) web.reload();
        else if (id == R.id.m_server) askServer();
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
}
