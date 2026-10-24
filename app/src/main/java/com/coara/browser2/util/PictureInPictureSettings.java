package com.coara.browser2.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

public final class PictureInPictureSettings {
    private static final String ACTION_PIP_SETTINGS = "android.settings.PICTURE_IN_PICTURE_SETTINGS";

    private PictureInPictureSettings() {
    }

    public static void open(Activity activity) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(activity, "この端末は小窓再生に対応していません", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri packageUri = Uri.fromParts("package", activity.getPackageName(), null);
        try {
            activity.startActivity(new Intent(ACTION_PIP_SETTINGS, packageUri));
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(activity, "Androidのアプリ設定を開けませんでした", Toast.LENGTH_SHORT).show();
        }
    }
}
