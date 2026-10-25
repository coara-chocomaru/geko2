package com.coara.browser2;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.util.PictureInPictureAspectRatio;
import com.coara.browser2.util.PictureInPictureSettings;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.PictureInPictureController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class PictureInPictureHost {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long TOKEN_TIMEOUT_MS = 6000L;
    private static WeakReference<PictureInPictureHost> current = new WeakReference<>(null);

    private final Activity activity;
    private final Runnable tokenTimeout = new Runnable() {
        @Override
        public void run() {
            onTokenTimeout();
        }
    };
    private WeakReference<BrowserView> sourceView = new WeakReference<>(null);
    private GeckoSession sourceSession;
    private FrameLayout overlay;
    private TextView statusView;
    private GeckoView pipView;
    private GeckoSession pipSession;
    private String pendingToken;
    private boolean entering;
    private boolean active;

    public PictureInPictureHost(Activity activity) {
        this.activity = activity;
    }

    public static void onPlaybackStateChanged() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host != null) {
                    host.refreshParams();
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

    private boolean isSource(BrowserView view) {
        BrowserView source = sourceView.get();
        return source == view || (sourceSession != null && view.getGeckoSession() == sourceSession);
    }

    public boolean isShowing() {
        return overlay != null;
    }

    public boolean onUserLeaveHint(BrowserView view) {
        if (view == null || overlay != null || activity.isFinishing() || !PictureInPictureSettings.isSystemPipAllowed(activity)
                || activity.isInPictureInPictureMode()) {
            return false;
        }
        if (!view.isMediaPlaying() || PictureInPictureAspectRatio.videoState(view) < 0) {
            return false;
        }
        if (view.isPictureInPictureActiveOrPending()) {
            view.rejectPictureInPictureRequest();
        }
        if (!begin(view)) {
            return false;
        }
        if (!view.requestPictureInPicture()) {
            release(false);
            return false;
        }
        return true;
    }

    public boolean onTokenReceived(BrowserView view, String token) {
        if (view == null || token == null || token.isEmpty() || activity.isFinishing()
                || !PictureInPictureSettings.isSystemPipAllowed(activity)) {
            return false;
        }
        if (overlay == null && !begin(view)) {
            return false;
        }
        pendingToken = token;
        if (activity.isInPictureInPictureMode()) {
            attachClone();
        }
        return true;
    }

    public void onGeckoClosed(BrowserView view) {
        if (view == null) {
            return;
        }
        if (overlay == null || !isSource(view)) {
            view.setPictureInPictureActive(false);
            return;
        }
        onMediaEnded();
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            if (overlay == null) {
                return;
            }
            entering = false;
            active = true;
            current = new WeakReference<>(this);
            MAIN.removeCallbacks(tokenTimeout);
            if (pipSession == null) {
                MAIN.postDelayed(tokenTimeout, TOKEN_TIMEOUT_MS);
            }
            attachClone();
            refreshParams();
            return;
        }
        if (overlay == null && !active && !entering) {
            return;
        }
        release(!isActivityVisible());
    }

    public void onStop() {
        if (overlay != null && !activity.isInPictureInPictureMode()) {
            release(active);
        }
    }

    public void onDestroy() {
        if (overlay != null || active || entering) {
            release(active && !activity.isChangingConfigurations());
        }
    }

    public void close() {
        if (overlay != null) {
            onMediaEnded();
        }
    }

    private boolean isActivityVisible() {
        if (activity instanceof LifecycleOwner) {
            return ((LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        }
        return true;
    }

    private boolean begin(BrowserView view) {
        GeckoSession session = view.getGeckoSession();
        if (session == null) {
            return false;
        }
        sourceView = new WeakReference<>(view);
        sourceSession = session;
        showCover();
        boolean entered;
        try {
            entered = activity.enterPictureInPictureMode(buildParams());
        } catch (RuntimeException error) {
            entered = false;
        }
        if (!entered) {
            removeOverlay();
            sourceView = new WeakReference<>(null);
            sourceSession = null;
            return false;
        }
        entering = true;
        current = new WeakReference<>(this);
        return true;
    }

    private void showCover() {
        if (overlay != null) {
            return;
        }
        FrameLayout layer = new FrameLayout(activity);
        layer.setBackgroundColor(Color.BLACK);
        layer.setClickable(true);
        TextView status = new TextView(activity);
        status.setTextColor(0xFFB0B0B0);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        status.setGravity(Gravity.CENTER);
        status.setText("小窓再生を準備中");
        layer.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.addContentView(layer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = layer;
        statusView = status;
    }

    private void showStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
            statusView.setVisibility(View.VISIBLE);
        }
    }

    private void attachClone() {
        String token = pendingToken;
        if (token == null || token.isEmpty() || overlay == null || pipSession != null) {
            return;
        }
        pendingToken = null;
        try {
            GeckoView view = new GeckoView(activity);
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                    .pictureInPictureToken(token)
                    .suspendMediaWhenInactive(false)
                    .build();
            GeckoSession session = new GeckoSession(settings);
            session.setPictureInPictureDelegate(new PictureInPictureController.SessionPictureInPicture.Delegate() {
                @Override
                public void onExitPictureInPicture(GeckoSession exited) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (pipSession == exited) {
                                onMediaEnded();
                            }
                        }
                    });
                }
            });
            session.open(BrowserEngine.get().getRuntime());
            pipView = view;
            pipSession = session;
            view.setSession(session);
            session.getSessionPictureInPicture().setKeepActive(true);
            try {
                session.getCompositorController().onPipModeChanged(true);
            } catch (Throwable ignored) {
            }
            overlay.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (statusView != null) {
                statusView.setVisibility(View.GONE);
            }
            MAIN.removeCallbacks(tokenTimeout);
        } catch (Throwable error) {
            detachClone();
            showStatus("この動画は小窓に表示できません");
        }
    }

    private void detachClone() {
        GeckoSession session = pipSession;
        GeckoView view = pipView;
        pipSession = null;
        pipView = null;
        if (session != null) {
            try {
                session.getSessionPictureInPicture().setKeepActive(false);
            } catch (Throwable ignored) {
            }
            try {
                session.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
        }
        if (view != null) {
            try {
                view.releaseSession();
            } catch (Throwable ignored) {
            }
            ViewGroup parent = view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
            if (parent != null) {
                parent.removeView(view);
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void onTokenTimeout() {
        if (overlay != null && pipSession == null) {
            pendingToken = null;
            showStatus("この動画は小窓に表示できません。音声は再生を続けます");
            BrowserView view = sourceView.get();
            if (view != null) {
                view.setPictureInPictureActive(false);
            }
        }
    }

    private void onMediaEnded() {
        if (overlay == null) {
            return;
        }
        if (!active) {
            release(false);
            return;
        }
        MAIN.removeCallbacks(tokenTimeout);
        pendingToken = null;
        detachClone();
        showStatus("再生が終了しました");
        releaseSource();
        refreshParams();
    }

    private void releaseSource() {
        BrowserView view = sourceView.get();
        GeckoSession session = sourceSession;
        if (session != null) {
            try {
                session.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
        }
        if (view != null && session != null && view.getGeckoSession() == session) {
            view.setPictureInPictureActive(false);
        } else if (session != null && !MediaPlaybackService.isRetainedSessionPlaying(session)) {
            try {
                session.getSessionPictureInPicture().setKeepActive(false);
            } catch (Throwable ignored) {
            }
            try {
                session.setActive(false);
            } catch (Throwable ignored) {
            }
            MediaPlaybackService.onPipClosed(session);
        }
    }

    private void release(boolean pauseMedia) {
        MAIN.removeCallbacks(tokenTimeout);
        pendingToken = null;
        detachClone();
        removeOverlay();
        releaseSource();
        sourceView = new WeakReference<>(null);
        sourceSession = null;
        entering = false;
        active = false;
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
        if (pauseMedia) {
            MediaPlaybackService.pauseActive();
        }
    }

    private void removeOverlay() {
        FrameLayout layer = overlay;
        overlay = null;
        statusView = null;
        if (layer != null && layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
    }

    private void refreshParams() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        try {
            activity.setPictureInPictureParams(buildParams());
        } catch (RuntimeException ignored) {
        }
    }

    private PictureInPictureParams buildParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        BrowserView view = sourceView.get();
        builder.setAspectRatio(view == null ? PictureInPictureAspectRatio.defaultAspectRatio()
                : PictureInPictureAspectRatio.getCached(view));
        List<RemoteAction> actions = buildActions();
        if (!actions.isEmpty()) {
            builder.setActions(actions);
        }
        return builder.build();
    }

    private List<RemoteAction> buildActions() {
        List<RemoteAction> actions = new ArrayList<>(3);
        int max;
        try {
            max = activity.getMaxNumPictureInPictureActions();
        } catch (RuntimeException error) {
            max = 3;
        }
        if (max <= 0) {
            return actions;
        }
        boolean playing = MediaPlaybackService.isPlaying();
        RemoteAction toggle = remoteAction(
                playing ? R.drawable.ic_pip_pause : R.drawable.ic_pip_play,
                playing ? "一時停止" : "再生",
                playing ? MediaPlaybackService.ACTION_PAUSE : MediaPlaybackService.ACTION_PLAY,
                playing ? 7422 : 7423);
        if (max >= 3) {
            actions.add(remoteAction(R.drawable.ic_pip_rewind, "巻き戻し", MediaPlaybackService.ACTION_SEEK_BACK, 7421));
            actions.add(toggle);
            actions.add(remoteAction(R.drawable.ic_pip_forward, "早送り", MediaPlaybackService.ACTION_SEEK_FORWARD, 7424));
        } else {
            actions.add(toggle);
        }
        return actions;
    }

    private RemoteAction remoteAction(int icon, String label, String action, int requestCode) {
        return new RemoteAction(Icon.createWithResource(activity, icon), label, label,
                MediaPlaybackService.controlIntent(activity, action, requestCode));
    }
}
