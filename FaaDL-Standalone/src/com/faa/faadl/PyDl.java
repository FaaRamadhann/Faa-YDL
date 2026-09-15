package com.faa.faadl;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Python built-in untuk menjalankan yt-dlp di HP (tanpa root).
 * Urutan: 1) python sistem dari modul Magisk-Python (/system/bin/python3*),
 *          2) faapy bundel (Python 3.14 arm64, assets/faapy),
 *          3) python AAR 3.12 (arm32, assets/bin/py-*).
 */
public class PyDl {

    private static final String[] SYS_CANDIDATES = new String[]{
            "/system/bin/python3",
            "/system/bin/python3.14",
            "/system/bin/python",
    };

    /** Coba python sistem (modul Magisk). Return path wrapper atau null. */
    public static String probeSystem(DlEngine.Listener L) {
        for (int i = 0; i < SYS_CANDIDATES.length; i++) {
            File f = new File(SYS_CANDIDATES[i]);
            if (!f.exists()) continue;
            try {
                Process p = new ProcessBuilder(
                        f.getAbsolutePath(), "-c",
                        "import ssl,json,urllib.request;print('py-ok')")
                        .redirectErrorStream(true).start();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                String line;
                boolean ok = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("py-ok")) ok = true;
                }
                int rc = p.waitFor();
                if (ok && rc == 0) {
                    L.onLog("python sistem OK: " + f.getAbsolutePath());
                    Log.d("FaaDL", "syspy OK " + f.getAbsolutePath());
                    return f.getAbsolutePath();
                } else {
                    L.onLog("python sistem tidak cocok: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                L.onLog("probe " + f.getAbsolutePath() + " gagal: " + e.getMessage());
            }
        }
        return null;
    }

