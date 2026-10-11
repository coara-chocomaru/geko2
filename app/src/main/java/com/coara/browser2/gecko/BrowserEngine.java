package com.coara.browser2.gecko;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;

import com.coara.browser2.util.BrowserConstants;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoPreferenceController;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class BrowserEngine {
    public static final String EXTENSION_LOCATION = "resource://android/assets/extensions/core/";
    public static final String EXTENSION_ID = "core@coara.browser2";
    public static final String PAGES_DIR = "pages/";
    public static final String PAGES_ASSET_DIR = "extensions/core/pages/";

    private static volatile BrowserEngine instance;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PageAgent agent;
    private final List<Runnable> agentWaiters = new ArrayList<>();
    private GeckoRuntime runtime;
    private GeckoWebExecutor executor;
    private WebExtension core;
    private String extensionBaseUrl;
    private String defaultUserAgent;
    private boolean darkMode;
    private boolean imagesEnabled = true;
    private boolean zoomEnabled;
    private int desktopViewportWidth = -1;

    private BrowserEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.agent = new PageAgent(this);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            BrowserEngine engine = new BrowserEngine(context);
            instance = engine;
            engine.start();
        }
    }

    public static BrowserEngine get() {
        BrowserEngine engine = instance;
        if (engine == null) {
            throw new IllegalStateException("BrowserEngine.init was not called");
        }
        return engine;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    private void start() {
        SharedPreferences prefs = appContext.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
        darkMode = prefs.getBoolean(BrowserConstants.KEY_DARK_MODE, false);
        ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                .build();
        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .contentBlocking(blocking)
                .aboutConfigEnabled(false)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .debugLogging(false)
                .doubleTapZoomingEnabled(false)
                .inputAutoZoomEnabled(false)
                .forceUserScalableEnabled(false)
                .webManifest(false)
                .fissionEnabled(false)
                .loginAutofillEnabled(false)
                .translationsOfferPopup(false)
                .crashPullNeverShowAgain(true)
                .build();
        runtime = GeckoRuntime.create(appContext, settings);
        runtime.getSettings().setPreferredColorScheme(darkMode
                ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        clearLegacyColorSchemePrefs();
        executor = new GeckoWebExecutor(runtime);
        defaultUserAgent = GeckoSession.getDefaultUserAgent();
        installCore();
    }

    private void installCore() {
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).then(
                new GeckoResult.OnValueListener<WebExtension, Void>() {
                    @Override
                    public GeckoResult<Void> onValue(WebExtension extension) {
                        onCoreReady(extension);
                        return null;
                    }
                },
                new GeckoResult.OnExceptionListener<Void>() {
                    @Override
                    public GeckoResult<Void> onException(Throwable error) {
                        flushAgentWaiters();
                        return null;
                    }
                });
    }

    private void onCoreReady(WebExtension extension) {
        core = extension;
        try {
            extensionBaseUrl = extension.metaData.baseUrl;
        } catch (Throwable ignored) {
            extensionBaseUrl = null;
        }
        runtime.getWebExtensionController().setAllowedInPrivateBrowsing(extension, true);
        extension.setMessageDelegate(agent.backgroundDelegate(), PageAgent.APP_BACKGROUND);
        extension.setTabDelegate(agent.tabDelegate());
        flushAgentWaiters();
    }

    private void flushAgentWaiters() {
        List<Runnable> copy = new ArrayList<>(agentWaiters);
        agentWaiters.clear();
        for (Runnable r : copy) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public void whenAgentSettled(Runnable runnable) {
        if (core != null || runtime == null) {
            runnable.run();
            return;
        }
        agentWaiters.add(runnable);
    }

    public Context getContext() {
        return appContext;
    }

    public GeckoRuntime getRuntime() {
        return runtime;
    }

    public GeckoWebExecutor getExecutor() {
        return executor;
    }

    public WebExtension getCore() {
        return core;
    }

    public PageAgent getAgent() {
        return agent;
    }

    public String getExtensionBaseUrl() {
        return extensionBaseUrl;
    }

    public boolean isAgentReady() {
        return core != null;
    }

    public String getDefaultUserAgent() {
        return defaultUserAgent == null ? "" : defaultUserAgent;
    }

    private void clearLegacyColorSchemePrefs() {
        try {
            GeckoPreferenceController.clearGeckoUserPref("layout.css.prefers-color-scheme.content-override");
            GeckoPreferenceController.clearGeckoUserPref("ui.systemUsesDarkTheme");
        } catch (Throwable ignored) {
        }
    }

    public void setDarkMode(boolean enabled) {
        darkMode = enabled;
        if (runtime != null) {
            runtime.getSettings().setPreferredColorScheme(enabled
                    ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                    : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        }
    }

    public void updateDesktopViewport(Context context) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density > 0f ? dm.density : 1f;
        float widthDp = dm.widthPixels / density;
        int target;
        if (widthDp < 600f) {
            target = Math.max(820, Math.round(widthDp * 2f));
        } else {
            target = Math.min(1440, Math.max(1024, Math.round(widthDp * 1.3f)));
        }
        if (target == desktopViewportWidth) {
            return;
        }
        desktopViewportWidth = target;
        try {
            GeckoPreferenceController.setGeckoPref("browser.viewport.desktopWidth", target,
                    GeckoPreferenceController.PREF_BRANCH_USER);
        } catch (Throwable ignored) {
        }
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setImagesEnabled(boolean enabled) {
        if (imagesEnabled == enabled) {
            return;
        }
        imagesEnabled = enabled;
        GeckoPreferenceController.setGeckoPref("permissions.default.image", enabled ? 1 : 2,
                GeckoPreferenceController.PREF_BRANCH_USER);
    }

    public void setZoomEnabled(boolean enabled) {
        zoomEnabled = enabled;
        if (runtime != null) {
            runtime.getSettings().setForceUserScalableEnabled(enabled);
            runtime.getSettings().setDoubleTapZoomingEnabled(enabled);
        }
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    public void setFontSizeFactor(float factor) {
        if (runtime != null) {
            runtime.getSettings().setFontSizeFactor(factor);
        }
    }

    public GeckoResult<Void> clearData(long flags) {
        return runtime.getStorageController().clearData(flags);
    }

    public GeckoResult<Void> clearAllBrowsingData() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.ALL);
    }

    public GeckoResult<Void> clearCookies() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.COOKIES);
    }

    public GeckoResult<Void> clearCaches() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.ALL_CACHES);
    }

    public GeckoResult<Void> clearDomStorage() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.DOM_STORAGES);
    }

    public GeckoResult<Void> clearAuthSessions() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.AUTH_SESSIONS);
    }

    public String readAsset(String path) {
        AssetManager assets = appContext.getAssets();
        try (InputStream in = assets.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public Handler mainHandler() {
        return main;
    }

    public void shutdown() {
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    static JSONObject obj() {
        return new JSONObject();
    }

    static GeckoResult<AllowOrDeny> allow() {
        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
    }

    static GeckoResult<AllowOrDeny> deny() {
        return GeckoResult.fromValue(AllowOrDeny.DENY);
    }
}
