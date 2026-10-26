package com.coara.browser2;

import android.app.Activity;
import android.util.Rational;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.util.PictureInPictureAspectRatio;

import org.mozilla.geckoview.GeckoSession;

import java.lang.ref.WeakReference;

final class PipSessionHolder {
    static GeckoSession pendingSession;
    static GeckoSession sourceSession;
    static boolean awaitingClone;
    static WeakReference<Activity> sourceActivity = new WeakReference<>(null);
    static Class<?> sourceActivityClass;
    static WeakReference<BrowserView> sourceView = new WeakReference<>(null);
    static WeakReference<PictureInPictureActivity> activeActivity = new WeakReference<>(null);
    static Rational aspectRatio = PictureInPictureAspectRatio.defaultAspectRatio();

    private PipSessionHolder() {
    }

    static void setPending(GeckoSession pipSession, GeckoSession originalSession, Activity activity,
                           BrowserView view, Rational ratio) {
        pendingSession = pipSession;
        awaitingClone = pipSession == null;
        sourceSession = originalSession;
        sourceActivity = new WeakReference<>(activity);
        sourceActivityClass = activity == null ? null : activity.getClass();
        sourceView = new WeakReference<>(view);
        aspectRatio = ratio == null ? PictureInPictureAspectRatio.defaultAspectRatio() : ratio;
    }

    static boolean supplyClone(GeckoSession expectedSource, GeckoSession pipSession) {
        if (expectedSource == null || sourceSession != expectedSource || pipSession == null) {
            return false;
        }
        pendingSession = pipSession;
        awaitingClone = false;
        return true;
    }

    static GeckoSession takePendingSession(GeckoSession expectedSource) {
        if (expectedSource == null || sourceSession != expectedSource) {
            return null;
        }
        GeckoSession session = pendingSession;
        pendingSession = null;
        return session;
    }

    static void updateAspectRatio(Rational ratio) {
        if (ratio != null) {
            aspectRatio = ratio;
        }
    }

    static void clear(GeckoSession expectedSource) {
        if (expectedSource != null && sourceSession != expectedSource) {
            return;
        }
        pendingSession = null;
        awaitingClone = false;
        sourceSession = null;
        sourceActivity = new WeakReference<>(null);
        sourceActivityClass = null;
        sourceView = new WeakReference<>(null);
        aspectRatio = PictureInPictureAspectRatio.defaultAspectRatio();
    }

    static PictureInPictureActivity getActiveActivity() {
        return activeActivity.get();
    }
}
