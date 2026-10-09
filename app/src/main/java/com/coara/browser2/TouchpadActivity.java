package com.coara.browser2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.TouchpadController;
import com.coara.browser2.util.TouchpadSettings;
import com.coara.browser2.util.TouchpadSettingsDialog;

public class TouchpadActivity extends AppCompatActivity implements TouchpadSurfaceView.Listener {
    private SharedPreferences preferences;
    private TouchpadSurfaceView surfaceView;
    private FrameLayout root;

    protected String getPreferenceName() {
        return BrowserConstants.PREF_NAME;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(getPreferenceName(), Context.MODE_PRIVATE);
        configureWindow();
        buildContent();
        applyWindowSize();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0f);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        attrs.dimAmount = 0f;
        window.setAttributes(attrs);
    }

    private void buildContent() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        surfaceView = new TouchpadSurfaceView(this, this);
        surfaceView.applyStickSettings(preferences);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageButton settings = createButton(R.drawable.ic_settings, "タッチパッド設定");
        ImageButton close = createButton(R.drawable.ic_close, "タッチパッドを閉じる");
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        settingsParams.gravity = Gravity.TOP | Gravity.END;
        settingsParams.setMargins(0, dp(5), dp(5), 0);
        root.addView(settings, settingsParams);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        closeParams.gravity = Gravity.TOP | Gravity.START;
        closeParams.setMargins(dp(5), dp(5), 0, 0);
        root.addView(close, closeParams);
        settings.setOnClickListener(v -> showSettings());
        close.setOnClickListener(v -> finish());
        setContentView(root);
    }

    private ImageButton createButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.WHITE);
        button.setContentDescription(description);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyWindowSize() {
        if (getWindow() == null) {
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int size = TouchpadSettings.getSize(preferences);
        int width;
        int height;
        if (size == TouchpadSettings.SIZE_SMALL) {
            int preferred = (int) (screenWidth * 0.80f);
            width = Math.min(screenWidth - dp(16), Math.max(dp(240), preferred));
            height = Math.min(screenHeight / 2, Math.min(dp(190), Math.max(dp(150), (int) (screenHeight * 0.26f))));
        } else if (size == TouchpadSettings.SIZE_LARGE) {
            width = screenWidth;
            height = Math.max(1, screenHeight / 2);
        } else {
            width = screenWidth;
            height = Math.max(dp(210), Math.min(dp(320), (int) (screenHeight * 0.38f)));
        }
        getWindow().setLayout(width, height);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.width = width;
        attrs.height = height;
        attrs.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        getWindow().setAttributes(attrs);
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private void showSettings() {
        TouchpadSettingsDialog.show(this, preferences, new TouchpadSettingsDialog.Callback() {
            @Override
            public void onSizeChanged() {
                applyWindowSize();
            }

            @Override
            public void onStickChanged() {
                if (surfaceView != null) {
                    surfaceView.applyStickSettings(preferences);
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyWindowSize();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TouchpadController.setActive(true);
    }

    @Override
    protected void onStop() {
        TouchpadController.setActive(false);
        super.onStop();
    }

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
}
