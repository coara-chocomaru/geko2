package com.coara.browser2.util;

import android.os.Handler;
import android.os.SystemClock;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;

public final class DarkModeSwitcher {
    private static final String PROBE = "(function(){try{return window.matchMedia('(prefers-color-scheme: dark)').matches;}catch(e){return null;}})()";
    private static final long POLL_MS = 90L;
    private static final long MAX_WAIT_MS = 450L;
    private static final long UNKNOWN_WAIT_MS = 300L;
    private static final long SETTLE_MS = 80L;

    private static int ticket;

    private DarkModeSwitcher() {
    }

    public static void settle(final BrowserView view, final boolean dark, final Runnable done) {
        final int mine = ++ticket;
        final long started = SystemClock.uptimeMillis();
        final Handler handler = UiThread.mainHandler();
        if (view == null) {
            handler.postDelayed(done, SETTLE_MS);
            return;
        }
        final Runnable[] poll = new Runnable[1];
        poll[0] = new Runnable() {
            @Override
            public void run() {
                if (mine != ticket) {
                    return;
                }
                try {
                    view.evaluateJavascript(PROBE, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (mine != ticket) {
                                return;
                            }
                            long elapsed = SystemClock.uptimeMillis() - started;
                            boolean known = "true".equals(value) || "false".equals(value);
                            boolean matches = known && "true".equals(value) == dark;
                            if (matches || elapsed >= MAX_WAIT_MS || (!known && elapsed >= UNKNOWN_WAIT_MS)) {
                                handler.postDelayed(done, SETTLE_MS);
                            } else {
                                handler.postDelayed(poll[0], POLL_MS);
                            }
                        }
                    });
                } catch (Throwable t) {
                    handler.postDelayed(done, SETTLE_MS);
                }
            }
        };
        handler.post(poll[0]);
    }
}
