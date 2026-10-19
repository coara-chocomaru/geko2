package com.coara.browser2.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.gecko.BrowserView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

public final class TouchpadController {
    private static final String CALL_HEAD = "(function(){try{var n=window.__coaraTouchpadNative;if(n&&typeof n.";
    private static final String CALL_MID = "==='function'){n.";
    private static final String CALL_TAIL = ");return 1;}return 0;}catch(e){return 0;}})();";
    private static final long REINSTALL_INTERVAL_MS = 700L;
    private static final int KIND_HOVER = 1;
    private static final int KIND_TOUCH_DOWN = 2;
    private static final int KIND_TOUCH_MOVE = 3;
    private static final int KIND_TOUCH_UP = 4;
    private static final int KIND_KEY_DOWN = 5;
    private static final int KIND_KEY_UP = 6;
    private static final long TOUCH_UP_DELAY_MS = 40L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object HOST_MOVE_LOCK = new Object();
    private static final class PendingMotion {
        BrowserView owner;
        float a;
        float b;
        boolean pending;
    }
    private static final PendingMotion pendingHover = new PendingMotion();
    private static final PendingMotion pendingTouchMove = new PendingMotion();
    private static final MotionEvent.PointerProperties[] HOVER_PROPERTIES = {new MotionEvent.PointerProperties()};
    private static final MotionEvent.PointerCoords[] HOVER_COORDS = {new MotionEvent.PointerCoords()};
    private static WeakReference<BrowserView> currentWebView = new WeakReference<>(null);
    private static volatile boolean active;
    private static volatile boolean blockScreenTouch;
    private static boolean injecting;
    private static float pendingMoveX;
    private static float pendingMoveY;
    private static float pendingStickX;
    private static float pendingStickY;
    private static boolean pendingStick;
    private static float stickX;
    private static float stickY;
    private static int stickMode;
    private static boolean flushScheduled;
    private static BrowserView flushWebView;
    private static boolean hostMoveScheduled;
    private static boolean bridgeInstalled;
    private static long lastInstallTime;
    private static long lastBridgeCheckTime;
    private static long nativeTouchDownTime;
    private static boolean nativeTouchDown;
    private static String bridgeSource;

