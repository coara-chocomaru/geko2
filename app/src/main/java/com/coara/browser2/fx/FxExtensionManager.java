package com.coara.browser2.fx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.coara.browser2.gecko.BrowserEngine;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FxExtensionManager {

    public interface Listener {
        void onExtensionsChanged();

        void onExtensionError(String message);
    }

    public interface TabHost {
        GeckoSession openExtensionTab(boolean active);

        void openUrl(String url);
    }

    public interface InstallCallback {
        void onDone(WebExtension extension, String error);
    }

    private static final String STAGING_DIR = "fx_install";
    private static final long MAX_PACKAGE_BYTES = 128L * 1024L * 1024L;

    private static FxExtensionManager instance;

    public static synchronized FxExtensionManager get() {
        if (instance == null) {
            instance = new FxExtensionManager();
        }
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WebExtension> installed = new ArrayList<>();
    private final Map<String, WebExtension.Action> browserActions = new HashMap<>();
    private final Map<String, WebExtension.Action> pageActions = new HashMap<>();
    private final Map<GeckoSession, Map<String, WebExtension.Action>> sessionBrowserActions = new WeakHashMap<>();
    private final Map<GeckoSession, Map<String, WebExtension.Action>> sessionPageActions = new WeakHashMap<>();
    private final Map<GeckoSession, Boolean> sessions = new WeakHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private WeakReference<TabHost> tabHost = new WeakReference<>(null);
    private boolean started;
    private boolean listed;
    private GeckoSession activeSession;

    private FxExtensionManager() {
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setForeground(Activity activity) {
        foreground = new WeakReference<>(activity);
    }

    public void clearForeground(Activity activity) {
        if (foreground.get() == activity) {
            foreground = new WeakReference<>(null);
        }
    }

    public void setTabHost(TabHost host) {
        tabHost = new WeakReference<>(host);
    }

    public void clearTabHost(TabHost host) {
        if (tabHost.get() == host) {
            tabHost = new WeakReference<>(null);
        }
    }

    private Activity foreground() {
        Activity a = foreground.get();
        return a == null || a.isFinishing() ? null : a;
    }

    private GeckoRuntime runtime() {
        return BrowserEngine.isInitialized() ? BrowserEngine.get().getRuntime() : null;
    }

    private WebExtensionController controller() {
        GeckoRuntime r = runtime();
        return r == null ? null : r.getWebExtensionController();
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        WebExtensionController c = controller();
        if (c == null) {
            return;
        }
        started = true;
        c.setPromptDelegate(promptDelegate);
        c.setAddonManagerDelegate(addonDelegate);
        refresh(null);
    }

    public boolean isListed() {
        return listed;
    }

    public List<WebExtension> installed() {
        synchronized (installed) {
            return new ArrayList<>(installed);
        }
    }

    public boolean isInstalled(String id) {
        synchronized (installed) {
            for (WebExtension e : installed) {
                if (e.id.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void refresh(final Runnable done) {
        start();
        WebExtensionController c = controller();
        if (c == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        c.list().then(new GeckoResult.OnValueListener<List<WebExtension>, Void>() {
            @Override
            public GeckoResult<Void> onValue(List<WebExtension> value) {
                List<WebExtension> next = new ArrayList<>();
                if (value != null) {
                    for (WebExtension e : value) {
                        if (!e.isBuiltIn) {
                            next.add(e);
                        }
                    }
                }
                Collections.sort(next, (a, b) -> {
                    String an = a.metaData.name == null ? a.id : a.metaData.name;
                    String bn = b.metaData.name == null ? b.id : b.metaData.name;
                    return an.compareToIgnoreCase(bn);
                });
                synchronized (installed) {
                    installed.clear();
                    installed.addAll(next);
                }
                listed = true;
                for (WebExtension e : next) {
                    attach(e);
                }
                notifyChanged();
                if (done != null) {
                    done.run();
                }
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                listed = true;
                notifyChanged();
                if (done != null) {
                    done.run();
                }
                return null;
            }
        });
    }

    private void notifyChanged() {
        for (Listener l : listeners) {
            l.onExtensionsChanged();
        }
    }

    private void notifyError(String message) {
        for (Listener l : listeners) {
            l.onExtensionError(message);
        }
    }

    private void attach(WebExtension extension) {
        extension.setActionDelegate(actionDelegate);
        extension.setTabDelegate(tabDelegate);
        List<GeckoSession> live;
        synchronized (sessions) {
            live = new ArrayList<>(sessions.keySet());
        }
        for (GeckoSession s : live) {
            bindSession(s, extension);
        }
    }

    private void bindSession(GeckoSession session, WebExtension extension) {
        try {
            session.getWebExtensionController().setActionDelegate(extension, sessionActionDelegate);
        } catch (Throwable ignored) {
        }
    }

    public void onSessionAttached(GeckoSession session, boolean active) {
        if (session == null) {
            return;
        }
        synchronized (sessions) {
            sessions.put(session, Boolean.TRUE);
        }
        for (WebExtension e : installed()) {
            bindSession(session, e);
        }
        if (active) {
            onTabActive(session, true);
        }
    }

    public void onTabActive(GeckoSession session, boolean active) {
        WebExtensionController c = controller();
        if (c == null || session == null) {
            return;
        }
        try {
            c.setTabActive(session, active);
        } catch (Throwable ignored) {
        }
        if (active) {
            activeSession = session;
        } else if (activeSession == session) {
            activeSession = null;
        }
    }

    private final WebExtension.ActionDelegate actionDelegate = new WebExtension.ActionDelegate() {
        @Override
        public void onBrowserAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            storeAction(browserActions, sessionBrowserActions, extension, session, action);
        }

        @Override
        public void onPageAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            storeAction(pageActions, sessionPageActions, extension, session, action);
        }

        @Override
        public GeckoResult<GeckoSession> onOpenPopup(WebExtension extension, WebExtension.Action action) {
            return openPopup();
        }

        @Override
        public GeckoResult<GeckoSession> onTogglePopup(WebExtension extension, WebExtension.Action action) {
            if (FxPopup.isShowing()) {
                FxPopup.dismiss();
                return null;
            }
            return openPopup();
        }
    };

    private final WebExtension.ActionDelegate sessionActionDelegate = new WebExtension.ActionDelegate() {
        @Override
        public void onBrowserAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            storeAction(browserActions, sessionBrowserActions, extension, session, action);
        }

        @Override
        public void onPageAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            storeAction(pageActions, sessionPageActions, extension, session, action);
        }
    };

    private void storeAction(Map<String, WebExtension.Action> defaults,
                             Map<GeckoSession, Map<String, WebExtension.Action>> perSession,
                             WebExtension extension, GeckoSession session, WebExtension.Action action) {
        synchronized (defaults) {
            if (session == null) {
                defaults.put(extension.id, action);
            } else {
                Map<String, WebExtension.Action> m = perSession.get(session);
                if (m == null) {
                    m = new HashMap<>();
                    perSession.put(session, m);
                }
                m.put(extension.id, action);
            }
        }
        main.post(this::notifyChanged);
    }

    private GeckoResult<GeckoSession> openPopup() {
        Activity a = foreground();
        GeckoRuntime r = runtime();
        if (a == null || r == null) {
            return null;
        }
        return FxPopup.show(a, r);
    }

    private final WebExtension.TabDelegate tabDelegate = new WebExtension.TabDelegate() {
        @Override
        public GeckoResult<GeckoSession> onNewTab(WebExtension source, WebExtension.CreateTabDetails details) {
            TabHost host = tabHost.get();
            if (host == null) {
                return null;
            }
            boolean active = details.active == null || details.active;
            GeckoSession s = host.openExtensionTab(active);
            return s == null ? null : GeckoResult.fromValue(s);
        }

        @Override
        public void onOpenOptionsPage(WebExtension source) {
            String url = source.metaData.optionsPageUrl;
            TabHost host = tabHost.get();
            if (url != null && !url.isEmpty() && host != null) {
                FxPopup.dismiss();
                host.openUrl(url);
            }
        }
    };

    public static final class ActionEntry {
        public final WebExtension extension;
        public final WebExtension.Action action;
        public final boolean page;

        ActionEntry(WebExtension extension, WebExtension.Action action, boolean page) {
            this.extension = extension;
            this.action = action;
            this.page = page;
        }

        public String title() {
            String t = action.title;
            if (t != null && !t.isEmpty()) {
                return t;
            }
            return extension.metaData.name == null ? extension.id : extension.metaData.name;
        }

        public boolean enabled() {
            return action.enabled == null || action.enabled;
        }
    }

    private WebExtension.Action resolve(Map<String, WebExtension.Action> defaults,
                                        Map<GeckoSession, Map<String, WebExtension.Action>> perSession,
                                        String id, GeckoSession session) {
        synchronized (defaults) {
            WebExtension.Action base = defaults.get(id);
            Map<String, WebExtension.Action> m = session == null ? null : perSession.get(session);
            WebExtension.Action over = m == null ? null : m.get(id);
            if (over != null && base != null) {
                return over.withDefault(base);
            }
            return over != null ? over : base;
        }
    }

    public List<ActionEntry> actionsFor(GeckoSession session) {
        List<ActionEntry> out = new ArrayList<>();
        for (WebExtension e : installed()) {
            if (!e.metaData.enabled) {
                continue;
            }
            WebExtension.Action b = resolve(browserActions, sessionBrowserActions, e.id, session);
            if (b != null) {
                out.add(new ActionEntry(e, b, false));
            }
            WebExtension.Action p = resolve(pageActions, sessionPageActions, e.id, session);
            if (p != null && (p.enabled == null || p.enabled)) {
                out.add(new ActionEntry(e, p, true));
            }
        }
        return out;
    }

    public GeckoSession activeSession() {
        return activeSession;
    }

    private final WebExtensionController.PromptDelegate promptDelegate = new WebExtensionController.PromptDelegate() {
        @Override
        public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                WebExtension extension, String[] permissions, String[] origins, String[] dataCollection) {
            return askInstall(extension, permissions, origins, dataCollection);
        }

        @Override
        public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension extension, String[] permissions, String[] origins, String[] dataCollection) {
            return askPermissions(extension, "権限の更新", permissions, origins, dataCollection);
        }

        @Override
        public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension extension, String[] permissions, String[] origins, String[] dataCollection) {
            return askPermissions(extension, "追加の権限", permissions, origins, dataCollection);
        }
    };

    private final WebExtensionController.AddonManagerDelegate addonDelegate = new WebExtensionController.AddonManagerDelegate() {
        @Override
        public void onInstalled(WebExtension extension) {
            refresh(null);
        }

        @Override
        public void onUninstalled(WebExtension extension) {
            synchronized (browserActions) {
                browserActions.remove(extension.id);
                pageActions.remove(extension.id);
            }
            refresh(null);
        }

        @Override
        public void onEnabled(WebExtension extension) {
            refresh(null);
        }

        @Override
        public void onDisabled(WebExtension extension) {
            refresh(null);
        }

        @Override
        public void onOptionalPermissionsChanged(WebExtension extension) {
            refresh(null);
        }

        @Override
        public void onInstallationFailed(WebExtension extension, WebExtension.InstallException exception) {
            if (exception != null && exception.code != WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED) {
                notifyError(describe(exception));
            }
        }
    };

    private static String nameOf(WebExtension e) {
        String n = e.metaData.name;
        return n == null || n.isEmpty() ? e.id : n;
    }

    private GeckoResult<WebExtension.PermissionPromptResponse> askInstall(final WebExtension extension,
                                                                         String[] permissions, String[] origins, String[] dataCollection) {
        final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
        final Activity a = foreground();
        if (a == null) {
            result.complete(new WebExtension.PermissionPromptResponse(false, false, false));
            return result;
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView body = new TextView(a);
        body.setText(buildPermissionText(permissions, origins, dataCollection, true));
        body.setTextSize(14f);
        box.addView(body);
        final CheckBox privateBox = new CheckBox(a);
        privateBox.setText("プライベート(シークレット)タブでも実行する");
        box.addView(privateBox);
        final CheckBox dataBox = new CheckBox(a);
        boolean wantsTechnical = false;
        for (String d : dataCollection) {
            if ("technicalAndInteraction".equals(d)) {
                wantsTechnical = true;
            }
        }
        if (wantsTechnical) {
            dataBox.setText("技術情報と操作データの収集を許可する");
            dataBox.setChecked(true);
            box.addView(dataBox);
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        final boolean[] answered = {false};
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("「" + nameOf(extension) + "」を追加しますか?")
                .setView(scroll)
                .setPositiveButton("追加", (d, w) -> {
                    answered[0] = true;
                    result.complete(new WebExtension.PermissionPromptResponse(true, privateBox.isChecked(), dataBox.isChecked()));
                })
                .setNegativeButton("キャンセル", (d, w) -> {
                    answered[0] = true;
                    result.complete(new WebExtension.PermissionPromptResponse(false, false, false));
                })
                .setOnDismissListener(d -> {
                    if (!answered[0]) {
                        result.complete(new WebExtension.PermissionPromptResponse(false, false, false));
                    }
                })
                .create();
        dialog.show();
        return result;
    }

    private GeckoResult<AllowOrDeny> askPermissions(WebExtension extension, String title, String[] permissions,
                                                    String[] origins, String[] dataCollection) {
        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        Activity a = foreground();
        if (a == null) {
            result.complete(AllowOrDeny.DENY);
            return result;
        }
        final boolean[] answered = {false};
        TextView body = new TextView(a);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad / 2, pad, 0);
        body.setText(buildPermissionText(permissions, origins, dataCollection, false));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(body);
        new AlertDialog.Builder(a)
                .setTitle(nameOf(extension) + ": " + title)
                .setView(scroll)
                .setPositiveButton("許可", (d, w) -> {
                    answered[0] = true;
                    result.complete(AllowOrDeny.ALLOW);
                })
                .setNegativeButton("拒否", (d, w) -> {
                    answered[0] = true;
                    result.complete(AllowOrDeny.DENY);
                })
                .setOnDismissListener(d -> {
                    if (!answered[0]) {
                        result.complete(AllowOrDeny.DENY);
                    }
                })
                .show();
        return result;
    }

    private static String buildPermissionText(String[] permissions, String[] origins, String[] dataCollection, boolean install) {
        StringBuilder sb = new StringBuilder();
        List<String> labels = FxPermissions.labels(permissions, origins);
        if (labels.isEmpty()) {
            sb.append(install ? "この拡張機能は特別な権限を必要としません。" : "追加の権限はありません。");
        } else {
            sb.append(install ? "この拡張機能は次の権限を要求します:\n" : "次の権限を許可しますか:\n");
            for (String l : labels) {
                sb.append("\n• ").append(l);
            }
        }
        if (FxPermissions.isBroad(permissions, origins)) {
            sb.append("\n\n※ 閲覧するほぼすべてのページの内容を読み取れる権限です。信頼できる拡張機能のみ追加してください。");
        }
        if (dataCollection != null && dataCollection.length > 0) {
            sb.append("\n\n開発者によるデータ収集:");
            for (String d : dataCollection) {
                sb.append("\n• ").append(FxPermissions.dataLabel(d));
            }
        }
        return sb.toString();
    }

    public static String describe(WebExtension.InstallException e) {
        switch (e.code) {
            case WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE:
                return "ダウンロードに失敗しました。ネットワーク接続を確認してください";
            case WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH:
                return "ファイルの整合性検証に失敗しました";
            case WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE:
                return "拡張機能ファイル(.xpi)が壊れているか形式が不正です";
            case WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS:
                return "ファイルにアクセスできませんでした";
            case WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED:
                return "Mozillaの署名がない拡張機能はインストールできません";
            case WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE:
            case WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE:
                return "対応していない種類のアドオンです(拡張機能のみ対応)";
            case WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE:
                return "このバージョンのブラウザと互換性がありません";
            case WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED:
            case WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED:
                return "セキュリティ上の理由でブロックされている拡張機能です";
            case WebExtension.InstallException.ErrorCodes.ERROR_ADMIN_INSTALL_ONLY:
                return "管理者のみがインストールできる拡張機能です";
            case WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED:
                return "キャンセルしました";
            default:
                return "インストールに失敗しました (コード " + e.code + ")";
        }
    }

    public void installFromUrl(String url, String method, final InstallCallback callback) {
        start();
        WebExtensionController c = controller();
        if (c == null) {
            deliver(callback, null, "エンジンの準備ができていません");
            return;
        }
        c.install(url, method).then(new GeckoResult.OnValueListener<WebExtension, Void>() {
            @Override
            public GeckoResult<Void> onValue(WebExtension value) {
                if (value != null) {
                    attach(value);
                }
                refresh(null);
                deliver(callback, value, null);
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                String message;
                if (error instanceof WebExtension.InstallException) {
                    WebExtension.InstallException ie = (WebExtension.InstallException) error;
                    message = ie.code == WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ? null : describe(ie);
                } else {
                    message = error == null || error.getMessage() == null ? "インストールに失敗しました" : error.getMessage();
                }
                deliver(callback, null, message);
                return null;
            }
        });
    }

    private void deliver(final InstallCallback callback, final WebExtension extension, final String error) {
        if (callback == null) {
            return;
        }
        main.post(() -> callback.onDone(extension, error));
    }

    public void installFromContentUri(final Context context, final Uri uri, final InstallCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            File staged = null;
            String error = null;
            try {
                File dir = new File(app.getCacheDir(), STAGING_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new java.io.IOException("cache");
                }
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) {
                        if (System.currentTimeMillis() - f.lastModified() > 10L * 60L * 1000L) {
                            f.delete();
                        }
                    }
                }
                staged = new File(dir, "ext_" + System.currentTimeMillis() + ".xpi");
                long total = 0;
                try (InputStream in = app.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(staged)) {
                    if (in == null) {
                        throw new java.io.IOException("open");
                    }
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_PACKAGE_BYTES) {
                            throw new java.io.IOException("too large");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                if (total < 4 || !looksLikeZip(staged)) {
                    error = "拡張機能ファイル(.xpi / .zip)ではありません";
                }
            } catch (Exception e) {
                error = "ファイルを読み込めませんでした";
            }
            final File file = staged;
            final String err = error;
            main.post(() -> {
                if (err != null || file == null) {
                    if (file != null) {
                        file.delete();
                    }
                    deliver(callback, null, err);
                    return;
                }
                installFromUrl(Uri.fromFile(file).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE, (ext, e) -> {
                    file.delete();
                    if (callback != null) {
                        callback.onDone(ext, e);
                    }
                });
            });
        }, "coara-fx-stage").start();
    }

    private static boolean looksLikeZip(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isExtensionPackage(String url, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("application/x-xpinstall")) {
            return true;
        }
        if (url == null) {
            return false;
        }
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        int h = u.indexOf('#');
        if (h >= 0) {
            u = u.substring(0, h);
        }
        return (u.startsWith("https://") || u.startsWith("http://")) && u.toLowerCase(java.util.Locale.ROOT).endsWith(".xpi");
    }

    public boolean offerInstall(final Activity activity, final String url, String mimeType, final Runnable downloadInstead) {
        if (activity == null || !isExtensionPackage(url, mimeType)) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle("Firefox拡張機能")
                .setMessage("この拡張機能(.xpi)をブラウザに追加しますか?\n\n" + url)
                .setPositiveButton("追加", (d, w) -> installFromUrl(url, WebExtensionController.INSTALLATION_METHOD_MANAGER, (ext, err) -> {
                    android.widget.Toast.makeText(activity,
                            err != null ? err : ext != null ? "追加しました" : "キャンセルしました",
                            android.widget.Toast.LENGTH_LONG).show();
                }))
                .setNegativeButton("キャンセル", null);
        if (downloadInstead != null) {
            b.setNeutralButton("ファイルとして保存", (d, w) -> downloadInstead.run());
        }
        b.show();
        return true;
    }

    public void setEnabled(WebExtension extension, boolean enabled, final InstallCallback callback) {
        WebExtensionController c = controller();
        if (c == null) {
            return;
        }
        GeckoResult<WebExtension> r = enabled
                ? c.enable(extension, WebExtensionController.EnableSource.USER)
                : c.disable(extension, WebExtensionController.EnableSource.USER);
        r.then(new GeckoResult.OnValueListener<WebExtension, Void>() {
            @Override
            public GeckoResult<Void> onValue(WebExtension value) {
                refresh(null);
                deliver(callback, value, null);
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                refresh(null);
                deliver(callback, null, "切り替えに失敗しました");
                return null;
            }
        });
    }

    public void setAllowedInPrivate(WebExtension extension, boolean allowed) {
        WebExtensionController c = controller();
        if (c == null) {
            return;
        }
        c.setAllowedInPrivateBrowsing(extension, allowed).then(new GeckoResult.OnValueListener<WebExtension, Void>() {
            @Override
            public GeckoResult<Void> onValue(WebExtension value) {
                refresh(null);
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                refresh(null);
                return null;
            }
        });
    }

    public void uninstall(WebExtension extension, final InstallCallback callback) {
        WebExtensionController c = controller();
        if (c == null) {
            return;
        }
        c.uninstall(extension).then(new GeckoResult.OnValueListener<Void, Void>() {
            @Override
            public GeckoResult<Void> onValue(Void value) {
                refresh(null);
                deliver(callback, null, null);
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                refresh(null);
                deliver(callback, null, "削除に失敗しました");
                return null;
            }
        });
    }

    public void update(WebExtension extension, final InstallCallback callback) {
        WebExtensionController c = controller();
        if (c == null) {
            return;
        }
        c.update(extension).then(new GeckoResult.OnValueListener<WebExtension, Void>() {
            @Override
            public GeckoResult<Void> onValue(WebExtension value) {
                refresh(null);
                deliver(callback, value, null);
                return null;
            }
        }, new GeckoResult.OnExceptionListener<Void>() {
            @Override
            public GeckoResult<Void> onException(Throwable error) {
                deliver(callback, null, "更新を確認できませんでした");
                return null;
            }
        });
    }

    public void openOptions(WebExtension extension) {
        String url = extension.metaData.optionsPageUrl;
        TabHost host = tabHost.get();
        if (url == null || url.isEmpty() || host == null) {
            return;
        }
        host.openUrl(url);
    }
}
