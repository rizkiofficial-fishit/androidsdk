package com.ryzfinity.live;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Catatan diagnosis: kenapa server gagal jalan atau app crash. */
final class Diag {
    private Diag() { }

    static File f(Context c, String name) { return new File(c.getFilesDir(), name); }

    static synchronized void log(Context c, String msg) {
        try (FileWriter w = new FileWriter(f(c, "boot.log"), true)) {
            w.write(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + msg + "\n");
        } catch (Exception ignored) { }
    }

    static synchronized void crash(Context c, Thread t, Throwable e) {
        try (PrintWriter w = new PrintWriter(new FileWriter(f(c, "crash.txt"), true))) {
            w.println("Thread: " + (t == null ? "?" : t.getName()));
            e.printStackTrace(w);
        } catch (Exception ignored) { }
    }

    static String tail(File file, int max) {
        try {
            if (!file.exists()) return "";
            try (RandomAccessFile r = new RandomAccessFile(file, "r")) {
                long len = r.length(), from = Math.max(0, len - max);
                byte[] buf = new byte[(int) (len - from)];
                r.seek(from);
                r.readFully(buf);
                return new String(buf, "UTF-8");
            }
        } catch (Exception e) {
            return "(gagal baca " + file.getName() + ": " + e.getMessage() + ")";
        }
    }

    static String report(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append("), ")
                .append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                .append(", ABI ").append(android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : "?").append("\n\n");
        String crash = tail(f(c, "crash.txt"), 2500);
        if (!crash.isEmpty()) sb.append("== CRASH ==\n").append(crash).append("\n");
        String boot = tail(f(c, "boot.log"), 1500);
        if (!boot.isEmpty()) sb.append("== LANGKAH ==\n").append(boot).append("\n");
        String node = tail(f(c, "node.log"), 1800);
        if (!node.isEmpty()) sb.append("== LOG NODE ==\n").append(node).append("\n");
        return sb.toString();
    }

    static boolean hasFail(Context c) { return tail(f(c, "boot.log"), 6000).contains("FAIL"); }

    /** Ada masalah dari jalan sebelumnya: ada crash, atau server tidak pernah sampai siap. */
    static boolean needsAttention(Context c) {
        File crash = f(c, "crash.txt");
        if (crash.exists() && crash.length() > 0) return true;
        File boot = f(c, "boot.log");
        return boot.exists() && !tail(boot, 20000).contains("READY");
    }

    static void reset(Context c) {
        f(c, "crash.txt").delete();
        f(c, "boot.log").delete();
    }
}
