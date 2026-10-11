package com.coara.browser2.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.Window;
import android.widget.Toast;

import com.coara.browser2.gecko.BrowserView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class ScreenshotSaver {
    private static final String RELATIVE_DIR = "DCIM/Screenshot";

    private ScreenshotSaver() {
    }

    public static void capture(final Activity activity, final BrowserView page, final ExecutorService io) {
        if (page != null && page.isCaptureReady()) {
            page.capture(bitmap -> {
                if (bitmap != null && !bitmap.isRecycled() && !isBlackFrame(bitmap)) {
                    persist(activity, bitmap, io);
                } else {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    captureWindow(activity, io);
                }
            });
            return;
        }
        captureWindow(activity, io);
    }

    private static void captureWindow(final Activity activity, final ExecutorService io) {
        Window window = activity.getWindow();
        int w = window.getDecorView().getWidth();
        int h = window.getDecorView().getHeight();
        if (w <= 0 || h <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(activity, "スクリーンショットを取得できませんでした");
            return;
        }
        final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(window, bitmap, result -> {
                if (result == PixelCopy.SUCCESS && !isBlackFrame(bitmap)) {
                    persist(activity, bitmap, io);
                } else {
                    bitmap.recycle();
                    toast(activity, "スクリーンショットを取得できませんでした");
                }
            }, com.coara.browser2.util.UiThread.mainHandler());
        } catch (Throwable t) {
            bitmap.recycle();
            toast(activity, "スクリーンショットを取得できませんでした");
        }
    }

    private static void persist(final Activity activity, final Bitmap source, final ExecutorService io) {
        Runnable job = () -> {
            Bitmap flat = null;
            try {
                flat = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(flat);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(source, 0f, 0f, null);
                String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";
                String where = save(activity, flat, name);
                toast(activity, where == null ? "スクリーンショットの保存に失敗しました" : "スクリーンショットを保存しました: " + where);
            } catch (Throwable t) {
                toast(activity, "スクリーンショット保存中にエラー: " + t.getMessage());
            } finally {
                if (flat != null) {
                    flat.recycle();
                }
                source.recycle();
            }
        };
        if (io == null || io.isShutdown()) {
            new Thread(job).start();
        } else {
            io.execute(job);
        }
    }

    private static String save(Context context, Bitmap bitmap, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return null;
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    resolver.delete(uri, null, null);
                    return null;
                }
            } catch (Exception e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return RELATIVE_DIR + "/" + name;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), RELATIVE_DIR);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            return null;
        }
        File file = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                return null;
            }
            out.flush();
        }
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
        return file.getAbsolutePath();
    }

    public static boolean isBlackFrame(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int steps = 16;
        for (int yi = 0; yi < steps; yi++) {
            for (int xi = 0; xi < steps; xi++) {
                int x = Math.min(w - 1, (int) ((xi + 0.5f) * w / steps));
                int y = Math.min(h - 1, (int) ((yi + 0.5f) * h / steps));
                int c = bitmap.getPixel(x, y);
                if ((c >>> 24) != 0 && (((c >> 16) & 0xFF) > 6 || ((c >> 8) & 0xFF) > 6 || (c & 0xFF) > 6)) {
                    return false;
                }
                if ((c >>> 24) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void toast(final Activity activity, final String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
