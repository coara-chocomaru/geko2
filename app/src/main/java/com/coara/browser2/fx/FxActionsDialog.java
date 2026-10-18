package com.coara.browser2.fx;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.List;

public final class FxActionsDialog {
    private FxActionsDialog() {
    }

    public static void show(final Activity activity, GeckoSession session) {
        final List<FxExtensionManager.ActionEntry> entries = FxExtensionManager.get().actionsFor(session);
        if (entries.isEmpty()) {
            Toast.makeText(activity, "利用できる拡張機能の操作はありません", Toast.LENGTH_SHORT).show();
            return;
        }
        final float density = activity.getResources().getDisplayMetrics().density;
        ArrayAdapter<FxExtensionManager.ActionEntry> adapter = new ArrayAdapter<FxExtensionManager.ActionEntry>(activity, 0, entries) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                FxExtensionManager.ActionEntry e = getItem(position);
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                int p = (int) (12 * density);
                row.setPadding(p, p, p, p);
                final ImageView icon = new ImageView(activity);
                int size = (int) (32 * density);
                row.addView(icon, new LinearLayout.LayoutParams(size, size));
                if (e.action.icon != null) {
                    e.action.icon.getBitmap(size).then(new GeckoResult.OnValueListener<Bitmap, Void>() {
                        @Override
                        public GeckoResult<Void> onValue(Bitmap value) {
                            if (value != null) {
                                icon.setImageBitmap(value);
                            }
                            return null;
                        }
                    }, new GeckoResult.OnExceptionListener<Void>() {
                        @Override
                        public GeckoResult<Void> onException(Throwable error) {
                            return null;
                        }
                    });
                }
                TextView label = new TextView(activity);
                String text = e.title();
                if (e.action.badgeText != null && !e.action.badgeText.isEmpty()) {
                    text += "  [" + e.action.badgeText + "]";
                }
                label.setText(text);
                label.setTextSize(16f);
                label.setEnabled(e.enabled());
                label.setPadding((int) (12 * density), 0, 0, 0);
                row.addView(label);
                return row;
            }
        };
        new AlertDialog.Builder(activity)
                .setTitle("拡張機能の操作")
                .setAdapter(adapter, (d, which) -> {
                    FxExtensionManager.ActionEntry e = entries.get(which);
                    if (e.enabled()) {
                        e.action.click();
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }
}
