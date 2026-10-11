package com.coara.browser2.gecko;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;

import com.coara.browser2.util.UiThread;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BrowserView extends GeckoView {
    public static final int PAGE_MODE_NORMAL = 0;
    public static final int PAGE_MODE_GAME = 1;
    public static final int PAGE_MODE_TYPING = 2;

    private static final int CHAR_MODE_NORMAL = 0;
    private static final int CHAR_MODE_NUMBER = 1;
    private static final int CHAR_MODE_ENGLISH = 2;
    private static final String STATE_KEY = "gecko_state";
    private static final String STATE_URL_KEY = "gecko_state_url";
    private static final int MAX_PENDING_EVALS = 32;
    private static final long EVAL_TIMEOUT_MS = 10000L;

    public interface BridgeListener {
        void onBridgeCall(BrowserView view, String function, JSONArray args);
    }

    public interface FindCallback {
        void onFindResult(int activeIndex, int total);
    }

    private static final class PendingEval {
        final String code;
        final ValueCallback<String> callback;

        PendingEval(String code, ValueCallback<String> callback) {
            this.code = code;
            this.callback = callback;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean privateMode;
    private final BrowserSettings settings = new BrowserSettings(this);
    private final Map<String, String> cssState = new LinkedHashMap<>();
    private final Map<String, ValueCallback<String>> evalCallbacks = new HashMap<>();
    private final List<PendingEval> pendingEvals = new ArrayList<>();
    private final List<WebExtension.Port> framePorts = new ArrayList<>();
    private GeckoSession session;
    private BrowserClient client;
    private BridgeListener bridgeListener;
    private WebExtension.Port topPort;
    private String url = "";
    private String dataLogicalUrl;
    private String title = "";
    private int progress = 100;
    private boolean loading;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean secure;
    private boolean paused;
    private boolean destroyed;
    private boolean fullScreen;
    private int pageScrollX;
    private int pageScrollY;
    private int clearColor = 0xFFFFFFFF;
    private int evalSeq;
    private GeckoSession.SessionState sessionState;
    private String sessionStateUrl;
    private NavHistory history = NavHistory.empty();
        private String pendingUrl;
    private Map<String, String> pendingHeaders;
    private Object extraTag;
    private int viewBackend = BACKEND_SURFACE_VIEW;

    private volatile int inputBehaviorMode = PAGE_MODE_NORMAL;
    private volatile int keyboardMode = CHAR_MODE_NORMAL;
    private volatile boolean alwaysVisibleKeyboard;
    private volatile String composingText = "";
    private boolean touchpadMouseInitialized;
    private boolean touchpadPointerLocked;
    private float touchpadMouseX;
    private float touchpadMouseY;
    private int touchpadMouseButtons;
    private long touchpadMouseDownTime;

    public BrowserView(Context context) {
        this(context, false);
    }

    public BrowserView(Context context, boolean privateMode) {
        super(context);
        this.privateMode = privateMode;
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public boolean isPrivateMode() {
        return privateMode;
    }

    public BrowserSettings getSettings() {
        return settings;
    }

    public void setClient(BrowserClient client) {
        this.client = client;
    }

    public void setBridgeListener(BridgeListener listener) {
        this.bridgeListener = listener;
    }

    public GeckoSession getGeckoSession() {
        return session;
    }

    public boolean hasSession() {
        return session != null;
    }

    private GeckoSessionSettings buildSessionSettings() {
        return new GeckoSessionSettings.Builder()
                .usePrivateMode(privateMode)
                .allowJavascript(settings.getJavaScriptEnabled())
                .useTrackingProtection(true)
                .userAgentMode(settings.isDesktopMode() ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(settings.isDesktopMode() ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .userAgentOverride(settings.getUserAgentOverride())
                .suspendMediaWhenInactive(false)
                .build();
    }

    void applySessionSettings() {
        if (session == null) {
            return;
        }
        GeckoSessionSettings s = session.getSettings();
        s.setAllowJavascript(settings.getJavaScriptEnabled());
        s.setUserAgentMode(settings.isDesktopMode() ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        s.setViewportMode(settings.isDesktopMode() ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        s.setUserAgentOverride(settings.getUserAgentOverride());
    }

    private void installDelegates(GeckoSession s) {
        s.setNavigationDelegate(navigationDelegate);
        s.setProgressDelegate(progressDelegate);
        s.setContentDelegate(contentDelegate);
        s.setHistoryDelegate(historyDelegate);
        s.setScrollDelegate(scrollDelegate);
        s.setPromptDelegate(new TabPrompts(this));
        s.setPermissionDelegate(new TabPermissions(this));
    }

    private void ensureSession() {
        if (session != null || destroyed) {
            return;
        }
        session = new GeckoSession(buildSessionSettings());
        installDelegates(session);
        session.open(BrowserEngine.get().getRuntime());
        attachSession();
    }

    private void attachSession() {
        if (session == null) {
            return;
        }
        setSession(session);
        session.getCompositorController().setClearColor(clearColor);
        session.setActive(!paused);
        final GeckoSession target = session;
        BrowserEngine.get().whenAgentSettled(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && session == target) {
                    BrowserEngine.get().getAgent().register(target, BrowserView.this);
                }
            }
        });
        if (pendingUrl != null) {
            String u = pendingUrl;
            Map<String, String> h = pendingHeaders;
            pendingUrl = null;
            pendingHeaders = null;
            loadUrl(u, h);
        }
    }

    GeckoSession prepareAsChild() {
        if (session == null) {
            session = new GeckoSession(buildSessionSettings());
            installDelegates(session);
        }
        final GeckoSession target = session;
        scheduleChildAttach(target, 0);
        return session;
    }

    private void scheduleChildAttach(final GeckoSession target, final int attempt) {
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed || session != target) {
                    return;
                }
                if (target.isOpen()) {
                    attachSession();
                } else if (attempt < 40) {
                    scheduleChildAttach(target, attempt + 1);
                }
            }
        }, 25L);
    }

    private final java.util.Set<String> certProceed = new java.util.HashSet<>();

    private static String hostOf(String u) {
        try {
            String h = android.net.Uri.parse(u).getHost();
            return h == null ? null : h.toLowerCase(java.util.Locale.US);
        } catch (Throwable t) {
            return null;
        }
    }

    public void proceedAfterCertificateError(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        String host = hostOf(target);
        if (host != null) {
            certProceed.add(host);
        }
        loadUrl(target);
    }

    public void loadUrlBypassClassifier(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        ensureSession();
        if (session == null) {
            return;
        }
        dataLogicalUrl = null;
        url = target;
        session.load(new GeckoSession.Loader().uri(target).flags(GeckoSession.LOAD_FLAGS_BYPASS_CLASSIFIER));
    }

    private void handleErrorAction(String actionUri) {
        if (destroyed) {
            return;
        }
        String action = ErrorPages.actionOf(actionUri);
        String target = ErrorPages.paramOf(actionUri, "u");
        if (target == null || target.isEmpty()) {
            target = getUrl();
        }
        if ("reload".equals(action)) {
            loadUrl(target);
        } else if ("proceed".equals(action)) {
            if (ErrorPages.KIND_SAFE.equals(ErrorPages.paramOf(actionUri, "k"))) {
                loadUrlBypassClassifier(target);
            } else {
                proceedAfterCertificateError(target);
            }
        } else {
            BrowserClient c = client;
            if (c != null) {
                c.onErrorAction(this, action, target);
            }
        }
    }

    public void loadUrlReplacingHistory(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        ensureSession();
        if (session == null) {
            return;
        }
        if (LocalPages.isLogical(target) || target.regionMatches(true, 0, "javascript:", 0, 11)) {
            loadUrl(target);
            return;
        }
        dataLogicalUrl = null;
        url = target;
        session.load(new GeckoSession.Loader().uri(target).flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY));
    }

    public static String savedStateUrl(Bundle in) {
        return in == null ? null : in.getString(STATE_URL_KEY);
    }

    public void loadUrl(String target) {
        loadUrl(target, null);
    }

    public void loadUrl(final String target, final Map<String, String> headers) {
        if (target == null || destroyed) {
            return;
        }
        if (target.regionMatches(true, 0, "javascript:", 0, 11)) {
            evaluateJavascript(target.substring(11), null);
            return;
        }
        ensureSession();
        if (session == null) {
            return;
        }
        if (LocalPages.isLogical(target)) {
            loadLocalPage(target);
            return;
        }
        dataLogicalUrl = null;
        url = target;
        GeckoSession.Loader loader = new GeckoSession.Loader().uri(target);
        if (headers != null && !headers.isEmpty()) {
            loader.additionalHeaders(headers);
        }
        if (settings.getCacheMode() == BrowserSettings.CACHE_BYPASS) {
            loader.flags(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
        }
        session.load(loader);
    }

    private void loadLocalPage(final String logical) {
        String actual = LocalPages.toActual(logical);
        if (actual != null) {
            dataLogicalUrl = null;
            url = logical;
            session.load(new GeckoSession.Loader().uri(actual));
            return;
        }
        final BrowserEngine engine = BrowserEngine.get();
        if (!engine.isAgentReady() && engine.getRuntime() != null) {
            engine.whenAgentSettled(new Runnable() {
                @Override
                public void run() {
                    if (destroyed || session == null) {
                        return;
                    }
                    String late = LocalPages.toActual(logical);
                    if (late != null) {
                        dataLogicalUrl = null;
                        url = logical;
                        session.load(new GeckoSession.Loader().uri(late));
                    } else {
                        loadLocalFallback(logical);
                    }
                }
            });
            return;
        }
        loadLocalFallback(logical);
    }

    private void loadLocalFallback(String logical) {
        String name = LocalPages.pageName(logical);
        String html = name == null ? null : BrowserEngine.get().readAsset(BrowserEngine.PAGES_ASSET_DIR + name);
        if (html == null) {
            html = "<!doctype html><meta charset=utf-8><title></title>";
        }
        dataLogicalUrl = logical;
        url = logical;
        session.load(new GeckoSession.Loader()
                .data(html, "text/html")
                .flags(GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI));
    }

    public void loadData(String html, String mimeType, String baseUrl) {
        ensureSession();
        if (session == null || html == null) {
            return;
        }
        dataLogicalUrl = baseUrl;
        if (baseUrl != null) {
            url = baseUrl;
        }
        session.load(new GeckoSession.Loader()
                .data(html, mimeType == null ? "text/html" : mimeType)
                .flags(GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI));
    }

    public void reload() {
        if (session == null) {
            return;
        }
        session.reload(settings.getCacheMode() == BrowserSettings.CACHE_BYPASS
                ? GeckoSession.LOAD_FLAGS_BYPASS_CACHE : GeckoSession.LOAD_FLAGS_NONE);
    }

    public void reloadForSettings() {
        if (session == null) {
            return;
        }
        String actual = url;
        String failed = actual != null ? ErrorPages.failedUrlFor(actual) : null;
        if (failed != null) {
            loadUrl(failed);
            return;
        }
        session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
    }

    public void stopLoading() {
        if (session != null) {
            session.stop();
        }
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    public boolean canGoForward() {
        return canGoForward;
    }

    public void goBack() {
        if (session != null) {
            session.goBack();
        }
    }

    public void goForward() {
        if (session != null) {
            session.goForward();
        }
    }

    public void clearHistory() {
        if (session != null) {
            session.purgeHistory();
        }
        history = NavHistory.empty();
    }

    public String getUrl() {
        String actual = url;
        if (actual != null && actual.startsWith("data:") && dataLogicalUrl != null) {
            return dataLogicalUrl;
        }
        return LocalPages.toLogical(actual);
    }

    public String getTitle() {
        return title;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isSecure() {
        return secure;
    }

    public int getPageScrollY() {
        return pageScrollY;
    }

    public NavHistory copyBackForwardList() {
        return history;
    }

    public String getUserAgentString() {
        return settings.getUserAgentString();
    }

    public void saveState(Bundle out) {
        if (out == null) {
            return;
        }
        if (session != null) {
            try {
                session.flushSessionState();
            } catch (Throwable ignored) {
            }
        }
        if (sessionState != null) {
            out.putString(STATE_KEY, sessionState.toString());
            if (sessionStateUrl != null) {
                out.putString(STATE_URL_KEY, sessionStateUrl);
            }
        }
    }

    public interface StateSource {
        Bundle toBundle();
    }

    public StateSource captureStateSource() {
        if (session != null) {
            try {
                session.flushSessionState();
            } catch (Throwable ignored) {
            }
        }
        final GeckoSession.SessionState captured = sessionState;
        final String capturedUrl = sessionStateUrl;
        if (captured == null) {
            return null;
        }
        return new StateSource() {
            @Override
            public Bundle toBundle() {
                Bundle out = new Bundle();
                out.putString(STATE_KEY, captured.toString());
                if (capturedUrl != null) {
                    out.putString(STATE_URL_KEY, capturedUrl);
                }
                return out;
            }
        };
    }

    public boolean restoreState(Bundle in) {
        if (in == null) {
            return false;
        }
        String raw = in.getString(STATE_KEY);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        GeckoSession.SessionState parsed;
        try {
            parsed = GeckoSession.SessionState.fromString(raw);
        } catch (Throwable t) {
            return false;
        }
        if (parsed == null) {
            return false;
        }
        ensureSession();
        if (session == null) {
            return false;
        }
        sessionState = parsed;
        sessionStateUrl = in.getString(STATE_URL_KEY);
        session.restoreState(parsed);
        return true;
    }

    public void onPause() {
        paused = true;
        if (session != null) {
            session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
            session.setActive(false);
        }
    }

    public void onResume() {
        paused = false;
        if (session != null) {
            session.setActive(true);
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        }
    }

    public void pauseTimers() {
    }

    public void resumeTimers() {
    }

    public void setTransparentSurface(boolean on) {
        int backend = on ? BACKEND_TEXTURE_VIEW : BACKEND_SURFACE_VIEW;
        if (backend == viewBackend) {
            return;
        }
        viewBackend = backend;
        try {
            GeckoSession attached = getSession();
            if (attached != null) {
                releaseSession();
                setViewBackend(backend);
                setSession(attached);
                attached.getCompositorController().setClearColor(clearColor);
                attached.setActive(!paused);
            } else {
                setViewBackend(backend);
            }
        } catch (Throwable ignored) {
        }
    }

    public void setBackgroundColorValue(int color) {
        clearColor = color;
        settings.setBackgroundColorValue(color);
        if (session != null) {
            session.getCompositorController().setClearColor(color);
        }
    }

    public void clearCache(boolean includeDiskFiles) {
        BrowserEngine.get().clearCaches();
    }

    public void clearFormData() {
    }

    public void exitFullScreen() {
        if (session != null) {
            session.exitFullScreen();
        }
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (direction < 0) {
            return pageScrollY > 0;
        }
        return true;
    }

    public void scrollPageToTop() {
        if (session != null) {
            session.getPanZoomController().scrollToTop();
        }
    }

    public void capture(final ValueCallback<Bitmap> callback) {
        if (session == null) {
            callback.onReceiveValue(null);
            return;
        }
        try {
            capturePixels().then(new GeckoResult.OnValueListener<Bitmap, Void>() {
                @Override
                public GeckoResult<Void> onValue(Bitmap bitmap) {
                    callback.onReceiveValue(bitmap);
                    return null;
                }
            }, new GeckoResult.OnExceptionListener<Void>() {
                @Override
                public GeckoResult<Void> onException(Throwable error) {
                    callback.onReceiveValue(null);
                    return null;
                }
            });
        } catch (Throwable t) {
            callback.onReceiveValue(null);
        }
    }

    public void findAllAsync(String query, final FindCallback callback) {
        if (session == null || query == null || query.isEmpty()) {
            clearMatches();
            if (callback != null) {
                callback.onFindResult(0, 0);
            }
            return;
        }
        session.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
        session.getFinder().find(query, 0).then(new GeckoResult.OnValueListener<GeckoSession.FinderResult, Void>() {
            @Override
            public GeckoResult<Void> onValue(GeckoSession.FinderResult r) {
                if (callback != null && r != null) {
                    callback.onFindResult(r.found ? Math.max(0, r.current - 1) : 0, r.found ? r.total : 0);
                }
                return null;
            }
        });
    }

    public void findNext(boolean forward, final FindCallback callback) {
        if (session == null) {
            return;
        }
        session.getFinder().find(null, forward ? GeckoSession.FINDER_FIND_FORWARD : GeckoSession.FINDER_FIND_BACKWARDS)
                .then(new GeckoResult.OnValueListener<GeckoSession.FinderResult, Void>() {
                    @Override
                    public GeckoResult<Void> onValue(GeckoSession.FinderResult r) {
                        if (callback != null && r != null) {
                            callback.onFindResult(r.found ? Math.max(0, r.current - 1) : 0, r.found ? r.total : 0);
                        }
                        return null;
                    }
                });
    }

    public void clearMatches() {
        if (session != null) {
            session.getFinder().clear();
        }
    }

    public void evaluateJavascript(String code, ValueCallback<String> callback) {
        if (code != null && code.regionMatches(true, 0, "javascript:", 0, 11)) {
            code = code.substring(11);
        }
        if (code == null) {
            if (callback != null) {
                callback.onReceiveValue("null");
            }
            return;
        }
        if (topPort == null) {
            if (pendingEvals.size() < MAX_PENDING_EVALS) {
                pendingEvals.add(new PendingEval(code, callback));
            } else if (callback != null) {
                callback.onReceiveValue("null");
            }
            return;
        }
        dispatchEval(code, callback);
    }

    private void dispatchEval(String code, final ValueCallback<String> callback) {
        String id = null;
        if (callback != null) {
            id = "e" + (++evalSeq);
            evalCallbacks.put(id, callback);
            final String key = id;
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ValueCallback<String> cb = evalCallbacks.remove(key);
                    if (cb != null) {
                        cb.onReceiveValue("null");
                    }
                }
            }, EVAL_TIMEOUT_MS);
        }
        JSONObject m = PageAgent.json("t", "eval", "code", code);
        if (id != null) {
            try {
                m.put("id", id);
            } catch (Exception ignored) {
            }
        }
        agentSend(m, true);
    }

    public void setCss(String key, String css) {
        if (css == null || css.isEmpty()) {
            removeCss(key);
            return;
        }
        if (css.equals(cssState.get(key))) {
            return;
        }
        cssState.put(key, css);
        agentSend(PageAgent.json("t", "css", "key", key, "css", css), false);
    }

    public void removeCss(String key) {
        if (cssState.remove(key) != null) {
            agentSend(PageAgent.json("t", "cssoff", "key", key), false);
        }
    }

    void agentSend(JSONObject message, boolean topOnly) {
        if (topPort != null) {
            try {
                topPort.postMessage(message);
            } catch (Throwable ignored) {
            }
        }
        if (!topOnly) {
            for (WebExtension.Port p : new ArrayList<>(framePorts)) {
                try {
                    p.postMessage(message);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    void onAgentConnected(WebExtension.Port port, boolean top) {
        if (top) {
            topPort = port;
        } else {
            framePorts.add(port);
        }
        for (Map.Entry<String, String> e : cssState.entrySet()) {
            try {
                port.postMessage(PageAgent.json("t", "css", "key", e.getKey(), "css", e.getValue()));
            } catch (Throwable ignored) {
            }
        }
        if (top && !pendingEvals.isEmpty()) {
            List<PendingEval> copy = new ArrayList<>(pendingEvals);
            pendingEvals.clear();
            for (PendingEval p : copy) {
                dispatchEval(p.code, p.callback);
            }
        }
    }

    void onAgentDisconnected(WebExtension.Port port, boolean top) {
        if (top) {
            if (topPort == port) {
                topPort = null;
            }
        } else {
            framePorts.remove(port);
        }
    }

    void onAgentMessage(WebExtension.Port port, boolean top, JSONObject m) {
        String t = m.optString("t");
        if ("res".equals(t)) {
            ValueCallback<String> cb = evalCallbacks.remove(m.optString("id"));
            if (cb != null) {
                cb.onReceiveValue(m.optString("v", "null"));
            }
        } else if ("call".equals(t)) {
            BridgeListener l = bridgeListener;
            if (l != null) {
                l.onBridgeCall(this, m.optString("fn"), m.optJSONArray("a"));
            }
        } else if ("icon".equals(t) && top) {
            JSONArray arr = m.optJSONArray("hrefs");
            if (arr == null) {
                String single = m.optString("href", "");
                arr = new JSONArray();
                if (!single.isEmpty()) {
                    arr.put(single);
                }
            }
            fetchIcon(arr);
        }
    }

    private static final android.util.LruCache<String, Bitmap> ICON_CACHE = new android.util.LruCache<String, Bitmap>(24);

    private void fetchIcon(final JSONArray hrefs) {
        if (hrefs == null || hrefs.length() == 0) {
            return;
        }
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < hrefs.length(); i++) {
            String h = hrefs.optString(i, "");
            if (!h.isEmpty()) {
                list.add(h);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        for (String h : list) {
            Bitmap cached = ICON_CACHE.get(h);
            if (cached != null && !cached.isRecycled()) {
                deliverIcon(cached);
                return;
            }
        }
        final String ua = settings.getUserAgentString();
        UiThread.io().execute(new Runnable() {
            @Override
            public void run() {
                for (String h : list) {
                    Bitmap bmp = loadIcon(h, ua);
                    if (bmp != null) {
                        ICON_CACHE.put(h, bmp);
                        deliverIcon(bmp);
                        return;
                    }
                }
            }
        });
    }

    private boolean iconDelivered;

    private void scheduleIconFallback() {
        final String page = getUrl();
        if (page == null || !(page.startsWith("http://") || page.startsWith("https://"))) {
            return;
        }
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed || iconDelivered || !page.equals(getUrl())) {
                    return;
                }
                try {
                    android.net.Uri u = android.net.Uri.parse(page);
                    if (u.getHost() == null) {
                        return;
                    }
                    JSONArray a = new JSONArray();
                    a.put(u.getScheme() + "://" + u.getAuthority() + "/favicon.ico");
                    fetchIcon(a);
                } catch (Throwable ignored) {
                }
            }
        }, 1800L);
    }

    private void deliverIcon(final Bitmap bmp) {
        main.post(new Runnable() {
            @Override
            public void run() {
                iconDelivered = true;
                BrowserClient c = client;
                if (!destroyed && c != null) {
                    c.onReceivedIcon(BrowserView.this, bmp);
                }
            }
        });
    }

    private static Bitmap loadIcon(String href, String ua) {
        try {
            if (href.startsWith("data:")) {
                int comma = href.indexOf(',');
                if (comma < 0) {
                    return null;
                }
                String meta = href.substring(0, comma);
                String body = href.substring(comma + 1);
                byte[] bytes = meta.contains(";base64")
                        ? android.util.Base64.decode(body, android.util.Base64.DEFAULT)
                        : java.net.URLDecoder.decode(body, "UTF-8").getBytes("UTF-8");
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(href).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", ua);
                c.setRequestProperty("Accept", "image/*,*/*;q=0.5");
                if (c.getResponseCode() != 200) {
                    return null;
                }
                try (InputStream in = new java.io.BufferedInputStream(c.getInputStream(), 8192)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    int total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 512 * 1024) {
                            return null;
                        }
                        out.write(buf, 0, n);
                    }
                    byte[] data = out.toByteArray();
                    return BitmapFactory.decodeByteArray(data, 0, data.length);
                }
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private void failPendingEvals() {
        pendingEvals.clear();
        List<ValueCallback<String>> cbs = new ArrayList<>(evalCallbacks.values());
        evalCallbacks.clear();
        for (ValueCallback<String> cb : cbs) {
            cb.onReceiveValue("null");
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        failPendingEvals();
        GeckoSession s = session;
        session = null;
        if (s != null) {
            BrowserEngine.get().getAgent().unregister(s);
            try {
                releaseSession();
            } catch (Throwable ignored) {
            }
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
        topPort = null;
        framePorts.clear();
        client = null;
        bridgeListener = null;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    private final GeckoSession.NavigationDelegate navigationDelegate = new GeckoSession.NavigationDelegate() {
        @Override
        public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
            String uri = request.uri;
            if (ErrorPages.isAction(uri)) {
                final String actionUri = uri;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        handleErrorAction(actionUri);
                    }
                });
                return GeckoResult.fromValue(AllowOrDeny.DENY);
            }
            if (LocalPages.isLogical(uri)) {
                final String target = uri;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        loadUrl(target);
                    }
                });
                return GeckoResult.fromValue(AllowOrDeny.DENY);
            }
            BrowserClient c = client;
            if (c != null && !uri.startsWith("data:") && !uri.startsWith("about:")) {
                if (c.shouldOverrideUrlLoading(BrowserView.this, LocalPages.toLogical(uri), true,
                        request.hasUserGesture, request.isRedirect)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.ALLOW);
        }

        @Override
        public void onLocationChange(GeckoSession s, String uri, java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms, Boolean hasUserGesture) {
            if (uri == null) {
                return;
            }
            url = uri;
            if (!uri.startsWith("data:")) {
                dataLogicalUrl = LocalPages.isLogical(dataLogicalUrl) && uri.startsWith("data:") ? dataLogicalUrl : null;
            }
            BrowserClient c = client;
            if (c != null) {
                c.onUrlChanged(BrowserView.this, getUrl(), !loading);
            }
        }

        @Override
        public void onCanGoBack(GeckoSession s, boolean value) {
            canGoBack = value;
        }

        @Override
        public void onCanGoForward(GeckoSession s, boolean value) {
            canGoForward = value;
        }

        @Override
        public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
            BrowserClient c = client;
            if (c == null) {
                return GeckoResult.fromValue(null);
            }
            BrowserView child = c.onCreateWindow(BrowserView.this, LocalPages.toLogical(uri));
            if (child == null) {
                return GeckoResult.fromValue(null);
            }
            return GeckoResult.fromValue(child.prepareAsChild());
        }

        @Override
        public GeckoResult<String> onLoadError(GeckoSession s, String uri, WebRequestError error) {
            String failed = LocalPages.toLogical(uri);
            if (error == null) {
                return GeckoResult.fromValue(ErrorPages.build(failed, null, false));
            }
            String detail = null;
            if (error.category == WebRequestError.ERROR_CATEGORY_SECURITY) {
                String host = null;
                try {
                    host = android.net.Uri.parse(failed).getHost();
                } catch (Throwable ignored) {
                }
                detail = ErrorPages.describeCertificate(error.certificate, host);
            }
            loading = false;
            BrowserClient c = client;
            if (c != null) {
                c.onLoadFailed(BrowserView.this, new BrowserClient.LoadFailure(failed, error.category, error.code, detail));
            }
            String failedHost = hostOf(failed);
            boolean auto = ErrorPages.isSecurityOverridable(error) && failedHost != null && certProceed.remove(failedHost);
            return GeckoResult.fromValue(ErrorPages.build(failed, error, auto));
        }
    };

    private final GeckoSession.ProgressDelegate progressDelegate = new GeckoSession.ProgressDelegate() {
        @Override
        public void onPageStart(GeckoSession s, String uri) {
            loading = true;
            progress = 0;
            title = "";
            if (uri != null) {
                url = uri;
            }
            iconDelivered = false;
            failPendingEvals();
            topPort = null;
            framePorts.clear();
            BrowserClient c = client;
            if (c != null) {
                c.onPageStarted(BrowserView.this, getUrl());
            }
        }

        @Override
        public void onPageStop(GeckoSession s, boolean success) {
            loading = false;
            progress = 100;
            BrowserClient c = client;
            if (c != null) {
                c.onPageFinished(BrowserView.this, getUrl());
            }
            if (success) {
                scheduleIconFallback();
            }
        }

        @Override
        public void onProgressChange(GeckoSession s, int value) {
            progress = value;
            BrowserClient c = client;
            if (c != null) {
                c.onProgressChanged(BrowserView.this, value);
            }
        }

        @Override
        public void onSecurityChange(GeckoSession s, SecurityInformation info) {
            secure = info != null && info.isSecure;
            BrowserClient c = client;
            if (c != null) {
                c.onSecurityChanged(BrowserView.this, secure);
            }
        }

        @Override
        public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState state) {
            sessionState = state;
            String now = getUrl();
            sessionStateUrl = now == null || now.isEmpty() ? null : now;
            BrowserClient c = client;
            if (c != null) {
                c.onSessionStateChanged(BrowserView.this);
            }
        }
    };

    private final GeckoSession.ContentDelegate contentDelegate = new GeckoSession.ContentDelegate() {
        @Override
        public void onTitleChange(GeckoSession s, String value) {
            title = value == null ? "" : value;
            BrowserClient c = client;
            if (c != null) {
                c.onReceivedTitle(BrowserView.this, title);
            }
        }

        @Override
        public void onFullScreen(GeckoSession s, boolean value) {
            fullScreen = value;
            BrowserClient c = client;
            if (c != null) {
                c.onFullScreenChanged(BrowserView.this, value);
            }
        }

        @Override
        public void onFirstContentfulPaint(GeckoSession s) {
            BrowserClient c = client;
            if (c != null) {
                c.onPageCommitVisible(BrowserView.this, getUrl());
            }
        }

        @Override
        public void onContextMenu(GeckoSession s, int x, int y, ContextElement element) {
            BrowserClient c = client;
            if (c == null || element == null) {
                return;
            }
            c.onContextMenu(BrowserView.this, new BrowserClient.ContextHit(element.type, element.linkUri,
                    element.srcUri, element.linkText, element.title));
        }

        @Override
        public void onExternalResponse(GeckoSession s, WebResponse response) {
            BrowserClient c = client;
            if (c == null || response == null) {
                return;
            }
            Map<String, String> headers = new HashMap<>();
            if (response.headers != null) {
                for (Map.Entry<String, String> e : response.headers.entrySet()) {
                    headers.put(e.getKey().toLowerCase(Locale.US), e.getValue());
                }
            }
            String disposition = headers.get("content-disposition");
            String mime = headers.get("content-type");
            long length = -1L;
            try {
                String raw = headers.get("content-length");
                if (raw != null) {
                    length = Long.parseLong(raw.trim());
                }
            } catch (Exception ignored) {
            }
            c.onDownloadRequested(BrowserView.this, new DownloadRequest(response.uri, null, mime, length, disposition,
                    settings.getUserAgentString(), getUrl(), headers, response));
        }

        @Override
        public void onCloseRequest(GeckoSession s) {
            BrowserClient c = client;
            if (c != null) {
                c.onCloseWindow(BrowserView.this);
            }
        }

        @Override
        public void onCrash(GeckoSession s) {
            handleProcessGone(true);
        }

        @Override
        public void onKill(GeckoSession s) {
            handleProcessGone(false);
        }
    };

    private void handleProcessGone(boolean crashed) {
        BrowserClient c = client;
        if (c != null) {
            c.onRenderProcessGone(this, crashed);
        }
    }

    private final GeckoSession.HistoryDelegate historyDelegate = new GeckoSession.HistoryDelegate() {
        @Override
        public void onHistoryStateChange(GeckoSession s, HistoryList list) {
            List<NavHistory.Item> items = new ArrayList<>();
            for (HistoryItem item : list) {
                items.add(new NavHistory.Item(LocalPages.toLogical(item.getUri()), item.getTitle()));
            }
            history = new NavHistory(items, list.getCurrentIndex());
            BrowserClient c = client;
            if (c != null) {
                c.onHistoryChanged(BrowserView.this, history);
            }
        }
    };

    private final GeckoSession.ScrollDelegate scrollDelegate = new GeckoSession.ScrollDelegate() {
        @Override
        public void onScrollChanged(GeckoSession s, int x, int y) {
            pageScrollX = x;
            pageScrollY = y;
            BrowserClient c = client;
            if (c != null) {
                c.onScrollChanged(BrowserView.this, x, y);
            }
        }
    };

    public void setInputBehaviorMode(int mode) {
        inputBehaviorMode = mode >= PAGE_MODE_NORMAL && mode <= PAGE_MODE_TYPING ? mode : PAGE_MODE_NORMAL;
        composingText = "";
    }

    public int getInputBehaviorMode() {
        return inputBehaviorMode;
    }

    public void setKeyboardMode(int mode) {
        keyboardMode = mode >= CHAR_MODE_NORMAL && mode <= CHAR_MODE_ENGLISH ? mode : CHAR_MODE_NORMAL;
        composingText = "";
    }

    public void setAlwaysVisibleKeyboard(boolean enabled) {
        alwaysVisibleKeyboard = enabled;
        if (!enabled) {
            inputBehaviorMode = PAGE_MODE_NORMAL;
            keyboardMode = CHAR_MODE_NORMAL;
            composingText = "";
        }
    }

    public boolean isAlwaysVisibleKeyboard() {
        return alwaysVisibleKeyboard;
    }

    public void setGameKeyboardEnabled(boolean enabled) {
        setInputBehaviorMode(enabled ? PAGE_MODE_GAME : PAGE_MODE_NORMAL);
    }

    public boolean isGameKeyboardEnabled() {
        return inputBehaviorMode != PAGE_MODE_NORMAL;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return alwaysVisibleKeyboard || super.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection base = super.onCreateInputConnection(outAttrs);
        boolean bridgeEnabled = isBridgeEnabled();
        boolean filterEnabled = alwaysVisibleKeyboard && keyboardMode != CHAR_MODE_NORMAL;
        boolean baseWasMissing = base == null;
        if (!alwaysVisibleKeyboard) {
            return base;
        }
        if (baseWasMissing) {
            base = new BaseInputConnection(this, true);
        }
        if (!bridgeEnabled && !filterEnabled && !baseWasMissing) {
            return base;
        }
        configureEditorInfo(outAttrs);
        if (!bridgeEnabled && !filterEnabled) {
            return base;
        }
        return new GameInputConnection(this, base);
    }

    private void configureEditorInfo(EditorInfo outAttrs) {
        if (outAttrs == null) {
            return;
        }
        if (keyboardMode == CHAR_MODE_NUMBER) {
            outAttrs.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL;
        } else if (keyboardMode == CHAR_MODE_ENGLISH) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        } else {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        if (keyboardMode == CHAR_MODE_ENGLISH) {
            outAttrs.imeOptions |= EditorInfo.IME_FLAG_FORCE_ASCII;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        if (alwaysVisibleKeyboard && event != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!alwaysVisibleKeyboard || !hasFocus() || getWindowToken() == null || !isAttachedToWindow()) {
                        return;
                    }
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(BrowserView.this, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 120L);
        }
        return handled;
    }

    public void resetTouchpadMouse() {
        long now = SystemClock.uptimeMillis();
        if (touchpadMouseButtons != 0) {
            int oldButtons = touchpadMouseButtons;
            touchpadMouseButtons = 0;
            if ((oldButtons & MotionEvent.BUTTON_SECONDARY) != 0) {
                dispatchTouchpadMouseButtonEvent(2, false, now, oldButtons);
            }
            if ((oldButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
                dispatchTouchpadMouseButtonEvent(0, false, now, oldButtons & ~MotionEvent.BUTTON_SECONDARY);
            }
        }
        touchpadMouseButtons = 0;
        touchpadMouseInitialized = false;
        touchpadMouseDownTime = 0L;
    }

    public boolean setTouchpadPointerLocked(boolean locked) {
        if (touchpadPointerLocked == locked) {
            return false;
        }
        touchpadPointerLocked = locked;
        if (!locked && touchpadMouseInitialized) {
            touchpadMouseX = clampTouchpadCoordinate(touchpadMouseX, getWidth());
            touchpadMouseY = clampTouchpadCoordinate(touchpadMouseY, getHeight());
        }
        return true;
    }

    public void installTouchpadPointerLockTracking() {
        evaluateJavascript("(function(){var d=document,w=window;function n(){try{AndroidBridge.onPointerLockChanged(!!d.pointerLockElement);}catch(e){}}function i(doc){try{if(!doc||doc.__coaraTouchpadPointerLockInstalled)return;doc.__coaraTouchpadPointerLockInstalled=true;doc.addEventListener('pointerlockchange',n,true);doc.addEventListener('pointerlockerror',n,true);var fs=doc.querySelectorAll('iframe,frame');for(var j=0;j<fs.length;j++){try{i(fs[j].contentDocument);}catch(e){}}}catch(e){}}i(d);n();})();", null);
    }

    private boolean sendMouse(MotionEvent event) {
        if (session == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            session.getPanZoomController().onMouseEvent(event);
        } else {
            session.getPanZoomController().onMotionEvent(event);
        }
        return true;
    }

    public boolean dispatchTouchpadMouseMove(float dx, float dy) {
        if (!isFiniteTouchpadDelta(dx) || !isFiniteTouchpadDelta(dy) || (dx == 0f && dy == 0f) || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        if (!touchpadMouseInitialized) {
            touchpadMouseX = getWidth() * 0.5f;
            touchpadMouseY = getHeight() * 0.5f;
            touchpadMouseInitialized = true;
        }
        if (!touchpadPointerLocked) {
            touchpadMouseX = clampTouchpadCoordinate(touchpadMouseX + dx, getWidth());
            touchpadMouseY = clampTouchpadCoordinate(touchpadMouseY + dy, getHeight());
        } else {
            touchpadMouseX += dx;
            touchpadMouseY += dy;
        }
        long now = SystemClock.uptimeMillis();
        int action = touchpadMouseButtons == 0 ? MotionEvent.ACTION_HOVER_MOVE : MotionEvent.ACTION_MOVE;
        MotionEvent event = obtainTouchpadMouseEvent(now, now, action, touchpadMouseX, touchpadMouseY, touchpadMouseButtons);
        try {
            return sendMouse(event);
        } finally {
            event.recycle();
        }
    }

    public boolean dispatchTouchpadMouseScroll(float dx, float dy) {
        if (!isFiniteTouchpadDelta(dx) || !isFiniteTouchpadDelta(dy) || (dx == 0f && dy == 0f) || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        if (!touchpadMouseInitialized) {
            touchpadMouseX = getWidth() * 0.5f;
            touchpadMouseY = getHeight() * 0.5f;
            touchpadMouseInitialized = true;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent event = obtainTouchpadMouseScrollEvent(now, touchpadMouseX, touchpadMouseY, dx, dy);
        try {
            return sendMouse(event);
        } finally {
            event.recycle();
        }
    }

    public boolean dispatchTouchpadMouseButton(int button, boolean pressed) {
        long now = SystemClock.uptimeMillis();
        int mask = button == 2 ? MotionEvent.BUTTON_SECONDARY : MotionEvent.BUTTON_PRIMARY;
        if (!touchpadMouseInitialized) {
            touchpadMouseX = getWidth() * 0.5f;
            touchpadMouseY = getHeight() * 0.5f;
            touchpadMouseInitialized = true;
        }
        if (pressed) {
            if ((touchpadMouseButtons & mask) != 0) {
                return true;
            }
            int next = touchpadMouseButtons | mask;
            boolean result = dispatchTouchpadMouseButtonEvent(button, true, now, next);
            if (result) {
                touchpadMouseButtons = next;
                if (touchpadMouseDownTime == 0L) {
                    touchpadMouseDownTime = now;
                }
            }
            return result;
        }
        if ((touchpadMouseButtons & mask) == 0) {
            return true;
        }
        int next = touchpadMouseButtons & ~mask;
        boolean result = dispatchTouchpadMouseButtonEvent(button, false, now, next);
        if (result) {
            touchpadMouseButtons = next;
            if (next == 0) {
                touchpadMouseDownTime = 0L;
            }
        }
        return result;
    }

    private boolean dispatchTouchpadMouseButtonEvent(int button, boolean pressed, long eventTime, int buttonState) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        int changedButton = button == 2 ? MotionEvent.BUTTON_SECONDARY : MotionEvent.BUTTON_PRIMARY;
        int actionBase = pressed ? MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE;
        int action = actionBase | (changedButton << 8);
        long downTime = touchpadMouseDownTime != 0L ? touchpadMouseDownTime : eventTime;
        MotionEvent event = obtainTouchpadMouseEvent(downTime, eventTime, action, touchpadMouseX, touchpadMouseY, buttonState);
        try {
            return sendMouse(event);
        } finally {
            event.recycle();
        }
    }

    private MotionEvent obtainTouchpadMouseEvent(long downTime, long eventTime, int action, float x, float y, int buttonState) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = buttonState == 0 ? 0f : 0.5f;
        coords.size = 1f;
        return MotionEvent.obtain(downTime, eventTime, action, 1, new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords}, 0, buttonState, 1f, 1f, -1, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private MotionEvent obtainTouchpadMouseScrollEvent(long eventTime, float x, float y, float dx, float dy) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, dx);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, dy);
        return MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_SCROLL, 1, new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords}, 0, 0, 1f, 1f, -1, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private static float clampTouchpadCoordinate(float value, int size) {
        if (size <= 1) {
            return 0f;
        }
        if (value < 0f) {
            return 0f;
        }
        float max = size - 1f;
        return value > max ? max : value;
    }

    private static boolean isFiniteTouchpadDelta(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && Math.abs(value) < 10000f;
    }

    public void installKeyboardEventTracking() {
        if (!alwaysVisibleKeyboard || inputBehaviorMode == PAGE_MODE_NORMAL) {
            setKeyboardEventTrackingEnabled(false);
            return;
        }
        evaluateJavascript("(function(){var w=window,d=document;w.__coaraKeyboardTrackerEnabled=true;if(w.__coaraKeyboardTrackerInstalled)return;w.__coaraKeyboardTrackerInstalled=true;var active=function(){return w.__coaraKeyboardTrackerEnabled===true;};var remember=function(e){if(!active())return;var t=e&&e.target;if(!t)return;if(t.nodeType===3)t=t.parentElement;if(t&&t!==d&&t!==d.documentElement&&t!==d.body)w.__coaraKeyboardTarget=t;};d.addEventListener('pointerdown',remember,true);d.addEventListener('touchstart',remember,true);d.addEventListener('mousedown',remember,true);d.addEventListener('click',remember,true);d.addEventListener('focusin',function(e){if(active())w.__coaraKeyboardFocusedTarget=e&&e.target;},true);d.addEventListener('focusout',function(e){if(active()&&w.__coaraKeyboardFocusedTarget===e.target)w.__coaraKeyboardFocusedTarget=null;},true);})();", null);
    }

    public void setKeyboardEventTrackingEnabled(boolean enabled) {
        evaluateJavascript("(function(){window.__coaraKeyboardTrackerEnabled=" + enabled + ";})();", null);
    }

    private boolean isBridgeEnabled() {
        return alwaysVisibleKeyboard && inputBehaviorMode != PAGE_MODE_NORMAL;
    }

    private boolean isFilteredCharacter(char c) {
        if (keyboardMode == CHAR_MODE_NUMBER) {
            return c >= '0' && c <= '9';
        }
        if (keyboardMode == CHAR_MODE_ENGLISH) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }
        if (inputBehaviorMode == PAGE_MODE_TYPING) {
            return c >= 32 && c <= 126;
        }
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r' || c == '\t';
    }

    private String filterText(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isFilteredCharacter(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private boolean isPrintableAsciiText(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 32 || c > 126) && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private boolean isAllowedKeyEvent(KeyEvent event) {
        if (!alwaysVisibleKeyboard || event == null || keyboardMode == CHAR_MODE_NORMAL) {
            return true;
        }
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL || key == KeyEvent.KEYCODE_TAB || key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_SHIFT_LEFT || key == KeyEvent.KEYCODE_SHIFT_RIGHT || key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_CTRL_RIGHT || key == KeyEvent.KEYCODE_ALT_LEFT || key == KeyEvent.KEYCODE_ALT_RIGHT || key == KeyEvent.KEYCODE_MOVE_HOME || key == KeyEvent.KEYCODE_MOVE_END || key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN) {
            return true;
        }
        if (keyboardMode == CHAR_MODE_NUMBER) {
            if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9) {
                return true;
            }
            int unicode = event.getUnicodeChar(event.getMetaState());
            return key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9 && unicode >= '0' && unicode <= '9';
        }
        return key >= KeyEvent.KEYCODE_A && key <= KeyEvent.KEYCODE_Z;
    }

    private boolean shouldHandleCommittedText(String value) {
        if (!isBridgeEnabled()) {
            return false;
        }
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (keyboardMode == CHAR_MODE_NUMBER || keyboardMode == CHAR_MODE_ENGLISH || inputBehaviorMode == PAGE_MODE_TYPING) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            return false;
        }
        return true;
    }

    private void dispatchCommittedText(String value) {
        if (!isBridgeEnabled() || value == null || value.isEmpty()) {
            return;
        }
        String filtered = filterText(value);
        if (filtered.isEmpty()) {
            return;
        }
        final String text = filtered.replace("\r\n", "\n");
        post(new Runnable() {
            @Override
            public void run() {
                int start = 0;
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == '\r' || c == '\t') {
                        if (i > start) {
                            dispatchCharacterRun(text.substring(start, i));
                        }
                        dispatchSpecialKey(c == '\t' ? KeyEvent.KEYCODE_TAB : KeyEvent.KEYCODE_ENTER);
                        start = i + 1;
                    }
                }
                if (start < text.length()) {
                    dispatchCharacterRun(text.substring(start));
                }
            }
        });
    }

    private void dispatchCharacterRun(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        KeyEvent[] events = null;
        try {
            events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(value.toCharArray());
        } catch (RuntimeException ignored) {
        }
        if (events != null && events.length > 0) {
            for (KeyEvent event : events) {
                if (event != null) {
                    dispatchKeyEvent(event);
                }
            }
        } else {
            dispatchFallbackText(value);
        }
    }

    private void dispatchSpecialKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0);
        KeyEvent up = new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, keyCode, 0, 0);
        dispatchKeyEvent(down);
        dispatchKeyEvent(up);
    }

    private void dispatchFallbackText(String value) {
        String quoted = JSONObject.quote(value);
        String script = "(function(){var w=window,d=document,s=" + quoted + ";var a=d.activeElement;var target=a&&a!==d.body&&a!==d.documentElement?a:(w.__coaraKeyboardTarget&&d.documentElement.contains(w.__coaraKeyboardTarget)?w.__coaraKeyboardTarget:d);if(!target)target=d;function kc(c){var n=c.charCodeAt(0);if(n>=65&&n<=90)return n;if(n>=97&&n<=122)return n-32;if(n>=48&&n<=57)return n;var m={' ':'32','-':'189','_':'189','=':'187','+':'187','[':'219','{':'219',']':'221','}':'221','\\\\':'220','|':'220',';':'186',':':'186',\"'\":'222',\"\\\"\":'222',',':'188','<':'188','.':'190','>':'190','/':'191','?':'191','`':'192','~':'192','!':'49','@':'50','#':'51','$':'52','%':'53','^':'54','&':'55','*':'56','(':'57',')':'48'};return m[c]===undefined?0:Number(m[c]);}var chars=Array.from(s);for(var i=0;i<chars.length;i++){var c=chars[i],n=c.codePointAt(0),k=kc(c),code='';if(/[a-z]/i.test(c))code='Key'+c.toUpperCase();else if(/[0-9]/.test(c))code='Digit'+c;else if(c===' ')code='Space';var o={key:c,code:code,bubbles:true,cancelable:true,composed:true,view:w};function send(type,keyCode,charCode){var e=new KeyboardEvent(type,Object.assign({},o,{keyCode:keyCode,which:type==='keypress'?charCode:keyCode,charCode:charCode}));try{Object.defineProperty(e,'keyCode',{get:function(){return keyCode;}});Object.defineProperty(e,'which',{get:function(){return type==='keypress'?charCode:keyCode;}});Object.defineProperty(e,'charCode',{get:function(){return charCode;}});}catch(_){}target.dispatchEvent(e);return e;}var down=send('keydown',k,0);if(!down.defaultPrevented&&n>=32&&!/[\\r\\n\\t]/.test(c))send('keypress',k,n<=255?n:0);send('keyup',k,0);}})();";
        evaluateJavascript(script, null);
    }

    private void dispatchFallbackKey(final int action, final int keyCode, final int repeat, final int metaState, final long downTime, final long eventTime) {
        post(new Runnable() {
            @Override
            public void run() {
                dispatchKeyEvent(new KeyEvent(downTime, eventTime, action, keyCode, repeat, metaState));
            }
        });
    }

    private final class GameInputConnection extends InputConnectionWrapper {
        private final BrowserView owner;

        GameInputConnection(BrowserView owner, InputConnection target) {
            super(target, true);
            this.owner = owner;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event != null && !owner.isAllowedKeyEvent(event)) {
                return true;
            }
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                owner.composingText = "";
            }
            boolean result = super.sendKeyEvent(event);
            if (!result && owner.isBridgeEnabled() && event != null) {
                owner.dispatchFallbackKey(event.getAction(), event.getKeyCode(), event.getRepeatCount(), event.getMetaState(), event.getDownTime(), event.getEventTime());
                return true;
            }
            return result;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            final String committed = text == null ? "" : text.toString();
            String composing = owner.composingText;
            owner.composingText = "";
            if (owner.inputBehaviorMode == PAGE_MODE_TYPING
                    && !committed.isEmpty()
                    && !owner.isPrintableAsciiText(committed)
                    && owner.isPrintableAsciiText(composing)) {
                owner.dispatchCommittedText(composing);
                return true;
            }
            if (owner.shouldHandleCommittedText(committed)) {
                owner.dispatchCommittedText(committed);
                return true;
            }
            if (!owner.isBridgeEnabled() && owner.keyboardMode != CHAR_MODE_NORMAL) {
                return super.commitText(owner.filterText(committed), newCursorPosition);
            }
            if (owner.isBridgeEnabled() && !committed.isEmpty()) {
                boolean result = super.commitText(text, newCursorPosition);
                owner.post(new Runnable() {
                    @Override
                    public void run() {
                        owner.dispatchFallbackText(committed);
                    }
                });
                return result;
            }
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            if (owner.isBridgeEnabled()) {
                owner.composingText = text == null ? "" : text.toString();
                return true;
            }
            return super.setComposingText(text, newCursorPosition);
        }

        @Override
        public boolean finishComposingText() {
            if (owner.isBridgeEnabled()) {
                final String pending = owner.composingText;
                owner.composingText = "";
                if (pending != null && !pending.isEmpty()) {
                    if (owner.shouldHandleCommittedText(pending)) {
                        owner.dispatchCommittedText(pending);
                    } else if (owner.isBridgeEnabled()) {
                        super.commitText(pending, 1);
                        owner.post(new Runnable() {
                            @Override
                            public void run() {
                                owner.dispatchFallbackText(pending);
                            }
                        });
                    } else if (owner.keyboardMode != CHAR_MODE_NORMAL) {
                        super.commitText(owner.filterText(pending), 1);
                    } else {
                        super.commitText(pending, 1);
                    }
                }
                return true;
            }
            return super.finishComposingText();
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            return owner.isBridgeEnabled() || super.setComposingRegion(start, end);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (owner.isBridgeEnabled() && (beforeLength > 0 || afterLength > 0)) {
                owner.composingText = "";
                owner.post(new Runnable() {
                    @Override
                    public void run() {
                        owner.dispatchSpecialKey(KeyEvent.KEYCODE_DEL);
                    }
                });
                return true;
            }
            return super.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            if (owner.isBridgeEnabled() && (beforeLength > 0 || afterLength > 0)) {
                owner.composingText = "";
                owner.post(new Runnable() {
                    @Override
                    public void run() {
                        owner.dispatchSpecialKey(KeyEvent.KEYCODE_DEL);
                    }
                });
                return true;
            }
            return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        }

        @Override
        public boolean performEditorAction(int actionCode) {
            if (owner.isBridgeEnabled() && (actionCode == EditorInfo.IME_ACTION_DONE || actionCode == EditorInfo.IME_ACTION_GO || actionCode == EditorInfo.IME_ACTION_NEXT || actionCode == EditorInfo.IME_ACTION_UNSPECIFIED || actionCode == EditorInfo.IME_ACTION_SEND || actionCode == EditorInfo.IME_ACTION_SEARCH || actionCode == EditorInfo.IME_ACTION_PREVIOUS)) {
                owner.composingText = "";
                owner.post(new Runnable() {
                    @Override
                    public void run() {
                        owner.dispatchSpecialKey(KeyEvent.KEYCODE_ENTER);
                    }
                });
                return true;
            }
            return super.performEditorAction(actionCode);
        }
    }
}
