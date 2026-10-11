package com.coara.browser2.util;

import android.content.SharedPreferences;
import android.graphics.Color;
import com.coara.browser2.gecko.BrowserView;

import org.json.JSONObject;

public final class BrowserVisualSettings {
    public static final String KEY_TEXT_COLOR = "text_color";
    public static final String KEY_FONT_FAMILY = "font_family";
    public static final String KEY_BACKGROUND_TRANSPARENT = "background_transparent";
    public static final String KEY_BACKGROUND_PATH = "background_image_path";
    public static final String KEY_BACKGROUND_TRANSPARENCY_PERCENT = "background_transparency_percent";
    public static final String KEY_OFFSCREEN_PRE_RASTER = "offscreen_pre_raster";

    private static final String DEFAULT = "default";
    private static final String STYLE_ID = "__coara_browser_visual_style";

    private static final String[] TEXT_COLOR_LABELS = {
            "赤", "青", "緑", "黄色", "ピンク", "グレー", "茶色", "デフォルト"
    };

    private static final String[] TEXT_COLOR_VALUES = {
            "#F44336", "#2196F3", "#4CAF50", "#FFC107", "#E91E63", "#757575", "#795548", DEFAULT
    };

    private static final String[] FONT_LABELS = {
            "デフォルト",
            "Sans Serif",
            "Sans Serif Light",
            "Sans Serif Medium",
            "Sans Serif Condensed",
            "Sans Serif Condensed Light",
            "Sans Serif Black",
            "Sans Serif Thin",
            "Sans Serif Extra Light",
            "Sans Serif Small Caps",
            "Serif",
            "Monospace",
            "Cursive",
            "Fantasy"
    };

    private static final String[] FONT_VALUES = {
            DEFAULT,
            "sans-serif",
            "sans-serif-light",
            "sans-serif-medium",
            "sans-serif-condensed",
            "sans-serif-condensed-light",
            "sans-serif-black",
            "sans-serif-thin",
            "sans-serif-extra-light",
            "sans-serif-smallcaps",
            "serif",
            "monospace",
            "cursive",
            "fantasy"
    };

    private static final java.util.Map<BrowserView, String> APPLIED_SIGNATURES = new java.util.WeakHashMap<>();
    private static String cachedCssKey;
    private static String cachedScript;

    private BrowserVisualSettings() {
    }

    public static String[] getTextColorLabels() {
        return TEXT_COLOR_LABELS.clone();
    }

    public static String[] getTextColorValues() {
        return TEXT_COLOR_VALUES.clone();
    }

    public static String[] getFontLabels() {
        return FONT_LABELS.clone();
    }

    public static String[] getFontValues() {
        return FONT_VALUES.clone();
    }

    public static int getTextColorIndex(SharedPreferences preferences) {
        String value = getTextColor(preferences);
        for (int i = 0; i < TEXT_COLOR_VALUES.length; i++) {
            if (TEXT_COLOR_VALUES[i].equals(value)) {
                return i;
            }
        }
        return TEXT_COLOR_VALUES.length - 1;
    }

