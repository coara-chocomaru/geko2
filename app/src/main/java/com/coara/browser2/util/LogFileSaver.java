package com.coara.browser2.util;

import android.content.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LogFileSaver {
    private LogFileSaver() {
    }

    public static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    public static String save(Context context, String fileName, String text) {
        PublicStorageWriter.Target target = null;
        try {
            target = PublicStorageWriter.openDownloadsTarget(context, fileName, "text/plain");
            OutputStream out = target.outputStream;
            byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            int offset = 0;
            while (offset < data.length) {
                int n = Math.min(65536, data.length - offset);
                out.write(data, offset, n);
                offset += n;
            }
            out.flush();
            String path = target.displayPath;
            if (!PublicStorageWriter.finish(context, target)) {
                return null;
            }
            return path;
        } catch (IOException | RuntimeException e) {
            PublicStorageWriter.abort(context, target);
            return null;
        }
    }
}
