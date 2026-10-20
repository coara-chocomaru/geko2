package com.coara.browser2;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.coara.browser2.model.DownloadItem;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.CookieSitePolicy;
import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.DayLabeler;
import com.coara.browser2.util.DownloadFallbackManager;
import com.coara.browser2.util.DownloadSupport;
import com.coara.browser2.util.FileOpenSupport;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadHistoryActivity extends AppCompatActivity {

    private static final int QUERY_CHUNK = 100;
    private static final long POLL_ACTIVE_MS = 1000L;
    private static final long POLL_IDLE_MS = 3000L;

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private EditText searchBox;
    private DownloadAdapter adapter;
    private List<DownloadItem> downloadItems;
    private SharedPreferences pref;

    private DownloadManager downloadManager;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger pollGeneration = new AtomicInteger(0);
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private boolean loadedOnce;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            final int myGeneration = pollGeneration.get();
            if (isFinishing() || isDestroyed() || downloadItems == null || adapter == null) {
                return;
            }
            final List<Long> pendingIds = new ArrayList<>();
            final Map<Long, DownloadItem> pendingRefs = new HashMap<>();
            boolean active = false;
            for (int i = 0, size = downloadItems.size(); i < size; i++) {
                DownloadItem item = downloadItems.get(i);
                if (item.status != DownloadManager.STATUS_SUCCESSFUL
                        && item.status != DownloadManager.STATUS_FAILED) {
                    pendingIds.add(item.downloadId);
                    pendingRefs.put(item.downloadId, item);
                    if (!item.isPaused && item.status != DownloadManager.STATUS_PAUSED) {
                        active = true;
                    }
                }
            }
            if (pendingIds.isEmpty()) {
                return;
            }
            final long nextDelay = active ? POLL_ACTIVE_MS : POLL_IDLE_MS;
            final Map<Long, String> pathById = new HashMap<>();
            for (Map.Entry<Long, DownloadItem> e : pendingRefs.entrySet()) {
                pathById.put(e.getKey(), e.getValue().filePath);
            }
            submit(() -> {
                final Map<Long, DownloadItem> updates = fetchItems(pendingIds, true);
                for (Map.Entry<Long, DownloadItem> e : updates.entrySet()) {
                    DownloadItem u = e.getValue();
                    if (u.status == DownloadManager.STATUS_SUCCESSFUL) {
                        String path = pathById.get(e.getKey());
                        u.fileExists = itemExists(path, u.localUri);
                        resolveFinalSize(u, path);
                    }
                }
                updateHandler.post(() -> {
                    if (pollGeneration.get() != myGeneration || isFinishing() || isDestroyed()) {
                        return;
                    }
                    applyUpdates(updates);
                    updateHandler.postDelayed(updateRunnable, nextDelay);
                });
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_history);

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEmpty = findViewById(R.id.tvEmpty);
        searchBox = findViewById(R.id.downloadSearch);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemViewCacheSize(8);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        pref = getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);

        adapter = new DownloadAdapter();
        downloadItems = adapter.all;
        recyclerView.setAdapter(adapter);

        findViewById(R.id.downloadBack).setOnClickListener(v -> finish());
        findViewById(R.id.downloadClearAll).setOnClickListener(v -> {
            if (downloadItems == null || downloadItems.isEmpty()) {
                Toast.makeText(this, "ダウンロード履歴がありません", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("ダウンロード履歴を消去")
                    .setMessage("履歴の記録のみ削除します。ダウンロード済みのファイルは削除されません。")
                    .setPositiveButton("消去", (d, w) -> clearDownloadHistory())
                    .setNegativeButton("キャンセル", null)
                    .show();
        });
        searchBox.addTextChangedListener(new TextWatcher() {
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

        if (getIntent().getBooleanExtra("clear_history", false)) {
            clearDownloadHistory();
        }
        loadDownloadHistory();

        swipeRefresh.setOnRefreshListener(() -> {
            loadDownloadHistory();
            swipeRefresh.setRefreshing(false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loadedOnce) {
            recheckExistence();
        }
        startPolling();
    }

    @Override
    protected void onPause() {
        pollGeneration.incrementAndGet();
        updateHandler.removeCallbacks(updateRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pollGeneration.incrementAndGet();
        loadGeneration.incrementAndGet();
        updateHandler.removeCallbacks(updateRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void submit(Runnable task) {
        if (executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void startPolling() {
        pollGeneration.incrementAndGet();
        updateHandler.removeCallbacks(updateRunnable);
        updateHandler.post(updateRunnable);
    }

    private void applyUpdates(Map<Long, DownloadItem> updates) {
        List<DownloadItem> current = downloadItems;
        if (current == null || adapter == null) {
            return;
        }
        boolean anyChanged = false;
        for (int i = 0, size = current.size(); i < size; i++) {
            DownloadItem currentItem = current.get(i);
            DownloadItem updated = updates.get(currentItem.downloadId);
            if (updated == null) continue;
            boolean changed = currentItem.isPaused != updated.isPaused
                    || currentItem.status != updated.status
                    || currentItem.downloadedSize != updated.downloadedSize
                    || currentItem.totalSize != updated.totalSize;
            currentItem.isPaused = updated.isPaused;
            currentItem.manual = updated.manual;
            if (!DownloadSupport.isBlank(updated.downloadUrl)) {
                currentItem.downloadUrl = updated.downloadUrl;
                currentItem.host = DayLabeler.hostOf(updated.downloadUrl);
            }
            currentItem.status = updated.status;
            currentItem.downloadedSize = updated.downloadedSize;
            currentItem.totalSize = updated.totalSize;
            if (currentItem.title == null || currentItem.title.isEmpty()) {
                currentItem.title = updated.title;
                changed = true;
            }
            if (!DownloadSupport.isBlank(updated.localUri)) {
                currentItem.localUri = updated.localUri;
            }
            if (updated.status == DownloadManager.STATUS_SUCCESSFUL) {
                if (currentItem.fileExists != updated.fileExists) {
                    currentItem.fileExists = updated.fileExists;
                    changed = true;
                }
                if (updated.totalSize > 0) {
                    currentItem.totalSize = updated.totalSize;
                }
            }
            if (changed) {
                anyChanged = true;
                adapter.notifyItem(currentItem);
            }
        }
        if (anyChanged) {
            updateEmptyState();
        }
    }

    private void recheckExistence() {
        final List<DownloadItem> snapshot = new ArrayList<>(downloadItems);
        final List<String[]> keys = new ArrayList<>(snapshot.size());
        for (DownloadItem item : snapshot) {
            keys.add(new String[]{item.filePath, item.localUri});
        }
        submit(() -> {
            final boolean[] exists = new boolean[snapshot.size()];
            for (int i = 0; i < exists.length; i++) {
                DownloadItem item = snapshot.get(i);
                if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) {
                    exists[i] = itemExists(keys.get(i)[0], keys.get(i)[1]);
                }
            }
            updateHandler.post(() -> {
                if (isFinishing() || isDestroyed() || adapter == null) {
                    return;
                }
                for (int i = 0; i < exists.length; i++) {
                    DownloadItem item = snapshot.get(i);
                    if ((item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED)
                            && item.fileExists != exists[i]) {
                        item.fileExists = exists[i];
                        adapter.notifyItem(item);
                    }
                }
            });
        });
    }

    private void updateEmptyState() {
        boolean noData = downloadItems == null || downloadItems.isEmpty();
        boolean noRows = adapter == null || adapter.getItemCount() == 0;
        if (noRows) {
            tvEmpty.setText(noData ? "ダウンロード履歴がありません" : "一致するダウンロードがありません");
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadDownloadHistory() {
        final int myLoad = loadGeneration.incrementAndGet();
        submit(() -> {
            DownloadHistoryManager.reconcileManualDownloads(this);
            final List<JSONObject> records = new ArrayList<>();
            final List<Long> ids = new ArrayList<>();
            String jsonStr = pref.getString(BrowserConstants.KEY_DOWNLOAD_HISTORY, "[]");
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0, len = array.length(); i < len; i++) {
                    JSONObject obj = array.getJSONObject(i);
                    records.add(obj);
                    ids.add(obj.getLong("id"));
                }
            } catch (JSONException e) {
                records.clear();
                ids.clear();
            }
            final Map<Long, DownloadItem> fromManager = fetchItems(ids, false);
            final List<DownloadItem> items = new ArrayList<>(records.size());
            long previousTimestamp = 0L;
            for (int i = 0, len = records.size(); i < len; i++) {
                JSONObject obj = records.get(i);
                long downloadId = ids.get(i);
                String storedFileName = obj.optString("fileName", "");
                String filePath = obj.optString("filePath", "");
                String storedLocalUri = obj.optString("localUri", "");
                boolean manualRecord = obj.has("manualStatus") || obj.optBoolean("pausedByUser", false);
                DownloadItem item = fromManager.get(downloadId);
                if (item == null && manualRecord) {
                    item = DownloadHistoryManager.getManualDownloadItem(this, downloadId);
                }
                if (item == null) {
                    String fileName = !storedFileName.isEmpty() ? storedFileName : fileNameFromPath(filePath, storedLocalUri);
                    String localUri = storedLocalUri;
                    if (localUri.isEmpty() && !filePath.isEmpty()) {
                        localUri = Uri.fromFile(new File(filePath)).toString();
                    }
                    if (manualRecord) {
                        int status = obj.has("manualStatus") ? obj.optInt("manualStatus", DownloadManager.STATUS_FAILED) : DownloadManager.STATUS_PAUSED;
                        long downloadedSize = obj.optLong("manualDownloaded", 0);
                        long totalSize = obj.optLong("manualTotal", 0);
                        item = new DownloadItem(downloadId, fileName, "", status, downloadedSize,
                                totalSize, localUri, obj.optString("downloadUrl", ""));
                        item.manual = obj.has("manualStatus");
                        item.isPaused = obj.optBoolean("manualPaused", false) || obj.optBoolean("pausedByUser", false);
                    } else {
                        boolean exists = itemExists(filePath, localUri);
                        long size = getItemSize(filePath, localUri);
                        int status = exists ? DownloadManager.STATUS_SUCCESSFUL : DownloadManager.STATUS_FAILED;
                        item = new DownloadItem(downloadId, fileName, "", status, 0, size, localUri, "");
                    }
                } else {
                    if (item.title == null || item.title.isEmpty() || "Unknown".equalsIgnoreCase(item.title)) {
                        if (!storedFileName.isEmpty()) {
                            item.title = storedFileName;
                        } else if (!filePath.isEmpty()) {
                            item.title = new File(filePath).getName();
                        } else if (!storedLocalUri.isEmpty()) {
                            item.title = fileNameFromPath("", storedLocalUri);
                        }
                    }
                    if (DownloadSupport.isBlank(item.localUri)) {
                        item.localUri = storedLocalUri;
                    }
                }
                item.filePath = filePath;
                item.userAgent = obj.optString("userAgent", "");
                item.referer = obj.optString("referer", "");
                item.basicAuthEnabled = obj.optBoolean("basicAuthEnabled", false);
                if (DownloadSupport.isBlank(item.localUri) && !DownloadSupport.isBlank(storedLocalUri)) {
                    item.localUri = storedLocalUri;
                }
                if (DownloadSupport.isBlank(item.downloadUrl)) {
                    item.downloadUrl = obj.optString("downloadUrl", "");
                }
                item.host = DayLabeler.hostOf(item.downloadUrl);
                item.fileExists = itemExists(item.filePath, item.localUri);
                if (item.status == DownloadManager.STATUS_SUCCESSFUL && item.fileExists) {
                    resolveFinalSize(item, item.filePath);
                }
                long ts = obj.optLong("addedAt", 0L);
                if (ts <= 0L) ts = item.timestamp;
                if (ts <= 0L) ts = obj.optLong("manualUpdatedAt", 0L);
                if (ts <= 0L && item.fileExists) ts = lastModifiedOf(item.filePath, item.localUri);
                if (ts <= 0L) ts = previousTimestamp;
                item.timestamp = ts;
                previousTimestamp = ts;
                items.add(item);
            }
            Collections.reverse(items);
            Collections.sort(items, new Comparator<DownloadItem>() {
                @Override
                public int compare(DownloadItem a, DownloadItem b) {
                    return Long.compare(b.timestamp, a.timestamp);
                }
            });
            updateHandler.post(() -> {
                if (loadGeneration.get() != myLoad || isFinishing() || isDestroyed() || adapter == null) {
                    return;
                }
                loadedOnce = true;
                adapter.replaceAll(items);
                updateEmptyState();
                startPolling();
            });
        });
    }

    private long lastModifiedOf(String filePath, String localUri) {
        try {
            if (!DownloadSupport.isBlank(localUri)) {
                Uri uri = Uri.parse(localUri);
                if ("file".equalsIgnoreCase(uri.getScheme()) && !DownloadSupport.isBlank(uri.getPath())) {
                    File f = new File(uri.getPath());
                    if (f.exists()) return f.lastModified();
                }
            }
            if (!DownloadSupport.isBlank(filePath)) {
                File f = new File(filePath);
                if (f.exists()) return f.lastModified();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String fileNameFromPath(String filePath, String localUri) {
        if (!DownloadSupport.isBlank(filePath)) {
            String name = new File(filePath).getName();
            if (!DownloadSupport.isBlank(name)) return name;
        }
        if (!DownloadSupport.isBlank(localUri)) {
            try {
                Uri uri = Uri.parse(localUri);
                String name = uri.getLastPathSegment();
                if (!DownloadSupport.isBlank(name)) return name;
            } catch (Exception ignored) {
            }
        }
        return "Unknown";
    }

    private boolean itemExists(DownloadItem item) {
        return item != null && itemExists(item.filePath, item.localUri);
    }

    private boolean itemExists(String filePath, String localUri) {
        if (!DownloadSupport.isBlank(localUri)) {
            try {
                Uri uri = Uri.parse(localUri);
                if ("content".equalsIgnoreCase(uri.getScheme())) {
                    try (android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                        if (fd != null) return true;
                    }
                } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                    String path = uri.getPath();
                    if (!DownloadSupport.isBlank(path) && new File(path).exists()) return true;
                }
            } catch (Exception ignored) {
            }
        }
        return !DownloadSupport.isBlank(filePath) && new File(filePath).exists();
    }

    private void resolveFinalSize(DownloadItem item, String filePath) {
        if (item == null || item.status != DownloadManager.STATUS_SUCCESSFUL) {
            return;
        }
        long best = Math.max(item.totalSize, item.downloadedSize);
        if (item.totalSize <= 0L || item.downloadedSize > item.totalSize) {
            long actual = getItemSize(filePath, item.localUri);
            if (actual > 0L) {
                best = actual;
            }
        }
        if (best > 0L) {
            item.totalSize = best;
            item.downloadedSize = best;
        }
    }

    private long getItemSize(String filePath, String localUri) {
        if (!DownloadSupport.isBlank(localUri)) {
            try {
                Uri uri = Uri.parse(localUri);
                if ("content".equalsIgnoreCase(uri.getScheme())) {
                    try (android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                        if (fd != null) {
                            if (fd.getLength() > 0) return fd.getLength();
                            try {
                                long stat = fd.getParcelFileDescriptor().getStatSize();
                                if (stat > 0) return stat;
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    try (Cursor c = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
                        if (c != null && c.moveToFirst() && !c.isNull(0)) {
                            long size = c.getLong(0);
                            if (size > 0) return size;
                        }
                    } catch (Exception ignored) {
                    }
                } else if ("file".equalsIgnoreCase(uri.getScheme()) && !DownloadSupport.isBlank(uri.getPath())) {
                    File file = new File(uri.getPath());
                    if (file.exists()) return file.length();
                }
            } catch (Exception ignored) {
            }
        }
        File file = DownloadSupport.isBlank(filePath) ? null : new File(filePath);
        return file != null && file.exists() ? file.length() : 0L;
    }

    private Map<Long, DownloadItem> fetchItems(List<Long> ids, boolean manualFallback) {
        Map<Long, DownloadItem> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (int from = 0; from < ids.size(); from += QUERY_CHUNK) {
            int to = Math.min(ids.size(), from + QUERY_CHUNK);
            long[] chunk = new long[to - from];
            for (int i = from; i < to; i++) {
                chunk[i - from] = ids.get(i);
            }
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(chunk);
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor == null) continue;
                int idId = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int idTitle = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int idDesc = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION);
                int idStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int idTotal = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int idSoFar = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int idLocal = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int idUri = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                int idModified = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
                if (idId < 0) continue;
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idId);
                    String localUri = columnString(cursor, idLocal);
                    String downloadUrl = columnString(cursor, idUri);
                    String title = robustFileName(columnString(cursor, idTitle), localUri, downloadUrl);
                    DownloadItem item = new DownloadItem(id, title, columnString(cursor, idDesc),
                            idStatus >= 0 ? cursor.getInt(idStatus) : 0,
                            idSoFar >= 0 ? cursor.getLong(idSoFar) : 0L,
                            idTotal >= 0 ? cursor.getLong(idTotal) : 0L,
                            localUri, downloadUrl);
                    item.timestamp = idModified >= 0 ? cursor.getLong(idModified) : 0L;
                    out.put(id, item);
                }
            } catch (Exception ignored) {
            }
        }
        if (manualFallback) {
            for (Long id : ids) {
                if (!out.containsKey(id)) {
                    DownloadItem manual = DownloadHistoryManager.getManualDownloadItem(this, id);
                    if (manual != null) {
                        out.put(id, manual);
                    }
                }
            }
        }
        return out;
    }

    private static String columnString(Cursor cursor, int index) {
        if (index < 0) return "";
        try {
            String v = cursor.getString(index);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private String robustFileName(String title, String localUri, String downloadUrl) {
        if (title == null || title.isEmpty()) {
            if (localUri != null && !localUri.isEmpty()) {
                try {
                    String path = Uri.parse(localUri).getPath();
                    if (path != null && !path.isEmpty()) {
                        title = new File(path).getName();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (title == null || title.isEmpty()) {
            title = URLUtil.guessFileName(downloadUrl, null, null);
        }
        return (title == null || title.isEmpty()) ? "Unknown" : title;
    }

    private String getMimeTypeFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        String lower = filePath.toLowerCase(Locale.ROOT);

        int queryPos = lower.indexOf('?');
        if (queryPos != -1) lower = lower.substring(0, queryPos);

        int dotPos = lower.lastIndexOf('.');
        if (dotPos < 0 || dotPos == lower.length() - 1) return null;

        String ext = lower.substring(dotPos + 1);

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime != null && !mime.isEmpty()) return mime;

        switch (ext) {
            case "apk":   return "application/vnd.android.package-archive";
            case "pdf":   return "application/pdf";
            case "zip":   return "application/zip";
            case "rar":   return "application/x-rar-compressed";
            case "7z":    return "application/x-7z-compressed";
            case "tar":   return "application/x-tar";
            case "gz":    return "application/gzip";
            case "mp4":
            case "m4v":   return "video/mp4";
            case "mkv":   return "video/x-matroska";
            case "avi":   return "video/x-msvideo";
            case "mov":   return "video/quicktime";
            case "webm":  return "video/webm";
            case "mp3":   return "audio/mpeg";
            case "aac":   return "audio/aac";
            case "ogg":   return "audio/ogg";
            case "flac":  return "audio/flac";
            case "wav":   return "audio/wav";
            case "m4a":   return "audio/mp4";
            case "jpg":
            case "jpeg":  return "image/jpeg";
            case "png":   return "image/png";
            case "gif":   return "image/gif";
            case "webp":  return "image/webp";
            case "bmp":   return "image/bmp";
            case "svg":   return "image/svg+xml";
            case "ico":   return "image/x-icon";
            case "txt":   return "text/plain";
            case "html":
            case "htm":   return "text/html";
            case "csv":   return "text/csv";
            case "json":  return "application/json";
            case "xml":   return "application/xml";
            case "doc":   return "application/msword";
            case "docx":  return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":   return "application/vnd.ms-excel";
            case "xlsx":  return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":   return "application/vnd.ms-powerpoint";
            case "pptx":  return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "epub":  return "application/epub+zip";
            default:      return FileOpenSupport.mimeTypeForExtension(ext);
        }
    }


    private void openFileWithApp(Context ctx, DownloadItem item) {
        if (item == null || !itemExists(item)) {
            Toast.makeText(ctx, "ファイルが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        String fileName = !DownloadSupport.isBlank(item.title) ? item.title : fileNameFromPath(item.filePath, item.localUri);
        String mimeType = getMimeTypeFromPath(fileName);
        Uri sourceUri = null;
        try {
            if (!DownloadSupport.isBlank(item.localUri)) {
                sourceUri = Uri.parse(item.localUri);
            }
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (sourceUri != null && "content".equalsIgnoreCase(sourceUri.getScheme())) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if (sourceUri != null && "file".equalsIgnoreCase(sourceUri.getScheme())) {
                File sourceFile = new File(sourceUri.getPath());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    sourceUri = FileProvider.getUriForFile(
                            ctx, ctx.getPackageName() + ".fileprovider", sourceFile);
                }
            } else if (!DownloadSupport.isBlank(item.filePath)) {
                File targetFile = new File(item.filePath);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    sourceUri = FileProvider.getUriForFile(
                            ctx, ctx.getPackageName() + ".fileprovider", targetFile);
                } else {
                    sourceUri = Uri.fromFile(targetFile);
                }
            }
            if (sourceUri == null) {
                throw new IllegalStateException("invalid file URI");
            }
            sourceUri = FileOpenSupport.toShareableUri(ctx, sourceUri, item.filePath);
            int result = FileOpenSupport.open(ctx, sourceUri, fileName, mimeType, false, null);
            if (result == FileOpenSupport.RESULT_NO_APP) {
                if (ctx instanceof android.app.Activity) {
                    FileOpenSupport.showNoAppDialog((android.app.Activity) ctx, fileName, mimeType, null);
                } else {
                    Toast.makeText(ctx, "開けるアプリがありません", Toast.LENGTH_SHORT).show();
                }
            } else if (result == FileOpenSupport.RESULT_FAILED) {
                Toast.makeText(ctx, "ファイルを開けません", Toast.LENGTH_SHORT).show();
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "開けるアプリがありません", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "ファイルを開けません", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeHistoryRecord(long downloadId) {
        try {
            String jsonStr = pref.getString(BrowserConstants.KEY_DOWNLOAD_HISTORY, "[]");
            JSONArray array = new JSONArray(jsonStr);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id", -1L) != downloadId) {
                    updated.put(obj);
                }
            }
            pref.edit().putString(BrowserConstants.KEY_DOWNLOAD_HISTORY, updated.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void replaceHistoryRecord(long oldId, long newId, String title, String path) {
        try {
            String jsonStr = pref.getString(BrowserConstants.KEY_DOWNLOAD_HISTORY, "[]");
            JSONArray array = new JSONArray(jsonStr);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id", -1L) == oldId) {
                    JSONObject next = new JSONObject(obj.toString());
                    next.put("id", newId);
                    next.put("fileName", title != null ? title : "");
                    next.put("filePath", path != null ? path : "");
                    next.remove("localUri");
                    next.remove("manualStatus");
                    next.remove("manualDownloaded");
                    next.remove("manualTotal");
                    next.remove("manualUpdatedAt");
                    next.remove("manualPaused");
                    next.remove("pausedByUser");
                    updated.put(next);
                } else {
                    updated.put(obj);
                }
            }
            pref.edit().putString(BrowserConstants.KEY_DOWNLOAD_HISTORY, updated.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void clearDownloadHistory() {
        pref.edit().remove(BrowserConstants.KEY_DOWNLOAD_HISTORY).apply();
        if (adapter != null) adapter.clearAll();
        updateEmptyState();
        Toast.makeText(this, "ダウンロード履歴を全消去しました", Toast.LENGTH_SHORT).show();
    }

    private void copyDownloadUrl(DownloadItem item) {
        if (item == null || DownloadSupport.isBlank(item.downloadUrl)) {
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("リンク", item.downloadUrl));
                Toast.makeText(this, "リンクをコピーしました", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
        }
    }

    private void removeFromHistory(DownloadItem item) {
        adapter.removeItem(item);
        removeHistoryRecord(item.downloadId);
        Toast.makeText(this, "履歴から消去しました", Toast.LENGTH_SHORT).show();
    }

    private void deleteFileAndRecord(DownloadItem item) {
        if (DownloadSupport.deleteLocalUri(this, item.localUri)
                || (!DownloadSupport.isBlank(item.filePath) && new File(item.filePath).delete())) {
            Toast.makeText(this, "ファイルを削除しました", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "ファイルの削除に失敗しました", Toast.LENGTH_SHORT).show();
        }
        adapter.removeItem(item);
        removeHistoryRecord(item.downloadId);
    }

    private void cancelItem(DownloadItem item) {
        if (item.manual) {
            DownloadFallbackManager.cancelDownload(this, item.downloadId);
        } else {
            try {
                downloadManager.remove(item.downloadId);
            } catch (Exception ignored) {
            }
        }
        adapter.removeItem(item);
        removeHistoryRecord(item.downloadId);
        Toast.makeText(this, "ダウンロードをキャンセルしました", Toast.LENGTH_SHORT).show();
    }

    private void pauseItem(DownloadItem item) {
        if (item.manual) {
            if (!DownloadFallbackManager.pauseDownload(this, item.downloadId)) {
                return;
            }
        } else {
            DownloadHistoryManager.setDownloadManagerPaused(this, item.downloadId, true);
            try {
                downloadManager.remove(item.downloadId);
            } catch (Exception ignored) {
            }
        }
        item.isPaused = true;
        item.status = DownloadManager.STATUS_PAUSED;
        adapter.notifyItem(item);
        Toast.makeText(this, "ダウンロードを一時停止しました", Toast.LENGTH_SHORT).show();
        startPolling();
    }

    private void resumeItem(DownloadItem item) {
        try {
            boolean started;
            if (item.manual) {
                started = DownloadFallbackManager.resumeDownload(this, item.downloadId);
                if (started) {
                    item.isPaused = false;
                    item.status = DownloadManager.STATUS_RUNNING;
                }
            } else {
                if (DownloadSupport.isBlank(item.downloadUrl)) throw new IllegalStateException("download url missing");
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.downloadUrl));
                request.setTitle(item.title);
                request.setDescription(item.description != null ? item.description : "Downloading file...");
                DownloadSupport.addHeaderIfSafe(request, "User-Agent", item.userAgent);
                DownloadSupport.addHeaderIfSafe(request, "Referer", item.referer);
                DownloadSupport.addHeaderIfSafe(request, "Cookie", CookieSitePolicy.cookieFor(item.downloadUrl));
                DownloadSupport.addHeaderIfSafe(request, "Authorization", BasicAuthManager.getAuthorizationHeaderForUrl(item.downloadUrl, item.basicAuthEnabled));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
                String resumeName = DownloadSupport.resolveUniqueDownloadFileName(DownloadHistoryActivity.this, item.title);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, resumeName);
                long oldId = item.downloadId;
                long newId = downloadManager.enqueue(request);
                item.downloadId = newId;
                item.title = resumeName;
                item.isPaused = false;
                item.manual = false;
                replaceHistoryRecord(oldId, newId, item.title, item.filePath);
                DownloadFallbackManager.register(newId, new DownloadFallbackManager.Spec(
                        DownloadHistoryActivity.this, null, item.downloadUrl, item.userAgent, null,
                        getMimeTypeFromPath(item.title), item.totalSize, item.referer, item.title,
                        getMimeTypeFromPath(item.title), item.basicAuthEnabled));
                DownloadHistoryManager.monitorDownloadProgress(DownloadHistoryActivity.this, newId, downloadManager);
                started = true;
            }
            if (!started) throw new IllegalStateException("resume failed");
            Toast.makeText(this, "ダウンロードを再開しました", Toast.LENGTH_SHORT).show();
            adapter.notifyItem(item);
            startPolling();
        } catch (Exception e) {
            Toast.makeText(this, "ダウンロードを再開できませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private void showActions(final DownloadItem item) {
        if (item == null) {
            return;
        }
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final boolean hasLink = !DownloadSupport.isBlank(item.downloadUrl);
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
            if (item.fileExists) {
                labels.add("開く");
                actions.add(() -> openFileWithApp(this, item));
            }
            if (hasLink) {
                labels.add("リンクをコピー");
                actions.add(() -> copyDownloadUrl(item));
            }
            labels.add("履歴から消去");
            actions.add(() -> removeFromHistory(item));
            if (item.fileExists) {
                labels.add("ファイルを削除");
                actions.add(() -> deleteFileAndRecord(item));
            }
        } else if (item.status == DownloadManager.STATUS_FAILED) {
            if (hasLink) {
                labels.add("リンクをコピー");
                actions.add(() -> copyDownloadUrl(item));
            }
            labels.add("履歴から消去");
            actions.add(() -> removeFromHistory(item));
        } else {
            if (item.isPaused) {
                labels.add("再開");
                actions.add(() -> resumeItem(item));
            } else {
                labels.add("一時停止");
                actions.add(() -> pauseItem(item));
            }
            labels.add("キャンセル");
            actions.add(() -> cancelItem(item));
            if (hasLink) {
                labels.add("リンクをコピー");
                actions.add(() -> copyDownloadUrl(item));
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.title == null || item.title.isEmpty() ? "操作を選択" : item.title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private static String joinParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part);
        }
        return sb.toString();
    }

    private static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int dg = (int) (Math.log10(size) / Math.log10(1024));
        if (dg < 0) dg = 0;
        if (dg >= units.length) dg = units.length - 1;
        return String.format(Locale.ROOT, "%.1f %s", size / Math.pow(1024, dg), units[dg]);
    }

    private static final int COLOR_GRAY = Color.parseColor("#5F6368");
    private static final int COLOR_BLUE = Color.parseColor("#1A73E8");
    private static final int COLOR_RED = Color.parseColor("#D93025");
    private static final int COLOR_ORANGE = Color.parseColor("#E37400");

    private static int[] visualFor(String name) {
        String ext = "";
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp":
            case "svg": case "ico": case "heic": case "avif":
                return new int[]{R.drawable.ic_file_image, Color.parseColor("#188038"), Color.parseColor("#E6F4EA")};
            case "mp4": case "mkv": case "avi": case "mov": case "webm": case "m4v": case "3gp": case "flv": case "ts":
                return new int[]{R.drawable.ic_file_video, Color.parseColor("#D93025"), Color.parseColor("#FCE8E6")};
            case "mp3": case "aac": case "ogg": case "flac": case "wav": case "m4a": case "opus":
                return new int[]{R.drawable.ic_file_audio, Color.parseColor("#E37400"), Color.parseColor("#FEEFC3")};
            case "zip": case "rar": case "7z": case "tar": case "gz": case "xz": case "bz2": case "tgz":
                return new int[]{R.drawable.ic_file_archive, Color.parseColor("#B06000"), Color.parseColor("#FEF7E0")};
            case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt": case "pptx":
            case "txt": case "csv": case "md": case "epub": case "json": case "xml": case "html": case "htm":
                return new int[]{R.drawable.ic_file_doc, Color.parseColor("#1A73E8"), Color.parseColor("#E8F0FE")};
            case "apk": case "xapk": case "apks":
                return new int[]{R.drawable.ic_file_generic, Color.parseColor("#188038"), Color.parseColor("#E6F4EA")};
            default:
                return new int[]{R.drawable.ic_file_generic, COLOR_GRAY, Color.parseColor("#F1F3F4")};
        }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final class DownloadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        final List<DownloadItem> all = new ArrayList<>();
        private final List<Object> rows = new ArrayList<>();
        private final DayLabeler labeler = new DayLabeler();
        private String query = "";

        void replaceAll(List<DownloadItem> items) {
            all.clear();
            all.addAll(items);
            rebuildRows();
            notifyDataSetChanged();
        }

        void clearAll() {
            all.clear();
            rows.clear();
            notifyDataSetChanged();
        }

        void setQuery(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(query)) {
                return;
            }
            query = normalized;
            rebuildRows();
            notifyDataSetChanged();
            updateEmptyState();
        }

        void removeItem(DownloadItem item) {
            all.remove(item);
            rebuildRows();
            notifyDataSetChanged();
            updateEmptyState();
        }

        void notifyItem(DownloadItem item) {
            int index = rows.indexOf(item);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        private boolean matches(DownloadItem item) {
            if (query.isEmpty()) {
                return true;
            }
            return (item.title != null && item.title.toLowerCase(Locale.ROOT).contains(query))
                    || (item.host != null && item.host.toLowerCase(Locale.ROOT).contains(query))
                    || (item.downloadUrl != null && item.downloadUrl.toLowerCase(Locale.ROOT).contains(query));
        }

        private void rebuildRows() {
            rows.clear();
            labeler.reset();
            int lastKey = Integer.MIN_VALUE;
            for (int i = 0, size = all.size(); i < size; i++) {
                DownloadItem item = all.get(i);
                if (!matches(item)) {
                    continue;
                }
                int key = labeler.key(item.timestamp);
                if (key != lastKey) {
                    rows.add(labeler.label(item.timestamp));
                    lastKey = key;
                }
                rows.add(item);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_download_header, parent, false));
            }
            return new ItemHolder(inflater.inflate(R.layout.item_download, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).text.setText((String) row);
                return;
            }
            final DownloadItem item = (DownloadItem) row;
            ItemHolder h = (ItemHolder) holder;
            if (item.title == null || item.title.isEmpty()) {
                item.title = "ダウンロード " + item.downloadId;
            }
            boolean deleted = item.status == DownloadManager.STATUS_SUCCESSFUL && !item.fileExists;
            h.fileTitle.setText(item.title);
            if (deleted) {
                h.fileTitle.setPaintFlags(h.fileTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.fileTitle.setTextColor(Color.parseColor("#80868B"));
            } else {
                h.fileTitle.setPaintFlags(h.fileTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                h.fileTitle.setTextColor(Color.parseColor("#202124"));
            }

            int[] visual = visualFor(item.title);
            h.fileIcon.setImageResource(visual[0]);
            h.fileIcon.setColorFilter(visual[1]);
            h.chip.setColor(visual[2]);

            String statusText;
            int statusColor = COLOR_GRAY;
            boolean showProgress = false;
            String progressText = item.totalSize > 0
                    ? formatSize(item.downloadedSize) + " / " + formatSize(item.totalSize) + " (" + item.getProgress() + "%)"
                    : formatSize(item.downloadedSize);
            switch (item.status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    statusText = deleted
                            ? "ファイルが削除されました"
                            : joinParts(item.totalSize > 0 ? formatSize(item.totalSize) : "", item.host);
                    break;
                case DownloadManager.STATUS_FAILED:
                    statusText = joinParts("ダウンロードに失敗しました", item.host);
                    statusColor = COLOR_RED;
                    break;
                case DownloadManager.STATUS_RUNNING:
                    statusText = "ダウンロード中 · " + progressText;
                    statusColor = COLOR_BLUE;
                    showProgress = true;
                    break;
                case DownloadManager.STATUS_PAUSED:
                    statusText = "一時停止中 · " + progressText;
                    statusColor = COLOR_ORANGE;
                    showProgress = true;
                    break;
                case DownloadManager.STATUS_PENDING:
                    statusText = "待機中";
                    break;
                default:
                    statusText = "不明";
            }
            if (item.isPaused && item.status != DownloadManager.STATUS_SUCCESSFUL && item.status != DownloadManager.STATUS_FAILED) {
                statusText = "一時停止中 · " + progressText;
                statusColor = COLOR_ORANGE;
                showProgress = true;
            }
            h.fileStatus.setText(statusText);
            h.fileStatus.setTextColor(statusColor);
            if (showProgress) {
                h.progressBar.setVisibility(View.VISIBLE);
                h.progressBar.setIndeterminate(item.totalSize <= 0);
                if (item.totalSize > 0) h.progressBar.setProgress(item.getProgress());
            } else {
                h.progressBar.setVisibility(View.GONE);
                h.progressBar.setIndeterminate(false);
            }

            h.itemView.setOnClickListener(v -> {
                if (item.status == DownloadManager.STATUS_SUCCESSFUL && item.fileExists) {
                    openFileWithApp(DownloadHistoryActivity.this, item);
                } else {
                    showActions(item);
                }
            });
            h.itemView.setOnLongClickListener(v -> {
                showActions(item);
                return true;
            });
            h.btnMore.setOnClickListener(v -> showActions(item));
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
            text = itemView.findViewById(R.id.downloadDateHeader);
        }
    }

    private static final class ItemHolder extends RecyclerView.ViewHolder {
        final ImageView fileIcon;
        final TextView fileTitle;
        final TextView fileStatus;
        final ProgressBar progressBar;
        final ImageButton btnMore;
        final GradientDrawable chip;

        ItemHolder(View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileTitle = itemView.findViewById(R.id.fileTitle);
            fileStatus = itemView.findViewById(R.id.fileStatus);
            progressBar = itemView.findViewById(R.id.progressBar);
            btnMore = itemView.findViewById(R.id.btnMore);
            chip = new GradientDrawable();
            chip.setCornerRadius(12f * itemView.getResources().getDisplayMetrics().density);
            fileIcon.setBackground(chip);
        }
    }
}
