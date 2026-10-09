package com.coara.browser2.util;

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

    private static WeakReference<BrowserView> currentWebView = new WeakReference<>(null);
    private static boolean active;
    private static float pendingMoveX;
    private static float pendingMoveY;
    private static float pendingStickX;
    private static float pendingStickY;
    private static boolean pendingStick;
    private static float stickX;
    private static float stickY;
    private static int stickMode;
    private static boolean flushScheduled;
    private static boolean bridgeInstalled;
    private static long lastInstallTime;
    private static long nativeTouchDownTime;
    private static boolean nativeTouchDown;
    private static String bridgeSource;

    private TouchpadController() {
    }

    private static void clearPending() {
        pendingMoveX = 0f;
        pendingMoveY = 0f;
        pendingStickX = 0f;
        pendingStickY = 0f;
        pendingStick = false;
        flushScheduled = false;
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
            call(webView, "stop", null);
        }
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
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        webView.postOnAnimation(() -> {
            flushScheduled = false;
            flush(webView);
        });
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
        final boolean verify = !"stop".equals(method);
        ValueCallback<String> callback = verify ? new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"1".equals(value)) {
                    onBridgeMissing(webView);
                }
            }
        } : null;
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
        installAndStart(webView);
        if (nativeTouchDown) {
            nativeTouchDown = false;
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
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 0f;
        coords[0].size = 1f;
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
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
        try {
            view.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
        if (action == MotionEvent.ACTION_UP) {
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
