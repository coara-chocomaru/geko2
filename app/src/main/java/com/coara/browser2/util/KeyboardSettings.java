package com.coara.browser2.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.coara.browser2.gecko.BrowserView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Map;
import java.util.WeakHashMap;

public final class KeyboardSettings {
    public static final String KEY_ALWAYS_VISIBLE = "keyboard_always_visible";
    public static final String KEY_MODE = "keyboard_mode";
    public static final String KEY_PAGE_INPUT_MODE = "keyboard_page_input_mode";
    public static final int MODE_NORMAL = 0;
    public static final int MODE_NUMBER = 1;
    public static final int MODE_ENGLISH = 2;
    public static final int PAGE_MODE_NORMAL = BrowserView.PAGE_MODE_NORMAL;
    public static final int PAGE_MODE_GAME = BrowserView.PAGE_MODE_GAME;
    public static final int PAGE_MODE_TYPING = BrowserView.PAGE_MODE_TYPING;

    private static final Map<BrowserView, Runnable> pendingKeyboardShows = new WeakHashMap<>();
    private static final Map<Activity, Integer> originalWindowSoftInputModes = new WeakHashMap<>();
    private static final Map<EditText, EditorDefaults> editorDefaults = new WeakHashMap<>();

    private static final class EditorDefaults {
        final int inputType;
        final int imeOptions;

        EditorDefaults(EditText editor) {
            inputType = editor.getInputType();
            imeOptions = editor.getImeOptions();
        }
    }

    private KeyboardSettings() {}

    public static boolean isAlwaysVisible(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(KEY_ALWAYS_VISIBLE, false);
    }

    public static int getMode(SharedPreferences preferences) {
        int mode = preferences != null ? preferences.getInt(KEY_MODE, MODE_NORMAL) : MODE_NORMAL;
        return mode >= MODE_NORMAL && mode <= MODE_ENGLISH ? mode : MODE_NORMAL;
    }

    public static int getPageInputMode(SharedPreferences preferences) {
        if (preferences == null) return PAGE_MODE_NORMAL;
        if (preferences.contains(KEY_PAGE_INPUT_MODE)) {
            int mode = preferences.getInt(KEY_PAGE_INPUT_MODE, PAGE_MODE_NORMAL);
            return mode == PAGE_MODE_TYPING ? PAGE_MODE_TYPING : PAGE_MODE_NORMAL;
        }
        return PAGE_MODE_NORMAL;
    }

    public static void configureWebView(BrowserView webView, SharedPreferences preferences) {
        if (!(webView instanceof BrowserView)) return;
        BrowserView gameWebView = (BrowserView) webView;
        boolean enabled = isAlwaysVisible(preferences);
        gameWebView.setInputBehaviorMode(enabled ? getPageInputMode(preferences) : PAGE_MODE_NORMAL);
        gameWebView.setKeyboardMode(enabled ? getMode(preferences) : MODE_NORMAL);
        gameWebView.setAlwaysVisibleKeyboard(enabled);
        if (enabled && getPageInputMode(preferences) != PAGE_MODE_NORMAL) {
            gameWebView.installKeyboardEventTracking();
        } else {
            gameWebView.setKeyboardEventTrackingEnabled(false);
        }
    }

