package com.coara.browser2;

import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.List;

public final class PictureInPictureActivity extends AppCompatActivity {
    private static final long ENTER_DELAY_MS = 150L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private GeckoSession sourceSession;
    private com.coara.browser2.gecko.BrowserView sourceView;
    private android.app.Activity sourceActivity;
    private Class<?> sourceActivityClass;
    private boolean renderReady;
    private boolean resumed;
    private boolean enteredPip;
    private boolean pipExitPending;
    private boolean closeFromGecko;
    private boolean closeFromHost;
    private final Runnable pipExitCheck = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || isInPictureInPictureMode()) {
                return;
            }
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                pipExitPending = false;
                returnToSource();
            }
            finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(Color.BLACK);
        geckoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(geckoView);
        setContentView(root);
        PipSessionHolder.activeActivity = new java.lang.ref.WeakReference<>(this);
        sourceSession = PipSessionHolder.sourceSession;
        sourceView = PipSessionHolder.sourceView.get();
        sourceActivity = PipSessionHolder.sourceActivity.get();
        sourceActivityClass = PipSessionHolder.sourceActivityClass;
        if (sourceSession == null) {
            finish();
            return;
        }
        try {
            sourceSession.getSessionPictureInPicture().setKeepActive(true);
            sourceSession.setActive(true);
            sourceSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        } catch (Throwable error) {
            finish();
            return;
        }
        GeckoSession clone = PipSessionHolder.takePendingSession(sourceSession);
        if (clone != null) {
            attachClone(clone);
        }
    }

    public void onCloneReady() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        GeckoSession clone = PipSessionHolder.takePendingSession(sourceSession);
        if (clone != null) {
            attachClone(clone);
        }
    }

    private void attachClone(GeckoSession clone) {
        if (clone == null) {
            return;
        }
        if (geckoSession != null || geckoView == null || isFinishing()) {
            try {
                clone.setActive(false);
                clone.close();
            } catch (Throwable ignored) {
            }
            return;
        }
        geckoSession = clone;
        try {
            geckoSession.getSettings().setSuspendMediaWhenInactive(false);
            geckoSession.getSessionPictureInPicture().setKeepActive(true);
            geckoSession.setActive(true);
            geckoSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            geckoSession.getCompositorController().setClearColor(Color.BLACK);
            geckoView.setSession(geckoSession);
            geckoView.post(new Runnable() {
                @Override
                public void run() {
                    if (geckoSession != clone || geckoView == null || isFinishing()) {
                        return;
                    }
                    if (!geckoView.isAttachedToWindow()) {
                        main.postDelayed(this, 50L);
                        return;
                    }
                    geckoView.requestLayout();
                    geckoView.invalidate();
                    checkRenderReady();
                }
            });
        } catch (Throwable error) {
            finish();
        }
    }

    private void checkRenderReady() {
        if (renderReady || isFinishing() || geckoSession == null || geckoView == null) {
            return;
        }
        if (!geckoView.isAttachedToWindow()) {
            geckoView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkRenderReady();
                }
            }, 50L);
            return;
        }
        renderReady = true;
        if (resumed) {
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    enterPipIfReady();
                }
            }, ENTER_DELAY_MS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (pipExitPending) {
            pipExitPending = false;
            main.removeCallbacks(pipExitCheck);
            returnToSource();
            finish();
            return;
        }
        enterPipIfReady();
    }

    private void enterPipIfReady() {
        if (!resumed || !renderReady || enteredPip || isFinishing()) {
            return;
        }
        enteredPip = true;
        try {
            if (!enterPictureInPictureMode(buildPipParams())) {
                enteredPip = false;
                finish();
            }
        } catch (RuntimeException error) {
            enteredPip = false;
            finish();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPipIfReady();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (inPip) {
            updateCompositorPipMode(true);
            return;
        }
        updateCompositorPipMode(false);
        if (!enteredPip) {
            return;
        }
        pipExitPending = true;
        main.removeCallbacks(pipExitCheck);
        main.postDelayed(pipExitCheck, 180L);
    }

    private void updateCompositorPipMode(boolean enabled) {
        GeckoSession session = geckoSession;
        GeckoView view = geckoView;
        if (session == null || view == null) {
            return;
        }
        view.post(new Runnable() {
            @Override
            public void run() {
                if (geckoSession != session || geckoView != view || !view.isAttachedToWindow()) {
                    return;
                }
                try {
                    session.getCompositorController().setClearColor(Color.BLACK);
                    session.getCompositorController().onPipModeChanged(enabled);
                    if (enabled) {
                        session.getSettings().setSuspendMediaWhenInactive(false);
                        session.getSessionPictureInPicture().setKeepActive(true);
                        session.setActive(true);
                        session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
                    }
                    view.requestLayout();
                    view.invalidate();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public void closeForGecko(GeckoSession session) {
        if (sourceSession != session || isFinishing()) {
            return;
        }
        closeFromGecko = true;
        sourceSession = null;
        finish();
    }

    public void closeForHost(GeckoSession session) {
        if (sourceSession != session || isFinishing()) {
            return;
        }
        closeFromHost = true;
        sourceSession = null;
        finish();
    }

    public void refreshPipParams() {
        if (isFinishing()) {
            return;
        }
        try {
            setPictureInPictureParams(buildPipParams());
        } catch (RuntimeException ignored) {
        }
    }

    private PictureInPictureParams buildPipParams() {
        Rational ratio = PipSessionHolder.aspectRatio;
        if (ratio == null) {
            ratio = new Rational(16, 9);
        }
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder().setAspectRatio(ratio);
        List<RemoteAction> actions = buildActions();
        if (!actions.isEmpty()) {
            builder.setActions(actions);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false);
            builder.setSeamlessResizeEnabled(false);
        }
        return builder.build();
    }

    private List<RemoteAction> buildActions() {
        List<RemoteAction> actions = new ArrayList<>(3);
        int max;
        try {
            max = getMaxNumPictureInPictureActions();
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
        return new RemoteAction(Icon.createWithResource(this, icon), label, label,
                MediaPlaybackService.controlIntent(this, action, requestCode));
    }

    private void returnToSource() {
        Class<?> target = sourceActivity != null ? sourceActivity.getClass() : sourceActivityClass;
        if (target == null) {
            return;
        }
        try {
            Intent intent = new Intent(this, target);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        pipExitPending = false;
        GeckoSession source = sourceSession;
        if (source != null && !closeFromGecko && !closeFromHost) {
            PictureInPictureHost.onPipActivityClosed(source, sourceView);
        }
        GeckoSession leftover = source != null ? PipSessionHolder.takePendingSession(source) : null;
        if (leftover != null) {
            try {
                leftover.setActive(false);
                leftover.close();
            } catch (Throwable ignored) {
            }
        }
        GeckoSession clone = geckoSession;
        GeckoView view = geckoView;
        geckoSession = null;
        geckoView = null;
        sourceSession = null;
        if (view != null) {
            try {
                view.releaseSession();
            } catch (Throwable ignored) {
            }
        }
        if (clone != null) {
            try {
                clone.getCompositorController().onPipModeChanged(false);
            } catch (Throwable ignored) {
            }
            try {
                clone.getSessionPictureInPicture().setKeepActive(false);
            } catch (Throwable ignored) {
            }
            try {
                clone.setActive(false);
                clone.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
            } catch (Throwable ignored) {
            }
            try {
                clone.close();
            } catch (Throwable ignored) {
            }
        }
        if (PipSessionHolder.getActiveActivity() == this) {
            PipSessionHolder.activeActivity = new java.lang.ref.WeakReference<>(null);
        }
        if (source != null) {
            PipSessionHolder.clear(source);
        }
        super.onDestroy();
    }
}
