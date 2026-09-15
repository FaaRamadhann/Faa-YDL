package com.faa.faadlmod;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesin download murni Java (tanpa Python di HP).
 * Alur: innertube player (client ANDROID, URL langsung tanpa decipher)
 *  -> download stream -> (mp3: ffmpeg convert / mp4: langsung atau merge).
 * yt-dlp built-in tetap dicoba sebagai fallback bila mesin utama gagal.
 */
public class DlEngine {

    public interface Listener {
        void onLog(String s);
        void onProgress(int pct, String msg);
    }

    private static final String API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String CLIENT_VER = "21.26.364";
    private static final String UA = "com.google.android.youtube/" + CLIENT_VER
            + " (Linux; U; Android 11) gzip";

    private static final Pattern[] ID_PATTERNS = new Pattern[]{
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/shorts/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/embed/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/v/([A-Za-z0-9_-]{11})"),
    };

    public static String extractVideoId(String url) {
        if (url == null) return null;
        for (int i = 0; i < ID_PATTERNS.length; i++) {
            Matcher m = ID_PATTERNS[i].matcher(url);
            if (m.find()) return m.group(1);
        }
        String t = url.trim();
        if (t.matches("[A-Za-z0-9_-]{11}")) return t;
        return null;
    }

    public static String sanitize(String s) {
        if (s == null || s.length() == 0) return "video";
        String r = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        // huruf/angka unicode dipertahankan (CJK, Arab, dsb); emoji/simbol jadi _
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c >= 32 && c < 127) {
                b.append(c);
                continue;
            }
            int t = Character.getType(c);
            boolean keep = t == Character.UPPERCASE_LETTER
                    || t == Character.LOWERCASE_LETTER
                    || t == Character.TITLECASE_LETTER
                    || t == Character.MODIFIER_LETTER
                    || t == Character.OTHER_LETTER
                    || t == Character.NON_SPACING_MARK
                    || t == Character.COMBINING_SPACING_MARK
                    || t == Character.ENCLOSING_MARK
                    || t == Character.DECIMAL_DIGIT_NUMBER
                    || t == Character.LETTER_NUMBER
                    || t == Character.OTHER_NUMBER
                    || t == Character.DASH_PUNCTUATION
                    || t == Character.SPACE_SEPARATOR;
            b.append(keep ? c : '_');
        }
        r = b.toString().replaceAll(" +", " ").trim();
        if (r.length() > 80) r = r.substring(0, 80).trim();
        if (r.length() == 0) r = "video";
        return r;
    }

    /** Pastikan binary ada + bisa dieksekusi, kalau tidak lempar pesan jelas. */
    public static void needExec(File f, String name) throws Exception {
        if (f == null || !f.exists() || f.length() < 100000) {
            throw new Exception(name + " built-in tidak ada di "
                    + (f == null ? "?" : f.getAbsolutePath()));
        }
        if (!f.canExecute()) {
            throw new Exception(name + " tidak bisa dieksekusi (cannot run program, "
                    + f.getAbsolutePath() + "). Coba install ulang APK / varian lain.");
        }
    }

    /** ffmpeg valid? Wrapper modul (.bin di sebelahnya) juga dihitung valid. */
    private static File resolveFfmpeg(File f) {
        if (f == null || !f.exists()) return null;
        if (f.length() > 1000000) return f;
        File bin = new File(f.getParentFile(), f.getName() + ".bin");
        if (bin.exists() && bin.length() > 1000000) return f;
        return null;
    }

    /** Download utama. Return file hasil akhir. */
    public static File download(File ffmpeg, File outDir, String pageUrl,
                                boolean mp3, Listener L) throws Exception {
        String vid = extractVideoId(pageUrl);
        if (vid == null) throw new Exception("ID video tidak ketemu di URL");
        L.onLog("Video ID: " + vid);
        Log.d("FaaDL", "download start vid=" + vid + " mp3=" + mp3);
        try { outDir.mkdirs(); } catch (Exception ignored) {}

        JSONObject player = innertubePlayer(vid, L);
        String status = player.optJSONObject("playabilityStatus") != null
                ? player.optJSONObject("playabilityStatus").optString("status", "?") : "?";
        if (!"OK".equals(status)) {
            String reason = "?";
            try { reason = player.getJSONObject("playabilityStatus").optString("reason", status); }
            catch (Exception ignored) {}
            throw new Exception("Video tidak bisa diunduh: " + reason);
        }
        String title = vid;
        try { title = player.getJSONObject("videoDetails").optString("title", vid); }
        catch (Exception ignored) {}
        title = sanitize(title);
        L.onLog("Judul: " + title);
        Log.d("FaaDL", "tube status=" + status + " title=" + title);

        JSONObject streaming;
        try { streaming = player.getJSONObject("streamingData"); }
        catch (Exception e) { throw new Exception("streamingData kosong (privat/umur/batasan?)"); }

        if (mp3) {
            return downloadAudio(ffmpeg, outDir, streaming, title, L);
        } else {
            return downloadVideo(ffmpeg, outDir, streaming, title, L);
        }
    }

    // ---------- audio -> mp3 ----------
    private static File downloadAudio(File ffmpeg, File outDir, JSONObject streaming,
                                      String title, Listener L) throws Exception {
        ArrayList<Stream> audios = collect(streaming, true);
        String dlExt = "m4a";
        File tmp;
        if (!audios.isEmpty()) {
            final Stream best = audios.get(0);
            L.onLog("Audio: " + best.desc());
            dlExt = mimeExt(best.mime, "m4a");
            tmp = new File(outDir, title + ".tmp-audio." + dlExt);
            downloadFile(best.url, tmp, L);
        } else {
            // adaptif di-cipher semua -> pakai audio dari mp4 progresif
            Stream prog = bestProgressive(streaming);
            if (prog == null) throw new Exception("Tidak ada stream audio langsung");
            L.onLog("Audio via progresif: " + prog.desc());
            tmp = new File(outDir, title + ".tmp-prog.mp4");
            downloadFile(prog.url, tmp, L);
        }
        File mp3 = unique(new File(outDir, title + ".mp3"));
        File ff = resolveFfmpeg(ffmpeg);
        if (ff != null) {
            needExec(ff, "ffmpeg");
            L.onLog("Convert ke mp3 via ffmpeg...");
            ArrayList<String> cmd = new ArrayList<String>();
            cmd.add(ff.getAbsolutePath());
            cmd.add("-y");
            cmd.add("-i"); cmd.add(tmp.getAbsolutePath());
            cmd.add("-vn");
            cmd.add("-ar"); cmd.add("44100");
            cmd.add("-b:a"); cmd.add("192k");
            cmd.add(mp3.getAbsolutePath());
            int rc;
            try {
                rc = runProc(cmd, L);
            } finally {
                try { tmp.delete(); } catch (Exception ignored) {}
            }
            if (rc != 0 || !mp3.exists() || mp3.length() == 0) {
                if (mp3.exists()) mp3.delete();
                throw new Exception("ffmpeg convert gagal (rc=" + rc + ")");
            }
            return mp3;
        } else {
            L.onLog("ffmpeg tidak ada, simpan audio apa adanya");
            File keep = unique(new File(outDir, title + "." + dlExt));
            if (!tmp.renameTo(keep)) throw new Exception("Gagal simpan file audio");
            return keep;
        }
    }

    // ---------- video -> mp4 ----------
    private static File downloadVideo(File ffmpeg, File outDir, JSONObject streaming,
                                      String title, Listener L) throws Exception {
        // 1) progresif (muxed) mp4 langsung
        Stream b = bestProgressive(streaming);
        if (b != null) {
            L.onLog("Video progresif: " + b.desc());
            File out = unique(new File(outDir, title + ".mp4"));
            downloadFile(b.url, out, L);
            return out;
        }
        // 2) merge video-only + audio via ffmpeg
        ArrayList<Stream> videos = collectVideoOnly(streaming);
        ArrayList<Stream> audios = collect(streaming, true);
        if (videos.isEmpty() || audios.isEmpty())
            throw new Exception("Tidak ada stream video langsung");
        File ffm = resolveFfmpeg(ffmpeg);
        if (ffm == null)
            throw new Exception("Butuh merge tapi ffmpeg built-in tidak ada");
        needExec(ffm, "ffmpeg");
        Stream v = videos.get(0);
        Stream a = audios.get(0);
        L.onLog("Video: " + v.desc());
        L.onLog("Audio: " + a.desc());
        File vf = new File(outDir, title + ".tmp-v.mp4");
        File af = new File(outDir, title + ".tmp-a.m4a");
        downloadFile(v.url, vf, L);
        downloadFile(a.url, af, L);
        File out = unique(new File(outDir, title + ".mp4"));
        L.onLog("Gabung video+audio via ffmpeg...");
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add(ffm.getAbsolutePath());
        cmd.add("-y");
        cmd.add("-i"); cmd.add(vf.getAbsolutePath());
        cmd.add("-i"); cmd.add(af.getAbsolutePath());
        cmd.add("-c"); cmd.add("copy");
        cmd.add("-movflags"); cmd.add("+faststart");
        cmd.add(out.getAbsolutePath());
        int rc;
        try {
            rc = runProc(cmd, L);
        } finally {
            try { vf.delete(); } catch (Exception ignored) {}
            try { af.delete(); } catch (Exception ignored) {}
        }
        if (rc != 0 || !out.exists() || out.length() == 0) {
            if (out.exists()) out.delete();
            throw new Exception("ffmpeg merge gagal (rc=" + rc + ")");
        }
        return out;
    }

    // ---------- innertube ----------
    private static JSONObject innertubePlayer(String vid, Listener L) throws Exception {
        String endpoint = "https://www.youtube.com/youtubei/v1/player?key=" + API_KEY
                + "&prettyPrint=false";
        JSONObject ctx = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("clientName", "ANDROID");
        client.put("clientVersion", CLIENT_VER);
        client.put("androidSdkVersion", 30);
        client.put("hl", "en");
        client.put("gl", "US");
        client.put("osName", "Android");
        client.put("osVersion", "11");
        ctx.put("client", client);
        JSONObject body = new JSONObject();
        body.put("context", new JSONObject().put("client", client));
        body.put("videoId", vid);
        body.put("racyCheckOk", true);
        body.put("contentCheckOk", true);
        L.onLog("Minta stream (innertube android-client)...");
        String resp = httpPostJson(endpoint, body.toString());
        return new JSONObject(resp);
    }

    private static String httpPostJson(String urlStr, String json) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", UA);
            byte[] payload = json.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(payload.length);
            OutputStream os = c.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            int rc = c.getResponseCode();
            InputStream in = rc >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new Exception("HTTP " + rc + " tanpa body");
            String s = readAll(in, 8 * 1024 * 1024);
            if (rc >= 400) throw new Exception("HTTP " + rc + ": " + s.substring(0, Math.min(200, s.length())));
            return s;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---------- structs ----------
    private static class Stream {
        String url; String mime; long bitrate; int w; int h; String q;
        Stream(String u, String m, long b, int w, int h, String q) {
            url = u; mime = m; bitrate = b; this.w = w; this.h = h; this.q = q;
        }
        long pixels() { return (long) w * h; }
        String desc() {
            String r = q != null && q.length() > 0 ? q : (h > 0 ? h + "p" : "?");
            return mime.split(";")[0] + " " + r + " " + (bitrate / 1000) + "kbps";
        }
    }

    /** Progressive mp4 terbaik (ada audio+video, URL langsung). */
    private static Stream bestProgressive(JSONObject streaming) {
        ArrayList<Stream> prog = new ArrayList<Stream>();
        JSONArray formats = streaming.optJSONArray("formats");
        if (formats != null) {
            for (int i = 0; i < formats.length(); i++) {
                JSONObject o = formats.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", null);
                String mime = o.optString("mimeType", "");
                if (url == null || url.length() == 0) continue;
                if (!mime.contains("mp4")) continue;
                prog.add(new Stream(url, mime, o.optLong("bitrate", 0),
                        o.optInt("width", 0), o.optInt("height", 0),
                        o.optString("qualityLabel", "?")));
            }
        }
        if (prog.isEmpty()) return null;
        Stream best = prog.get(0);
        for (int i = 1; i < prog.size(); i++) {
            if (prog.get(i).pixels() > best.pixels()) best = prog.get(i);
        }
        return best;
    }

    private static ArrayList<Stream> collect(JSONObject streaming, boolean audio) {
        ArrayList<Stream> out = new ArrayList<Stream>();
        JSONArray arr = streaming.optJSONArray("adaptiveFormats");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", null);
            String mime = o.optString("mimeType", "");
            if (url == null || url.length() == 0) continue;
            boolean isA = mime.startsWith("audio/");
            if (isA != audio) continue;
            out.add(new Stream(url, mime, o.optLong("bitrate", 0),
                    o.optInt("width", 0), o.optInt("height", 0),
                    o.optString(audio ? "audioQuality" : "qualityLabel", "?")));
        }
        final boolean a = audio;
        Collections.sort(out, new Comparator<Stream>() {
            @Override public int compare(Stream x, Stream y) {
                if (a) return Long.compare(y.bitrate, x.bitrate);
                long px = x.pixels() > 0 ? x.pixels() : x.bitrate;
                long py = y.pixels() > 0 ? y.pixels() : y.bitrate;
                return Long.compare(py, px);
            }
        });
        return out;
    }

    private static ArrayList<Stream> collectVideoOnly(JSONObject streaming) {
        ArrayList<Stream> out = new ArrayList<Stream>();
        JSONArray arr = streaming.optJSONArray("adaptiveFormats");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", null);
            String mime = o.optString("mimeType", "");
            if (url == null || url.length() == 0) continue;
            if (!mime.startsWith("video/") || !mime.contains("mp4")) continue;
            out.add(new Stream(url, mime, o.optLong("bitrate", 0),
                    o.optInt("width", 0), o.optInt("height", 0),
                    o.optString("qualityLabel", "?")));
        }
        Collections.sort(out, new Comparator<Stream>() {
            @Override public int compare(Stream x, Stream y) {
                long px = x.pixels() > 0 ? x.pixels() : x.bitrate;
                long py = y.pixels() > 0 ? y.pixels() : y.bitrate;
                return Long.compare(py, px);
            }
        });
        return out;
    }

    private static String mimeExt(String mime, String def) {
        if (mime == null) return def;
        if (mime.contains("webm")) return "webm";
        if (mime.contains("mp4")) return "m4a";
        return def;
    }

    private static File unique(File f) {
        if (!f.exists()) return f;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File c = new File(f.getParent(), base + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return f;
    }

    // ---------- download file (GET, progress) ----------
    private static void downloadFile(String urlStr, File dst, Listener L) throws Exception {
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            int rc = c.getResponseCode();
            if (rc / 100 != 2) throw new Exception("HTTP " + rc + " saat download");
            long total = c.getContentLengthLong();
            in = c.getInputStream();
            out = new FileOutputStream(dst);
            byte[] buf = new byte[32768];
            long got = 0;
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                if (total > 0) {
                    int pct = (int) (got * 100 / total);
                    if (pct != lastPct && (pct - lastPct >= 1 || pct == 100)) {
                        lastPct = pct;
                        L.onProgress(pct, human(got) + " / " + human(total));
                    }
                } else if (got % (512 * 1024) < 32768) {
                    L.onProgress(-1, human(got) + " terunduh...");
                }
            }
            out.flush();
            L.onLog("Tersimpan sementara: " + human(got));
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    private static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        if (b < 1024 * 1024 * 1024) return String.format("%.1f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    // ---------- jalankan proses, tangkap output (untuk tes debug) ----------
    /** Return {rc, output}. Tidak throw kecuali gagal start. */
    public static String[] runCapture(ArrayList<String> cmd,
                                      Map<String, String> extraEnv, int cap) throws Exception {
        Process p;
        try {
            p = Root.start(cmd, extraEnv);
        } catch (Exception e) {
            throw new Exception("cannot run " + cmd.get(0) + ": " + e.getMessage());
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            if (sb.length() < cap) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        int rc = p.waitFor();
        return new String[]{String.valueOf(rc), sb.toString()};
    }

    /** Laporan diagnostik innertube tanpa download. Untuk tab Debug. */
    public static String debugReport(String pageUrl, Listener L) {
        StringBuilder r = new StringBuilder();
        try {
            String vid = extractVideoId(pageUrl);
            r.append("videoId=" + vid + "\n");
            if (vid == null) return r.append("ID video tidak ketemu di URL").toString();
            JSONObject player = innertubePlayer(vid, L);
            JSONObject ps = player.optJSONObject("playabilityStatus");
            String status = ps != null ? ps.optString("status", "?") : "?";
            String reason = ps != null ? ps.optString("reason", "-") : "-";
            r.append("status=" + status + " reason=" + reason + "\n");
            String title = "?";
            try { title = player.getJSONObject("videoDetails").optString("title", "?"); }
            catch (Exception ignored) {}
            r.append("title=" + title + "\n");
            JSONObject sd = player.optJSONObject("streamingData");
            if (sd == null) return r.append("streamingData KOSONG").toString();
            JSONArray fmts = sd.optJSONArray("formats");
            JSONArray adap = sd.optJSONArray("adaptiveFormats");
            int nf = fmts != null ? fmts.length() : 0;
            int na = adap != null ? adap.length() : 0;
            int direct = 0;
            if (fmts != null) {
                for (int i = 0; i < fmts.length(); i++) {
                    JSONObject o = fmts.optJSONObject(i);
                    if (o != null && o.optString("url", "").length() > 0) direct++;
                }
            }
            if (adap != null) {
                for (int i = 0; i < adap.length(); i++) {
                    JSONObject o = adap.optJSONObject(i);
                    if (o != null && o.optString("url", "").length() > 0) direct++;
                }
            }
            r.append("formats=" + nf + " adaptive=" + na + " direct-url=" + direct + "\n");
            Stream bp = bestProgressive(sd);
            r.append("progresif=" + (bp == null ? "-" : bp.desc()) + "\n");
            ArrayList<Stream> aud = collect(sd, true);
            r.append("audio-langsung=" + aud.size()
                    + (aud.isEmpty() ? "" : " (" + aud.get(0).desc() + ")") + "\n");
        } catch (Exception e) {
            r.append("ERROR: " + e.getMessage());
        }
        return r.toString();
    }

    // ---------- jalankan proses (ffmpeg), buang output biar tidak macet ----------
    public static int runProc(ArrayList<String> cmd, final Listener L) throws Exception {
        java.util.HashMap<String, String> env = new java.util.HashMap<String, String>();
        // library pendamping: modul Magisk (/system/lib64/faadl) + folder binary
        try {
            File dir = new File(cmd.get(0)).getParentFile();
            String old = System.getenv("LD_LIBRARY_PATH");
            String v = "/system/lib64/faadl:/system/lib/faadl:";
            if (dir != null) v = v + dir.getAbsolutePath() + ":";
            v = v + (old == null || old.length() == 0 ? "" : old);
            if (v.endsWith(":")) v = v.substring(0, v.length() - 1);
            env.put("LD_LIBRARY_PATH", v);
        } catch (Exception ignored) {}
        Process p;
        try {
            p = Root.start(cmd, env);
        } catch (Exception e) {
            throw new Exception("Tidak bisa menjalankan " + cmd.get(0)
                    + " (" + e.getMessage() + "). Kemungkinan binary salah ABI "
                    + "untuk HP ini - coba varian APK lain (arm64/arm32/universal).");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        String last = "";
        while ((line = br.readLine()) != null) {
            last = line;
        }
        int rc = p.waitFor();
        if (last.length() > 0 && rc != 0) L.onLog(trim(last, 200));
        if (rc != 0) Log.d("FaaDL", "proc rc=" + rc + " cmd=" + cmd.get(0));
        return rc;
    }

    private static String readAll(InputStream in, int cap) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        char[] buf = new char[8192];
        int n;
        while ((n = br.read(buf)) > 0) {
            sb.append(buf, 0, n);
            if (sb.length() > cap) break;
        }
        return sb.toString();
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n);
    }
}
