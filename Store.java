package ir.turk7filimo.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.net.URLDecoder;
import java.util.Locale;

final class Store {
    static final String HOME = "https://www.turk7filimo.ir/";
    static final String UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private Store() {}

    /** پوشه خصوصی اپ: در گالری و فایل‌منیجر عمومی دیده نمی‌شود و با حذف اپ پاک می‌شود */
    static File dir(Context c) {
        File d = c.getExternalFilesDir("movies");
        if (d == null) d = new File(c.getFilesDir(), "movies");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dl", Context.MODE_PRIVATE);
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    private static String stripQuery(String url) {
        String p = url;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#');
        if (h >= 0) p = p.substring(0, h);
        return p;
    }

    static boolean isVideoUrl(String url) {
        if (url == null) return false;
        String p = stripQuery(url).toLowerCase(Locale.ROOT);
        return p.endsWith(".mkv") || p.endsWith(".mp4") || p.endsWith(".webm")
                || p.endsWith(".avi") || p.endsWith(".mov") || p.endsWith(".m4v")
                || p.endsWith(".3gp");
    }

    static String nameFromUrl(String url) {
        String p = stripQuery(url);
        int s = p.lastIndexOf('/');
        String n = s >= 0 ? p.substring(s + 1) : p;
        try {
            n = URLDecoder.decode(n, "UTF-8");
        } catch (Exception ignored) {
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (n.isEmpty()) {
            n = "video_" + System.currentTimeMillis() + ".mp4";
        } else if (!n.matches(".*\\.[A-Za-z0-9]{2,4}$")) {
            n = n + ".mp4";
        }
        return n;
    }

    static void startDownload(Activity a, String url, String name) {
        if (new File(dir(a), name).exists()) {
            Toast.makeText(a, "این فایل قبلاً دانلود شده", Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadService.cancelled.remove(name);
        DownloadService.toDelete.remove(name);
        if (!DownloadService.active.add(name)) {
            Toast.makeText(a, "این فایل در حال دانلود است", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        prefs(a).edit().putString("url_" + name, url).apply();
        Intent i = new Intent(a, DownloadService.class)
                .putExtra(DownloadService.EXTRA_URL, url)
                .putExtra(DownloadService.EXTRA_NAME, name);
        if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(i);
        else a.startService(i);
        Toast.makeText(a, "دانلود شروع شد", Toast.LENGTH_SHORT).show();
    }
}
