package com.coara.browser2.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.coara.browser2.R;
import com.coara.browser2.TouchpadSurfaceView;

import java.lang.ref.WeakReference;

public final class TouchpadOverlayController {
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static FrameLayout overlay;
    private static TouchpadSurfaceView surfaceView;
    private static SharedPreferences preferences;
    private static boolean visible;

    private TouchpadOverlayController() {
    }

    public static void toggle(Activity activity, SharedPreferences prefs) {
        if (isVisible(activity)) {
            hide(activity);
        } else {
            show(activity, prefs);
        }
    }

    public static void show(Activity activity, SharedPreferences prefs) {
        if (!isUsable(activity) || prefs == null) {
            return;
        }
        if (overlay != null && activityRef.get() == activity) {
            preferences = prefs;
            visible = true;
            applyWindowSize(activity);
            if (surfaceView != null) {
                surfaceView.applyStickSettings(prefs);
            }
            TouchpadController.setScreenTouchBlocked(TouchpadSettings.isScreenTouchBlocked(prefs));
            TouchpadController.setActive(true);
            return;
        }
        detach();
        preferences = prefs;
        activityRef = new WeakReference<>(activity);
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        surfaceView = new TouchpadSurfaceView(activity, new TouchpadSurfaceView.Listener() {
            @Override
            public void onMove(float dx, float dy) {
                TouchpadController.move(dx, dy);
            }

            @Override
            public void onClick(int button) {
                TouchpadController.click(button);
            }

            @Override
            public void onButtonDown(int button) {
                TouchpadController.buttonDown(button);
            }

            @Override
            public void onButtonUp(int button) {
                TouchpadController.buttonUp(button);
            }

            @Override
            public void onCancel() {
                TouchpadController.cancelButtons();
            }

            @Override
            public void onStick(float x, float y) {
                TouchpadController.stick(x, y);
            }

            @Override
            public void onStickTap() {
                TouchpadController.stickTap();
            }
        });
        surfaceView.applyStickSettings(prefs);

        overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

        ImageButton settings = createButton(activity, R.drawable.ic_settings, "タッチパッド設定");
        ImageButton close = createButton(activity, R.drawable.ic_close, "タッチパッドを閉じる");

        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
        settingsParams.gravity = Gravity.TOP | Gravity.END;
        settingsParams.setMargins(0, dp(activity, 5), dp(activity, 5), 0);
        overlay.addView(settings, settingsParams);

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
        closeParams.gravity = Gravity.TOP | Gravity.START;
        closeParams.setMargins(dp(activity, 5), dp(activity, 5), 0, 0);
        overlay.addView(close, closeParams);

        settings.setOnClickListener(v -> showSettings(activity, prefs));
        close.setOnClickListener(v -> hide(activity));

        content.addView(overlay, buildLayoutParams(activity, prefs));
        visible = true;
        TouchpadController.setScreenTouchBlocked(TouchpadSettings.isScreenTouchBlocked(prefs));
        TouchpadController.setActive(true);
    }

    public static void hide(Activity activity) {
        if (activity != null && activityRef.get() != activity) {
            return;
        }
        TouchpadController.setActive(false);
        visible = false;
        detachViewOnly();
    }

    public static void onHostPause(Activity activity) {
        if (activity != null && activityRef.get() == activity) {
            TouchpadController.setActive(false);
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
            }
            visible = false;
        }
    }

    public static void onHostResume(Activity activity, SharedPreferences prefs) {
        if (!isUsable(activity) || prefs == null) {
            return;
        }
        preferences = prefs;
        if (TouchpadSettings.isAlwaysVisible(prefs)) {
            show(activity, prefs);
        } else if (activityRef.get() == activity && overlay != null) {
            overlay.setVisibility(View.GONE);
            TouchpadController.setActive(false);
            visible = false;
        }
    }

    public static void onConfigurationChanged(Activity activity, SharedPreferences prefs) {
        if (!isUsable(activity) || prefs == null) {
            return;
        }
        preferences = prefs;
        if (activityRef.get() == activity && overlay != null) {
            applyWindowSize(activity);
        }
    }

    public static boolean isVisible(Activity activity) {
        return visible && overlay != null && activityRef.get() == activity && overlay.getVisibility() == View.VISIBLE;
    }

    private static FrameLayout.LayoutParams buildLayoutParams(Activity activity, SharedPreferences prefs) {
        int width;
        int height;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int size = TouchpadSettings.getSize(prefs);
        if (size == TouchpadSettings.SIZE_SMALL) {
            int preferred = (int) (screenWidth * 0.80f);
            width = Math.min(screenWidth - dp(activity, 16), Math.max(dp(activity, 240), preferred));
            height = Math.min(screenHeight / 2, Math.min(dp(activity, 190), Math.max(dp(activity, 150), (int) (screenHeight * 0.26f))));
        } else if (size == TouchpadSettings.SIZE_LARGE) {
            width = screenWidth;
            height = Math.max(1, screenHeight / 2);
        } else {
            width = screenWidth;
            height = Math.max(dp(activity, 210), Math.min(dp(activity, 320), (int) (screenHeight * 0.38f)));
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private static void applyWindowSize(Activity activity) {
        if (overlay == null || activity == null) {
            return;
        }
        FrameLayout.LayoutParams params = buildLayoutParams(activity, preferences);
        overlay.setLayoutParams(params);
        overlay.setVisibility(View.VISIBLE);
        overlay.requestLayout();
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private static ImageButton createButton(Context context, int icon, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.WHITE);
        button.setContentDescription(description);
        int padding = dp(context, 9);
        button.setPadding(padding, padding, padding, padding);
        return button;
    }

    private static void showSettings(Activity activity, SharedPreferences prefs) {
        if (!isUsable(activity) || prefs == null) {
            return;
        }
        TouchpadSettingsDialog.show(activity, prefs, new TouchpadSettingsDialog.Callback() {
            @Override
            public void onSizeChanged() {
                applyWindowSize(activity);
            }

            @Override
            public void onStickChanged() {
                if (surfaceView != null) {
                    surfaceView.applyStickSettings(prefs);
                }
            }

            @Override
            public void onScreenTouchChanged() {
                TouchpadController.setScreenTouchBlocked(TouchpadSettings.isScreenTouchBlocked(prefs));
            }
        });
    }

    private static void detach() {
        TouchpadController.setActive(false);
        detachViewOnly();
        activityRef = new WeakReference<>(null);
        preferences = null;
        visible = false;
    }

    private static void detachViewOnly() {
        if (overlay != null) {
            View parent = (View) overlay.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(overlay);
            }
        }
        overlay = null;
        surfaceView = null;
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
