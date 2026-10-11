package com.coara.browser2.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;

public final class BackgroundWallpaper {
    private static final int MAX_LONG_SIDE = 1280;
    private static final int MAX_ALPHA_BYTES = 1536 * 1024;

    private static volatile String dataUri;
    private static volatile int averageColor = Color.WHITE;
    private static volatile long stamp;

    private BackgroundWallpaper() {
    }

    public static boolean isReady() {
        return dataUri != null;
    }

    public static String getDataUri() {
        return dataUri;
    }

    public static int getAverageColor() {
        return averageColor;
    }

    public static long getStamp() {
        return stamp;
    }

    public static void clear() {
        dataUri = null;
        averageColor = Color.WHITE;
        stamp++;
    }

    public static boolean load(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        Bitmap source = null;
        Bitmap scaled = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }
            int longSide = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longSide / (sample * 2) >= MAX_LONG_SIDE) {
                sample <<= 1;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            source = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (source == null) {
                return false;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            int decodedLong = Math.max(width, height);
            if (decodedLong > MAX_LONG_SIDE) {
                float ratio = (float) MAX_LONG_SIDE / decodedLong;
                int w = Math.max(1, Math.round(width * ratio));
                int h = Math.max(1, Math.round(height * ratio));
                scaled = Bitmap.createScaledBitmap(source, w, h, true);
                if (scaled != source) {
                    source.recycle();
                }
                source = scaled;
                scaled = null;
            }

            int average = averageOf(source);
            boolean alpha = source.hasAlpha();
            String mime = "image/jpeg";
            byte[] bytes = null;
            if (alpha) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                source.compress(Bitmap.CompressFormat.PNG, 100, png);
                if (png.size() <= MAX_ALPHA_BYTES) {
                    bytes = png.toByteArray();
                    mime = "image/png";
                }
            }
            if (bytes == null) {
                Bitmap flat = source;
                if (alpha) {
                    flat = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(flat);
                    canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(source, 0f, 0f, null);
                }
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                flat.compress(Bitmap.CompressFormat.JPEG, 82, jpeg);
                if (flat != source) {
                    flat.recycle();
                }
                bytes = jpeg.toByteArray();
                mime = "image/jpeg";
            }
            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
            dataUri = "data:" + mime + ";base64," + encoded;
            averageColor = average;
            stamp++;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (source != null && !source.isRecycled()) {
                source.recycle();
            }
            if (scaled != null && !scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    private static int averageOf(Bitmap bitmap) {
        Bitmap one = null;
        try {
            one = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            int pixel = one.getPixel(0, 0);
            return Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel));
        } catch (Throwable t) {
            return Color.WHITE;
        } finally {
            if (one != null && one != bitmap && !one.isRecycled()) {
                one.recycle();
            }
        }
    }
}
