package com.coara.browser2.util;

import android.content.SharedPreferences;

public final class TouchpadSettings {
    public static final String KEY_SIZE = "touchpad_size";
    public static final String KEY_ALWAYS_VISIBLE = "touchpad_always_visible";
    public static final String KEY_STICK_ENABLED = "touchpad_stick_enabled";
    public static final String KEY_STICK_MODE = "touchpad_stick_mode";
    public static final String KEY_STICK_SIDE = "touchpad_stick_side";
    public static final String KEY_BLOCK_SCREEN_TOUCH = "touchpad_block_screen_touch";
    public static final int SIDE_LEFT = 0;
    public static final int SIDE_RIGHT = 1;
    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;
    public static final int STICK_AUTO = 0;
    public static final int STICK_WASD = 1;
    public static final int STICK_ARROWS = 2;
    public static final int STICK_GAMEPAD = 3;
    public static final int STICK_LOOK = 4;
    public static final int STICK_POINTER = 5;
    public static final int STICK_MODE_COUNT = 6;

    private static final String[] STICK_LABELS = {
            "WASD+矢印+パッド",
            "WASDキー",
            "矢印キー",
            "ゲームパッド",
            "視点移動",
            "ポインタ方向"
    };

    private static final String[] STICK_SHORT_LABELS = {
            "自動",
            "WASD",
            "矢印",
            "パッド",
            "視点",
            "ポインタ"
    };

    private static final String[] STICK_DESCRIPTIONS = {
            "自動",
            "WASD",
            "矢印キー操作のゲーム向け",
            "Gamepad API対応ゲーム向け",
            "FPS・マウス移動量を送ります",
            "ポインタ移動タイプ"
    };

    private TouchpadSettings() {
    }

    public static int getSize(SharedPreferences preferences) {
        if (preferences == null) {
            return SIZE_MEDIUM;
        }
        int size = preferences.getInt(KEY_SIZE, SIZE_MEDIUM);
        return size >= SIZE_SMALL && size <= SIZE_LARGE ? size : SIZE_MEDIUM;
    }

    public static boolean isAlwaysVisible(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(KEY_ALWAYS_VISIBLE, false);
    }

    public static void setSize(SharedPreferences preferences, int size) {
        if (preferences == null) {
            return;
        }
        int safe = size >= SIZE_SMALL && size <= SIZE_LARGE ? size : SIZE_MEDIUM;
        preferences.edit().putInt(KEY_SIZE, safe).apply();
    }

    public static void setAlwaysVisible(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(KEY_ALWAYS_VISIBLE, enabled).apply();
    }

    public static boolean isScreenTouchBlocked(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(KEY_BLOCK_SCREEN_TOUCH, false);
    }

    public static void setScreenTouchBlocked(SharedPreferences preferences, boolean blocked) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(KEY_BLOCK_SCREEN_TOUCH, blocked).apply();
    }

    public static boolean isStickEnabled(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(KEY_STICK_ENABLED, true);
    }

    public static void setStickEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(KEY_STICK_ENABLED, enabled).apply();
    }

    public static int getStickMode(SharedPreferences preferences) {
        if (preferences == null) {
            return STICK_AUTO;
        }
        int mode = preferences.getInt(KEY_STICK_MODE, STICK_AUTO);
        return mode >= 0 && mode < STICK_MODE_COUNT ? mode : STICK_AUTO;
    }

    public static void setStickMode(SharedPreferences preferences, int mode) {
        if (preferences == null) {
            return;
        }
        int safe = mode >= 0 && mode < STICK_MODE_COUNT ? mode : STICK_AUTO;
        preferences.edit().putInt(KEY_STICK_MODE, safe).apply();
    }

    public static int getStickSide(SharedPreferences preferences) {
        if (preferences == null) {
            return SIDE_LEFT;
        }
        return preferences.getInt(KEY_STICK_SIDE, SIDE_LEFT) == SIDE_RIGHT ? SIDE_RIGHT : SIDE_LEFT;
    }

    public static void setStickSide(SharedPreferences preferences, int side) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putInt(KEY_STICK_SIDE, side == SIDE_RIGHT ? SIDE_RIGHT : SIDE_LEFT).apply();
    }

    public static String stickLabel(int mode) {
        return mode >= 0 && mode < STICK_MODE_COUNT ? STICK_LABELS[mode] : STICK_LABELS[0];
    }

    public static String stickShortLabel(int mode) {
        return mode >= 0 && mode < STICK_MODE_COUNT ? STICK_SHORT_LABELS[mode] : STICK_SHORT_LABELS[0];
    }

    public static String stickDescription(int mode) {
        return mode >= 0 && mode < STICK_MODE_COUNT ? STICK_DESCRIPTIONS[mode] : STICK_DESCRIPTIONS[0];
    }
}
