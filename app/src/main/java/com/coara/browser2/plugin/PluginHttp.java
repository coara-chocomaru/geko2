package com.coara.browser2.plugin;

import android.util.Base64;
import com.coara.browser2.util.CookieBridge;
import com.coara.browser2.util.CookieSitePolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PluginHttp {
    static final int MAX_BODY = 8 * 1024 * 1024;
    static final int MAX_REQUEST_BODY = 2 * 1024 * 1024;
    static final int MAX_REQUEST_JSON_CHARS = 3_000_000;
    private static final int MAX_RESPONSE_HEADERS = 64 * 1024;
    private static final int MAX_REDIRECTS = 8;
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final int MAX_TIMEOUT = 120000;

    static final class Call {
        volatile boolean aborted;
        volatile HttpURLConnection conn;

        void abort() {
            aborted = true;
            HttpURLConnection c = conn;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private PluginHttp() {
    }

    private static long parseLooseIpv4(String h) {
        if (h.isEmpty()) {
            return -1L;
        }
        String[] parts = h.split("\\.", -1);
        if (parts.length > 4) {
            return -1L;
        }
        long[] v = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i];
            if (t.isEmpty()) {
                return -1L;
            }
            try {
                if (t.startsWith("0x") || t.startsWith("0X")) {
                    if (t.length() == 2) {
                        return -1L;
                    }
                    v[i] = Long.parseLong(t.substring(2), 16);
                } else if (t.length() > 1 && t.charAt(0) == '0') {
                    v[i] = Long.parseLong(t.substring(1), 8);
                } else {
                    v[i] = Long.parseLong(t, 10);
                }
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (v[i] < 0) {
                return -1L;
            }
        }
        long result;
        switch (parts.length) {
            case 1:
                if (v[0] > 0xFFFFFFFFL) {
                    return -1L;
                }
                result = v[0];
                break;
            case 2:
                if (v[0] > 0xFF || v[1] > 0xFFFFFFL) {
                    return -1L;
                }
                result = (v[0] << 24) | v[1];
                break;
            case 3:
                if (v[0] > 0xFF || v[1] > 0xFF || v[2] > 0xFFFFL) {
                    return -1L;
                }
                result = (v[0] << 24) | (v[1] << 16) | v[2];
                break;
            default:
                if (v[0] > 0xFF || v[1] > 0xFF || v[2] > 0xFF || v[3] > 0xFF) {
                    return -1L;
                }
                result = (v[0] << 24) | (v[1] << 16) | (v[2] << 8) | v[3];
                break;
        }
        return result;
    }

    private static boolean isPrivateIpv4(long ip) {
        int a = (int) ((ip >> 24) & 0xFF);
        int b = (int) ((ip >> 16) & 0xFF);
        return a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168) || (a == 100 && b >= 64 && b <= 127) || a >= 224;
    }

    static boolean isPrivateHost(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.US);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty() || h.startsWith("[") || h.indexOf(':') >= 0) {
            return true;
        }
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")
                || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.indexOf('.') < 0 && parseLooseIpv4(h) < 0) {
            return true;
        }
        long ip = parseLooseIpv4(h);
        if (ip >= 0) {
            return isPrivateIpv4(ip);
        }
        return false;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.startsWith("[") || parseLooseIpv4(host) >= 0;
    }

    private static boolean resolvesToPrivate(String host) {
        if (isIpLiteral(host)) {
            return false;
        }
        try {
            java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
            for (java.net.InetAddress a : addrs) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                        || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                    return true;
                }
                byte[] raw = a.getAddress();
                if (raw.length == 4) {
                    long ip = ((raw[0] & 0xFFL) << 24) | ((raw[1] & 0xFFL) << 16) | ((raw[2] & 0xFFL) << 8) | (raw[3] & 0xFFL);
                    if (isPrivateIpv4(ip)) {
                        return true;
                    }
                } else if (raw.length == 16 && (raw[0] & 0xFE) == 0xFC) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static boolean hostAllowed(List<String> connects, String url, String originHost) {
        String host;
        String scheme;
        try {
            URL u = new URL(url);
            host = u.getHost();
            scheme = u.getProtocol();
            if (u.getUserInfo() != null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        if (host == null || host.isEmpty()) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        boolean secure = "https".equalsIgnoreCase(scheme);
        boolean priv = isPrivateHost(host);
        if (originHost != null && !originHost.isEmpty() && sameOrigin(originHost, url)) {
            return priv || !resolvesToPrivate(host);
        }
        if (connects != null) {
            for (String c : connects) {
                String e = c.trim().toLowerCase(Locale.US);
                if (e.isEmpty()) {
                    continue;
                }
                if (e.equals("*")) {
                    if (secure && !priv && !resolvesToPrivate(host)) {
                        return true;
                    }
                    continue;
                }
                if (host.equals(e)) {
                    return priv || !resolvesToPrivate(host);
                }
                if (!priv && host.endsWith("." + e) && (secure || !isIpLiteral(host)) && !resolvesToPrivate(host)) {
                    return true;
                }
            }
        }
        return false;
    }

    static JSONObject errorPayload(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("status", 0);
            o.put("error", message == null ? "error" : message);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static JSONObject execute(JSONObject opts, List<String> connects, Call call, String trustedOrigin) {
        try {
            String url = opts.optString("url", "");
            if (url.length() > 8192) {
                return errorPayload("url too long");
            }
            String origin = trustedOrigin == null || trustedOrigin.length() > 8192 ? "" : trustedOrigin;
            String method = opts.optString("method", "GET").toUpperCase(Locale.US);
            if (!method.matches("[A-Z]{3,10}")) {
                return errorPayload("invalid method");
            }
            String lower = url.toLowerCase(Locale.US);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                return errorPayload("unsupported scheme");
            }
            boolean anonymous = opts.optBoolean("anonymous", false);
            int timeout = Math.max(1000, Math.min(MAX_TIMEOUT, opts.optInt("timeout", DEFAULT_TIMEOUT)));
            if (opts.optInt("timeout", 0) <= 0) {
                timeout = DEFAULT_TIMEOUT;
            }
            JSONObject headers = opts.optJSONObject("headers");
            byte[] body = null;
            if (opts.has("dataB64")) {
                String encoded = opts.optString("dataB64", "");
                if (encoded.length() > ((MAX_REQUEST_BODY + 2L) / 3L) * 4L + 16L) {
                    return errorPayload("request body too large");
                }
                try {
                    body = Base64.decode(encoded, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    return errorPayload("invalid request body");
                }
            } else if (opts.has("data") && !opts.isNull("data")) {
                String text = opts.optString("data", "");
                if (text.length() > MAX_REQUEST_BODY) {
                    return errorPayload("request body too large");
                }
                body = text.getBytes(StandardCharsets.UTF_8);
            }
            if (body != null && body.length > MAX_REQUEST_BODY) {
                return errorPayload("request body too large");
            }
            if (headers != null && !validHeaders(headers)) {
                return errorPayload("invalid headers");
            }
            String responseType = opts.optString("responseType", "");
            String user = opts.optString("user", "");
            String password = opts.optString("password", "");
            String userAgent = opts.optString("ua", "Mozilla/5.0 (Linux; Android 10) CoaraBrowser");
            String charsetOverride = opts.optString("charset", "");
            if (responseType.length() > 64 || user.length() > 4096 || password.length() > 4096
                    || userAgent.length() > 8192 || charsetOverride.length() > 128) {
                return errorPayload("request metadata too large");
            }

            String current = url;
            int redirects = 0;
            while (true) {
                if (call.aborted) {
                    return abortedPayload();
                }
                if (!hostAllowed(connects, current, origin)) {
                    return errorPayload("blocked host (not in @connect): " + hostOf(current));
                }
                HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
                call.conn = c;
                try {
                    c.setConnectTimeout(Math.min(timeout, 20000));
                    c.setReadTimeout(timeout);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestMethod(method);
                    boolean hasCookie = false;
                    boolean hasUa = false;
                    if (headers != null) {
                        Iterator<String> it = headers.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            String kl = k.toLowerCase(Locale.US);
                            boolean sameOriginalOrigin = sameOrigin(url, current);
                            if (kl.equals("host") || kl.equals("content-length") || kl.equals("connection")
                                    || kl.equals("transfer-encoding") || kl.equals("proxy-authorization")
                                    || (!sameOriginalOrigin && (kl.equals("authorization") || kl.equals("cookie")))) {
                                continue;
                            }
                            if (kl.equals("cookie")) {
                                hasCookie = true;
                            }
                            if (kl.equals("user-agent")) {
                                hasUa = true;
                            }
                            c.setRequestProperty(k, headers.optString(k, ""));
                        }
                    }
                    if (!hasUa) {
                        c.setRequestProperty("User-Agent", userAgent);
                    }
                    if (!anonymous && !hasCookie) {
                        String cookie = CookieSitePolicy.cookieFor(current);
                        if (cookie != null && !cookie.isEmpty() && cookie.length() <= 65536) {
                            c.setRequestProperty("Cookie", cookie);
                        }
                    }
                    if (!user.isEmpty() && sameOrigin(url, current)) {
                        String cred = Base64.encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                        c.setRequestProperty("Authorization", "Basic " + cred);
                    }
                    if (body != null && body.length > 0 && !method.equals("GET") && !method.equals("HEAD")) {
                        c.setDoOutput(true);
                        c.setFixedLengthStreamingMode(body.length);
                        try (java.io.OutputStream output = c.getOutputStream()) {
                            output.write(body);
                            output.flush();
                        }
                    }
                    int code = c.getResponseCode();
                    if (!anonymous && !CookieSitePolicy.isBlockAllActive()) {
                        Map<String, List<String>> hf = c.getHeaderFields();
                        List<String> sc = hf == null ? null : hf.get("Set-Cookie");
                        if (sc != null) {
                            CookieBridge.storeSetCookies(current, sc);
                        }
                    }
                    if (code >= 300 && code < 400 && code != 304 && code != 300) {
                        String loc = c.getHeaderField("Location");
                        if (loc != null && !loc.isEmpty() && redirects < MAX_REDIRECTS && !opts.optBoolean("noRedirect", false)) {
                            redirects++;
                            String next = new URL(new URL(current), loc).toString();
                            if (current.regionMatches(true, 0, "https://", 0, 8) && !next.regionMatches(true, 0, "https://", 0, 8)) {
                                return errorPayload("blocked https to http redirect: " + hostOf(next));
                            }
                            current = next;
                            if (code == 301 || code == 302 || code == 303) {
                                if (!method.equals("HEAD")) {
                                    method = "GET";
                                }
                                body = null;
                            }
                            continue;
                        }
                    }
                    long contentLength = c.getContentLengthLong();
                    if (contentLength > MAX_BODY) {
                        return errorPayload("response too large");
                    }
                    String responseHeaders = headerString(c);
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    byte[] data = new byte[0];
                    if (in != null && !method.equals("HEAD")) {
                        try (InputStream is = in) {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n;
                            long total = 0L;
                            while ((n = is.read(buf)) > 0) {
                                if (call.aborted) {
                                    return abortedPayload();
                                }
                                total += n;
                                if (total > MAX_BODY) {
                                    return errorPayload("response too large");
                                }
                                bos.write(buf, 0, n);
                            }
                            data = bos.toByteArray();
                        }
                    }
                    JSONObject out = new JSONObject();
                    out.put("status", code);
                    out.put("statusText", c.getResponseMessage() == null ? "" : c.getResponseMessage());
                    out.put("finalUrl", current);
                    out.put("headers", responseHeaders);
                    String ctype = c.getContentType();
                    if (responseType.equals("arraybuffer") || responseType.equals("blob") || opts.optBoolean("binary", false)) {
                        out.put("b64", Base64.encodeToString(data, Base64.NO_WRAP));
                        out.put("mime", ctype == null ? "" : ctype);
                    } else {
                        out.put("text", new String(data, charsetOf(ctype, opts.optString("charset", ""))));
                    }
                    return out;
                } finally {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            if (call.aborted) {
                return abortedPayload();
            }
            return errorPayload(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static boolean sameOrigin(String first, String second) {
        try {
            URL a = new URL(first);
            URL b = new URL(second);
            int ap = a.getPort() < 0 ? a.getDefaultPort() : a.getPort();
            int bp = b.getPort() < 0 ? b.getDefaultPort() : b.getPort();
            return a.getProtocol().equalsIgnoreCase(b.getProtocol())
                    && a.getHost().equalsIgnoreCase(b.getHost()) && ap == bp
                    && a.getUserInfo() == null && b.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validHeaders(JSONObject headers) {
        if (headers.length() > 64) {
            return false;
        }
        Iterator<String> it = headers.keys();
        int total = 0;
        while (it.hasNext()) {
            String key = it.next();
            String value = headers.optString(key, "");
            if (key == null || key.isEmpty() || key.length() > 256 || value.length() > 8192
                    || key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                    || !key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                return false;
            }
            total += key.length() + value.length();
            if (total > 32768) {
                return false;
            }
        }
        return true;
    }

    private static JSONObject abortedPayload() {
        JSONObject o = errorPayload("aborted");
        try {
            o.put("aborted", true);
        } catch (JSONException ignored) {
        }
        return o;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private static String headerString(HttpURLConnection c) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> hf = c.getHeaderFields();
        if (hf == null) {
            return "";
        }
        for (Map.Entry<String, List<String>> e : hf.entrySet()) {
            if (e.getKey() == null || e.getKey().equalsIgnoreCase("Set-Cookie")
                    || e.getKey().equalsIgnoreCase("Set-Cookie2")) {
                continue;
            }
            for (String v : e.getValue()) {
                if (sb.length() >= MAX_RESPONSE_HEADERS) {
                    return sb.toString();
                }
                int remaining = MAX_RESPONSE_HEADERS - sb.length();
                String prefix = e.getKey() + ": ";
                int prefixLength = Math.min(prefix.length(), remaining);
                sb.append(prefix, 0, prefixLength);
                remaining -= prefixLength;
                String value = v == null ? "" : v;
                int valueLength = Math.min(value.length(), remaining);
                sb.append(value, 0, valueLength);
                remaining -= valueLength;
                if (remaining > 0) {
                    sb.append('\r');
                }
                if (remaining > 1) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static Charset charsetOf(String contentType, String override) {
        String name = override;
        if ((name == null || name.isEmpty()) && contentType != null) {
            int i = contentType.toLowerCase(Locale.US).indexOf("charset=");
            if (i >= 0) {
                name = contentType.substring(i + 8).split(";")[0].trim().replace("\"", "");
            }
        }
        if (name != null && !name.isEmpty()) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }
}
