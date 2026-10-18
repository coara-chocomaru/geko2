package com.coara.browser2.fx;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FxExtensionsActivity extends AppCompatActivity implements FxExtensionManager.Listener {

    private static final int REQUEST_PICK = 4101;
    private static final int TAB_RECOMMENDED = 0;
    private static final int TAB_INSTALLED = 1;

    private final FxExtensionManager manager = FxExtensionManager.get();
    private final List<AmoAddon> remote = new ArrayList<>();
    private final Set<String> busy = new HashSet<>();

    private ListView listView;
    private TextView statusView;
    private ProgressBar progress;
    private EditText searchBox;
    private TextView tabRecommended;
    private TextView tabInstalled;
    private ListAdapter adapter;
    private int tab = TAB_RECOMMENDED;
    private int remotePage = 1;
    private boolean remoteLoading;
    private boolean remoteEnd;
    private String remoteQuery = "";
    private String remoteError;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private int background;
    private int surface;
    private int divider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolveColors();
        buildUi();
        manager.start();
        manager.refresh(null);
        loadRemote(true);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        manager.setForeground(this);
        manager.addListener(this);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    @Override
    protected void onPause() {
        manager.removeListener(this);
        manager.clearForeground(this);
        super.onPause();
    }

    private void resolveColors() {
        textPrimary = attr(android.R.attr.textColorPrimary, Color.BLACK);
        textSecondary = attr(android.R.attr.textColorSecondary, Color.GRAY);
        accent = attr(android.R.attr.colorAccent, 0xFF0A84FF);
        background = attr(android.R.attr.colorBackground, Color.WHITE);
        boolean dark = (textPrimary >> 16 & 0xFF) > 0x80;
        surface = dark ? 0xFF2B2B30 : 0xFFF2F3F5;
        divider = dark ? 0x33FFFFFF : 0x22000000;
    }

    private int attr(int attr, int fallback) {
        TypedArray a = obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, fallback);
        } finally {
            a.recycle();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(12), dp(4));
        Button back = new Button(this);
        back.setText("←");
        back.setAllCaps(false);
        back.setBackground(null);
        back.setTextColor(textPrimary);
        back.setTextSize(20f);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("Firefox拡張機能");
        title.setTextColor(textPrimary);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(4), dp(12), dp(4));
        tabRecommended = tabView("おすすめ", TAB_RECOMMENDED);
        tabInstalled = tabView("インストール済み", TAB_INSTALLED);
        tabs.addView(tabRecommended, new LinearLayout.LayoutParams(0, dp(40), 1f));
        tabs.addView(tabInstalled, new LinearLayout.LayoutParams(0, dp(40), 1f));
        root.addView(tabs);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(12), dp(4), dp(12), dp(4));
        Button file = toolButton("ファイルから追加");
        file.setOnClickListener(v -> pickFile());
        Button url = toolButton("URLから追加");
        url.setOnClickListener(v -> askUrl());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp1.rightMargin = dp(6);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp2.leftMargin = dp(6);
        tools.addView(file, lp1);
        tools.addView(url, lp2);
        root.addView(tools);

        searchBox = new EditText(this);
        searchBox.setHint("Firefoxアドオンを検索");
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setTextColor(textPrimary);
        searchBox.setHintTextColor(textSecondary);
        searchBox.setBackground(round(surface, 12));
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                remoteQuery = searchBox.getText().toString().trim();
                loadRemote(true);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        sp.setMargins(dp(12), dp(4), dp(12), dp(4));
        root.addView(searchBox, sp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        statusView = new TextView(this);
        statusView.setTextColor(textSecondary);
        statusView.setTextSize(14f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(24), dp(24), dp(24), dp(8));
        statusView.setVisibility(View.GONE);
        root.addView(statusView);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(24));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        listView.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
                if (tab == TAB_RECOMMENDED && total > 0 && first + visible >= total - 3) {
                    loadRemote(false);
                }
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        selectTab(TAB_RECOMMENDED);
    }

    private TextView tabView(String text, final int index) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14f);
        t.setOnClickListener(v -> selectTab(index));
        return t;
    }

    private Button toolButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13f);
        b.setTextColor(accent);
        b.setBackground(round(surface, 12));
        return b;
    }

    private void selectTab(int index) {
        tab = index;
        styleTab(tabRecommended, index == TAB_RECOMMENDED);
        styleTab(tabInstalled, index == TAB_INSTALLED);
        searchBox.setVisibility(index == TAB_RECOMMENDED ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
        updateStatus();
    }

    private void styleTab(TextView t, boolean selected) {
        t.setTextColor(selected ? Color.WHITE : textPrimary);
        t.setBackground(selected ? round(accent, 20) : null);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void updateStatus() {
        String message = null;
        if (tab == TAB_RECOMMENDED) {
            if (remote.isEmpty()) {
                if (remoteLoading) {
                    message = "読み込み中…";
                } else if (remoteError != null) {
                    message = "取得できませんでした: " + remoteError + "\n検索ボックスで再検索すると再試行します";
                } else {
                    message = "該当する拡張機能がありません";
                }
            }
        } else if (!manager.isListed()) {
            message = "読み込み中…";
        } else if (manager.installed().isEmpty()) {
            message = "インストール済みのFirefox拡張機能はありません\n「おすすめ」から追加するか、ファイル/URLから追加できます";
        }
        statusView.setText(message == null ? "" : message);
        statusView.setVisibility(message == null ? View.GONE : View.VISIBLE);
        progress.setVisibility(remoteLoading && tab == TAB_RECOMMENDED ? View.VISIBLE : View.GONE);
    }

    private void loadRemote(boolean reset) {
        if (remoteLoading || (!reset && remoteEnd)) {
            return;
        }
        if (reset) {
            remotePage = 1;
            remoteEnd = false;
            remote.clear();
            remoteError = null;
        }
        remoteLoading = true;
        final int page = remotePage;
        final String query = remoteQuery;
        AmoClient.Callback<List<AmoAddon>> cb = (list, error) -> {
            remoteLoading = false;
            if (!query.equals(remoteQuery)) {
                loadRemote(true);
                return;
            }
            if (error != null) {
                remoteError = error;
                remoteEnd = true;
            } else if (list != null) {
                Set<String> seen = new HashSet<>();
                for (AmoAddon a : remote) {
                    seen.add(a.guid);
                }
                int added = 0;
                for (AmoAddon a : list) {
                    if (seen.add(a.guid)) {
                        remote.add(a);
                        added++;
                    }
                }
                remoteEnd = list.size() < 30 || added == 0;
                remotePage = page + 1;
            }
            adapter.notifyDataSetChanged();
            updateStatus();
        };
        if (query.isEmpty()) {
            AmoClient.recommended(page, cb);
        } else {
            AmoClient.search(query, page, cb);
        }
        updateStatus();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-xpinstall", "application/zip", "application/octet-stream", "*/*"});
        try {
            startActivityForResult(intent, REQUEST_PICK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "ファイル選択を開けませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            installUri(data.getData());
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            intent.setData(null);
            String scheme = data.getScheme() == null ? "" : data.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https")) {
                installUrl(data.toString());
            } else {
                installUri(data);
            }
            selectTab(TAB_INSTALLED);
        }
    }

    private void askUrl() {
        final EditText input = new EditText(this);
        input.setHint("https://…/extension.xpi");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("URLから追加")
                .setMessage(".xpiファイルのURL、またはAddons.mozilla.orgのアドオンページURLを入力")
                .setView(box)
                .setPositiveButton("追加", (d, w) -> installUrl(input.getText().toString().trim()))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void installUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String url = raw;
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            url = "https://" + url;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!FxExtensionManager.isExtensionPackage(url, null) && host.equals("addons.mozilla.org")) {
            resolveAmoPage(uri);
            return;
        }
        runInstall("url:" + url, url, WebExtensionController.INSTALLATION_METHOD_MANAGER);
    }

    private void resolveAmoPage(Uri page) {
        List<String> segs = page.getPathSegments();
        String slug = null;
        for (int i = 0; i < segs.size(); i++) {
            if ("addon".equals(segs.get(i)) && i + 1 < segs.size()) {
                slug = segs.get(i + 1);
                break;
            }
        }
        if (slug == null) {
            Toast.makeText(this, "アドオンのURLを認識できませんでした", Toast.LENGTH_LONG).show();
            return;
        }
        remoteQuery = slug;
        searchBox.setText(slug);
        selectTab(TAB_RECOMMENDED);
        loadRemote(true);
        Toast.makeText(this, "検索結果から追加してください", Toast.LENGTH_SHORT).show();
    }

    private void installUri(Uri uri) {
        busy.add("file");
        setBusy(true);
        manager.installFromContentUri(this, uri, (ext, error) -> {
            busy.remove("file");
            setBusy(false);
            finishInstall(ext, error);
        });
    }

    private void runInstall(final String key, String url, String method) {
        if (!busy.add(key)) {
            return;
        }
        setBusy(true);
        adapter.notifyDataSetChanged();
        manager.installFromUrl(url, method, (ext, error) -> {
            busy.remove(key);
            setBusy(false);
            adapter.notifyDataSetChanged();
            finishInstall(ext, error);
        });
    }

    private void setBusy(boolean value) {
        progress.setVisibility(value || remoteLoading ? View.VISIBLE : View.GONE);
    }

    private void finishInstall(WebExtension ext, String error) {
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        } else if (ext != null) {
            String name = ext.metaData.name == null ? ext.id : ext.metaData.name;
            Toast.makeText(this, "「" + name + "」を追加しました", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    @Override
    public void onExtensionsChanged() {
        runOnUiThread(() -> {
            adapter.notifyDataSetChanged();
            updateStatus();
        });
    }

    @Override
    public void onExtensionError(final String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private String ratingText(AmoAddon a) {
        StringBuilder sb = new StringBuilder();
        if (a.ratingCount > 0) {
            sb.append(String.format(Locale.US, "★%.1f (%d)", a.rating, a.ratingCount));
        }
        if (a.users > 0) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(formatUsers(a.users)).append("ユーザー");
        }
        return sb.toString();
    }

    private static String formatUsers(long n) {
        if (n >= 10000) {
            return String.format(Locale.US, "%.1f万", n / 10000.0);
        }
        return String.valueOf(n);
    }

    private void showDetail(final WebExtension e) {
        StringBuilder sb = new StringBuilder();
        WebExtension.MetaData m = e.metaData;
        sb.append("バージョン: ").append(m.version).append('\n');
        if (m.creatorName != null && !m.creatorName.isEmpty()) {
            sb.append("作者: ").append(m.creatorName).append('\n');
        }
        sb.append("ID: ").append(e.id).append('\n');
        if (m.description != null && !m.description.isEmpty()) {
            sb.append('\n').append(m.description).append('\n');
        }
        List<String> labels = FxPermissions.labels(m.requiredPermissions, m.requiredOrigins);
        sb.append("\n権限:");
        if (labels.isEmpty()) {
            sb.append("\n• なし");
        }
        for (String l : labels) {
            sb.append("\n• ").append(l);
        }
        if (m.signedState == WebExtension.SignedStateFlags.MISSING) {
            sb.append("\n\n※ 署名がありません");
        }
        TextView body = new TextView(this);
        body.setText(sb.toString());
        body.setTextSize(14f);
        body.setPadding(dp(20), dp(8), dp(20), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(m.name == null ? e.id : m.name)
                .setView(sv)
                .setPositiveButton("閉じる", null);
        if (m.homepageUrl != null && !m.homepageUrl.isEmpty()) {
            final String home = m.homepageUrl;
            b.setNeutralButton("ホームページ", (d, w) -> openInBrowser(home));
        }
        b.show();
    }

    private void openInBrowser(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void confirmRemove(final WebExtension e) {
        String name = e.metaData.name == null ? e.id : e.metaData.name;
        new AlertDialog.Builder(this)
                .setTitle("削除")
                .setMessage("「" + name + "」を削除しますか?拡張機能のデータも消去されます。")
                .setPositiveButton("削除", (d, w) -> manager.uninstall(e, (x, err) -> {
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void bindIcon(final ImageView view, final String url, WebExtension e) {
        view.setTag(url != null ? url : e.id);
        view.setImageDrawable(null);
        view.setBackground(round(surface, 10));
        if (e != null) {
            final Object tag = view.getTag();
            e.metaData.icon.getBitmap(dp(48)).then(new GeckoResult.OnValueListener<Bitmap, Void>() {
                @Override
                public GeckoResult<Void> onValue(Bitmap value) {
                    if (value != null && tag.equals(view.getTag())) {
                        view.setImageBitmap(value);
                    }
                    return null;
                }
            }, new GeckoResult.OnExceptionListener<Void>() {
                @Override
                public GeckoResult<Void> onException(Throwable error) {
                    return null;
                }
            });
            return;
        }
        Bitmap cached = AmoClient.cachedIcon(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        AmoClient.icon(url, (bitmap, error) -> {
            if (bitmap != null && url.equals(view.getTag())) {
                view.setImageBitmap(bitmap);
            }
        });
    }

    private final class ListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tab == TAB_RECOMMENDED ? remote.size() : manager.installed().size();
        }

        @Override
        public Object getItem(int position) {
            return tab == TAB_RECOMMENDED ? remote.get(position) : manager.installed().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return tab;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            if (tab == TAB_RECOMMENDED) {
                return remoteRow(remote.get(position));
            }
            List<WebExtension> list = manager.installed();
            if (position >= list.size()) {
                return new View(FxExtensionsActivity.this);
            }
            return installedRow(list.get(position));
        }
    }

    private View wrap(LinearLayout card) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        f.setLayoutParams(new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(4), dp(12), dp(4));
        f.addView(card, lp);
        return f;
    }

    private LinearLayout plainCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(surface, 14));
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        return c;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private View remoteRow(final AmoAddon a) {
        LinearLayout card = plainCard();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        bindIcon(icon, a.iconUrl, null);
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.addView(text(a.name, 16f, textPrimary, true));
        if (!a.author.isEmpty()) {
            col.addView(text(a.author, 12f, textSecondary, false));
        }
        String meta = ratingText(a);
        if (a.recommended) {
            meta = ("おすすめ  " + meta).trim();
        }
        if (!meta.isEmpty()) {
            col.addView(text(meta, 12f, accent, false));
        }
        top.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);
        if (!a.summary.isEmpty()) {
            TextView s = text(a.summary, 13f, textPrimary, false);
            s.setMaxLines(3);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, dp(8), 0, 0);
            card.addView(s);
        }
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.END);
        bottom.setPadding(0, dp(8), 0, 0);
        final boolean has = manager.isInstalled(a.guid);
        final boolean working = busy.contains("url:" + a.downloadUrl);
        Button add = new Button(this);
        add.setAllCaps(false);
        add.setTextSize(13f);
        add.setText(has ? "追加済み" : working ? "追加中…" : "追加");
        add.setEnabled(!has && !working);
        add.setTextColor(has || working ? textSecondary : Color.WHITE);
        add.setBackground(round(has || working ? divider : accent, 18));
        add.setOnClickListener(v -> runInstall("url:" + a.downloadUrl, a.downloadUrl, WebExtensionController.INSTALLATION_METHOD_MANAGER));
        bottom.addView(add, new LinearLayout.LayoutParams(dp(110), dp(38)));
        card.addView(bottom);
        card.setOnClickListener(v -> showRemoteDetail(a));
        return wrap(card);
    }

    private void showRemoteDetail(final AmoAddon a) {
        StringBuilder sb = new StringBuilder();
        if (!a.author.isEmpty()) {
            sb.append("作者: ").append(a.author).append('\n');
        }
        String meta = ratingText(a);
        if (!meta.isEmpty()) {
            sb.append(meta).append('\n');
        }
        sb.append('\n').append(a.summary).append('\n');
        if (a.permissions.length > 0) {
            sb.append("\n要求する権限:");
            for (String l : FxPermissions.labels(a.permissions, new String[0])) {
                sb.append("\n• ").append(l);
            }
        }
        TextView body = new TextView(this);
        body.setText(sb.toString());
        body.setTextSize(14f);
        body.setPadding(dp(20), dp(8), dp(20), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(a.name).setView(sv)
                .setPositiveButton("閉じる", null);
        if (!a.pageUrl.isEmpty()) {
            b.setNeutralButton("AMOで開く", (d, w) -> openInBrowser(a.pageUrl));
        }
        b.show();
    }

    private View installedRow(final WebExtension e) {
        LinearLayout card = plainCard();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        bindIcon(icon, null, e);
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        String name = e.metaData.name == null || e.metaData.name.isEmpty() ? e.id : e.metaData.name;
        col.addView(text(name, 16f, textPrimary, true));
        col.addView(text("v" + e.metaData.version + (e.metaData.enabled ? "" : "  (無効)"), 12f, textSecondary, false));
        top.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(e.metaData.enabled);
        sw.setOnClickListener(v -> {
            sw.setEnabled(false);
            manager.setEnabled(e, sw.isChecked(), (x, err) -> {
                if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                }
            });
        });
        top.addView(sw);
        card.addView(top);
        if (e.metaData.description != null && !e.metaData.description.isEmpty()) {
            TextView d = text(e.metaData.description, 13f, textPrimary, false);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setPadding(0, dp(8), 0, 0);
            card.addView(d);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        if (e.metaData.optionsPageUrl != null && !e.metaData.optionsPageUrl.isEmpty() && e.metaData.enabled) {
            row.addView(smallButton("設定", v -> {
                manager.openOptions(e);
                finish();
            }));
        }
        row.addView(smallButton("詳細", v -> showDetail(e)));
        row.addView(smallButton("更新確認", v -> manager.update(e, (x, err) -> Toast.makeText(this,
                err != null ? err : "確認しました", Toast.LENGTH_SHORT).show())));
        row.addView(smallButton("削除", v -> confirmRemove(e)));
        card.addView(row);
        final boolean priv = e.metaData.allowedInPrivateBrowsing;
        TextView privacy = text(priv ? "プライベートタブ: 許可中(タップで変更)" : "プライベートタブ: 無効(タップで変更)", 12f, textSecondary, false);
        privacy.setPadding(0, dp(8), 0, 0);
        privacy.setOnClickListener(v -> manager.setAllowedInPrivate(e, !priv));
        card.addView(privacy);
        return wrap(card);
    }

    private Button smallButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setTextColor(accent);
        b.setBackground(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        return b;
    }
}
