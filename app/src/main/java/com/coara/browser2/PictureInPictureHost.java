package com.coara.browser2;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.util.PictureInPictureAspectRatio;
import com.coara.browser2.util.PictureInPictureSettings;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.lang.ref.WeakReference;

public final class PictureInPictureHost {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long REQUEST_TIMEOUT_MS = 6500L;
    private static WeakReference<PictureInPictureHost> current = new WeakReference<>(null);

    private final Activity activity;
    private WeakReference<BrowserView> sourceView = new WeakReference<>(null);
    private GeckoSession sourceSession;
    private boolean requestPending;
    private boolean active;
    private final Runnable requestTimeout = new Runnable() {
        @Override
        public void run() {
            GeckoSession session = sourceSession;
            if (session == null) {
                return;
            }
            PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
            boolean activityAlive = pipActivity != null && !pipActivity.isFinishing();
            if (active && activityAlive) {
                return;
            }
            BrowserView view = sourceView.get();
            if (view != null) {
                try {
                    view.rejectPictureInPictureRequest();
                } catch (Throwable ignored) {
                }
            }
            if (activityAlive) {
                pipActivity.closeForHost(session);
            }
            closePendingClone();
            clearState();
            releaseSource(session, view, true);
        }
    };

    public PictureInPictureHost(Activity activity) {
        this.activity = activity;
    }

    public static void markInternalLaunch(Activity owner, Intent intent) {
        if (owner == null || intent == null) {
            return;
        }
        ComponentName component = intent.getComponent();
        if (component != null && owner.getPackageName().equals(component.getPackageName())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        }
    }

