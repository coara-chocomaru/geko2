package com.coara.browser2;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.util.PictureInPictureAspectRatio;
import com.coara.browser2.util.PictureInPictureLayout;
import com.coara.browser2.util.PictureInPictureSettings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

import java.util.function.Consumer;

public final class PictureInPictureHost {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DISMISS_CHECK_DELAY_MS = 350L;
    private static final long ENTER_TIMEOUT_MS = 2500L;
    private static final long COMPOSITOR_KICK_DELAY_MS = 400L;
    private static final long FIRST_PROBE_DELAY_MS = 1500L;
    private static WeakReference<PictureInPictureHost> current = new WeakReference<>(null);

    private final Activity activity;
    private final Consumer<Boolean> chrome;
    private WeakReference<BrowserView> sourceView = new WeakReference<>(null);
    private boolean entering;
    private boolean inPip;
    private boolean chromeApplied;
    private boolean pipMediaEnded;
    private final Runnable probeRunnable = new Runnable() {
        @Override
        public void run() {
            runProbe();
        }
    };
    private final Runnable dismissCheck = new Runnable() {
        @Override
        public void run() {
            finishExit();
        }
    };
    private final Runnable compositorKick = new Runnable() {
        @Override
        public void run() {
            BrowserView view = sourceView.get();
            if (inPip && view != null && !view.isDestroyed()) {
                keepSessionActive(view);
                notifyCompositor(view, true);
            }
        }
    };
    private final Runnable enterTimeout = new Runnable() {
        @Override
        public void run() {
            if (entering && !inPip) {
                abortEnter();
            }
        }
    };

    public PictureInPictureHost(Activity activity, Consumer<Boolean> chrome) {
        this.activity = activity;
        this.chrome = chrome;
    }

    public static void markInternalLaunch(Activity owner, Intent intent, int requestCode) {
        if (owner == null || intent == null) {
            return;
        }
        if (requestCode >= 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            return;
        }
        ComponentName component = intent.getComponent();
        if (component != null && owner.getPackageName().equals(component.getPackageName())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        }
    }

    public static boolean isPipShowing() {
        PictureInPictureHost host = current.get();
        return host != null && host.inPip;
    }