    public static boolean isArm64() {        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            if (abis != null) {
                for (int i = 0; i < abis.length; i++) {
                    String a = abis[i].toLowerCase();
                    if (a.contains("arm64") || a.contains("x86_64")) return true;
                }
                return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    /** Dir lib native milik aplikasi (diekstrak PackageManager, boleh di-exec). */
    public static String nlDir(Context ctx) {
        try {
            return ctx.getApplicationInfo().nativeLibraryDir;
        } catch (Exception e) {
            return null;
        }
    }

    /** File di nativeLibraryDir bila ada, else null. */
    public static File nlFile(Context ctx, String name) {
        try {
            String d = nlDir(ctx);
            if (d == null) return null;
            File f = new File(d, name);
            if (f.exists()) {
                Log.d("FaaDL", "native lib: " + name);
                return f;
            }
        } catch (Exception e) {
            Log.d("FaaDL", "nl err " + e.getMessage());
        }
        return null;
    }

    // ---------- staging dari assets ----------
    private static void copyStream(InputStream in, File dst) throws Exception {
        dst.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private static int copyTree(AssetManager am, String assetDir, File dstDir,
                                boolean execBin, DlEngine.Listener L) throws Exception {
        String[] list = am.list(assetDir);
        if (list == null) return 0;
        int count = 0;
        for (int i = 0; i < list.length; i++) {
            String sub = assetDir + "/" + list[i];
            File out = new File(dstDir, list[i]);
            // list() kosong = file ATAU dir kosong -> coba buka sebagai file
            boolean isDir = false;
            try {
                String[] sub2 = am.list(sub);
                if (sub2 != null && sub2.length > 0) isDir = true;
            } catch (Exception ignored) {}
            if (isDir) {
                count += copyTree(am, sub, out, execBin, L);
            } else {
                try {
                    copyStream(am.open(sub), out);
                } catch (java.io.FileNotFoundException e) {
                    out.mkdirs(); // dir kosong
                    continue;
                }
                if (execBin && out.getParentFile().getName().equals("bin")) {
                    out.setExecutable(true, false);
                }
                count++;
            }
        }
        return count;
    }

    /** Siapkan faapy (arm64). Return dir fpy atau null. */
    public static File stageFaapy(Context ctx, DlEngine.Listener L) {
        try {
            String[] top = ctx.getAssets().list("");
            boolean ada = false;
            if (top != null) {
                for (int i = 0; i < top.length; i++) {
                    if (top[i].equals("faapy")) { ada = true; break; }
                }
            }
            if (!ada) return null;
            File fpy = new File(ctx.getFilesDir(), "fpy");
            File marker = new File(fpy, ".staged-faapy");
            File interp = new File(fpy, "bin/python3.14");
            if (marker.exists() && interp.exists() && interp.canExecute()) {
                return fpy;
            }
            deleteRec(fpy);
            fpy.mkdirs();
            L.onLog("menyiapkan python bundel (faapy)...");
            int n = copyTree(ctx.getAssets(), "faapy", fpy, true, L);
            interp.setExecutable(true, false);
            interp.setReadable(true, false);
            new FileOutputStream(marker).close();
            L.onLog("faapy siap: " + n + " file");
            return fpy;
        } catch (Exception e) {
            L.onLog("stage faapy gagal: " + e.getMessage());
            return null;
        }
    }

    /** Siapkan python AAR (arm32): loader + stdlib.zip + qjs. Return dir atau null. */
    public static File stageAarPy(Context ctx, boolean arm64, DlEngine.Listener L) {
        try {
            String pre = arm64 ? "bin/py-arm64-" : "bin/py-";
            String alt = arm64 ? null : "bin/py-armeabi-";
            AssetManager am = ctx.getAssets();
            String[] all = am.list("bin");
            String pPy = pick(all, pre + "python");
            String pZip = pick(all, pre + "stdlib");
            String pQjs = pick(all, pre + "qjs");
            if (pPy == null && alt != null) {
                pPy = pick(all, alt + "python");
                pZip = pick(all, alt + "stdlib");
                pQjs = pick(all, alt + "qjs");
            }
            // fallback varian single-arch arm32: bin/py-python
            if (pPy == null) {
                pPy = pick(all, "bin/py-python".substring(4));
                pZip = pick(all, "py-stdlib");
                pQjs = pick(all, "py-qjs");
            }
            if (pPy == null || pZip == null) {
                // loader boleh absen (exec utama dari native lib); stdlib wajib
                if (pZip == null) return null;
            }
            File dir = new File(ctx.getFilesDir(), "apy");
            File marker = new File(dir, ".staged-apy");
            File loader = new File(dir, "python");
            if (marker.exists()
                    && new File(dir, "usr/lib").exists()) {
                return dir;
            }
            deleteRec(dir);
            dir.mkdirs();
            L.onLog("menyiapkan python bundel (3.12)...");
            if (pPy != null) {
                copyAssetBin(am, pPy, loader, L);
                loader.setExecutable(true, false);
            }
            File zip = new File(dir, "stdlib.zip");
            copyAssetBin(am, pZip, zip, L);
            if (pQjs != null) {
                File qjs = new File(dir, "libqjs.so");
                copyAssetBin(am, pQjs, qjs, L);
                qjs.setExecutable(true, false);
            }
            unzipWithLinks(zip, dir, L);
            try { zip.delete(); } catch (Exception ignored) {}
            new FileOutputStream(marker).close();
            L.onLog("python 3.12 siap");
            return dir;
        } catch (Exception e) {
            L.onLog("stage python AAR gagal: " + e.getMessage());
            return null;
        }
    }

    private static String pick(String[] all, String prefix) {
        if (all == null) return null;
        String best = null;
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(prefix)) return all[i];
            if (all[i].startsWith(prefix + ".part")) {
                if (best == null || all[i].compareTo(best) < 0) best = all[i];
            }
        }
        if (best != null) return best;
        // kumpulkan part: panggil via stageParts
        ArrayList<String> parts = new ArrayList<String>();
        for (int i = 0; i < all.length; i++) {
            if (all[i].startsWith(prefix + ".part")) parts.add(all[i]);
        }
        if (parts.isEmpty()) return null;
        Collections.sort(parts);
        return parts.get(0) + "|SPLIT:" + parts.size();
    }

    private static void copyAssetBin(AssetManager am, String spec, File dst,
                                     DlEngine.Listener L) throws Exception {
        int split = spec.indexOf("|SPLIT:");
        if (split < 0) {
            copyStream(am.open("bin/" + spec), dst);
            return;
        }
        String base = spec.substring(0, split);
        int nPart = Integer.parseInt(spec.substring(split + 7));
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[32768];
        for (int i = 0; i < nPart; i++) {
            String nm = String.format(base + ".part%02d", i);
            // base sudah "x.part00" -> potong ".partNN"
            int cut = base.lastIndexOf(".part");
            nm = String.format(base.substring(0, cut) + ".part%02d", i);
            InputStream in = am.open("bin/" + nm);
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
        }
        out.close();
        L.onLog("rakit " + dst.getName() + " dari " + nPart + " part");
    }

    private static void deleteRec(File f) {
        try {
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                if (fs != null) {
                    for (int i = 0; i < fs.length; i++) deleteRec(fs[i]);
                }
            }
            f.delete();
        } catch (Exception ignored) {}
    }

