package com.coara.browser2.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coara.browser2.R;
import com.coara.browser2.model.HistoryItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HistoryDialog {

    public interface Host {
        Bitmap favicon(String url);

        void open(String url);

        void copy(String url);

        void changed();
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int FALLBACK_TINT = Color.parseColor("#5F6368");

    private HistoryDialog() {
    }

    private static final class Row {
        final int type;
        final String header;
        final HistoryItem item;

        Row(int type, String header, HistoryItem item) {
            this.type = type;
            this.header = header;
            this.item = item;
        }
    }

    public static void show(Activity activity, List<HistoryItem> source, Host host) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final Dialog dialog = new Dialog(activity, R.style.Theme_CoaraBrowser_FullDialog);
        dialog.setContentView(R.layout.dialog_history);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final RecyclerView list = dialog.findViewById(R.id.historyList);
        final TextView empty = dialog.findViewById(R.id.historyEmpty);
        final EditText search = dialog.findViewById(R.id.historySearch);
        final ImageButton back = dialog.findViewById(R.id.historyBack);
        final ImageButton clearAll = dialog.findViewById(R.id.historyClearAll);

        final Adapter adapter = new Adapter(activity, dialog, source, host, empty);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setItemViewCacheSize(8);
        list.setAdapter(adapter);

        back.setOnClickListener(v -> dialog.dismiss());
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.setQuery(s == null ? "" : s.toString());
            }
        });
        clearAll.setOnClickListener(v -> {
            if (adapter.isSourceEmpty()) {
                Toast.makeText(activity, "履歴がありません", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("閲覧履歴を消去")
                    .setMessage("履歴をすべて削除します。よろしいですか?")
                    .setPositiveButton("削除", (d, w) -> adapter.clearAll())
                    .setNegativeButton("キャンセル", null)
                    .show();
        });
        dialog.show();
    }

    private static final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Activity activity;
        private final Dialog dialog;
        private final List<HistoryItem> source;
        private final Host host;
        private final TextView empty;
        private final List<Row> rows = new ArrayList<>();
        private final DayLabeler labeler = new DayLabeler();
        private String query = "";

        Adapter(Activity activity, Dialog dialog, List<HistoryItem> source, Host host, TextView empty) {
            this.activity = activity;
            this.dialog = dialog;
            this.source = source;
            this.host = host;
            this.empty = empty;
            rebuild();
        }

        boolean isSourceEmpty() {
            synchronized (source) {
                return source.isEmpty();
            }
        }

        void setQuery(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(query)) {
                return;
            }
            query = normalized;
            rebuild();
            notifyDataSetChanged();
        }

        void clearAll() {
            synchronized (source) {
                source.clear();
            }
            host.changed();
            rebuild();
            notifyDataSetChanged();
        }

        private void removeItem(HistoryItem item) {
            synchronized (source) {
                source.remove(item);
            }
            host.changed();
            rebuild();
            notifyDataSetChanged();
        }

        private void rebuild() {
            rows.clear();
            ArrayList<HistoryItem> copy;
            synchronized (source) {
                copy = new ArrayList<>(source);
            }
            labeler.reset();
            int lastKey = Integer.MIN_VALUE;
            for (int i = copy.size() - 1; i >= 0; i--) {
                HistoryItem item = copy.get(i);
                if (!matches(item)) {
                    continue;
                }
                int key = labeler.key(item.getTimestamp());
                if (key != lastKey) {
                    rows.add(new Row(TYPE_HEADER, labeler.label(item.getTimestamp()), null));
                    lastKey = key;
                }
                rows.add(new Row(TYPE_ITEM, null, item));
            }
            if (rows.isEmpty()) {
                empty.setText(query.isEmpty() ? "履歴がありません" : "一致する履歴がありません");
                empty.setVisibility(View.VISIBLE);
            } else {
                empty.setVisibility(View.GONE);
            }
        }

        private boolean matches(HistoryItem item) {
            if (query.isEmpty()) {
                return true;
            }
            String title = item.getTitle();
            String url = item.getUrl();
            return (title != null && title.toLowerCase(Locale.ROOT).contains(query))
                    || (url != null && url.toLowerCase(Locale.ROOT).contains(query));
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_history_header, parent, false));
            }
            return new ItemHolder(inflater.inflate(R.layout.item_history, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).text.setText(row.header);
                return;
            }
            final HistoryItem item = row.item;
            ItemHolder h = (ItemHolder) holder;
            String title = item.getTitle();
            String url = item.getUrl();
            h.title.setText(title != null && !title.isEmpty() ? title : url);
            String hostName = DayLabeler.hostOf(url);
            String time = labeler.time(item.getTimestamp());
            h.sub.setText(time.isEmpty() ? hostName : hostName + " · " + time);
            Bitmap icon = host.favicon(url);
            if (icon != null) {
                h.favicon.clearColorFilter();
                h.favicon.setImageBitmap(icon);
            } else {
                h.favicon.setImageResource(R.drawable.ic_globe);
                h.favicon.setColorFilter(FALLBACK_TINT);
            }
            h.itemView.setOnClickListener(v -> {
                host.open(item.getUrl());
                dialog.dismiss();
            });
            h.delete.setOnClickListener(v -> removeItem(item));
            h.itemView.setOnLongClickListener(v -> {
                String[] options = {"URLをコピー", "履歴から削除"};
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("操作を選択")
                        .setItems(options, (d, which) -> {
                            if (which == 0) {
                                host.copy(item.getUrl());
                            } else {
                                removeItem(item);
                            }
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.historyDateHeader);
        }
    }

    private static final class ItemHolder extends RecyclerView.ViewHolder {
        final ImageView favicon;
        final TextView title;
        final TextView sub;
        final ImageButton delete;

        ItemHolder(View itemView) {
            super(itemView);
            favicon = itemView.findViewById(R.id.historyFavicon);
            title = itemView.findViewById(R.id.historyTitle);
            sub = itemView.findViewById(R.id.historyUrl);
            delete = itemView.findViewById(R.id.historyDelete);
        }
    }
}
