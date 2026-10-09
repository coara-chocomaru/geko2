package com.coara.browser2.gecko;

public final class BrowserSettings {
    public static final int CACHE_DEFAULT = 0;
    public static final int CACHE_BYPASS = 1;

    private final BrowserView owner;
    private boolean javaScriptEnabled = true;
    private boolean loadsImagesAutomatically = true;
    private boolean zoomEnabled = false;
    private boolean desktopMode = false;
    private String userAgentOverride;
    private int cacheMode = CACHE_DEFAULT;
    private int textZoom = 100;
    private String standardFontFamily;
    private int backgroundColor = 0xFFFFFFFF;

    BrowserSettings(BrowserView owner) {
        this.owner = owner;
    }

    public boolean getJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    public void setJavaScriptEnabled(boolean enabled) {
        if (javaScriptEnabled == enabled) {
            return;
        }
        javaScriptEnabled = enabled;
        owner.applySessionSettings();
    }

    public boolean getLoadsImagesAutomatically() {
        return loadsImagesAutomatically;
    }

    public void setLoadsImagesAutomatically(boolean enabled) {
        loadsImagesAutomatically = enabled;
        BrowserEngine.get().setImagesEnabled(enabled);
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    public void setZoomEnabled(boolean enabled) {
        zoomEnabled = enabled;
        BrowserEngine.get().setZoomEnabled(enabled);
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    public void setDesktopMode(boolean enabled) {
        if (desktopMode == enabled) {
            return;
        }
        desktopMode = enabled;
        owner.applySessionSettings();
    }

    public String getUserAgentString() {
        return userAgentOverride != null ? userAgentOverride : BrowserEngine.get().getDefaultUserAgent();
    }

    public String getUserAgentOverride() {
        return userAgentOverride;
    }

    public void setUserAgentString(String userAgent) {
        String normalized = (userAgent == null || userAgent.isEmpty()) ? null : userAgent;
        if ((normalized == null && userAgentOverride == null)
                || (normalized != null && normalized.equals(userAgentOverride))) {
            return;
        }
        userAgentOverride = normalized;
        owner.applySessionSettings();
    }

    public int getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(int mode) {
        cacheMode = mode;
    }

    public int getTextZoom() {
        return textZoom;
    }

    public void setTextZoom(int zoom) {
        textZoom = Math.max(50, Math.min(300, zoom));
    }

    public String getStandardFontFamily() {
        return standardFontFamily;
    }

    public void setStandardFontFamily(String family) {
        standardFontFamily = family;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    void setBackgroundColorValue(int color) {
        backgroundColor = color;
    }
}
