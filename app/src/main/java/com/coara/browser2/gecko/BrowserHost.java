package com.coara.browser2.gecko;

import android.app.Activity;
import android.net.Uri;

public interface BrowserHost {
    interface AuthCallback {
        void proceed(String user, String password);

        void cancel();
    }

    Activity hostActivity();

    void chooseFiles(String[] mimeTypes, boolean multiple, boolean capture, ValueCallback<Uri[]> callback);

    void requestAndroidPermissions(String[] permissions, ValueCallback<Boolean> callback);

    void requestStorageAccess(BrowserView view, String topUrl, String frameOrigin, ValueCallback<Boolean> callback);

    void requestAuth(BrowserView view, String host, String realm, boolean previousFailed, String presetUser,
                     String presetPassword, AuthCallback callback);
}
