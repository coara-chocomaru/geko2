package com.coara.browser2.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coara.browser2.R;
import com.coara.browser2.model.Bookmark;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BookmarkDialog {

    public interface Host {
        Bitmap favicon(String url);

        void open(String url);

        void openInNewTab(String url);

        void changed();
    }

    private static final String KEY_SORT = "bookmark_sort_mode";
    private static final int[] TILE_COLORS = {
        0xFF1A73E8, 0xFF188038, 0xFFD93025, 0xFFE37400, 0xFF9334E6, 0xFF129EAF, 0xFFC2185B, 0xFF5F6368
    };
    private static final long UNDO_MS = 6000L;

    private final Activity activity;
    private final List<Bookmark> bookmarks;
    private final SharedPreferences pref;
    private final Host host;

    private AlertDialog dialog;
    private Adapter adapter;
    private View root;
    private TextView countView;
    private TextView emptyView;
    private MaterialButton sortButton;
    private MaterialButton defaultFolderButton;
    private ChipGroup chipGroup;
    private HorizontalScrollView chipScroll;
    private View undoBar;
    private TextView undoText;

    private String query = "";
    private String folderFilter;
    private int sortMode;
    private Bookmark undoBookmark;
    private int undoIndex;
    private final Runnable hideUndo = new Runnable() {
        @Override
        public void run() {
            undoBookmark = null;
            if (undoBar != null) {
                undoBar.setVisibility(View.GONE);
            }
        }
    };

    private BookmarkDialog(Activity activity, List<Bookmark> bookmarks, SharedPreferences pref, Host host) {
        this.activity = activity;
        this.bookmarks = bookmarks;
        this.pref = pref;
        this.host = host;
        this.sortMode = pref.getInt(KEY_SORT, BookmarkStore.SORT_MANUAL);
        if (sortMode < 0 || sortMode >= BookmarkStore.SORT_LABELS.length) {
            sortMode = BookmarkStore.SORT_MANUAL;
        }
    }

    public static void show(Activity activity, List<Bookmark> bookmarks, SharedPreferences pref, Host host) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new BookmarkDialog(activity, bookmarks, pref, host).display();
    }

    public static void showEditor(Activity activity, List<Bookmark> bookmarks, int index, Host host, Runnable afterSave) {
        if (activity == null || activity.isFinishing() || index < 0 || index >= bookmarks.size()) {
            return;
        }
        final Bookmark current = bookmarks.get(index);
        View editView = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_bookmark, null);
        final EditText etTitle = editView.findViewById(R.id.editTitle);
        final EditText etUrl = editView.findViewById(R.id.editUrl);
        final MaterialAutoCompleteTextView etFolder = editView.findViewById(R.id.editFolder);
        etTitle.setText(current.getTitle());
        etUrl.setText(current.getUrl());
        etFolder.setText(current.getFolder());
        List<String> folders = BookmarkStore.allFolders(activity, bookmarks);
        if (!folders.isEmpty()) {
            etFolder.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, folders));
            etFolder.setThreshold(0);
            etFolder.setOnClickListener(v -> etFolder.showDropDown());
            etFolder.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    etFolder.showDropDown();
                }
            });
        }
        new MaterialAlertDialogBuilder(activity)
            .setTitle("ブックマークの編集")
            .setView(editView)
            .setPositiveButton("保存", (d, which) -> {
                String newUrl = etUrl.getText().toString().trim();
                if (newUrl.isEmpty()) {
                    Toast.makeText(activity, "URLを入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newUrl.contains("://") && !newUrl.startsWith("about:") && !newUrl.startsWith("file:")) {
                    newUrl = "http://" + newUrl;
                }
                int pos = BookmarkStore.indexOfInstance(bookmarks, current);
                if (pos < 0) {
                    return;
                }
                String newTitle = etTitle.getText().toString().trim();
                String newFolder = BookmarkStore.cleanFolderName(etFolder.getText().toString());
                bookmarks.set(pos, current.with(newTitle, newUrl, newFolder));
                host.changed();
                if (afterSave != null) {
                    afterSave.run();
                }
                Toast.makeText(activity, "保存しました", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private void display() {
        root = LayoutInflater.from(activity).inflate(R.layout.dialog_bookmarks, null);
        root.setMinimumHeight((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f));
        final EditText search = root.findViewById(R.id.bookmarkSearch);
        countView = root.findViewById(R.id.bookmarkCount);
        emptyView = root.findViewById(R.id.bookmarkEmpty);
        sortButton = root.findViewById(R.id.bookmarkSort);
        defaultFolderButton = root.findViewById(R.id.bookmarkDefaultFolder);
        defaultFolderButton.setOnClickListener(v -> showDefaultFolderDialog());
        chipGroup = root.findViewById(R.id.bookmarkFolderChips);
        chipScroll = root.findViewById(R.id.bookmarkFolderScroll);
        undoBar = root.findViewById(R.id.bookmarkUndoBar);
        undoText = root.findViewById(R.id.bookmarkUndoText);
        RecyclerView list = root.findViewById(R.id.bookmarkList);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setHasFixedSize(false);
        adapter = new Adapter();
        list.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString();
                refresh();
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        sortButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
            .setTitle("並べ替え")
            .setSingleChoiceItems(BookmarkStore.SORT_LABELS, sortMode, (d, which) -> {
                sortMode = which;
                pref.edit().putInt(KEY_SORT, which).apply();
                d.dismiss();
                refresh();
            })
            .setNegativeButton("キャンセル", null)
            .show());
        root.findViewById(R.id.bookmarkUndo).setOnClickListener(v -> undoDelete());

        dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle("ブックマーク")
            .setView(root)
            .setNegativeButton("閉じる", null)
            .create();
        dialog.setOnDismissListener(d -> root.removeCallbacks(hideUndo));
        rebuildChips();
        refresh();
        dialog.show();
    }

    private List<String> folderNames() {
        return BookmarkStore.allFolders(activity, bookmarks);
    }

    private void rebuildChips() {
        List<String> folders = folderNames();
        if (folderFilter != null && !folderFilter.isEmpty() && !folders.contains(folderFilter)) {
            folderFilter = null;
        }
        chipGroup.setOnCheckedStateChangeListener(null);
        chipGroup.removeAllViews();
        chipScroll.setVisibility(View.VISIBLE);
        addChip("すべて " + bookmarks.size(), null);
        if (!folders.isEmpty()) {
            addChip("未分類 " + BookmarkStore.countIn(bookmarks, ""), "");
        }
        for (String folder : folders) {
            String star = folder.equals(BookmarkStore.getDefaultFolder(activity)) ? "★ " : "";
            Chip chip = addChip(star + folder + " " + BookmarkStore.countIn(bookmarks, folder), folder);
            chip.setOnLongClickListener(v -> {
                showFolderMenu(v, folder);
                return true;
            });
        }
        Chip add = new Chip(activity);
        add.setId(View.generateViewId());
        add.setText("＋ フォルダ");
        add.setCheckable(false);
        add.setOnClickListener(v -> promptNewFolder(null));
        chipGroup.addView(add);
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            View chip = group.findViewById(ids.get(0));
            Object tag = chip == null ? null : chip.getTag();
            folderFilter = tag instanceof String ? (String) tag : null;
            refresh();
        });
    }

    private Chip addChip(String label, String folder) {
        Chip chip = new Chip(activity);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(folder);
        chip.setCheckable(true);
        chip.setChecked(folder == null ? folderFilter == null : folder.equals(folderFilter));
        chipGroup.addView(chip);
        return chip;
    }

    private void showDefaultFolderDialog() {
        final List<String> folders = folderNames();
        final String[] labels = new String[folders.size() + 1];
        labels[0] = "（フォルダなし）";
        String current = BookmarkStore.getDefaultFolder(activity);
        int checked = 0;
        for (int i = 0; i < folders.size(); i++) {
            labels[i + 1] = folders.get(i);
            if (folders.get(i).equals(current)) {
                checked = i + 1;
            }
        }
        new MaterialAlertDialogBuilder(activity)
            .setTitle("新規ブックマークの保存先")
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                BookmarkStore.setDefaultFolder(activity, which == 0 ? "" : folders.get(which - 1));
                d.dismiss();
                rebuildChips();
                refresh();
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private String defaultFolderLabel() {
        String d = BookmarkStore.getDefaultFolder(activity);
        return d.isEmpty() ? "保存先: なし" : "保存先: " + d;
    }

    private void persistExtraFolders(List<String> names) {
        BookmarkStore.saveExtraFolders(activity, names);
    }

    private void promptNewFolder(final Bookmark moveTarget) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("フォルダ名");
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        FrameLayoutHolder holder = new FrameLayoutHolder(activity, input, pad);
        new MaterialAlertDialogBuilder(activity)
            .setTitle("新しいフォルダ")
            .setView(holder.view)
            .setPositiveButton("作成", (d, w) -> {
                String name = BookmarkStore.cleanFolderName(input.getText().toString());
                if (name.isEmpty()) {
                    Toast.makeText(activity, "フォルダ名を入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> extras = BookmarkStore.loadExtraFolders(activity);
                if (!extras.contains(name)) {
                    extras.add(name);
                    persistExtraFolders(extras);
                }
                if (moveTarget != null) {
                    moveTo(moveTarget, name);
                } else {
                    folderFilter = name;
                    rebuildChips();
                    refresh();
                }
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private static final class FrameLayoutHolder {
        final android.widget.FrameLayout view;

        FrameLayoutHolder(Context context, View child, int pad) {
            view = new android.widget.FrameLayout(context);
            view.setPadding(pad, pad / 2, pad, 0);
            view.addView(child, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showFolderMenu(View anchor, final String folder) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(Menu.NONE, 1, 1, "名前を変更");
        menu.getMenu().add(Menu.NONE, 2, 2, "フォルダを削除");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                promptRenameFolder(folder);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDeleteFolder(folder);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void promptRenameFolder(final String folder) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(folder);
        input.setSelection(folder.length());
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        FrameLayoutHolder holder = new FrameLayoutHolder(activity, input, pad);
        new MaterialAlertDialogBuilder(activity)
            .setTitle("フォルダ名を変更")
            .setView(holder.view)
            .setPositiveButton("変更", (d, w) -> {
                String name = BookmarkStore.cleanFolderName(input.getText().toString());
                if (name.isEmpty() || name.equals(folder)) {
                    return;
                }
                BookmarkStore.renameFolder(bookmarks, folder, name);
                if (folder.equals(BookmarkStore.getDefaultFolder(activity))) {
                    BookmarkStore.setDefaultFolder(activity, name);
                }
                List<String> extras = BookmarkStore.loadExtraFolders(activity);
                extras.remove(folder);
                if (!extras.contains(name)) {
                    extras.add(name);
                }
                persistExtraFolders(extras);
                if (folder.equals(folderFilter)) {
                    folderFilter = name;
                }
                host.changed();
                rebuildChips();
                refresh();
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private void confirmDeleteFolder(final String folder) {
        final int count = BookmarkStore.countIn(bookmarks, folder);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle("「" + folder + "」を削除")
            .setMessage(count == 0 ? "空のフォルダを削除します。" : count + "件のブックマークがあります。")
            .setNegativeButton("キャンセル", null);
        if (count == 0) {
            builder.setPositiveButton("削除", (d, w) -> removeFolder(folder, false));
        } else {
            builder.setPositiveButton("未分類へ移動して削除", (d, w) -> removeFolder(folder, false));
            builder.setNeutralButton("中身ごと削除", (d, w) -> removeFolder(folder, true));
        }
        builder.show();
    }

    private void removeFolder(String folder, boolean deleteContents) {
        BookmarkStore.removeFolder(bookmarks, folder, deleteContents);
        if (folder.equals(BookmarkStore.getDefaultFolder(activity))) {
            BookmarkStore.setDefaultFolder(activity, "");
        }
        List<String> extras = BookmarkStore.loadExtraFolders(activity);
        extras.remove(folder);
        persistExtraFolders(extras);
        if (folder.equals(folderFilter)) {
            folderFilter = null;
        }
        host.changed();
        rebuildChips();
        refresh();
    }

    private void showMoveDialog(final Bookmark bookmark) {
        final List<String> folders = folderNames();
        final String[] labels = new String[folders.size() + 2];
        labels[0] = "（フォルダなし）";
        int checked = bookmark.getFolder().isEmpty() ? 0 : -1;
        for (int i = 0; i < folders.size(); i++) {
            labels[i + 1] = folders.get(i);
            if (folders.get(i).equals(bookmark.getFolder())) {
                checked = i + 1;
            }
        }
        labels[labels.length - 1] = "＋ 新しいフォルダ…";
        new MaterialAlertDialogBuilder(activity)
            .setTitle("フォルダへ移動")
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                d.dismiss();
                if (which == labels.length - 1) {
                    promptNewFolder(bookmark);
                } else if (which == 0) {
                    moveTo(bookmark, "");
                } else {
                    moveTo(bookmark, folders.get(which - 1));
                }
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private void moveTo(Bookmark bookmark, String folder) {
        int pos = BookmarkStore.indexOfInstance(bookmarks, bookmark);
        if (pos < 0) {
            return;
        }
        bookmarks.set(pos, bookmark.with(bookmark.getTitle(), bookmark.getUrl(), folder));
        host.changed();
        rebuildChips();
        refresh();
        Toast.makeText(activity, folder.isEmpty() ? "フォルダから出しました" : "「" + folder + "」へ移動しました", Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        List<Bookmark> shown = BookmarkStore.view(bookmarks, query, folderFilter, sortMode);
        adapter.submit(shown);
        int total = bookmarks.size();
        countView.setText(shown.size() == total ? total + "件" : shown.size() + " / " + total + "件");
        sortButton.setText(BookmarkStore.SORT_LABELS[sortMode]);
        defaultFolderButton.setText(defaultFolderLabel());
        if (total == 0) {
            emptyView.setText("ブックマークがありません\n「ブックマーク登録」でページを追加できます");
        } else if (query.trim().isEmpty() && folderFilter != null && !folderFilter.isEmpty()) {
            emptyView.setText("このフォルダは空です\nメニューの「フォルダへ移動」で追加できます");
        } else {
            emptyView.setText("該当するブックマークがありません");
        }
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showItemMenu(View anchor, final Bookmark bookmark) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(Menu.NONE, 1, 1, "新しいタブで開く");
        menu.getMenu().add(Menu.NONE, 2, 2, "編集");
        menu.getMenu().add(Menu.NONE, 6, 3, "フォルダへ移動");
        menu.getMenu().add(Menu.NONE, 3, 4, "URLをコピー");
        menu.getMenu().add(Menu.NONE, 4, 5, "共有");
        menu.getMenu().add(Menu.NONE, 5, 6, "削除");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    host.openInNewTab(bookmark.getUrl());
                    dialog.dismiss();
                    return true;
                case 2:
                    int pos = BookmarkStore.indexOfInstance(bookmarks, bookmark);
                    showEditor(activity, bookmarks, pos, host, () -> {
                        rebuildChips();
                        refresh();
                    });
                    return true;
                case 6:
                    showMoveDialog(bookmark);
                    return true;
                case 3:
                    copy(bookmark.getUrl());
                    return true;
                case 4:
                    share(bookmark);
                    return true;
                case 5:
                    delete(bookmark);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void copy(String url) {
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(activity, "URLをコピーしました", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
        }
    }

    private void share(Bookmark bookmark) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, BookmarkStore.displayTitle(bookmark));
            send.putExtra(Intent.EXTRA_TEXT, bookmark.getUrl());
            activity.startActivity(Intent.createChooser(send, "共有"));
        } catch (Exception ignored) {
        }
    }

    private void delete(Bookmark bookmark) {
        int index = BookmarkStore.indexOfInstance(bookmarks, bookmark);
        if (index < 0) {
            return;
        }
        bookmarks.remove(index);
        host.changed();
        undoBookmark = bookmark;
        undoIndex = index;
        undoText.setText("「" + BookmarkStore.displayTitle(bookmark) + "」を削除しました");
        undoBar.setVisibility(View.VISIBLE);
        root.removeCallbacks(hideUndo);
        root.postDelayed(hideUndo, UNDO_MS);
        rebuildChips();
        refresh();
    }

    private void undoDelete() {
        if (undoBookmark == null) {
            return;
        }
        bookmarks.add(Math.min(undoIndex, bookmarks.size()), undoBookmark);
        host.changed();
        root.removeCallbacks(hideUndo);
        hideUndo.run();
        rebuildChips();
        refresh();
    }

    private static int tileColor(String key) {
        int h = key == null ? 0 : key.hashCode();
        return TILE_COLORS[(h & 0x7fffffff) % TILE_COLORS.length];
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private List<Bookmark> items = new ArrayList<>();

        void submit(List<Bookmark> next) {
            items = new ArrayList<>(next);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            final Bookmark bm = items.get(position);
            String title = BookmarkStore.displayTitle(bm);
            h.title.setText(title);
            h.url.setText(BookmarkStore.displayUrl(bm.getUrl()));
            if (bm.getFolder().isEmpty()) {
                h.folder.setVisibility(View.GONE);
            } else {
                h.folder.setText(bm.getFolder());
                h.folder.setVisibility(View.VISIBLE);
            }
            Bitmap icon = host.favicon(bm.getUrl());
            if (icon != null) {
                h.favicon.setImageBitmap(icon);
                h.favicon.setVisibility(View.VISIBLE);
                h.initial.setVisibility(View.GONE);
            } else {
                h.favicon.setVisibility(View.GONE);
                String hostName = BookmarkStore.hostOf(bm.getUrl());
                String basis = hostName.isEmpty() ? title : hostName;
                h.initial.setText(basis.isEmpty() ? "?" : basis.substring(0, 1).toUpperCase(Locale.ROOT));
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(tileColor(hostName.isEmpty() ? title : hostName));
                h.initial.setBackground(bg);
                h.initial.setVisibility(View.VISIBLE);
            }
            h.itemView.setOnClickListener(v -> {
                host.open(bm.getUrl());
                dialog.dismiss();
            });
            h.itemView.setOnLongClickListener(v -> {
                showItemMenu(h.menu, bm);
                return true;
            });
            h.menu.setOnClickListener(v -> showItemMenu(v, bm));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView favicon;
        final TextView initial;
        final TextView title;
        final TextView url;
        final TextView folder;
        final ImageButton menu;

        Holder(View itemView) {
            super(itemView);
            favicon = itemView.findViewById(R.id.bookmarkFavicon);
            initial = itemView.findViewById(R.id.bookmarkInitial);
            title = itemView.findViewById(R.id.bookmarkTitle);
            url = itemView.findViewById(R.id.bookmarkUrl);
            folder = itemView.findViewById(R.id.bookmarkFolder);
            menu = itemView.findViewById(R.id.bookmarkMenu);
        }
    }
}