    /** Unzip + resolve symlink dengan cara copy target (Java tak bisa symlink). */
    private static void unzipWithLinks(File zip, File dest, DlEngine.Listener L) throws Exception {
        ZipFile zf = new ZipFile(zip);
        ArrayList<String[]> links = new ArrayList<String[]>();
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            File out = new File(dest, e.getName());
            if (e.isDirectory()) {
                out.mkdirs();
                continue;
            }
            if (e.getSize() == 0) {
                byte[] b = readAll(zf.getInputStream(e));
                String s = new String(b, "UTF-8").trim();
                if (looksLikeLink(s)) {
                    links.add(new String[]{e.getName(), s});
                    continue;
                }
                out.getParentFile().mkdirs();
                new FileOutputStream(out).close();
                continue;
            }
            out.getParentFile().mkdirs();
            copyStream(zf.getInputStream(e), out);
        }
        for (int i = 0; i < links.size(); i++) {
            String name = links.get(i)[0];
            String target = links.get(i)[1];
            File linkFile = new File(dest, name);
            File t = new File(linkFile.getParentFile(), target);
            if (!t.exists()) t = new File(dest, target);
            if (t.exists() && t.isFile()) {
                copyFile(t, linkFile);
            } else {
                L.onLog("link skip: " + name + " -> " + target);
            }
        }
        zf.close();
    }

    private static boolean looksLikeLink(String s) {
        if (s.length() == 0 || s.length() > 256) return false;
        if (s.indexOf('\n') >= 0 || s.indexOf(0) >= 0) return false;
        if (s.startsWith("/") || s.startsWith("usr/") || !s.contains("/")) {
            // target relatif seperti libssl.so.3 atau usr/lib/...
            return s.matches("[A-Za-z0-9_\\.\\-/+]+");
        }
        return s.matches("[A-Za-z0-9_\\.\\-/+]+");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private static void copyFile(File a, File b) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(a);
        copyStream(in, b);
    }

    // ---------- eksekusi yt-dlp ----------
    private static int parsePct(String l) {
        try {
            int i = l.indexOf('%');
            if (i < 0) return -1;
            int s = i - 1;
            while (s >= 0 && (Character.isDigit(l.charAt(s)) || l.charAt(s) == '.')) s--;
            float f = Float.parseFloat(l.substring(s + 1, i).trim());
            if (f < 0) return 0;
            if (f > 100) return 100;
            return (int) f;
        } catch (Exception e) { return -1; }
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n);
    }

    /** Cari + stage file bin/xxx(-yyy) ke filesDir/bin. Return null bila tak ada. */
    private static File findBin(Context ctx, String[] cands, DlEngine.Listener L) {
        try {
            String[] all = ctx.getAssets().list("bin");
            if (all == null) return null;
            for (int c = 0; c < cands.length; c++) {
                for (int i = 0; i < all.length; i++) {
                    if (all[i].equals(cands[c])) {
                        File dir = new File(ctx.getFilesDir(), "bin");
                        dir.mkdirs();
                        File dst = new File(dir, cands[c]);
                        copyStream(ctx.getAssets().open("bin/" + all[i]), dst);
                        dst.setExecutable(true, false);
                        return dst;
                    }
                }
            }
        } catch (Exception e) {
            L.onLog("findBin gagal: " + e.getMessage());
        }
        return null;
    }

    /**
     * Jalankan yt-dlp via python (sistem Magisk dulu, lalu bundel).
     * ytdlpScript = file script yt-dlp yang sudah di-stage. Return exit code.
     */
    public static int runYtDlp(Context ctx, File ytdlpScript, File ffmpeg,
                               ArrayList<String> ytArgs, DlEngine.Listener L) throws Exception {
        String sysPy = probeSystem(L);
        ArrayList<String> cmd = new ArrayList<String>();
        Map<String, String> extraEnv = new java.util.HashMap<String, String>();

        if (sysPy != null) {
            cmd.add(sysPy);
            // decipher YouTube butuh JS runtime walau pakai python sistem
            File qjs = nlFile(ctx, "libqjs.so");
            if (qjs == null) {
                qjs = findBin(ctx,
                        new String[]{"py-arm64-qjs", "py-qjs", "py-armeabi-qjs"}, L);
            }
            if (qjs != null && qjs.exists()) {
                ytArgs.add("--js-runtimes");
                ytArgs.add("quickjs:" + qjs.getAbsolutePath());
            }
        } else if (isArm64()) {
            // stdlib .py selalu dari files (read-only); yang di-exec dari native lib
            File fpy = stageFaapy(ctx, L);
            if (fpy == null) throw new Exception("python bundel (faapy) tidak ada di APK ini");
            File interp = nlFile(ctx, "libfpy.so");
            if (interp == null) {
                interp = new File(fpy, "bin/python3.14");
                if (!interp.canExecute())
                    throw new Exception("faapy tidak bisa dieksekusi: " + interp.getAbsolutePath());
            } else {
                L.onLog("faapy native (libfpy.so)");
            }
            File lib = new File(fpy, "lib64");
            File dyn = new File(lib, "lib/python3.14/lib-dynload");
            cmd.add(interp.getAbsolutePath());
            extraEnv.put("PYTHONHOME", lib.getAbsolutePath());
            extraEnv.put("LD_LIBRARY_PATH", dyn.getAbsolutePath() + ":" + lib.getAbsolutePath());
            String nl = nlDir(ctx);
            if (nl != null) extraEnv.put("PYTHONPATH", nl);
            // quickjs untuk decipher (bundle arm64/universal)
            File qjs = nlFile(ctx, "libqjs.so");
            if (qjs == null) qjs = findBin(ctx, new String[]{"py-arm64-qjs", "py-qjs"}, L);
            if (qjs != null && qjs.exists()) {
                ytArgs.add("--js-runtimes");
                ytArgs.add("quickjs:" + qjs.getAbsolutePath());
            }
            L.onLog("pakai faapy (Python 3.14 bundel)");
        } else {
            File apy = stageAarPy(ctx, false, L);
            if (apy == null) throw new Exception("python bundel (3.12) tidak ada di APK ini");
            File loader = nlFile(ctx, "libfpy32.so");
            if (loader == null) {
                loader = new File(apy, "python");
                if (!loader.canExecute())
                    throw new Exception("python 3.12 tidak bisa dieksekusi");
            } else {
                L.onLog("python 3.12 native (libfpy32.so)");
            }
            File usr = new File(apy, "usr");
            cmd.add(loader.getAbsolutePath());
            extraEnv.put("PYTHONHOME", usr.getAbsolutePath());
            extraEnv.put("HOME", usr.getAbsolutePath());
            extraEnv.put("LD_LIBRARY_PATH", new File(usr, "lib").getAbsolutePath());
            extraEnv.put("SSL_CERT_FILE",
                    new File(usr, "etc/tls/cert.pem").getAbsolutePath());
            extraEnv.put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
            String nl = nlDir(ctx);
            if (nl != null) extraEnv.put("PYTHONPATH", nl);
            File qjs = nlFile(ctx, "libqjs.so");
            if (qjs == null) qjs = new File(apy, "libqjs.so");
            if (qjs.exists()) {
                ytArgs.add("--js-runtimes");
                ytArgs.add("quickjs:" + qjs.getAbsolutePath());
            }
            L.onLog("pakai python 3.12 bundel");
        }

        cmd.add(ytdlpScript.getAbsolutePath());
        cmd.add("--no-cache-dir");
        cmd.addAll(ytArgs);

        StringBuilder sb = new StringBuilder("$");
        for (int i = 0; i < cmd.size(); i++) sb.append(' ').append(cmd.get(i));
        L.onLog(sb.length() > 220 ? sb.substring(0, 220) + "..." : sb.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = new java.util.HashMap<String, String>();
        for (Map.Entry<String, String> e : extraEnv.entrySet()) env.put(e.getKey(), e.getValue());
        // ffmpeg (anak proses yt-dlp) butuh libc++ pendamping di foldernya;
        // native lib dir paling depan (jalur resmi, lolos SELinux ketat)
        try {
            String nl = nlDir(ctx);
            String old = env.get("LD_LIBRARY_PATH");
            String v = (nl != null ? nl + ":" : "")
                    + (old == null || old.length() == 0 ? "" : old + ":");
            if (ffmpeg != null) {
                v = v + ffmpeg.getParentFile().getAbsolutePath();
            } else if (v.endsWith(":")) {
                v = v.substring(0, v.length() - 1);
            }
            if (v.length() > 0) env.put("LD_LIBRARY_PATH", v);
        } catch (Exception ignored) {}
        String path = System.getenv("PATH");
        if (path == null) path = "/system/bin:/vendor/bin";
        env.put("PATH", path);
        env.put("PYTHONDONTWRITEBYTECODE", "1");

        Process p;
        try {
            p = Root.start(cmd, env);
        } catch (Exception e) {
            throw new Exception("gagal start python (" + e.getMessage() + ")");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        String lastErr = "";
        while ((line = br.readLine()) != null) {
            final String l = line;
            int pct = parsePct(l);
            if (pct >= 0) L.onProgress(pct, trim(l, 90));
            if (l.contains("[download] Destination:")) L.onLog(trim(l, 140));
            else if (l.contains("ERROR") || l.contains("error")) {
                L.onLog(trim(l, 200));
                lastErr = trim(l, 200);
            }
            else if (l.length() < 220
                    && (l.contains("%") || l.contains("Extract") || l.contains("Download"))) {
                L.onLog(trim(l, 160));
            }
        }
        int rc = p.waitFor();
        L.onLog("yt-dlp exit: " + rc);
        Log.d("FaaDL", "ytdlp rc=" + rc + (lastErr.length() > 0 ? " err=" + lastErr : ""));
        return rc;
    }
}
