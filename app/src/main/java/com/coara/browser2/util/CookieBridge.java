package com.coara.browser2.util;

import android.os.Handler;
import android.os.Looper;

import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.PageAgent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class CookieBridge {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long TIMEOUT_MS = 4000L;

    private CookieBridge() {
    }

    public static String getCookieHeader(String url) {
        if (url == null || url.isEmpty() || Looper.myLooper() == Looper.getMainLooper()) {
            return null;
        }
        final AtomicReference<JSONArray> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final JSONObject filter = PageAgent.json("url", url);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    BrowserEngine.get().getAgent().cookiesGet(filter, new PageAgent.CookieCallback() {
                        @Override
                        public void onResult(boolean ok, JSONArray list, int count) {
                            result.set(ok ? list : null);
                            latch.countDown();
                        }
                    });
                } catch (Exception e) {
                    latch.countDown();
                }
            }
        });
        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        JSONArray list = result.get();
        if (list == null || list.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            if (c == null) {
                continue;
            }
            String name = c.optString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(c.optString("value", ""));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static void storeSetCookies(String url, List<String> setCookieHeaders) {
        if (url == null || setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }
        final JSONArray list = new JSONArray();
        String host = android.net.Uri.parse(url).getHost();
        boolean https = url.startsWith("https://");
        if (host == null) {
            return;
        }
        for (String header : setCookieHeaders) {
            JSONObject c = parseSetCookie(header, host, https);
            if (c != null) {
                list.put(c);
            }
        }
        if (list.length() == 0) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    BrowserEngine.get().getAgent().cookiesSet(list, new PageAgent.CookieCallback() {
                        @Override
                        public void onResult(boolean ok, JSONArray l, int count) {
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static JSONObject parseSetCookie(String header, String host, boolean https) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String[] parts = header.split(";");
        int eq = parts[0].indexOf('=');
        if (eq <= 0) {
            return null;
        }
        try {
            JSONObject c = new JSONObject();
            c.put("name", parts[0].substring(0, eq).trim());
            c.put("value", parts[0].substring(eq + 1).trim());
            String domain = host;
            boolean hostOnly = true;
            String path = "/";
            boolean secure = false;
            boolean httpOnly = false;
            long expires = 0L;
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim();
                String lower = p.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("domain=")) {
                    domain = p.substring(7).trim();
                    hostOnly = false;
                } else if (lower.startsWith("path=")) {
                    path = p.substring(5).trim();
                } else if (lower.equals("secure")) {
                    secure = true;
                } else if (lower.equals("httponly")) {
                    httpOnly = true;
                } else if (lower.startsWith("max-age=")) {
                    try {
                        expires = System.currentTimeMillis() / 1000L + Long.parseLong(p.substring(8).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            c.put("domain", domain);
            c.put("hostOnly", hostOnly);
            c.put("path", path.isEmpty() ? "/" : path);
            c.put("secure", secure);
            c.put("httpOnly", httpOnly);
            c.put("session", expires == 0L);
            if (expires != 0L) {
                c.put("expirationDate", expires);
            }
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
