package com.faa.faadl;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 1001;
    private static final int REQ_QR_SCAN = 1002;

    private EditText urlInput;
    private EditText qrUrlInput;
    private Spinner formatSpin;
    private Spinner qrFormatSpin;
    private Button btnDownload;
    private Button btnQrDownload;
    private Button btnQrScan;
    private Button tabManual;
    private Button tabQr;
    private Button tabHistory;
    private Button tabDebug;
    private LinearLayout pageManual;
    private LinearLayout pageQr;
    private LinearLayout pageHistory;
    private LinearLayout pageDebug;
    private ProgressBar progressBar;
    private TextView progressPct;
    private TextView statusText;
    private TextView logView;
    private TextView historyView;
    private TextView binStatus;
    private ScrollView logScroll;
    private Handler ui = new Handler(Looper.getMainLooper());

    private File binDir;
    private File ffmpegFile;
    private File ytdlpFile;
    private File nodeFile;
    private File lastResult = null;
    private volatile boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        askPerms();
        maybeAskRoot();
        new Thread(new Runnable() {
            @Override public void run() {
                extractBins();
                ui.post(new Runnable() {
                    @Override public void run() {
                        updateBinStatus();
                        refreshHistory();
                    }
                });
            }
        }).start();
    }

    // ---------- UI (tema light blue, programmatic biar aapt simpel) ----------
    private void buildUI() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#E0F2FE"));

        TextView title = new TextView(this);
        title.setText("Faa DL");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#0284C7"));
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Download audio/video dari YouTube");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#475569"));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = lp(-1, -2);
        slp.bottomMargin = dp(12);
        root.addView(sub, slp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(cardBg());
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabManual = tabBtn("Manual");
        tabQr = tabBtn("Scan QR");
        tabHistory = tabBtn("History");
        tabDebug = tabBtn("Debug");
        tabs.addView(tabManual, lpw(1));
        tabs.addView(tabQr, lpw(1));
        tabs.addView(tabHistory, lpw(1));
        tabs.addView(tabDebug, lpw(1));
        LinearLayout.LayoutParams tlp = lp(-1, -2);
        tlp.bottomMargin = dp(12);
        root.addView(tabs, tlp);

        // --- page manual ---
        pageManual = new LinearLayout(this);
        pageManual.setOrientation(LinearLayout.VERTICAL);
        pageManual.addView(label("URL YouTube"));
        urlInput = new EditText(this);
        urlInput.setHint("https://youtube.com/watch?v=...");
        urlInput.setBackground(cardBg());
        urlInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        pageManual.addView(urlInput, lp(-1, -2));
        pageManual.addView(label("Format"));
        formatSpin = new Spinner(this);
        formatSpin.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"MP3 (Audio)", "Video (MP4)"}));
        pageManual.addView(formatSpin, lp(-1, -2));
        btnDownload = primaryBtn("Download");
        LinearLayout.LayoutParams blp = lp(-1, -2);
        blp.topMargin = dp(10);
        pageManual.addView(btnDownload, blp);
        root.addView(pageManual, lp(-1, -2));

        // --- page QR ---
        pageQr = new LinearLayout(this);
        pageQr.setOrientation(LinearLayout.VERTICAL);
        pageQr.setVisibility(View.GONE);
        btnQrScan = outlineBtn("Scan QR via Kamera / ZXing");
        pageQr.addView(btnQrScan, lp(-1, -2));
        pageQr.addView(label("Atau masukkan URL manual"));
        qrUrlInput = new EditText(this);
        qrUrlInput.setHint("https://youtube.com/watch?v=...");
        qrUrlInput.setBackground(cardBg());
        qrUrlInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        pageQr.addView(qrUrlInput, lp(-1, -2));
        pageQr.addView(label("Format"));
        qrFormatSpin = new Spinner(this);
        qrFormatSpin.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"MP3 (Audio)", "Video (MP4)"}));
        pageQr.addView(qrFormatSpin, lp(-1, -2));
        btnQrDownload = primaryBtn("Download");
        LinearLayout.LayoutParams qlp = lp(-1, -2);
        qlp.topMargin = dp(10);
        pageQr.addView(btnQrDownload, qlp);
        root.addView(pageQr, lp(-1, -2));

        // --- page history ---
        pageHistory = new LinearLayout(this);
        pageHistory.setOrientation(LinearLayout.VERTICAL);
        pageHistory.setVisibility(View.GONE);
        TextView hTitle = new TextView(this);
        hTitle.setText("File terunduh  (/sdcard/Download/FaaDL)");
        hTitle.setTextSize(13);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hTitle.setTextColor(Color.parseColor("#475569"));
        pageHistory.addView(hTitle, lp(-1, -2));
        historyView = new TextView(this);
        historyView.setText("Belum ada file.");
        historyView.setTextSize(13);
        historyView.setBackground(cardBg());
        historyView.setPadding(dp(12), dp(12), dp(12), dp(12));
        pageHistory.addView(historyView, lp(-1, -2));
        Button btnClear = outlineBtn("Hapus semua");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearHistory(); }
        });
        pageHistory.addView(btnClear, lp(-1, -2));
        Button btnOpenFolder = outlineBtn("Buka folder Download");
        btnOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDownloadFolder(); }
        });
        pageHistory.addView(btnOpenFolder, lp(-1, -2));
        root.addView(pageHistory, lp(-1, -2));

        // --- page debug (diagnostik + tes adb) ---
        pageDebug = new LinearLayout(this);
        pageDebug.setOrientation(LinearLayout.VERTICAL);
        pageDebug.setVisibility(View.GONE);
        TextView dTitle = new TextView(this);
        dTitle.setText("Diagnostik (hasil juga masuk Log)");
        dTitle.setTextSize(13);
        dTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dTitle.setTextColor(Color.parseColor("#475569"));
        pageDebug.addView(dTitle, lp(-1, -2));
        pageDebug.addView(dbgBtn("Tes Python", 0), lp(-1, -2));
        pageDebug.addView(dbgBtn("Tes ffmpeg", 1), lp(-1, -2));
        pageDebug.addView(dbgBtn("Tes innertube (URL di tab Manual)", 2), lp(-1, -2));
        pageDebug.addView(dbgBtn("Tes yt-dlp --version", 3), lp(-1, -2));
        pageDebug.addView(dbgBtn("Simpan log ke file", 4), lp(-1, -2));
        root.addView(pageDebug, lp(-1, -2));

        // --- progress ---
        LinearLayout progCard = new LinearLayout(this);
        progCard.setOrientation(LinearLayout.VERTICAL);
        progCard.setBackground(cardBg());
        progCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams plp = lp(-1, -2);
        plp.topMargin = dp(12);
        statusText = new TextView(this);
        statusText.setText("Siap.");
        statusText.setTextSize(13);
        progCard.addView(statusText, lp(-1, -2));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progCard.addView(progressBar, lp(-1, dp(8)));
        progressPct = new TextView(this);
        progressPct.setText("0%");
        progressPct.setTextColor(Color.parseColor("#0284C7"));
        progressPct.setTypeface(Typeface.DEFAULT_BOLD);
        progCard.addView(progressPct, lp(-1, -2));
        binStatus = new TextView(this);
        binStatus.setTextSize(11);
        binStatus.setTextColor(Color.parseColor("#475569"));
        binStatus.setText("ffmpeg: ... | yt-dlp: ... | node: ...");
        progCard.addView(binStatus, lp(-1, -2));
        root.addView(progCard, plp);

        // --- log ---
        TextView logTitle = new TextView(this);
        logTitle.setText("Log");
        logTitle.setTextSize(12);
        logTitle.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams llp = lp(-1, -2);
        llp.topMargin = dp(12);
        root.addView(logTitle, llp);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setBackground(cardBg());
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll = new ScrollView(this);
        logScroll.addView(logView, lp(-1, -2));
        LinearLayout.LayoutParams slog = lp(-1, dp(160));
        slog.topMargin = dp(4);
        root.addView(logScroll, slog);

        ScrollView outer = new ScrollView(this);
        outer.addView(root);

        setContentView(outer);

        tabManual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(0); }
        });
        tabQr.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });
        tabHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(2); refreshHistory(); }
        });
        tabDebug.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(3); }
        });
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String u = urlInput.getText().toString().trim();
                boolean mp3 = formatSpin.getSelectedItemPosition() == 0;
                startDownload(u, mp3);
            }
        });
        btnQrDownload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String u = qrUrlInput.getText().toString().trim();
                boolean mp3 = qrFormatSpin.getSelectedItemPosition() == 0;
                startDownload(u, mp3);
            }
        });
        btnQrScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryQrScan(); }
        });
        selectTabVisual(tabManual);
    }

    private void showTab(int i) {
        pageManual.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        pageQr.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        pageHistory.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        pageDebug.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
        selectTabVisual(i == 0 ? tabManual : (i == 1 ? tabQr : (i == 2 ? tabHistory : tabDebug)));
    }

    private void selectTabVisual(Button active) {
        Button[] all = new Button[]{tabManual, tabQr, tabHistory, tabDebug};
        for (int i = 0; i < all.length; i++) {
            if (all[i] == active) {
                all[i].setBackgroundColor(Color.parseColor("#FFFFFF"));
                all[i].setTextColor(Color.parseColor("#0C4A6E"));
            } else {
                all[i].setBackgroundColor(Color.TRANSPARENT);
                all[i].setTextColor(Color.parseColor("#64748B"));
            }
        }
    }

    private Button tabBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setTextColor(Color.parseColor("#64748B"));
        return b;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(8);
        p.bottomMargin = dp(4);
        v.setLayoutParams(p);
        return v;
    }

    private Button primaryBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#0284C7"), Color.parseColor("#0D9488")});
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        return b;
    }

    private Button outlineBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.parseColor("#0284C7"));
        b.setBackground(cardBg());
        return b;
    }

    private GradientDrawable cardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor("#FFFFFF"));
        d.setCornerRadius(dp(10));
        d.setStroke(1, Color.parseColor("#CBD5E1"));
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        int ww = (w == -1) ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        int hh = (h == -1) ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (h > 0) hh = h;
        return new LinearLayout.LayoutParams(ww, hh);
    }

    private LinearLayout.LayoutParams lpw(int weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return p;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    // ---------- debug ----------
    private Button dbgBtn(String t, final int id) {
        Button b = outlineBtn(t);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runDebug(id); }
        });
        return b;
    }

    private void runDebug(final int id) {
        if (id == 4) { saveLogFile(); return; }
        final String url = urlInput.getText() != null
                ? urlInput.getText().toString().trim() : "";
        log("== debug #" + id + " mulai ==");
        android.util.Log.d("FaaDL", "debug #" + id);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (id == 0) dbgPython();
                    else if (id == 1) dbgFfmpeg();
                    else if (id == 2) dbgTube(url);
                    else if (id == 3) dbgYtdlp();
                } catch (Exception e) {
                    postLog("debug ERROR: " + e.getMessage());
                    android.util.Log.d("FaaDL", "debug ERROR " + e.getMessage());
                }
                postLog("== debug #" + id + " selesai ==");
            }
        }).start();
    }

    private final DlEngine.Listener dbgL = new DlEngine.Listener() {
        @Override public void onLog(final String s) { postLog(s); }
        @Override public void onProgress(final int pct, final String msg) { }
    };

    private void dbgPython() throws Exception {
        String sys = PyDl.probeSystem(dbgL);
        if (sys != null) {
            String[] r = DlEngine.runCapture(
                    arr(sys, "--version"), null, 500);
            postLog("system python: rc=" + r[0] + " " + firstLine(r[1]));
            return;
        }
        if (PyDl.isArm64()) {
            File interp = PyDl.nlFile(this, "libfpy.so");
            java.util.Map<String, String> env = null;
            if (interp == null) {
                File fpy = PyDl.stageFaapy(this, dbgL);
                if (fpy == null) throw new Exception("faapy tidak ada");
                interp = new File(fpy, "bin/python3.14");
                File lib = new File(fpy, "lib64");
                env = new java.util.HashMap<String, String>();
                env.put("PYTHONHOME", lib.getAbsolutePath());
                env.put("LD_LIBRARY_PATH",
                        new File(lib, "lib/python3.14/lib-dynload").getAbsolutePath()
                        + ":" + lib.getAbsolutePath());
            } else {
                postLog("python native (libfpy.so)");
            }
            String[] r = DlEngine.runCapture(arr(interp.getAbsolutePath(),
                    "-c", "import ssl,json;print('py-ok')"), env, 500);
            postLog("faapy: rc=" + r[0] + " " + firstLine(r[1]));
            android.util.Log.d("FaaDL", "dbg faapy rc=" + r[0]);
        } else {
            postLog("perangkat 32-bit: pakai tab Tes yt-dlp untuk cek python AAR");
        }
    }

    private void dbgFfmpeg() throws Exception {
        preferNativeFfmpeg();
        if (ffmpegFile == null || !ffmpegFile.exists())
            throw new Exception("ffmpeg file tidak ada: "
                    + (ffmpegFile == null ? "?" : ffmpegFile.getAbsolutePath()));
        postLog("ffmpeg: " + ffmpegFile.length() / 1024 + "KB exec=" + ffmpegFile.canExecute());
        String[] r = DlEngine.runCapture(arr(ffmpegFile.getAbsolutePath(), "-version"), null, 800);
        postLog("ffmpeg rc=" + r[0]);
        String[] lines = r[1].split("\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) postLog(trim(lines[i], 150));
        android.util.Log.d("FaaDL", "dbg ffmpeg rc=" + r[0]);
    }

    private void dbgTube(String url) {
        if (url == null || url.length() == 0) url = "https://www.youtube.com/watch?v=Oreek8z0yxk";
        postLog("tes innertube: " + url);
        String rep = DlEngine.debugReport(url, dbgL);
        String[] lines = rep.split("\n");
        for (int i = 0; i < lines.length; i++) postLog(lines[i]);
        android.util.Log.d("FaaDL", "dbg tube " + firstLine(rep).replace('\n', '|'));
    }

    private void dbgYtdlp() throws Exception {
        if (ytdlpFile == null || !ytdlpFile.exists())
            throw new Exception("yt-dlp file tidak ada");
        String sys = PyDl.probeSystem(dbgL);
        ArrayList<String> cmd = new ArrayList<String>();
        Map<String, String> env = null;
        if (sys != null) {
            cmd.add(sys);
        } else if (PyDl.isArm64()) {
            File interp = PyDl.nlFile(this, "libfpy.so");
            File lib = null;
            if (interp == null) {
                File fpy = PyDl.stageFaapy(this, dbgL);
                if (fpy == null) throw new Exception("faapy tidak ada");
                interp = new File(fpy, "bin/python3.14");
                lib = new File(fpy, "lib64");
            } else {
                postLog("python native (libfpy.so)");
                try {
                    lib = new File(new File(getFilesDir(), "fpy"), "lib64");
                    if (!lib.exists()) { PyDl.stageFaapy(this, dbgL); }
                } catch (Exception ignored) {}
            }
            cmd.add(interp.getAbsolutePath());
            env = new java.util.HashMap<String, String>();
            if (lib != null && lib.exists()) {
                env.put("PYTHONHOME", lib.getAbsolutePath());
                env.put("LD_LIBRARY_PATH",
                        new File(lib, "lib/python3.14/lib-dynload").getAbsolutePath()
                        + ":" + lib.getAbsolutePath());
            }
            try {
                String nl = PyDl.nlDir(this);
                if (nl != null) env.put("PYTHONPATH", nl);
            } catch (Exception ignored) {}
        } else {
            throw new Exception("32-bit: butuh staging AAR (pakai download biasa)");
        }
        cmd.add(ytdlpFile.getAbsolutePath());
        cmd.add("--version");
        cmd.add("--no-cache-dir");
        String[] r = DlEngine.runCapture(cmd, env, 800);
        postLog("yt-dlp rc=" + r[0] + " versi=" + firstLine(r[1]));
        android.util.Log.d("FaaDL", "dbg ytdlp rc=" + r[0]);
    }

    private void saveLogFile() {
        try {
            File d = getDownloadDir();
            String name = "faadl-debug-" + System.currentTimeMillis() + ".txt";
            File f = new File(d, name);
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write("Faa DL debug " + new java.util.Date().toString() + "\n");
            w.write("bin: " + binInfo(ffmpegFile) + " | " + binInfo(ytdlpFile) + "\n\n");
            w.write(logView.getText().toString());
            w.close();
            String shown = f.getAbsolutePath();
            if (!legacyDlOk) {
                try {
                    publishToDownloads(f);
                    shown = "/sdcard/Download/FaaDL/" + name;
                } catch (Exception e) {
                    toast("Publish gagal: " + e.getMessage());
                }
            }
            toast("Log tersimpan: " + shown);
            android.util.Log.d("FaaDL", "log saved " + shown);
        } catch (Exception e) {
            toast("Gagal simpan log: " + e.getMessage());
        }
    }

    private ArrayList<String> arr(String... xs) {
        ArrayList<String> a = new ArrayList<String>();
        for (int i = 0; i < xs.length; i++) a.add(xs[i]);
        return a;
    }

    private String firstLine(String s) {
        if (s == null) return "-";
        int i = s.indexOf('\n');
        String r = i < 0 ? s : s.substring(0, i);
        return trim(r, 150);
    }

    // ---------- root ----------
    private void maybeAskRoot() {
        final android.content.SharedPreferences sp =
                getSharedPreferences("faadl", MODE_PRIVATE);
        if (sp.getBoolean("root_asked", false)) {
            if (sp.getBoolean("root_ok", false)) {
                log("mode root: aktif");
                android.util.Log.d("FaaDL", "root mode on");
            }
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Akses root?")
                .setMessage("Faa DL jalan penuh dengan root: eksekusi yang ditolak "
                        + "sistem otomatis diulang via superuser.\n\nBerikan akses root?")
                .setPositiveButton("Izinkan", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        requestRoot(sp);
                    }
                })
                .setNegativeButton("Tanpa root", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        sp.edit().putBoolean("root_asked", true)
                                .putBoolean("root_ok", false).apply();
                        log("mode non-root");
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void requestRoot(final android.content.SharedPreferences sp) {
        toast("Menunggu izin superuser...");
        new Thread(new Runnable() {
            @Override public void run() {
                Root.recheck();
                final boolean ok = Root.haveRoot();
                sp.edit().putBoolean("root_asked", true).putBoolean("root_ok", ok).apply();
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (ok) {
                            log("root OK (superuser)", true);
                            toast("Akses root diberikan");
                        } else {
                            log("root tidak tersedia, mode non-root", false);
                            toast("Root tidak tersedia");
                        }
                    }
                });
            }
        }).start();
    }

    // ---------- permission ----------
    private void askPerms() {
        if (Build.VERSION.SDK_INT < 29) {
            String[] perms = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA};
            ArrayList<String> need = new ArrayList<String>();
            for (int i = 0; i < perms.length; i++) {
                if (checkSelfPermission(perms[i]) != PackageManager.PERMISSION_GRANTED) need.add(perms[i]);
            }
            if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
        } else {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM);
            }
        }
    }

    // ---------- folder download: /sdcard/Download/FaaDL ----------
    // Android 11+ (scoped storage): tulis langsung ke Download publik DITOLAK
    // (EACCES). Deteksi sekali: kalau publik bisa ditulis -> pakai langsung,
    // kalau tidak -> kerja di staging internal lalu publish via MediaStore
    // (tetap muncul di /sdcard/Download/FaaDL).
    private boolean legacyDlOk = false;
    private boolean storageProbed = false;
    private File stageDir = null;

    private File getLegacyDir() {
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(dl, "FaaDL");
    }

    private boolean canWriteDir(File d) {
        try {
            d.mkdirs();
            // tulis 1MB beneran: probe 1 byte bisa lolos padahal tulis besar ditolak
            File t = new File(d, ".writetest");
            java.io.FileOutputStream o = new java.io.FileOutputStream(t);
            byte[] buf = new byte[32768];
            for (int i = 0; i < 32; i++) o.write(buf);
            o.close();
            t.delete();
            return true;
        } catch (Exception e) {
            android.util.Log.d("FaaDL", "probe tulis gagal: " + e.getMessage());
            return false;
        }
    }

    private void probeStorage() {
        if (storageProbed) return;
        storageProbed = true;
        legacyDlOk = canWriteDir(getLegacyDir());
        if (!legacyDlOk) {
            stageDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FaaDL-stage");
            try { stageDir.mkdirs(); } catch (Exception ignored) {}
        }
        String msg = "storage: " + (legacyDlOk ? "publik langsung" : "staging+MediaStore");
        log(msg);
        android.util.Log.d("FaaDL", msg);
    }

    private File getDownloadDir() {
        probeStorage();
        if (legacyDlOk) return getLegacyDir();
        if (stageDir != null) return stageDir;
        File f = new File(getCacheDir(), "FaaDL-stage");
        try { f.mkdirs(); } catch (Exception ignored) {}
        return f;
    }

    private String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    /** Salin file stage ke koleksi Download/FaaDL via MediaStore (API 29+). */
    private String publishToDownloads(File src) throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new Exception("MediaStore perlu Android 10+");
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, src.getName());
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeOf(src.getName()));
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/FaaDL");
        android.net.Uri uri = getContentResolver().insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("MediaStore insert gagal");
        java.io.InputStream in = new java.io.FileInputStream(src);
        java.io.OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) { in.close(); throw new Exception("MediaStore open gagal"); }
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        return uri.toString();
    }

    private void openDownloadFolder() {
        try {
            File d = getDownloadDir();
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(d), "resource/folder");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(i, "Buka folder"));
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivity(i);
            } catch (Exception e2) {
                toast("Folder: " + getDownloadDir().getAbsolutePath());
            }
        }
    }

    // ---------- built-in bins (support 64 / 32 / universal) ----------
    // Varian APK:
    //  - arm64     : assets/bin/ffmpeg (arm64) + yt-dlp + node
    //  - arm32     : assets/bin/ffmpeg (arm32) + yt-dlp + node
    //  - universal : assets/bin/ffmpeg-arm64 + ffmpeg-armeabi-v7a (+ yt-dlp, node-*)
    // Runtime otomatis pilih sesuai Build.SUPPORTED_ABIS.
    private boolean isArm64Device() {
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null) {
                for (int i = 0; i < abis.length; i++) {
                    String a = abis[i].toLowerCase();
                    if (a.contains("arm64") || a.contains("x86_64")) return true;
                }
                return false;
            }
        } catch (Exception ignored) {}
        try {
            String arch = System.getProperty("os.arch");
            if (arch != null && arch.toLowerCase().contains("64")) return true;
        } catch (Exception ignored) {}
        return true;
    }

    /**
     * Salin satu binary dari assets. Mendukung file utuh (bin/nama) maupun
     * pecahan (bin/nama.part00, .part01, ...) untuk binary besar.
     * Return false diam-diam bila asset tidak ada (untuk fallback berurutan).
     */
    private boolean stageOne(String base, File dst) {
        try {
            String[] all = getAssets().list("bin");
            ArrayList<String> parts = new ArrayList<String>();
            boolean direct = false;
            if (all != null) {
                for (int i = 0; i < all.length; i++) {
                    if (all[i].equals(base)) direct = true;
                    else if (all[i].startsWith(base + ".part")) parts.add(all[i]);
                }
            }
            Collections.sort(parts);
            if (!parts.isEmpty()) {
                OutputStream out = new FileOutputStream(dst);
                byte[] buf = new byte[32768];
                for (int i = 0; i < parts.size(); i++) {
                    InputStream in = getAssets().open("bin/" + parts.get(i));
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                }
                out.close();
                log("rakit " + base + " dari " + parts.size() + " part");
            } else if (direct) {
                InputStream in = getAssets().open("bin/" + base);
                OutputStream out = new FileOutputStream(dst);
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            } else {
                return false;
            }
            dst.setExecutable(true, false);
            dst.setReadable(true, false);
            return dst.exists() && dst.length() > 0;
        } catch (Exception e) {
            log("copy gagal " + base + ": " + e.getMessage());
            return false;
        }
    }

    private boolean tryCopyBin(String asset, File dst) {
        // asset = "bin/nama" -> stageOne("nama")
        String base = asset.startsWith("bin/") ? asset.substring(4) : asset;
        return stageOne(base, dst);
    }

    private boolean canExec(File f) {
        return f != null && f.exists() && f.length() > 100000 && f.canExecute();
    }

    private void extractBins() {
        binDir = new File(getFilesDir(), "bin");
        try { binDir.mkdirs(); } catch (Exception ignored) {}
        ffmpegFile = new File(binDir, "ffmpeg");
        ytdlpFile = new File(binDir, "yt-dlp");
        nodeFile = new File(binDir, "node");
        boolean arm64 = isArm64Device();

        // PENTING: di varian universal, pilih binary SESUAI ABI dulu.
        // Binary beda-arch yang dipaksa jalan memberi error=2 (ENOENT).
        boolean fOk = false;
        if (arm64) {
            fOk = tryCopyBin("bin/ffmpeg-arm64", ffmpegFile);
            if (!fOk) fOk = tryCopyBin("bin/ffmpeg-aarch64", ffmpegFile);
        } else {
            fOk = tryCopyBin("bin/ffmpeg-armeabi-v7a", ffmpegFile);
            if (!fOk) fOk = tryCopyBin("bin/ffmpeg-arm32", ffmpegFile);
            if (!fOk) fOk = tryCopyBin("bin/ffmpeg-arm", ffmpegFile);
        }
        if (!fOk) fOk = tryCopyBin("bin/ffmpeg", ffmpegFile); // varian single-arch
        // libc++ pendamping ffmpeg (Tyrrrz build butuh ini, kalau tidak: CANNOT LINK)
        File cxxFile = new File(binDir, "libc++_shared.so");
        boolean cOk = false;
        if (arm64) {
            cOk = tryCopyBin("bin/ff-arm64-libc++_shared.so", cxxFile);
        } else {
            cOk = tryCopyBin("bin/ff-armeabi-libc++_shared.so", cxxFile);
        }
        if (!cOk) cOk = tryCopyBin("bin/ff-libc++_shared.so", cxxFile);
        if (!cOk) { try { cxxFile.delete(); } catch (Exception ignored) {} }
        // Pilihan utama di HP ketat: binary dari nativeLibraryDir (lib/ APK,
        // diekstrak PackageManager, lolos SELinux exec). Fallback: filesDir.
        // Dicek ULANG tiap mau dipakai (ekstraksi PM bisa telat dari launch).
        preferNativeFfmpeg();
        try {
            String nl = getApplicationInfo().nativeLibraryDir;
            StringBuilder nls = new StringBuilder("nativelib=" + nl + " [");
            try {
                File[] nfs = new File(nl).listFiles();
                if (nfs != null) {
                    for (int i = 0; i < nfs.length; i++) {
                        if (i > 0) nls.append(",");
                        nls.append(nfs[i].getName());
                    }
                }
            } catch (Exception ignored) {}
            nls.append("]");
            android.util.Log.d("FaaDL", nls.toString());
        } catch (Exception e) {
            android.util.Log.d("FaaDL", "nativelib err " + e.getMessage());
        }
        // yt-dlp: arch-independent, satu file untuk semua varian
        boolean yOk = tryCopyBin("bin/yt-dlp", ytdlpFile);
        if (!yOk) yOk = tryCopyBin("bin/yt-dlp-arm64", ytdlpFile);
        if (!yOk) yOk = tryCopyBin("bin/yt-dlp-arm32", ytdlpFile);
        // node (opsional): boleh tidak ada
        boolean nOk = false;
        if (arm64) nOk = tryCopyBin("bin/node-arm64", nodeFile);
        else nOk = tryCopyBin("bin/node-arm32", nodeFile);
        if (!nOk) nOk = tryCopyBin("bin/node", nodeFile);
        try {
            File www = new File(getFilesDir(), "www");
            www.mkdirs();
        } catch (Exception ignored) {}
        log("bin dir: " + binDir.getAbsolutePath() + (arm64 ? " [arm64]" : " [arm32]"));
        log("ffmpeg: " + binInfo(ffmpegFile) + " | libc++: " + binInfo(cxxFile)
                + " | yt-dlp: " + binInfo(ytdlpFile));
        android.util.Log.d("FaaDL", "bins ffmpeg=" + binInfo(ffmpegFile)
                + " cxx=" + binInfo(cxxFile) + " ytdlp=" + binInfo(ytdlpFile));
        if (!fOk) log("ffmpeg built-in tidak ketemu (coba varian universal/arm lain)");
        if (!yOk) log("yt-dlp built-in tidak ketemu");
    }

    private String binInfo(File f) {
        if (f == null || !f.exists()) return "tidak ada";
        return (f.length() / 1024) + "KB" + (f.canExecute() ? ",exec ok" : ",TIDAK exec");
    }

    private void copyBin(String asset, File dst) {
        tryCopyBin(asset, dst);
    }

    /** Pakai libffmpeg.so native bila sudah terekstrak (dipanggil tiap mau exec). */
    private void preferNativeFfmpeg() {
        try {
            String nl = getApplicationInfo().nativeLibraryDir;
            File nff = new File(nl, "libffmpeg.so");
            if (nff.exists() && nff.length() > 1000000) {
                if (ffmpegFile == null || !ffmpegFile.getAbsolutePath().equals(nff.getAbsolutePath())) {
                    ffmpegFile = nff;
                    log("ffmpeg: pakai native lib");
                    android.util.Log.d("FaaDL", "ffmpeg native ok");
                }
            }
        } catch (Exception e) {
            android.util.Log.d("FaaDL", "preferNative " + e.getMessage());
        }
    }

    private void updateBinStatus() {
        String f = (ffmpegFile != null && ffmpegFile.exists()) ? ("ffmpeg OK (" + (ffmpegFile.length() / 1024) + "KB)") : "ffmpeg -";
        String y = (ytdlpFile != null && ytdlpFile.exists()) ? ("yt-dlp OK (" + (ytdlpFile.length() / 1024) + "KB)") : "yt-dlp -";
        String n = (nodeFile != null && nodeFile.exists() && nodeFile.length() > 4096) ? "node OK" : "node opsional -";
        binStatus.setText(f + " | " + y + " | " + n);
    }

    // ---------- download ----------
    private void startDownload(String url, final boolean mp3) {
        if (url != null) url = url.replaceAll("\\s+", ""); // URL ditempel kadang bawa spasi
        if (url == null || url.trim().length() == 0) { toast("Masukkan URL dulu"); return; }
        final String furl = url;
        downloading = true;
        btnDownload.setEnabled(false);
        btnQrDownload.setEnabled(false);
        setProgress(0, "Memproses URL...");
        log("URL: " + url);
        log("Format: " + (mp3 ? "MP3 (Audio)" : "Video (MP4)"));
        log("Tujuan: /sdcard/Download/FaaDL/");

        new Thread(new Runnable() {
            @Override public void run() {
                final File outDir = getDownloadDir();
                boolean ok = false;
                String err = null;
                try {
                    ok = runYtDlp(furl, mp3, outDir);
                } catch (Exception e) {
                    err = e.getMessage();
                    // tulis langsung ditolak di tengah jalan -> alih ke MediaStore, coba sekali lagi
                    if (legacyDlOk && err != null
                            && (err.contains("EACCES") || err.contains("Permission denied"))) {
                        postLog("Tulis langsung ditolak, alih ke MediaStore...");
                        android.util.Log.d("FaaDL", "retry via MediaStore");
                        legacyDlOk = false;
                        lastResult = null;
                        try {
                            ok = runYtDlp(furl, mp3, getDownloadDir());
                            err = null;
                        } catch (Exception e2) {
                            err = e2.getMessage();
                        }
                    }
                }
                final boolean fok = ok;
                final String ferr = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        downloading = false;
                        btnDownload.setEnabled(true);
                        btnQrDownload.setEnabled(true);
                        if (fok) {
                            String where = outDir.getAbsolutePath();
                            if (!legacyDlOk && lastResult != null && lastResult.exists()) {
                                try {
                                    String uri = publishToDownloads(lastResult);
                                    log("Publish: " + lastResult.getName(), true);
                                    android.util.Log.d("FaaDL", "published " + uri);
                                    lastResult.delete();
                                    where = "/sdcard/Download/FaaDL";
                                } catch (Exception e) {
                                    log("Publish gagal: " + e.getMessage(), false);
                                }
                            }
                            setProgress(100, "Selesai! Cek /sdcard/Download/FaaDL");
                            log("Selesai! File tersimpan di " + where, true);
                            android.util.Log.d("FaaDL", "download OK");
                            try {
                                MediaScan.scanFileHack(MainActivity.this, outDir);
                            } catch (Exception ignored) {}
                            refreshHistory();
                            showTab(2);
                        } else {
                            setProgress(0, "Gagal: " + (ferr == null ? "yt-dlp error" : ferr));
                            log("Gagal: " + (ferr == null ? "lihat log di atas" : ferr), false);
                            android.util.Log.d("FaaDL", "download FAIL " + ferr);
                        }
                    }
                });
            }
        }).start();
    }

    /** Mesin utama: Java murni (innertube) + ffmpeg. yt-dlp hanya fallback. */
    private boolean runYtDlp(String url, boolean mp3, File outDir) throws Exception {
        preferNativeFfmpeg();
        lastResult = null;
        final DlEngine.Listener L = new DlEngine.Listener() {
            @Override public void onLog(final String s) { postLog(s); }
            @Override public void onProgress(final int pct, final String msg) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (pct >= 0) setProgress(pct, msg);
                        else setProgress(progressBar.getProgress(), msg);
                    }
                });
            }
        };
        try {
            File res = DlEngine.download(ffmpegFile, outDir, url, mp3, L);
            postLog("Hasil: " + res.getName() + " (" + human(res.length()) + ")");
            lastResult = res;
            return true;
        } catch (Exception e) {
            postLog("Mesin utama gagal: " + e.getMessage());
            android.util.Log.d("FaaDL", "primary FAIL " + e.getMessage());
            postLog("Coba fallback yt-dlp built-in...");
        }
        // Fallback: yt-dlp via Python (modul Magisk dulu, lalu bundel faapy/3.12).
        if (ytdlpFile == null || !ytdlpFile.exists()) {
            throw new Exception("yt-dlp built-in tidak ada");
        }
        String tmpl = new File(outDir, "%(title)s.%(ext)s").getAbsolutePath();
        ArrayList<String> ytArgs = new ArrayList<String>();
        ytArgs.add("--no-playlist");
        ytArgs.add("--ignore-errors");
        ytArgs.add("--no-warnings");
        ytArgs.add("--extractor-args");
        ytArgs.add("youtube:player_client=android");
        ytArgs.add("-o");
        ytArgs.add(tmpl);
        if (ffmpegFile != null && ffmpegFile.exists()) {
            ytArgs.add("--ffmpeg-location");
            ytArgs.add(ffmpegFile.getAbsolutePath());
        }
        if (mp3) {
            ytArgs.add("-x");
            ytArgs.add("--audio-format");
            ytArgs.add("mp3");
            ytArgs.add("--audio-quality");
            ytArgs.add("192K");
        } else {
            ytArgs.add("-f");
            ytArgs.add("best");
        }
        ytArgs.add(url);

        int rc = PyDl.runYtDlp(MainActivity.this, ytdlpFile, ffmpegFile, ytArgs, L);
        if (rc != 0) throw new Exception("yt-dlp gagal (exit " + rc + "), lihat log di atas");
        lastResult = newestMedia(outDir);
        return true;
    }

    private File newestMedia(File dir) {
        try {
            File[] fs = dir.listFiles();
            File best = null;
            if (fs != null) {
                for (int i = 0; i < fs.length; i++) {
                    String n = fs[i].getName().toLowerCase();
                    if (!fs[i].isFile()) continue;
                    if (!(n.endsWith(".mp3") || n.endsWith(".mp4")
                            || n.endsWith(".m4a") || n.endsWith(".webm"))) continue;
                    if (best == null || fs[i].lastModified() > best.lastModified()) best = fs[i];
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private int parsePct(String l) {
        try {
            int i = l.indexOf('%');
            if (i < 0) return -1;
            int s = i - 1;
            while (s >= 0 && (Character.isDigit(l.charAt(s)) || l.charAt(s) == '.')) s--;
            String num = l.substring(s + 1, i).trim();
            float f = Float.parseFloat(num);
            if (f < 0) return 0;
            if (f > 100) return 100;
            return (int) f;
        } catch (Exception e) { return -1; }
    }

    private String join(ArrayList<String> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(a.get(i));
        }
        return sb.toString();
    }

    private String trim(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n);
    }

    // ---------- history (/sdcard/Download/FaaDL) ----------
    private static class HistRow {
        String name; long size; long time;
        HistRow(String n, long s, long t) { name = n; size = s; time = t; }
    }

    private void refreshHistory() {
        try {
            ArrayList<HistRow> rows = new ArrayList<HistRow>();
            // 1) folder legacy (HP lama / bisa tulis langsung)
            try {
                File d = getLegacyDir();
                File[] fs = d.listFiles();
                if (fs != null) {
                    for (int i = 0; i < fs.length; i++) {
                        if (fs[i].isFile() && !fs[i].getName().startsWith("."))
                            rows.add(new HistRow(fs[i].getName(), fs[i].length(),
                                    fs[i].lastModified()));
                    }
                }
            } catch (Exception ignored) {}
            // 2) MediaStore (HP baru: hasil publish)
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    android.database.Cursor c = getContentResolver().query(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            new String[]{
                                    android.provider.MediaStore.Downloads.DISPLAY_NAME,
                                    android.provider.MediaStore.Downloads.SIZE,
                                    android.provider.MediaStore.Downloads.DATE_MODIFIED},
                            android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?",
                            new String[]{"Download/FaaDL/"}, null);
                    if (c != null) {
                        while (c.moveToNext()) {
                            String nm = c.getString(0);
                            long sz = 0;
                            long dt = 0;
                            try { sz = c.getLong(1); } catch (Exception ignored) {}
                            try { dt = c.getLong(2) * 1000; } catch (Exception ignored) {}
                            rows.add(new HistRow(nm, sz, dt));
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    android.util.Log.d("FaaDL", "hist query " + e.getMessage());
                }
            }
            if (rows.isEmpty()) {
                historyView.setText("Belum ada file.\nFolder: /sdcard/Download/FaaDL");
                return;
            }
            Collections.sort(rows, new Comparator<HistRow>() {
                @Override public int compare(HistRow a, HistRow b) {
                    return Long.compare(b.time, a.time);
                }
            });
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rows.size() && i < 30; i++) {
                HistRow r = rows.get(i);
                sb.append("• ").append(r.name)
                  .append("  (").append(human(r.size)).append(")\n");
            }
            sb.append("\nFolder: /sdcard/Download/FaaDL");
            historyView.setText(sb.toString());
        } catch (Exception e) {
            historyView.setText("Gagal baca history: " + e.getMessage());
        }
    }

    private String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.1f MB", b / 1048576.0);
    }

    private void clearHistory() {
        try {
            int n = 0;
            try {
                File[] fs = getLegacyDir().listFiles();
                if (fs != null) {
                    for (int i = 0; i < fs.length; i++) {
                        if (fs[i].isFile() && fs[i].delete()) n++;
                    }
                }
            } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    n += getContentResolver().delete(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?",
                            new String[]{"Download/FaaDL/"});
                } catch (Exception e) {
                    android.util.Log.d("FaaDL", "hist del " + e.getMessage());
                }
            }
            toast("Dihapus " + n + " file");
            refreshHistory();
        } catch (Exception e) {
            toast("Gagal hapus: " + e.getMessage());
        }
    }

    // ---------- QR ----------
    private void tryQrScan() {
        // Scanner QR built-in (kamera + ZXing vendored, tanpa aplikasi luar)
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM);
                toast("Izinkan kamera lalu tekan scan lagi");
                return;
            }
            startActivityForResult(new Intent(this, QrScanActivity.class), REQ_QR_SCAN);
        } catch (Exception e) {
            toast("Scanner tidak tersedia, masukkan URL manual");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_QR_SCAN && res == RESULT_OK && data != null) {
            String v = data.getStringExtra("SCAN_RESULT");
            if (v != null) {
                qrUrlInput.setText(v);
                urlInput.setText(v);
                toast("QR: " + v);
                log("QR terdeteksi: " + v);
            }
        }
    }

    // ---------- log/progress ----------
    private void setProgress(int pct, String msg) {
        progressBar.setProgress(pct);
        progressPct.setText(pct + "%");
        statusText.setText(msg == null ? "" : msg);
    }

    private void log(final String s) { log(s, false); }
    private void log(final String s, final boolean ok) {
        ui.post(new Runnable() {
            @Override public void run() {
                logView.append((ok ? "✓ " : "• ") + s + "\n");
                logScroll.post(new Runnable() {
                    @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void postLog(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                logView.append(s + "\n");
                logScroll.post(new Runnable() {
                    @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void toast(final String s) {
        ui.post(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); }
        });
    }

    // helper kecil biar file muncul di Download tanpa rescan manual (pakai intent)
    static class MediaScan {
        static void scanFileHack(Activity a, File dir) {
            try {
                Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                i.setData(Uri.fromFile(dir));
                a.sendBroadcast(i);
            } catch (Exception ignored) {}
        }
    }
}
