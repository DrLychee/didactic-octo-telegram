package com.lcz.attend;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private static final int REQ_STORAGE = 1001;
    private boolean permAsked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.white);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
        ensureStorage();
    }

    private void ensureStorage() {
        boolean need = Build.VERSION.SDK_INT <= 32 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        if (need) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!ok) showPermGuide(false);
        }
    }

    private void showPermGuide(final boolean permanentlyDenied) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage(permanentlyDenied
                ? "您已拒绝过存储权限，导出名单/课表/考勤需要该权限才能保存到手机。\n\n请点击「去授权」，在系统设置中允许本应用的存储权限后返回。"
                : "导出名单、课表模板、考勤记录需要存储权限来保存文件。\n\n拒绝后导出功能将无法使用，您可随时在系统设置中重新开启。")
            .setCancelable(permanentlyDenied)
            .setPositiveButton("去授权", (d, w) -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开设置，请手动到系统设置授权", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("暂不", (d, w) -> Toast.makeText(this, "未授权：文件将无法保存到手机", Toast.LENGTH_LONG).show())
            .show());
    }

    // JS 桥：保存文件到公共下载目录
    private class Bridge {
        @JavascriptInterface
        public String getDir() {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + "/班级考勤/";
        }

        @JavascriptInterface
        public String saveFile(String name, String content) {
            try {
                boolean granted = Build.VERSION.SDK_INT > 32 ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    ensureStorage();
                    return "未授权：请到「设置页」重试，并在弹窗中允许存储权限";
                }
                String body = content;
                if (body.startsWith("\ufeff")) body = body.substring(1);
                byte[] data = body.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, name.endsWith(".json") ? "application/json" : "text/csv");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/班级考勤");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(data); }
                    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        + "/班级考勤/" + name;
                } else {
                    File dir = getDirPublic();
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(data); }
                    return f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "保存失败：" + e.getMessage();
            }
        }
    }

    private File getDirPublic() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "班级考勤");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
