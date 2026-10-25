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
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

import java.lang.ref.WeakReference;

public final class MediaPipActivity extends AppCompatActivity {
    public static final String ACTION_RETURN_TO_SOURCE = "com.coara.browser2.action.RETURN_TO_PIP_SOURCE";
    private static final String ABOUT_BLANK = "about:blank";
    private static final Object LOCK = new Object();
    private static WeakReference<MediaPipActivity> activeActivity = new WeakReference<>(null);
    private static PendingPip pendingPip;
    private static WeakReference<BrowserView> returnSourceView = new WeakReference<>(null);
    private static WeakReference<Activity> returnSourceHost = new WeakReference<>(null);

    private static final class PendingPip {
        final GeckoSession pipSession;
        final GeckoSession sourceSession;
        final WeakReference<BrowserView> sourceView;
        final WeakReference<Activity> sourceHost;

        PendingPip(GeckoSession pipSession, GeckoSession sourceSession, BrowserView sourceView, Activity sourceHost) {
            this.pipSession = pipSession;
            this.sourceSession = sourceSession;
            this.sourceView = new WeakReference<>(sourceView);
            this.sourceHost = new WeakReference<>(sourceHost);
        }
    }

    private WeakReference<BrowserView> sourceView = new WeakReference<>(null);
    private WeakReference<Activity> sourceHost = new WeakReference<>(null);
    private GeckoSession sourceSession;
    private GeckoView pipView;
    private GeckoSession pipSession;
    private boolean enteredPip;
    private boolean closing;
    private boolean returnToSourceOnResume;

    public static boolean launch(Context context, BrowserView source, String placementToken) {
        if (context == null || source == null || placementToken == null || placementToken.isEmpty()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                || !BrowserEngine.isInitialized()) {
            return false;
        }
        GeckoSession originalSession = source.getGeckoSession();
        if (originalSession == null || source.isDestroyed()) {
            return false;
        }
        GeckoSession clonedSession = null;
        try {
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder(originalSession.getSettings())
                    .pictureInPictureToken(placementToken)
                    .suspendMediaWhenInactive(false)
                    .build();
            clonedSession = new GeckoSession(settings);
            clonedSession.open(BrowserEngine.get().getRuntime());
            originalSession.getSessionPictureInPicture().setKeepActive(true);
            originalSession.setActive(true);
            originalSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        } catch (Throwable error) {
            if (clonedSession != null) {
                try {
                    clonedSession.close();
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
        Activity host = context instanceof Activity ? (Activity) context : null;
        PendingPip request = new PendingPip(clonedSession, originalSession, source, host);
        MediaPipActivity active = activeActivity.get();
        if (active != null && !active.isFinishing() && active.pipView != null) {
            try {
                return active.adoptSession(request);
            } catch (Throwable error) {
                active.finishWindow(true, true);
                closeClonedSession(clonedSession);
                return false;
            }
        }
        synchronized (LOCK) {
            pendingPip = request;
        }
        try {
            Intent intent = new Intent(context, MediaPipActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            clearPending(request);
            closeClonedSession(clonedSession);
            source.setPictureInPictureActive(false);
            return false;
        }
    }

    public static void closeForSource(BrowserView source) {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null && activity.isSource(source)) {
            activity.finishWindow(true, false);
        }
    }

    public static void closeForGecko(BrowserView source) {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null && activity.isSource(source)) {
            activity.finishWindow(false, false);
        }
    }

    public static void closeForSession(GeckoSession session) {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null && session != null && activity.sourceSession == session) {
            activity.runOnUiThread(() -> activity.finishWindow(true, false));
        }
    }

    public static void closeActive() {
        MediaPipActivity activity = activeActivity.get();
        if (activity != null) {
            activity.runOnUiThread(() -> activity.finishWindow(true, true));
        }
        synchronized (LOCK) {
            PendingPip pending = pendingPip;
            pendingPip = null;
            if (pending != null) {
                closeClonedSession(pending.pipSession);
            }
        }
    }

    public static boolean isReturnToSourceIntent(Intent intent) {
        return intent != null && ACTION_RETURN_TO_SOURCE.equals(intent.getAction());
    }

    public static boolean consumeReturnToSource(Activity host) {
        if (host == null) {
            return false;
        }
        BrowserView target;
        Activity expectedHost;
        synchronized (LOCK) {
            target = returnSourceView.get();
            expectedHost = returnSourceHost.get();
            if (target == null || expectedHost == null || expectedHost != host) {
                return false;
            }
            returnSourceView = new WeakReference<>(null);
            returnSourceHost = new WeakReference<>(null);
        }
        if (host instanceof MainActivity) {
            ((MainActivity) host).returnToPictureInPictureSource(target);
        } else if (host instanceof SecretActivity) {
            ((SecretActivity) host).returnToPictureInPictureSource(target);
        }
        return true;
    }

    private static void clearPending(PendingPip request) {
        synchronized (LOCK) {
            if (pendingPip == request) {
                pendingPip = null;
            }
        }
    }

    private static PendingPip takePending() {
        synchronized (LOCK) {
            PendingPip request = pendingPip;
            pendingPip = null;
            return request;
        }
    }

    private static void closeClonedSession(GeckoSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Throwable ignored) {
        }
    }

