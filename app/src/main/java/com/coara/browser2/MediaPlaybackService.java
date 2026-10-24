package com.coara.browser2;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.coara.browser2.util.BrowserConstants;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.MediaSession;

import java.lang.ref.WeakReference;

public final class MediaPlaybackService extends Service {
    private static final String CHANNEL_ID = "coara_media_playback";
    private static final int NOTIFICATION_ID = 7402;
    private static final String ACTION_UPDATE = "com.coara.browser2.media.UPDATE";
    private static final String ACTION_PLAY = "com.coara.browser2.media.PLAY";
    private static final String ACTION_PAUSE = "com.coara.browser2.media.PAUSE";
    private static final String ACTION_STOP = "com.coara.browser2.media.STOP";
    private static final String ACTION_DISMISS = "com.coara.browser2.media.DISMISS";
    private static final String EXTRA_VERSION = "state_version";
    private static final String EXTRA_ENDED = "state_ended";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7406;
    private static final String NOTIFICATION_PERMISSION_ASKED = "media_notification_permission_asked";
    private static final Object LOCK = new Object();
    private static WeakReference<MediaSession> activeSession = new WeakReference<>(null);
    private static GeckoSession retainedGeckoSession;
    private static MediaSession retainedMediaSession;
    private static Context applicationContext;
    private static String activeTitle = "メディア再生";
    private static String activeArtist = "Coara Gecko";
    private static boolean activePlaying;
    private static boolean notificationActive;
    private static long stateVersion;
    private String title = activeTitle;
    private String artist = activeArtist;
    private boolean playing;
    private boolean foregroundStarted;
    private AudioManager audioManager;
    private android.media.session.MediaSession platformMediaSession;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus;
    private boolean audioFocusPending;
    private boolean transientFocusLoss;
    private long lastAppliedVersion = -1L;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = this::onAudioFocusChanged;

    public static boolean isActiveSession(MediaSession session) {
        synchronized (LOCK) {
            return session != null && activeSession.get() == session && activePlaying;
        }
    }

