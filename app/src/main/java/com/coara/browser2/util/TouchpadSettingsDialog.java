package com.coara.browser2.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

public final class TouchpadSettingsDialog {
    public interface Callback {
        void onSizeChanged();

        void onStickChanged();
    }

    private TouchpadSettingsDialog() {
    }

    public static void show(Activity activity, SharedPreferences prefs, Callback callback) {
        if (activity == null || activity.isFinishing() || prefs == null) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density + 0.5f);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, 0, padding, 0);

        TextView title = new TextView(activity);
        title.setText("ウィンドウサイズ");
        title.setTextSize(16);
        title.setPadding(0, 0, 0, (int) (6 * density + 0.5f));
        container.addView(title, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        RadioButton small = new RadioButton(activity);
        RadioButton medium = new RadioButton(activity);
        RadioButton large = new RadioButton(activity);
        small.setId(View.generateViewId());
        medium.setId(View.generateViewId());
        large.setId(View.generateViewId());
        small.setText("小\n画面端に収まる");
        medium.setText("中\n画面両端に収まる");
        large.setText("大\n画面両端＋画面の縦半分まで使う");
        group.addView(small, new RadioGroup.LayoutParams(-1, -2));
        group.addView(medium, new RadioGroup.LayoutParams(-1, -2));
        group.addView(large, new RadioGroup.LayoutParams(-1, -2));
        int currentSize = TouchpadSettings.getSize(prefs);
        if (currentSize == TouchpadSettings.SIZE_SMALL) {
            small.setChecked(true);
        } else if (currentSize == TouchpadSettings.SIZE_LARGE) {
            large.setChecked(true);
        } else {
            medium.setChecked(true);
        }
        container.addView(group, new LinearLayout.LayoutParams(-1, -2));

        SwitchMaterial always = new SwitchMaterial(activity);
        always.setText("常に表示有効");
        always.setTextSize(16);
        always.setChecked(TouchpadSettings.isAlwaysVisible(prefs));
        LinearLayout.LayoutParams alwaysParams = new LinearLayout.LayoutParams(-1, -2);
        alwaysParams.topMargin = (int) (12 * density + 0.5f);
        container.addView(always, alwaysParams);

        SwitchMaterial stickSwitch = new SwitchMaterial(activity);
        stickSwitch.setText("アナログスティックを表示");
        stickSwitch.setTextSize(16);
        stickSwitch.setChecked(TouchpadSettings.isStickEnabled(prefs));
        LinearLayout.LayoutParams stickParams = new LinearLayout.LayoutParams(-1, -2);
        stickParams.topMargin = (int) (12 * density + 0.5f);
        container.addView(stickSwitch, stickParams);

        TextView sideTitle = new TextView(activity);
        sideTitle.setText("スティックの位置");
        sideTitle.setTextSize(16);
        sideTitle.setPadding(0, (int) (12 * density + 0.5f), 0, (int) (6 * density + 0.5f));
        container.addView(sideTitle, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup sideGroup = new RadioGroup(activity);
        sideGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton sideLeft = new RadioButton(activity);
        RadioButton sideRight = new RadioButton(activity);
        sideLeft.setId(View.generateViewId());
        sideRight.setId(View.generateViewId());
        sideLeft.setText("左");
        sideRight.setText("右");
        sideGroup.addView(sideLeft, new RadioGroup.LayoutParams(0, -2, 1f));
        sideGroup.addView(sideRight, new RadioGroup.LayoutParams(0, -2, 1f));
        if (TouchpadSettings.getStickSide(prefs) == TouchpadSettings.SIDE_RIGHT) {
            sideRight.setChecked(true);
        } else {
            sideLeft.setChecked(true);
        }
        sideLeft.setEnabled(stickSwitch.isChecked());
        sideRight.setEnabled(stickSwitch.isChecked());
        container.addView(sideGroup, new LinearLayout.LayoutParams(-1, -2));

        TextView modeTitle = new TextView(activity);
        modeTitle.setText("スティックの操作モード");
        modeTitle.setTextSize(16);
        modeTitle.setPadding(0, (int) (12 * density + 0.5f), 0, (int) (6 * density + 0.5f));
        container.addView(modeTitle, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup modeGroup = new RadioGroup(activity);
        modeGroup.setOrientation(LinearLayout.VERTICAL);
        int[] modeIds = new int[TouchpadSettings.STICK_MODE_COUNT];
        int currentMode = TouchpadSettings.getStickMode(prefs);
        for (int i = 0; i < TouchpadSettings.STICK_MODE_COUNT; i++) {
            RadioButton button = new RadioButton(activity);
            modeIds[i] = View.generateViewId();
            button.setId(modeIds[i]);
            button.setText(TouchpadSettings.stickLabel(i) + "\n" + TouchpadSettings.stickDescription(i));
            button.setTextSize(14);
            modeGroup.addView(button, new RadioGroup.LayoutParams(-1, -2));
            if (i == currentMode) {
                button.setChecked(true);
            }
        }
        modeGroup.setEnabled(stickSwitch.isChecked());
        for (int i = 0; i < modeGroup.getChildCount(); i++) {
            modeGroup.getChildAt(i).setEnabled(stickSwitch.isChecked());
        }
        container.addView(modeGroup, new LinearLayout.LayoutParams(-1, -2));

        group.setOnCheckedChangeListener((g, id) -> {
            int selected = id == small.getId() ? TouchpadSettings.SIZE_SMALL : id == large.getId() ? TouchpadSettings.SIZE_LARGE : TouchpadSettings.SIZE_MEDIUM;
            TouchpadSettings.setSize(prefs, selected);
            if (callback != null) {
                callback.onSizeChanged();
            }
        });
        always.setOnCheckedChangeListener((button, checked) -> TouchpadSettings.setAlwaysVisible(prefs, checked));
        stickSwitch.setOnCheckedChangeListener((button, checked) -> {
            TouchpadSettings.setStickEnabled(prefs, checked);
            sideLeft.setEnabled(checked);
            sideRight.setEnabled(checked);
            for (int i = 0; i < modeGroup.getChildCount(); i++) {
                modeGroup.getChildAt(i).setEnabled(checked);
            }
            if (callback != null) {
                callback.onStickChanged();
            }
        });
        sideGroup.setOnCheckedChangeListener((g, id) -> {
            TouchpadSettings.setStickSide(prefs, id == sideRight.getId() ? TouchpadSettings.SIDE_RIGHT : TouchpadSettings.SIDE_LEFT);
            if (callback != null) {
                callback.onStickChanged();
            }
        });
        modeGroup.setOnCheckedChangeListener((g, id) -> {
            for (int i = 0; i < modeIds.length; i++) {
                if (modeIds[i] == id) {
                    TouchpadSettings.setStickMode(prefs, i);
                    if (callback != null) {
                        callback.onStickChanged();
                    }
                    return;
                }
            }
        });

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(container, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("タッチパッド設定")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .create();
        dialog.show();
    }
}