    public static int getFontIndex(SharedPreferences preferences) {
        String value = getFontFamily(preferences);
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (FONT_VALUES[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    public static String getTextColor(SharedPreferences preferences) {
        String value = preferences.getString(KEY_TEXT_COLOR, DEFAULT);
        if (value == null) {
            return DEFAULT;
        }
        for (String allowed : TEXT_COLOR_VALUES) {
            if (allowed.equals(value)) {
                return value;
            }
        }
        return DEFAULT;
    }

    public static void setTextColor(SharedPreferences preferences, String value) {
        if (DEFAULT.equals(value)) {
            preferences.edit().remove(KEY_TEXT_COLOR).apply();
            return;
        }
        for (String allowed : TEXT_COLOR_VALUES) {
            if (allowed.equals(value)) {
                preferences.edit().putString(KEY_TEXT_COLOR, value).apply();
                return;
            }
        }
        preferences.edit().remove(KEY_TEXT_COLOR).apply();
    }

    public static String getFontFamily(SharedPreferences preferences) {
        String value = preferences.getString(KEY_FONT_FAMILY, DEFAULT);
        if (value == null) {
            return DEFAULT;
        }
        for (String allowed : FONT_VALUES) {
            if (allowed.equals(value)) {
                return value;
            }
        }
        return DEFAULT;
    }

    public static void setFontFamily(SharedPreferences preferences, String value) {
        if (DEFAULT.equals(value)) {
            preferences.edit().remove(KEY_FONT_FAMILY).apply();
            return;
        }
        for (String allowed : FONT_VALUES) {
            if (allowed.equals(value)) {
                preferences.edit().putString(KEY_FONT_FAMILY, value).apply();
                return;
            }
        }
        preferences.edit().remove(KEY_FONT_FAMILY).apply();
    }

    public static boolean isTransparencyEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_BACKGROUND_TRANSPARENT, false);
    }

    public static void setTransparencyEnabled(SharedPreferences preferences, boolean enabled) {
        if (enabled) {
            preferences.edit().putBoolean(KEY_BACKGROUND_TRANSPARENT, true).apply();
        } else {
            preferences.edit().remove(KEY_BACKGROUND_TRANSPARENT).apply();
        }
    }

    public static String getBackgroundPath(SharedPreferences preferences) {
        String path = preferences.getString(KEY_BACKGROUND_PATH, null);
        return path == null || path.trim().isEmpty() ? null : path;
    }

    public static void setBackgroundPath(SharedPreferences preferences, String path) {
        if (path == null || path.trim().isEmpty()) {
            preferences.edit().remove(KEY_BACKGROUND_PATH).apply();
        } else {
            preferences.edit().putString(KEY_BACKGROUND_PATH, path).apply();
        }
    }


    public static int getBackgroundTransparencyPercent(SharedPreferences preferences) {
        int value;
        try {
            value = preferences.getInt(KEY_BACKGROUND_TRANSPARENCY_PERCENT, 0);
        } catch (Exception ignored) {
            value = 0;
        }
        return clampPercent(value);
    }

    public static void setBackgroundTransparencyPercent(SharedPreferences preferences, int value) {
        int clamped = clampPercent(value);
        if (clamped == 0) {
            preferences.edit().remove(KEY_BACKGROUND_TRANSPARENCY_PERCENT).apply();
        } else {
            preferences.edit().putInt(KEY_BACKGROUND_TRANSPARENCY_PERCENT, clamped).apply();
        }
    }

    public static boolean isOffscreenPreRasterEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_OFFSCREEN_PRE_RASTER, false);
    }

    public static void setOffscreenPreRasterEnabled(SharedPreferences preferences, boolean enabled) {
        if (enabled) {
            preferences.edit().putBoolean(KEY_OFFSCREEN_PRE_RASTER, true).apply();
        } else {
            preferences.edit().remove(KEY_OFFSCREEN_PRE_RASTER).apply();
        }
    }

    private static int clampPercent(int value) {
        if (value < 0) return 0;
        if (value > 120) return 120;
        return value;
    }

    public static int resolveTextColor(SharedPreferences preferences) {
        String color = getTextColor(preferences);
        if (DEFAULT.equals(color)) {
            return Color.TRANSPARENT;
        }
        try {
            return Color.parseColor(color);
        } catch (Exception ignored) {
            return Color.TRANSPARENT;
        }
    }

    public static void applyToWebView(BrowserView webView, SharedPreferences preferences) {
        if (webView == null) {
            return;
        }

        String fontFamily = getFontFamily(preferences);
        boolean transparency = isTransparencyEnabled(preferences);
        int transparencyPercent = getBackgroundTransparencyPercent(preferences);
        boolean preRaster = isOffscreenPreRasterEnabled(preferences);
        boolean dark = false;
        try {
            dark = com.coara.browser2.gecko.BrowserEngine.get().isDarkMode();
        } catch (Exception ignored) {
        }
        boolean wallpaper = transparency && BackgroundWallpaper.isReady();
        String signature = fontFamily + "|" + transparency + "|" + preRaster + "|" + dark + "|" + BackgroundWallpaper.getStamp();
        boolean unchanged;
        synchronized (APPLIED_SIGNATURES) {
            unchanged = signature.equals(APPLIED_SIGNATURES.get(webView));
            if (!unchanged) {
                APPLIED_SIGNATURES.put(webView, signature);
            }
        }

        if (!unchanged) {
            try {
                webView.getSettings().setStandardFontFamily(DEFAULT.equals(fontFamily) ? null : fontFamily);
            } catch (Exception ignored) {
            }

            try {
                webView.setBackgroundColorValue(wallpaper
                        ? BackgroundWallpaper.getAverageColor()
                        : (dark ? 0xFF2A2A2E : Color.WHITE));
            } catch (Exception ignored) {
            }
        }

        String textColor = getTextColor(preferences);
        boolean hasCss = hasCustomVisualCss(preferences);
        if (!hasCss) {
            return;
        }

        injectStyle(webView, textColor, fontFamily, transparency, transparencyPercent);
    }

    public static boolean hasCustomVisualCss(SharedPreferences preferences) {
        return isTransparencyEnabled(preferences)
                || !DEFAULT.equals(getTextColor(preferences))
                || !DEFAULT.equals(getFontFamily(preferences));
    }

    public static void clearInjectedStyle(BrowserView webView) {
        removeStyle(webView);
    }

    private static void injectStyle(BrowserView webView, String textColor, String fontFamily, boolean transparency, int transparencyPercent) {
        try {
            String cssKey = textColor + "|" + fontFamily + "|" + transparency + "|" + transparencyPercent + "|" + BackgroundWallpaper.getStamp();
            if (!cssKey.equals(cachedCssKey) || cachedScript == null) {
                StringBuilder css = new StringBuilder();
                if (!DEFAULT.equals(textColor)) {
                    css.append("body,body *{color:")
                            .append(textColor)
                            .append(" !important;}");
                }
                if (!DEFAULT.equals(fontFamily)) {
                    css.append("body,body *{font-family:")
                            .append(fontFamily)
                            .append(" !important;}");
                }
                if (transparency) {
                    css.append(buildTransparencyCss(transparencyPercent));
                    String wallpaperUri = BackgroundWallpaper.getDataUri();
                    if (wallpaperUri != null) {
                        css.append("html:root{background-color:transparent !important;background-image:url(\"")
                                .append(wallpaperUri)
                                .append("\") !important;background-size:cover !important;background-position:center center !important;background-repeat:no-repeat !important;background-attachment:fixed !important;}");
                    }
                }
                cachedCssKey = cssKey;
                cachedScript = css.toString();
            }
            webView.setCss(STYLE_ID, cachedScript);
        } catch (Exception ignored) {
        }
    }

    private static void removeStyle(BrowserView webView) {
        try {
            webView.removeCss(STYLE_ID);
        } catch (Exception ignored) {
        }
    }

    private static String buildTransparencyCss(int percent) {
        StringBuilder css = new StringBuilder();
        int clamped = clampPercent(percent);
        css.append("html,body,body:before,body:after{background-color:transparent !important;background-image:none !important;}");
        if (clamped >= 11) {
            css.append("main,section,article,aside,header,footer,nav,form,dialog,[role='main'],[role='article'],[role='dialog']{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 21) {
            css.append("[class*='container'],[class*='content'],[class*='panel'],[class*='card'],[class*='surface'],[class*='layout'],[class*='wrapper'],[class*='background']{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 31) {
            css.append("section > div,article > div,main > div,aside > div,header > div,footer > div,nav > div,form > div{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 41) {
            css.append("table,thead,tbody,tfoot,tr,td,th,ul,ol,li,blockquote,pre{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 51) {
            css.append("[style*='background'],[style*='background-color'],[style*='background-image']{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 61) {
            css.append("div{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 71) {
            css.append("span,button,label,fieldset,legend,details,summary{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 81) {
            css.append("body *:not(img):not(video):not(canvas):not(svg):not(input):not(textarea):not(select):not(option):not(button):not(progress):not(meter){background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 91) {
            css.append("body *:not(img):not(video):not(canvas):not(svg){background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 101) {
            css.append("html,body,body *{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 111) {
            css.append("html,body,body *,body *:before,body *:after{background-color:transparent !important;background-image:none !important;}");
        }
        if (clamped >= 116) {
            css.append("body *{box-shadow:none !important;}");
        }
        return css.toString();
    }

}
