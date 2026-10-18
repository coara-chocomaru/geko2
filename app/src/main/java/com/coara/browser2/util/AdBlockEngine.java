package com.coara.browser2.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.PageAgent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AdBlockEngine {

    private static final String PREFS = "coara_adblock";
    private static final String KEY_ENABLED = "enabled";
    private static final Map<BrowserView, Boolean> VIEWS = Collections.synchronizedMap(new WeakHashMap<BrowserView, Boolean>());
    private static final Object LOCK = new Object();

    private static volatile boolean enabled;
    private static volatile boolean initialized;
    private static volatile int blocked;
    private static String script;
    private static String rules;

    private AdBlockEngine() {
    }

    public static void init(Context context) {
        if (initialized || context == null) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                enabled = sp.getBoolean(KEY_ENABLED, false);
            } catch (Throwable ignored) {
                enabled = false;
            }
            initialized = true;
        }
        if (enabled) {
            push();
        }
    }

    public static void attach(BrowserView view) {
        if (view == null) {
            return;
        }
        init(view.getContext());
        VIEWS.put(view, Boolean.TRUE);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int blockedCount() {
        return blocked;
    }

    public static void onBlockedCount(int value) {
        blocked = value;
    }

    public static String menuTitle() {
        int n = blocked;
        return n > 0 ? "AdBlock (" + n + ")" : "AdBlock";
    }

    private static void ensureBuilt() {
        synchronized (LOCK) {
            if (script == null) {
                script = AdBlockRules.pageWorldWrapper(AdBlockRules.buildScript());
                rules = AdBlockRules.rulesJson();
            }
        }
    }

    private static void push() {
        if (!BrowserEngine.isInitialized()) {
            return;
        }
        try {
            BrowserEngine engine = BrowserEngine.get();
            if (engine.getAgent() == null) {
                return;
            }
            if (enabled) {
                ensureBuilt();
                engine.getAgent().pushAdBlock(true, new org.json.JSONObject(rules), script);
            } else {
                engine.getAgent().pushAdBlock(false, null, null);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setEnabled(Context context, boolean value) {
        init(context);
        enabled = value;
        try {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, value).apply();
        } catch (Throwable ignored) {
        }
        push();
        String code;
        if (value) {
            ensureBuilt();
            code = script;
        } else {
            code = AdBlockRules.pageWorldWrapper(AdBlockRules.SCRIPT_OFF);
        }
        List<BrowserView> views;
        synchronized (VIEWS) {
            views = new ArrayList<>(VIEWS.keySet());
        }
        for (BrowserView view : views) {
            try {
                if (view != null && !view.isDestroyed() && view.hasSession()) {
                    view.evaluateJavascript(code, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onPageStarted(BrowserView view) {
    }

    public static boolean allowNewWindow(boolean isUserGesture) {
        return !enabled || isUserGesture;
    }
}