    private boolean isSource(BrowserView source) {
        return source != null && (sourceView.get() == source
                || (sourceSession != null && source.getGeckoSession() == sourceSession));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            finish();
            return;
        }
        activeActivity = new WeakReference<>(this);
        pipView = new GeckoView(this);
        pipView.setFocusable(true);
        pipView.setFocusableInTouchMode(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(pipView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        PendingPip request = takePending();
        if (request == null) {
            clearActiveActivity();
            finish();
            return;
        }
        adoptSession(request);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        PendingPip request = takePending();
        if (request != null) {
            adoptSession(request);
        }
    }

    private boolean adoptSession(PendingPip request) {
        if (request == null || closing || isFinishing() || pipView == null) {
            if (request != null) {
                closeClonedSession(request.pipSession);
                BrowserView failedSource = request.sourceView.get();
                try {
                    request.sourceSession.getSessionPictureInPicture().exit();
                } catch (Throwable ignored) {
                }
                if (failedSource != null) {
                    failedSource.setPictureInPictureActive(false);
                }
                MediaPlaybackService.onPipClosed(request.sourceSession);
            }
            return false;
        }
        GeckoSession oldSourceSession = sourceSession;
        BrowserView oldSourceView = sourceView.get();
        GeckoSession oldPipSession = pipSession;
        boolean sameSource = oldSourceSession != null && oldSourceSession == request.sourceSession;
        sourceSession = null;
        sourceView = new WeakReference<>(null);
        sourceHost = new WeakReference<>(null);
        pipSession = null;
        if (oldPipSession != null) {
            try {
                pipView.releaseSession();
            } catch (Throwable ignored) {
            }
            closeClonedSession(oldPipSession);
        }
        if (oldSourceSession != null && !sameSource) {
            if (oldSourceView != null) {
                oldSourceView.stopMediaPlayback();
                oldSourceView.setPictureInPictureActive(false);
            } else {
                MediaPlaybackService.stopRetainedPlayback(oldSourceSession);
            }
            try {
                oldSourceSession.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
            MediaPlaybackService.onPipClosed(oldSourceSession);
        }
        BrowserView nextSourceView = request.sourceView.get();
        if (nextSourceView == null || nextSourceView.getGeckoSession() != request.sourceSession) {
            closeClonedSession(request.pipSession);
            try {
                request.sourceSession.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
            if (nextSourceView != null) {
                nextSourceView.setPictureInPictureActive(false);
            }
            closeEmptyActivity();
            return false;
        }
        sourceView = new WeakReference<>(nextSourceView);
        sourceHost = request.sourceHost;
        sourceSession = request.sourceSession;
        pipSession = request.pipSession;
        try {
            sourceSession.getSessionPictureInPicture().setKeepActive(true);
            sourceSession.setActive(true);
            sourceSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            nextSourceView.setPictureInPictureActive(true);
            pipSession.getSettings().setSuspendMediaWhenInactive(false);
            pipView.setSession(pipSession);
            pipSession.loadUri(ABOUT_BLANK);
            setPictureInPictureParams(buildPipParams());
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                enterPipIfNeeded();
            }
            return true;
        } catch (Throwable error) {
            finishWindow(true, true);
            return false;
        }
    }

    private void closeEmptyActivity() {
        if (pipView != null) {
            try {
                pipView.releaseSession();
            } catch (Throwable ignored) {
            }
        }
        clearActiveActivity();
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (returnToSourceOnResume) {
            returnToSourceOnResume = false;
            returnToSourceAndClose();
            return;
        }
        enterPipIfNeeded();
    }

    private void enterPipIfNeeded() {
        if (pipSession == null || closing || isFinishing() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || isInPictureInPictureMode()) {
            return;
        }
        PictureInPictureParams params = buildPipParams();
        try {
            setPictureInPictureParams(params);
            if (enterPictureInPictureMode(params)) {
                enteredPip = true;
            } else {
                showPipStartFailure();
                finishWindow(true, true);
            }
        } catch (RuntimeException error) {
            showPipStartFailure();
            finishWindow(true, true);
        }
    }

    private PictureInPictureParams buildPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true);
        }
        return builder.build();
    }

    private void showPipStartFailure() {
        if (!isFinishing()) {
            android.widget.Toast.makeText(this,
                    "小窓再生を開始できません。AndroidのPiP許可設定と端末の対応状況を確認してください",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            enteredPip = true;
            return;
        }
        if (!enteredPip || closing) {
            return;
        }
        if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            finishWindow(true, true);
        } else {
            returnToSourceOnResume = true;
        }
    }

    @Override
    public void onBackPressed() {
        finishWindow(true, true);
    }

    private void returnToSourceAndClose() {
        BrowserView source = sourceView.get();
        Activity host = sourceHost.get();
        if (source != null && host != null && !host.isFinishing() && !host.isDestroyed()) {
            synchronized (LOCK) {
                returnSourceView = new WeakReference<>(source);
                returnSourceHost = new WeakReference<>(host);
            }
            try {
                Intent intent = new Intent(host, host.getClass())
                        .setAction(ACTION_RETURN_TO_SOURCE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                host.startActivity(intent);
            } catch (RuntimeException ignored) {
            }
        }
        finishWindow(true, false);
    }

    private void finishWindow(boolean notifyGecko, boolean stopPlayback) {
        if (closing) {
            if (!isFinishing()) {
                finish();
            }
            return;
        }
        closing = true;
        GeckoSession oldPipSession = pipSession;
        GeckoSession oldSourceSession = sourceSession;
        BrowserView oldSourceView = sourceView.get();
        pipSession = null;
        sourceSession = null;
        sourceView = new WeakReference<>(null);
        sourceHost = new WeakReference<>(null);
        if (activeActivity.get() == this) {
            clearActiveActivity();
        }
        if (stopPlayback && oldSourceSession != null) {
            if (oldSourceView != null) {
                oldSourceView.stopMediaPlayback();
            }
            MediaPlaybackService.stopPlaybackForSession(oldSourceSession);
        }
        if (oldPipSession != null) {
            try {
                pipPipReleaseViewSession();
            } catch (Throwable ignored) {
            }
            closeClonedSession(oldPipSession);
        }
        if (notifyGecko && oldSourceSession != null) {
            try {
                oldSourceSession.getSessionPictureInPicture().exit();
            } catch (Throwable ignored) {
            }
        }
        if (oldSourceView != null) {
            oldSourceView.setPictureInPictureActive(false);
        }
        if (oldSourceSession != null) {
            MediaPlaybackService.onPipClosed(oldSourceSession);
        }
        if (!isFinishing()) {
            finish();
        }
    }

    private void pipPipReleaseViewSession() {
        if (pipView != null) {
            pipView.releaseSession();
        }
    }

    private void clearActiveActivity() {
        if (activeActivity.get() == this) {
            activeActivity = new WeakReference<>(null);
        }
    }

    @Override
    protected void onDestroy() {
        if (!closing && pipSession != null) {
            finishWindow(true, enteredPip && !returnToSourceOnResume);
        }
        clearActiveActivity();
        super.onDestroy();
    }
}
