package com.coara.browser2;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;


import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.MediaSession;

import java.lang.ref.WeakReference;

public final class MediaPlaybackService extends Service {
    private static final String LEGACY_CHANNEL_ID = "coara_media_playback";
    private static final int LEGACY_NOTIFICATION_ID = 7402;
    private static final String ACTION_UPDATE = "com.coara.browser2.media.UPDATE";
    public static final String ACTION_PLAY = "com.coara.browser2.media.PLAY";
    public static final String ACTION_PAUSE = "com.coara.browser2.media.PAUSE";
    public static final String ACTION_STOP = "com.coara.browser2.media.STOP";
    private static final String ACTION_DISMISS = "com.coara.browser2.media.DISMISS";
    public static final String ACTION_SEEK_BACK = "com.coara.browser2.media.SEEK_BACK";
    public static final String ACTION_SEEK_FORWARD = "com.coara.browser2.media.SEEK_FORWARD";
    private static final String EXTRA_VERSION = "state_version";
    private static final String EXTRA_ENDED = "state_ended";
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile WeakReference<MediaPlaybackService> instance = new WeakReference<>(null);
    private static WeakReference<MediaSession> activeSession = new WeakReference<>(null);
    private static GeckoSession activeOwnerSession;
    private static GeckoSession retainedGeckoSession;
    private static MediaSession retainedMediaSession;
    private static Context applicationContext;
    private static String activeTitle = "メディア再生";
    private static String activeArtist = "Coara Gecko";
    private static boolean activePlaying;
    private static long stateVersion;
    private boolean playing;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus;
    private boolean audioFocusPending;
    private boolean transientFocusLoss;
    private long lastAppliedVersion = -1L;
    private boolean stopping;
    private int lastStartId;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = this::onAudioFocusChanged;

    public static boolean isPlaying() {
        synchronized (LOCK) {
            return activeSession.get() != null && activePlaying;
        }
    }

