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
    private static final long RENDER_TIMEOUT_MS = 7000L;
    private static final long ENTER_DELAY_MS = 300L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private GeckoSession sourceSession;
    private com.coara.browser2.gecko.BrowserView sourceView;
    private android.app.Activity sourceActivity;
    private Class<?> sourceActivityClass;
    private boolean renderReady;
    private boolean firstContentfulPaintReceived;
    private boolean resumed;
    private boolean enteredPip;
    private boolean returningToSource;
    private boolean closeFromGecko;
    private boolean closeFromHost;
    private int drawCount;
    private Runnable drawCallback;
    private final Runnable renderTimeout = new Runnable() {
        @Override
        public void run() {
            if (!enteredPip && !isFinishing()) {
                finish();
            }
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
        geckoSession = PipSessionHolder.takePendingSession();
        if (geckoSession == null || sourceSession == null) {
            finish();
            return;
        }
        try {
            sourceSession.getSessionPictureInPicture().setKeepActive(true);
            sourceSession.setActive(true);
            sourceSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            geckoSession.getSettings().setSuspendMediaWhenInactive(false);
            geckoSession.setActive(true);
            geckoSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            geckoSession.getCompositorController().setClearColor(Color.BLACK);
            geckoSession.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onFirstContentfulPaint(GeckoSession session) {
                    if (session == geckoSession) {
                        firstContentfulPaintReceived = true;
                        checkRenderReady();
                    }
                }
            });
            geckoView.setSession(geckoSession);
            registerDrawCallback();
            geckoSession.loadUri("about:blank");
            geckoView.post(new Runnable() {
                @Override
                public void run() {
                    if (geckoView != null && geckoView.isAttachedToWindow()) {
                        geckoView.requestLayout();
                        geckoView.invalidate();
                    }
                }
            });
            main.postDelayed(renderTimeout, RENDER_TIMEOUT_MS);
        } catch (Throwable error) {
            finish();
        }
    }

    private void registerDrawCallback() {
        final GeckoSession session = geckoSession;
        if (session == null) {
            return;
        }
        drawCount = 0;
        Runnable callback = new Runnable() {
            @Override
            public void run() {
                if (geckoSession != session || isFinishing() || enteredPip) {
                    return;
                }
                drawCount++;
                checkRenderReady();
            }
        };
        drawCallback = callback;
        try {
            session.getCompositorController().addDrawCallback(callback);
        } catch (Throwable ignored) {
        }
    }

    private void checkRenderReady() {
        if (renderReady || isFinishing() || !firstContentfulPaintReceived || drawCount < 2) {
            return;
        }
        renderReady = true;
        if (drawCallback != null && geckoSession != null) {
            try {
                geckoSession.getCompositorController().removeDrawCallback(drawCallback);
            } catch (Throwable ignored) {
            }
            drawCallback = null;
        }
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
        if (returningToSource) {
            returningToSource = false;
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
        main.removeCallbacks(renderTimeout);
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
        if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            main.removeCallbacks(renderTimeout);
            finish();
        } else {
            returningToSource = true;
        }
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
        main.removeCallbacks(renderTimeout);
        if (drawCallback != null && geckoSession != null) {
            try {
                geckoSession.getCompositorController().removeDrawCallback(drawCallback);
            } catch (Throwable ignored) {
            }
            drawCallback = null;
        }
        GeckoSession source = sourceSession;
        if (source != null && !closeFromGecko && !closeFromHost) {
            PictureInPictureHost.onPipActivityClosed(source, sourceView);
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
