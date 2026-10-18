package com.coara.browser2.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ErrorPages;

import java.util.List;
import java.util.function.Supplier;

public final class NetworkStateGuard {

    private final Context context;
    private final Supplier<List<BrowserView>> views;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback callback;

    public NetworkStateGuard(Context context, Supplier<List<BrowserView>> views) {
        this.context = context.getApplicationContext();
        this.views = views;
    }

    public void start() {
        if (callback != null) {
            return;
        }
        try {
            manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return;
            }
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    markOnline();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        markOnline();
                    }
                }
            };
            manager.registerDefaultNetworkCallback(callback);
        } catch (Throwable t) {
            callback = null;
        }
    }

    public void stop() {
        if (callback == null || manager == null) {
            return;
        }
        try {
            manager.unregisterNetworkCallback(callback);
        } catch (Throwable ignored) {
        }
        callback = null;
    }

    private void markOnline() {
        main.removeCallbacks(apply);
        main.postDelayed(apply, 400L);
    }

    private final Runnable apply = new Runnable() {
        @Override
        public void run() {
            List<BrowserView> list;
            try {
                list = views.get();
            } catch (Throwable t) {
                return;
            }
            if (list == null) {
                return;
            }
            for (BrowserView w : list) {
                if (w == null) {
                    continue;
                }
                try {
                    String failed = ErrorPages.failedUrlFor(w.getUrl());
                    if (failed != null && w.hasSession() && !w.isDestroyed()) {
                        w.loadUrl(failed);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    };
}
