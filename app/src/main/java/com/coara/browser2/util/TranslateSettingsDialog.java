package com.coara.browser2.util;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class TranslateSettingsDialog {

    private TranslateSettingsDialog() {
    }

    public static void show(Activity activity, boolean secret) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final int count = TranslateSettings.languageCount();
        String[] sourceLabels = new String[count + 1];
        sourceLabels[0] = "自動検出";
        String[] targetLabels = new String[count];
        for (int i = 0; i < count; i++) {
            sourceLabels[i + 1] = TranslateSettings.languageLabel(i);
            targetLabels[i] = TranslateSettings.languageLabel(i);
        }

        int pad = dp(activity, 20);
        int gap = dp(activity, 8);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(activity, 12), pad, 0);

        SwitchCompat autoSwitch = new SwitchCompat(activity);
        autoSwitch.setText("自動翻訳");
        autoSwitch.setChecked(TranslateSettings.isAutoTranslate(activity, secret));
        root.addView(autoSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView autoHint = new TextView(activity);
        autoHint.setText("オンにすると、翻訳先と異なる言語のページを読み込み後に自動で翻訳します。オフの場合はメニューの「ページを翻訳」で手動翻訳します。");
        autoHint.setTextSize(12f);
        autoHint.setAlpha(0.7f);
        autoHint.setPadding(0, gap / 2, 0, gap);
        root.addView(autoHint);

        TextView sourceTitle = new TextView(activity);
        sourceTitle.setText("翻訳元の言語");
        sourceTitle.setPadding(0, gap, 0, 0);
        root.addView(sourceTitle);

        Spinner sourceSpinner = new Spinner(activity);
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, sourceLabels);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);
        String savedSource = TranslateSettings.getSource(activity, secret);
        int sourceIndex = TranslateSettings.indexOf(savedSource);
        sourceSpinner.setSelection(sourceIndex >= 0 ? sourceIndex + 1 : 0);
        root.addView(sourceSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView targetTitle = new TextView(activity);
        targetTitle.setText("翻訳先の言語");
        targetTitle.setPadding(0, gap, 0, 0);
        root.addView(targetTitle);

        Spinner targetSpinner = new Spinner(activity);
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, targetLabels);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(targetAdapter);
        int targetIndex = TranslateSettings.indexOf(TranslateSettings.getTarget(activity, secret));
        targetSpinner.setSelection(Math.max(0, targetIndex));
        root.addView(targetSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (secret) {
            TextView secretHint = new TextView(activity);
            secretHint.setText("シークレットでは端末内翻訳のみ使用します。初回は言語モデルのダウンロードが必要です。");
            secretHint.setTextSize(12f);
            secretHint.setAlpha(0.7f);
            secretHint.setPadding(0, gap, 0, 0);
            root.addView(secretHint);
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("翻訳設定")
                .setView(root)
                .setPositiveButton("保存", (dialog, which) -> {
                    int selectedSource = sourceSpinner.getSelectedItemPosition();
                    String source = selectedSource <= 0
                            ? TranslateSettings.AUTO
                            : TranslateSettings.languageTag(selectedSource - 1);
                    String target = TranslateSettings.languageTag(Math.max(0, targetSpinner.getSelectedItemPosition()));
                    TranslateSettings.save(activity, secret, source, target, autoSwitch.isChecked());
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
