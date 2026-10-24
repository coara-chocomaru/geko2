package com.coara.browser2.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import com.coara.browser2.gecko.BrowserView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BrowserThemeManager {
    private static final String THEME_DIR = "themes";
    private static final String MANIFEST = "manifest.json";
    private static final String DEFAULT_START_CSS = "";
    private static final long MAX_THEME_FILE_BYTES = 6L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 96L * 1024L;
    private static final long MAX_CSS_BYTES = 192L * 1024L;
    private static final long MAX_PAGE_CSS_BYTES = 128L * 1024L;
    private static final int MAX_PAGE_SITES = 24;
    private static final String[] DEFAULT_PAGE_SITES = {"google", "bing", "yahoo", "duckduckgo", "brave", "startpage"};
    private static final String PAGE_STYLE_ID = "__coara_page_theme";
    private static final long MAX_TOTAL_ENTRY_BYTES = 12L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 48;
    private static final String START_PAGE_PREFIX = BrowserConstants.APP_ASSET_START_PAGE;
    private static final Map<View, OriginalViewState> ORIGINAL_VIEWS = new java.util.WeakHashMap<>();
    private static final Map<Activity, OriginalWindowState> ORIGINAL_WINDOWS = new java.util.WeakHashMap<>();
    private static final Map<Activity, String> APPLIED_ACTIVITY_THEMES = new java.util.WeakHashMap<>();
    private static final Map<Activity, String> FAILED_LAYOUT_THEMES = new java.util.WeakHashMap<>();
    private static final Map<String, String> START_CSS_CACHE = new LinkedHashMap<String, String>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 8;
        }
    };
    private static String cachedActiveFile;
    private static long cachedActiveModified = Long.MIN_VALUE;
    private static ThemeInfo cachedActiveTheme;
    private static String cachedScriptKey;
    private static String cachedScript;
    private static boolean activeCacheValid;
    private static final Map<String, String> PAGE_SCRIPT_CACHE = new LinkedHashMap<String, String>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 12;
        }
    };
    private static final Map<String, String> PAGE_CSS_CACHE = new LinkedHashMap<String, String>(4, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 4;
        }
    };
    private static final Map<BrowserView, String> PAGE_THEMED = new java.util.WeakHashMap<>();
    private static final Map<BrowserView, String> START_PAGE_THEMED = new java.util.WeakHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Integer, String> ID_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private BrowserThemeManager() {
    }

    public static final class ThemeInfo {
        public final String fileName;
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final ThemePalette palette;
        public final String startPageCssEntry;
        public final String pageCssEntry;
        public final List<String> pageSites;
        public final BrowserThemeLayoutController.LayoutSpec layout;
        public final MenuStyle menuStyle;
        public final boolean layoutEnabled;
        public final long sizeBytes;
        public final long modifiedAt;

        ThemeInfo(String fileName, String id, String name, String version, String author,
                  String description, ThemePalette palette, String startPageCssEntry,
                  String pageCssEntry, List<String> pageSites,
                  BrowserThemeLayoutController.LayoutSpec layout, MenuStyle menuStyle,
                  boolean layoutEnabled, long sizeBytes, long modifiedAt) {
            this.fileName = fileName;
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.palette = palette;
            this.startPageCssEntry = startPageCssEntry;
            this.pageCssEntry = pageCssEntry == null ? "" : pageCssEntry;
            this.pageSites = pageSites == null ? java.util.Collections.<String>emptyList() : pageSites;
            this.layout = layout == null ? BrowserThemeLayoutController.LayoutSpec.defaults() : layout;
            this.menuStyle = menuStyle == null ? MenuStyle.defaultStyle(palette) : menuStyle;
            this.layoutEnabled = layoutEnabled;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
        }
    }

    public static final class ThemePalette {
        public final int primary;
        public final int primaryVariant;
        public final int onPrimary;
        public final int secondary;
        public final int onSecondary;
        public final int background;
        public final int surface;
        public final int onSurface;
        public final int textPrimary;
        public final int textSecondary;
        public final int urlBar;
        public final int accent;
        public final int buttonBackground;
        public final int buttonText;
        public final int stroke;
        public final int danger;

        ThemePalette(int primary, int primaryVariant, int onPrimary, int secondary, int onSecondary,
                     int background, int surface, int onSurface, int textPrimary, int textSecondary,
                     int urlBar, int accent, int buttonBackground, int buttonText, int stroke, int danger) {
            this.primary = primary;
            this.primaryVariant = primaryVariant;
            this.onPrimary = onPrimary;
            this.secondary = secondary;
            this.onSecondary = onSecondary;
            this.background = background;
            this.surface = surface;
            this.onSurface = onSurface;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.urlBar = urlBar;
            this.accent = accent;
            this.buttonBackground = buttonBackground;
            this.buttonText = buttonText;
            this.stroke = stroke;
            this.danger = danger;
        }

        static ThemePalette defaultPalette() {
            return fromDefaults();
        }

        private static ThemePalette fromDefaults() {
            return new ThemePalette(
                    Color.rgb(66, 133, 244), Color.rgb(51, 103, 214), Color.WHITE,
                    Color.rgb(3, 218, 197), Color.BLACK, Color.WHITE, Color.WHITE, Color.rgb(31, 31, 31),
                    Color.rgb(33, 33, 33), Color.rgb(117, 117, 117), Color.rgb(241, 243, 244),
                    Color.rgb(66, 133, 244), Color.rgb(66, 133, 244), Color.WHITE, Color.rgb(224, 227, 231),
                    Color.rgb(198, 40, 40));
        }
    }

    public static final class MenuStyle {
        public final boolean customized;
        public final String style;
        public final int background;
        public final int itemBackground;
        public final int pressedBackground;
        public final int text;
        public final int secondary;
        public final int accent;
        public final int headerText;
        public final int headerBackground;
        public final int divider;
        public final int stroke;
        public final int iconTint;
        public final int radiusDp;
        public final int itemRadiusDp;
        public final int elevationDp;
        public final int paddingHorizontalDp;
        public final int paddingVerticalDp;
        public final int itemHeightDp;
        public final int groupSpacingDp;
        public final int itemGapDp;
        public final int rowPaddingHorizontalDp;
        public final int iconGapDp;
        public final int iconSizeDp;
        public final int trailingWidthDp;
        public final int maxWidthDp;
        public final int strokeWidthDp;
        public final int buttonInsetDp;
        public final int buttonIconSizeDp;
        public final int buttonSizeDp;
        public final int textSizeSp;
        public final int headerTextSizeSp;
        public final String checkStyle;
        public final boolean useDividers;
        public final String surfacePattern;
        public final String rowPattern;
        public final String headerPattern;
        public final String fontFamily;
        public final boolean alternateRows;

        private MenuStyle(boolean customized, String style, int background, int itemBackground, int pressedBackground,
                          int text, int secondary, int accent, int headerText, int headerBackground, int divider, int stroke,
                          int iconTint, int radiusDp, int itemRadiusDp, int elevationDp,
                          int paddingHorizontalDp, int paddingVerticalDp, int itemHeightDp, int groupSpacingDp,
                          int itemGapDp, int rowPaddingHorizontalDp, int iconGapDp, int iconSizeDp, int trailingWidthDp,
                          int maxWidthDp, int strokeWidthDp, int buttonInsetDp, int buttonIconSizeDp, int buttonSizeDp,
                          int textSizeSp, int headerTextSizeSp, String checkStyle, boolean useDividers,
                          String surfacePattern, String rowPattern, String headerPattern, String fontFamily, boolean alternateRows) {
            this.customized = customized;
            this.style = style;
            this.background = background;
            this.itemBackground = itemBackground;
            this.pressedBackground = pressedBackground;
            this.text = text;
            this.secondary = secondary;
            this.accent = accent;
            this.headerText = headerText;
            this.headerBackground = headerBackground;
            this.divider = divider;
            this.stroke = stroke;
            this.iconTint = iconTint;
            this.radiusDp = radiusDp;
            this.itemRadiusDp = itemRadiusDp;
            this.elevationDp = elevationDp;
            this.paddingHorizontalDp = paddingHorizontalDp;
            this.paddingVerticalDp = paddingVerticalDp;
            this.itemHeightDp = itemHeightDp;
            this.groupSpacingDp = groupSpacingDp;
            this.itemGapDp = itemGapDp;
            this.rowPaddingHorizontalDp = rowPaddingHorizontalDp;
            this.iconGapDp = iconGapDp;
            this.iconSizeDp = iconSizeDp;
            this.trailingWidthDp = trailingWidthDp;
            this.maxWidthDp = maxWidthDp;
            this.strokeWidthDp = strokeWidthDp;
            this.buttonInsetDp = buttonInsetDp;
            this.buttonIconSizeDp = buttonIconSizeDp;
            this.buttonSizeDp = buttonSizeDp;
            this.textSizeSp = textSizeSp;
            this.headerTextSizeSp = headerTextSizeSp;
            this.checkStyle = checkStyle;
            this.useDividers = useDividers;
            this.surfacePattern = surfacePattern;
            this.rowPattern = rowPattern;
            this.headerPattern = headerPattern;
            this.fontFamily = fontFamily;
            this.alternateRows = alternateRows;
        }

        public static MenuStyle defaultStyle(ThemePalette palette) {
            ThemePalette p = palette == null ? ThemePalette.defaultPalette() : palette;
            int background = p.surface;
            int item = p.surface;
            int pressed = ColorUtils.withAlpha(p.accent, 38);
            int divider = ColorUtils.withAlpha(p.stroke, 180);
            return new MenuStyle(false, "rounded", background, item, pressed, p.textPrimary, p.textSecondary,
                    p.accent, p.accent, background, divider, p.stroke, p.textPrimary,
                    16, 10, 8, 8, 8, 48, 8, 2, 10, 10, 20, 26, 336, 1, 1, 22, 44,
                    15, 15, "check", true, "solid", "card", "band", "sans-serif", false);
        }

        static MenuStyle fromJson(JSONObject object, ThemePalette palette) {
            MenuStyle d = defaultStyle(palette);
            if (object == null) return d;
            String style = oneOf(object.optString("style", d.style), "rounded", "pill", "soft", "glass", "terminal", "flat");
            int background = parseColor(object, "background", defaultBackground(style, palette, d.background));
            int itemBackground = parseColor(object, "itemBackground", defaultItemBackground(style, palette, d.itemBackground));
            int pressedBackground = parseColor(object, "pressedBackground", defaultPressed(style, palette, d.pressedBackground));
            int text = parseColor(object, "text", d.text);
            int secondary = parseColor(object, "secondary", d.secondary);
            int accent = parseColor(object, "accent", d.accent);
            int headerText = parseColor(object, "headerText", accent);
            int headerBackground = parseColor(object, "headerBackground", defaultHeaderBackground(style, palette, background));
            int divider = parseColor(object, "divider", d.divider);
            int stroke = parseColor(object, "stroke", d.stroke);
            int iconTint = parseColor(object, "iconTint", text);
            int radius = intValue(object, "radiusDp", defaultRadius(style, d.radiusDp), 0, 40);
            int itemRadius = intValue(object, "itemRadiusDp", defaultItemRadius(style, d.itemRadiusDp), 0, 32);
            int elevation = intValue(object, "elevationDp", defaultElevation(style, d.elevationDp), 0, 24);
            int padH = intValue(object, "paddingHorizontalDp", d.paddingHorizontalDp, 0, 28);
            int padV = intValue(object, "paddingVerticalDp", d.paddingVerticalDp, 0, 28);
            int itemHeight = intValue(object, "itemHeightDp", d.itemHeightDp, 36, 64);
            int groupSpacing = intValue(object, "groupSpacingDp", defaultGroupSpacing(style, d.groupSpacingDp), 0, 24);
            int itemGap = intValue(object, "itemGapDp", defaultItemGap(style, d.itemGapDp), 0, 10);
            int rowPadding = intValue(object, "rowPaddingHorizontalDp", d.rowPaddingHorizontalDp, 4, 24);
            int iconGap = intValue(object, "iconGapDp", d.iconGapDp, 2, 18);
            int iconSize = intValue(object, "iconSizeDp", d.iconSizeDp, 16, 32);
            int trailingWidth = intValue(object, "trailingWidthDp", d.trailingWidthDp, 20, 36);
            int maxWidth = intValue(object, "maxWidthDp", d.maxWidthDp, 220, 440);
            int strokeWidth = intValue(object, "strokeWidthDp", d.strokeWidthDp, 0, 4);
            int buttonInset = intValue(object, "buttonInsetDp", d.buttonInsetDp, 0, 6);
            int buttonIconSize = intValue(object, "buttonIconSizeDp", d.buttonIconSizeDp, 16, 24);
            int buttonSize = intValue(object, "buttonSizeDp", d.buttonSizeDp, 36, 56);
            int textSize = intValue(object, "textSizeSp", d.textSizeSp, 13, 18);
            int headerTextSize = intValue(object, "headerTextSizeSp", d.headerTextSizeSp, 13, 18);
            String checkStyle = oneOf(object.optString("checkStyle", d.checkStyle), "check", "dot", "circle");
            boolean useDividers = object.optBoolean("useDividers", defaultUseDividers(style, d.useDividers));
            String surfacePattern = oneOf(object.optString("surfacePattern", defaultSurfacePattern(style, d.surfacePattern)), "solid", "gradient", "frosted", "paper", "terminal");
            String rowPattern = oneOf(object.optString("rowPattern", defaultRowPattern(style, d.rowPattern)), "flat", "card", "pill", "glass", "stripe", "terminal");
            String headerPattern = oneOf(object.optString("headerPattern", defaultHeaderPattern(style, d.headerPattern)), "band", "accent", "outline", "plain", "terminal");
            String fontFamily = oneOf(object.optString("fontFamily", defaultFontFamily(style, d.fontFamily)), "sans-serif", "sans-serif-medium", "monospace", "serif");
            boolean alternateRows = object.optBoolean("alternateRows", defaultAlternateRows(style, d.alternateRows));
            return new MenuStyle(true, style, background, itemBackground, pressedBackground, text, secondary,
                    accent, headerText, headerBackground, divider, stroke, iconTint, radius, itemRadius, elevation,
                    padH, padV, itemHeight, groupSpacing, itemGap, rowPadding, iconGap, iconSize, trailingWidth,
                    maxWidth, strokeWidth, buttonInset, buttonIconSize, buttonSize, textSize, headerTextSize, checkStyle, useDividers,
                    surfacePattern, rowPattern, headerPattern, fontFamily, alternateRows);
        }

        private static String oneOf(String value, String... values) {
            if (value != null) {
                for (String candidate : values) {
                    if (candidate.equals(value)) return value;
                }
            }
            return values[0];
        }

        private static int intValue(JSONObject object, String key, int fallback, int min, int max) {
            int value = object.optInt(key, fallback);
            return Math.max(min, Math.min(max, value));
        }

        private static int defaultBackground(String style, ThemePalette palette, int fallback) {
            if ("glass".equals(style)) return ColorUtils.withAlpha(palette.surface, 242);
            if ("terminal".equals(style)) return palette.background;
            return fallback;
        }

        private static int defaultItemBackground(String style, ThemePalette palette, int fallback) {
            if ("flat".equals(style)) return ColorUtils.withAlpha(palette.surface, 0);
            if ("glass".equals(style)) return ColorUtils.withAlpha(palette.surface, 150);
            if ("terminal".equals(style)) return ColorUtils.withAlpha(palette.surface, 24);
            return fallback;
        }

        private static int defaultHeaderBackground(String style, ThemePalette palette, int fallback) {
            if ("flat".equals(style)) return ColorUtils.withAlpha(palette.accent, 16);
            if ("glass".equals(style)) return ColorUtils.withAlpha(palette.onSurface, 18);
            if ("terminal".equals(style)) return ColorUtils.withAlpha(palette.accent, 18);
            if ("pill".equals(style)) return ColorUtils.withAlpha(palette.accent, 20);
            return fallback;
        }

        private static int defaultRadius(String style, int fallback) {
            if ("pill".equals(style)) return 30;
            if ("terminal".equals(style)) return 8;
            if ("flat".equals(style)) return 4;
            return fallback;
        }

        private static int defaultItemRadius(String style, int fallback) {
            if ("pill".equals(style)) return 22;
            if ("terminal".equals(style)) return 5;
            if ("flat".equals(style)) return 4;
            return fallback;
        }

        private static int defaultElevation(String style, int fallback) {
            if ("flat".equals(style)) return 4;
            if ("glass".equals(style)) return 12;
            return fallback;
        }

        private static int defaultGroupSpacing(String style, int fallback) {
            if ("pill".equals(style)) return 10;
            if ("flat".equals(style)) return 6;
            if ("terminal".equals(style)) return 7;
            return fallback;
        }

        private static int defaultItemGap(String style, int fallback) {
            if ("pill".equals(style)) return 4;
            if ("glass".equals(style)) return 3;
            if ("flat".equals(style)) return 1;
            return fallback;
        }

        private static boolean defaultUseDividers(String style, boolean fallback) {
            return "flat".equals(style) || "terminal".equals(style) || fallback;
        }

        private static String defaultSurfacePattern(String style, String fallback) {
            if ("glass".equals(style)) return "frosted";
            if ("terminal".equals(style)) return "terminal";
            if ("pill".equals(style)) return "gradient";
            if ("soft".equals(style)) return "paper";
            return fallback;
        }

        private static String defaultRowPattern(String style, String fallback) {
            if ("glass".equals(style)) return "glass";
            if ("terminal".equals(style)) return "terminal";
            if ("pill".equals(style)) return "pill";
            if ("flat".equals(style)) return "flat";
            if ("soft".equals(style)) return "card";
            return fallback;
        }

        private static String defaultHeaderPattern(String style, String fallback) {
            if ("terminal".equals(style)) return "terminal";
            if ("pill".equals(style)) return "accent";
            if ("glass".equals(style)) return "band";
            if ("soft".equals(style)) return "outline";
            return fallback;
        }

        private static String defaultFontFamily(String style, String fallback) {
            return "terminal".equals(style) ? "monospace" : fallback;
        }

        private static boolean defaultAlternateRows(String style, boolean fallback) {
            return "terminal".equals(style) || "flat".equals(style) || fallback;
        }

        private static int defaultPressed(String style, ThemePalette palette, int fallback) {
            if ("terminal".equals(style)) return ColorUtils.withAlpha(palette.accent, 68);
            if ("glass".equals(style)) return ColorUtils.withAlpha(palette.onSurface, 34);
            if ("flat".equals(style)) return ColorUtils.withAlpha(palette.accent, 42);
            if ("pill".equals(style)) return ColorUtils.withAlpha(palette.accent, 48);
            return fallback;
        }
    }

    private static final class ColorUtils {
        static int withAlpha(int color, int alpha) {
            return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
        }

        static double luminance(int color) {
            double r = channel(Color.red(color));
            double g = channel(Color.green(color));
            double b = channel(Color.blue(color));
            return 0.2126 * r + 0.7152 * g + 0.0722 * b;
        }

        private static double channel(int value) {
            double c = value / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }

        static double contrast(int a, int b) {
            double la = luminance(a);
            double lb = luminance(b);
            double hi = Math.max(la, lb);
            double lo = Math.min(la, lb);
            return (hi + 0.05) / (lo + 0.05);
        }

        static int readable(int preferred, int background, int fallback) {
            return contrast(preferred, background) >= 4.5 ? preferred : fallback;
        }
    }

    public static int chipTextColor(ThemePalette p) {
        return ColorUtils.readable(p.accent, p.urlBar, p.textPrimary);
    }

    public static int chipBackgroundColor(ThemePalette p) {
        return p.urlBar;
    }

    public static int fieldContentColor(Context context, int fallback) {
        ThemeInfo theme = getActiveTheme(context);
        return theme == null ? fallback : theme.palette.textPrimary;
    }


    public static ThemePalette getDefaultPalette() {
        return ThemePalette.defaultPalette();
    }

    public static int accentColor(Context context, int fallback) {
        ThemeInfo theme = getActiveTheme(context);
        return theme == null ? fallback : theme.palette.accent;
    }

    public static boolean isEnabled(Context context) {
        return getActiveTheme(context) != null;
    }

    public static synchronized ThemeInfo getActiveTheme(Context context) {
        if (activeCacheValid) {
            return cachedActiveTheme;
        }
        String active = context.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE)
                .getString(BrowserConstants.KEY_ACTIVE_THEME_FILE, null);
        if (active == null || active.trim().isEmpty()) {
            cachedActiveFile = null;
            cachedActiveModified = Long.MIN_VALUE;
            cachedActiveTheme = null;
            activeCacheValid = true;
            return null;
        }
        if (!active.equals(new File(active).getName()) || active.contains("/") || active.contains("\\")) {
            clearActiveTheme(context);
            return null;
        }
        File file = new File(getThemeDir(context), active);
        if (!isSafeThemeFile(file)) {
            clearActiveTheme(context);
            return null;
        }
        long modified = file.lastModified();
        if (active.equals(cachedActiveFile) && cachedActiveTheme != null && modified == cachedActiveModified) {
            activeCacheValid = true;
            return cachedActiveTheme;
        }
        try {
            ThemeInfo theme = readTheme(file);
            cachedActiveFile = active;
            cachedActiveModified = modified;
            cachedActiveTheme = theme;
            activeCacheValid = true;
            return theme;
        } catch (Exception e) {
            clearActiveTheme(context);
            return null;
        }
    }

    public static List<ThemeInfo> list(Context context) {
        ArrayList<ThemeInfo> result = new ArrayList<>();
        File dir = getThemeDir(context);
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (!isSafeThemeFile(file)) {
                continue;
            }
            try {
                result.add(readTheme(file));
            } catch (Exception ignored) {
            }
        }
        result.sort((a, b) -> {
            if (a.modifiedAt != b.modifiedAt) {
                return Long.compare(b.modifiedAt, a.modifiedAt);
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    public static ThemeInfo importFromUri(Context context, Uri uri, String displayName) throws Exception {
        if (uri == null) {
            throw new IllegalArgumentException("ファイルが選択されていません");
        }
        File dir = getThemeDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("テーマ保存先を作成できません");
        }
        File temp = new File(dir, ".theme_import_" + System.nanoTime() + ".tmp");
        File target = null;
        boolean success = false;
        try {
            long copied = 0L;
            InputStream source = context.getContentResolver().openInputStream(uri);
            if (source == null) {
                throw new IllegalStateException("ファイルを開けません");
            }
            try (InputStream in = new BufferedInputStream(source); OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    copied += n;
                    if (copied > MAX_THEME_FILE_BYTES) {
                        throw new IllegalArgumentException("テーマZIPが大きすぎます");
                    }
                    out.write(buffer, 0, n);
                }
            }
            ThemeInfo parsed = readTheme(temp);
            String base = sanitizeFilePart(parsed.id.isEmpty() ? displayName : parsed.id);
            if (base.isEmpty()) {
                base = "theme";
            }
            String finalName = base + "_" + System.currentTimeMillis() + "_" + Math.abs(System.nanoTime()) + ".zip";
            target = new File(dir, finalName);
            if (!temp.renameTo(target)) {
                try (InputStream in = new FileInputStream(temp); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, n);
                    }
                }
            }
            ThemeInfo result = readTheme(target);
            synchronized (START_CSS_CACHE) {
                START_CSS_CACHE.remove(result.fileName);
            }
            success = true;
            return result;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
            if (!success && target != null && target.exists()) {
                target.delete();
            }
        }
    }

    public static synchronized boolean setActiveTheme(Context context, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return clearActiveTheme(context);
        }
        File file = new File(getThemeDir(context), fileName);
        if (!isSafeThemeFile(file)) {
            return false;
        }
        ThemeInfo validated;
        try {
            validated = readTheme(file);
        } catch (Exception e) {
            return false;
        }
        boolean committed = context.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(BrowserConstants.KEY_ACTIVE_THEME_FILE, fileName).commit();
        if (!committed) {
            return false;
        }
        synchronized (BrowserThemeManager.class) {
            cachedActiveFile = fileName;
            cachedActiveModified = file.lastModified();
            cachedActiveTheme = validated;
            activeCacheValid = true;
            cachedScriptKey = null;
            cachedScript = null;
        }
        clearPageCaches();
        synchronized (APPLIED_ACTIVITY_THEMES) {
            APPLIED_ACTIVITY_THEMES.clear();
            FAILED_LAYOUT_THEMES.clear();
        }
        return true;
    }

    public static synchronized boolean clearActiveTheme(Context context) {
        boolean committed = context.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove(BrowserConstants.KEY_ACTIVE_THEME_FILE).commit();
        if (!committed) {
            return false;
        }
        synchronized (BrowserThemeManager.class) {
            cachedActiveFile = null;
            cachedActiveModified = Long.MIN_VALUE;
            cachedActiveTheme = null;
            activeCacheValid = true;
            cachedScriptKey = null;
            cachedScript = null;
        }
        clearPageCaches();
        synchronized (APPLIED_ACTIVITY_THEMES) {
            APPLIED_ACTIVITY_THEMES.clear();
            FAILED_LAYOUT_THEMES.clear();
        }
        return true;
    }

    private static void clearPageCaches() {
        synchronized (PAGE_SCRIPT_CACHE) {
            PAGE_SCRIPT_CACHE.clear();
            PAGE_CSS_CACHE.clear();
        }
        synchronized (START_CSS_CACHE) {
            START_CSS_CACHE.clear();
        }
    }

    public static boolean delete(Context context, ThemeInfo theme) {
        if (theme == null || theme.fileName == null || theme.fileName.isEmpty()) {
            return false;
        }
        String active = context.getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE)
                .getString(BrowserConstants.KEY_ACTIVE_THEME_FILE, null);
        if (theme.fileName.equals(active)) {
            clearActiveTheme(context);
        }
        File file = new File(getThemeDir(context), theme.fileName);
        return isSafeThemeFile(file) && file.delete();
    }

    public static void applyToActivity(Activity activity) {
        if (activity == null || shouldSkip(activity) || activity.findViewById(com.coara.browser2.R.id.topAppBar) == null) {
            return;
        }
        ThemeInfo active = getActiveTheme(activity);
        if (active == null) {
            boolean dirty;
            synchronized (ORIGINAL_WINDOWS) {
                dirty = ORIGINAL_WINDOWS.containsKey(activity);
            }
            if (!dirty) {
                View content = activity.findViewById(android.R.id.content);
                View rootContent = content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0
                        ? ((ViewGroup) content).getChildAt(0) : null;
                synchronized (ORIGINAL_VIEWS) {
                    dirty = rootContent != null && ORIGINAL_VIEWS.containsKey(rootContent);
                }
            }
            if (dirty) {
                restoreActivity(activity);
            }
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        View rootContent = content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0
                ? ((ViewGroup) content).getChildAt(0) : null;
        if (rootContent == null) {
            return;
        }
        String themeKey = active.fileName + ":" + active.modifiedAt;
        synchronized (APPLIED_ACTIVITY_THEMES) {
            if (themeKey.equals(APPLIED_ACTIVITY_THEMES.get(activity))) {
                return;
            }
        }
        ThemePalette palette = active.palette;
        try {
            if (!active.layoutEnabled) {
                restoreActivity(activity);
            }
            saveOriginal(rootContent);
            BrowserThemeLayoutController.prepare(activity);
            Window window = activity.getWindow();
            synchronized (ORIGINAL_WINDOWS) {
                if (!ORIGINAL_WINDOWS.containsKey(activity)) {
                    ORIGINAL_WINDOWS.put(activity, new OriginalWindowState(
                            window.getStatusBarColor(),
                            window.getNavigationBarColor(),
                            window.getDecorView().getSystemUiVisibility()));
                }
            }
            applyWindowPalette(window, palette);
            boolean layoutApplied = !active.layoutEnabled;
            if (active.layoutEnabled) {
                try {
                    layoutApplied = BrowserThemeLayoutController.apply(activity, active.layout, palette);
                } catch (Throwable ignored) {
                    layoutApplied = false;
                }
            }
            if (!layoutApplied) {
                synchronized (APPLIED_ACTIVITY_THEMES) {
                    FAILED_LAYOUT_THEMES.put(activity, themeKey);
                }
                applyPaletteOnly(rootContent, palette);
            } else {
                synchronized (APPLIED_ACTIVITY_THEMES) {
                    FAILED_LAYOUT_THEMES.remove(activity);
                }
                if (active.layoutEnabled) {
                    applyToView(rootContent, palette, true, false, false, true);
                } else {
                    applyPaletteOnly(rootContent, palette);
                }
            }
            synchronized (APPLIED_ACTIVITY_THEMES) {
                APPLIED_ACTIVITY_THEMES.put(activity, themeKey);
            }
        } catch (Throwable ignored) {
            try {
                applyPaletteOnly(rootContent, palette);
                synchronized (APPLIED_ACTIVITY_THEMES) {
                    APPLIED_ACTIVITY_THEMES.put(activity, themeKey);
                    FAILED_LAYOUT_THEMES.put(activity, themeKey);
                }
            } catch (Throwable ignoredAgain) {
                synchronized (APPLIED_ACTIVITY_THEMES) {
                    APPLIED_ACTIVITY_THEMES.put(activity, themeKey);
                    FAILED_LAYOUT_THEMES.put(activity, themeKey);
                }
            }
        }
        try {
            activity.invalidateOptionsMenu();
        } catch (Throwable ignored) {
        }
    }

    private static void applyWindowPalette(Window window, ThemePalette palette) {
        if (window == null || palette == null) return;
        try {
            window.setStatusBarColor(palette.primaryVariant);
            window.setNavigationBarColor(palette.background);
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            if (isLight(palette.primaryVariant) && android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (isLight(palette.background) && android.os.Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    private static void applyPaletteOnly(View rootContent, ThemePalette palette) {
        if (rootContent == null || palette == null) return;
        if (!(rootContent instanceof BrowserView)) {
            try { rootContent.setBackgroundColor(palette.background); } catch (Throwable ignored) {}
        }
        applyToView(rootContent, palette, true, false, false, false);
    }

    public static void release(Activity activity) {
        if (activity == null) return;
        synchronized (APPLIED_ACTIVITY_THEMES) {
            APPLIED_ACTIVITY_THEMES.remove(activity);
            FAILED_LAYOUT_THEMES.remove(activity);
        }
        synchronized (ORIGINAL_WINDOWS) {
            ORIGINAL_WINDOWS.remove(activity);
        }
        BrowserThemeLayoutController.release(activity);
    }

    private static void handleThemeApplyFailure(Activity activity) {
        restoreActivity(activity);
    }

    private static void restoreActivity(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        synchronized (ORIGINAL_WINDOWS) {
            OriginalWindowState state = ORIGINAL_WINDOWS.get(activity);
            if (state != null) {
                try {
                    window.setStatusBarColor(state.statusBarColor);
                    window.setNavigationBarColor(state.navigationBarColor);
                    window.getDecorView().setSystemUiVisibility(state.systemUiVisibility);
                } catch (Throwable ignored) {
                }
                ORIGINAL_WINDOWS.remove(activity);
            }
        }
        BrowserThemeLayoutController.restore(activity);
        synchronized (APPLIED_ACTIVITY_THEMES) {
            APPLIED_ACTIVITY_THEMES.remove(activity);
        }
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            restoreView(((ViewGroup) content).getChildAt(0));
        }
    }

    private static String installStyleJs(String id, String css) {
        return "var M=window.__coaraSheets||(window.__coaraSheets={});var id=" + JSONObject.quote(id) + ",css=" + JSONObject.quote(css) + ";" +
                "try{var sh=new CSSStyleSheet();sh.replaceSync(css);var old=M[id];" +
                "var list=Array.prototype.slice.call(document.adoptedStyleSheets).filter(function(x){return x!==old;});" +
                "list.push(sh);document.adoptedStyleSheets=list;M[id]=sh;}catch(e1){" +
                "var o=document.getElementById(id);if(o&&o.parentNode)o.parentNode.removeChild(o);" +
                "var s=document.createElement('style');s.id=id;s.appendChild(document.createTextNode(css));" +
                "(document.head||document.documentElement).appendChild(s);}";
    }

    private static String removeStyleJs(String id) {
        return "var M=window.__coaraSheets||(window.__coaraSheets={});var id=" + JSONObject.quote(id) + ";" +
                "try{var old=M[id];if(old){document.adoptedStyleSheets=Array.prototype.slice.call(document.adoptedStyleSheets).filter(function(x){return x!==old;});delete M[id];}}catch(e1){}" +
                "var o=document.getElementById(id);if(o&&o.parentNode)o.parentNode.removeChild(o);";
    }

    public static void applyToWebView(BrowserView webView, Context context) {
        if (webView == null || context == null) {
            return;
        }
        String url = webView.getUrl();
        if (url == null) {
            return;
        }
        if (!url.startsWith(START_PAGE_PREFIX)) {
            synchronized (START_PAGE_THEMED) {
                START_PAGE_THEMED.remove(webView);
            }
            applyPageTheme(webView, context, url);
            return;
        }
        ThemeInfo theme = getActiveTheme(context);
        if (theme == null) {
            String previous;
            synchronized (START_PAGE_THEMED) {
                previous = START_PAGE_THEMED.put(webView, "");
            }
            if (previous == null || !previous.isEmpty()) {
                removeStartPageStyle(webView);
            }
            return;
        }
        String key = theme.fileName + ":" + theme.modifiedAt;
        synchronized (START_PAGE_THEMED) {
            if (key.equals(START_PAGE_THEMED.get(webView))) {
                return;
            }
        }
        String script;
        synchronized (BrowserThemeManager.class) {
            if (key.equals(cachedScriptKey) && cachedScript != null) {
                script = cachedScript;
            } else {
                String combined = buildStartPagePaletteCss(theme.palette) + readStartPageCss(context, theme);
                script = "(function(){try{" + installStyleJs("__coara_custom_theme", combined) + "}catch(e){}})();";
                cachedScriptKey = key;
                cachedScript = script;
            }
        }
        try {
            webView.evaluateJavascript(script, null);
            synchronized (START_PAGE_THEMED) {
                START_PAGE_THEMED.put(webView, key);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onPageStarted(BrowserView webView) {
        if (webView == null) {
            return;
        }
        synchronized (START_PAGE_THEMED) {
            START_PAGE_THEMED.remove(webView);
        }
        synchronized (PAGE_THEMED) {
            PAGE_THEMED.remove(webView);
        }
    }

    private static String siteKeyFor(String host) {
        if (host == null || host.isEmpty()) return null;
        String h = host.toLowerCase(Locale.US);
        if (h.startsWith("www.")) h = h.substring(4);
        if (h.matches("(m\\.|news\\.|images\\.|scholar\\.|books\\.)?google\\.[a-z.]{2,8}")) return "google";
        if (h.equals("bing.com") || h.endsWith(".bing.com")) return "bing";
        if (h.equals("yahoo.co.jp") || h.endsWith(".yahoo.co.jp") || h.equals("yahoo.com") || h.endsWith(".yahoo.com")) return "yahoo";
        if (h.equals("duckduckgo.com") || h.endsWith(".duckduckgo.com")) return "duckduckgo";
        if (h.equals("search.brave.com")) return "brave";
        if (h.equals("startpage.com") || h.endsWith(".startpage.com")) return "startpage";
        return null;
    }

    private static String matchPageSite(ThemeInfo theme, String host) {
        if (theme.pageSites.isEmpty() || host == null || host.isEmpty()) return null;
        String h = host.toLowerCase(Locale.US);
        String preset = siteKeyFor(h);
        for (String site : theme.pageSites) {
            if ("*".equals(site)) return preset != null ? preset : "web";
            if (preset != null && preset.equals(site)) return preset;
            if (site.indexOf('.') > 0 && (h.equals(site) || h.endsWith("." + site))) return site;
        }
        return null;
    }

    public static void applyPageTheme(BrowserView webView, Context context, String url) {
        if (webView == null || context == null || url == null) {
            return;
        }
        boolean web = url.startsWith("https://") || url.startsWith("http://");
        ThemeInfo theme = web ? getActiveTheme(context) : null;
        String site = null;
        if (theme != null && !theme.pageCssEntry.isEmpty()) {
            String host = null;
            try {
                host = android.net.Uri.parse(url).getHost();
            } catch (Throwable ignored) {
            }
            site = matchPageSite(theme, host);
        }
        if (site == null) {
            String previous;
            synchronized (PAGE_THEMED) {
                previous = PAGE_THEMED.remove(webView);
            }
            if (previous != null) {
                try {
                    webView.evaluateJavascript(pageRemoveScript(), null);
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        String key = theme.fileName + ":" + theme.modifiedAt + ":" + site;
        synchronized (PAGE_THEMED) {
            if (key.equals(PAGE_THEMED.get(webView))) {
                return;
            }
        }
        String script = pageScript(context, theme, site);
        if (script == null) {
            return;
        }
        try {
            webView.evaluateJavascript(script, null);
            synchronized (PAGE_THEMED) {
                PAGE_THEMED.put(webView, key);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String pageRemoveScript() {
        return "(function(){try{var d=document.documentElement;" + removeStyleJs(PAGE_STYLE_ID) +
                "d.removeAttribute('data-coara-site');d.removeAttribute('data-coara-tone');}catch(e){}})();";
    }

    private static String pageScript(Context context, ThemeInfo theme, String site) {
        String key = theme.fileName + ":" + theme.modifiedAt + ":" + site;
        synchronized (PAGE_SCRIPT_CACHE) {
            String cached = PAGE_SCRIPT_CACHE.get(key);
            if (cached != null) {
                return cached.isEmpty() ? null : cached;
            }
        }
        String css = readPageCss(context, theme);
        String script = "";
        if (css != null && !css.isEmpty()) {
            String tone = isLight(theme.palette.background) ? "light" : "dark";
            String combined = buildPagePaletteCss(theme.palette, tone) + css;
            String version = Integer.toHexString(combined.hashCode());
            String guard = "light".equals(tone)
                    ? "if(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches){" + removeStyleJs(PAGE_STYLE_ID) + "d.removeAttribute('data-coara-site');d.removeAttribute('data-coara-tone');return;}"
                    : "";
            script = "(function(){try{var d=document.documentElement;if(!d)return;" + guard +
                    "d.setAttribute('data-coara-site'," + JSONObject.quote(site) + ");d.setAttribute('data-coara-tone','" + tone + "');" +
                    installStyleJs(PAGE_STYLE_ID, combined) + "}catch(e){}})();";
        }
        synchronized (PAGE_SCRIPT_CACHE) {
            PAGE_SCRIPT_CACHE.put(key, script);
        }
        return script.isEmpty() ? null : script;
    }

    private static String readPageCss(Context context, ThemeInfo theme) {
        String cacheKey = theme.fileName + ":" + theme.modifiedAt;
        synchronized (PAGE_SCRIPT_CACHE) {
            String cached = PAGE_CSS_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        String css = "";
        try (ZipFile zip = new ZipFile(getThemeFile(context, theme))) {
            ZipEntry entry = zip.getEntry(theme.pageCssEntry);
            if (entry != null && !entry.isDirectory() && entry.getSize() <= MAX_PAGE_CSS_BYTES) {
                try (InputStream in = zip.getInputStream(entry)) {
                    css = sanitizePageCss(new String(readLimited(in, MAX_PAGE_CSS_BYTES), StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
            css = "";
        }
        synchronized (PAGE_SCRIPT_CACHE) {
            PAGE_CSS_CACHE.put(cacheKey, css);
        }
        return css;
    }

    private static String sanitizePageCss(String css) {
        if (css == null || css.isEmpty()) {
            return "";
        }
        css = css.replace("\u0000", "").replace("\ufeff", "");
        css = css.replaceAll("(?is)@import[^;]*;", "");
        css = css.replaceAll("(?i)javascript:", "");
        css = css.replaceAll("(?i)expression\\s*\\(", "(");
        css = css.replaceAll("(?is)url\\(\\s*['\"]?\\s*(https?:|//|file:|content:|ftp:)[^)]*\\)", "none");
        css = css.replaceAll("(?i)</style", "<\\\\/style");
        return css;
    }

    private static String buildPagePaletteCss(ThemePalette p, String tone) {
        return ":root{" +
                "--coara-primary:" + toHex(p.primary) + ";" +
                "--coara-primary-variant:" + toHex(p.primaryVariant) + ";" +
                "--coara-on-primary:" + toHex(p.onPrimary) + ";" +
                "--coara-secondary:" + toHex(p.secondary) + ";" +
                "--coara-background:" + toHex(p.background) + ";" +
                "--coara-surface:" + toHex(p.surface) + ";" +
                "--coara-text-primary:" + toHex(p.textPrimary) + ";" +
                "--coara-text-secondary:" + toHex(p.textSecondary) + ";" +
                "--coara-urlbar:" + toHex(p.urlBar) + ";" +
                "--coara-accent:" + toHex(p.accent) + ";" +
                "--coara-button:" + toHex(p.buttonBackground) + ";" +
                "--coara-button-text:" + toHex(p.buttonText) + ";" +
                "--coara-stroke:" + toHex(p.stroke) + ";" +
                "--coara-danger:" + toHex(p.danger) + ";" +
                "color-scheme:" + tone + ";}";
    }

    private static List<String> parsePageSites(JSONObject obj) {
        org.json.JSONArray array = obj.optJSONArray("pageSites");
        ArrayList<String> sites = new ArrayList<>();
        if (array == null) {
            for (String site : DEFAULT_PAGE_SITES) sites.add(site);
            return sites;
        }
        for (int i = 0; i < array.length() && sites.size() < MAX_PAGE_SITES; i++) {
            String value = array.optString(i, "").trim().toLowerCase(Locale.US);
            if (value.isEmpty() || value.length() > 64 || !value.matches("[a-z0-9.*-]+")) continue;
            if (!sites.contains(value)) sites.add(value);
        }
        return sites;
    }

    private static void removeStartPageStyle(BrowserView webView) {
        try {
            webView.evaluateJavascript("(function(){try{" + removeStyleJs("__coara_custom_theme") + "}catch(e){}})();", null);
        } catch (Throwable ignored) {
        }
    }

    private static String readStartPageCss(Context context, ThemeInfo theme) {
        if (theme == null || theme.startPageCssEntry == null || theme.startPageCssEntry.isEmpty()) {
            return DEFAULT_START_CSS;
        }
        synchronized (START_CSS_CACHE) {
            String cached = START_CSS_CACHE.get(theme.fileName);
            if (cached != null) {
                return cached;
            }
        }
        File file = getThemeFile(context, theme);
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry(theme.startPageCssEntry);
            if (entry == null || entry.isDirectory() || entry.getSize() > MAX_CSS_BYTES) {
                synchronized (START_CSS_CACHE) {
                    START_CSS_CACHE.put(theme.fileName, DEFAULT_START_CSS);
                }
                return DEFAULT_START_CSS;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = readLimited(in, MAX_CSS_BYTES);
                String css = sanitizeCss(new String(bytes, StandardCharsets.UTF_8));
                synchronized (START_CSS_CACHE) {
                    START_CSS_CACHE.put(theme.fileName, css);
                }
                return css;
            }
        } catch (Throwable ignored) {
            return DEFAULT_START_CSS;
        }
    }

    private static String sanitizeCss(String css) {
        if (css == null || css.isEmpty()) {
            return DEFAULT_START_CSS;
        }
        String lower = css.toLowerCase(Locale.US);
        if (lower.contains("@import") || lower.contains("<script") || lower.contains("javascript:")) {
            css = css.replaceAll("(?is)@import[^;]+;", "");
            css = css.replaceAll("(?i)javascript:", "");
        }
        return css.replace("\u0000", "");
    }

    private static String buildStartPagePaletteCss(ThemePalette p) {
        return ":root{" +
                "--coara-primary:" + toHex(p.primary) + ";" +
                "--coara-primary-variant:" + toHex(p.primaryVariant) + ";" +
                "--coara-on-primary:" + toHex(p.onPrimary) + ";" +
                "--coara-secondary:" + toHex(p.secondary) + ";" +
                "--coara-on-secondary:" + toHex(p.onSecondary) + ";" +
                "--coara-background:" + toHex(p.background) + ";" +
                "--coara-surface:" + toHex(p.surface) + ";" +
                "--coara-text-primary:" + toHex(p.textPrimary) + ";" +
                "--coara-text-secondary:" + toHex(p.textSecondary) + ";" +
                "--coara-urlbar:" + toHex(p.urlBar) + ";" +
                "--coara-accent:" + toHex(p.accent) + ";" +
                "--coara-button:" + toHex(p.buttonBackground) + ";" +
                "--coara-button-text:" + toHex(p.buttonText) + ";" +
                "--coara-stroke:" + toHex(p.stroke) + ";" +
                "--coara-danger:" + toHex(p.danger) + ";" +
                "}" +
                "html,body{background:var(--coara-background)!important;color:var(--coara-text-primary)!important;}" +
                "body{font-family:inherit!important;}" +
                ".container{color:var(--coara-text-primary)!important;}" +
                "#logo{color:var(--coara-text-primary)!important;}" +
                "input[type='text'],#searchInput{background:var(--coara-urlbar)!important;color:var(--coara-text-primary)!important;border-color:var(--coara-stroke)!important;}" +
                "input[type='text']::placeholder,#searchInput::placeholder{color:var(--coara-text-secondary)!important;}" +
                "#goButton,#engineToggle,#bgSelector,#bgClear{background:var(--coara-button)!important;color:var(--coara-button-text)!important;border-color:var(--coara-stroke)!important;}" +
                "#engineSelector{background:var(--coara-surface)!important;color:var(--coara-text-primary)!important;border-color:var(--coara-stroke)!important;}" +
                "#engineSelector button{background:var(--coara-urlbar)!important;border-color:var(--coara-stroke)!important;color:var(--coara-text-primary)!important;}" +
                "#notification{background:var(--coara-primary)!important;color:var(--coara-on-primary)!important;}";
    }

    private static void applyToView(View view, ThemePalette p, boolean includeRoot, boolean insideUrl, boolean insidePrimary, boolean layoutApplied) {
        if (view == null) {
            return;
        }
        if (view instanceof BrowserView) {
            return;
        }
        saveOriginal(view);
        String idName = getIdName(view);
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            int background = idName.contains("urlcontainer") ? p.urlBar : p.surface;
            try { card.setCardBackgroundColor(ColorStateList.valueOf(background)); } catch (Throwable ignored) {}
            try { card.setStrokeColor(p.stroke); } catch (Throwable ignored) {}
        }
        if (insideUrl && !layoutApplied) {
            try {
                Drawable original = view.getBackground();
                if (original != null) {
                    view.setBackgroundColor(p.urlBar);
                }
            } catch (Throwable ignored) {
            }
        }
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            boolean secondary = idName.contains("newtab") || idName.contains("outline");
            try { button.setBackgroundTintList(ColorStateList.valueOf(secondary ? chipBackgroundColor(p) : p.buttonBackground)); } catch (Throwable ignored) {}
            try { button.setTextColor(secondary ? chipTextColor(p) : p.buttonText); } catch (Throwable ignored) {}
            try { button.setStrokeColor(ColorStateList.valueOf(p.stroke)); } catch (Throwable ignored) {}
        } else if (view instanceof Button) {
            Button button = (Button) view;
            try { button.setBackgroundTintList(ColorStateList.valueOf(p.buttonBackground)); } catch (Throwable ignored) {}
            try { button.setTextColor(p.buttonText); } catch (Throwable ignored) {}
        }
        if (view instanceof TextInputLayout) {
            try {
                ((TextInputLayout) view).setBoxBackgroundColor(p.urlBar);
                ((TextInputLayout) view).setBoxStrokeColor(p.stroke);
            } catch (Throwable ignored) {
            }
        }
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            try { edit.setTextColor(p.textPrimary); } catch (Throwable ignored) {}
            try { edit.setHintTextColor(p.textSecondary); } catch (Throwable ignored) {}
        } else if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (idName.contains("status") && idName.contains("plugin")) {
                try { text.setTextColor(p.danger); } catch (Throwable ignored) {}
            } else if (idName.contains("description") || idName.contains("meta") || idName.contains("hint")
                    || idName.contains("secondary") || idName.contains("subsummary")) {
                try { text.setTextColor(p.textSecondary); } catch (Throwable ignored) {}
            } else if (idName.equals("tabcounttextview")) {
                try { text.setTextColor(chipTextColor(p)); } catch (Throwable ignored) {}
            } else if (!(view instanceof Button)) {
                if (insidePrimary) {
                    try { text.setTextColor(p.onPrimary); } catch (Throwable ignored) {}
                } else {
                    try { text.setTextColor(p.textPrimary); } catch (Throwable ignored) {}
                }
            }
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            boolean iconLike = idName.contains("icon") || idName.contains("menu") || idName.contains("arrow")
                    || idName.contains("back") || idName.contains("forward") || idName.contains("close")
                    || idName.contains("copy") || idName.contains("bookmark") || idName.contains("search")
                    || idName.contains("refresh") || idName.contains("theme");
            if (iconLike && !idName.contains("backgroundimage") && !idName.contains("favicon")) {
                int tint = insidePrimary && !insideUrl ? p.onPrimary : p.textPrimary;
                try { image.setColorFilter(tint); } catch (Throwable ignored) {}
            }
        }
        if (view instanceof ProgressBar) {
            ProgressBar progress = (ProgressBar) view;
            try { progress.setProgressTintList(ColorStateList.valueOf(p.primary)); } catch (Throwable ignored) {}
            try { progress.setIndeterminateTintList(ColorStateList.valueOf(p.primary)); } catch (Throwable ignored) {}
        }
        if (view instanceof SeekBar) {
            SeekBar seek = (SeekBar) view;
            try { seek.setProgressTintList(ColorStateList.valueOf(p.primary)); } catch (Throwable ignored) {}
            try { seek.setThumbTintList(ColorStateList.valueOf(p.primary)); } catch (Throwable ignored) {}
        }
        if (view instanceof SwitchCompat) {
            SwitchCompat sw = (SwitchCompat) view;
            int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
            int[] thumb = new int[]{p.primary, p.stroke};
            int[] track = new int[]{withAlpha(p.primary, 110), withAlpha(p.stroke, 140)};
            try { sw.setThumbTintList(new ColorStateList(states, thumb)); } catch (Throwable ignored) {}
            try { sw.setTrackTintList(new ColorStateList(states, track)); } catch (Throwable ignored) {}
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (includeRoot || view != group.getRootView()) {
                if (shouldApplyBackground(view, idName) && !(layoutApplied && idName.contains("topappbar"))) {
                    int color = backgroundForView(view, idName, p);
                    try { view.setBackgroundColor(color); } catch (Throwable ignored) {}
                }
            }
            boolean childUrl = insideUrl || idName.contains("urlcontainer");
            boolean childPrimary = insidePrimary || idName.contains("topappbar");
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToView(group.getChildAt(i), p, false, childUrl, childPrimary, layoutApplied);
            }
        } else if (shouldApplyBackground(view, idName)) {
            try { view.setBackgroundColor(backgroundForView(view, idName, p)); } catch (Throwable ignored) {}
        }
    }

    private static boolean shouldApplyBackground(View view, String idName) {
        if (view instanceof ImageView || view instanceof TextView || view instanceof EditText || view instanceof Button
                || view instanceof MaterialCardView || view instanceof MaterialButton || view instanceof ProgressBar
                || view instanceof SeekBar || view instanceof SwitchCompat) {
            return false;
        }
        return idName.contains("activity_") || idName.contains("rootlayout") || idName.contains("browservisualcontainer")
                || idName.contains("pluginlist") || idName.contains("recyclerview") || idName.contains("listview")
                || idName.contains("findinpagebar") || idName.contains("item_container") || idName.contains("toplayout")
                || idName.contains("topappbar") || idName.contains("themeactivityroot")
                || idName.contains("searchoverlay");
    }

    private static int backgroundForView(View view, String idName, ThemePalette p) {
        if (idName.contains("topappbar")) return p.primary;
        if (idName.contains("urlcontainer")) return p.urlBar;
        if (idName.contains("browservisualcontainer") || idName.contains("webviewcontainer")) return Color.TRANSPARENT;
        if (idName.contains("item_container") || idName.contains("searchoverlay")) return p.surface;
        return p.background;
    }



    private static void saveOriginal(View view) {
        synchronized (ORIGINAL_VIEWS) {
            if (ORIGINAL_VIEWS.containsKey(view)) return;
            OriginalViewState state = new OriginalViewState();
            state.background = view.getBackground();
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                state.textColors = text.getTextColors();
                state.hintColors = text.getHintTextColors();
            }
            if (view instanceof ImageView) {
                state.imageColorFilter = ((ImageView) view).getColorFilter();
            }
            if (view instanceof android.widget.ProgressBar) {
                android.widget.ProgressBar progress = (android.widget.ProgressBar) view;
                state.progressTint = progress.getProgressTintList();
                state.indeterminateTint = progress.getIndeterminateTintList();
            }
            if (view instanceof SeekBar) {
                state.seekProgressTint = ((SeekBar) view).getProgressTintList();
                state.seekThumbTint = ((SeekBar) view).getThumbTintList();
            }
            if (view instanceof SwitchCompat) {
                SwitchCompat sw = (SwitchCompat) view;
                state.switchThumbTint = sw.getThumbTintList();
                state.switchTrackTint = sw.getTrackTintList();
            }
            if (view instanceof MaterialButton) {
                MaterialButton button = (MaterialButton) view;
                state.buttonBackgroundTint = button.getBackgroundTintList();
                state.buttonStrokeColor = button.getStrokeColor();
            } else if (view instanceof Button) {
                state.buttonBackgroundTint = ((Button) view).getBackgroundTintList();
            }
            if (view instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) view;
                state.cardBackgroundColor = card.getCardBackgroundColor();
                state.cardStrokeColor = ColorStateList.valueOf(card.getStrokeColor());
            }
            ORIGINAL_VIEWS.put(view, state);
        }
    }

    private static void restoreView(View view) {
        if (view == null) return;
        OriginalViewState state;
        synchronized (ORIGINAL_VIEWS) {
            state = ORIGINAL_VIEWS.remove(view);
        }
        if (state != null) {
            try { view.setBackground(state.background); } catch (Throwable ignored) {}
            if (view instanceof TextView) {
                try { ((TextView) view).setTextColor(state.textColors); } catch (Throwable ignored) {}
                try { ((TextView) view).setHintTextColor(state.hintColors); } catch (Throwable ignored) {}
            }
            if (view instanceof ImageView) {
                try { ((ImageView) view).setColorFilter(state.imageColorFilter); } catch (Throwable ignored) {}
            }
            if (view instanceof android.widget.ProgressBar) {
                try { ((android.widget.ProgressBar) view).setProgressTintList(state.progressTint); } catch (Throwable ignored) {}
                try { ((android.widget.ProgressBar) view).setIndeterminateTintList(state.indeterminateTint); } catch (Throwable ignored) {}
            }
            if (view instanceof SeekBar) {
                try { ((SeekBar) view).setProgressTintList(state.seekProgressTint); } catch (Throwable ignored) {}
                try { ((SeekBar) view).setThumbTintList(state.seekThumbTint); } catch (Throwable ignored) {}
            }
            if (view instanceof SwitchCompat) {
                try { ((SwitchCompat) view).setThumbTintList(state.switchThumbTint); } catch (Throwable ignored) {}
                try { ((SwitchCompat) view).setTrackTintList(state.switchTrackTint); } catch (Throwable ignored) {}
            }
            if (view instanceof MaterialButton) {
                MaterialButton button = (MaterialButton) view;
                try { button.setBackgroundTintList(state.buttonBackgroundTint); } catch (Throwable ignored) {}
                try { button.setStrokeColor(state.buttonStrokeColor); } catch (Throwable ignored) {}
            } else if (view instanceof Button) {
                try { ((Button) view).setBackgroundTintList(state.buttonBackgroundTint); } catch (Throwable ignored) {}
            }
            if (view instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) view;
                try { card.setCardBackgroundColor(state.cardBackgroundColor); } catch (Throwable ignored) {}
                try { card.setStrokeColor(state.cardStrokeColor); } catch (Throwable ignored) {}
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) restoreView(group.getChildAt(i));
        }
    }

    private static final class OriginalViewState {
        Drawable background;
        ColorStateList textColors;
        ColorStateList hintColors;
        android.graphics.ColorFilter imageColorFilter;
        ColorStateList progressTint;
        ColorStateList indeterminateTint;
        ColorStateList seekProgressTint;
        ColorStateList seekThumbTint;
        ColorStateList switchThumbTint;
        ColorStateList switchTrackTint;
        ColorStateList buttonBackgroundTint;
        ColorStateList buttonStrokeColor;
        ColorStateList cardBackgroundColor;
        ColorStateList cardStrokeColor;
    }

    private static final class OriginalWindowState {
        final int statusBarColor;
        final int navigationBarColor;
        final int systemUiVisibility;
        OriginalWindowState(int statusBarColor, int navigationBarColor, int systemUiVisibility) {
            this.statusBarColor = statusBarColor;
            this.navigationBarColor = navigationBarColor;
            this.systemUiVisibility = systemUiVisibility;
        }
    }

    private static String getIdName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "";
        String cached = ID_NAME_CACHE.get(id);
        if (cached != null) return cached;
        String name;
        try {
            name = view.getResources().getResourceEntryName(id).toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            name = "";
        }
        ID_NAME_CACHE.put(id, name);
        return name;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static boolean isLight(int color) {
        double lum = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
        return lum > 160;
    }

    private static String toHex(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private static File getThemeDir(Context context) {
        File dir = new File(context.getFilesDir(), THEME_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File getThemeFile(Context context, ThemeInfo theme) {
        return new File(getThemeDir(context), theme.fileName);
    }

    private static boolean isSafeThemePath(File file) {
        if (file == null || !file.isFile()) return false;
        try {
            File dir = file.getParentFile();
            if (dir == null) return false;
            String parent = dir.getCanonicalPath();
            String actual = file.getCanonicalPath();
            if (!actual.startsWith(parent + File.separator)) return false;
            return file.length() > 0 && file.length() <= MAX_THEME_FILE_BYTES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSafeThemeFile(File file) {
        return isSafeThemePath(file) && file.getName().endsWith(".zip") && !file.getName().startsWith(".theme_import_");
    }

    private static ThemeInfo readTheme(File file) throws Exception {
        if (!isSafeThemePath(file)) throw new IllegalArgumentException("無効なテーマファイル");
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.size() <= 0 || zip.size() > MAX_ENTRIES) throw new IllegalArgumentException("テーマ内のファイル数が多すぎます");
            long totalEntryBytes = 0L;
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null || manifestEntry.isDirectory() || manifestEntry.getSize() > MAX_MANIFEST_BYTES) {
                throw new IllegalArgumentException("manifest.jsonがありません");
            }
            Set<String> names = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (!names.add(name) || name.startsWith("/") || name.contains("../") || name.contains("\\")) {
                    throw new IllegalArgumentException("安全でないテーマZIPです");
                }
                if (!e.isDirectory() && e.getSize() > 0) {
                    totalEntryBytes += e.getSize();
                    if (e.getSize() > MAX_THEME_FILE_BYTES || totalEntryBytes > MAX_TOTAL_ENTRY_BYTES) {
                        throw new IllegalArgumentException("テーマ内データが大きすぎます");
                    }
                }
            }
            String manifest;
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                manifest = new String(readLimited(in, MAX_MANIFEST_BYTES), StandardCharsets.UTF_8);
            }
            if (manifest.startsWith("\ufeff")) {
                manifest = manifest.substring(1);
            }
            JSONObject obj = new JSONObject(manifest);
            String id = requiredText(obj, "id", 48);
            String name = requiredText(obj, "name", 96);
            String version = text(obj, "version", "1.0", 32);
            String author = text(obj, "author", "", 96);
            String description = text(obj, "description", "", 280);
            if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("テーマIDが不正です");
            JSONObject colors = obj.optJSONObject("colors");
            JSONObject layoutObject = obj.optJSONObject("layout");
            JSONObject menuObject = obj.optJSONObject("menu");
            boolean layoutEnabled = layoutObject != null;
            ThemePalette defaults = ThemePalette.defaultPalette();
            ThemePalette palette = new ThemePalette(
                    parseColor(colors, "primary", defaults.primary),
                    parseColor(colors, "primaryVariant", defaults.primaryVariant),
                    parseColor(colors, "onPrimary", defaults.onPrimary),
                    parseColor(colors, "secondary", defaults.secondary),
                    parseColor(colors, "onSecondary", defaults.onSecondary),
                    parseColor(colors, "background", defaults.background),
                    parseColor(colors, "surface", defaults.surface),
                    parseColor(colors, "onSurface", defaults.onSurface),
                    parseColor(colors, "textPrimary", defaults.textPrimary),
                    parseColor(colors, "textSecondary", defaults.textSecondary),
                    parseColor(colors, "urlBar", defaults.urlBar),
                    parseColor(colors, "accent", defaults.accent),
                    parseColor(colors, "buttonBackground", defaults.buttonBackground),
                    parseColor(colors, "buttonText", defaults.buttonText),
                    parseColor(colors, "stroke", defaults.stroke),
                    parseColor(colors, "danger", defaults.danger));
            String cssEntry = text(obj, "startPageCss", "", 120);
            String pageCssEntry = text(obj, "pageCss", "", 120);
            List<String> pageSites = pageCssEntry.isEmpty() ? java.util.Collections.<String>emptyList() : parsePageSites(obj);
            if (!pageCssEntry.isEmpty()) {
                ZipEntry pageCss = zip.getEntry(pageCssEntry);
                if (pageCssEntry.startsWith("/") || pageCssEntry.contains("../") || pageCssEntry.contains("\\")
                        || pageCss == null || pageCss.isDirectory() || pageCss.getSize() > MAX_PAGE_CSS_BYTES) {
                    throw new IllegalArgumentException("pageCssが不正です");
                }
            }
            BrowserThemeLayoutController.LayoutSpec layout = BrowserThemeLayoutController.LayoutSpec.fromJson(layoutObject);
            MenuStyle menuStyle = MenuStyle.fromJson(menuObject, palette);
            if (!cssEntry.isEmpty()) {
                ZipEntry css = zip.getEntry(cssEntry);
                if (css == null || css.isDirectory() || css.getSize() > MAX_CSS_BYTES) {
                    throw new IllegalArgumentException("startPageCssが不正です");
                }
                if (cssEntry.startsWith("/") || cssEntry.contains("../") || cssEntry.contains("\\")) {
                    throw new IllegalArgumentException("startPageCssのパスが不正です");
                }
            }
            return new ThemeInfo(file.getName(), id, name, version, author, description, palette,
                    cssEntry, pageCssEntry, pageSites, layout, menuStyle, layoutEnabled, file.length(), file.lastModified());
        }
    }

    private static int parseColor(JSONObject object, String key, int fallback) {
        if (object == null) return fallback;
        String value = object.optString(key, "");
        if (!value.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            return fallback;
        }
        try {
            if (value.length() == 9) {
                int rgb = Color.parseColor("#" + value.substring(1, 7));
                int alpha = Integer.parseInt(value.substring(7, 9), 16);
                return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
            }
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String requiredText(JSONObject obj, String key, int max) {
        String value = obj.optString(key, "").trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(key + "が不正です");
        return value;
    }

    private static String text(JSONObject obj, String key, String fallback, int max) {
        String value = obj.optString(key, fallback);
        if (value == null || value.length() > max) return fallback;
        return value.trim();
    }

    private static byte[] readLimited(InputStream in, long max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long count = 0L;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            count += n;
            if (count > max) throw new IllegalArgumentException("データが大きすぎます");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String sanitizeFilePart(String value) {
        if (value == null) return "";
        String s = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static boolean shouldSkip(Activity activity) {
        return activity instanceof com.coara.browser2.SecretActivity
                || activity instanceof com.coara.browser2.TouchpadActivity
                || activity instanceof com.coara.browser2.SecretTouchpadActivity
                || activity instanceof com.coara.browser2.DownloadOpenActivity;
    }

}
