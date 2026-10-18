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
import java.util.concurrent.Executors;

public final class AmoClient {

    public interface Callback<T> {
        void onResult(T value, String error);
    }

    private static final String SEARCH = "https://addons.mozilla.org/api/v5/addons/search/";
    private static final String USER_AGENT = "Mozilla/5.0 (Android 10; Mobile; rv:157.0) Gecko/157.0 Firefox/157.0";
    private static final int TIMEOUT_MS = 15000;
    private static final long MAX_BYTES = 8L * 1024L * 1024L;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "coara-amo");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(96);

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

    private static String localized(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String current = lang();
            String[] keys = {current, current.contains("-") ? current.substring(0, current.indexOf('-')) : current, "en-US"};
            for (String key : keys) {
                String v = o.optString(key, "");
                if (!v.isEmpty()) {
                    return v;
                }
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String v = o.optString(it.next(), "");
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
        String guid = o.optString("guid", "");
        JSONObject version = o.optJSONObject("current_version");
        JSONObject file = version == null ? null : version.optJSONObject("file");
        String download = file == null ? "" : file.optString("url", "");
        if (guid.isEmpty() || download.isEmpty()) {
            return null;
        }
        String author = "";
        JSONArray authors = o.optJSONArray("authors");
        if (authors != null && authors.length() > 0) {
            JSONObject a = authors.optJSONObject(0);
            if (a != null) {
                author = a.optString("name", "");
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
            recommended = "recommended".equals(((JSONObject) promoted).optString("category", ""));
        } else if (promoted instanceof JSONArray) {
            JSONArray list = (JSONArray) promoted;
            for (int i = 0; i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p != null && "recommended".equals(p.optString("category", ""))) {
                    recommended = true;
                }
            }
        }
        List<String> perms = new ArrayList<>();
        if (file != null) {
            JSONArray pa = file.optJSONArray("permissions");
            if (pa != null) {
                for (int i = 0; i < pa.length(); i++) {
                    perms.add(pa.optString(i));
                }
            }
            JSONArray ha = file.optJSONArray("host_permissions");
            if (ha != null) {
                for (int i = 0; i < ha.length(); i++) {
                    perms.add(ha.optString(i));
                }
            }
        }
        return new AmoAddon(guid, localized(o.opt("name")), localized(o.opt("summary")), o.optString("icon_url", ""),
                download, o.optString("url", ""), author, rating, count, o.optLong("average_daily_users", 0),
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

    public static Bitmap cachedIcon(String url) {
        return url == null ? null : ICONS.get(url);
    }

    public static void icon(final String url, final Callback<Bitmap> callback) {
        if (url == null || url.isEmpty() || !url.startsWith("https://")) {
            callback.onResult(null, "no icon");
            return;
        }
        Bitmap cached = ICONS.get(url);
        if (cached != null) {
            callback.onResult(cached, null);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] data = download(url);
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
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