    public static void notePipMediaEnded() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host != null && (host.inPip || host.entering)) {
                    host.pipMediaEnded = true;
                }
            }
        });
    }

    public static void playPipMediaIfEnded(final Runnable playAction) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                BrowserView view = host == null ? null : host.sourceView.get();
                if (host == null || !host.inPip || !host.pipMediaEnded || view == null || view.isDestroyed()) {
                    runSeekFallback(playAction);
                    return;
                }
                host.pipMediaEnded = false;
                PictureInPictureLayout.restartPosition(view, value -> runSeekFallback(playAction));
            }
        });
    }

    public static void seekPipMedia(final int seconds, final Runnable fallback) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                BrowserView view = host == null ? null : host.sourceView.get();
                if (host == null || !host.inPip || view == null || view.isDestroyed()) {
                    runSeekFallback(fallback);
                    return;
                }
                host.pipMediaEnded = false;
                PictureInPictureLayout.seekBy(view, seconds, value -> {
                    if (!"true".equals(value)) {
                        runSeekFallback(fallback);
                    }
                });
            }
        });
    }

    private static void runSeekFallback(Runnable fallback) {
        if (fallback == null) {
            return;
        }
        try {
            fallback.run();
        } catch (RuntimeException ignored) {
        }
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
                    host.closeWindow();
                }
            }
        });
    }

    public static void closeForSession(final org.mozilla.geckoview.GeckoSession session) {
        if (session == null) {
            return;
        }
        runOnUi(new Runnable() {
            @Override
            public void run() {
                PictureInPictureHost host = current.get();
                if (host == null) {
                    return;
                }
                BrowserView view = host.sourceView.get();
                if (view != null && view.getGeckoSession() == session) {
                    host.closeWindow();
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
                if (host != null && host.sourceView.get() == view) {
                    host.closeWindow();
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

    public boolean isShowing() {
        return entering || inPip;
    }

    public boolean onUserLeaveHint(BrowserView view) {
        if (view == null || activity.isFinishing() || activity.isDestroyed()) {
            return true;
        }
        if (inPip || entering) {
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !PictureInPictureSettings.isSystemPipAllowed(activity)) {
            return false;
        }
        boolean mediaPlaying = view.isMediaPlaying();
        boolean videoPlaying = PictureInPictureAspectRatio.isVideoPlaying(view);
        if (!mediaPlaying && !videoPlaying) {
            return true;
        }
        if (!videoPlaying && PictureInPictureAspectRatio.videoState(view) < 0) {
            Toast.makeText(activity.getApplicationContext(), "動画を検出できないため小窓を表示しません",
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return enter(view);
    }

    public boolean onTokenReceived(BrowserView view, String token) {
        if (view != null) {
            view.rejectPictureInPictureRequest();
        }
        return true;
    }

    public void onGeckoClosed(BrowserView view) {
        if (view != null && !inPip && !entering) {
            view.setPictureInPictureActive(false);
        }
    }

    private boolean enter(BrowserView view) {
        sourceView = new WeakReference<>(view);
        current = new WeakReference<>(this);
        entering = true;
        pipMediaEnded = false;
        view.setPictureInPictureActive(true);
        keepSessionActive(view);
        PictureInPictureLayout.enter(view);
        PictureInPictureAspectRatio.query(view, true, ratio -> refreshParams());
        boolean accepted;
        try {
            accepted = activity.enterPictureInPictureMode(buildParams(view));
        } catch (RuntimeException error) {
            accepted = false;
        }
        if (!accepted) {
            abortEnter();
            return false;
        }
        MAIN.removeCallbacks(enterTimeout);
        MAIN.postDelayed(enterTimeout, ENTER_TIMEOUT_MS);
        return true;
    }

    private void abortEnter() {
        MAIN.removeCallbacks(enterTimeout);
        MAIN.removeCallbacks(compositorKick);
        BrowserView view = sourceView.get();
        entering = false;
        inPip = false;
        if (view != null) {
            notifyCompositor(view, false);
            PictureInPictureLayout.exit(view);
            view.setPictureInPictureActive(false);
        }
        sourceView = new WeakReference<>(null);
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            MAIN.removeCallbacks(enterTimeout);
            MAIN.removeCallbacks(dismissCheck);
            BrowserView view = sourceView.get();
            if (view == null || view.isDestroyed()) {
                return;
            }
            entering = false;
            inPip = true;
            current = new WeakReference<>(this);
            setChrome(true);
            view.setPictureInPictureActive(true);
            keepSessionActive(view);
            PictureInPictureLayout.enter(view);
            notifyCompositor(view, true);
            refreshParams();
            MAIN.removeCallbacks(compositorKick);
            MAIN.postDelayed(compositorKick, COMPOSITOR_KICK_DELAY_MS);
            MAIN.removeCallbacks(probeRunnable);
            MAIN.postDelayed(probeRunnable, FIRST_PROBE_DELAY_MS);
            return;
        }
        if (!inPip && !entering) {
            return;
        }
        entering = false;
        inPip = false;
        pipMediaEnded = false;
        setChrome(false);
        MAIN.removeCallbacks(compositorKick);
        MAIN.removeCallbacks(probeRunnable);
        BrowserView view = sourceView.get();
        if (view != null) {
            notifyCompositor(view, false);
            PictureInPictureLayout.exit(view);
        }
        MAIN.removeCallbacks(dismissCheck);
        MAIN.postDelayed(dismissCheck, DISMISS_CHECK_DELAY_MS);
    }

    private void runProbe() {
        if (!inPip) {
            return;
        }
        final BrowserView view = sourceView.get();
        if (view == null || view.isDestroyed()) {
            return;
        }
        PictureInPictureLayout.probe(view, value -> MAIN.post(new Runnable() {
            @Override
            public void run() {
                onProbe(view, value);
            }
        }));
    }

    private void onProbe(BrowserView view, String value) {
        if (!inPip || view.isDestroyed()) {
            return;
        }
        try {
            JSONObject result = new JSONObject(value);
            if (!result.optBoolean("nv", false) && !result.has("err")) {
                pipMediaEnded = pipMediaEnded || result.optBoolean("e", false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void keepSessionActive(BrowserView view) {
        org.mozilla.geckoview.GeckoSession session = view.getGeckoSession();
        if (session == null) {
            return;
        }
        try {
            session.getSessionPictureInPicture().setKeepActive(true);
            session.getSettings().setSuspendMediaWhenInactive(false);
            session.setActive(true);
            session.setPriorityHint(org.mozilla.geckoview.GeckoSession.PRIORITY_HIGH);
        } catch (Throwable ignored) {
        }
    }

    private static void notifyCompositor(BrowserView view, boolean enabled) {
        org.mozilla.geckoview.GeckoSession session = view.getGeckoSession();
        if (session == null) {
            return;
        }
        try {
            session.getCompositorController().onPipModeChanged(enabled);
        } catch (Throwable ignored) {
        }
    }

    private void finishExit() {
        BrowserView view = sourceView.get();
        boolean expanded = isActivityStarted();
        sourceView = new WeakReference<>(null);
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
        if (view == null) {
            return;
        }
        if (!expanded) {
            MediaPlaybackService.pauseActive();
        }
        view.setPictureInPictureActive(false);
    }

    private boolean isActivityStarted() {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        if (activity instanceof LifecycleOwner) {
            return ((LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        }
        return activity.hasWindowFocus();
    }

    private void setChrome(boolean pip) {
        if (pip == chromeApplied) {
            return;
        }
        chromeApplied = pip;
        if (chrome != null) {
            chrome.accept(pip);
        }
    }

    public void onStop() {
    }

    public void onDestroy() {
        MAIN.removeCallbacks(enterTimeout);
        MAIN.removeCallbacks(dismissCheck);
        MAIN.removeCallbacks(compositorKick);
        MAIN.removeCallbacks(probeRunnable);
        BrowserView view = sourceView.get();
        boolean actualPip = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                actualPip = activity.isInPictureInPictureMode();
            } catch (RuntimeException ignored) {
            }
        }
        if (view != null && (inPip || entering) && !actualPip) {
            view.setPictureInPictureActive(false);
        }
        entering = false;
        inPip = false;
        chromeApplied = false;
        sourceView = new WeakReference<>(null);
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
    }

    public void close() {
        closeWindow();
    }

    private void closeWindow() {
        if (!inPip && !entering) {
            return;
        }
        if (inPip && !activity.isFinishing() && !activity.isDestroyed()) {
            try {
                activity.moveTaskToBack(true);
            } catch (RuntimeException ignored) {
            }
        } else {
            abortEnter();
        }
    }

    private void refreshParams() {
        if ((!inPip && !entering) || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        BrowserView view = sourceView.get();
        if (view == null) {
            return;
        }
        try {
            activity.setPictureInPictureParams(buildParams(view));
        } catch (RuntimeException ignored) {
        }
    }

    private PictureInPictureParams buildParams(BrowserView view) {
        Rational ratio = PictureInPictureAspectRatio.getCached(view);
        if (ratio == null) {
            ratio = PictureInPictureAspectRatio.defaultAspectRatio();
        }
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder().setAspectRatio(ratio);
        List<RemoteAction> actions = buildActions();
        if (!actions.isEmpty()) {
            builder.setActions(actions);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false);
            builder.setSeamlessResizeEnabled(true);
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
