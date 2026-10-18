package com.coara.browser2.webview;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.coara.browser2.R;
import com.coara.browser2.util.TabGroupStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.coara.browser2.gecko.BrowserView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TabOverviewDialog {
    private TabOverviewDialog() {}

    public interface Host {
        int getTabCount();
        BrowserView getTabAt(int index);
        Bitmap getTabSnapshot(BrowserView webView);
        int getCurrentTabIndex();
        void switchToTab(int index);
        void closeTabAt(int index);
        void createNewTab();
        void refreshTabCount();
        void dismissCurrentKeyboard();

        default String getTabFallbackTitle(BrowserView webView) {
            return null;
        }

        default TabGroupStore getGroupStore() {
            return null;
        }

        default int getTabId(BrowserView webView) {
            return -1;
        }
    }

    private static final int FILTER_ALL = -2;
    private static final int UNGROUPED = -1;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TAB = 1;
    private static final int COLOR_ACTIVE = 0xFF4A90E2;

    public static void show(@NonNull Activity activity, @NonNull Host host) {
        new Controller(activity, host).show();
    }

    private static int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static int secondaryTextColor(Activity activity) {
        android.content.res.TypedArray ta = activity.obtainStyledAttributes(new int[] {android.R.attr.textColorSecondary});
        try {
            return ta.getColor(0, Color.GRAY);
        } finally {
            ta.recycle();
        }
    }

    private static int primaryTextColor(Activity activity) {
        android.content.res.TypedArray ta = activity.obtainStyledAttributes(new int[] {android.R.attr.textColorPrimary});
        try {
            return ta.getColor(0, Color.BLACK);
        } finally {
            ta.recycle();
        }
    }

    private static final class Row {
        final boolean header;
        final int groupId;
        final int tabIndex;

        Row(boolean header, int groupId, int tabIndex) {
            this.header = header;
            this.groupId = groupId;
            this.tabIndex = tabIndex;
        }
    }

    private static final class Controller {
        private final Activity activity;
        private final Host host;
        private final TabGroupStore store;
        private final List<Row> rows = new ArrayList<>();
        private final Set<Integer> collapsed = new HashSet<>();
        private final Set<Integer> selected = new HashSet<>();
        private boolean selecting;
        private int filter = FILTER_ALL;
        private AlertDialog dialog;
        private TabOverviewAdapter adapter;
        private ChipGroup chipGroup;
        private HorizontalScrollView chipScroll;
        private TextView hint;
        private MaterialButton selectButton;
        private MaterialButton groupButton;
        private MaterialButton closeSelectedButton;
        private MaterialButton doneButton;
        private TextView selectionInfo;
        private LinearLayout toolbar;

        Controller(Activity activity, Host host) {
            this.activity = activity;
            this.host = host;
            this.store = host.getGroupStore();
        }

        private MaterialButton textButton(String label) {
            MaterialButton b = new MaterialButton(activity, null, android.R.attr.borderlessButtonStyle);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            return b;
        }

        void show() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setMinimumHeight((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.5f));

            toolbar = new LinearLayout(activity);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);

            selectionInfo = new TextView(activity);
            selectionInfo.setTextSize(13);
            selectionInfo.setTextColor(primaryTextColor(activity));
            selectionInfo.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            toolbar.addView(selectionInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            selectButton = textButton("選択して整理");
            groupButton = textButton("グループ化");
            closeSelectedButton = textButton("閉じる");
            doneButton = textButton("完了");
            toolbar.addView(selectButton);
            toolbar.addView(groupButton);
            toolbar.addView(closeSelectedButton);
            toolbar.addView(doneButton);
            selectButton.setOnClickListener(v -> setSelecting(true, -1));
            doneButton.setOnClickListener(v -> setSelecting(false, -1));
            groupButton.setOnClickListener(v -> showSelectionGroupMenu());
            closeSelectedButton.setOnClickListener(v -> confirmCloseSelected());
            root.addView(toolbar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            hint = new TextView(activity);
            hint.setTextSize(12);
            hint.setPadding(dp(activity, 16), dp(activity, 2), dp(activity, 16), dp(activity, 4));
            hint.setTextColor(secondaryTextColor(activity));
            root.addView(hint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            chipScroll = new HorizontalScrollView(activity);
            chipScroll.setHorizontalScrollBarEnabled(false);
            chipGroup = new ChipGroup(activity);
            chipGroup.setSingleLine(true);
            chipGroup.setSingleSelection(true);
            chipGroup.setSelectionRequired(true);
            chipGroup.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            chipScroll.addView(chipGroup, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(chipScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            RecyclerView recyclerView = new RecyclerView(activity);
            GridLayoutManager layoutManager = new GridLayoutManager(activity, 2);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return isHeader(position) ? 2 : 1;
                }
            });
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setNestedScrollingEnabled(true);
            recyclerView.setItemViewCacheSize(8);
            root.addView(recyclerView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle("タブ一覧")
                    .setView(root)
                    .setNegativeButton("閉じる", null)
                    .create();

            adapter = new TabOverviewAdapter(activity, this);
            recyclerView.setAdapter(adapter);

            ItemTouchHelper swipeToCloseHelper = new ItemTouchHelper(new SwipeToCloseCallback(activity, this));
            swipeToCloseHelper.attachToRecyclerView(recyclerView);

            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "新しいタブ", (d, which) -> {
                host.dismissCurrentKeyboard();
                host.createNewTab();
                if (store != null && filter >= 0 && store.get(filter) != null) {
                    int last = host.getTabCount() - 1;
                    int id = host.getTabId(host.getTabAt(last));
                    store.assign(id, filter);
                }
                host.refreshTabCount();
            });

            refreshAll();
            dialog.show();
        }

        void setSelecting(boolean on, int firstTabId) {
            if (store == null) {
                return;
            }
            selecting = on;
            selected.clear();
            if (on && firstTabId >= 0) {
                selected.add(firstTabId);
            }
            refreshAll();
        }

        void refreshAll() {
            computeRows();
            rebuildChips();
            updateToolbar();
            adapter.notifyDataSetChanged();
        }

        private void updateToolbar() {
            if (store == null) {
                toolbar.setVisibility(View.GONE);
                return;
            }
            toolbar.setVisibility(View.VISIBLE);
            selectButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
            groupButton.setVisibility(selecting ? View.VISIBLE : View.GONE);
            closeSelectedButton.setVisibility(selecting ? View.VISIBLE : View.GONE);
            doneButton.setVisibility(selecting ? View.VISIBLE : View.GONE);
            groupButton.setEnabled(!selected.isEmpty());
            closeSelectedButton.setEnabled(!selected.isEmpty());
            selectionInfo.setText(selecting
                    ? (selected.isEmpty() ? "タブを選択してください" : selected.size() + "個を選択中")
                    : Math.max(0, host.getTabCount()) + "個のタブ");
        }

        private void computeRows() {
            rows.clear();
            int n = Math.max(0, host.getTabCount());
            if (store == null) {
                for (int i = 0; i < n; i++) {
                    rows.add(new Row(false, UNGROUPED, i));
                }
                return;
            }
            Set<Integer> live = new HashSet<>();
            for (int i = 0; i < n; i++) {
                int id = host.getTabId(host.getTabAt(i));
                if (id >= 0) {
                    live.add(id);
                }
            }
            if (!live.isEmpty()) {
                store.prune(live);
            }
            selected.retainAll(live);
            if (filter >= 0 && store.get(filter) == null) {
                filter = FILTER_ALL;
            }
            List<Integer> ungrouped = new ArrayList<>();
            Map<Integer, List<Integer>> byGroup = new LinkedHashMap<>();
            for (TabGroupStore.Group g : store.groups()) {
                byGroup.put(g.id, new ArrayList<Integer>());
            }
            for (int i = 0; i < n; i++) {
                int gid = store.groupOf(host.getTabId(host.getTabAt(i)));
                List<Integer> bucket = gid >= 0 ? byGroup.get(gid) : null;
                if (bucket == null) {
                    ungrouped.add(i);
                } else {
                    bucket.add(i);
                }
            }
            boolean anyGroups = !byGroup.isEmpty();
            if (filter == FILTER_ALL) {
                if (anyGroups && !ungrouped.isEmpty()) {
                    rows.add(new Row(true, UNGROUPED, -1));
                }
                for (int index : ungrouped) {
                    rows.add(new Row(false, UNGROUPED, index));
                }
            }
            for (Map.Entry<Integer, List<Integer>> e : byGroup.entrySet()) {
                int gid = e.getKey();
                if (filter != FILTER_ALL && gid != filter) {
                    continue;
                }
                if (e.getValue().isEmpty()) {
                    continue;
                }
                rows.add(new Row(true, gid, -1));
                if (!collapsed.contains(gid) || selecting) {
                    for (int index : e.getValue()) {
                        rows.add(new Row(false, gid, index));
                    }
                }
            }
        }

        private void rebuildChips() {
            chipGroup.setOnCheckedStateChangeListener(null);
            chipGroup.removeAllViews();
            if (store == null) {
                chipScroll.setVisibility(View.GONE);
                hint.setVisibility(View.GONE);
                return;
            }
            List<TabGroupStore.Group> groups = store.groups();
            if (groups.isEmpty()) {
                chipScroll.setVisibility(View.GONE);
                hint.setVisibility(View.VISIBLE);
                hint.setText(selecting
                        ? "まとめたいタブをタップして選び、「グループ化」を押します"
                        : "タブ右上の ⋮ か長押しでグループに追加。「選択して整理」で複数まとめられます");
                return;
            }
            hint.setVisibility(selecting ? View.VISIBLE : View.GONE);
            hint.setText("まとめたいタブをタップして選び、「グループ化」を押します");
            chipScroll.setVisibility(View.VISIBLE);
            addChip("すべて (" + Math.max(0, host.getTabCount()) + ")", FILTER_ALL, 0xFF5F6368);
            for (TabGroupStore.Group g : groups) {
                addChip(g.name + " (" + store.count(g.id) + ")", g.id, g.color);
            }
            chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
                if (ids.isEmpty()) {
                    return;
                }
                View chip = group.findViewById(ids.get(0));
                Object tag = chip == null ? null : chip.getTag();
                int next = tag instanceof Integer ? (Integer) tag : FILTER_ALL;
                if (next != filter) {
                    filter = next;
                    computeRows();
                    updateToolbar();
                    adapter.notifyDataSetChanged();
                }
            });
        }

        private void addChip(String label, int groupId, int color) {
            Chip chip = new Chip(activity);
            chip.setId(View.generateViewId());
            chip.setText(label);
            chip.setTag(groupId);
            chip.setCheckable(true);
            chip.setChipStrokeWidth(dp(activity, 2));
            chip.setChipStrokeColor(ColorStateList.valueOf(color));
            chip.setTextColor(color);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                    new int[] {(color & 0x00FFFFFF) | 0x40000000, Color.TRANSPARENT}));
            chip.setChecked(groupId == filter);
            if (groupId >= 0) {
                chip.setOnLongClickListener(v -> {
                    showGroupMenu(groupId);
                    return true;
                });
            }
            chipGroup.addView(chip);
        }

        int rowCount() {
            return rows.size();
        }

        boolean isHeader(int position) {
            return position >= 0 && position < rows.size() && rows.get(position).header;
        }

        Row row(int position) {
            return position < 0 || position >= rows.size() ? null : rows.get(position);
        }

        boolean canSwipe(int position) {
            return !selecting && position >= 0 && position < rows.size() && !rows.get(position).header;
        }

        BrowserView tabAtPosition(int position) {
            Row r = row(position);
            return r == null || r.header ? null : host.getTabAt(r.tabIndex);
        }

        TabGroupStore.Group groupAtPosition(int position) {
            BrowserView webView = tabAtPosition(position);
            if (store == null || webView == null) {
                return null;
            }
            return store.groupFor(host.getTabId(webView));
        }

        TabGroupStore.Group groupOfHeader(int position) {
            Row r = row(position);
            return r == null || store == null || r.groupId < 0 ? null : store.get(r.groupId);
        }

        int tabCountInSection(int groupId) {
            if (store == null) {
                return Math.max(0, host.getTabCount());
            }
            if (groupId >= 0) {
                return store.count(groupId);
            }
            int n = 0;
            for (int i = 0; i < host.getTabCount(); i++) {
                if (store.groupOf(host.getTabId(host.getTabAt(i))) < 0) {
                    n++;
                }
            }
            return n;
        }

        boolean isCollapsed(int groupId) {
            return collapsed.contains(groupId) && !selecting;
        }

        void toggleCollapse(int groupId) {
            if (groupId < 0 || selecting) {
                return;
            }
            if (!collapsed.remove(groupId)) {
                collapsed.add(groupId);
            }
            computeRows();
            adapter.notifyDataSetChanged();
        }

        boolean isSelecting() {
            return selecting;
        }

        boolean isSelectedAt(int position) {
            BrowserView w = tabAtPosition(position);
            return w != null && selected.contains(host.getTabId(w));
        }

        boolean isCurrent(int position) {
            Row r = row(position);
            return r != null && !r.header && r.tabIndex == host.getCurrentTabIndex();
        }

        Bitmap snapshotAtPosition(int position) {
            BrowserView webView = tabAtPosition(position);
            return webView == null ? null : host.getTabSnapshot(webView);
        }

        void closeVisible(int position) {
            Row r = row(position);
            if (r == null || r.header) {
                return;
            }
            int id = host.getTabId(host.getTabAt(r.tabIndex));
            host.dismissCurrentKeyboard();
            host.closeTabAt(r.tabIndex);
            selected.remove(id);
            host.refreshTabCount();
            refreshAll();
        }

        void clickTab(int position) {
            Row r = row(position);
            if (r == null || r.header) {
                return;
            }
            if (selecting) {
                toggleSelected(position);
                return;
            }
            host.dismissCurrentKeyboard();
            host.switchToTab(r.tabIndex);
            host.refreshTabCount();
            dialog.dismiss();
        }

        void toggleSelected(int position) {
            BrowserView w = tabAtPosition(position);
            if (w == null) {
                return;
            }
            int id = host.getTabId(w);
            if (id < 0) {
                return;
            }
            if (!selected.remove(id)) {
                selected.add(id);
            }
            updateToolbar();
            adapter.notifyItemChanged(position);
        }

        void longClickTab(int position) {
            if (selecting) {
                toggleSelected(position);
            } else {
                showTabMenu(position);
            }
        }

        void showTabMenu(final int position) {
            final BrowserView webView = tabAtPosition(position);
            if (webView == null) {
                return;
            }
            final int tabId = host.getTabId(webView);
            final List<String> labels = new ArrayList<>();
            final List<Runnable> actions = new ArrayList<>();
            if (store != null && tabId >= 0) {
                int current = store.groupOf(tabId);
                labels.add("新しいグループに追加");
                actions.add(() -> promptNewGroup(java.util.Collections.singletonList(tabId)));
                for (final TabGroupStore.Group g : store.groups()) {
                    if (g.id != current) {
                        labels.add("「" + g.name + "」に追加");
                        actions.add(() -> {
                            store.assign(tabId, g.id);
                            refreshAll();
                        });
                    }
                }
                if (current >= 0) {
                    labels.add("グループから外す");
                    actions.add(() -> {
                        store.remove(tabId);
                        refreshAll();
                    });
                }
                labels.add("複数を選択して整理");
                actions.add(() -> setSelecting(true, tabId));
            }
            labels.add("タブを閉じる");
            actions.add(() -> closeVisible(position));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(titleOf(webView))
                    .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                    .show();
        }

        void showSelectionGroupMenu() {
            if (store == null || selected.isEmpty()) {
                return;
            }
            final List<Integer> ids = new ArrayList<>(selected);
            final List<String> labels = new ArrayList<>();
            final List<Runnable> actions = new ArrayList<>();
            labels.add("新しいグループにまとめる");
            actions.add(() -> promptNewGroup(ids));
            for (final TabGroupStore.Group g : store.groups()) {
                labels.add("「" + g.name + "」に追加");
                actions.add(() -> {
                    for (int id : ids) {
                        store.assign(id, g.id);
                    }
                    finishSelection();
                });
            }
            labels.add("グループから外す");
            actions.add(() -> {
                for (int id : ids) {
                    store.remove(id);
                }
                finishSelection();
            });
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(ids.size() + "個のタブ")
                    .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                    .show();
        }

        private void finishSelection() {
            selecting = false;
            selected.clear();
            refreshAll();
        }

        private void confirmCloseSelected() {
            if (selected.isEmpty()) {
                return;
            }
            final int count = selected.size();
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("選択したタブを閉じる")
                    .setMessage(count + "個のタブを閉じます。")
                    .setPositiveButton("閉じる", (d, w) -> {
                        host.dismissCurrentKeyboard();
                        for (int i = host.getTabCount() - 1; i >= 0; i--) {
                            if (selected.contains(host.getTabId(host.getTabAt(i)))) {
                                host.closeTabAt(i);
                            }
                        }
                        host.refreshTabCount();
                        finishSelection();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        private FrameLayout inputHolder(EditText input) {
            FrameLayout holder = new FrameLayout(activity);
            holder.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
            holder.addView(input, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return holder;
        }

        private void promptNewGroup(final List<Integer> tabIds) {
            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
            input.setText(store.suggestName());
            input.setSelectAllOnFocus(true);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("新しいグループ")
                    .setView(inputHolder(input))
                    .setPositiveButton("作成", (d, w) -> {
                        TabGroupStore.Group g = store.create(input.getText().toString());
                        for (int id : tabIds) {
                            store.assign(id, g.id);
                        }
                        finishSelection();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        void showGroupMenu(final int groupId) {
            final TabGroupStore.Group group = store.get(groupId);
            if (group == null) {
                return;
            }
            final String[] labels = {"名前を変更", "色を変更", "グループのタブをすべて閉じる", "グループを解除"};
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(group.name)
                    .setItems(labels, (d, which) -> {
                        if (which == 0) {
                            promptRename(group);
                        } else if (which == 1) {
                            promptColor(group);
                        } else if (which == 2) {
                            confirmCloseGroup(group);
                        } else {
                            store.dissolve(group.id);
                            collapsed.remove(group.id);
                            if (filter == group.id) {
                                filter = FILTER_ALL;
                            }
                            refreshAll();
                        }
                    })
                    .show();
        }

        private void promptRename(final TabGroupStore.Group group) {
            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
            input.setText(group.name);
            input.setSelectAllOnFocus(true);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("グループ名")
                    .setView(inputHolder(input))
                    .setPositiveButton("保存", (d, w) -> {
                        store.rename(group.id, input.getText().toString());
                        refreshAll();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        private void promptColor(final TabGroupStore.Group group) {
            int selectedIndex = 0;
            for (int i = 0; i < TabGroupStore.COLORS.length; i++) {
                if (TabGroupStore.COLORS[i] == group.color) {
                    selectedIndex = i;
                    break;
                }
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("グループの色")
                    .setSingleChoiceItems(TabGroupStore.COLOR_NAMES, selectedIndex, (d, which) -> {
                        store.setColor(group.id, TabGroupStore.COLORS[which]);
                        d.dismiss();
                        refreshAll();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        private void confirmCloseGroup(final TabGroupStore.Group group) {
            int count = store.count(group.id);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("グループのタブを閉じる")
                    .setMessage("「" + group.name + "」の" + count + "個のタブを閉じます。")
                    .setPositiveButton("閉じる", (d, w) -> {
                        host.dismissCurrentKeyboard();
                        for (int i = host.getTabCount() - 1; i >= 0; i--) {
                            if (store.groupOf(host.getTabId(host.getTabAt(i))) == group.id) {
                                host.closeTabAt(i);
                            }
                        }
                        host.refreshTabCount();
                        refreshAll();
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        String titleOf(BrowserView webView) {
            String title = webView.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = webView.getUrl();
            }
            if (title == null || title.trim().isEmpty()) {
                title = host.getTabFallbackTitle(webView);
            }
            if (title == null || title.trim().isEmpty()) {
                return "新しいタブ";
            }
            title = title.trim();
            if (title.length() > 48) {
                return title.substring(0, 45) + "...";
            }
            return title;
        }
    }

    private static final class SwipeToCloseCallback extends ItemTouchHelper.SimpleCallback {
        private final Activity activity;
        private final Controller controller;
        private final Paint backgroundPaint = new Paint();
        private final Paint iconPaint = new Paint();

        SwipeToCloseCallback(Activity activity, Controller controller) {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            this.activity = activity;
            this.controller = controller;
            backgroundPaint.setColor(Color.parseColor("#D9464646"));
            backgroundPaint.setAntiAlias(true);
            iconPaint.setColor(Color.WHITE);
            iconPaint.setAntiAlias(true);
            iconPaint.setStrokeWidth(dp(activity, 3));
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || !controller.canSwipe(position)) {
                return 0;
            }
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                               @NonNull RecyclerView.ViewHolder viewHolder,
                               @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            controller.closeVisible(position);
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                 int actionState, boolean isCurrentlyActive) {
            View itemView = viewHolder.itemView;
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                float radius = dp(activity, 18);
                RectF rect = new RectF(itemView.getLeft(), itemView.getTop(),
                        itemView.getRight(), itemView.getBottom());
                c.drawRoundRect(rect, radius, radius, backgroundPaint);

                float centerY = itemView.getTop() + itemView.getHeight() / 2f;
                float centerX = dX > 0
                        ? itemView.getLeft() + dp(activity, 28)
                        : itemView.getRight() - dp(activity, 28);
                float half = dp(activity, 8);
                c.drawLine(centerX - half, centerY - half, centerX + half, centerY + half, iconPaint);
                c.drawLine(centerX - half, centerY + half, centerX + half, centerY - half, iconPaint);

                itemView.setTranslationX(dX);
                itemView.setAlpha(Math.max(0.25f, 1f - Math.abs(dX) / itemView.getWidth()));
            } else {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setTranslationX(0f);
        }
    }

    private static final class TabOverviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Activity activity;
        private final Controller controller;

        TabOverviewAdapter(Activity activity, Controller controller) {
            this.activity = activity;
            this.controller = controller;
        }

        @Override
        public int getItemViewType(int position) {
            return controller.isHeader(position) ? TYPE_HEADER : TYPE_TAB;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                return createHeader();
            }
            return createTab();
        }

        private HeaderViewHolder createHeader() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(6), dp(4), dp(6));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(dp(10), dp(8), dp(10), dp(0));
            row.setLayoutParams(params);

            View dot = new View(activity);
            row.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));

            TextView name = new TextView(activity);
            name.setTextSize(14);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(primaryTextColor(activity));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameParams.setMargins(dp(10), 0, dp(8), 0);
            row.addView(name, nameParams);

            TextView arrow = new TextView(activity);
            arrow.setTextSize(16);
            arrow.setTextColor(secondaryTextColor(activity));
            arrow.setPadding(dp(8), 0, dp(8), 0);
            row.addView(arrow);

            ImageButton menu = new ImageButton(activity);
            menu.setImageResource(R.drawable.ic_more_vert);
            menu.setBackgroundColor(Color.TRANSPARENT);
            menu.setColorFilter(secondaryTextColor(activity));
            row.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));

            return new HeaderViewHolder(row, dot, name, arrow, menu);
        }

        private TabViewHolder createTab() {
            int padding = dp(10);
            MaterialCardView card = new MaterialCardView(activity);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(padding, padding, padding, padding);
            card.setLayoutParams(params);
            card.setRadius(dp(18));
            card.setUseCompatPadding(true);
            card.setCardElevation(dp(2));
            card.setStrokeWidth(dp(2));
            card.setStrokeColor(Color.TRANSPARENT);

            FrameLayout root = new FrameLayout(activity);
            root.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            ImageView preview = new ImageView(activity);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setAdjustViewBounds(false);
            preview.setBackgroundColor(Color.parseColor("#1E1E1E"));
            FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(200)
            );
            root.addView(preview, previewParams);

            LinearLayout overlay = new LinearLayout(activity);
            overlay.setOrientation(LinearLayout.VERTICAL);
            overlay.setPadding(dp(12), dp(8), dp(12), dp(8));
            overlay.setBackgroundColor(Color.argb(190, 20, 20, 20));
            FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            root.addView(overlay, overlayParams);

            TextView title = new TextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(12);
            title.setMaxLines(2);
            overlay.addView(title);

            TextView activeBadge = new TextView(activity);
            activeBadge.setTextSize(11);
            activeBadge.setTextColor(Color.WHITE);
            activeBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START
            );
            badgeParams.setMargins(dp(10), dp(10), dp(10), dp(10));
            root.addView(activeBadge, badgeParams);

            ImageButton close = new ImageButton(activity);
            close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            close.setBackgroundColor(Color.argb(160, 0, 0, 0));
            close.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.END);
            closeParams.setMargins(dp(10), dp(10), dp(10), dp(10));
            root.addView(close, closeParams);

            ImageButton more = new ImageButton(activity);
            more.setImageResource(R.drawable.ic_more_vert);
            more.setBackgroundColor(Color.argb(160, 0, 0, 0));
            more.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams moreParams = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.END);
            moreParams.setMargins(dp(10), dp(10), dp(10) + dp(36) + dp(6), dp(10));
            root.addView(more, moreParams);

            card.addView(root);
            return new TabViewHolder(card, preview, title, activeBadge, close, more);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            if (viewHolder instanceof HeaderViewHolder) {
                bindHeader((HeaderViewHolder) viewHolder, position);
            } else {
                bindTab((TabViewHolder) viewHolder, position);
            }
        }

        private void bindHeader(final HeaderViewHolder holder, int position) {
            Row r = controller.row(position);
            if (r == null) {
                return;
            }
            final int groupId = r.groupId;
            TabGroupStore.Group group = controller.groupOfHeader(position);
            int count = controller.tabCountInSection(groupId);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            if (group != null) {
                dotBg.setColor(group.color);
                holder.name.setText(group.name + " · " + count + "個のタブ");
                holder.arrow.setText(controller.isCollapsed(groupId) ? "▸" : "▾");
                holder.arrow.setVisibility(View.VISIBLE);
                holder.menu.setVisibility(View.VISIBLE);
                holder.itemView.setOnClickListener(v -> controller.toggleCollapse(groupId));
                holder.itemView.setOnLongClickListener(v -> {
                    controller.showGroupMenu(groupId);
                    return true;
                });
                holder.menu.setOnClickListener(v -> controller.showGroupMenu(groupId));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(12));
                bg.setColor((group.color & 0x00FFFFFF) | 0x26000000);
                holder.itemView.setBackground(bg);
            } else {
                dotBg.setColor(0xFF9AA0A6);
                holder.name.setText("グループなし · " + count + "個のタブ");
                holder.arrow.setVisibility(View.GONE);
                holder.menu.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(null);
                holder.itemView.setOnLongClickListener(null);
                holder.itemView.setBackground(null);
            }
            holder.dot.setBackground(dotBg);
        }

        private void bindTab(final TabViewHolder holder, int position) {
            BrowserView webView = controller.tabAtPosition(position);
            if (webView == null) {
                holder.card.setVisibility(View.GONE);
                return;
            }
            holder.card.setVisibility(View.VISIBLE);

            Bitmap snapshot = controller.snapshotAtPosition(position);
            if (snapshot != null && !snapshot.isRecycled()) {
                holder.preview.setImageBitmap(snapshot);
            } else {
                holder.preview.setImageDrawable(null);
                holder.preview.setBackgroundColor(Color.parseColor("#2A2A2A"));
            }

            holder.title.setText(controller.titleOf(webView));

            TabGroupStore.Group group = controller.groupAtPosition(position);
            boolean isCurrent = controller.isCurrent(position);
            boolean selecting = controller.isSelecting();
            boolean checked = selecting && controller.isSelectedAt(position);

            if (selecting) {
                holder.activeBadge.setText(checked ? "✓ 選択中" : "○ 未選択");
                holder.activeBadge.setBackgroundColor(checked ? COLOR_ACTIVE : Color.argb(170, 0, 0, 0));
                holder.activeBadge.setVisibility(View.VISIBLE);
            } else if (isCurrent) {
                holder.activeBadge.setText("現在のタブ");
                holder.activeBadge.setBackgroundColor(COLOR_ACTIVE);
                holder.activeBadge.setVisibility(View.VISIBLE);
            } else {
                holder.activeBadge.setVisibility(View.GONE);
            }
            holder.close.setVisibility(selecting ? View.GONE : View.VISIBLE);
            holder.more.setVisibility(selecting ? View.GONE : View.VISIBLE);

            int stroke;
            if (checked) {
                stroke = COLOR_ACTIVE;
            } else if (isCurrent && !selecting) {
                stroke = COLOR_ACTIVE;
            } else {
                stroke = group != null ? group.color : Color.TRANSPARENT;
            }
            holder.card.setStrokeColor(stroke);

            holder.card.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    controller.clickTab(pos);
                }
            });
            holder.card.setOnLongClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    controller.longClickTab(pos);
                }
                return true;
            });
            holder.close.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    controller.closeVisible(pos);
                }
            });
            holder.more.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    controller.showTabMenu(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return controller.rowCount();
        }

        private int dp(int value) {
            float density = activity.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }

        static final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final View dot;
            final TextView name;
            final TextView arrow;
            final ImageButton menu;

            HeaderViewHolder(@NonNull View itemView, View dot, TextView name, TextView arrow, ImageButton menu) {
                super(itemView);
                this.dot = dot;
                this.name = name;
                this.arrow = arrow;
                this.menu = menu;
            }
        }

        static final class TabViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView preview;
            final TextView title;
            final TextView activeBadge;
            final ImageButton close;
            final ImageButton more;

            TabViewHolder(@NonNull View itemView, ImageView preview, TextView title,
                          TextView activeBadge, ImageButton close, ImageButton more) {
                super(itemView);
                this.card = (MaterialCardView) itemView;
                this.preview = preview;
                this.title = title;
                this.activeBadge = activeBadge;
                this.close = close;
                this.more = more;
            }
        }
    }
}