    static {
        HOVER_PROPERTIES[0].id = 0;
        HOVER_PROPERTIES[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        HOVER_COORDS[0].pressure = 0f;
        HOVER_COORDS[0].size = 1f;
    }

    private static final Runnable FLUSH_SCHEDULED_MOVE = new Runnable() {
        @Override
        public void run() {
            BrowserView target = flushWebView;
            flushWebView = null;
            flushScheduled = false;
            if (target != null) {
                flush(target);
            }
        }
    };

    private static final Runnable FLUSH_HOST_MOVES = new Runnable() {
        @Override
        public void run() {
            BrowserView hoverOwner = null;
            BrowserView touchOwner = null;
            float hoverA = 0f;
            float hoverB = 0f;
            float touchA = 0f;
            float touchB = 0f;
            boolean hasHover;
            boolean hasTouch;
            synchronized (HOST_MOVE_LOCK) {
                hasHover = pendingHover.pending;
                if (hasHover) {
                    hoverOwner = pendingHover.owner;
                    hoverA = pendingHover.a;
                    hoverB = pendingHover.b;
                    pendingHover.pending = false;
                    pendingHover.owner = null;
                }
                hasTouch = pendingTouchMove.pending;
                if (hasTouch) {
                    touchOwner = pendingTouchMove.owner;
                    touchA = pendingTouchMove.a;
                    touchB = pendingTouchMove.b;
                    pendingTouchMove.pending = false;
                    pendingTouchMove.owner = null;
                }
                hostMoveScheduled = false;
            }
            if (hasHover && hoverOwner != null) {
                dispatchHostRequest(hoverOwner, KIND_HOVER, hoverA, hoverB);
            }
            if (hasTouch && touchOwner != null) {
                dispatchHostRequest(touchOwner, KIND_TOUCH_MOVE, touchA, touchB);
            }
        }
    };

    private TouchpadController() {
    }

    private static void clearPending() {
        if (flushScheduled && flushWebView != null) {
            flushWebView.removeCallbacks(FLUSH_SCHEDULED_MOVE);
        }
        flushWebView = null;
        pendingMoveX = 0f;
        pendingMoveY = 0f;
        pendingStickX = 0f;
        pendingStickY = 0f;
        pendingStick = false;
        flushScheduled = false;
    }

    public static void refreshSurface() {
        BrowserView view = currentWebView.get();
        if (view != null) {
            view.refreshSurface();
        }
    }

    public static void setCurrentWebView(BrowserView webView) {
        BrowserView current = currentWebView.get();
        if (current == webView) {
            if (active) {
                installAndStart(webView);
            }
            return;
        }
        if (current != null) {
            abandonNativeTouch(current);
            call(current, "stop", null);
        }
        currentWebView = new WeakReference<>(webView);
        clearPending();
        stickX = 0f;
        stickY = 0f;
        bridgeInstalled = false;
        if (active) {
            installAndStart(webView);
        }
    }

    public static void clearCurrentWebView(BrowserView webView) {
        if (currentWebView.get() == webView) {
            abandonNativeTouch(webView);
            call(webView, "stop", null);
            currentWebView = new WeakReference<>(null);
            clearPending();
            stickX = 0f;
            stickY = 0f;
            bridgeInstalled = false;
        }
    }

    public static void onPageStarted(BrowserView webView) {
        if (currentWebView.get() == webView) {
            pendingMoveX = 0f;
            pendingMoveY = 0f;
            pendingStick = false;
            bridgeInstalled = false;
        }
    }

    public static void onPageFinished(BrowserView webView) {
        if (currentWebView.get() == webView) {
            if (active) {
                installAndStart(webView);
            }
        }
    }

    public static void setPointerLocked(BrowserView webView, boolean locked) {
        if (currentWebView.get() != webView || !bridgeInstalled) {
            return;
        }
        call(webView, "setPointerLocked", locked ? "true" : "false");
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean enabled) {
        if (active == enabled) {
            BrowserView webView = currentWebView.get();
            if (enabled) {
                installAndStart(webView);
            }
            return;
        }
        active = enabled;
        BrowserView webView = currentWebView.get();
        clearPending();
        stickX = 0f;
        stickY = 0f;
        if (webView == null) {
            return;
        }
        if (enabled) {
            installAndStart(webView);
        } else {
            abandonNativeTouch(webView);
            call(webView, "stop", null);
        }
    }

    public static void setScreenTouchBlocked(boolean blocked) {
        blockScreenTouch = blocked;
    }

    public static boolean isScreenTouchBlocked() {
        return active && blockScreenTouch;
    }

    public static boolean shouldDropScreenTouch() {
        return active && blockScreenTouch && !injecting;
    }

    private static void abandonNativeTouch(BrowserView view) {
        if (nativeTouchDown && view != null) {
            try {
                injectTouch(view, MotionEvent.ACTION_CANCEL, 0f, 0f);
            } catch (Throwable ignored) {
            }
        }
        nativeTouchDown = false;
    }

    public static void setStickMode(int mode) {
        int safe = mode >= 0 && mode < TouchpadSettings.STICK_MODE_COUNT ? mode : 0;
        if (stickMode == safe) {
            return;
        }
        stickMode = safe;
        BrowserView webView = currentWebView.get();
        if (webView != null && bridgeInstalled) {
            call(webView, "stickMode", Integer.toString(safe));
        }
    }

    public static void move(float dx, float dy) {
        if (!active || !isFinite(dx) || !isFinite(dy) || (dx == 0f && dy == 0f)) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        pendingMoveX += dx;
        pendingMoveY += dy;
        scheduleFlush(webView);
    }

    public static void stickTap() {
        if (!active) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        flush(webView);
        ensureStarted(webView);
        call(webView, "stickTap", null);
    }

    public static void stick(float x, float y) {
        if (!active || !isFinite(x) || !isFinite(y)) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        stickX = x;
        stickY = y;
        pendingStickX = x;
        pendingStickY = y;
        pendingStick = true;
        if (x == 0f && y == 0f) {
            flush(webView);
        } else {
            scheduleFlush(webView);
        }
    }

    public static void click(int button) {
        if (!active) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        flush(webView);
        ensureStarted(webView);
        call(webView, "click", Integer.toString(button));
    }

    public static void buttonDown(int button) {
        if (!active) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        flush(webView);
        ensureStarted(webView);
        call(webView, "down", Integer.toString(button));
    }

    public static void buttonUp(int button) {
        if (!active) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        flush(webView);
        ensureStarted(webView);
        call(webView, "up", Integer.toString(button));
    }

    public static void cancelButtons() {
        if (!active) {
            return;
        }
        BrowserView webView = currentWebView.get();
        if (webView == null) {
            return;
        }
        flush(webView);
        ensureStarted(webView);
        call(webView, "cancel", null);
    }

    private static void scheduleFlush(BrowserView webView) {
        if (flushScheduled && flushWebView == webView) {
            return;
        }
        if (flushScheduled && flushWebView != null) {
            flushWebView.removeCallbacks(FLUSH_SCHEDULED_MOVE);
        }
        flushScheduled = true;
        flushWebView = webView;
        webView.postOnAnimation(FLUSH_SCHEDULED_MOVE);
    }

    private static void flush(BrowserView webView) {
        if (webView == null || currentWebView.get() != webView) {
            return;
        }
        float dx = pendingMoveX;
        float dy = pendingMoveY;
        boolean hasStick = pendingStick;
        float tx = pendingStickX;
        float ty = pendingStickY;
        pendingMoveX = 0f;
        pendingMoveY = 0f;
        pendingStickX = 0f;
        pendingStickY = 0f;
        pendingStick = false;
        boolean hasMove = dx != 0f || dy != 0f;
        if (!hasMove && !hasStick) {
            return;
        }
        ensureStarted(webView);
        StringBuilder args = new StringBuilder(80);
        args.append(dx).append(',').append(dy).append(',')
                .append(hasStick ? 1 : 0).append(',')
                .append(tx).append(',').append(ty);
        call(webView, "batch", args.toString());
    }

    private static void ensureStarted(BrowserView webView) {
        if (!bridgeInstalled) {
            installAndStart(webView);
        }
    }

    private static void installAndStart(BrowserView webView) {
        if (webView == null) {
            return;
        }
        boolean freshlyInstalled = false;
        if (!bridgeInstalled) {
            installBridge(webView);
            freshlyInstalled = bridgeInstalled;
        }
        if (bridgeInstalled) {
            call(webView, "start", null);
            if (freshlyInstalled) {
                call(webView, "stickMode", Integer.toString(stickMode));
                if (stickX != 0f || stickY != 0f) {
                    call(webView, "stick", Float.toString(stickX) + "," + Float.toString(stickY));
                }
            }
        }
    }

    private static void installBridge(BrowserView webView) {
        if (webView == null || currentWebView.get() != webView) {
            return;
        }
        try {
            String script = readBridge(webView);
            if (script == null || script.isEmpty()) {
                return;
            }
            webView.evaluateJavascript(script, null);
            bridgeInstalled = true;
            lastInstallTime = SystemClock.uptimeMillis();
            lastBridgeCheckTime = 0L;
        } catch (Throwable ignored) {
            bridgeInstalled = false;
        }
    }

    private static String readBridge(BrowserView webView) {
        if (bridgeSource != null) {
            return bridgeSource;
        }
        StringBuilder out = new StringBuilder(32000);
        try (InputStream in = webView.getContext().getAssets().open("touchpad_bridge.js");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            bridgeSource = out.toString();
            return bridgeSource;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void call(BrowserView webView, String method, String args) {
        if (webView == null || method == null || method.isEmpty()) {
            return;
        }
        StringBuilder js = new StringBuilder(160);
        js.append(CALL_HEAD).append(method).append(CALL_MID).append(method).append('(');
        if (args != null) {
            js.append(args);
        }
        js.append(CALL_TAIL);
        long now = SystemClock.uptimeMillis();
        boolean verify = !"stop".equals(method)
                && (lastBridgeCheckTime == 0L || now - lastBridgeCheckTime >= REINSTALL_INTERVAL_MS);
        ValueCallback<String> callback = null;
        if (verify) {
            lastBridgeCheckTime = now;
            callback = new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (!"1".equals(value)) {
                        onBridgeMissing(webView);
                    }
                }
            };
        }
        try {
            webView.evaluateJavascript(js.toString(), callback);
        } catch (Throwable ignored) {
        }
    }

    private static void onBridgeMissing(BrowserView webView) {
        if (!active || currentWebView.get() != webView) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastInstallTime < REINSTALL_INTERVAL_MS) {
            return;
        }
        bridgeInstalled = false;
        lastBridgeCheckTime = 0L;
        installAndStart(webView);
        if (nativeTouchDown) {
            nativeTouchDown = false;
        }
    }

