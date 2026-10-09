package com.coara.browser2.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FileOpenSupport {
    public static final int RESULT_OPENED = 0;
    public static final int RESULT_NO_APP = 1;
    public static final int RESULT_FAILED = 2;

    private static final Map<String, String> EXTENSION_MIME = new HashMap<>(512);

    static {
        put("application/pdf", "pdf");
        put("application/postscript", "ps", "eps", "ai");
        put("application/msword", "doc", "dot");
        put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx");
        put("application/vnd.ms-word.document.macroenabled.12", "docm");
        put("application/vnd.ms-excel", "xls", "xlt", "xla");
        put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx");
        put("application/vnd.ms-excel.sheet.macroenabled.12", "xlsm");
        put("application/vnd.ms-excel.sheet.binary.macroenabled.12", "xlsb");
        put("application/vnd.ms-powerpoint", "ppt", "pot", "pps");
        put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        put("application/vnd.openxmlformats-officedocument.presentationml.template", "potx");
        put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx");
        put("application/vnd.ms-powerpoint.presentation.macroenabled.12", "pptm");
        put("application/vnd.oasis.opendocument.text", "odt");
        put("application/vnd.oasis.opendocument.spreadsheet", "ods");
        put("application/vnd.oasis.opendocument.presentation", "odp");
        put("application/vnd.oasis.opendocument.graphics", "odg");
        put("application/vnd.oasis.opendocument.formula", "odf");
        put("application/vnd.apple.pages", "pages");
        put("application/vnd.apple.numbers", "numbers");
        put("application/vnd.apple.keynote", "key");
        put("application/rtf", "rtf");
        put("application/epub+zip", "epub");
        put("application/x-mobipocket-ebook", "mobi", "prc");
        put("application/vnd.amazon.ebook", "azw");
        put("application/vnd.amazon.mobi8-ebook", "azw3");
        put("application/x-fictionbook+xml", "fb2");
        put("image/vnd.djvu", "djvu", "djv");
        put("application/vnd.ms-xpsdocument", "xps");
        put("application/oxps", "oxps");
        put("application/vnd.ms-htmlhelp", "chm");
        put("application/vnd.comicbook+zip", "cbz");
        put("application/vnd.comicbook-rar", "cbr");
        put("text/csv", "csv");
        put("text/tab-separated-values", "tsv");
        put("text/markdown", "md", "markdown", "mkd");
        put("text/plain", "txt", "log", "ini", "conf", "cfg", "properties", "lrc", "rst", "nfo", "sub", "text");
        put("text/html", "html", "htm");
        put("application/xhtml+xml", "xhtml", "xht");
        put("multipart/related", "mht", "mhtml");
        put("application/xml", "xml", "xsd", "xsl", "xslt", "plist", "rss", "atom");
        put("application/json", "json", "jsonl", "geojson");
        put("application/yaml", "yaml", "yml");
        put("application/toml", "toml");
        put("application/sql", "sql");
        put("application/javascript", "js", "mjs");
        put("text/css", "css");
        put("text/x-java-source", "java");
        put("text/x-kotlin", "kt", "kts");
        put("text/x-python", "py");
        put("text/x-shellscript", "sh", "bash");
        put("text/x-csrc", "c", "h");
        put("text/x-c++src", "cpp", "cc", "cxx", "hpp");
        put("application/x-httpd-php", "php");
        put("text/x-ruby", "rb");
        put("text/x-go", "go");
        put("text/x-rust", "rs");
        put("application/x-tex", "tex");
        put("text/calendar", "ics", "ical");
        put("text/vcard", "vcf", "vcard");
        put("message/rfc822", "eml");
        put("application/vnd.ms-outlook", "msg");
        put("application/mbox", "mbox");
        put("application/zip", "zip", "xapk", "apks", "apkm", "zipx");
        put("application/vnd.rar", "rar");
        put("application/x-7z-compressed", "7z");
        put("application/x-tar", "tar");
        put("application/gzip", "gz", "tgz");
        put("application/x-bzip2", "bz2", "tbz2");
        put("application/x-xz", "xz", "txz");
        put("application/zstd", "zst");
        put("application/x-lzma", "lzma");
        put("application/x-lzip", "lz");
        put("application/x-compress", "z");
        put("application/vnd.ms-cab-compressed", "cab");
        put("application/x-iso9660-image", "iso");
        put("application/x-apple-diskimage", "dmg");
        put("application/x-arj", "arj");
        put("application/x-cpio", "cpio");
        put("application/vnd.debian.binary-package", "deb");
        put("application/x-rpm", "rpm");
        put("application/vnd.android.package-archive", "apk");
        put("application/java-archive", "jar", "war");
        put("application/octet-stream", "aab", "bin", "dat", "img", "dex");
        put("application/x-msdownload", "exe", "dll");
        put("application/x-msi", "msi");
        put("application/vnd.sqlite3", "sqlite", "sqlite3", "db");
        put("application/x-x509-ca-cert", "crt", "cer", "pem", "der");
        put("application/x-pkcs12", "p12", "pfx");
        put("application/x-bittorrent", "torrent");
        put("application/x-shockwave-flash", "swf");
        put("application/wasm", "wasm");
        put("image/jpeg", "jpg", "jpeg", "jpe", "jfif");
        put("image/png", "png");
        put("image/apng", "apng");
        put("image/gif", "gif");
        put("image/webp", "webp");
        put("image/bmp", "bmp");
        put("image/svg+xml", "svg", "svgz");
        put("image/x-icon", "ico");
        put("image/heic", "heic");
        put("image/heif", "heif");
        put("image/avif", "avif");
        put("image/jxl", "jxl");
        put("image/tiff", "tif", "tiff");
        put("image/vnd.adobe.photoshop", "psd");
        put("image/x-adobe-dng", "dng");
        put("image/x-canon-cr2", "cr2");
        put("image/x-nikon-nef", "nef");
        put("image/x-sony-arw", "arw");
        put("video/mp4", "mp4");
        put("video/x-m4v", "m4v");
        put("video/quicktime", "mov");
        put("video/webm", "webm");
        put("video/x-matroska", "mkv");
        put("video/x-msvideo", "avi");
        put("video/3gpp", "3gp");
        put("video/3gpp2", "3g2");
        put("video/mp2t", "ts", "mts", "m2ts");
        put("video/mpeg", "mpg", "mpeg", "mpe", "vob");
        put("video/x-ms-wmv", "wmv");
        put("video/x-ms-asf", "asf");
        put("video/x-flv", "flv");
        put("video/ogg", "ogv");
        put("audio/mpeg", "mp3", "mp2");
        put("audio/mp4", "m4a");
        put("audio/aac", "aac");
        put("audio/flac", "flac");
        put("audio/wav", "wav");
        put("audio/ogg", "ogg", "oga");
        put("audio/opus", "opus");
        put("audio/3gpp", "3gpp");
        put("audio/x-ms-wma", "wma");
        put("audio/midi", "mid", "midi", "kar");
        put("audio/amr", "amr");
        put("audio/aiff", "aif", "aiff");
        put("audio/x-matroska", "mka");
        put("audio/webm", "weba");
        put("audio/x-mpegurl", "m3u", "m3u8");
        put("audio/x-scpls", "pls");
        put("font/ttf", "ttf");
        put("font/otf", "otf");
        put("font/woff", "woff");
        put("font/woff2", "woff2");
        put("application/x-subrip", "srt");
        put("text/vtt", "vtt");
        put("text/x-ssa", "ass", "ssa");
        put("model/stl", "stl");
        put("model/gltf-binary", "glb");
        put("model/gltf+json", "gltf");
        put("model/obj", "obj");
        put("application/acad", "dwg");
        put("image/vnd.dxf", "dxf");
        put("application/vnd.google-earth.kml+xml", "kml");
        put("application/vnd.google-earth.kmz", "kmz");
        put("application/gpx+xml", "gpx");
    }

    private FileOpenSupport() {
    }

    private static void put(String mime, String... extensions) {
        for (String extension : extensions) {
            EXTENSION_MIME.put(extension, mime);
        }
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String normalizeMime(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) normalized = normalized.substring(0, separator).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isGenericMime(String mimeType) {
        return isBlank(mimeType)
                || "*/*".equals(mimeType)
                || "application/octet-stream".equals(mimeType)
                || "binary/octet-stream".equals(mimeType)
                || "application/binary".equals(mimeType)
                || "application/x-binary".equals(mimeType);
    }

    public static String extensionOf(String value) {
        if (isBlank(value)) return null;
        String candidate = value;
        int query = candidate.indexOf('?');
        if (query >= 0) candidate = candidate.substring(0, query);
        int fragment = candidate.indexOf('#');
        if (fragment >= 0) candidate = candidate.substring(0, fragment);
        int slash = candidate.lastIndexOf('/');
        if (slash >= 0) candidate = candidate.substring(slash + 1);
        int dot = candidate.lastIndexOf('.');
        if (dot < 0 || dot >= candidate.length() - 1) return null;
        return candidate.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String mimeTypeForExtension(String extension) {
        if (isBlank(extension)) return null;
        String key = extension.toLowerCase(Locale.ROOT);
        String mapped = EXTENSION_MIME.get(key);
        if (mapped != null) return mapped;
        try {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(key);
            return isGenericMime(mime) ? null : mime;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String mimeTypeForFileName(String fileName) {
        return mimeTypeForExtension(extensionOf(fileName));
    }

    public static String resolveMimeType(Context context, Uri uri, String fileName, String preferredMime, String targetMime) {
        String normalizedPreferred = normalizeMime(preferredMime);
        if (isGenericMime(normalizedPreferred)) {
            normalizedPreferred = normalizeMime(targetMime);
        }
        if (!isBlank(normalizedPreferred) && !isGenericMime(normalizedPreferred)) {
            return normalizedPreferred;
        }
        String extension = extensionOf(fileName);
        if (extension == null && uri != null) {
            extension = extensionOf(uri.getPath());
        }
        String extensionMime = mimeTypeForExtension(extension);
        if (!isBlank(extensionMime)) {
            return extensionMime;
        }
        try {
            if (context != null && uri != null) {
                String fromResolver = normalizeMime(context.getContentResolver().getType(uri));
                if (!isBlank(fromResolver) && !isGenericMime(fromResolver)) {
                    return fromResolver;
                }
            }
        } catch (Exception ignored) {
        }
        return isBlank(normalizedPreferred) ? "*/*" : normalizedPreferred;
    }

    private static void addCandidate(List<String> list, String mime) {
        String normalized = normalizeMime(mime);
        if (normalized != null && !list.contains(normalized)) {
            list.add(normalized);
        }
    }

    public static List<String> candidateMimes(Context context, Uri uri, String fileName, String preferredMime) {
        List<String> list = new ArrayList<>(8);
        String primary = resolveMimeType(context, uri, fileName, preferredMime, null);
        if (!isGenericMime(primary)) {
            addCandidate(list, primary);
        }
        String extension = extensionOf(fileName);
        if (extension == null && uri != null) {
            extension = extensionOf(uri.getPath());
        }
        addCandidate(list, mimeTypeForExtension(extension));
        addCandidate(list, preferredMime);
        try {
            if (context != null && uri != null) {
                addCandidate(list, context.getContentResolver().getType(uri));
            }
        } catch (Exception ignored) {
        }
        int count = list.size();
        for (int i = 0; i < count; i++) {
            String mime = list.get(i);
            if (isGenericMime(mime)) continue;
            int slash = mime.indexOf('/');
            if (slash > 0 && !mime.endsWith("/*")) {
                String major = mime.substring(0, slash);
                if ("image".equals(major) || "video".equals(major) || "audio".equals(major) || "text".equals(major)) {
                    addCandidate(list, major + "/*");
                }
            }
        }
        if (isTextLike(list, extension)) {
            addCandidate(list, "text/plain");
        }
        addCandidate(list, "*/*");
        return list;
    }

    private static boolean isTextLike(List<String> mimes, String extension) {
        for (String mime : mimes) {
            if (mime.startsWith("text/") || mime.endsWith("+xml") || mime.endsWith("json") || mime.endsWith("javascript")
                    || "application/xml".equals(mime) || "application/yaml".equals(mime) || "application/toml".equals(mime)
                    || "application/sql".equals(mime) || "application/x-httpd-php".equals(mime)) {
                return true;
            }
        }
        return false;
    }

    public static Intent buildViewIntent(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, isBlank(mime) ? "*/*" : mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            intent.setClipData(ClipData.newRawUri("", uri));
        } catch (Exception ignored) {
        }
        return intent;
    }

    private static boolean hasHandler(PackageManager pm, Intent intent) {
        try {
            return pm != null && !pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Uri toShareableUri(Context context, Uri uri, String filePath) {
        if (uri != null && !"file".equalsIgnoreCase(uri.getScheme())) {
            return uri;
        }
        File file = null;
        if (uri != null && !isBlank(uri.getPath())) {
            file = new File(uri.getPath());
        } else if (!isBlank(filePath)) {
            file = new File(filePath);
        }
        if (file == null || !file.isFile()) {
            return uri;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            } catch (Exception ignored) {
            }
            Uri media = queryMediaStoreUri(context, file);
            if (media != null) {
                return media;
            }
            return uri != null ? uri : Uri.fromFile(file);
        }
        return uri != null ? uri : Uri.fromFile(file);
    }

    private static Uri queryMediaStoreUri(Context context, File file) {
        try {
            Uri collection = MediaStore.Files.getContentUri("external");
            try (Cursor cursor = context.getContentResolver().query(collection,
                    new String[]{MediaStore.Files.FileColumns._ID},
                    MediaStore.Files.FileColumns.DATA + "=?",
                    new String[]{file.getAbsolutePath()}, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return Uri.withAppendedPath(collection, Long.toString(cursor.getLong(0)));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static int open(Context context, Uri uri, String fileName, String preferredMime, boolean useChooser, String chooserTitle) {
        if (context == null || uri == null) {
            return RESULT_FAILED;
        }
        List<String> mimes = candidateMimes(context, uri, fileName, preferredMime);
        PackageManager pm = context.getPackageManager();
        boolean denied = false;
        for (String mime : mimes) {
            Intent view = buildViewIntent(uri, mime);
            if (!hasHandler(pm, view)) continue;
            try {
                if (useChooser) {
                    Intent chooser = Intent.createChooser(view, chooserTitle == null ? "アプリを選択" : chooserTitle);
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(chooser);
                } else {
                    context.startActivity(view);
                }
                return RESULT_OPENED;
            } catch (ActivityNotFoundException ignored) {
            } catch (SecurityException e) {
                denied = true;
            } catch (Exception ignored) {
            }
        }
        for (String mime : mimes) {
            try {
                context.startActivity(buildViewIntent(uri, mime));
                return RESULT_OPENED;
            } catch (ActivityNotFoundException ignored) {
            } catch (SecurityException e) {
                denied = true;
            } catch (Exception ignored) {
            }
        }
        return denied ? RESULT_FAILED : RESULT_NO_APP;
    }

    private static String searchKeyword(String extension, String mime) {
        String ext = isBlank(extension) ? "" : extension.toLowerCase(Locale.ROOT);
        String kind;
        if (mime != null && mime.startsWith("image/")) {
            kind = "画像ビューア";
        } else if (mime != null && mime.startsWith("video/")) {
            kind = "動画プレーヤー";
        } else if (mime != null && mime.startsWith("audio/")) {
            kind = "音楽プレーヤー";
        } else if (mime != null && (mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("xml"))) {
            kind = "テキストエディタ";
        } else if (mime != null && (mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") || mime.contains("gzip"))) {
            kind = "解凍 アーカイブ";
        } else if (mime != null && (mime.contains("pdf") || mime.contains("word") || mime.contains("excel") || mime.contains("powerpoint") || mime.contains("opendocument") || mime.contains("epub"))) {
            kind = "ビューア";
        } else {
            kind = "ファイル 開く";
        }
        return (ext.isEmpty() ? "" : ext + " ") + kind;
    }

    public static void showNoAppDialog(Activity activity, String fileName, String preferredMime, Runnable onDismiss) {
        if (activity == null || activity.isFinishing()) {
            if (onDismiss != null) onDismiss.run();
            return;
        }
        final String extension = extensionOf(fileName);
        final String mime = normalizeMime(preferredMime) != null ? normalizeMime(preferredMime) : mimeTypeForExtension(extension);
        final String keyword = searchKeyword(extension, mime);
        String label = isBlank(fileName) ? "このファイル" : fileName;
        try {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("開けるアプリがありません")
                    .setMessage(label + (isBlank(extension) ? "" : " (." + extension + ")") + " を開けるアプリが見つかりませんでした。\nストアで対応アプリを探しますか？")
                    .setPositiveButton("ストアで探す", (dialog, which) -> openStoreSearch(activity, keyword))
                    .setNegativeButton("キャンセル", null)
                    .setOnDismissListener(dialog -> {
                        if (onDismiss != null) onDismiss.run();
                    })
                    .show();
        } catch (Exception ignored) {
            if (onDismiss != null) onDismiss.run();
        }
    }

    private static void openStoreSearch(Activity activity, String keyword) {
        String encoded = Uri.encode(keyword);
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + encoded + "&c=apps"));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(market);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + encoded + "&c=apps"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(web);
        } catch (Exception ignored) {
        }
    }
}
