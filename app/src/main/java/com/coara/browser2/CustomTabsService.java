package com.coara.browser2;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.util.Patterns;

import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSessionToken;

import com.coara.browser2.gecko.BrowserEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CustomTabsService extends androidx.browser.customtabs.CustomTabsService {
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_RELATIONSHIP_CACHE = 256;
    private static final long RELATIONSHIP_CACHE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long PRECONNECT_THROTTLE_MILLIS = 2500L;
    private static final int MAX_ASSET_LINKS_BYTES = 128 * 1024;
    private static final int MAX_HINTS_PER_CALL = 3;
    private static final String RELATION_USE_AS_ORIGIN = "delegate_permission/common.use_as_origin";
    private static final String RELATION_HANDLE_ALL_URLS = "delegate_permission/handle_all_urls";

    private static final Map<CustomTabsSessionToken, String> VERIFIED_POST_MESSAGE_ORIGINS = new ConcurrentHashMap<>();
    private final Set<CustomTabsSessionToken> sessions = ConcurrentHashMap.newKeySet();
    private final Map<CustomTabsSessionToken, Long> preconnectTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> recentOrigins = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor ioExecutor = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(runnable, "CoaraCustomTabsIO");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final LinkedHashMap<String, RelationshipResult> relationshipCache = new LinkedHashMap<String, RelationshipResult>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RelationshipResult> eldest) {
            return size() > MAX_RELATIONSHIP_CACHE;
        }
    };
    private volatile boolean warmedUp;

    @Override
    protected boolean warmup(long flags) {
        try {
            BrowserEngine engine = BrowserEngine.get();
            if (engine.getRuntime() == null || engine.getExecutor() == null) {
                return false;
            }
            warmedUp = true;
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    protected boolean newSession(CustomTabsSessionToken sessionToken) {
        if (sessionToken == null || sessionToken.getCallback() == null) {
            return false;
        }
        synchronized (sessions) {
            if (sessions.size() >= MAX_SESSIONS && !sessions.contains(sessionToken)) {
                return false;
            }
            return sessions.add(sessionToken);
        }
    }

    @Override
    protected boolean mayLaunchUrl(CustomTabsSessionToken sessionToken, Uri url, Bundle extras,
                                   java.util.List<Bundle> otherLikelyBundles) {
        if (!isKnownSession(sessionToken) || !warmedUp) {
            return false;
        }
        ArrayList<Uri> hints = new ArrayList<>(MAX_HINTS_PER_CALL);
        if (isSafePreconnectUri(url)) {
            hints.add(url);
        }
        if (otherLikelyBundles != null) {
            for (Bundle candidate : otherLikelyBundles) {
                if (hints.size() >= MAX_HINTS_PER_CALL) {
                    break;
                }
                if (candidate == null) {
                    continue;
                }
                try {
                    candidate.setClassLoader(Uri.class.getClassLoader());
                    Uri candidateUri = candidate.getParcelable(KEY_URL);
                    if (isSafePreconnectUri(candidateUri) && !containsOrigin(hints, candidateUri)) {
                        hints.add(candidateUri);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (hints.isEmpty()) {
            return false;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        Long lastSessionPreconnect = preconnectTimes.get(sessionToken);
        if (lastSessionPreconnect != null && now - lastSessionPreconnect < 100L) {
            return true;
        }
        preconnectTimes.put(sessionToken, now);
        boolean scheduled = false;
        for (Uri hint : hints) {
            String origin = normalizedOrigin(hint);
            if (origin == null) {
                continue;
            }
            Long last = recentOrigins.get(origin);
            if (last != null && now - last < PRECONNECT_THROTTLE_MILLIS) {
                scheduled = true;
                continue;
            }
            recentOrigins.put(origin, now);
            try {
                ioExecutor.execute(() -> {
                    try {
                        BrowserEngine engine = BrowserEngine.get();
                        Uri originUri = Uri.parse(origin);
                        if (engine.getExecutor() != null && hasOnlyPublicAddresses(originUri.getHost())) {
                            engine.getExecutor().speculativeConnect(origin);
                        }
                    } catch (RuntimeException ignored) {
                    } finally {
                        trimRecentOrigins(android.os.SystemClock.elapsedRealtime());
                    }
                });
                scheduled = true;
            } catch (RuntimeException rejected) {
                recentOrigins.remove(origin, now);
            }
        }
        trimRecentOrigins(now);
        return scheduled;
    }

    @Override
    protected Bundle extraCommand(String commandName, Bundle args) {
        return null;
    }

    @Override
    protected boolean updateVisuals(CustomTabsSessionToken sessionToken, Bundle bundle) {
        if (!isKnownSession(sessionToken) || bundle == null) {
            return false;
        }
        Bundle safe = sanitizeVisualsBundle(bundle);
        return CustomTabActivity.updateVisualsForSession(sessionToken, safe);
    }

    @Override
    protected boolean requestPostMessageChannel(CustomTabsSessionToken sessionToken, Uri postMessageOrigin) {
        return requestPostMessageChannelInternal(sessionToken, postMessageOrigin, null);
    }

    @Override
    protected boolean requestPostMessageChannel(CustomTabsSessionToken sessionToken, Uri postMessageOrigin,
                                                Uri postMessageTargetOrigin, Bundle extras) {
        return requestPostMessageChannelInternal(sessionToken, postMessageOrigin, postMessageTargetOrigin);
    }

    @Override
    protected int postMessage(CustomTabsSessionToken sessionToken, String message, Bundle extras) {
        if (!isKnownSession(sessionToken) || message == null || message.length() > 16 * 1024) {
            return RESULT_FAILURE_DISALLOWED;
        }
        return CustomTabActivity.postMessageForSession(sessionToken, message)
                ? RESULT_SUCCESS : RESULT_FAILURE_DISALLOWED;
    }

    static String getVerifiedPostMessageOrigin(CustomTabsSessionToken token) {
        return token == null ? null : VERIFIED_POST_MESSAGE_ORIGINS.get(token);
    }

    static void clearVerifiedPostMessageOrigin(CustomTabsSessionToken token) {
        if (token != null) {
            VERIFIED_POST_MESSAGE_ORIGINS.remove(token);
        }
    }

    private boolean requestPostMessageChannelInternal(CustomTabsSessionToken sessionToken, Uri postMessageOrigin,
                                                      Uri postMessageTargetOrigin) {
        if (!isKnownSession(sessionToken) || !warmedUp || postMessageOrigin == null) {
            return false;
        }
        String origin = normalizedRelationshipOrigin(postMessageOrigin);
        if (origin == null) {
            return false;
        }
        if (postMessageTargetOrigin != null) {
            String target = normalizedRelationshipOrigin(postMessageTargetOrigin);
            if (target == null || !origin.equals(target)) {
                return false;
            }
        }
        String[] callerPackages = getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (callerPackages == null || callerPackages.length != 1 || callerPackages[0] == null) {
            return false;
        }
        String callerPackage = callerPackages[0];
        ArrayList<String> fingerprints = getSigningFingerprints(callerPackage);
        if (fingerprints.isEmpty()) {
            return false;
        }
        String existingPostMessageOrigin = VERIFIED_POST_MESSAGE_ORIGINS.get(sessionToken);
        if (existingPostMessageOrigin != null && !existingPostMessageOrigin.equals(origin)) {
            VERIFIED_POST_MESSAGE_ORIGINS.remove(sessionToken);
            CustomTabActivity.clearPostMessageChannelForSession(sessionToken);
        }
        String cacheKey = relationshipCacheKey(callerPackage, RELATION_USE_AS_ORIGIN_VALUE, origin, fingerprints);
        RelationshipResult cached = getCachedRelationship(cacheKey);
        if (cached != null) {
            if (!cached.valid) {
                return false;
            }
            VERIFIED_POST_MESSAGE_ORIGINS.put(sessionToken, origin);
            CustomTabActivity.onPostMessageOriginVerified(sessionToken, origin);
            return true;
        }
        VERIFIED_POST_MESSAGE_ORIGINS.remove(sessionToken);
        CustomTabActivity.clearPostMessageChannelForSession(sessionToken);
        try {
            ioExecutor.execute(() -> {
                boolean result = false;
                try {
                    result = verifyAssetLinks(origin, callerPackage, fingerprints, RELATION_USE_AS_ORIGIN_VALUE);
                } catch (Exception ignored) {
                }
                putCachedRelationship(cacheKey, result, android.os.SystemClock.elapsedRealtime());
                if (result && isKnownSession(sessionToken)) {
                    VERIFIED_POST_MESSAGE_ORIGINS.put(sessionToken, origin);
                    CustomTabActivity.onPostMessageOriginVerified(sessionToken, origin);
                } else if (!result) {
                    deliverRelationshipResult(sessionToken, RELATION_USE_AS_ORIGIN_VALUE, Uri.parse(origin), false);
                }
            });
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    @Override
    protected boolean validateRelationship(CustomTabsSessionToken sessionToken, int relation, Uri origin,
                                           Bundle extras) {
        if (!isKnownSession(sessionToken) || !warmedUp || origin == null
                || (relation != RELATION_USE_AS_ORIGIN_VALUE && relation != RELATION_HANDLE_ALL_URLS_VALUE)) {
            return false;
        }
        String normalizedOrigin = normalizedRelationshipOrigin(origin);
        if (normalizedOrigin == null) {
            return false;
        }
        int callingUid = Binder.getCallingUid();
        String[] callerPackages = getPackageManager().getPackagesForUid(callingUid);
        if (callerPackages == null || callerPackages.length != 1 || callerPackages[0] == null) {
            return false;
        }
        String callerPackage = callerPackages[0];
        ArrayList<String> fingerprints = getSigningFingerprints(callerPackage);
        if (fingerprints.isEmpty()) {
            return false;
        }
        String cacheKey = relationshipCacheKey(callerPackage, relation, normalizedOrigin, fingerprints);
        RelationshipResult cached = getCachedRelationship(cacheKey);
        if (cached != null) {
            deliverRelationshipResult(sessionToken, relation, origin, cached.valid);
            return true;
        }
        try {
            ioExecutor.execute(() -> {
                boolean result = false;
                try {
                    result = verifyAssetLinks(normalizedOrigin, callerPackage, fingerprints, relation);
                } catch (Exception ignored) {
                }
                putCachedRelationship(cacheKey, result, android.os.SystemClock.elapsedRealtime());
                deliverRelationshipResult(sessionToken, relation, origin, result);
            });
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    @Override
    protected boolean cleanUpSession(CustomTabsSessionToken sessionToken) {
        if (sessionToken == null) {
            return false;
        }
        boolean removed = sessions.remove(sessionToken);
        preconnectTimes.remove(sessionToken);
        VERIFIED_POST_MESSAGE_ORIGINS.remove(sessionToken);
        CustomTabActivity.clearPostMessageChannelForSession(sessionToken);
        return removed;
    }

    static Bundle sanitizeVisualsBundle(Bundle input) {
        Bundle result = new Bundle();
        if (input == null) {
            return result;
        }
        try {
            input.setClassLoader(CustomTabsIntent.class.getClassLoader());
            for (String key : new String[]{
                    CustomTabsIntent.EXTRA_TOOLBAR_COLOR,
                    CustomTabsIntent.EXTRA_SECONDARY_TOOLBAR_COLOR,
                    CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR,
                    CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR}) {
                if (input.containsKey(key)) {
                    result.putInt(key, input.getInt(key));
                }
            }
            if (input.containsKey(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON)) {
                result.putBoolean(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON,
                        input.getBoolean(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON, false));
            }
            Bundle nested = input.getBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE);
            Bundle button = sanitizeActionButton(nested != null ? nested : input);
            if (!button.isEmpty()) {
                result.putBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, button);
            } else if (input.containsKey(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE)) {
                result.putBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, new Bundle());
            }
        } catch (RuntimeException ignored) {
            result.clear();
        }
        return result;
    }

    static Bundle sanitizeActionButton(Bundle input) {
        Bundle result = new Bundle();
        if (input == null) {
            return result;
        }
        try {
            input.setClassLoader(CustomTabsIntent.class.getClassLoader());
            android.graphics.Bitmap icon = input.getParcelable(CustomTabsIntent.KEY_ICON);
            android.app.PendingIntent pendingIntent = input.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT);
            String description = input.getString(CustomTabsIntent.KEY_DESCRIPTION);
            if (icon != null && !icon.isRecycled() && icon.getWidth() <= 256 && icon.getHeight() <= 256
                    && pendingIntent != null) {
                result.putParcelable(CustomTabsIntent.KEY_ICON, icon);
                result.putParcelable(CustomTabsIntent.KEY_PENDING_INTENT, pendingIntent);
                if (description != null) {
                    result.putString(CustomTabsIntent.KEY_DESCRIPTION, description.substring(0, Math.min(128, description.length())));
                }
            }
        } catch (RuntimeException ignored) {
            result.clear();
        }
        return result;
    }

    private boolean isKnownSession(CustomTabsSessionToken token) {
        return token != null && sessions.contains(token) && token.getCallback() != null;
    }

    private static boolean containsOrigin(ArrayList<Uri> uris, Uri candidate) {
        String origin = normalizedOrigin(candidate);
        if (origin == null) {
            return false;
        }
        for (Uri uri : uris) {
            if (origin.equals(normalizedOrigin(uri))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSafePreconnectUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getHost() == null || uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!isValidPublicHostnameShape(host)) {
            return false;
        }
        return normalizedOrigin(uri) != null;
    }

    private static String normalizedOrigin(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!isValidPublicHostnameShape(host)) {
            return null;
        }
        try {
            String ascii = java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return "https://" + ascii;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String normalizedRelationshipOrigin(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPort() != -1 && uri.getPort() != 443
                || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            return null;
        }
        return normalizedOrigin(uri);
    }

    private static boolean isValidPublicHostnameShape(String host) {
        if (host == null || host.isEmpty() || host.length() > 253 || host.endsWith(".")
                || host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".invalid")) {
            return false;
        }
        if (!host.contains(".") || Patterns.IP_ADDRESS.matcher(host).matches() || host.indexOf(':') >= 0) {
            return false;
        }
        return true;
    }

    private boolean verifyAssetLinks(String origin, String packageName, ArrayList<String> fingerprints, int relation)
            throws Exception {
        Uri originUri = Uri.parse(origin);
        if (!hasOnlyPublicAddresses(originUri.getHost())) {
            return false;
        }
        String relationName = relation == RELATION_USE_AS_ORIGIN_VALUE
                ? RELATION_USE_AS_ORIGIN : RELATION_HANDLE_ALL_URLS;
        URL url = new URL(origin + "/.well-known/assetlinks.json");
        URLConnection rawConnection = url.openConnection(java.net.Proxy.NO_PROXY);
        if (!(rawConnection instanceof javax.net.ssl.HttpsURLConnection)) {
            return false;
        }
        javax.net.ssl.HttpsURLConnection connection = (javax.net.ssl.HttpsURLConnection) rawConnection;
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "CoaraGecko/CustomTabs");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                return false;
            }
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_ASSET_LINKS_BYTES) {
                return false;
            }
            byte[] data;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > MAX_ASSET_LINKS_BYTES) {
                        return false;
                    }
                    output.write(buffer, 0, read);
                }
                data = output.toByteArray();
            }
            JSONArray statements = new JSONArray(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < statements.length(); i++) {
                JSONObject statement = statements.optJSONObject(i);
                if (statement == null || !containsString(statement.optJSONArray("relation"), relationName)) {
                    continue;
                }
                JSONObject target = statement.optJSONObject("target");
                if (target == null || !"android_app".equals(target.optString("namespace", ""))
                        || !packageName.equals(target.optString("package_name", ""))) {
                    continue;
                }
                JSONArray assetFingerprints = target.optJSONArray("sha256_cert_fingerprints");
                if (assetFingerprints == null) {
                    continue;
                }
                for (int j = 0; j < assetFingerprints.length(); j++) {
                    String fingerprint = normalizeFingerprint(assetFingerprints.optString(j, ""));
                    if (fingerprint != null && fingerprints.contains(fingerprint)) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            connection.disconnect();
        }
    }

    private static boolean containsString(JSONArray values, String wanted) {
        if (values == null) {
            return false;
        }
        for (int i = 0; i < values.length(); i++) {
            if (wanted.equals(values.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOnlyPublicAddresses(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    return false;
                }
                byte[] bytes = address.getAddress();
                if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
                    return false;
                }
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private ArrayList<String> getSigningFingerprints(String packageName) {
        ArrayList<String> result = new ArrayList<>();
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 28) {
                info = getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo != null) {
                    Signature[] signers = info.signingInfo.hasMultipleSigners()
                            ? info.signingInfo.getApkContentsSigners()
                            : info.signingInfo.getSigningCertificateHistory();
                    addFingerprints(result, signers);
                }
            } else {
                info = getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                addFingerprints(result, info.signatures);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void addFingerprints(ArrayList<String> target, Signature[] signatures) {
        if (signatures == null) {
            return;
        }
        for (Signature signature : signatures) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                StringBuilder value = new StringBuilder(digest.length * 3 - 1);
                for (int i = 0; i < digest.length; i++) {
                    if (i > 0) {
                        value.append(':');
                    }
                    value.append(String.format(Locale.ROOT, "%02X", digest[i] & 0xff));
                }
                if (!target.contains(value.toString())) {
                    target.add(value.toString());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        String value = fingerprint.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        if (value.length() != 64 || !value.matches("[0-9A-F]{64}")) {
            return null;
        }
        StringBuilder formatted = new StringBuilder(95);
        for (int i = 0; i < value.length(); i += 2) {
            if (formatted.length() > 0) {
                formatted.append(':');
            }
            formatted.append(value, i, i + 2);
        }
        return formatted.toString();
    }

    private static String relationshipCacheKey(String packageName, int relation, String origin, ArrayList<String> fingerprints) {
        ArrayList<String> sorted = new ArrayList<>(fingerprints);
        Collections.sort(sorted);
        return packageName + '|' + relation + '|' + origin + '|' + android.text.TextUtils.join(",", sorted);
    }

    private RelationshipResult getCachedRelationship(String key) {
        synchronized (relationshipCache) {
            RelationshipResult result = relationshipCache.get(key);
            if (result == null) {
                return null;
            }
            if (android.os.SystemClock.elapsedRealtime() - result.timestamp > RELATIONSHIP_CACHE_MILLIS) {
                relationshipCache.remove(key);
                return null;
            }
            return result;
        }
    }

    private void putCachedRelationship(String key, boolean valid, long now) {
        synchronized (relationshipCache) {
            relationshipCache.put(key, new RelationshipResult(valid, now));
        }
    }

    private void deliverRelationshipResult(CustomTabsSessionToken token, int relation, Uri origin, boolean valid) {
        if (!isKnownSession(token)) {
            return;
        }
        try {
            token.getCallback().onRelationshipValidationResult(relation, origin, valid, null);
        } catch (RuntimeException ignored) {
        }
    }

    private void trimRecentOrigins(long now) {
        if (recentOrigins.size() <= 128) {
            recentOrigins.entrySet().removeIf(entry -> now - entry.getValue() > PRECONNECT_THROTTLE_MILLIS * 4);
            return;
        }
        recentOrigins.entrySet().removeIf(entry -> now - entry.getValue() > PRECONNECT_THROTTLE_MILLIS);
        if (recentOrigins.size() > 128) {
            recentOrigins.clear();
        }
    }

    private static final int RELATION_USE_AS_ORIGIN_VALUE = androidx.browser.customtabs.CustomTabsService.RELATION_USE_AS_ORIGIN;
    private static final int RELATION_HANDLE_ALL_URLS_VALUE = androidx.browser.customtabs.CustomTabsService.RELATION_HANDLE_ALL_URLS;

    private static final class RelationshipResult {
        final boolean valid;
        final long timestamp;

        RelationshipResult(boolean valid, long timestamp) {
            this.valid = valid;
            this.timestamp = timestamp;
        }
    }
}