    public static void requestNotificationPermission(Activity activity) {
        if (activity == null || activity.isFinishing() || Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences preferences = activity.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
        if (preferences.getBoolean(NOTIFICATION_PERMISSION_ASKED, false)) {
            return;
        }
        preferences.edit().putBoolean(NOTIFICATION_PERMISSION_ASKED, true).apply();
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            }
        });
    }

    public static boolean isRetainedSessionPlaying(GeckoSession session) {
        synchronized (LOCK) {
            MediaSession current = activeSession.get();
            return session != null && retainedGeckoSession == session && current != null && activePlaying;
        }
    }

    public static void retainGeckoSession(Context context, GeckoSession geckoSession, MediaSession mediaSession,
                                          boolean isPlaying, String mediaTitle, String mediaArtist) {
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
            activeSession = new WeakReference<>(mediaSession);
            activePlaying = isPlaying;
            notificationActive = true;
            activeTitle = normalize(mediaTitle, "メディア再生");
            activeArtist = normalize(mediaArtist, "Coara Gecko");
            version = ++stateVersion;
        }
        if (oldSession != null) {
            MediaPipActivity.closeForSession(oldSession);
            closeRetainedSession(oldSession);
        }
        try {
            geckoSession.getSessionPictureInPicture().setKeepActive(true);
            geckoSession.setActive(true);
            geckoSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        } catch (Throwable ignored) {
        }
        geckoSession.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override
            public void onActivated(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onSessionActivated(app, changedSession, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onMetadata(GeckoSession sourceSession, MediaSession changedSession, MediaSession.Metadata metadata) {
                if (sourceSession != geckoSession) {
                    return;
                }
                String nextTitle = metadata == null ? "" : metadata.title;
                String nextArtist = metadata == null ? "" : metadata.artist;
                onMetadata(app, changedSession, nextTitle, nextArtist);
            }

            @Override
            public void onPlay(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onPlaybackState(app, changedSession, true, false, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onPause(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    onPlaybackState(app, changedSession, false, false, currentRetainedTitle(), currentRetainedArtist());
                }
            }

            @Override
            public void onStop(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    MediaPipActivity.closeForSession(geckoSession);
                    onPlaybackState(app, changedSession, false, true, currentRetainedTitle(), currentRetainedArtist());
                    releaseRetainedSession(geckoSession);
                }
            }

            @Override
            public void onDeactivated(GeckoSession sourceSession, MediaSession changedSession) {
                if (sourceSession == geckoSession) {
                    MediaPipActivity.closeForSession(geckoSession);
                    onPlaybackState(app, changedSession, false, true, currentRetainedTitle(), currentRetainedArtist());
                    releaseRetainedSession(geckoSession);
                }
            }
        });
        try {
            Intent update = updateIntent(app, version, false, isPlaying, mediaTitle, mediaArtist);
            ContextCompat.startForegroundService(app, update);
        } catch (RuntimeException ignored) {
        }
    }

    public static void onPipClosed(GeckoSession session) {
        if (session != null && !isRetainedSessionPlaying(session)) {
            releaseRetainedSession(session);
        }
    }

    public static void releaseRetainedSession(GeckoSession expected) {
        GeckoSession session;
        long version = 0L;
        Context app = null;
        boolean clearNotification = false;
        synchronized (LOCK) {
            if (retainedGeckoSession == null || (expected != null && retainedGeckoSession != expected)) {
                return;
            }
            session = retainedGeckoSession;
            MediaSession mediaSession = retainedMediaSession;
            retainedGeckoSession = null;
            retainedMediaSession = null;
            boolean activeOwnerMatches = mediaSession != null
                    ? activeSession.get() == mediaSession : activeSession.get() == null;
            if (activeOwnerMatches && !activePlaying) {
                activeSession = new WeakReference<>(null);
                notificationActive = false;
                activeTitle = "メディア再生";
                activeArtist = "Coara Gecko";
                version = ++stateVersion;
                app = applicationContext;
                clearNotification = app != null;
            }
        }
        closeRetainedSession(session);
        if (clearNotification && app != null) {
            postEndedState(app, version, "メディア再生", "Coara Gecko");
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
        if (context == null || session == null) {
            return;
        }
        Context app = context.getApplicationContext();
        long version;
        boolean clearPrevious;
        synchronized (LOCK) {
            applicationContext = app;
            MediaSession current = activeSession.get();
            clearPrevious = current != null && current != session;
            if (clearPrevious) {
                notificationActive = false;
            }
            activeSession = new WeakReference<>(session);
            activePlaying = current == session && activePlaying;
            activeTitle = normalize(mediaTitle, "メディア再生");
            activeArtist = normalize(mediaArtist, "Coara Gecko");
            version = ++stateVersion;
        }
        if (!clearPrevious) {
            return;
        }
        postEndedState(app, version, mediaTitle, mediaArtist);
    }

    public static void onSessionDeactivated(Context context, MediaSession session) {
        if (context == null || session == null) {
            return;
        }
        Context app = context.getApplicationContext();
        long version;
        boolean clear;
        String mediaTitle;
        String mediaArtist;
        synchronized (LOCK) {
            applicationContext = app;
            clear = activeSession.get() == session && !activePlaying;
            if (!clear) {
                return;
            }
            activeSession = new WeakReference<>(null);
            notificationActive = false;
            activeTitle = "メディア再生";
            activeArtist = "Coara Gecko";
            mediaTitle = activeTitle;
            mediaArtist = activeArtist;
            version = ++stateVersion;
        }
        postEndedState(app, version, mediaTitle, mediaArtist);
    }

    public static void onPlaybackState(Context context, MediaSession session, boolean isPlaying,
                                       boolean stopped, String mediaTitle, String mediaArtist) {
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
                if (current != session) {
                    return;
                }
                activeSession = new WeakReference<>(null);
                activePlaying = false;
                notificationActive = false;
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
                activePlaying = true;
                notificationActive = true;
                activeTitle = normalize(mediaTitle, "メディア再生");
                activeArtist = normalize(mediaArtist, "Coara Gecko");
            } else {
                if (current != session) {
                    return;
                }
                activePlaying = false;
                activeTitle = normalize(mediaTitle, activeTitle);
                activeArtist = normalize(mediaArtist, activeArtist);
            }
            version = ++stateVersion;
        }
        if (retainedToClose != null) {
            MediaPipActivity.closeForSession(retainedToClose);
            closeRetainedSession(retainedToClose);
        }
        Context app = context.getApplicationContext();
        if (ended) {
            postEndedState(app, version, mediaTitle, mediaArtist);
            return;
        }
        Intent intent = updateIntent(app, version, false, isPlaying, mediaTitle, mediaArtist);
        try {
            ContextCompat.startForegroundService(app, intent);
        } catch (RuntimeException ignored) {
        }
    }

    public static void onMetadata(Context context, MediaSession session, String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        Context app = context.getApplicationContext();
        long version;
        boolean isPlaying;
        synchronized (LOCK) {
            applicationContext = app;
            if (activeSession.get() != session) {
                return;
            }
            activeTitle = normalize(mediaTitle, activeTitle);
            activeArtist = normalize(mediaArtist, activeArtist);
            isPlaying = activePlaying;
            if (!notificationActive) {
                return;
            }
            version = ++stateVersion;
        }
        try {
            ContextCompat.startForegroundService(app, updateIntent(app, version,
                    false, isPlaying, mediaTitle, mediaArtist));
        } catch (RuntimeException ignored) {
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

    private static Intent updateIntent(Context context, long version, boolean ended, boolean isPlaying,
                                       String mediaTitle, String mediaArtist) {
        return new Intent(context, MediaPlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("title", normalize(mediaTitle, "メディア再生"))
                .putExtra("artist", normalize(mediaArtist, "Coara Gecko"))
                .putExtra("playing", isPlaying)
                .putExtra(EXTRA_ENDED, ended)
                .putExtra(EXTRA_VERSION, version);
    }

    private static void postEndedState(Context context, long version, String mediaTitle, String mediaArtist) {
        if (context == null) {
            return;
        }
        try {
            context.startService(updateIntent(context, version, true, false, mediaTitle, mediaArtist));
        } catch (RuntimeException ignored) {
            boolean stillCurrent;
            synchronized (LOCK) {
                stillCurrent = stateVersion <= version && !activePlaying;
            }
            if (stillCurrent) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
                try {
                    context.stopService(new Intent(context, MediaPlaybackService.class));
                } catch (RuntimeException ignoredAgain) {
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        platformMediaSession = new android.media.session.MediaSession(this, "Coara Gecko Media");
        platformMediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        platformMediaSession.setCallback(new android.media.session.MediaSession.Callback() {
            @Override
            public void onPlay() {
                dispatchMediaAction(ACTION_PLAY);
            }

            @Override
            public void onPause() {
                dispatchMediaAction(ACTION_PAUSE);
            }

            @Override
            public void onStop() {
                dispatchMediaAction(ACTION_STOP);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        CHANNEL_ID, "バックグラウンドメディア再生", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("ウェブページの音声・動画のバックグラウンド再生");
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void dispatchMediaAction(String action) {
        try {
            startService(new Intent(this, MediaPlaybackService.class).setAction(action));
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action)) {
            MediaSession session;
            synchronized (LOCK) {
                session = activeSession.get();
            }
            if (session == null) {
                abandonAudioFocus();
                stopForeground(STOP_FOREGROUND_REMOVE);
                foregroundStarted = false;
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
                stopPlatformMediaSession();
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
            playing = ACTION_PLAY.equals(action);
            if (playing) {
                transientFocusLoss = false;
            } else {
                transientFocusLoss = false;
                abandonAudioFocus();
            }
            try {
                if (playing) {
                    session.play();
                } else {
                    session.pause();
                }
            } catch (RuntimeException ignored) {
            }
            startPlaybackForeground(buildNotification());
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action) || ACTION_DISMISS.equals(action)) {
            stopActivePlayback(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE.equals(action)) {
            long version = intent.getLongExtra(EXTRA_VERSION, 0L);
            if (version > 0L && version < lastAppliedVersion) {
                if (!foregroundStarted) {
                    stopSelfResult(startId);
                }
                return START_NOT_STICKY;
            }
            if (version > 0L) {
                lastAppliedVersion = version;
            }
            if (intent.getBooleanExtra(EXTRA_ENDED, false)) {
                abandonAudioFocus();
                playing = false;
                title = "メディア再生";
                artist = "Coara Gecko";
                stopPlatformMediaSession();
                stopForeground(STOP_FOREGROUND_REMOVE);
                foregroundStarted = false;
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
            title = normalize(intent.getStringExtra("title"), "メディア再生");
            artist = normalize(intent.getStringExtra("artist"), "Coara Gecko");
            playing = intent.getBooleanExtra("playing", true);
            startPlaybackForeground(buildNotification());
            return START_NOT_STICKY;
        }
        stopSelfResult(startId);
        return START_NOT_STICKY;
    }

    private void stopActivePlayback(int startId) {
        MediaSession session;
        GeckoSession retained;
        long version;
        synchronized (LOCK) {
            session = activeSession.get();
            activeSession = new WeakReference<>(null);
            activePlaying = false;
            notificationActive = false;
            activeTitle = "メディア再生";
            activeArtist = "Coara Gecko";
            retained = retainedGeckoSession;
            retainedGeckoSession = null;
            retainedMediaSession = null;
            version = ++stateVersion;
        }
        MediaPipActivity.closeActive();
        if (session != null) {
            try {
                session.stop();
            } catch (RuntimeException ignored) {
            }
        }
        if (retained != null) {
            MediaPipActivity.closeForSession(retained);
            closeRetainedSession(retained);
        }
        abandonAudioFocus();
        stopPlatformMediaSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        lastAppliedVersion = Math.max(lastAppliedVersion, version);
        stopSelfResult(startId);
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

    private void startPlaybackForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
        updatePlatformMediaSession();
        if (playing) {
            requestAudioFocus();
        } else if (!transientFocusLoss) {
            abandonAudioFocus();
        }
    }

    private void updatePlatformMediaSession() {
        android.media.session.MediaSession platform = platformMediaSession;
        if (platform == null) {
            return;
        }
        try {
            platform.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                    .build());
            long actions = android.media.session.PlaybackState.ACTION_PLAY
                    | android.media.session.PlaybackState.ACTION_PAUSE
                    | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
                    | android.media.session.PlaybackState.ACTION_STOP;
            android.media.session.PlaybackState state = new android.media.session.PlaybackState.Builder()
                    .setActions(actions)
                    .setState(playing ? android.media.session.PlaybackState.STATE_PLAYING
                                    : android.media.session.PlaybackState.STATE_PAUSED,
                            android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                            playing ? 1.0f : 0.0f, SystemClock.elapsedRealtime())
                    .build();
            platform.setPlaybackState(state);
            if (!platform.isActive()) {
                platform.setActive(true);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void stopPlatformMediaSession() {
        android.media.session.MediaSession platform = platformMediaSession;
        if (platform == null) {
            return;
        }
        try {
            platform.setPlaybackState(new android.media.session.PlaybackState.Builder()
                    .setActions(0)
                    .setState(android.media.session.PlaybackState.STATE_STOPPED,
                            android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                            0.0f, SystemClock.elapsedRealtime())
                    .build());
            platform.setActive(false);
        } catch (RuntimeException ignored) {
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 7402, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setDeleteIntent(serviceAction(ACTION_DISMISS, 7406))
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_LOW);
        builder.addAction(new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "一時停止" : "再生",
                serviceAction(playing ? ACTION_PAUSE : ACTION_PLAY, playing ? 7403 : 7404)).build());
        builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                "停止", serviceAction(ACTION_STOP, 7405)).build());
        if (Build.VERSION.SDK_INT < 35 && platformMediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(platformMediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1));
        }
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, MediaPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        abandonAudioFocus();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        android.media.session.MediaSession platform = platformMediaSession;
        platformMediaSession = null;
        if (platform != null) {
            try {
                platform.setActive(false);
                platform.setCallback(null);
                platform.release();
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
