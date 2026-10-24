package com.coara.browser2.gecko;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private volatile String persistedBaseUrl;
    private volatile String legacyBaseUrl;
    private static final String KEY_CORE_BASE = "core_ext_base_url";
    private String defaultUserAgent;
    private boolean darkMode;
    private boolean imagesEnabled = true;
    private boolean zoomEnabled;
    private int desktopViewportWidth = -1;
    private WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private final List<WeakReference<Activity>> resumedActivities = new ArrayList<>();
    private final Map<Integer, PendingActivityResult> pendingActivityResults = new HashMap<>();
    private static final int WEBAUTHN_REQUEST_CODE_START = 0x6A00;
    private static final int WEBAUTHN_REQUEST_CODE_COUNT = 256;
    private int nextWebAuthnRequestCode = WEBAUTHN_REQUEST_CODE_START;
    private static final long ACTIVITY_RESULT_TIMEOUT_MS = 300000L;

    private static final class PendingActivityResult {
        final GeckoResult<Intent> result;
        final Runnable timeout;
        final WeakReference<Activity> sourceActivity;

        PendingActivityResult(GeckoResult<Intent> result, Runnable timeout, Activity sourceActivity) {
            this.result = result;
            this.timeout = timeout;
            this.sourceActivity = new WeakReference<>(sourceActivity);
        }
    }

    private BrowserEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.agent = new PageAgent(this);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            BrowserEngine engine = new BrowserEngine(context);
            instance = engine;
            engine.start();
            com.coara.browser2.fx.FxExtensionManager.get().start();
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
        registerActivityTracking();
        SharedPreferences prefs = appContext.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
        darkMode = prefs.getBoolean(BrowserConstants.KEY_DARK_MODE, false);
        persistedBaseUrl = prefs.getString(KEY_CORE_BASE, null);
        ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
                .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
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
                .fissionEnabled(true)
                .loginAutofillEnabled(false)
                .translationsOfferPopup(false)
                .crashPullNeverShowAgain(true)
                .build();
        settings.setWebContentIsolationStrategy(GeckoRuntimeSettings.STRATEGY_ISOLATE_EVERYTHING);
        runtime = GeckoRuntime.create(appContext, settings);
        runtime.setActivityDelegate(this::startWebAuthnActivityForResult);
        runtime.getSettings().setPreferredColorScheme(darkMode
                ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        clearLegacyColorSchemePrefs();
        applyContentColorScheme(darkMode);
        try {
            GeckoPreferenceController.setGeckoPref("accessibility.typeaheadfind.matchesCountLimit", 500,
                    GeckoPreferenceController.PREF_BRANCH_USER);
        } catch (Throwable ignored) {
        }
        executor = new GeckoWebExecutor(runtime);
        defaultUserAgent = GeckoSession.getDefaultUserAgent();
        installCore();
    }


    private void registerActivityTracking() {
        if (!(appContext instanceof Application)) {
            return;
        }
        ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                markActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                removeResumedActivity(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                removeResumedActivity(activity);
                List<PendingActivityResult> abandoned = new ArrayList<>();
                Iterator<Map.Entry<Integer, PendingActivityResult>> iterator =
                        pendingActivityResults.entrySet().iterator();
                while (iterator.hasNext()) {
                    PendingActivityResult pending = iterator.next().getValue();
                    if (pending.sourceActivity.get() == activity) {
                        iterator.remove();
                        main.removeCallbacks(pending.timeout);
                        abandoned.add(pending);
                    }
                }
                for (PendingActivityResult pending : abandoned) {
                    pending.result.completeExceptionally(
                            new IllegalStateException("Browser activity closed during WebAuthn"));
                }
            }
        });
    }

    private void markActivityResumed(Activity activity) {
        for (int i = resumedActivities.size() - 1; i >= 0; i--) {
            Activity existing = resumedActivities.get(i).get();
            if (existing == null || existing == activity) {
                resumedActivities.remove(i);
            }
        }
        resumedActivities.add(new WeakReference<>(activity));
        refreshResumedActivity();
    }

    private void removeResumedActivity(Activity activity) {
        for (int i = resumedActivities.size() - 1; i >= 0; i--) {
            Activity existing = resumedActivities.get(i).get();
            if (existing == null || existing == activity) {
                resumedActivities.remove(i);
            }
        }
        refreshResumedActivity();
    }

    private void refreshResumedActivity() {
        Activity candidate = null;
        for (int i = resumedActivities.size() - 1; i >= 0; i--) {
            Activity activity = resumedActivities.get(i).get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                resumedActivities.remove(i);
                continue;
            }
            candidate = activity;
            break;
        }
        resumedActivity = new WeakReference<>(candidate instanceof BrowserHost ? candidate : null);
    }

    private GeckoResult<Intent> startWebAuthnActivityForResult(PendingIntent pendingIntent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return GeckoResult.fromException(new IllegalStateException("WebAuthn activity request must run on the main thread"));
        }
        if (pendingIntent == null) {
            return GeckoResult.fromException(new IllegalArgumentException("Missing WebAuthn PendingIntent"));
        }
        Activity activity = resumedActivity.get();
        if (activity == null || !(activity instanceof BrowserHost)
                || activity.isFinishing() || activity.isDestroyed()) {
            return GeckoResult.fromException(new IllegalStateException("No active browser activity for WebAuthn"));
        }
        if (!pendingActivityResults.isEmpty()) {
            return GeckoResult.fromException(new IllegalStateException("A WebAuthn request is already active"));
        }
        int requestCode = allocateWebAuthnRequestCode();
        if (requestCode < 0) {
            return GeckoResult.fromException(new IllegalStateException("Too many active WebAuthn requests"));
        }
        GeckoResult<Intent> result = new GeckoResult<>(main);
        Runnable timeout = () -> {
            PendingActivityResult pending = pendingActivityResults.remove(requestCode);
            if (pending != null) {
                pending.result.completeExceptionally(new IllegalStateException("WebAuthn activity timed out"));
            }
        };
        pendingActivityResults.put(requestCode, new PendingActivityResult(result, timeout, activity));
        main.postDelayed(timeout, ACTIVITY_RESULT_TIMEOUT_MS);
        try {
            activity.startIntentSenderForResult(
                    pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException | RuntimeException error) {
            PendingActivityResult pending = pendingActivityResults.remove(requestCode);
            main.removeCallbacks(timeout);
            if (pending != null) {
                pending.result.completeExceptionally(error);
            }
        }
        return result;
    }

    private int allocateWebAuthnRequestCode() {
        for (int i = 0; i < WEBAUTHN_REQUEST_CODE_COUNT; i++) {
            int requestCode = nextWebAuthnRequestCode;
            nextWebAuthnRequestCode++;
            if (nextWebAuthnRequestCode >= WEBAUTHN_REQUEST_CODE_START + WEBAUTHN_REQUEST_CODE_COUNT) {
                nextWebAuthnRequestCode = WEBAUTHN_REQUEST_CODE_START;
            }
            if (!pendingActivityResults.containsKey(requestCode)) {
                return requestCode;
            }
        }
        return -1;
    }

    public void handleWebAuthnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode < WEBAUTHN_REQUEST_CODE_START
                || requestCode >= WEBAUTHN_REQUEST_CODE_START + WEBAUTHN_REQUEST_CODE_COUNT) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> handleWebAuthnActivityResult(requestCode, resultCode, data));
            return;
        }
        PendingActivityResult pending = pendingActivityResults.remove(requestCode);
        if (pending == null) {
            return;
        }
        main.removeCallbacks(pending.timeout);
        if (resultCode == Activity.RESULT_OK && data != null) {
            pending.result.complete(data);
        } else {
            pending.result.completeExceptionally(new IntentSender.SendIntentException(
                    resultCode == Activity.RESULT_OK
                            ? "WebAuthn provider returned no result"
                            : "WebAuthn request canceled"));
        }
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
            if (extensionBaseUrl != null && !extensionBaseUrl.equals(persistedBaseUrl)) {
                final String previous = persistedBaseUrl;
                persistedBaseUrl = extensionBaseUrl;
                appContext.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putString(KEY_CORE_BASE, extensionBaseUrl).apply();
                if (previous != null) {
                    legacyBaseUrl = previous;
                }
            }
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

    public String getKnownExtensionBaseUrl() {
        String live = extensionBaseUrl;
        return live != null ? live : persistedBaseUrl;
    }

    public String getLegacyExtensionBaseUrl() {
        return legacyBaseUrl;
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

    private void applyContentColorScheme(boolean dark) {
        try {
            GeckoPreferenceController.setGeckoPref("layout.css.prefers-color-scheme.content-override", dark ? 0 : 1,
                    GeckoPreferenceController.PREF_BRANCH_USER);
        } catch (Throwable ignored) {
        }
    }

    public void setDarkMode(boolean enabled) {
        darkMode = enabled;
        applyContentColorScheme(enabled);
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

    public void setCookieBehavior(int behavior) {
        if (runtime != null) {
            runtime.getSettings().getContentBlocking().setCookieBehavior(behavior);
            runtime.getSettings().getContentBlocking().setCookieBehaviorPrivateMode(behavior);
        }
    }

    public GeckoResult<Void> clearData(long flags) {
        return runtime.getStorageController().clearData(flags);
    }

    public GeckoResult<Void> clearAllBrowsingData() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.ALL);
    }

    public GeckoResult<Void> clearCookies() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.DOM_STORAGES | StorageController.ClearFlags.AUTH_SESSIONS);
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
