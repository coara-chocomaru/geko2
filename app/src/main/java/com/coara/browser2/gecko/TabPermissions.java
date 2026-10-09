package com.coara.browser2.gecko;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.List;

final class TabPermissions implements GeckoSession.PermissionDelegate {
    private final BrowserView view;

    TabPermissions(BrowserView view) {
        this.view = view;
    }

    private BrowserHost host() {
        Context c = view.getContext();
        int depth = 0;
        while (c != null && depth++ < 8) {
            if (c instanceof BrowserHost) {
                return (BrowserHost) c;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
        }
        return null;
    }

    @Override
    public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session, ContentPermission perm) {
        int value;
        switch (perm.permission) {
            case PERMISSION_PERSISTENT_STORAGE:
            case PERMISSION_MEDIA_KEY_SYSTEM_ACCESS:
            case PERMISSION_STORAGE_ACCESS:
            case PERMISSION_AUTOPLAY_INAUDIBLE:
                value = ContentPermission.VALUE_ALLOW;
                break;
            default:
                value = ContentPermission.VALUE_DENY;
                break;
        }
        return GeckoResult.fromValue(value);
    }

    @Override
    public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, final Callback callback) {
        BrowserHost host = host();
        if (host == null || permissions == null) {
            callback.reject();
            return;
        }
        host.requestAndroidPermissions(permissions, new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean granted) {
                if (granted != null && granted) {
                    callback.grant();
                } else {
                    callback.reject();
                }
            }
        });
    }

    @Override
    public void onMediaPermissionRequest(GeckoSession session, String uri, final MediaSource[] video,
                                         final MediaSource[] audio, final MediaCallback callback) {
        final MediaSource v = video != null && video.length > 0 ? video[0] : null;
        final MediaSource a = audio != null && audio.length > 0 ? audio[0] : null;
        if (v == null && a == null) {
            callback.reject();
            return;
        }
        List<String> need = new ArrayList<>();
        Context ctx = view.getContext();
        if (v != null && ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (a != null && ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) {
            callback.grant(v, a);
            return;
        }
        BrowserHost host = host();
        if (host == null) {
            callback.reject();
            return;
        }
        host.requestAndroidPermissions(need.toArray(new String[0]), new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean granted) {
                if (granted != null && granted) {
                    callback.grant(v, a);
                } else {
                    callback.reject();
                }
            }
        });
    }
}
