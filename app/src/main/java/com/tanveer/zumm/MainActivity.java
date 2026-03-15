package com.tanveer.zumm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.print.PrintManager;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView      webView;
    private ProgressBar  progressBar;
    private EditText     urlInput;
    private LinearLayout bottomBar;
    private ImageButton  btnBack, btnForward, btnRefresh, btnHome, btnMenu, btnVoice;

    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQ_FILE  = 1002;
    private static final int REQ_VOICE = 1001;
    private boolean isDesktop       = false;
    private boolean isDarkMode      = false;
    private boolean isNavBarVisible = true;
    private int     lastScrollY     = 0;
    private boolean isDestroyed     = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        if (Build.VERSION.SDK_INT >= 21)
            getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23)
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        if (Build.VERSION.SDK_INT >= 23) {
            ArrayList<String> perms = new ArrayList<String>();
            perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(android.Manifest.permission.CAMERA);
            perms.add(android.Manifest.permission.RECORD_AUDIO);
            perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= 26)
                perms.add(android.Manifest.permission.REQUEST_INSTALL_PACKAGES);
            requestPermissions(perms.toArray(new String[0]), 100);
        }

        progressBar = (ProgressBar)  findViewById(R.id.myProgressBar);
        webView     = (WebView)      findViewById(R.id.myWebView);
        urlInput    = (EditText)     findViewById(R.id.urlInput);
        bottomBar   = (LinearLayout) findViewById(R.id.bottomBar);
        btnBack     = (ImageButton)  findViewById(R.id.btnBack);
        btnForward  = (ImageButton)  findViewById(R.id.btnForward);
        btnRefresh  = (ImageButton)  findViewById(R.id.btnRefresh);
        btnHome     = (ImageButton)  findViewById(R.id.btnHome);
        btnMenu     = (ImageButton)  findViewById(R.id.btnMenu);
        btnVoice    = (ImageButton)  findViewById(R.id.btnVoice);

        if (webView == null) return;

        initWebView();
        initButtons();
        webView.loadUrl("https://www.google.com");
        initScrollHide();
    }

    private void initWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                    WebChromeClient.FileChooserParams params) {
                if (isDestroyed) return false;
                uploadMessage = cb;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Choose File"), REQ_FILE);
                } catch (Exception e) { uploadMessage = null; return false; }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (isDestroyed) return;
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                if (urlInput != null && url != null) urlInput.setText(url);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isDestroyed) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (urlInput != null && url != null) urlInput.setText(url);
                if (url != null && !url.startsWith("about:")) saveHistory(url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return true;
                if (url.startsWith("http://") || url.startsWith("https://")) return false;
                try {
                    Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    if (i != null) { startActivity(i); return true; }
                } catch (Exception e) {}
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long cl) {
                if (!isDestroyed) startDownload(url, cd, mime);
            }
        });
    }

    private void initButtons() {
        if (btnMenu != null) btnMenu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showMenu(); }
        });
        if (btnVoice != null) btnVoice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startVoice(); }
        });
        if (btnBack != null) btnBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (webView != null && webView.canGoBack()) webView.goBack();
            }
        });
        if (btnForward != null) btnForward.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (webView != null && webView.canGoForward()) webView.goForward();
            }
        });
        if (btnRefresh != null) btnRefresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (webView != null) webView.reload(); }
        });
        if (btnHome != null) btnHome.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (webView != null) webView.loadUrl("https://www.google.com");
            }
        });
        if (urlInput != null) urlInput.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && e.getAction() == KeyEvent.ACTION_DOWN);
                if (go) { loadUrl(v.getText().toString().trim()); return true; }
                return false;
            }
        });
    }

    private void initScrollHide() {
        if (Build.VERSION.SDK_INT < 23 || webView == null || bottomBar == null) return;
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                if (isDestroyed || bottomBar == null) return;
                int delta = sy - lastScrollY;
                lastScrollY = sy;
                if (delta > 15 && isNavBarVisible) {
                    isNavBarVisible = false;
                    bottomBar.animate().translationY(bottomBar.getHeight() + 60).setDuration(220).start();
                } else if (delta < -15 && !isNavBarVisible) {
                    isNavBarVisible = true;
                    bottomBar.animate().translationY(0).setDuration(220).start();
                }
            }
        });
    }

    private void loadUrl(String input) {
        if (input == null || input.isEmpty() || webView == null) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    private String currentUrl() {
        if (webView == null) return "";
        String u = webView.getUrl();
        return u != null ? u : "";
    }

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try { startActivityForResult(i, REQ_VOICE); }
        catch (Exception e) { Toast.makeText(this, "Voice not available", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (isDestroyed) return;
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String text = r.get(0);
                if (urlInput != null) urlInput.setText(text);
                loadUrl(text);
            }
        }
        if (req == REQ_FILE && uploadMessage != null) {
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            uploadMessage = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void showMenu() {
        if (isDestroyed) return;
        final Dialog d = new Dialog(this);
        d.setContentView(R.layout.menu_bottom);
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setGravity(Gravity.BOTTOM);
        }
        tap(d, R.id.menuHistory,   new Runnable() { public void run() { showHistory(); } });
        tap(d, R.id.menuBookmarks, new Runnable() { public void run() { showBookmarkOptions(); } });
        tap(d, R.id.menuDownloads, new Runnable() { public void run() { showDownloads(); } });
        tap(d, R.id.menuPDF,       new Runnable() { public void run() { savePDF(); } });
        tap(d, R.id.menuCopy,      new Runnable() { public void run() { copyLink(); } });
        tap(d, R.id.menuShare,     new Runnable() { public void run() { shareLink(); } });
        tap(d, R.id.menuDesktop,   new Runnable() { public void run() { toggleDesktop(); } });
        tap(d, R.id.menuNight,     new Runnable() { public void run() { toggleNight(); } });
        tap(d, R.id.menuExit,      new Runnable() { public void run() { finish(); } });
        d.show();
    }

    private void tap(final Dialog d, int id, final Runnable r) {
        View v = d.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { d.dismiss(); r.run(); }
        });
    }

    private void copyLink() {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("url", currentUrl()));
        Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show();
    }

    private void shareLink() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, currentUrl());
        startActivity(Intent.createChooser(i, "Share via"));
    }

    private void savePDF() {
        if (Build.VERSION.SDK_INT < 19 || webView == null) {
            Toast.makeText(this, "Not supported", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pm == null) return;
            String title = webView.getTitle();
            if (title == null || title.isEmpty()) title = "page";
            pm.print("Zumm_" + title, webView.createPrintDocumentAdapter("Zumm_" + title), null);
        } catch (Exception e) { Toast.makeText(this, "PDF error", Toast.LENGTH_SHORT).show(); }
    }

    private void toggleDesktop() {
        if (webView == null) return;
        isDesktop = !isDesktop;
        if (isDesktop) {
            webView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            webView.getSettings().setUseWideViewPort(true);
            webView.getSettings().setLoadWithOverviewMode(true);
            Toast.makeText(this, "Desktop Mode ON", Toast.LENGTH_SHORT).show();
        } else {
            webView.getSettings().setUserAgentString(null);
            webView.getSettings().setUseWideViewPort(false);
            webView.getSettings().setLoadWithOverviewMode(false);
            Toast.makeText(this, "Mobile Mode ON", Toast.LENGTH_SHORT).show();
        }
        webView.reload();
    }

    private void toggleNight() {
        isDarkMode = !isDarkMode;
        int bg  = isDarkMode ? Color.parseColor("#1E1E1E") : Color.WHITE;
        int txt = isDarkMode ? Color.WHITE : Color.parseColor("#202124");
        View topBar = findViewById(R.id.topBar);
        if (topBar   != null) topBar.setBackgroundColor(bg);
        if (bottomBar != null) bottomBar.setBackgroundColor(bg);
        if (urlInput  != null) urlInput.setTextColor(txt);
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(bg);
        if (Build.VERSION.SDK_INT >= 23)
            getWindow().getDecorView().setSystemUiVisibility(
                isDarkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        Toast.makeText(this, isDarkMode ? "Night ON" : "Night OFF", Toast.LENGTH_SHORT).show();
    }

    // ── Download ───────────────────────────────────
    private void startDownload(String url, String cd, String mime) {
        if (url == null || url.isEmpty()) return;
        try {
            String name = smartName(url, cd, mime);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(req);
            Toast.makeText(this, "Downloading: " + name, Toast.LENGTH_SHORT).show();
            Set<String> list = new HashSet<String>(prefs().getStringSet("dl", new HashSet<String>()));
            list.add(name + "|" + url);
            prefs().edit().putStringSet("dl", list).apply();
        } catch (Exception e) { Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show(); }
    }

    private String smartName(String url, String cd, String mime) {
        String base = null, ext = null;
        if (cd != null && cd.toLowerCase().contains("filename")) {
            try {
                int i = cd.toLowerCase().indexOf("filename=");
                if (i >= 0) {
                    String raw = cd.substring(i + 9).trim();
                    if (raw.startsWith("\"")) raw = raw.substring(1);
                    int end = raw.indexOf('"'); if (end < 0) end = raw.indexOf(';');
                    if (end < 0) end = raw.length();
                    raw = raw.substring(0, end).trim();
                    if (!raw.isEmpty()) base = raw;
                }
            } catch (Exception ignored) {}
        }
        if (mime != null && !mime.isEmpty()) {
            String m = mime.trim().toLowerCase();
            if (!m.equals("application/octet-stream") && !m.equals("application/force-download"))
                ext = mimeExt(m);
        }
        if (base == null) {
            try {
                String path = Uri.parse(url).getPath();
                if (path != null && path.contains("/")) {
                    String seg = path.substring(path.lastIndexOf('/') + 1);
                    if (seg.contains("?")) seg = seg.substring(0, seg.indexOf('?'));
                    if (!seg.isEmpty()) base = seg;
                }
            } catch (Exception ignored) {}
        }
        if (base != null && ext != null && base.toLowerCase().endsWith("." + ext))
            return clean(base);
        String fb = "file_" + System.currentTimeMillis();
        if (base != null) { int dot = base.lastIndexOf('.'); fb = (dot > 0) ? base.substring(0, dot) : base; }
        String fe;
        if (ext != null) { fe = ext; }
        else if (base != null && base.contains(".")) {
            String e = base.substring(base.lastIndexOf('.') + 1).toLowerCase();
            fe = isServerExt(e) ? "bin" : e;
        } else { fe = "bin"; }
        return clean(fb + "." + fe);
    }

    private boolean isServerExt(String e) {
        if (e == null) return false;
        switch (e) {
            case "php": case "php3": case "php5": case "php7":
            case "asp": case "aspx": case "jsp": case "cfm":
            case "cgi": case "pl": case "py": case "rb": return true;
            default: return false;
        }
    }

    private String clean(String s) {
        if (s == null || s.isEmpty()) return "download_" + System.currentTimeMillis();
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.length() > 80) { int dot = s.lastIndexOf('.'); s = (dot > 0) ? s.substring(0,70) + s.substring(dot) : s.substring(0,80); }
        return s;
    }

    private String mimeExt(String m) {
        if (m.equals("audio/mpeg")||m.equals("audio/mp3")||m.equals("audio/x-mp3")) return "mp3";
        if (m.equals("audio/mp4")||m.equals("audio/x-m4a"))  return "m4a";
        if (m.equals("audio/ogg")||m.equals("audio/x-ogg"))  return "ogg";
        if (m.equals("audio/wav")||m.equals("audio/x-wav"))  return "wav";
        if (m.equals("audio/flac")||m.equals("audio/x-flac")) return "flac";
        if (m.equals("audio/aac"))  return "aac";
        if (m.equals("audio/amr"))  return "amr";
        if (m.equals("audio/x-ms-wma")) return "wma";
        if (m.equals("audio/opus")) return "opus";
        if (m.equals("audio/3gpp")) return "3gp";
        if (m.startsWith("audio/")) return "mp3";
        if (m.equals("video/mp4"))  return "mp4";
        if (m.equals("video/x-matroska")) return "mkv";
        if (m.equals("video/webm")) return "webm";
        if (m.equals("video/x-msvideo")||m.equals("video/avi")) return "avi";
        if (m.equals("video/quicktime")) return "mov";
        if (m.equals("video/x-ms-wmv")) return "wmv";
        if (m.equals("video/x-flv"))    return "flv";
        if (m.equals("video/3gpp"))     return "3gp";
        if (m.equals("video/mpeg"))     return "mpeg";
        if (m.startsWith("video/"))     return "mp4";
        if (m.equals("image/jpeg")||m.equals("image/jpg")) return "jpg";
        if (m.equals("image/png"))  return "png";
        if (m.equals("image/gif"))  return "gif";
        if (m.equals("image/webp")) return "webp";
        if (m.equals("image/bmp"))  return "bmp";
        if (m.equals("image/svg+xml")) return "svg";
        if (m.equals("image/heic")||m.equals("image/heif")) return "heic";
        if (m.startsWith("image/")) return "jpg";
        if (m.equals("application/pdf"))    return "pdf";
        if (m.equals("application/msword")) return "doc";
        if (m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return "docx";
        if (m.equals("application/vnd.ms-excel")) return "xls";
        if (m.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) return "xlsx";
        if (m.equals("application/vnd.ms-powerpoint")) return "ppt";
        if (m.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) return "pptx";
        if (m.equals("application/rtf")||m.equals("text/rtf")) return "rtf";
        if (m.equals("application/epub+zip")) return "epub";
        if (m.equals("application/zip")||m.equals("application/x-zip-compressed")) return "zip";
        if (m.equals("application/x-rar-compressed")||m.equals("application/vnd.rar")) return "rar";
        if (m.equals("application/x-7z-compressed")) return "7z";
        if (m.equals("application/x-tar")) return "tar";
        if (m.equals("application/gzip")||m.equals("application/x-gzip")) return "gz";
        if (m.equals("application/vnd.android.package-archive")||m.equals("application/x-apk")) return "apk";
        if (m.equals("application/x-msdownload")) return "exe";
        if (m.equals("application/x-iso9660-image")) return "iso";
        if (m.equals("text/plain"))  return "txt";
        if (m.equals("text/html"))   return "html";
        if (m.equals("text/css"))    return "css";
        if (m.equals("text/javascript")||m.equals("application/javascript")) return "js";
        if (m.equals("application/json")) return "json";
        if (m.equals("application/xml")||m.equals("text/xml")) return "xml";
        if (m.equals("text/csv"))    return "csv";
        if (m.equals("font/ttf"))    return "ttf";
        if (m.equals("font/otf"))    return "otf";
        if (m.equals("font/woff")||m.equals("application/font-woff")) return "woff";
        if (m.equals("font/woff2"))  return "woff2";
        return null;
    }

    // ── Downloads List ─────────────────────────────
    private void showDownloads() {
        if (isDestroyed) return;
        Set<String> set = prefs().getStringSet("dl", new HashSet<String>());
        if (set.isEmpty()) { Toast.makeText(this, "No downloads yet", Toast.LENGTH_SHORT).show(); return; }
        final Dialog d = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        d.setContentView(R.layout.download_view);
        final ArrayList<String> list = new ArrayList<String>(set);
        ListView lv = (ListView) d.findViewById(R.id.downloadListView);
        if (lv == null) { d.show(); return; }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.download_item, R.id.downloadName, list) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                String raw = list.get(pos);
                String name = raw.contains("|") ? raw.split("\\|", 2)[0] : raw;
                String furl = raw.contains("|") ? raw.split("\\|", 2)[1] : "";
                TextView nm = (TextView) v.findViewById(R.id.downloadName);
                TextView dm = (TextView) v.findViewById(R.id.downloadDomain);
                if (nm != null) nm.setText(name);
                if (dm != null) { try { String h = Uri.parse(furl).getHost(); dm.setText(h != null ? h : "Local"); } catch (Exception e) { dm.setText("Local"); } }
                return v;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> pa, View v, int pos, long id) {
                String raw = list.get(pos); openFile(raw.contains("|") ? raw.split("\\|",2)[0] : raw);
            }
        });
        lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> pa, View v, final int pos, long id) {
                final String raw  = list.get(pos);
                final String name = raw.contains("|") ? raw.split("\\|",2)[0] : raw;
                new AlertDialog.Builder(MainActivity.this).setTitle(name)
                    .setItems(new String[]{"Share","Delete"}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dl, int w) {
                            if (w == 0) { shareFile(name); }
                            else {
                                Set<String> cur = new HashSet<String>(prefs().getStringSet("dl", new HashSet<String>()));
                                cur.remove(raw); prefs().edit().putStringSet("dl", cur).apply();
                                d.dismiss(); Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).show();
                return true;
            }
        });
        View close = d.findViewById(R.id.btnCloseDownloads);
        if (close != null) close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        d.show();
    }

    private void openFile(String name) {
        try {
            File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
            if (!f.exists()) { Toast.makeText(this,"File not found",Toast.LENGTH_SHORT).show(); return; }
            String mime = name.endsWith(".apk") ? "application/vnd.android.package-archive" : "*/*";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(f), mime);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { Toast.makeText(this,"Cannot open file",Toast.LENGTH_SHORT).show(); }
    }

    private void shareFile(String name) {
        try {
            File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("*/*"); i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) { Toast.makeText(this,"Cannot share",Toast.LENGTH_SHORT).show(); }
    }

    // ── History ────────────────────────────────────
    private void showHistory() {
        if (isDestroyed) return;
        Set<String> set = prefs().getStringSet("history", new HashSet<String>());
        if (set.isEmpty()) { Toast.makeText(this,"No history",Toast.LENGTH_SHORT).show(); return; }
        final Dialog d = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        d.setContentView(R.layout.history_view);
        final ArrayList<String> data = new ArrayList<String>(set);
        ListView lv = (ListView) d.findViewById(R.id.historyListView);
        if (lv != null) {
            lv.setAdapter(new ArrayAdapter<String>(this, R.layout.history_item, R.id.historyUrl, data));
            lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                public void onItemClick(android.widget.AdapterView<?> pa, View v, int pos, long id) {
                    if (webView != null) webView.loadUrl(data.get(pos)); d.dismiss();
                }
            });
        }
        View clear = d.findViewById(R.id.btnClearHistory);
        if (clear != null) clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { prefs().edit().remove("history").apply(); Toast.makeText(MainActivity.this,"Cleared",Toast.LENGTH_SHORT).show(); d.dismiss(); }
        });
        View close = d.findViewById(R.id.btnCloseHistory);
        if (close != null) close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        d.show();
    }

    private void saveHistory(String url) {
        Set<String> h = new HashSet<String>(prefs().getStringSet("history", new HashSet<String>()));
        h.add(url);
        if (h.size() > 150) { ArrayList<String> tmp = new ArrayList<String>(h); h = new HashSet<String>(tmp.subList(tmp.size()-150, tmp.size())); }
        prefs().edit().putStringSet("history", h).apply();
    }

    // ── Bookmarks ──────────────────────────────────
    private void showBookmarkOptions() {
        if (isDestroyed) return;
        new AlertDialog.Builder(this).setItems(new String[]{"Add Bookmark","View Bookmarks"},
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { if (w==0) saveBookmark(); else showBookmarks(); }
            }).show();
    }

    private void saveBookmark() {
        String url = currentUrl();
        if (url.isEmpty()) { Toast.makeText(this,"No page loaded",Toast.LENGTH_SHORT).show(); return; }
        Set<String> b = new HashSet<String>(prefs().getStringSet("bm", new HashSet<String>()));
        b.add(url); prefs().edit().putStringSet("bm", b).apply();
        Toast.makeText(this,"Bookmark saved!",Toast.LENGTH_SHORT).show();
    }

    private void showBookmarks() {
        if (isDestroyed) return;
        Set<String> set = prefs().getStringSet("bm", new HashSet<String>());
        if (set.isEmpty()) { Toast.makeText(this,"No bookmarks",Toast.LENGTH_SHORT).show(); return; }
        final Dialog d = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        d.setContentView(R.layout.bookmark_view);
        final ArrayList<String> data = new ArrayList<String>(set);
        ListView lv = (ListView) d.findViewById(R.id.bookmarkListView);
        if (lv != null) {
            lv.setAdapter(new ArrayAdapter<String>(this, R.layout.bookmark_item, R.id.bookmarkUrl, data));
            lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                public void onItemClick(android.widget.AdapterView<?> pa, View v, int pos, long id) {
                    if (webView != null) webView.loadUrl(data.get(pos)); d.dismiss();
                }
            });
        }
        View close = d.findViewById(R.id.btnCloseBookmarks);
        if (close != null) close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        d.show();
    }

    private SharedPreferences prefs() { return getSharedPreferences("ZummData", MODE_PRIVATE); }

    @Override protected void onPause()   { super.onPause();   if (webView != null) webView.onPause(); }
    @Override protected void onResume()  { super.onResume();  if (webView != null) webView.onResume(); }
    @Override protected void onDestroy() {
        isDestroyed = true;
        if (webView != null) { webView.stopLoading(); webView.clearHistory(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
