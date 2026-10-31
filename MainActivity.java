package ir.turk7filimo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String FIND_VIDEOS_JS =
            "(function(){var r=[];function add(u){if(u&&r.indexOf(u)<0)r.push(u);}"
                    + "document.querySelectorAll('video,source').forEach(function(e){add(e.currentSrc||e.src);});"
                    + "document.querySelectorAll('a[href]').forEach(function(a){"
                    + "if(/\\.(mkv|mp4|webm|avi|mov|m4v|3gp)(\\?|#|$)/i.test(a.href))add(a.href);});"
                    + "return JSON.stringify(r);})()";

    private WebView web;
    private ProgressBar bar;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout top = new FrameLayout(this);
        web = new WebView(this);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        top.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        top.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Store.dp(this, 3), Gravity.TOP));
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xFFEEEEEE);
        nav.addView(btn("خانه", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.loadUrl(Store.HOME);
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(btn("ویدیوهای صفحه", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickVideos();
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        nav.addView(btn("دانلودها", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(btn("رفرش", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.reload();
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        setupWeb();
        web.loadUrl(Store.HOME);
    }

    private Button btn(String text, View.OnClickListener l) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (Store.isVideoUrl(u)) {
                    askVideo(u, Store.nameFromUrl(u));
                    return true;
                }
                String sch = req.getUrl().getScheme();
                if (!"http".equals(sch) && !"https".equals(sch)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "اتصال اینترنت را بررسی کن", Toast.LENGTH_SHORT).show();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                customView = v;
                customCb = cb;
                ((FrameLayout) getWindow().getDecorView()).addView(v, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                fullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                ((FrameLayout) getWindow().getDecorView()).removeView(customView);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
                fullscreen(false);
            }
        });

        web.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                String name = Store.isVideoUrl(u) ? Store.nameFromUrl(u) : URLUtil.guessFileName(u, cd, mime);
                askVideo(u, name);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void fullscreen(boolean on) {
        if (on) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void askVideo(final String url, final String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"⬇ دانلود", "▶ پخش آنلاین"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            Store.startDownload(MainActivity.this, url, name);
                        } else {
                            startActivity(new Intent(MainActivity.this, PlayerActivity.class).putExtra("src", url));
                        }
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void noVideos() {
        Toast.makeText(this, "لینک مستقیم ویدیو در این صفحه پیدا نشد", Toast.LENGTH_LONG).show();
    }

    private void pickVideos() {
        web.evaluateJavascript(FIND_VIDEOS_JS, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                try {
                    if (value == null || value.equals("null")) {
                        noVideos();
                        return;
                    }
                    Object o = new JSONTokener(value).nextValue();
                    JSONArray arr = new JSONArray((String) o);
                    final List<String> urls = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String u = arr.getString(i);
                        if (u.startsWith("http")) urls.add(u);
                    }
                    if (urls.isEmpty()) {
                        noVideos();
                        return;
                    }
                    final String[] names = new String[urls.size()];
                    for (int i = 0; i < names.length; i++) names[i] = Store.nameFromUrl(urls.get(i));
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("ویدیوهای این صفحه")
                            .setItems(names, new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    askVideo(urls.get(w), names[w]);
                                }
                            })
                            .show();
                } catch (Exception e) {
                    noVideos();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
