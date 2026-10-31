package ir.turk7filimo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DownloadsActivity extends Activity {
    private static class Item {
        File f;
        String name;
        boolean part;
        boolean active;
        long size;
        long total;
        String url;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Item> items = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView empty;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xFFEEEEEE);
        top.setPadding(Store.dp(this, 14), Store.dp(this, 6), Store.dp(this, 6), Store.dp(this, 6));

        TextView title = new TextView(this);
        title.setText("دانلودهای من");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this, null, android.R.attr.borderlessButtonStyle);
        add.setText("+ لینک");
        add.setAllCaps(false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askLink();
            }
        });
        top.addView(add);

        Button close = new Button(this, null, android.R.attr.borderlessButtonStyle);
        close.setText("بستن");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(close);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        empty = new TextView(this);
        empty.setText("هنوز چیزی دانلود نشده.\nروی لینک فیلم در سایت بزن و «دانلود» را انتخاب کن.");
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Store.dp(this, 24), Store.dp(this, 48), Store.dp(this, 24), 0);
        root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < items.size()) options(items.get(pos));
            }
        });
        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void refresh() {
        items.clear();
        File[] fs = Store.dir(this).listFiles();
        if (fs != null) {
            Arrays.sort(fs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            SharedPreferences p = Store.prefs(this);
            for (File f : fs) {
                if (!f.isFile()) continue;
                Item it = new Item();
                it.f = f;
                String n = f.getName();
                if (n.endsWith(".part")) {
                    it.part = true;
                    it.name = n.substring(0, n.length() - 5);
                    it.total = p.getLong("total_" + it.name, -1);
                    it.url = p.getString("url_" + it.name, null);
                    it.active = DownloadService.active.contains(it.name);
                } else {
                    it.name = n;
                }
                it.size = f.length();
                items.add(it);
            }
        }
        adapter.setNotifyOnChange(false);
        adapter.clear();
        for (Item it : items) adapter.add(label(it));
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String label(Item it) {
        String s = it.name + "\n";
        String sz = Formatter.formatFileSize(this, it.size);
        if (!it.part) return s + sz;
        String pct = it.total > 0 ? (" " + (it.size * 100 / it.total) + "%") : "";
        if (it.total > 0) sz = sz + " / " + Formatter.formatFileSize(this, it.total);
        return s + (it.active ? "⬇ در حال دانلود" : "⏸ متوقف") + pct + " — " + sz;
    }

    private void options(final Item it) {
        String[] opts = it.part
                ? new String[]{it.active ? "توقف" : "ادامه دانلود", "حذف"}
                : new String[]{"▶ پخش", "حذف"};
        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 1) {
                            confirmDelete(it);
                        } else if (!it.part) {
                            startActivity(new Intent(DownloadsActivity.this, PlayerActivity.class)
                                    .putExtra("src", Uri.fromFile(it.f).toString()));
                        } else if (it.active) {
                            DownloadService.cancelled.add(it.name);
                        } else if (it.url != null) {
                            Store.startDownload(DownloadsActivity.this, it.url, it.name);
                        } else {
                            Toast.makeText(DownloadsActivity.this, "لینک دانلود ذخیره نشده؛ دوباره از سایت بزن", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void confirmDelete(final Item it) {
        new AlertDialog.Builder(this)
                .setTitle("حذف شود؟")
                .setMessage(it.name)
                .setPositiveButton("حذف", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (it.active) {
                            DownloadService.toDelete.add(it.name);
                            DownloadService.cancelled.add(it.name);
                        } else {
                            //noinspection ResultOfMethodCallIgnored
                            it.f.delete();
                            Store.prefs(DownloadsActivity.this).edit()
                                    .remove("total_" + it.name).remove("url_" + it.name).apply();
                        }
                        refresh();
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void askLink() {
        final EditText et = new EditText(this);
        et.setHint("https://...");
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("لینک مستقیم فیلم")
                .setView(et)
                .setPositiveButton("دانلود", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String u = et.getText().toString().trim();
                        if (!u.startsWith("http")) {
                            Toast.makeText(DownloadsActivity.this, "لینک معتبر نیست", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Store.startDownload(DownloadsActivity.this, u, Store.nameFromUrl(u));
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }
}