    public static void postHostRequest(final BrowserView owner, final int kind, final float a, final float b) {
        if (!active || owner == null || !isFinite(a) || !isFinite(b)) {
            return;
        }
        if (kind == KIND_HOVER || kind == KIND_TOUCH_MOVE) {
            synchronized (HOST_MOVE_LOCK) {
                PendingMotion pending = kind == KIND_HOVER ? pendingHover : pendingTouchMove;
                pending.owner = owner;
                pending.a = a;
                pending.b = b;
                pending.pending = true;
                if (!hostMoveScheduled) {
                    hostMoveScheduled = true;
                    MAIN_HANDLER.post(FLUSH_HOST_MOVES);
                }
            }
            return;
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                dispatchHostRequest(owner, kind, a, b);
            }
        });
    }

    private static void dispatchHostRequest(BrowserView owner, int kind, float a, float b) {
        try {
            onHostRequest(owner, kind, a, b);
        } catch (Throwable ignored) {
        }
    }

    public static void onHostRequest(BrowserView owner, int kind, float a, float b) {
        if (!active || owner == null || currentWebView.get() != owner || !isFinite(a) || !isFinite(b)) {
            return;
        }
        int w = owner.getWidth();
        int h = owner.getHeight();
        if (w <= 1 || h <= 1) {
            return;
        }
        float x = Math.max(0f, Math.min(1f, a)) * (w - 1);
        float y = Math.max(0f, Math.min(1f, b)) * (h - 1);
        try {
            switch (kind) {
                case KIND_HOVER:
                    injectHover(owner, x, y);
                    break;
                case KIND_TOUCH_DOWN:
                    injectTouch(owner, MotionEvent.ACTION_DOWN, x, y);
                    break;
                case KIND_TOUCH_MOVE:
                    injectTouch(owner, MotionEvent.ACTION_MOVE, x, y);
                    break;
                case KIND_TOUCH_UP:
                    final float ux = x;
                    final float uy = y;
                    owner.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (currentWebView.get() == owner) {
                                injectTouch(owner, MotionEvent.ACTION_UP, ux, uy);
                            }
                        }
                    }, TOUCH_UP_DELAY_MS);
                    break;
                case KIND_KEY_DOWN:
                    injectKey(owner, KeyEvent.ACTION_DOWN, (int) a);
                    break;
                case KIND_KEY_UP:
                    injectKey(owner, KeyEvent.ACTION_UP, (int) a);
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void injectHover(BrowserView view, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerCoords coords = HOVER_COORDS[0];
        coords.x = x;
        coords.y = y;
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, 1,
                HOVER_PROPERTIES, HOVER_COORDS, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        try {
            view.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void injectTouch(BrowserView view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            nativeTouchDownTime = now;
            nativeTouchDown = true;
        } else if (!nativeTouchDown) {
            return;
        }
        MotionEvent event = MotionEvent.obtain(nativeTouchDownTime, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        boolean wasInjecting = injecting;
        injecting = true;
        try {
            view.dispatchTouchEvent(event);
        } finally {
            injecting = wasInjecting;
            event.recycle();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            nativeTouchDown = false;
        }
    }

    private static void injectKey(BrowserView view, int action, int keyCode) {
        if (keyCode <= 0) {
            return;
        }
        if (!view.hasFocus()) {
            view.requestFocus();
        }
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        view.dispatchKeyEvent(event);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
