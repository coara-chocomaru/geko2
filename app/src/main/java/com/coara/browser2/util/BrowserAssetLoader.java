package com.coara.browser2.util;

public final class BrowserAssetLoader {
    private BrowserAssetLoader() {
    }

    public static String normalizeLegacyAssetUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith(BrowserConstants.LEGACY_ASSET_PREFIX)) {
            return BrowserConstants.APP_ASSET_BASE_URL + url.substring(BrowserConstants.LEGACY_ASSET_PREFIX.length());
        }
        if (url.startsWith(BrowserConstants.LEGACY_ASSET_HOST_PREFIX)) {
            return BrowserConstants.APP_ASSET_BASE_URL + url.substring(BrowserConstants.LEGACY_ASSET_HOST_PREFIX.length());
        }
        return url;
    }

    public static boolean isAppAssetUrl(String url) {
        return url != null && (url.startsWith(BrowserConstants.APP_ASSET_BASE_URL)
                || url.startsWith(BrowserConstants.LEGACY_ASSET_HOST_PREFIX));
    }
}
