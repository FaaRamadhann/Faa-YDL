package com.faa.faadl;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Helper root: deteksi superuser sekali, dan ulangi exec yang ditolak
 * (error=13 SELinux) via su -c dengan env ikut terbawa.
 */
public class Root {

    private static Boolean cached = null;

    public static void recheck() {
        cached = null;
    }

    public static boolean haveRoot() {
        if (cached != null) return cached.booleanValue();
        boolean ok = false;
        try {
            Process p = new ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            int rc = p.waitFor();
            ok = (rc == 0 && sb.toString().contains("uid=0"));
        } catch (Exception e) {
            Log.d("FaaDL", "root check " + e.getMessage());
        }
        cached = Boolean.valueOf(ok);
        Log.d("FaaDL", "root=" + ok);
        return ok;
    }

    private static String shq(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Start proses: coba langsung dulu; bila ditolak (error=13) dan ada
     * root, ulangi via su -c dengan env yang sama.
     */
    public static Process start(List<String> cmd, Map<String, String> env) throws Exception {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (env != null) {
                for (Map.Entry<String, String> e : env.entrySet()) {
                    pb.environment().put(e.getKey(), e.getValue());
                }
            }
            return pb.start();
        } catch (java.io.IOException e) {
            String m = e.getMessage();
            boolean denied = m != null
                    && (m.contains("error=13") || m.contains("Permission denied"));
            if (!denied || !haveRoot()) throw e;
            StringBuilder sb = new StringBuilder();
            if (env != null) {
                for (Map.Entry<String, String> e2 : env.entrySet()) {
                    sb.append("export ").append(e2.getKey()).append("=")
                      .append(shq(e2.getValue())).append("; ");
                }
            }
            sb.append("exec");
            for (int i = 0; i < cmd.size(); i++) sb.append(" ").append(shq(cmd.get(i)));
            Log.d("FaaDL", "retry via su");
            return new ProcessBuilder("su", "-c", sb.toString())
                    .redirectErrorStream(true).start();
        }
    }
}
