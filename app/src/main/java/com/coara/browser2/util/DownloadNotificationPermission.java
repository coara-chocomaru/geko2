package com.coara.browser2.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class DownloadNotificationPermission {
    private static final int REQUEST_CODE = 7406;
    private static final String PREF_ASKED = "download_notification_permission_asked";

    private DownloadNotificationPermission() {
    }

    public static void request(Activity activity) {
        if (activity == null || activity.isFinishing() || Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences preferences = activity.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
        if (preferences.getBoolean(PREF_ASKED, false)) {
            return;
        }
        preferences.edit().putBoolean(PREF_ASKED, true).apply();
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            }
        });
    }
}
