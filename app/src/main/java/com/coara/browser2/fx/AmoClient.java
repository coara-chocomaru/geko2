package com.coara.browser2.fx;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AmoClient {

    public interface Callback<T> {
        void onResult(T value, String error);
    }

    private static final String SEARCH = "https://addons.mozilla.org/api/v5/addons/search/";
    private static final String USER_AGENT = "Mozilla/5.0 (Android 10; Mobile; rv:158.0) Gecko/158.0 Firefox/158.0";
    private static final int TIMEOUT_MS = 15000;
    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_ICON_BYTES = 1024 * 1024;
    private static final int MAX_ICON_SIDE = 128;
    private static final int MAX_ICON_REDIRECTS = 3;

    private static final ExecutorService EXECUTOR = createExecutor();

    private static ExecutorService createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "coara-amo");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> ICONS = new LruCache<String, Bitmap>(1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, (value.getByteCount() + 1023) / 1024);
        }
    };

    private AmoClient() {
    }

    private static String lang() {
        Locale l = Locale.getDefault();
        String tag = l.getLanguage();
        String country = l.getCountry();
        if (tag.isEmpty()) {
            return "en-US";
        }
        return country.isEmpty() ? tag : tag + "-" + country;
    }

    public static void recommended(int page, Callback<List<AmoAddon>> callback) {
        query("", page, true, callback);
    }

    public static void search(String text, int page, Callback<List<AmoAddon>> callback) {
        query(text, page, false, callback);
    }

    private static void query(final String text, final int page, final boolean recommended, final Callback<List<AmoAddon>> callback) {
        EXECUTOR.execute(() -> {
            List<AmoAddon> result = null;
            String error = null;
            try {
                result = fetch(text, page, recommended);
                if (recommended && result.isEmpty() && page <= 1) {
                    result = fetch(text, page, false);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<AmoAddon> out = result;
            final String err = error;
            MAIN.post(() -> callback.onResult(out, err));
        });
    }

    private static List<AmoAddon> fetch(String text, int page, boolean recommended) throws Exception {
        StringBuilder url = new StringBuilder(SEARCH);
        url.append("?app=android&type=extension&page_size=30&page=").append(Math.max(1, page));
        url.append("&lang=").append(URLEncoder.encode(lang(), "UTF-8"));
        if (text != null && !text.trim().isEmpty()) {
            url.append("&q=").append(URLEncoder.encode(text.trim(), "UTF-8")).append("&sort=relevance,users");
        } else {
            url.append("&sort=users");
            if (recommended) {
                url.append("&promoted=recommended");
            }
        }
        String body = new String(download(url.toString()), StandardCharsets.UTF_8);
        JSONArray results = new JSONObject(body).optJSONArray("results");
        List<AmoAddon> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (int i = 0; i < results.length(); i++) {
            AmoAddon addon = parse(results.optJSONObject(i));
            if (addon != null) {
                out.add(addon);
            }
        }
        return out;
    }

    private static String str(JSONObject o, String key) {
        if (o == null) {
            return "";
        }
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) {
            return "";
        }
        String t = v instanceof String ? (String) v : String.valueOf(v);
        return "null".equals(t) ? "" : t.trim();
    }

    private static String strAt(JSONArray a, int i) {
        if (a == null) {
            return "";
        }
        Object v = a.opt(i);
        if (v == null || v == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(v);
    }

    private static String localized(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            String t = ((String) value).trim();
            return "null".equals(t) ? "" : t;
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String current = lang();
            String[] keys = {current, current.contains("-") ? current.substring(0, current.indexOf('-')) : current, "en-US"};
            for (String key : keys) {
                String v = str(o, key);
                if (!v.isEmpty()) {
                    return v;
                }
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String v = str(o, it.next());
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }

    private static AmoAddon parse(JSONObject o) {
        if (o == null) {
            return null;
        }
        String guid = str(o, "guid");
        JSONObject version = o.optJSONObject("current_version");
        String slug = str(o, "slug");
        JSONObject file = version == null ? null : version.optJSONObject("file");
        String download = file == null ? "" : str(file, "url");
        if (guid.isEmpty() || download.isEmpty()) {
            return null;
        }
        String author = "";
        JSONArray authors = o.optJSONArray("authors");
        if (authors != null && authors.length() > 0) {
            JSONObject a = authors.optJSONObject(0);
            if (a != null) {
                author = str(a, "name");
            }
        }
        double rating = 0;
        int count = 0;
        JSONObject ratings = o.optJSONObject("ratings");
        if (ratings != null) {
            rating = ratings.optDouble("average", 0);
            count = ratings.optInt("count", 0);
        }
        boolean recommended = false;
        Object promoted = o.opt("promoted");
        if (promoted instanceof JSONObject) {
            recommended = "recommended".equals(str((JSONObject) promoted, "category"));
        } else if (promoted instanceof JSONArray) {
            JSONArray list = (JSONArray) promoted;
            for (int i = 0; i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p != null && "recommended".equals(str(p, "category"))) {
                    recommended = true;
                }
            }
        }
        List<String> perms = new ArrayList<>();
        if (file != null) {
            JSONArray pa = file.optJSONArray("permissions");
            if (pa != null) {
                for (int i = 0; i < pa.length(); i++) {
                    String pv = strAt(pa, i);
                    if (!pv.isEmpty()) {
                        perms.add(pv);
                    }
                }
            }
            JSONArray ha = file.optJSONArray("host_permissions");
            if (ha != null) {
                for (int i = 0; i < ha.length(); i++) {
                    String hv = strAt(ha, i);
                    if (!hv.isEmpty()) {
                        perms.add(hv);
                    }
                }
            }
        }
        String title = localized(o.opt("name"));
        if (title.isEmpty()) {
            title = slug.isEmpty() ? guid : slug;
        }
        return new AmoAddon(guid, title, localized(o.opt("summary")), str(o, "icon_url"),
                download, str(o, "url"), author, rating, count, o.optLong("average_daily_users", 0),
                recommended, perms.toArray(new String[0]));
    }

    private static byte[] download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", "application/json, image/*;q=0.8, */*;q=0.5");
            int code = c.getResponseCode();
            if (code != 200) {
                throw new java.io.IOException("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) {
                        throw new java.io.IOException("too large");
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static boolean allowedIconHost(URL url) {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
            return false;
        }
        String host = url.getHost().toLowerCase(Locale.US);
        return host.equals("addons.mozilla.org") || host.equals("addons.cdn.mozilla.net")
                || host.equals("static.addons.mozilla.net");
    }

    private static byte[] downloadIcon(String address) throws Exception {
        URL current = new URL(address);
        for (int redirect = 0; redirect <= MAX_ICON_REDIRECTS; redirect++) {
            if (!allowedIconHost(current)) {
                throw new java.io.IOException("icon host not allowed");
            }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) current.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Accept", "image/*");
                int code = c.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null || redirect == MAX_ICON_REDIRECTS) {
                        throw new java.io.IOException("too many redirects");
                    }
                    current = new URL(current, location);
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK || c.getContentLengthLong() > MAX_ICON_BYTES) {
                    throw new java.io.IOException("icon response rejected");
                }
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_ICON_BYTES) {
                            throw new java.io.IOException("icon too large");
                        }
                        out.write(buffer, 0, read);
                    }
                    return out.toByteArray();
                }
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }
        throw new java.io.IOException("too many redirects");
    }

    private static Bitmap decodeIcon(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_ICON_BYTES) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768
                || (long) width * height > 100_000_000L) {
            return null;
        }
        int sample = 1;
        while (width / sample > MAX_ICON_SIDE || height / sample > MAX_ICON_SIDE) {
            sample <<= 1;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    public static Bitmap cachedIcon(String url) {
        return url == null ? null : ICONS.get(url);
    }

    public static void icon(final String url, final Callback<Bitmap> callback) {
        if (url == null || url.isEmpty() || url.length() > 4096) {
            callback.onResult(null, "no icon");
            return;
        }
        try {
            if (!allowedIconHost(new URL(url))) {
                callback.onResult(null, "no icon");
                return;
            }
        } catch (Exception e) {
            callback.onResult(null, "no icon");
            return;
        }
        Bitmap cached = ICONS.get(url);
        if (cached != null && !cached.isRecycled()) {
            callback.onResult(cached, null);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] data = downloadIcon(url);
                bitmap = decodeIcon(data);
                if (bitmap != null) {
                    ICONS.put(url, bitmap);
                }
            } catch (Exception ignored) {
            }
            final Bitmap out = bitmap;
            MAIN.post(() -> callback.onResult(out, out == null ? "failed" : null));
        });
    }
}
