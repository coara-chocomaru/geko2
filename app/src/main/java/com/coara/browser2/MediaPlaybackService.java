package com.coara.browser2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.mozilla.geckoview.MediaSession;

import java.lang.ref.WeakReference;

public final class MediaPlaybackService extends Service {
    private static final String CHANNEL_ID = "coara_media_playback";
    private static final int NOTIFICATION_ID = 7402;
    private static final String ACTION_UPDATE = "com.coara.browser2.media.UPDATE";
    private static final String ACTION_PLAY = "com.coara.browser2.media.PLAY";
    private static final String ACTION_PAUSE = "com.coara.browser2.media.PAUSE";
    private static final String ACTION_STOP = "com.coara.browser2.media.STOP";
    private static final Object LOCK = new Object();
    private static WeakReference<MediaSession> activeSession = new WeakReference<>(null);
    private static String activeTitle = "メディア再生";
    private static String activeArtist = "Coara Gecko";
    private static boolean activePlaying;
    private String title = activeTitle;
    private String artist = activeArtist;
    private boolean playing;

    public static boolean isActiveSession(MediaSession session) {
        synchronized (LOCK) {
            return session != null && activeSession.get() == session;
        }
    }

    public static void onPlaybackState(Context context, MediaSession session, boolean isPlaying,
                                       boolean stopped, String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        synchronized (LOCK) {
            MediaSession current = activeSession.get();
            if (isPlaying) {
                activeSession = new WeakReference<>(session);
                current = session;
                activePlaying = true;
                activeTitle = normalize(mediaTitle, "メディア再生");
                activeArtist = normalize(mediaArtist, "Coara Gecko");
            } else if (current != session) {
                return;
            } else if (stopped) {
                activeSession = new WeakReference<>(null);
                activePlaying = false;
                activeTitle = "メディア再生";
                activeArtist = "Coara Gecko";
                context.stopService(new Intent(context, MediaPlaybackService.class));
                return;
            } else {
                activePlaying = false;
                activeTitle = normalize(mediaTitle, activeTitle);
                activeArtist = normalize(mediaArtist, activeArtist);
            }
        }
        Intent intent = new Intent(context, MediaPlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("title", normalize(mediaTitle, "メディア再生"))
                .putExtra("artist", normalize(mediaArtist, "Coara Gecko"))
                .putExtra("playing", isPlaying);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException ignored) {
        }
    }

    public static void onMetadata(Context context, MediaSession session, String mediaTitle, String mediaArtist) {
        if (context == null || session == null) {
            return;
        }
        boolean update;
        boolean isPlaying;
        synchronized (LOCK) {
            update = activeSession.get() == session;
            if (update) {
                activeTitle = normalize(mediaTitle, activeTitle);
                activeArtist = normalize(mediaArtist, activeArtist);
            }
            isPlaying = activePlaying;
        }
        if (!update) {
            return;
        }
        Intent intent = new Intent(context, MediaPlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("title", normalize(mediaTitle, "メディア再生"))
                .putExtra("artist", normalize(mediaArtist, "Coara Gecko"))
                .putExtra("playing", isPlaying);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        CHANNEL_ID, "バックグラウンドメディア再生", android.app.NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("ウェブページの音声・動画のバックグラウンド再生");
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action) || ACTION_STOP.equals(action)) {
            MediaSession session;
            synchronized (LOCK) {
                session = activeSession.get();
            }
            if (session == null) {
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
            try {
                if (ACTION_PLAY.equals(action)) {
                    session.play();
                    playing = true;
                } else if (ACTION_PAUSE.equals(action)) {
                    session.pause();
                    playing = false;
                } else {
                    session.stop();
                    synchronized (LOCK) {
                        if (activeSession.get() == session) {
                            activeSession = new WeakReference<>(null);
                            activePlaying = false;
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelfResult(startId);
                    return START_NOT_STICKY;
                }
            } catch (RuntimeException ignored) {
            }
        } else if (ACTION_UPDATE.equals(action)) {
            title = normalize(intent.getStringExtra("title"), "メディア再生");
            artist = normalize(intent.getStringExtra("artist"), "Coara Gecko");
            playing = intent.getBooleanExtra("playing", true);
        }
        startPlaybackForeground(buildNotification());
        return START_NOT_STICKY;
    }

    private void startPlaybackForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 7402, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(content)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        builder.addAction(new NotificationCompat.Action(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "一時停止" : "再生",
                serviceAction(playing ? ACTION_PAUSE : ACTION_PLAY, playing ? 7403 : 7404)));
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel,
                "停止", serviceAction(ACTION_STOP, 7405)));
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
        stopForeground(STOP_FOREGROUND_REMOVE);
        synchronized (LOCK) {
            activeSession = new WeakReference<>(null);
            activePlaying = false;
            activeTitle = "メディア再生";
            activeArtist = "Coara Gecko";
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
