package com.coara.browser2;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.coara.browser2.model.DownloadItem;
import com.coara.browser2.util.DownloadSupport;
import com.coara.browser2.util.FileOpenSupport;

import java.io.File;

public class DownloadOpenActivity extends Activity {
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_LOCAL_URI = "local_uri";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_MIME_TYPE = "mime_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openTarget();
    }

    private void openTarget() {
        final int notificationId = getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        Target target = resolveTarget();
        if (target == null || target.uri == null || !exists(target.uri)) {
            cancelNotification(notificationId);
            Toast.makeText(this, "存在しません…", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String preferredMime = FileOpenSupport.normalizeMime(getIntent().getStringExtra(EXTRA_MIME_TYPE));
        if (FileOpenSupport.isGenericMime(preferredMime)) {
            preferredMime = FileOpenSupport.normalizeMime(target.mimeType);
        }
        int result = FileOpenSupport.open(this, target.uri, target.fileName, preferredMime, true, "アプリを選択");
        if (result == FileOpenSupport.RESULT_OPENED) {
            cancelNotification(notificationId);
            finish();
        } else if (result == FileOpenSupport.RESULT_NO_APP) {
            FileOpenSupport.showNoAppDialog(this, target.fileName, preferredMime, this::finish);
        } else {
            Toast.makeText(this, "ファイルを開けません", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private Target resolveTarget() {
        String localUri = getIntent().getStringExtra(EXTRA_LOCAL_URI);
        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);

        if (isUsableUri(localUri)) {
            Uri uri = Uri.parse(localUri);
            if (exists(uri)) {
                return new Target(FileOpenSupport.toShareableUri(this, uri, filePath), chooseFileName(fileName, uri, filePath), null);
            }
        }

        if (!DownloadSupport.isBlank(filePath)) {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                Uri uri = toContentUri(this, file);
                if (uri != null) {
                    return new Target(uri, chooseFileName(fileName, uri, filePath), null);
                }
            }
        }

        long downloadId = getIntent().getLongExtra(EXTRA_DOWNLOAD_ID, -1L);
        if (downloadId > 0L) {
            Target managerTarget = resolveFromDownloadManager(downloadId);
            if (managerTarget != null) {
                return managerTarget;
            }
            DownloadItem manual = DownloadHistoryManager.getManualDownloadItem(this, downloadId);
            if (manual != null) {
                if (!DownloadSupport.isBlank(manual.localUri) && isUsableUri(manual.localUri)) {
                    Uri uri = Uri.parse(manual.localUri);
                    if (exists(uri)) {
                        return new Target(FileOpenSupport.toShareableUri(this, uri, manual.filePath), chooseFileName(manual.title, uri, manual.filePath), manual.mimeType);
                    }
                }
                if (!DownloadSupport.isBlank(manual.filePath)) {
                    File file = new File(manual.filePath);
                    if (file.exists() && file.isFile()) {
                        Uri uri = toContentUri(this, file);
                        if (uri != null) {
                            return new Target(uri, chooseFileName(manual.title, uri, manual.filePath), manual.mimeType);
                        }
                    }
                }
            }
        }
        return null;
    }

    private Target resolveFromDownloadManager(long downloadId) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return null;
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            try (android.database.Cursor cursor = manager.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) return null;
                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                String localUri = uriIndex >= 0 ? cursor.getString(uriIndex) : null;
                String title = titleIndex >= 0 ? cursor.getString(titleIndex) : null;
                int mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                String mimeType = mimeIndex >= 0 ? cursor.getString(mimeIndex) : null;
                if (isUsableUri(localUri)) {
                    Uri uri = Uri.parse(localUri);
                    if (exists(uri)) {
                        return new Target(FileOpenSupport.toShareableUri(this, uri, null), chooseFileName(title, uri, null), mimeType);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Uri toContentUri(Context context, File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            }
            return Uri.fromFile(file);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isUsableUri(String value) {
        if (DownloadSupport.isBlank(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            return uri.getScheme() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean exists(Uri uri) {
        if (uri == null) return false;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                return !DownloadSupport.isBlank(path) && new File(path).isFile();
            }
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                try (android.content.res.AssetFileDescriptor descriptor = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                    return descriptor != null;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String chooseFileName(String preferred, Uri uri, String filePath) {
        if (!DownloadSupport.isBlank(preferred)) return preferred;
        if (!DownloadSupport.isBlank(filePath)) {
            String name = new File(filePath).getName();
            if (!DownloadSupport.isBlank(name)) return name;
        }
        try {
            String last = uri == null ? null : uri.getLastPathSegment();
            if (!DownloadSupport.isBlank(last)) return last;
        } catch (Exception ignored) {
        }
        return "download";
    }

    private void cancelNotification(int notificationId) {
        if (notificationId < 0) return;
        try {
            NotificationManagerCompat.from(this).cancel(notificationId);
        } catch (Exception ignored) {
        }
    }

    private static final class Target {
        final Uri uri;
        final String fileName;
        final String mimeType;
        Target(Uri uri, String fileName, String mimeType) {
            this.uri = uri;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }
}
