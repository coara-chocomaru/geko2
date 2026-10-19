package com.coara.browser2.gecko;

import com.coara.browser2.util.BrowserConstants;

public final class LocalPages {
    private LocalPages() {
    }

    public static boolean isLogical(String url) {
        return url != null && url.startsWith(BrowserConstants.APP_ASSET_BASE_URL);
    }

    public static String pageName(String logicalUrl) {
        if (!isLogical(logicalUrl)) {
            return null;
        }
        String rest = logicalUrl.substring(BrowserConstants.APP_ASSET_BASE_URL.length());
        int cut = rest.length();
        int q = rest.indexOf('?');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        int h = rest.indexOf('#');
        if (h >= 0) {
            cut = Math.min(cut, h);
        }
        return rest.substring(0, cut);
    }

    public static String suffix(String logicalUrl) {
        if (!isLogical(logicalUrl)) {
            return "";
        }
        String rest = logicalUrl.substring(BrowserConstants.APP_ASSET_BASE_URL.length());
        int cut = rest.length();
        int q = rest.indexOf('?');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        int h = rest.indexOf('#');
        if (h >= 0) {
            cut = Math.min(cut, h);
        }
        return rest.substring(cut);
    }

    public static String toActual(String logicalUrl) {
        String base = BrowserEngine.get().getExtensionBaseUrl();
        String name = pageName(logicalUrl);
        if (base == null || name == null || name.isEmpty()) {
            return null;
        }
        return base + BrowserEngine.PAGES_DIR + name + suffix(logicalUrl);
    }

    public static String toLogical(String actualUrl) {
        if (actualUrl == null) {
            return null;
        }
        String failed = ErrorPages.failedUrlFor(actualUrl);
        if (failed != null) {
            return failed;
        }
        BrowserEngine engine = BrowserEngine.get();
        String[] bases = {engine.getKnownExtensionBaseUrl(), engine.getLegacyExtensionBaseUrl()};
        for (String base : bases) {
            if (base == null) {
                continue;
            }
            String prefix = base + BrowserEngine.PAGES_DIR;
            if (actualUrl.startsWith(prefix)) {
                return BrowserConstants.APP_ASSET_BASE_URL + actualUrl.substring(prefix.length());
            }
        }
        return actualUrl;
    }
}
