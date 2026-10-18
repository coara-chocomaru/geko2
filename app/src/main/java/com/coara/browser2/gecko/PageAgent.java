package com.coara.browser2.gecko;

import com.coara.browser2.util.AdBlockEngine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class PageAgent {
    public static final String APP_PAGE = "coaraPage";
    public static final String APP_PLUGIN = "coaraPlugin";
    public static final String APP_BACKGROUND = "coaraBg";

    public interface PluginHandler {
        void onPluginMessage(BrowserView view, WebExtension.Port port, JSONObject message);
    }

    public interface CookieCallback {
        void onResult(boolean ok, JSONArray list, int count);
    }

    private final BrowserEngine engine;
    private final Map<GeckoSession, WeakReference<BrowserView>> views = new WeakHashMap<>();
    private final Map<String, CookieCallback> cookieCallbacks = new HashMap<>();
    private WebExtension.Port backgroundPort;
    private PluginHandler pluginHandler;
    private JSONArray lastPlugins;
    private final Map<String, String> lastCss = new HashMap<>();
    private JSONObject lastAdBlock;
    private int rid;
    private int pluginSeq;
    private int pluginSettledSeq;
    private final List<Runnable> pluginWaiters = new ArrayList<>();

    PageAgent(BrowserEngine engine) {
        this.engine = engine;
    }

    public void setPluginHandler(PluginHandler handler) {
        pluginHandler = handler;
    }

    void register(GeckoSession session, BrowserView view) {
        views.put(session, new WeakReference<>(view));
        final WebExtension core = engine.getCore();
        if (core == null) {
            return;
        }
        session.getWebExtensionController().setMessageDelegate(core, pageDelegate, APP_PAGE);
        session.getWebExtensionController().setMessageDelegate(core, pluginDelegate, APP_PLUGIN);
    }

    void unregister(GeckoSession session) {
        views.remove(session);
        WebExtension core = engine.getCore();
        if (core != null) {
            try {
                session.getWebExtensionController().setMessageDelegate(core, null, APP_PAGE);
                session.getWebExtensionController().setMessageDelegate(core, null, APP_PLUGIN);
            } catch (Throwable ignored) {
            }
        }
    }

    private BrowserView viewOf(GeckoSession session) {
        if (session == null) {
            return null;
        }
        WeakReference<BrowserView> ref = views.get(session);
        return ref == null ? null : ref.get();
    }

    private final WebExtension.MessageDelegate pageDelegate = new WebExtension.MessageDelegate() {
        @Override
        public void onConnect(final WebExtension.Port port) {
            final BrowserView view = viewOf(port.sender.session);
            if (view == null) {
                return;
            }
            final boolean top = port.sender.isTopLevel();
            view.onAgentConnected(port, top);
            port.setDelegate(new WebExtension.PortDelegate() {
                @Override
                public void onPortMessage(Object message, WebExtension.Port p) {
                    if (message instanceof JSONObject) {
                        view.onAgentMessage(p, top, (JSONObject) message);
                    }
                }

                @Override
                public void onDisconnect(WebExtension.Port p) {
                    view.onAgentDisconnected(p, top);
                }
            });
        }
    };

    private final WebExtension.MessageDelegate pluginDelegate = new WebExtension.MessageDelegate() {
        @Override
        public void onConnect(final WebExtension.Port port) {
            final BrowserView view = viewOf(port.sender.session);
            if (view == null) {
                return;
            }
            port.setDelegate(new WebExtension.PortDelegate() {
                @Override
                public void onPortMessage(Object message, WebExtension.Port p) {
                    PluginHandler handler = pluginHandler;
                    if (handler != null && message instanceof JSONObject) {
                        handler.onPluginMessage(view, p, (JSONObject) message);
                    }
                }
            });
        }
    };

    WebExtension.MessageDelegate backgroundDelegate() {
        return new WebExtension.MessageDelegate() {
            @Override
            public void onConnect(WebExtension.Port port) {
                backgroundPort = port;
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onPortMessage(Object message, WebExtension.Port p) {
                        if (message instanceof JSONObject) {
                            onBackgroundMessage((JSONObject) message);
                        }
                    }

                    @Override
                    public void onDisconnect(WebExtension.Port p) {
                        if (backgroundPort == p) {
                            backgroundPort = null;
                        }
                    }
                });
                replayBackground();
            }
        };
    }

    WebExtension.TabDelegate tabDelegate() {
        return new WebExtension.TabDelegate() {
            @Override
            public GeckoResult<GeckoSession> onNewTab(WebExtension source, WebExtension.CreateTabDetails details) {
                return GeckoResult.fromValue(null);
            }
        };
    }

    private void replayBackground() {
        if (lastPlugins != null) {
            postBackground(json("t", "plugins", "list", lastPlugins, "seq", pluginSeq));
        }
        for (Map.Entry<String, String> e : lastCss.entrySet()) {
            postBackground(json("t", "css", "key", e.getKey(), "css", e.getValue()));
        }
        if (lastAdBlock != null) {
            postBackground(lastAdBlock);
        }
        engine.mainHandler().post(new Runnable() {
            @Override
            public void run() {
                BrowserEngine.get();
            }
        });
    }

    private void onBackgroundMessage(JSONObject m) {
        String t = m.optString("t");
        if ("plugins.done".equals(t)) {
            final int seq = m.optInt("seq", pluginSeq);
            engine.mainHandler().post(new Runnable() {
                @Override
                public void run() {
                    onPluginsSettled(seq);
                }
            });
            return;
        }
        if ("adblock.count".equals(t)) {
            AdBlockEngine.onBlockedCount(m.optInt("n"));
            return;
        }
        if ("cookies.res".equals(t)) {
            CookieCallback cb = cookieCallbacks.remove(m.optString("rid"));
            if (cb != null) {
                cb.onResult(m.optBoolean("ok"), m.optJSONArray("list"), m.optInt("count"));
            }
        }
    }

    boolean postBackground(JSONObject message) {
        WebExtension.Port p = backgroundPort;
        if (p == null) {
            return false;
        }
        try {
            p.postMessage(message);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void pushPlugins(JSONArray list) {
        lastPlugins = list;
        pluginSeq++;
        postBackground(json("t", "plugins", "list", list, "seq", pluginSeq));
    }

    public void afterPluginsApplied(final Runnable task, long timeoutMs) {
        if (pluginSettledSeq >= pluginSeq || backgroundPort == null) {
            task.run();
            return;
        }
        final Runnable once = new Runnable() {
            private boolean done;

            @Override
            public void run() {
                if (done) {
                    return;
                }
                done = true;
                pluginWaiters.remove(this);
                task.run();
            }
        };
        pluginWaiters.add(once);
        engine.mainHandler().postDelayed(once, timeoutMs);
    }

    private void onPluginsSettled(int seq) {
        if (seq > pluginSettledSeq) {
            pluginSettledSeq = seq;
        }
        if (pluginSettledSeq < pluginSeq) {
            return;
        }
        for (Runnable r : new ArrayList<>(pluginWaiters)) {
            engine.mainHandler().removeCallbacks(r);
            r.run();
        }
    }

    public void pushAdBlock(boolean on, JSONObject rules, String script) {
        JSONObject msg = on ? json("t", "adblock", "on", true, "rules", rules, "script", script) : json("t", "adblock", "on", false);
        lastAdBlock = msg;
        postBackground(msg);
    }

    public void setGlobalCss(String key, String css) {
        if (css == null || css.isEmpty()) {
            lastCss.remove(key);
            postBackground(json("t", "cssoff", "key", key));
        } else {
            lastCss.put(key, css);
            postBackground(json("t", "css", "key", key, "css", css));
        }
    }

    public void cookiesGet(JSONObject filter, CookieCallback callback) {
        String id = "c" + (++rid);
        cookieCallbacks.put(id, callback);
        if (!postBackground(json("t", "cookies.get", "rid", id, "filter", filter == null ? new JSONObject() : filter))) {
            cookieCallbacks.remove(id);
            callback.onResult(false, new JSONArray(), 0);
        }
    }

    public void cookiesSet(JSONArray list, CookieCallback callback) {
        String id = "c" + (++rid);
        cookieCallbacks.put(id, callback);
        if (!postBackground(json("t", "cookies.set", "rid", id, "list", list))) {
            cookieCallbacks.remove(id);
            callback.onResult(false, new JSONArray(), 0);
        }
    }

    public void cookiesClear(CookieCallback callback) {
        String id = "c" + (++rid);
        cookieCallbacks.put(id, callback);
        if (!postBackground(json("t", "cookies.clear", "rid", id))) {
            cookieCallbacks.remove(id);
            callback.onResult(false, new JSONArray(), 0);
        }
    }

    public static JSONObject json(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                o.put(String.valueOf(kv[i]), kv[i + 1]);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public static List<String> toList(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                out.add(a.optString(i));
            }
        }
        return out;
    }
}
