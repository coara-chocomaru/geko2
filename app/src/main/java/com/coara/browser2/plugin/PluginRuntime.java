package com.coara.browser2.plugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.coara.browser2.R;
import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.PageAgent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.WebExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PluginRuntime {
    private static final String RUNTIME_ASSET = "plugin_runtime.js";
    private static final String HOST_ASSET = "plugin_host.js";
    private static final int MAX_COMMANDS = 32;
    private static final long REPUSH_DELAY_MS = 2000L;
    private static final long XHR_POLL_MS = 150L;
    private static final long XHR_GIVE_UP_MS = 130_000L;

    private static String runtimeTemplate;
    private static String hostSource;
    private static final Map<String, String> PRELUDES = new HashMap<>();
    private static final int MAX_SCRIPT_CACHE_ENTRIES = 8;
    private static final int MAX_SCRIPT_CACHE_CHARS = 512 * 1024;
    private static final int MAX_CACHED_SCRIPT_CHARS = 128 * 1024;
    private static final Map<String, String> SCRIPT_CACHE = new LinkedHashMap<String, String>(16, 0.75f, true);
    private static int SCRIPT_CACHE_VERSION = -1;
    private static int SCRIPT_CACHE_CHARS = 0;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean installed = false;
    private static int pushedVersion = -1;
    private static Context appContext;

    private static final Runnable REPUSH = new Runnable() {
        @Override
        public void run() {
            pushRegistrations(true);
        }
    };

    public static final class PluginCommand {
        public final String pluginId;
        public final String pluginTitle;
        public final String commandId;
        public final String title;

        PluginCommand(String pluginId, String pluginTitle, String commandId, String title) {
            this.pluginId = pluginId;
            this.pluginTitle = pluginTitle;
            this.commandId = commandId;
            this.title = title;
        }
    }

    private static final class ViewState {
        final Set<String> activeIds = new LinkedHashSet<>();
        long navigationStartedAt = 0L;
        long navigationGeneration = 0L;
        final Map<String, String[]> commands = new LinkedHashMap<>();
        final Map<String, WebExtension.Port> commandPorts = new HashMap<>();
        ViewOpsImpl ops;
    }

    private static final class ViewOpsImpl implements PluginViewOps {
        private final WeakReference<BrowserView> ref;
        private final ViewState state;

        ViewOpsImpl(BrowserView view, ViewState state) {
            this.ref = new WeakReference<>(view);
            this.state = state;
        }

        @Override
        public void registerCommand(String pluginId, String cmdId, String title) {
            synchronized (state.commands) {
                if (state.commands.size() >= MAX_COMMANDS && !state.commands.containsKey(cmdId)) {
                    return;
                }
                state.commands.put(cmdId, new String[]{pluginId, title});
            }
        }

        @Override
        public void unregisterCommand(String pluginId, String cmdId) {
            synchronized (state.commands) {
                String[] cur = state.commands.get(cmdId);
                if (cur != null && cur[0].equals(pluginId)) {
                    state.commands.remove(cmdId);
                }
            }
        }

        @Override
        public void openTab(final String url) {
            final BrowserView v = ref.get();
            if (v == null) {
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    PluginHost host = hostOf(v.getContext());
                    if (host != null) {
                        host.onPluginOpenTab(url);
                    }
                }
            });
        }

        @Override
        public void notifyUser(final String title, final String text) {
            final BrowserView v = ref.get();
            if (v == null) {
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String shown = text.isEmpty() ? title : title + ": " + text;
                        Toast.makeText(v.getContext().getApplicationContext(), shown, Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @Override
        public boolean copyText(final String text) {
            final BrowserView v = ref.get();
            if (v == null) {
                return false;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("plugin", text));
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            return true;
        }

        @Override
        public boolean isPrivate() {
            BrowserView v = ref.get();
            return v != null && v.isPrivateMode();
        }
    }

    private static final PluginManager.Listener LISTENER = new PluginManager.Listener() {
        @Override
        public void onPluginsChanged() {
            scheduleRepush();
        }

        @Override
        public void onPluginAutoDisabled(PluginInfo info, String reason) {
            repushNow();
        }

        @Override
        public void onPluginRuntimeChanged() {
            repushNow();
        }
    };

    private static final PageAgent.PluginHandler HANDLER = new PageAgent.PluginHandler() {
        @Override
        public void onPluginMessage(BrowserView view, WebExtension.Port port, JSONObject message) {
            handleMessage(view, port, message);
        }
    };

    private PluginRuntime() {
    }

    private static PluginHost hostOf(Context context) {
        Context c = context;
        int depth = 0;
        while (c != null && depth < 8) {
            if (c instanceof PluginHost) {
                return (PluginHost) c;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
            depth++;
        }
        return null;
    }

    private static synchronized void install(Context context) {
        if (installed) {
            return;
        }
        installed = true;
        appContext = context.getApplicationContext();
        BrowserEngine.get().getAgent().setPluginHandler(HANDLER);
        PluginManager.get(context).addListener(LISTENER);
        pushRegistrations(false);
    }

    public static void applyRegistrationsThen(Runnable afterApplied) {
        MAIN.removeCallbacks(REPUSH);
        pushRegistrations(true);
        BrowserEngine.get().getAgent().afterPluginsApplied(afterApplied, 2500L);
    }

    private static void repushNow() {
        MAIN.removeCallbacks(REPUSH);
        MAIN.post(REPUSH);
    }

    private static void scheduleRepush() {
        MAIN.removeCallbacks(REPUSH);
        MAIN.postDelayed(REPUSH, REPUSH_DELAY_MS);
    }

    private static void pushRegistrations(boolean force) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            PluginManager manager = PluginManager.get(context);
            manager.reloadIfChanged();
            JSONArray list = new JSONArray();
            for (PluginInfo info : manager.getEnabledPlugins()) {
                JSONObject snap = null;
                List<PluginEntry> entries = info.entries();
                for (int i = 0; i < entries.size(); i++) {
                    PluginEntry entry = entries.get(i);
                    String js = buildEntryScript(manager, info, entry, i);
                    if (js == null) {
                        continue;
                    }
                    if (snap == null) {
                        snap = manager.buildSnapshot(info);
                    }
                    JSONObject spec = new JSONObject();
                    spec.put("id", info.id + "#" + i);
                    spec.put("matches", geckoMatches(entry));
                    spec.put("allFrames", entry.allFrames);
                    spec.put("code", buildWrapper(manager, info, snap, js, i));
                    list.put(spec);
                }
            }
            pushedVersion = manager.version();
            BrowserEngine.get().getAgent().pushPlugins(list);
        } catch (Throwable t) {
            logFailure(context, "pushRegistrations", t);
        }
    }

    private static JSONArray geckoMatches(PluginEntry entry) {
        JSONArray out = new JSONArray();
        Set<String> origins = PluginPatterns.toAllowedOriginRules(PluginPatterns.effectiveMatches(entry.matches));
        Set<String> seen = new LinkedHashSet<>();
        for (String origin : origins) {
            if ("*".equals(origin)) {
                seen.add("http://*/*");
                seen.add("https://*/*");
            } else {
                seen.add(origin + "/*");
            }
        }
        for (String s : seen) {
            out.put(s);
        }
        return out;
    }

    private static String buildWrapper(PluginManager manager, PluginInfo info, JSONObject snap, String entryScript, int index) {
        String host = loadHost(manager.context());
        String bridgeName = bridgeName(manager, info, index);
        StringBuilder sb = new StringBuilder(host.length() + entryScript.length() + 512);
        sb.append(host).append("\n;window.__coaraPlg.run(")
                .append(JSONObject.quote(info.id)).append(',')
                .append(JSONObject.quote(manager.getToken())).append(',')
                .append(JSONObject.quote(bridgeName)).append(',')
                .append(snap.toString()).append(',')
                .append(JSONObject.quote(entryScript)).append(");");
        return sb.toString();
    }

    private static String bridgeName(PluginManager manager, PluginInfo info, int index) {
        return PluginManager.BRIDGE_NAME + "_" + manager.keyFor(info.id + "#" + index);
    }

    private static void handleMessage(final BrowserView view, final WebExtension.Port port, JSONObject m) {
        if (view == null || m == null || !"inv".equals(m.optString("t"))) {
            return;
        }
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            ViewState state = stateOf(view);
            PluginManager manager = PluginManager.get(context);
            final String id = m.optString("id");
            final String op = m.optString("op");
            final String a = m.optString("a");
            final String trustedOrigin = m.optString("originUrl", "");
            String result = manager.invoke(state == null ? null : state.ops, m.optString("tk"), id, op, a,
                    m.optString("b"), m.optString("c"), trustedOrigin);
            if (state != null && "1".equals(result) && "cmd".equals(op) && a.length() <= 128) {
                synchronized (state.commands) {
                    if (state.commands.containsKey(a)) {
                        state.commandPorts.put(a, port);
                    }
                }
            } else if (state != null && "uncmd".equals(op)) {
                synchronized (state.commands) {
                    state.commandPorts.remove(a);
                }
            }
            if ("sset".equals(op) || "sdel".equals(op) || "sclear".equals(op)) {
                scheduleRepush();
            } else if ("xhr".equals(op)) {
                if ("1".equals(result)) {
                    pollXhr(manager, port, id, a, System.currentTimeMillis());
                } else {
                    deliverXhr(port, id, a, "{\"error\":\"denied\"}");
                }
            }
        } catch (Throwable t) {
            logFailure(context, "handleMessage", t);
        }
    }

    private static void pollXhr(final PluginManager manager, final WebExtension.Port port, final String id,
                                final String reqId, final long startedAt) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                String r = manager.xhrResult(id, reqId);
                if (r == null) {
                    deliverXhr(port, id, reqId, "{\"error\":\"lost\"}");
                } else if (r.isEmpty()) {
                    if (System.currentTimeMillis() - startedAt > XHR_GIVE_UP_MS) {
                        deliverXhr(port, id, reqId, "{\"error\":\"timeout\"}");
                    } else {
                        pollXhr(manager, port, id, reqId, startedAt);
                    }
                } else {
                    deliverXhr(port, id, reqId, r);
                }
            }
        }, XHR_POLL_MS);
    }

    private static void deliverXhr(WebExtension.Port port, String id, String reqId, String value) {
        try {
            port.postMessage(PageAgent.json("t", "xhr", "pid", id, "rid", reqId, "v", value));
        } catch (Throwable ignored) {
        }
    }

    public static List<PluginCommand> getCommands(BrowserView view) {
        List<PluginCommand> out = new ArrayList<>();
        if (view == null) {
            return out;
        }
        ViewState state = stateOf(view);
        if (state == null) {
            return out;
        }
        PluginManager manager = PluginManager.get(view.getContext());
        synchronized (state.commands) {
            for (Map.Entry<String, String[]> e : state.commands.entrySet()) {
                PluginInfo p = manager.get(e.getValue()[0]);
                if (p != null && p.enabled) {
                    out.add(new PluginCommand(p.id, p.title, e.getKey(), e.getValue()[1]));
                }
            }
        }
        return out;
    }

    public static void runCommand(BrowserView view, String commandId) {
        if (view == null || commandId == null || commandId.isEmpty()) {
            return;
        }
        ViewState state = stateOf(view);
        if (state == null) {
            return;
        }
        WebExtension.Port port;
        synchronized (state.commands) {
            if (!state.commands.containsKey(commandId)) {
                return;
            }
            port = state.commandPorts.get(commandId);
        }
        if (port == null) {
            return;
        }
        try {
            port.postMessage(PageAgent.json("t", "cmd", "id", commandId));
        } catch (Throwable ignored) {
            synchronized (state.commands) {
                state.commandPorts.remove(commandId);
            }
        }
    }

    public static void attach(Context context, BrowserView view) {
        if (context == null || view == null) {
            return;
        }
        try {
            install(context);
            ViewState state = new ViewState();
            state.ops = new ViewOpsImpl(view, state);
            view.setTag(R.id.plugin_view_state, state);
            PluginManager manager = PluginManager.get(context);
            if (pushedVersion != manager.version()) {
                scheduleRepush();
            }
        } catch (Throwable t) {
            logFailure(context, "attach", t);
        }
    }

    public static void prepareForFullReload(BrowserView view) {
        if (view == null) {
            return;
        }
        try {
            ViewState state = stateOf(view);
            if (state == null) {
                return;
            }
            PluginManager manager = PluginManager.get(view.getContext());
            manager.reloadIfChanged();
            resetNavigation(state);
        } catch (Throwable t) {
            logFailure(view.getContext(), "prepareForFullReload", t);
        }
    }

    private static void resetNavigation(ViewState state) {
        state.navigationStartedAt = System.currentTimeMillis();
        state.navigationGeneration++;
        state.activeIds.clear();
        synchronized (state.commands) {
            state.commands.clear();
            state.commandPorts.clear();
        }
    }

    public static void onPageStarted(BrowserView view, String url) {
        if (view == null) {
            return;
        }
        try {
            ViewState state = stateOf(view);
            if (state == null) {
                return;
            }
            PluginManager manager = PluginManager.get(view.getContext());
            manager.reloadIfChanged();
            resetNavigation(state);
            if (url != null) {
                List<String> ids = manager.matchingEnabledIds(url);
                if (!ids.isEmpty()) {
                    state.activeIds.addAll(ids);
                    manager.markInjected(ids);
                }
            }
        } catch (Throwable t) {
            logFailure(view.getContext(), "onPageStarted", t);
        }
    }

    public static void onPageCommitVisible(BrowserView view, String url) {
    }

    public static void onPageFinished(BrowserView view, String url) {
    }

    public static void onRenderProcessGone(BrowserView view, boolean didCrash) {
        if (view == null) {
            return;
        }
        try {
            ViewState state = stateOf(view);
            PluginManager manager = PluginManager.get(view.getContext());
            if (state == null || state.activeIds.isEmpty()) {
                return;
            }
            List<String> ids = new ArrayList<>(state.activeIds);
            long age = System.currentTimeMillis() - state.navigationStartedAt;
            if (didCrash) {
                manager.recordCrash(ids, "レンダラープロセスがクラッシュしました");
            } else {
                for (String id : ids) {
                    manager.log("W", id, "レンダラープロセスがシステムにより終了されました(ページ読み込みから" + (age / 1000) + "秒)");
                }
            }
            state.activeIds.clear();
        } catch (Throwable t) {
            logFailure(view.getContext(), "onRenderProcessGone", t);
        }
    }

    public static void onHostPause(Context context) {
        try {
            PluginManager.get(context).onHostPause();
        } catch (Throwable ignored) {
        }
    }

    static String buildEntryScript(PluginManager manager, PluginInfo info, PluginEntry entry, int index) {
        try {
            int version = manager.version();
            synchronized (SCRIPT_CACHE) {
                if (SCRIPT_CACHE_VERSION != version) {
                    SCRIPT_CACHE.clear();
                    SCRIPT_CACHE_CHARS = 0;
                    SCRIPT_CACHE_VERSION = version;
                }
                String cached = SCRIPT_CACHE.get(info.id + "#" + index);
                if (cached != null) {
                    return cached;
                }
            }
            String template = loadAsset(manager.context(), RUNTIME_ASSET, true);
            if (template == null) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            for (String name : entry.scripts) {
                String src = manager.readText(info, name);
                if (src == null) {
                    manager.log("W", info.id, "スクリプトを読み込めません: " + name);
                    continue;
                }
                body.append(src).append("\n;\n");
            }
            JSONArray css = new JSONArray();
            for (String name : entry.styles) {
                String text = manager.readText(info, name);
                if (text == null) {
                    manager.log("W", info.id, "スタイルを読み込めません: " + name);
                    continue;
                }
                css.put(text);
            }
            if (body.length() == 0 && css.length() == 0) {
                return null;
            }
            JSONObject meta = new JSONObject();
            meta.put("id", info.id);
            meta.put("title", info.title);
            meta.put("version", info.version);
            meta.put("description", info.description);
            meta.put("author", info.author);
            meta.put("homepage", info.homepage);
            meta.put("format", info.format);
            meta.put("runAt", entry.runAt);
            meta.put("allFrames", entry.allFrames);
            meta.put("permissions", new JSONArray(info.permissions));
            meta.put("rawMatches", new JSONArray(entry.matches));
            meta.put("rawExcludes", new JSONArray(entry.excludes));
            meta.put("matches", new JSONArray(PluginPatterns.toRegexList(PluginPatterns.effectiveMatches(entry.matches))));
            meta.put("excludes", new JSONArray(PluginPatterns.toRegexList(entry.excludes)));

            String prelude = "";
            String kind = info.apiKind();
            if (kind.equals("gm")) {
                prelude = loadPrelude(manager.context(), "plugin_api_gm.js");
            } else if (kind.equals("webext")) {
                prelude = loadPrelude(manager.context(), "plugin_api_webext.js");
            }

            int idx = template.indexOf("%BODY%");
            if (idx < 0) {
                return null;
            }
            String head = template.substring(0, idx);
            String tail = template.substring(idx + "%BODY%".length());
            List<String[]> headValues = new ArrayList<>();
            headValues.add(new String[]{"%META%", meta.toString()});
            headValues.add(new String[]{"%CSS%", css.toString()});
            headValues.add(new String[]{"%KEY%", manager.keyFor(info.id + "#" + index)});
            headValues.add(new String[]{"%PRELUDE%", prelude});
            List<String[]> tailValues = new ArrayList<>();
            tailValues.add(new String[]{"%ID%", info.id + "/" + index});
            String built = fill(head, headValues) + body + fill(tail, tailValues);
            if (built.length() <= MAX_CACHED_SCRIPT_CHARS) {
                synchronized (SCRIPT_CACHE) {
                    String key = info.id + "#" + index;
                    while ((!SCRIPT_CACHE.isEmpty()) && (SCRIPT_CACHE.size() >= MAX_SCRIPT_CACHE_ENTRIES
                            || SCRIPT_CACHE_CHARS + built.length() > MAX_SCRIPT_CACHE_CHARS)) {
                        Map.Entry<String, String> eldest = SCRIPT_CACHE.entrySet().iterator().next();
                        SCRIPT_CACHE_CHARS -= eldest.getValue().length();
                        SCRIPT_CACHE.remove(eldest.getKey());
                    }
                    String previous = SCRIPT_CACHE.put(key, built);
                    if (previous != null) {
                        SCRIPT_CACHE_CHARS -= previous.length();
                    }
                    SCRIPT_CACHE_CHARS += built.length();
                }
            }
            return built;
        } catch (Throwable t) {
            manager.log("E", info.id, "スクリプト生成に失敗: " + t);
            return null;
        }
    }

    private static String readAsset(Context context, String asset) {
        try (InputStream in = context.getAssets().open(asset)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized String loadPrelude(Context context, String asset) {
        String cached = PRELUDES.get(asset);
        if (cached != null) {
            return cached;
        }
        String text = readAsset(context, asset);
        if (text == null) {
            return "";
        }
        PRELUDES.put(asset, text);
        return text;
    }

    private static synchronized String loadAsset(Context context, String asset, boolean runtime) {
        if (runtime) {
            if (runtimeTemplate == null) {
                runtimeTemplate = readAsset(context, asset);
            }
            return runtimeTemplate;
        }
        return null;
    }

    private static synchronized String loadHost(Context context) {
        if (hostSource == null) {
            String text = readAsset(context, HOST_ASSET);
            hostSource = text == null ? "" : text;
        }
        return hostSource;
    }

    private static String fill(String template, List<String[]> values) {
        StringBuilder out = new StringBuilder(template.length() + 256);
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '%') {
                boolean replaced = false;
                for (String[] kv : values) {
                    if (template.startsWith(kv[0], i)) {
                        out.append(kv[1]);
                        i += kv[0].length();
                        replaced = true;
                        break;
                    }
                }
                if (replaced) {
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static ViewState stateOf(BrowserView view) {
        Object o = view.getTag(R.id.plugin_view_state);
        return o instanceof ViewState ? (ViewState) o : null;
    }

    private static void logFailure(Context context, String where, Throwable t) {
        try {
            PluginManager.get(context).log("E", "-", "PluginRuntime." + where + " で例外: " + t);
        } catch (Throwable ignored) {
        }
    }
}
