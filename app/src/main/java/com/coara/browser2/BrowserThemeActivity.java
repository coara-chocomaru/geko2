package com.coara.browser2;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.coara.browser2.util.BrowserThemeManager;
import com.coara.browser2.util.UiThread;

import java.util.ArrayList;
import java.util.List;

public class BrowserThemeActivity extends AppCompatActivity {
    private final List<BrowserThemeManager.ThemeInfo> items = new ArrayList<>();
    private ThemeAdapter adapter;
    private TextView emptyView;
    private ActivityResultLauncher<Intent> pickLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser_theme);

        RecyclerView list = findViewById(R.id.themeListView);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThemeAdapter();
        list.setAdapter(adapter);
        emptyView = findViewById(R.id.themeEmptyText);

        pickLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onPickResult);
        findViewById(R.id.themeInstallButton).setOnClickListener(v -> startPick());
        findViewById(R.id.themeDisableButton).setOnClickListener(v -> {
            BrowserThemeManager.clearActiveTheme(this);
            refresh();
            Toast.makeText(this, "デフォルトテーマに戻しました", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private int refreshGeneration;

    private void refresh() {
        final int generation = ++refreshGeneration;
        UiThread.io().execute(() -> {
            final List<BrowserThemeManager.ThemeInfo> loaded = BrowserThemeManager.list(this);
            runOnUiThread(() -> {
                if (isFinishing() || generation != refreshGeneration) {
                    return;
                }
                items.clear();
                items.addAll(loaded);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void startPick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            pickLauncher.launch(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            try {
                pickLauncher.launch(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, "ファイル選択を開始できません", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onPickResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }
        Toast.makeText(this, "テーマを検証しています…", Toast.LENGTH_SHORT).show();
        UiThread.io().execute(() -> {
            try {
                String name = queryDisplayName(uri);
                BrowserThemeManager.ThemeInfo theme = BrowserThemeManager.importFromUri(this, uri, name);
                runOnUiThread(() -> {
                    refresh();
                    showEnableDialog(theme);
                });
            } catch (Throwable t) {
                String message = t.getMessage();
                runOnUiThread(() -> showMessage("テーマを追加できません", message == null ? "テーマZIPを読み込めませんでした" : message));
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Throwable ignored) {
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "theme.zip" : segment;
    }

    private void showEnableDialog(BrowserThemeManager.ThemeInfo theme) {
        new AlertDialog.Builder(this)
                .setTitle(theme.name)
                .setMessage(buildDetails(theme))
                .setNegativeButton("あとで", null)
                .setPositiveButton("有効にする", (dialog, which) -> {
                    if (BrowserThemeManager.setActiveTheme(this, theme.fileName)) {
                        setResult(Activity.RESULT_OK);
                        Toast.makeText(this, "テーマを適用しました", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .show();
    }

    private void showDetails(BrowserThemeManager.ThemeInfo theme) {
        new AlertDialog.Builder(this)
                .setTitle(theme.name)
                .setMessage(buildDetails(theme))
                .setNegativeButton("閉じる", null)
                .setNeutralButton("削除", (dialog, which) -> confirmDelete(theme))
                .show();
    }

    private void confirmDelete(BrowserThemeManager.ThemeInfo theme) {
        new AlertDialog.Builder(this)
                .setTitle("テーマを削除")
                .setMessage(theme.name + " を端末から削除しますか？")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (dialog, which) -> {
                    if (BrowserThemeManager.delete(this, theme)) {
                                    refresh();
                        Toast.makeText(this, "テーマを削除しました", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "テーマを削除できません", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private String buildDetails(BrowserThemeManager.ThemeInfo theme) {
        StringBuilder b = new StringBuilder();
        b.append("ID: ").append(theme.id).append('\n');
        b.append("バージョン: ").append(theme.version).append('\n');
        if (!theme.author.isEmpty()) b.append("作者: ").append(theme.author).append('\n');
        b.append("\n").append(theme.layout.describe());
        if (!theme.description.isEmpty()) b.append('\n').append(theme.description);
        String error = com.coara.browser2.util.BrowserThemeLayoutController.lastError();
        if (error != null) b.append("\n\n適用エラー: ").append(error);
        return b.toString();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("閉じる", null).show();
    }

    private final class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.Holder> {
        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_browser_theme, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            BrowserThemeManager.ThemeInfo theme = items.get(position);
            BrowserThemeManager.ThemeInfo activeTheme = BrowserThemeManager.getActiveTheme(BrowserThemeActivity.this);
            boolean active = activeTheme != null && theme.fileName.equals(activeTheme.fileName);
            holder.title.setText(theme.name);
            String meta = theme.layout.describe();
            if (!theme.author.isEmpty()) meta = theme.author + "  •  " + meta;
            holder.meta.setText(meta);
            holder.description.setText(theme.description.isEmpty() ? "カスタムブラウザテーマ" : theme.description);
            holder.swatch.setCardBackgroundColor(theme.palette.surface);
            holder.primaryDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.palette.primary));
            holder.switchView.setOnCheckedChangeListener(null);
            holder.switchView.setChecked(active);
            holder.switchView.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    if (BrowserThemeManager.setActiveTheme(BrowserThemeActivity.this, theme.fileName)) {
                        setResult(Activity.RESULT_OK);
                        Toast.makeText(BrowserThemeActivity.this, theme.name + " を適用", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        refresh();
                    }
                } else if (active) {
                    BrowserThemeManager.clearActiveTheme(BrowserThemeActivity.this);
                    refresh();
                }
            });
            holder.menu.setOnClickListener(v -> showDetails(theme));
            holder.itemView.setOnClickListener(v -> showDetails(theme));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            TextView title;
            TextView meta;
            TextView description;
            SwitchCompat switchView;
            TextView menu;
            MaterialCardView swatch;
            ImageView primaryDot;

            Holder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.themeTitle);
                meta = itemView.findViewById(R.id.themeMeta);
                description = itemView.findViewById(R.id.themeDescription);
                switchView = itemView.findViewById(R.id.themeSwitch);
                menu = itemView.findViewById(R.id.themeMenu);
                swatch = itemView.findViewById(R.id.themeSwatch);
                primaryDot = itemView.findViewById(R.id.themePrimaryDot);
            }
        }
    }
}
