package com.coara.browser2.fx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class FxPopup {
    private static Dialog current;
    private static GeckoSession currentSession;

    private FxPopup() {
    }

    public static boolean isShowing() {
        return current != null && current.isShowing();
    }

    public static void dismiss() {
        Dialog d = current;
        current = null;
        if (d != null) {
            try {
                d.dismiss();
            } catch (Throwable ignored) {
            }
        }
        GeckoSession s = currentSession;
        currentSession = null;
        if (s != null) {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static GeckoResult<GeckoSession> show(Activity activity, GeckoRuntime runtime) {
        dismiss();
        final GeckoSession session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onCloseRequest(GeckoSession s) {
                dismiss();
            }
        });
        session.open(runtime);
        final GeckoView view = new GeckoView(activity);
        view.setSession(session);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int width = Math.min(dm.widthPixels - (int) (24 * dm.density), (int) (420 * dm.density));
        int height = Math.min(dm.heightPixels - (int) (96 * dm.density), (int) (600 * dm.density));
        FrameLayout frame = new FrameLayout(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(16 * dm.density);
        frame.setBackground(bg);
        frame.setClipToOutline(true);
        frame.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(frame, new ViewGroup.LayoutParams(width, height));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setLayout(width, height);
        }
        dialog.setOnDismissListener(d -> {
            if (current == dialog) {
                current = null;
                GeckoSession s = currentSession;
                currentSession = null;
                if (s != null) {
                    try {
                        s.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
        current = dialog;
        currentSession = session;
        dialog.show();
        return GeckoResult.fromValue(session);
    }
}