    public static void refreshWebViewInput(Activity activity, BrowserView webView, SharedPreferences preferences) {
        if (!isActivityUsable(activity) || webView == null) return;
        boolean wasAlwaysVisible = webView instanceof BrowserView
                && ((BrowserView) webView).isAlwaysVisibleKeyboard();
        if (isAlwaysVisible(preferences)) {
            showKeyboardForWebView(activity, webView, preferences);
            return;
        }
        configureWebView(webView, preferences);
        if (wasAlwaysVisible) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                if (webView.isAttachedToWindow()) imm.restartInput(webView);
            }
            restoreWindowSoftInputMode(activity);
        }
    }

    public static void applyToEditor(EditText editor, SharedPreferences preferences) {
        if (editor == null) return;
        EditorDefaults defaults = editorDefaults.get(editor);
        if (defaults == null) {
            defaults = new EditorDefaults(editor);
            editorDefaults.put(editor, defaults);
        }
        int selectionStart = editor.getSelectionStart();
        int selectionEnd = editor.getSelectionEnd();
        int inputType;
        int imeOptions;
        if (!isAlwaysVisible(preferences)) {
            inputType = defaults.inputType;
            imeOptions = defaults.imeOptions;
        } else {
            int mode = getMode(preferences);
            imeOptions = EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            if (mode == MODE_NUMBER) {
                inputType = InputType.TYPE_CLASS_NUMBER;
            } else {
                inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                if (mode == MODE_ENGLISH && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    imeOptions |= EditorInfo.IME_FLAG_FORCE_ASCII;
                }
            }
        }
        boolean inputTypeChanged = editor.getInputType() != inputType;
        boolean imeOptionsChanged = editor.getImeOptions() != imeOptions;
        if (inputTypeChanged) editor.setInputType(inputType);
        if (imeOptionsChanged) editor.setImeOptions(imeOptions);
        int length = editor.length();
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart <= length && selectionEnd <= length) {
            editor.setSelection(selectionStart, selectionEnd);
        }
        if ((inputTypeChanged || imeOptionsChanged) && editor.isAttachedToWindow() && editor.hasFocus()) {
            InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(editor);
        }
    }

    private static void applyAlwaysVisibleWindowMode(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        synchronized (originalWindowSoftInputModes) {
            if (!originalWindowSoftInputModes.containsKey(activity)) {
                originalWindowSoftInputModes.put(activity, activity.getWindow().getAttributes().softInputMode);
            }
        }
        activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private static void restoreWindowSoftInputMode(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        Integer original;
        synchronized (originalWindowSoftInputModes) {
            original = originalWindowSoftInputModes.remove(activity);
        }
        if (original != null) activity.getWindow().setSoftInputMode(original);
    }

    public static void showKeyboard(Activity activity, EditText editor, SharedPreferences preferences) {
        if (!isActivityUsable(activity) || editor == null) return;
        if (!isAlwaysVisible(preferences)) {
            applyToEditor(editor, preferences);
            restoreWindowSoftInputMode(activity);
            return;
        }
        applyToEditor(editor, preferences);
        applyAlwaysVisibleWindowMode(activity);
        editor.setFocusableInTouchMode(true);
        if (!editor.hasFocus()) editor.requestFocus();
        editor.postDelayed(() -> {
            if (!isActivityUsable(activity) || !editor.hasFocus() || !activity.hasWindowFocus()) return;
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }, 120L);
    }

    public static void showKeyboardForWebView(Activity activity, BrowserView webView, SharedPreferences preferences) {
        if (!isActivityUsable(activity) || webView == null) return;
        if (!isAlwaysVisible(preferences)) {
            refreshWebViewInput(activity, webView, preferences);
            return;
        }
        configureWebView(webView, preferences);
        cancelPendingShow(webView);
        applyAlwaysVisibleWindowMode(activity);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        if (!webView.hasFocus()) webView.requestFocus();
        Runnable show = () -> {
            pendingKeyboardShows.remove(webView);
            if (!isActivityUsable(activity) || !isAlwaysVisible(preferences) || !activity.hasWindowFocus()) return;
            if (webView.getWindowToken() == null || webView.getParent() == null) return;
            if (!webView.hasFocus()) webView.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.restartInput(webView);
                imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
            }
        };
        pendingKeyboardShows.put(webView, show);
        webView.postDelayed(show, 140L);
    }

    private static void cancelPendingShow(BrowserView webView) {
        Runnable pending = pendingKeyboardShows.remove(webView);
        if (pending != null) webView.removeCallbacks(pending);
    }

    private static boolean isActivityUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    public static void hideKeyboardForWebView(Activity activity, BrowserView webView) {
        if (activity == null || webView == null) return;
        cancelPendingShow(webView);
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
    }

    public static void onResumeWebView(Activity activity, BrowserView webView, SharedPreferences preferences) {
        refreshWebViewInput(activity, webView, preferences);
    }

    public static void hideKeyboard(Activity activity, EditText editor) {
        if (activity == null || editor == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        restoreWindowSoftInputMode(activity);
        editor.clearFocus();
    }

    public static void onResume(Activity activity, EditText editor, SharedPreferences preferences) {
        if (isAlwaysVisible(preferences)) showKeyboard(activity, editor, preferences);
    }

    public static String getModeLabel(int mode) {
        if (mode == MODE_NUMBER) return "数字だけ";
        if (mode == MODE_ENGLISH) return "英語だけ";
        return "通常";
    }

    public static String getPageInputModeLabel(int mode) {
        if (mode == PAGE_MODE_TYPING) return "タイピングモード";
        return "通常";
    }

    public static void showSettingsDialog(Activity activity, EditText editor, SharedPreferences preferences) {
        showSettingsDialog(activity, editor, null, preferences);
    }

    public static void showSettingsDialog(Activity activity, EditText editor, BrowserView webView, SharedPreferences preferences) {
        if (!isActivityUsable(activity) || editor == null || preferences == null) return;

        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * density + 0.5f);
        root.setPadding(padding, 0, padding, 0);

        SwitchMaterial alwaysSwitch = new SwitchMaterial(activity);
        alwaysSwitch.setText("常に表示");
        alwaysSwitch.setTextSize(16);
        alwaysSwitch.setChecked(isAlwaysVisible(preferences));
        root.addView(alwaysSwitch, new LinearLayout.LayoutParams(-1, -2));

        TextView pageModeTitle = new TextView(activity);
        pageModeTitle.setText("ページ入力モード");
        pageModeTitle.setTextSize(16);
        pageModeTitle.setPadding(0, (int) (18 * density + 0.5f), 0, (int) (6 * density + 0.5f));
        root.addView(pageModeTitle, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup pageModeGroup = new RadioGroup(activity);
        pageModeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton pageNormal = new RadioButton(activity);
        pageNormal.setId(View.generateViewId());
        pageNormal.setText("通常（Web標準入力）");
        RadioButton pageTyping = new RadioButton(activity);
        pageTyping.setId(View.generateViewId());
        pageTyping.setText("タイピングモード（ローマ字入力）");
        pageModeGroup.addView(pageNormal, new RadioGroup.LayoutParams(-1, -2));
        pageModeGroup.addView(pageTyping, new RadioGroup.LayoutParams(-1, -2));
        int pageMode = getPageInputMode(preferences);
        if (pageMode == PAGE_MODE_TYPING) pageTyping.setChecked(true);
        else pageNormal.setChecked(true);
        root.addView(pageModeGroup, new LinearLayout.LayoutParams(-1, -2));

        TextView charModeTitle = new TextView(activity);
        charModeTitle.setText("入力文字種");
        charModeTitle.setTextSize(16);
        charModeTitle.setPadding(0, (int) (18 * density + 0.5f), 0, (int) (6 * density + 0.5f));
        root.addView(charModeTitle, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup charModeGroup = new RadioGroup(activity);
        charModeGroup.setOrientation(LinearLayout.VERTICAL);
        RadioButton charNormal = new RadioButton(activity);
        charNormal.setId(View.generateViewId());
        charNormal.setText("通常");
        RadioButton charNumber = new RadioButton(activity);
        charNumber.setId(View.generateViewId());
        charNumber.setText("数字だけ");
        RadioButton charEnglish = new RadioButton(activity);
        charEnglish.setId(View.generateViewId());
        charEnglish.setText("英語だけ");
        charModeGroup.addView(charNormal, new RadioGroup.LayoutParams(-1, -2));
        charModeGroup.addView(charNumber, new RadioGroup.LayoutParams(-1, -2));
        charModeGroup.addView(charEnglish, new RadioGroup.LayoutParams(-1, -2));
        int charMode = getMode(preferences);
        if (charMode == MODE_NUMBER) charNumber.setChecked(true);
        else if (charMode == MODE_ENGLISH) charEnglish.setChecked(true);
        else charNormal.setChecked(true);
        root.addView(charModeGroup, new LinearLayout.LayoutParams(-1, -2));

        pageModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selected = checkedId == pageTyping.getId() ? PAGE_MODE_TYPING : PAGE_MODE_NORMAL;
            preferences.edit().putInt(KEY_PAGE_INPUT_MODE, selected).apply();
            refreshWebViewInput(activity, webView, preferences);
        });

        charModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selected = checkedId == charNumber.getId() ? MODE_NUMBER : checkedId == charEnglish.getId() ? MODE_ENGLISH : MODE_NORMAL;
            preferences.edit().putInt(KEY_MODE, selected).apply();
            applyToEditor(editor, preferences);
            refreshWebViewInput(activity, webView, preferences);
        });

        alwaysSwitch.setOnCheckedChangeListener((button, checked) -> {
            boolean wasEnabled = isAlwaysVisible(preferences);
            SharedPreferences.Editor update = preferences.edit().putBoolean(KEY_ALWAYS_VISIBLE, checked);
            boolean initializePageMode = checked && !preferences.contains(KEY_PAGE_INPUT_MODE);
            if (initializePageMode) update.putInt(KEY_PAGE_INPUT_MODE, PAGE_MODE_NORMAL);
            update.apply();
            if (initializePageMode) pageModeGroup.check(pageNormal.getId());
            applyToEditor(editor, preferences);
            if (checked) {
                if (webView instanceof BrowserView) refreshWebViewInput(activity, webView, preferences);
                else showKeyboard(activity, editor, preferences);
            } else {
                if (webView != null) refreshWebViewInput(activity, webView, preferences);
                if (wasEnabled && !(webView instanceof BrowserView)) {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
                    restoreWindowSoftInputMode(activity);
                }
            }
        });

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.addView(root);

        androidx.appcompat.app.AlertDialog settingsDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("キーボード設定")
                .setView(scrollView)
                .setPositiveButton("閉じる", null)
                .show();
        settingsDialog.setOnDismissListener(dialogInterface -> {
            refreshWebViewInput(activity, webView, preferences);
            if (isAlwaysVisible(preferences) && !(webView instanceof BrowserView)) {
                showKeyboard(activity, editor, preferences);
            }
        });
    }
}