    public static void onPlaybackStateChanged() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
                if (pipActivity != null) {
                    pipActivity.refreshPipParams();
                }
                if (host != null && host.active) {
                    host.refreshActiveState();
                }
            }
        });
    }

    public static void closeActive() {
        runOnUi(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host != null) {
                    host.onMediaEnded();
                } else {
                    PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
                    GeckoSession source = PipSessionHolder.sourceSession;
                    if (source != null) {
                        BrowserView view = PipSessionHolder.sourceView.get();
                        if (pipActivity != null) {
                            pipActivity.closeForHost(source);
                        }
                        closePendingClone();
                        PipSessionHolder.clear(source);
                        releaseSource(source, view, true);
                    }
                }
            }
        });
    }

    public static void closeForSession(final GeckoSession session) {
        if (session == null) {
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host != null && host.sourceSession == session) {
                    host.onMediaEnded();
                } else if (PipSessionHolder.sourceSession == session) {
                    PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
                    if (pipActivity != null) {
                        pipActivity.closeForHost(session);
                    }
                    closePendingClone();
                    BrowserView view = PipSessionHolder.sourceView.get();
                    PipSessionHolder.clear(session);
                    releaseSource(session, view, true);
                }
            }
        });
    }

    public static void closeForSource(final BrowserView view) {
        if (view == null) {
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host != null && host.isSource(view)) {
                    host.onMediaEnded();
                    return;
                }
                GeckoSession source = PipSessionHolder.sourceSession;
                if (source != null && PipSessionHolder.sourceView.get() == view) {
                    PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
                    if (pipActivity != null) {
                        pipActivity.closeForHost(source);
                    }
                    closePendingClone();
                    PipSessionHolder.clear(source);
                    releaseSource(source, view, true);
                }
            }
        });
    }

    private static void runOnUi(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN.post(runnable);
        }
    }

    private static void closePendingClone() {
        GeckoSession pending = PipSessionHolder.pendingSession;
        PipSessionHolder.pendingSession = null;
        PipSessionHolder.awaitingClone = false;
        if (pending == null) {
            return;
        }
        try {
            pending.setActive(false);
        } catch (Throwable ignored) {
        }
        try {
            pending.close();
        } catch (Throwable ignored) {
        }
    }

    private static void closePipActivityFor(GeckoSession session) {
        if (session == null) {
            return;
        }
        PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
        if (pipActivity != null && !pipActivity.isFinishing()) {
            pipActivity.closeForHost(session);
        }
    }

    private boolean isSource(BrowserView view) {
        BrowserView source = sourceView.get();
        return source == view || (sourceSession != null && view != null && view.getGeckoSession() == sourceSession);
    }

    public boolean isShowing() {
        return requestPending || active || (sourceSession != null && PipSessionHolder.sourceSession == sourceSession);
    }

    private boolean launchPipActivity() {
        try {
            Intent intent = new Intent(activity, PictureInPictureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void cancelRequest(GeckoSession session, BrowserView view) {
        closePipActivityFor(session);
        closePendingClone();
        clearState();
        releaseSource(session, view, false);
    }

    public boolean onUserLeaveHint(BrowserView view) {
        if (view == null || activity.isFinishing() || activity.isDestroyed()) {
            return true;
        }
        if (!PictureInPictureSettings.isSystemPipAllowed(activity)) {
            return false;
        }
        if (!view.isMediaPlaying() || PictureInPictureAspectRatio.videoState(view) < 0) {
            return true;
        }
        GeckoSession session = view.getGeckoSession();
        if (session == null) {
            return false;
        }
        PictureInPictureActivity existing = PipSessionHolder.getActiveActivity();
        if (existing != null && !existing.isFinishing()) {
            return PipSessionHolder.sourceSession == session;
        }
        if ((requestPending || active) && sourceSession == session) {
            return true;
        }
        if (PipSessionHolder.sourceSession != null && PipSessionHolder.sourceSession != session) {
            return true;
        }
        if (view.isPictureInPictureActiveOrPending()) {
            view.rejectPictureInPictureRequest();
        }
        sourceView = new WeakReference<>(view);
        sourceSession = session;
        requestPending = true;
        active = false;
        current = new WeakReference<>(this);
        MAIN.removeCallbacks(requestTimeout);
        MAIN.postDelayed(requestTimeout, REQUEST_TIMEOUT_MS);
        PipSessionHolder.setPending(null, session, activity, view, PictureInPictureAspectRatio.getCached(view));
        if (!launchPipActivity()) {
            cancelRequest(session, view);
            return false;
        }
        boolean accepted = view.requestPictureInPicture(new Runnable() {
            @Override
            public void run() {
                onPipRequestFailed(view);
            }
        });
        if (!accepted) {
            cancelRequest(session, view);
            return false;
        }
        return true;
    }

    public boolean onTokenReceived(BrowserView view, String token) {
        if (view == null || token == null || token.isEmpty() || activity.isFinishing()
                || activity.isDestroyed() || !PictureInPictureSettings.isSystemPipAllowed(activity)) {
            return false;
        }
        GeckoSession session = view.getGeckoSession();
        if (session == null || view.isDestroyed()) {
            return false;
        }
        boolean awaiting = PipSessionHolder.awaitingClone && PipSessionHolder.sourceSession == session
                && sourceSession == session;
        PictureInPictureActivity existing = PipSessionHolder.getActiveActivity();
        boolean existingAlive = existing != null && !existing.isFinishing();
        if (!awaiting) {
            if ((requestPending || active) && sourceSession != null && sourceSession != session) {
                return false;
            }
            if (existingAlive) {
                return PipSessionHolder.sourceSession == session;
            }
            if (sourceSession != null && sourceSession != session && PipSessionHolder.sourceSession != null) {
                return false;
            }
        }
        sourceSession = session;
        sourceView = new WeakReference<>(view);
        requestPending = false;
        active = true;
        current = new WeakReference<>(this);
        MAIN.removeCallbacks(requestTimeout);
        GeckoSession clone = null;
        try {
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                    .pictureInPictureToken(token)
                    .suspendMediaWhenInactive(false)
                    .build();
            clone = new GeckoSession(settings);
            clone.open(BrowserEngine.get().getRuntime());
            clone.getSettings().setSuspendMediaWhenInactive(false);
            clone.getCompositorController().setClearColor(android.graphics.Color.BLACK);
            clone.setActive(true);
            clone.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            view.setPictureInPictureActive(true);
            session.getSessionPictureInPicture().setKeepActive(true);
            if (awaiting) {
                PipSessionHolder.updateAspectRatio(PictureInPictureAspectRatio.getCached(view));
                PipSessionHolder.supplyClone(clone);
                if (existingAlive) {
                    existing.onCloneReady();
                }
            } else {
                PipSessionHolder.setPending(clone, session, activity, view, PictureInPictureAspectRatio.getCached(view));
                if (!launchPipActivity()) {
                    throw new IllegalStateException("pip activity launch failed");
                }
            }
            MAIN.postDelayed(requestTimeout, REQUEST_TIMEOUT_MS);
            refreshActiveState();
            return true;
        } catch (Throwable error) {
            if (clone != null) {
                try {
                    clone.setActive(false);
                    clone.close();
                } catch (Throwable ignored) {
                }
            }
            closePipActivityFor(session);
            closePendingClone();
            clearState();
            releaseSource(session, view, true);
            return false;
        }
    }

    private void onPipRequestFailed(BrowserView view) {
        if (!isSource(view) || active) {
            return;
        }
        GeckoSession session = sourceSession;
        closePipActivityFor(session);
        closePendingClone();
        clearState();
        if (view != null) {
            view.setPictureInPictureActive(false);
            view.setPictureInPicturePending(false);
        }
        if (session != null && PipSessionHolder.sourceSession == session) {
            PipSessionHolder.clear(session);
        }
    }

    public void onGeckoClosed(BrowserView view) {
        if (view == null) {
            return;
        }
        if (!isSource(view)) {
            view.setPictureInPictureActive(false);
            return;
        }
        GeckoSession session = sourceSession;
        PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
        if (pipActivity != null && session != null) {
            pipActivity.closeForGecko(session);
        }
        closePendingClone();
        clearState();
        releaseSource(session, view, false);
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            refreshActiveState();
        }
    }

    public void onStop() {
    }

    public void onDestroy() {
        if (requestPending && !active) {
            GeckoSession session = sourceSession;
            BrowserView view = sourceView.get();
            if (view != null) {
                try {
                    view.rejectPictureInPictureRequest();
                } catch (Throwable ignored) {
                }
            }
            closePipActivityFor(session);
            closePendingClone();
            clearState();
            releaseSource(session, view, true);
        }
    }

    public void close() {
        onMediaEnded();
    }

    private void onMediaEnded() {
        GeckoSession session = sourceSession;
        BrowserView view = sourceView.get();
        PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
        if (pipActivity != null && session != null) {
            pipActivity.closeForHost(session);
        }
        closePendingClone();
        clearState();
        releaseSource(session, view, true);
    }

    private void refreshActiveState() {
        PictureInPictureActivity pipActivity = PipSessionHolder.getActiveActivity();
        if (pipActivity != null) {
            pipActivity.refreshPipParams();
        }
    }

    private void clearState() {
        MAIN.removeCallbacks(requestTimeout);
        requestPending = false;
        active = false;
        GeckoSession oldSession = sourceSession;
        sourceSession = null;
        sourceView = new WeakReference<>(null);
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
        if (oldSession != null) {
            PipSessionHolder.clear(oldSession);
        }
    }

    private static void releaseSource(GeckoSession session, BrowserView view, boolean notifyGecko) {
        if (session != null && notifyGecko) {
            try {
                session.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
        }
        if (view != null && session != null && view.getGeckoSession() == session) {
            view.setPictureInPictureActive(false);
            view.setPictureInPicturePending(false);
        } else if (session != null && !MediaPlaybackService.isRetainedSessionPlaying(session)) {
            try {
                session.getSessionPictureInPicture().setKeepActive(false);
            } catch (Throwable ignored) {
            }
            try {
                session.getSettings().setSuspendMediaWhenInactive(true);
                session.setActive(false);
                session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
            } catch (Throwable ignored) {
            }
            MediaPlaybackService.onPipClosed(session);
        }
    }

    public static void onPipActivityClosed(GeckoSession sourceSession, BrowserView sourceView) {
        if (sourceSession == null) {
            return;
        }
        PictureInPictureHost host = current.get();
        if (host != null && host.sourceSession == sourceSession) {
            host.clearState();
        } else {
            PipSessionHolder.clear(sourceSession);
        }
        releaseSource(sourceSession, sourceView, true);
    }
}