    public static PendingIntent controlIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, MediaPlaybackService.class).setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void pauseActive() {
        final MediaSession session;
        synchronized (LOCK) {
            session = activeSession.get();
        }
        if (session == null) {
            return;
        }
        runOnMain(() -> {
            try {
                session.pause();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private static void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN.post(runnable);
        }
    }

    private static void deliverState(Context context, long version, boolean ended, boolean isPlaying) {
        if (context == null) {
            return;
        }
        MediaPlaybackService live = instance.get();
        if (live != null && !live.stopping) {
            runOnMain(() -> {
                if (instance.get() == live && !live.stopping) {
                    live.applyState(version, ended, isPlaying);
                }
            });
            return;
        }
        if (ended || !isPlaying) {
            return;
        }
        try {
            context.startService(updateIntent(context, version, false, true));
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean isActiveSession(MediaSession session) {
        synchronized (LOCK) {
            return session != null && activeSession.get() == session && activePlaying;
        }
    }

    public static boolean isRetainedSessionPlaying(GeckoSession session) {
        synchronized (LOCK) {
            MediaSession current = activeSession.get();
            return session != null && retainedGeckoSession == session && current != null && activePlaying;
        }
    }

    public static void retainGeckoSession(Context context, GeckoSession geckoSession, MediaSession mediaSession,
                                          boolean isPlaying, String mediaTitle, String mediaArtist,
                                          boolean privatePlayback) {
        if (context == null || geckoSession == null) {
            return;
        }
        Context app = context.getApplicationContext();
        GeckoSession oldSession = null;
        long version;
        synchronized (LOCK) {
            applicationContext = app;
            if (retainedGeckoSession != null && retainedGeckoSession != geckoSession) {
                oldSession = retainedGeckoSession;
            }
            retainedGeckoSession = geckoSession;
            retainedMediaSession = mediaSession;
            activeOwnerSession = geckoSession;
            activeSession = new WeakReference<>(mediaSession);
            activePlaying = isPlaying;
            activeTitle = normalize(mediaTitle, "メディア再生");
            activeArtist = normalize(mediaArtist, "Coara Gecko");
            version = ++stateVersion;
        }
        if (oldSession != null) {
            PictureInPictureHost.closeForSession(oldSession);
            closeRetainedSession(oldSession);
        }
        try {
            geckoSession.getSessionPictureInPicture().setKeepActive(true);
            geckoSession.setActive(true);
            geckoSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        } catch (Throwable ignored) {
        }
        try {
            geckoSession.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override
            public void onActivated(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onSessionActivated(app, geckoSession, changedSession, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onMetadata(GeckoSession sourceSession, MediaSession changedSession, MediaSession.Metadata metadata) {
                if (sourceSession != geckoSession) {
                    return;
                }
                String nextTitle = privatePlayback ? "プライベート再生" : metadata == null ? "" : metadata.title;
                String nextArtist = privatePlayback ? "Coara Gecko" : metadata == null ? "" : metadata.artist;
                MediaPlaybackService.onMetadata(app, geckoSession, changedSession, nextTitle, nextArtist);
            }

            @Override
            public void onPlay(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onPlaybackState(app, geckoSession, changedSession, true, false, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onPause(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onPlaybackState(app, geckoSession, changedSession, false, false, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onStop(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession != geckoSession) {
                    return;
                }
                if (PictureInPictureHost.isPipShowing()) {
                    PictureInPictureHost.notePipMediaEnded();
                    onPlaybackState(app, geckoSession, changedSession, false, false, currentRetainedTitle(), currentRetainedArtist());
                } else {
                    PictureInPictureHost.closeForSession(geckoSession);
                    onPlaybackState(app, geckoSession, changedSession, false, true, currentRetainedTitle(), currentRetainedArtist());
                    releaseRetainedSession(geckoSession);
                }
            }

            @Override
            public void onDeactivated(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession != geckoSession) {
                    return;
                }
                if (PictureInPictureHost.isPipShowing()) {
                    PictureInPictureHost.notePipMediaEnded();
                    onPlaybackState(app, geckoSession, changedSession, false, false, currentRetainedTitle(), currentRetainedArtist());
                } else {
                    PictureInPictureHost.closeForSession(geckoSession);
                    onPlaybackState(app, geckoSession, changedSession, false, true, currentRetainedTitle(), currentRetainedArtist());
                    releaseRetainedSession(geckoSession);
                }
            }
            });
        } catch (RuntimeException ignored) {
        }
        deliverState(app, version, false, isPlaying);
    }

    public static void onPipClosed(GeckoSession session) {
        if (session != null && !isRetainedSessionPlaying(session)) {
            releaseRetainedSession(session);
        }
    }

    public static void stopRetainedPlayback(GeckoSession expected) {
        if (expected == null) {
            return;
        }
        MediaSession mediaSession;
        synchronized (LOCK) {
            if (retainedGeckoSession == null || retainedGeckoSession != expected) {
                return;
            }
            mediaSession = retainedMediaSession;
        }
        if (mediaSession != null) {
            try {
                mediaSession.stop();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void stopPlaybackForSession(GeckoSession expected) {
        if (expected == null) {
            return;
        }
        MediaSession mediaSession = null;
        GeckoSession retained = null;
        Context app = null;
        long version = 0L;
        boolean endService = false;
        synchronized (LOCK) {
            if (activeOwnerSession != expected && retainedGeckoSession != expected) {
                return;
            }
            mediaSession = activeOwnerSession == expected ? activeSession.get() : retainedMediaSession;
            if (retainedGeckoSession == expected) {
                retained = retainedGeckoSession;
                retainedGeckoSession = null;
                retainedMediaSession = null;
            }
            if (activeOwnerSession == expected) {
                activeOwnerSession = null;
                activeSession = new WeakReference<>(null);
                activePlaying = false;
                activeTitle = "メディア再生";
                activeArtist = "Coara Gecko";
                app = applicationContext;
                version = ++stateVersion;
                endService = app != null;
            }
        }
        if (mediaSession != null) {
            try {
                mediaSession.stop();
            } catch (RuntimeException ignored) {
            }
        }
        if (retained != null) {
            closeRetainedSession(retained);
        }
        if (endService && app != null) {
            deliverEndedState(app, version);
        }
    }

    public static void releaseRetainedSession(GeckoSession expected) {
        GeckoSession session;
        long version = 0L;
        Context app = null;
        boolean endService = false;
        synchronized (LOCK) {
            if (retainedGeckoSession == null || (expected != null && retainedGeckoSession != expected)) {
                return;
            }
            session = retainedGeckoSession;
            MediaSession mediaSession = retainedMediaSession;
            retainedGeckoSession = null;
            retainedMediaSession = null;
            boolean activeOwnerMatches = activeOwnerSession == session && (mediaSession != null
                    ? activeSession.get() == mediaSession : activeSession.get() == null);
            if (activeOwnerMatches && !activePlaying) {
                if (activeOwnerSession == session) {
                    activeOwnerSession = null;
                }
                activeSession = new WeakReference<>(null);
                activeTitle = "メディア再生";
                activeArtist = "Coara Gecko";
                version = ++stateVersion;
                app = applicationContext;
                endService = app != null;
            }
        }
        closeRetainedSession(session);
        if (endService && app != null) {
            deliverEndedState(app, version);
        }
    }

    private static void closeRetainedSession(GeckoSession session) {
        if (session == null) {
            return;
        }
        try {
            session.getSessionPictureInPicture().setKeepActive(false);
            session.getSettings().setSuspendMediaWhenInactive(true);
        } catch (Throwable ignored) {
        }
        try {
            session.setActive(false);
            session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        } catch (Throwable ignored) {
        }
        try {
            session.setMediaSessionDelegate(null);
        } catch (Throwable ignored) {
        }
        try {
            session.close();
        } catch (Throwable ignored) {
        }
    }

    public static void onSessionActivated(Context context, MediaSession session, String mediaTitle, String mediaArtist) {
        onSessionActivated(context, null, session, mediaTitle, mediaArtist);
    }

    public static void onSessionActivated(Context context, GeckoSession owner, MediaSession session,
                                          String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        Context app = context.getApplicationContext();
        long version;
        boolean nextPlaying;
        synchronized (LOCK) {
            applicationContext = app;
            MediaSession current = activeSession.get();
            if (activePlaying && current != session) {
                return;
            }
            nextPlaying = current == session && activePlaying;
            activeSession = new WeakReference<>(session);
            if (owner != null) {
                activeOwnerSession = owner;
            }
            activePlaying = nextPlaying;
            activeTitle = normalize(mediaTitle, "メディア再生");
            activeArtist = normalize(mediaArtist, "Coara Gecko");
            version = ++stateVersion;
        }
        if (nextPlaying) {
            deliverState(app, version, false, true);
        }
    }

    public static void onSessionDeactivated(Context context, MediaSession session) {
        onSessionDeactivated(context, null, session);
    }

    public static void onSessionDeactivated(Context context, GeckoSession owner, MediaSession session) {
        if (context == null || session == null) {
            return;
        }
        Context app = context.getApplicationContext();
        long version;
        synchronized (LOCK) {
            applicationContext = app;
            if (activeSession.get() != session || (owner != null && activeOwnerSession != null
                    && activeOwnerSession != owner) || activePlaying) {
                return;
            }
            activeSession = new WeakReference<>(null);
            activeOwnerSession = null;
            activeTitle = "メディア再生";
            activeArtist = "Coara Gecko";
            version = ++stateVersion;
        }
        deliverEndedState(app, version);
    }

    public static void onPlaybackState(Context context, MediaSession session, boolean isPlaying,
                                       boolean stopped, String mediaTitle, String mediaArtist) {
        onPlaybackState(context, null, session, isPlaying, stopped, mediaTitle, mediaArtist);
    }

    public static void onPlaybackState(Context context, GeckoSession owner, MediaSession session,
                                       boolean isPlaying, boolean stopped, String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        long version;
        boolean ended = false;
        GeckoSession retainedToClose = null;
        synchronized (LOCK) {
            applicationContext = appContext;
            MediaSession current = activeSession.get();
            if (stopped) {
                if (current != session || (owner != null && activeOwnerSession != null
                        && activeOwnerSession != owner)) {
                    return;
                }
                activeSession = new WeakReference<>(null);
                activeOwnerSession = null;
                activePlaying = false;
                activeTitle = "メディア再生";
                activeArtist = "Coara Gecko";
                if (retainedMediaSession == session) {
                    retainedToClose = retainedGeckoSession;
                    retainedGeckoSession = null;
                    retainedMediaSession = null;
                }
                ended = true;
            } else if (isPlaying) {
                activeSession = new WeakReference<>(session);
                if (owner != null) {
                    activeOwnerSession = owner;
                }
                activePlaying = true;
                activeTitle = normalize(mediaTitle, "メディア再生");
                activeArtist = normalize(mediaArtist, "Coara Gecko");
            } else {
                if (current != session || (owner != null && activeOwnerSession != null
                        && activeOwnerSession != owner)) {
                    return;
                }
                activePlaying = false;
                activeTitle = normalize(mediaTitle, activeTitle);
                activeArtist = normalize(mediaArtist, activeArtist);
            }
            version = ++stateVersion;
        }
        if (retainedToClose != null) {
            PictureInPictureHost.closeForSession(retainedToClose);
            closeRetainedSession(retainedToClose);
        }
        deliverState(appContext, version, ended, isPlaying && !ended);
        PictureInPictureHost.onPlaybackStateChanged();
    }

    public static void onMetadata(Context context, MediaSession session, String mediaTitle, String mediaArtist) {
        onMetadata(context, null, session, mediaTitle, mediaArtist);
    }

    public static void onMetadata(Context context, GeckoSession owner, MediaSession session,
                                  String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        synchronized (LOCK) {
            if (activeSession.get() != session || (owner != null && activeOwnerSession != null
                    && activeOwnerSession != owner)) {
                return;
            }
            activeTitle = normalize(mediaTitle, activeTitle);
            activeArtist = normalize(mediaArtist, activeArtist);
        }
    }

    private static String currentRetainedTitle() {
        synchronized (LOCK) {
            return activeTitle;
        }
    }

    private static String currentRetainedArtist() {
        synchronized (LOCK) {
            return activeArtist;
        }
    }

    private static Intent updateIntent(Context context, long version, boolean ended, boolean isPlaying) {
        return new Intent(context, MediaPlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("playing", isPlaying)
                .putExtra(EXTRA_ENDED, ended)
                .putExtra(EXTRA_VERSION, version);
    }

    private static void deliverEndedState(Context context, long version) {
        deliverState(context, version, true, false);
    }

    public static void clearLegacyNotification(Context context) {
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(LEGACY_NOTIFICATION_ID);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = new WeakReference<>(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        clearLegacyNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        stopping = false;
        if (intent == null) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_UPDATE.equals(action)) {
            applyState(intent.getLongExtra(EXTRA_VERSION, 0L), intent.getBooleanExtra(EXTRA_ENDED, false),
                    intent.getBooleanExtra("playing", true));
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action) || ACTION_STOP.equals(action)
                || ACTION_DISMISS.equals(action) || ACTION_SEEK_BACK.equals(action)
                || ACTION_SEEK_FORWARD.equals(action)) {
            handleControl(action);
            return START_NOT_STICKY;
        }
        stopSelfSafely();
        return START_NOT_STICKY;
    }

    private void applyState(long version, boolean ended, boolean isPlaying) {
        boolean stale = version > 0L && version < lastAppliedVersion;
        if (ended) {
            if (stale) {
                return;
            }
            if (version > 0L) {
                lastAppliedVersion = Math.max(lastAppliedVersion, version);
            }
            endPlayback();
            return;
        }
        if (version > 0L && !stale) {
            lastAppliedVersion = version;
        }
        if (!stale) {
            playing = isPlaying;
        }
        updatePlaybackState(playing);
    }

    private void updatePlaybackState(boolean isPlaying) {
        playing = isPlaying;
        if (playing) {
            transientFocusLoss = false;
            requestAudioFocus();
        } else if (!transientFocusLoss) {
            abandonAudioFocus();
        }
    }

    private void handleControl(String action) {
        if (ACTION_STOP.equals(action) || ACTION_DISMISS.equals(action)) {
            stopActivePlayback();
            return;
        }
        MediaSession session;
        synchronized (LOCK) {
            session = activeSession.get();
        }
        if (session == null) {
            endPlayback();
            return;
        }
        try {
            if (ACTION_PLAY.equals(action)) {
                playing = true;
                transientFocusLoss = false;
                com.coara.browser2.PictureInPictureHost.playPipMediaIfEnded(() -> {
                    try {
                        session.play();
                        updatePlaybackState(true);
                    } catch (RuntimeException ignored) {
                    }
                });
            } else if (ACTION_PAUSE.equals(action)) {
                playing = false;
                transientFocusLoss = false;
                session.pause();
                updatePlaybackState(false);
            } else if (ACTION_SEEK_BACK.equals(action)) {
                PictureInPictureHost.seekPipMedia(-10, () -> session.seekBackward());
            } else if (ACTION_SEEK_FORWARD.equals(action)) {
                PictureInPictureHost.seekPipMedia(10, () -> session.seekForward());
            }
        } catch (RuntimeException ignored) {
        }
        PictureInPictureHost.onPlaybackStateChanged();
    }

    private void stopActivePlayback() {
        MediaSession session;
        GeckoSession retained;
        long version;
        synchronized (LOCK) {
            session = activeSession.get();
            activeSession = new WeakReference<>(null);
            activeOwnerSession = null;
            activePlaying = false;
            activeTitle = "メディア再生";
            activeArtist = "Coara Gecko";
            retained = retainedGeckoSession;
            retainedGeckoSession = null;
            retainedMediaSession = null;
            version = ++stateVersion;
        }
        PictureInPictureHost.closeActive();
        if (session != null) {
            try {
                session.stop();
            } catch (RuntimeException ignored) {
            }
        }
        if (retained != null) {
            PictureInPictureHost.closeForSession(retained);
            closeRetainedSession(retained);
        }
        lastAppliedVersion = Math.max(lastAppliedVersion, version);
        endPlayback();
    }

    private void endPlayback() {
        playing = false;
        transientFocusLoss = false;
        abandonAudioFocus();
        stopSelfSafely();
        PictureInPictureHost.onPlaybackStateChanged();
    }

    private void stopSelfSafely() {
        stopping = true;
        if (!stopSelfResult(lastStartId)) {
            stopping = false;
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null || hasAudioFocus || audioFocusPending) {
            return;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener, new Handler(Looper.getMainLooper()))
                    .build();
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        audioFocusPending = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null || (!hasAudioFocus && !audioFocusPending)) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        } catch (RuntimeException ignored) {
        }
        hasAudioFocus = false;
        audioFocusPending = false;
    }

    private void onAudioFocusChanged(int focusChange) {
        MediaSession session;
        synchronized (LOCK) {
            session = activeSession.get();
        }
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            hasAudioFocus = true;
            audioFocusPending = false;
            transientFocusLoss = false;
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            transientFocusLoss = true;
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            transientFocusLoss = false;
            hasAudioFocus = false;
            audioFocusPending = false;
        }
        if (session == null) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                abandonAudioFocus();
            }
            return;
        }
        try {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                session.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_GAIN);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                session.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_TRANSIENT_LOSS);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                session.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_PERMANENT_LOSS);
                session.pause();
                abandonAudioFocus();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static String normalize(String value, String fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized.length() > 180 ? normalized.substring(0, 177) + "…" : normalized;
    }

    @Override
    public void onDestroy() {
        if (instance.get() == this) {
            instance = new WeakReference<>(null);
        }
        abandonAudioFocus();
        clearLegacyNotification(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
