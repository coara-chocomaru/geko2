package com.coara.browser2.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CookieSitePolicy {
    public static final String KEY_ALLOW_SITES = "third_party_cookie_allow_sites";
    public static final String KEY_BLOCK_SITES = "third_party_cookie_block_sites";
    public static final String KEY_BLOCK_ALL = "cookies_block_all";

    public static final int MODE_BLOCK_THIRD_PARTY = 0;
    public static final int MODE_ALLOW_THIRD_PARTY = 1;
    public static final int MODE_BLOCK_ALL = 2;

    private static final int MAX_SITES = 500;

    private static volatile boolean blockAllCache;
    private static volatile int revision;

    private CookieSitePolicy() {
    }

    public static int revision() {
        return revision;
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        try {
            SharedPreferences p = context.getApplicationContext()
                    .getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
            blockAllCache = p.getBoolean(KEY_BLOCK_ALL, false);
        } catch (Throwable ignored) {
        }
    }

    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.US);
            int end = host.length();
            while (end > 0 && host.charAt(end - 1) == '.') {
                end--;
            }
            return end == host.length() ? host : host.substring(0, end);
        } catch (Exception e) {
            return "";
        }
    }

    private static int matchingLength(Set<String> sites, String host) {
        if (sites == null || sites.isEmpty() || host.isEmpty()) {
            return -1;
        }
        String candidate = host;
        while (true) {
            if (sites.contains(candidate)) {
                return candidate.length();
            }
            int dot = candidate.indexOf('.');
            if (dot < 0 || dot == candidate.length() - 1) {
                return -1;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    public static boolean blockAll(SharedPreferences pref) {
        return pref != null && pref.getBoolean(KEY_BLOCK_ALL, false);
    }

    public static boolean isBlockAllActive() {
        return blockAllCache;
    }

    public static boolean globalAllowed(SharedPreferences pref) {
        return pref != null && pref.getBoolean(BrowserConstants.KEY_THIRD_PARTY_COOKIES, false);
    }

    public static int mode(SharedPreferences pref) {
        if (blockAll(pref)) {
            return MODE_BLOCK_ALL;
        }
        return globalAllowed(pref) ? MODE_ALLOW_THIRD_PARTY : MODE_BLOCK_THIRD_PARTY;
    }

    public static void setMode(SharedPreferences pref, int mode) {
        if (pref == null) {
            return;
        }
        pref.edit()
                .putBoolean(KEY_BLOCK_ALL, mode == MODE_BLOCK_ALL)
                .putBoolean(BrowserConstants.KEY_THIRD_PARTY_COOKIES, mode == MODE_ALLOW_THIRD_PARTY)
                .apply();
        blockAllCache = mode == MODE_BLOCK_ALL;
        revision++;
    }

    public static boolean isAllowed(SharedPreferences pref, String url) {
        if (blockAll(pref)) {
            return false;
        }
        int siteMode = override(pref, url);
        if (siteMode != 0) {
            return siteMode > 0;
        }
        return globalAllowed(pref);
    }

    public static int override(SharedPreferences pref, String url) {
        if (pref == null) {
            return 0;
        }
        String host = hostOf(url);
        if (host.isEmpty()) {
            return 0;
        }
        int allowLength = matchingLength(pref.getStringSet(KEY_ALLOW_SITES, null), host);
        int blockLength = matchingLength(pref.getStringSet(KEY_BLOCK_SITES, null), host);
        if (allowLength < 0 && blockLength < 0) {
            return 0;
        }
        if (allowLength > blockLength) {
            return 1;
        }
        return -1;
    }

    public static boolean setOverride(SharedPreferences pref, String url, int mode) {
        return setOverrideHost(pref, hostOf(url), mode);
    }

    public static boolean setOverrideHost(SharedPreferences pref, String host, int mode) {
        if (pref == null || host == null || host.isEmpty()) {
            return false;
        }
        Set<String> allow = copy(pref.getStringSet(KEY_ALLOW_SITES, null));
        Set<String> block = copy(pref.getStringSet(KEY_BLOCK_SITES, null));
        allow.remove(host);
        block.remove(host);
        if (mode > 0) {
            if (allow.size() >= MAX_SITES) {
                return false;
            }
            allow.add(host);
        } else if (mode < 0) {
            if (block.size() >= MAX_SITES) {
                return false;
            }
            block.add(host);
        }
        pref.edit().putStringSet(KEY_ALLOW_SITES, allow).putStringSet(KEY_BLOCK_SITES, block).apply();
        revision++;
        return true;
    }

    public static List<String> sites(SharedPreferences pref, boolean allowList) {
        if (pref == null) {
            return Collections.emptyList();
        }
        Set<String> source = pref.getStringSet(allowList ? KEY_ALLOW_SITES : KEY_BLOCK_SITES, null);
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(source);
        Collections.sort(out);
        return out;
    }

    public static void clearOverrides(SharedPreferences pref) {
        if (pref == null) {
            return;
        }
        pref.edit().remove(KEY_ALLOW_SITES).remove(KEY_BLOCK_SITES).apply();
        revision++;
    }

    public static String cookieFor(String url) {
        if (blockAllCache || url == null) {
            return null;
        }
        return CookieBridge.getCookieHeader(url);
    }

    private static Set<String> copy(Set<String> source) {
        return source == null ? new HashSet<String>() : new HashSet<String>(source);
    }
}
