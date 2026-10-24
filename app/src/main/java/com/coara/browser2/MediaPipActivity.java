package com.coara.browser2;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.PictureInPictureController;

import java.lang.ref.WeakReference;

public final class MediaPipActivity extends Activity {
    private static final String EXTRA_PLACEMENT_TOKEN = "pip_placement_token";
    private static WeakReference<MediaPipActivity> activeActivity = new WeakReference<>(null);
    private static WeakReference<BrowserView> pendingSource = new WeakReference<>(null);
    private BrowserView sourceView;
    private GeckoView pipView;
    private GeckoSession pipSession;
    private boolean enteredPip;
    private boolean closing;

    public static boolean launch(Context context, BrowserView source, String placementToken) {
        if (context == null || source == null || placementToken == null || placementToken.isEmpty()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        MediaPipActivity existing = activeActivity.get();
        if (existing != null && !existing.isFinishing()) {
            existing.finishAndClose();
        }
        pendingSource = new WeakReference<>(source);
        Intent intent = new Intent(context, MediaPipActivity.class)
                .putExtra(EXTRA_PLACEMENT_TOKEN, placementToken)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            pendingSource = new WeakReference<>(null);
            source.setPictureInPictureActive(false);
            return false;
        }
    }

    public static void closeForSource(BrowserView source) {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null && activity.sourceView == source) {
            activity.finishAndClose();
        }
    }

    public static void closeActive() {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null) {
            activity.runOnUiThread(activity::finishAndClose);
        }
        pendingSource = new WeakReference<>(null);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            finishAndClose();
            return;
        }
        sourceView = pendingSource.get();
        pendingSource = new WeakReference<>(null);
        if (sourceView == null) {
            finishAndClose();
            return;
        }
        String token = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PLACEMENT_TOKEN);
        if (token == null || token.isEmpty() || !com.coara.browser2.gecko.BrowserEngine.isInitialized()) {
            finishAndClose();
            return;
        }
        activeActivity = new WeakReference<>(this);
        pipView = new GeckoView(this);
        pipView.setFocusable(true);
        pipView.setFocusableInTouchMode(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(pipView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        try {
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                    .pictureInPictureToken(token)
                    .suspendMediaWhenInactive(false)
                    .build();
            pipSession = new GeckoSession(settings);
            pipSession.setPictureInPictureDelegate(new PictureInPictureController.SessionPictureInPicture.Delegate() {
                @Override
                public void onExitPictureInPicture(GeckoSession session) {
                    runOnUiThread(MediaPipActivity.this::finishAndClose);
                }
            });
            pipSession.open(BrowserEngine.get().getRuntime());
            pipView.setSession(pipSession);
            pipSession.getSessionPictureInPicture().setKeepActive(true);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            setPictureInPictureParams(params);
            pipView.post(() -> {
                if (isFinishing() || closing || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    return;
                }
                try {
                    if (enterPictureInPictureMode(params)) {
                        enteredPip = true;
                    } else {
                        finishAndClose();
                    }
                } catch (RuntimeException error) {
                    finishAndClose();
                }
            });
        } catch (Throwable error) {
            finishAndClose();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            enteredPip = true;
        } else if (enteredPip) {
            finishAndClose();
        }
    }

    @Override
    public void onBackPressed() {
        finishAndClose();
    }

    private void finishAndClose() {
        if (closing) {
            if (!isFinishing()) {
                finish();
            }
            return;
        }
        closing = true;
        GeckoSession session = pipSession;
        pipSession = null;
        if (session != null) {
            try {
                session.getSessionPictureInPicture().setKeepActive(false);
            } catch (Throwable ignored) {
            }
            try {
                session.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
            if (pipView != null) {
                try {
                    pipView.releaseSession();
                } catch (Throwable ignored) {
                }
            }
            try {
                session.close();
            } catch (Throwable ignored) {
            }
        }
        if (sourceView != null) {
            sourceView.setPictureInPictureActive(false);
            sourceView = null;
        }
        if (activeActivity.get() == this) {
            activeActivity = new WeakReference<>(null);
        }
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        finishAndClose();
        super.onDestroy();
    }
}
