package com.lcz.attend;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final int REQ_STORAGE = 1001;
    private static final int REQ_FILE = 1002;
    private ValueCallback<Uri[]> uploadMessage;

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
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (uploadMessage != null) uploadMessage.onReceiveValue(null);
                uploadMessage = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] types = params.getAcceptTypes();
                if (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty())
                    intent.setType(types[0]);
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE);
                } catch (Exception e) {
                    uploadMessage = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
        ensureStorage();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && uploadMessage != null) {
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            uploadMessage = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /* ---------- 自动更新：镜像源依次下载 + 拉起安装 ---------- */
    private static final String[] MIRRORS = {
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        ""
    };

    private class Bridge {
        @JavascriptInterface
        public String getDir() {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + "/班级考勤/";
        }

        @JavascriptInterface
        public void downloadAndInstall(String urlsJson, String fileName) {
            new Thread(() -> {
                try {
                    JSONArray arr = new JSONArray(urlsJson);
                    for (int i = 0; i < arr.length(); i++) {
                        String url = arr.getString(i).trim();
                        if (url.isEmpty()) continue;
                        String label = (i == arr.length() - 1) ? "GitHub直连" : "镜像" + (i + 1);
                        js("txInstallProgress(0,'" + label + " 连接中…')");
                        long id = lastId = enqueue(url, fileName);
                        Boolean ok = waitDownload(id, label);
                        if (ok != null && ok) {
                            js("txInstallProgress(100,'✅ 下载完成，正在拉起安装…')");
                            ui.post(() -> installApk(fileName));
                            return;
                        }
                        js("txInstallProgress(0,'" + label + " 失败，切换下一个源…')");
                        if (i == arr.length() - 1)
                            js("txInstallProgress(0,'❌ 所有下载源均失败，请检查网络后重试')");
                    }
                } catch (Exception e) {
                    js("txInstallProgress(0,'❌ 下载出错：" + e.getMessage().replace("'", "") + "')");
                }
            }).start();
        }

        @JavascriptInterface
        public String saveFile(String name, String content) {
            try {
                boolean granted = Build.VERSION.SDK_INT >= 29 ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (!granted) { ensureStorage(); return "未授权：请在弹窗中允许存储权限后重试"; }
                String body = content;
                if (body.startsWith("\ufeff")) body = body.substring(1);
                byte[] dataBytes = body.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, name.endsWith(".json") ? "application/json" : "text/csv");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/班级考勤");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(dataBytes); }
                    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        + "/班级考勤/" + name;
                } else {
                    File dir = getDirPublic();
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(dataBytes); }
                    return f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "保存失败：" + e.getMessage();
            }
        }
    }

    private long enqueue(String url, String fileName) {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(fileName);
        req.setDescription("班级考勤 更新包");
        req.setMimeType("application/vnd.android.package-archive");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        return dm.enqueue(req);
    }

    /** 返回 true=成功 false=失败 null=取消/未知 */
    private Boolean waitDownload(long id, String label) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        long start = System.currentTimeMillis();
        int lastPct = -1;
        while (System.currentTimeMillis() - start < 10 * 60 * 1000) {
            Cursor c = dm.query(q);
            if (c == null) return false;
            int status = DownloadManager.STATUS_FAILED;
            long done = 0, total = -1;
            if (c.moveToFirst()) {
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            }
            c.close();
            if (total > 0) {
                int pct = (int) (done * 100 / total);
                if (pct != lastPct && pct <= 100) {
                    lastPct = pct;
                    js("txInstallProgress(" + pct + ",'" + label + " 下载中 " + pct + "%')");
                }
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) return true;
            if (status == DownloadManager.STATUS_FAILED) return false;
            try { Thread.sleep(800); } catch (InterruptedException ignored) { return null; }
        }
        return false;
    }

    private void installApk(String fileName) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri local = dm.getUriForDownloadedFile(lastId);
            if (local == null) {
                File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                local = Uri.fromFile(f);
            }
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                js("txInstallProgress(0,'⚠️ 请在接下来的页面允许「安装未知应用」，然后重新点一次下载安装')");
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(local, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(it);
        } catch (Exception e) {
            js("txInstallProgress(0,'❌ 拉起安装失败：" + e.getMessage().replace("'", "") + "')");
        }
    }

    private long lastId;

    private void js(String script) {
        ui.post(() -> web.evaluateJavascript(script, null));
    }

    private void ensureStorage() {
        boolean need = Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        if (need) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!ok) showPermGuide(false);
        }
    }

    private void showPermGuide(boolean permanentlyDenied) {
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

    private File getDirPublic() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "班级考勤");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
