package com.coara.browser2.util;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

public final class PictureInPictureSettings {
    private static final String ACTION_PIP_SETTINGS = "android.settings.PICTURE_IN_PICTURE_SETTINGS";

    private PictureInPictureSettings() {
    }

    public static boolean isSystemPipAllowed(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false;
        }
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return true;
        }
        try {
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(), context.getPackageName());
            return mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED;
        } catch (RuntimeException ignored) {
            return true;
        }
    }


    public static void showSystemPipDisabled(Context context) {
        if (context != null) {
            Toast.makeText(context, "Androidのアプリ設定で小窓表示（PiP）を許可してください", Toast.LENGTH_LONG).show();
        }
    }

    public static void showStartFailed(Context context) {
        if (context != null) {
            Toast.makeText(context, "小窓表示を開始できませんでした。AndroidのPiP許可と再生中の動画を確認してください", Toast.LENGTH_LONG).show();
        }
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
