package com.coara.browser2.util;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BrowserThemeLayoutController {
    private static final Map<Activity, State> STATES = new WeakHashMap<>();
    private static String lastError;

    public static String lastError() {
        return lastError;
    }

    private static void record(String step, Throwable t) {
        lastError = step + ": " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " " + t.getMessage());
        try {
            android.util.Log.w("CoaraTheme", lastError, t);
        } catch (Throwable ignored) {
        }
    }

    private interface Step {
        void run() throws Throwable;
    }

    private static void guarded(String name, Step step) {
        try {
            step.run();
        } catch (Throwable t) {
            record(name, t);
        }
    }

    private BrowserThemeLayoutController() {
    }

    public static final class LayoutSpec {
        public final String toolbarPosition;
        public final String toolbarStyle;
        public final int toolbarHeightDp;
        public final int toolbarRadiusDp;
        public final int toolbarMarginHorizontalDp;
        public final int toolbarMarginTopDp;
        public final int toolbarMarginBottomDp;
        public final int toolbarPaddingHorizontalDp;
        public final int toolbarPaddingVerticalDp;
        public final int toolbarElevationDp;
        public final String urlBarStyle;
        public final int urlBarHeightDp;
        public final int urlBarRadiusDp;
        public final int urlBarStrokeWidthDp;
        public final int urlBarMarginStartDp;
        public final int urlBarMarginEndDp;
        public final float urlBarWeight;
        public final int urlTextSizeSp;
        public final int urlHorizontalPaddingDp;
        public final String goMode;
        public final int goWidthDp;
        public final String newTabStyle;
        public final int newTabSizeDp;
        public final String tabCountStyle;
        public final int tabCountSizeDp;
        public final String menuPosition;
        public final int menuOffsetDp;
        public final List<String> controlOrder;

        private LayoutSpec(
                String toolbarPosition,
                String toolbarStyle,
                int toolbarHeightDp,
                int toolbarRadiusDp,
                int toolbarMarginHorizontalDp,
                int toolbarMarginTopDp,
                int toolbarMarginBottomDp,
                int toolbarPaddingHorizontalDp,
                int toolbarPaddingVerticalDp,
                int toolbarElevationDp,
                String urlBarStyle,
                int urlBarHeightDp,
                int urlBarRadiusDp,
                int urlBarStrokeWidthDp,
                int urlBarMarginStartDp,
                int urlBarMarginEndDp,
                float urlBarWeight,
                int urlTextSizeSp,
                int urlHorizontalPaddingDp,
                String goMode,
                int goWidthDp,
                String newTabStyle,
                int newTabSizeDp,
                String tabCountStyle,
                int tabCountSizeDp,
                String menuPosition,
                int menuOffsetDp,
                List<String> controlOrder) {
            this.toolbarPosition = toolbarPosition;
            this.toolbarStyle = toolbarStyle;
            this.toolbarHeightDp = toolbarHeightDp;
            this.toolbarRadiusDp = toolbarRadiusDp;
            this.toolbarMarginHorizontalDp = toolbarMarginHorizontalDp;
            this.toolbarMarginTopDp = toolbarMarginTopDp;
            this.toolbarMarginBottomDp = toolbarMarginBottomDp;
            this.toolbarPaddingHorizontalDp = toolbarPaddingHorizontalDp;
            this.toolbarPaddingVerticalDp = toolbarPaddingVerticalDp;
            this.toolbarElevationDp = toolbarElevationDp;
            this.urlBarStyle = urlBarStyle;
            this.urlBarHeightDp = urlBarHeightDp;
            this.urlBarRadiusDp = urlBarRadiusDp;
            this.urlBarStrokeWidthDp = urlBarStrokeWidthDp;
            this.urlBarMarginStartDp = urlBarMarginStartDp;
            this.urlBarMarginEndDp = urlBarMarginEndDp;
            this.urlBarWeight = urlBarWeight;
            this.urlTextSizeSp = urlTextSizeSp;
            this.urlHorizontalPaddingDp = urlHorizontalPaddingDp;
            this.goMode = goMode;
            this.goWidthDp = goWidthDp;
            this.newTabStyle = newTabStyle;
            this.newTabSizeDp = newTabSizeDp;
            this.tabCountStyle = tabCountStyle;
            this.tabCountSizeDp = tabCountSizeDp;
            this.menuPosition = menuPosition;
            this.menuOffsetDp = menuOffsetDp;
            this.controlOrder = Collections.unmodifiableList(controlOrder);
        }

        public static LayoutSpec defaults() {
            return new LayoutSpec(
                    "top", "flat", 70, 0, 0, 0, 0, 12, 10, 6,
                    "pill", 46, 24, 1, 0, 0, 1f, 15, 14,
                    "hidden", 64, "circle", 47, "circle", 35, "end", 0,
                    Arrays.asList("url", "go", "newtab", "tabs"));
        }

        public static LayoutSpec fromJson(JSONObject object) {
            LayoutSpec d = defaults();
            if (object == null) return d;
            return new LayoutSpec(
                    oneOf(object.optString("toolbarPosition", d.toolbarPosition), "top", "bottom"),
                    oneOf(object.optString("toolbarStyle", d.toolbarStyle), "flat", "floating", "capsule", "rail", "glass"),
                    intValue(object, "toolbarHeightDp", d.toolbarHeightDp, 44, 128),
                    intValue(object, "toolbarRadiusDp", d.toolbarRadiusDp, 0, 64),
                    intValue(object, "toolbarMarginHorizontalDp", d.toolbarMarginHorizontalDp, 0, 48),
                    intValue(object, "toolbarMarginTopDp", d.toolbarMarginTopDp, 0, 48),
                    intValue(object, "toolbarMarginBottomDp", d.toolbarMarginBottomDp, 0, 48),
                    intValue(object, "toolbarPaddingHorizontalDp", d.toolbarPaddingHorizontalDp, 0, 32),
                    intValue(object, "toolbarPaddingVerticalDp", d.toolbarPaddingVerticalDp, 0, 24),
                    intValue(object, "toolbarElevationDp", d.toolbarElevationDp, 0, 24),
                    oneOf(object.optString("urlBarStyle", d.urlBarStyle), "pill", "rounded", "square", "underline", "minimal", "glass"),
                    intValue(object, "urlBarHeightDp", d.urlBarHeightDp, 34, 80),
                    intValue(object, "urlBarRadiusDp", d.urlBarRadiusDp, 0, 40),
                    intValue(object, "urlBarStrokeWidthDp", d.urlBarStrokeWidthDp, 0, 6),
                    intValue(object, "urlBarMarginStartDp", d.urlBarMarginStartDp, 0, 32),
                    intValue(object, "urlBarMarginEndDp", d.urlBarMarginEndDp, 0, 32),
                    floatValue(object, "urlBarWeight", d.urlBarWeight, 0.35f, 1.8f),
                    intValue(object, "urlTextSizeSp", d.urlTextSizeSp, 11, 24),
                    intValue(object, "urlHorizontalPaddingDp", d.urlHorizontalPaddingDp, 4, 28),
                    oneOf(object.optString("goMode", d.goMode), "hidden", "visible", "compact"),
                    intValue(object, "goWidthDp", d.goWidthDp, 40, 96),
                    oneOf(object.optString("newTabStyle", d.newTabStyle), "circle", "square", "pill", "compact"),
                    intValue(object, "newTabSizeDp", d.newTabSizeDp, 32, 64),
                    oneOf(object.optString("tabCountStyle", d.tabCountStyle), "circle", "square", "pill", "compact"),
                    intValue(object, "tabCountSizeDp", d.tabCountSizeDp, 28, 56),
                    oneOf(object.optString("menuPosition", d.menuPosition), "start", "end"),
                    intValue(object, "menuOffsetDp", d.menuOffsetDp, -48, 48),
                    parseOrder(object.optJSONArray("controlOrder"), d.controlOrder));
        }

        public String describe() {
            String position = "bottom".equals(toolbarPosition) ? "下部" : "上部";
            String style;
            if ("floating".equals(toolbarStyle)) style = "フローティング";
            else if ("capsule".equals(toolbarStyle)) style = "カプセル";
            else if ("rail".equals(toolbarStyle)) style = "レール";
            else if ("glass".equals(toolbarStyle)) style = "グラス";
            else style = "フラット";
            return "ネイティブUI: " + position + " / " + style + " / URLバー " + urlBarStyle + " / メニュー " + menuPosition;
        }
    }


    private static LinearLayout findInner(Toolbar toolbar) {
        if (toolbar == null) return null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            ViewGroup group = (ViewGroup) child;
            if (group.findViewById(com.coara.browser2.R.id.urlContainer) != null
                    || group.findViewById(com.coara.browser2.R.id.btnNewTab) != null
                    || group.findViewById(com.coara.browser2.R.id.tabCountTextView) != null) {
                return (LinearLayout) child;
            }
        }
        return null;
    }

    public static void prepare(Activity activity) {
        if (activity == null) return;
        Toolbar toolbar = activity.findViewById(com.coara.browser2.R.id.topAppBar);
        ViewGroup root = toolbar == null || !(toolbar.getParent() instanceof ViewGroup) ? null : (ViewGroup) toolbar.getParent();
        LinearLayout inner = findInner(toolbar);
        if (toolbar == null || root == null || inner == null) return;
        if (!STATES.containsKey(activity)) {
            STATES.put(activity, capture(toolbar, root, inner, activity));
        }
    }

    public static boolean apply(Activity activity, LayoutSpec spec, BrowserThemeManager.ThemePalette palette) {
        if (activity == null || spec == null || palette == null) return false;
        lastError = null;
        try {
        Toolbar toolbar = activity.findViewById(com.coara.browser2.R.id.topAppBar);
        ViewGroup root = toolbar == null || !(toolbar.getParent() instanceof ViewGroup) ? null : (ViewGroup) toolbar.getParent();
        LinearLayout inner = findInner(toolbar);
        if (toolbar == null || root == null || inner == null || !(root instanceof LinearLayout)) return false;
        State state = STATES.get(activity);
        if (state == null) {
            state = capture(toolbar, root, inner, activity);
            STATES.put(activity, state);
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int toolbarHeight = dp(spec.toolbarHeightDp, density);
        if (!(root instanceof LinearLayout)) return false;
        LinearLayout.LayoutParams toolbarLp = ensureLinearParams(toolbar.getLayoutParams(), toolbarHeight);
        toolbarLp.height = toolbarHeight;
        toolbarLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        toolbarLp.weight = 0f;
        toolbarLp.gravity = Gravity.CENTER_HORIZONTAL;
        toolbarLp.setMargins(dp(spec.toolbarMarginHorizontalDp, density), dp(spec.toolbarMarginTopDp, density),
                dp(spec.toolbarMarginHorizontalDp, density), dp(spec.toolbarMarginBottomDp, density));
        toolbar.setLayoutParams(toolbarLp);
        toolbar.setElevation(dp(spec.toolbarElevationDp, density));
        toolbar.setPadding(dp(spec.toolbarPaddingHorizontalDp, density), dp(spec.toolbarPaddingVerticalDp, density),
                dp(spec.toolbarPaddingHorizontalDp, density), dp(spec.toolbarPaddingVerticalDp, density));
        toolbar.setBackground(toolbarBackground(palette, spec, density));
        root.setBackgroundColor(palette.background);
        if ("bottom".equals(spec.toolbarPosition)) {
            int oldIndex = root.indexOfChild(toolbar);
            if (oldIndex >= 0 && oldIndex != root.getChildCount() - 1) {
                root.removeView(toolbar);
                root.addView(toolbar);
            }
        } else {
            int oldIndex = root.indexOfChild(toolbar);
            if (oldIndex > 0) {
                root.removeView(toolbar);
                root.addView(toolbar, 0);
            }
        }
        inner.setOrientation(LinearLayout.HORIZONTAL);
        int horizontalPad = dp(spec.toolbarPaddingHorizontalDp, density);
        inner.setPadding(horizontalPad, dp(spec.toolbarPaddingVerticalDp, density), horizontalPad, dp(spec.toolbarPaddingVerticalDp, density));
        final LinearLayout innerFinal = inner;
        final Toolbar toolbarFinal = toolbar;
        final State stateFinal = state;
        guarded("urlBar", () -> applyUrlBar(activity, innerFinal, spec, palette, density));
        guarded("go", () -> applyGo(activity, innerFinal, spec, palette, density));
        guarded("newTab", () -> applyNewTab(activity, innerFinal, spec, palette, density));
        guarded("tabs", () -> applyTabs(activity, innerFinal, spec, palette, density));
        guarded("order", () -> reorderControls(innerFinal, spec.controlOrder));
        guarded("menu", () -> applyMenu(toolbarFinal, innerFinal, spec, palette, density, stateFinal));
        } catch (Throwable t) {
            record("layout", t);
            restore(activity);
            return false;
        }
        return true;
    }
    public static void release(Activity activity) {
        if (activity != null) {
            STATES.remove(activity);
        }
    }

    public static void restore(Activity activity) {
        State state = STATES.remove(activity);
        if (state == null) return;
        try {
            ViewGroup root = state.toolbarParent;
            if (root != null) {
                root.setBackground(state.rootBackground);
            }
            if (root != null && state.toolbar != null) {
                int index = Math.max(0, Math.min(state.toolbarIndex, root.getChildCount()));
                if (root.indexOfChild(state.toolbar) >= 0) root.removeView(state.toolbar);
                root.addView(state.toolbar, index);
                state.toolbar.setLayoutParams(copyParams(state.toolbarLayoutParams));
                state.toolbar.setBackground(state.toolbarBackground);
                state.toolbar.setElevation(state.toolbarElevation);
                state.toolbar.setPadding(state.toolbarPaddingLeft, state.toolbarPaddingTop, state.toolbarPaddingRight, state.toolbarPaddingBottom);
                if (state.overflowChanged) {
                    android.graphics.drawable.Drawable icon = overflowIcon(state.toolbar.getContext(), controlNormalColor(state.toolbar.getContext()));
                    if (icon != null) state.toolbar.setOverflowIcon(icon);
                }
                if (state.menuListener != null) {
                    state.toolbar.removeOnLayoutChangeListener(state.menuListener);
                    state.menuListener = null;
                }
                restoreMenu(state);
            }
            if (state.inner != null) {
                state.inner.setTranslationX(0f);
                state.inner.setOrientation(state.innerOrientation);
                state.inner.setPadding(state.innerPaddingLeft, state.innerPaddingTop, state.innerPaddingRight, state.innerPaddingBottom);
                state.inner.setBackground(state.innerBackground);
                state.inner.removeAllViews();
                for (View child : state.originalOrder) {
                    state.inner.addView(child);
                }
                for (ViewSnapshot snapshot : state.snapshots) {
                    snapshot.restore();
                }
            }
            for (ViewSnapshot snapshot : state.outerSnapshots) {
                snapshot.restore();
            }
        } catch (Throwable ignored) {
        }
    }

    private static State capture(Toolbar toolbar, ViewGroup root, LinearLayout inner, Activity activity) {
        State state = new State();
        state.rootBackground = root.getBackground();
        state.toolbar = toolbar;
        state.toolbarParent = root;
        state.toolbarIndex = root.indexOfChild(toolbar);
        state.toolbarLayoutParams = copyParams(toolbar.getLayoutParams());
        state.toolbarBackground = toolbar.getBackground();
        state.toolbarElevation = toolbar.getElevation();
        state.toolbarPaddingLeft = toolbar.getPaddingLeft();
        state.toolbarPaddingTop = toolbar.getPaddingTop();
        state.toolbarPaddingRight = toolbar.getPaddingRight();
        state.toolbarPaddingBottom = toolbar.getPaddingBottom();
        state.inner = inner;
        state.innerOrientation = inner.getOrientation();
        state.innerPaddingLeft = inner.getPaddingLeft();
        state.innerPaddingTop = inner.getPaddingTop();
        state.innerPaddingRight = inner.getPaddingRight();
        state.innerPaddingBottom = inner.getPaddingBottom();
        state.innerBackground = inner.getBackground();
        for (int i = 0; i < inner.getChildCount(); i++) {
            View child = inner.getChildAt(i);
            state.originalOrder.add(child);
            state.snapshots.add(new ViewSnapshot(child));
        }
        View go = activity.findViewById(com.coara.browser2.R.id.btnGo);
        View newTab = activity.findViewById(com.coara.browser2.R.id.btnNewTab);
        View tabs = activity.findViewById(com.coara.browser2.R.id.tabCountTextView);
        View favicon = activity.findViewById(com.coara.browser2.R.id.favicon);
        View url = activity.findViewById(com.coara.browser2.R.id.urlContainer);
        View urlChild = url instanceof ViewGroup && ((ViewGroup) url).getChildCount() > 0 ? ((ViewGroup) url).getChildAt(0) : null;
        if (go != null && !containsSnapshot(state.snapshots, go)) state.outerSnapshots.add(new ViewSnapshot(go));
        if (newTab != null && !containsSnapshot(state.snapshots, newTab)) state.outerSnapshots.add(new ViewSnapshot(newTab));
        if (tabs != null && !containsSnapshot(state.snapshots, tabs)) state.outerSnapshots.add(new ViewSnapshot(tabs));
        if (favicon != null && !containsSnapshot(state.snapshots, favicon)) state.outerSnapshots.add(new ViewSnapshot(favicon));
        if (urlChild != null && !containsSnapshot(state.snapshots, urlChild)) state.outerSnapshots.add(new ViewSnapshot(urlChild));
        View urlText = activity.findViewById(com.coara.browser2.R.id.urlEditText);
        if (urlText != null && !containsSnapshot(state.snapshots, urlText)) state.outerSnapshots.add(new ViewSnapshot(urlText));
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView) {
                state.actionMenus.add(new MenuSnapshot(child));
            }
        }
        return state;
    }

    private static boolean containsSnapshot(List<ViewSnapshot> snapshots, View view) {
        for (ViewSnapshot s : snapshots) if (s.view == view) return true;
        return false;
    }

    private static void applyUrlBar(Activity activity, LinearLayout inner, LayoutSpec spec, BrowserThemeManager.ThemePalette palette, float density) {
        MaterialCardView card = activity.findViewById(com.coara.browser2.R.id.urlContainer);
        if (card == null) return;
        int h = dp(spec.urlBarHeightDp, density);
        LinearLayout.LayoutParams lp = ensureLinearParams(card.getLayoutParams(), h);
        lp.width = 0;
        lp.height = h;
        lp.weight = spec.urlBarWeight;
        lp.setMargins(dp(spec.urlBarMarginStartDp, density), 0, dp(spec.urlBarMarginEndDp, density), 0);
        card.setLayoutParams(lp);
        float radius = spec.urlBarRadiusDp > 0 ? dp(spec.urlBarRadiusDp, density) : h / 2f;
        if ("pill".equals(spec.urlBarStyle)) radius = h / 2f;
        if ("square".equals(spec.urlBarStyle) || "underline".equals(spec.urlBarStyle) || "minimal".equals(spec.urlBarStyle)) radius = dp("square".equals(spec.urlBarStyle) ? 8 : 5, density);
        if ("rounded".equals(spec.urlBarStyle)) radius = dp(16, density);
        card.setRadius(radius);
        card.setCardBackgroundColor(ColorStateList.valueOf(palette.urlBar));
        card.setStrokeWidth(dp(spec.urlBarStrokeWidthDp, density));
        card.setStrokeColor(ColorStateList.valueOf(palette.stroke));
        if (card.getChildCount() > 0) {
            View child = card.getChildAt(0);
            child.setPadding(dp(spec.urlHorizontalPaddingDp, density), child.getPaddingTop(), dp(spec.urlHorizontalPaddingDp, density), child.getPaddingBottom());
            child.setBackground(urlBarBackground(palette.urlBar, palette.stroke, radius, spec, density));
        }
        TextView url = activity.findViewById(com.coara.browser2.R.id.urlEditText);
        if (url != null) {
            url.setTextSize(spec.urlTextSizeSp);
            url.setPadding(0, url.getPaddingTop(), 0, url.getPaddingBottom());
        }
    }

    private static void applyGo(Activity activity, LinearLayout inner, LayoutSpec spec, BrowserThemeManager.ThemePalette palette, float density) {
        MaterialButton button = activity.findViewById(com.coara.browser2.R.id.btnGo);
        if (button == null) return;
        if ("hidden".equals(spec.goMode)) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        int h = dp("compact".equals(spec.goMode) ? spec.newTabSizeDp : spec.urlBarHeightDp, density);
        LinearLayout.LayoutParams lp = ensureLinearParams(button.getLayoutParams(), h);
        lp.width = dp("compact".equals(spec.goMode) ? spec.goWidthDp : spec.goWidthDp + 10, density);
        lp.height = h;
        lp.weight = 0f;
        lp.setMargins(dp(8, density), 0, 0, 0);
        button.setLayoutParams(lp);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(h / 2);
        button.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBackground));
        button.setTextColor(palette.buttonText);
        button.setStrokeColor(ColorStateList.valueOf(palette.stroke));
        button.setText("compact".equals(spec.goMode) ? "›" : "Go");
        button.setTextSize("compact".equals(spec.goMode) ? 20 : 13);
    }

    private static void applyNewTab(Activity activity, LinearLayout inner, LayoutSpec spec, BrowserThemeManager.ThemePalette palette, float density) {
        MaterialButton button = activity.findViewById(com.coara.browser2.R.id.btnNewTab);
        if (button == null) return;
        int size = dp(spec.newTabSizeDp, density);
        int width = size;
        int height = size;
        if ("pill".equals(spec.newTabStyle)) {
            width = dp(spec.newTabSizeDp + 14, density);
            height = dp(spec.newTabSizeDp - 6, density);
        }
        if ("compact".equals(spec.newTabStyle)) {
            width = dp(40, density);
            height = dp(40, density);
        }
        LinearLayout.LayoutParams lp = ensureLinearParams(button.getLayoutParams(), height);
        lp.width = width;
        lp.height = height;
        lp.weight = 0f;
        lp.setMargins(dp(8, density), 0, 0, 0);
        button.setLayoutParams(lp);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius("square".equals(spec.newTabStyle) ? dp(10, density) : height / 2);
        button.setBackgroundTintList(ColorStateList.valueOf(BrowserThemeManager.chipBackgroundColor(palette)));
        button.setTextColor(BrowserThemeManager.chipTextColor(palette));
        button.setStrokeColor(ColorStateList.valueOf(palette.stroke));
        button.setTextSize("compact".equals(spec.newTabStyle) ? 15 : 17);
        button.setText("+");
    }

    private static void applyTabs(Activity activity, LinearLayout inner, LayoutSpec spec, BrowserThemeManager.ThemePalette palette, float density) {
        TextView tabs = activity.findViewById(com.coara.browser2.R.id.tabCountTextView);
        if (tabs == null) return;
        int size = dp(spec.tabCountSizeDp, density);
        int width = size;
        int height = size;
        if ("pill".equals(spec.tabCountStyle)) width = dp(spec.tabCountSizeDp + 16, density);
        if ("compact".equals(spec.tabCountStyle)) {
            width = dp(42, density);
            height = dp(30, density);
        }
        LinearLayout.LayoutParams lp = ensureLinearParams(tabs.getLayoutParams(), height);
        lp.width = width;
        lp.height = height;
        lp.weight = 0f;
        lp.setMargins(dp(8, density), 0, 0, 0);
        tabs.setLayoutParams(lp);
        tabs.setGravity(Gravity.CENTER);
        tabs.setBackground(chipBackground(BrowserThemeManager.chipBackgroundColor(palette), palette.stroke, "square".equals(spec.tabCountStyle) ? dp(8, density) : height / 2f, density));
        tabs.setTextColor(BrowserThemeManager.chipTextColor(palette));
    }

    private static void reorderControls(LinearLayout inner, List<String> order) {
        Map<String, View> map = new java.util.HashMap<>();
        View url = find(inner, com.coara.browser2.R.id.urlContainer); if (url != null) map.put("url", url);
        View go = find(inner, com.coara.browser2.R.id.btnGo); if (go != null) map.put("go", go);
        View newTab = find(inner, com.coara.browser2.R.id.btnNewTab); if (newTab != null) map.put("newtab", newTab);
        View tabs = find(inner, com.coara.browser2.R.id.tabCountTextView); if (tabs != null) map.put("tabs", tabs);
        List<View> extras = new ArrayList<>();
        for (int i = 0; i < inner.getChildCount(); i++) {
            View child = inner.getChildAt(i);
            if (!map.containsValue(child)) extras.add(child);
        }
        inner.removeAllViews();
        java.util.HashSet<View> used = new java.util.HashSet<>();
        for (String token : order) {
            View child = map.get(token);
            if (child != null && !used.contains(child)) {
                inner.addView(child);
                used.add(child);
            }
        }
        for (String token : Arrays.asList("url", "go", "newtab", "tabs")) {
            View child = map.get(token);
            if (child != null && !used.contains(child)) {
                inner.addView(child);
                used.add(child);
            }
        }
        for (View child : extras) inner.addView(child);
    }

    private static void applyMenu(Toolbar toolbar, LinearLayout inner, LayoutSpec spec, BrowserThemeManager.ThemePalette palette, float density, State state) {
        android.graphics.drawable.Drawable icon = overflowIcon(toolbar.getContext(), palette.onPrimary);
        if (icon != null) {
            try {
                toolbar.setOverflowIcon(icon);
                state.overflowChanged = true;
            } catch (Throwable ignored) {
            }
        }
        if (state.menuListener != null) {
            toolbar.removeOnLayoutChangeListener(state.menuListener);
        }
        final boolean start = "start".equals(spec.menuPosition);
        final int offset = dp(spec.menuOffsetDp, density);
        final Toolbar bar = toolbar;
        final LinearLayout content = inner;
        state.menuListener = (v, l, t, r, b, ol, ot, or, ob) -> syncMenu(bar, content, start, offset);
        toolbar.addOnLayoutChangeListener(state.menuListener);
        syncMenu(toolbar, inner, start, offset);
    }

    private static void syncMenu(Toolbar toolbar, LinearLayout inner, boolean start, int offset) {
        ActionMenuView menu = null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView) {
                menu = (ActionMenuView) child;
                break;
            }
        }
        if (menu == null || menu.getVisibility() == View.GONE || menu.getWidth() <= 0) {
            if (inner.getTranslationX() != 0f) inner.setTranslationX(0f);
            return;
        }
        float menuTx = offset;
        float innerTx = 0f;
        if (start) {
            View overflow = BrowserMenuController.overflowOf(toolbar);
            int slot = menu.getWidth();
            int overflowLeft = 0;
            if (overflow != null && overflow.getWidth() > 0 && overflow.getWidth() <= menu.getWidth()) {
                slot = overflow.getWidth();
                overflowLeft = overflow.getLeft();
            }
            float origin = Math.max(inner.getLeft(), toolbar.getPaddingLeft());
            menuTx = origin - menu.getLeft() - overflowLeft + offset;
            innerTx = slot;
        }
        boolean changed = false;
        if (menu.getTranslationX() != menuTx) {
            menu.setTranslationX(menuTx);
            changed = true;
        }
        if (inner.getTranslationX() != innerTx) {
            inner.setTranslationX(innerTx);
            changed = true;
        }
        if (changed) BrowserMenuController.realign(toolbar);
    }

    private static android.graphics.drawable.Drawable overflowIcon(android.content.Context context, int color) {
        try {
            android.graphics.drawable.Drawable base = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.coara.browser2.R.drawable.ic_more_vert);
            if (base == null) return null;
            android.graphics.drawable.Drawable icon = androidx.core.graphics.drawable.DrawableCompat.wrap(base.mutate());
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, color);
            return icon;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int controlNormalColor(android.content.Context context) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, tv, true)) {
                if (tv.resourceId != 0) {
                    ColorStateList csl = androidx.appcompat.content.res.AppCompatResources.getColorStateList(context, tv.resourceId);
                    if (csl != null) return csl.getDefaultColor();
                }
                return tv.data;
            }
        } catch (Throwable ignored) {
        }
        return 0xFF5F6368;
    }

    private static android.graphics.drawable.Drawable toolbarBackground(BrowserThemeManager.ThemePalette palette, LayoutSpec spec, float density) {
        int color = palette.primary;
        int adjusted = color;
        if ("glass".equals(spec.toolbarStyle)) adjusted = Color.argb(218, Color.red(color), Color.green(color), Color.blue(color));
        else if ("floating".equals(spec.toolbarStyle)) adjusted = Color.argb(246, Color.red(color), Color.green(color), Color.blue(color));
        float radius = dp(spec.toolbarRadiusDp, density);
        if ("capsule".equals(spec.toolbarStyle)) radius = dp(spec.toolbarHeightDp / 2, density);
        if ("floating".equals(spec.toolbarStyle)) radius = dp(Math.max(16, spec.toolbarRadiusDp), density);
        if ("rail".equals(spec.toolbarStyle)) radius = 0f;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(adjusted);
        drawable.setCornerRadius(radius);
        if ("floating".equals(spec.toolbarStyle) || "glass".equals(spec.toolbarStyle) || "capsule".equals(spec.toolbarStyle)) {
            drawable.setStroke(dp(1, density), withAlpha(palette.stroke, 140));
        }
        if ("rail".equals(spec.toolbarStyle)) {
            GradientDrawable line = new GradientDrawable();
            line.setColor(palette.accent);
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{drawable, line});
            layers.setLayerGravity(1, "bottom".equals(spec.toolbarPosition) ? Gravity.TOP : Gravity.BOTTOM);
            layers.setLayerHeight(1, dp(3, density));
            return layers;
        }
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static GradientDrawable urlBarBackground(int fill, int stroke, float radius, LayoutSpec spec, float density) {
        int color = fill;
        if ("glass".equals(spec.urlBarStyle)) color = Color.argb(210, Color.red(fill), Color.green(fill), Color.blue(fill));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (spec.urlBarStrokeWidthDp > 0 || "underline".equals(spec.urlBarStyle) || "minimal".equals(spec.urlBarStyle)) {
            drawable.setStroke(dp("minimal".equals(spec.urlBarStyle) ? Math.max(1, spec.urlBarStrokeWidthDp) : spec.urlBarStrokeWidthDp, density), stroke);
        }
        return drawable;
    }

    private static GradientDrawable chipBackground(int fill, int stroke, float radius, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1, density), stroke);
        return drawable;
    }

    private static View find(ViewGroup group, int id) {
        return group.findViewById(id);
    }

    private static LinearLayout.LayoutParams ensureLinearParams(ViewGroup.LayoutParams old, int height) {
        if (old instanceof LinearLayout.LayoutParams) return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) old);
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        if (old != null) {
            result.width = old.width;
            result.height = old.height;
        }
        return result;
    }

    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams src = (LinearLayout.LayoutParams) lp;
            LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(src.width, src.height, src.weight);
            out.gravity = src.gravity;
            out.leftMargin = src.leftMargin;
            out.topMargin = src.topMargin;
            out.rightMargin = src.rightMargin;
            out.bottomMargin = src.bottomMargin;
            return out;
        }
        if (lp instanceof Toolbar.LayoutParams) {
            Toolbar.LayoutParams src = (Toolbar.LayoutParams) lp;
            Toolbar.LayoutParams out = new Toolbar.LayoutParams(src.width, src.height);
            out.gravity = src.gravity;
            out.leftMargin = src.leftMargin;
            out.topMargin = src.topMargin;
            out.rightMargin = src.rightMargin;
            out.bottomMargin = src.bottomMargin;
            return out;
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams src = (ViewGroup.MarginLayoutParams) lp;
            ViewGroup.MarginLayoutParams out = new ViewGroup.MarginLayoutParams(src.width, src.height);
            out.leftMargin = src.leftMargin;
            out.topMargin = src.topMargin;
            out.rightMargin = src.rightMargin;
            out.bottomMargin = src.bottomMargin;
            return out;
        }
        return lp == null ? new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new ViewGroup.LayoutParams(lp);
    }

    private static int dp(int value, float density) { return Math.round(value * density); }
    private static int dp(float value, float density) { return Math.round(value * density); }

    private static int intValue(JSONObject object, String key, int fallback, int min, int max) {
        int v = object.optInt(key, fallback);
        return Math.max(min, Math.min(max, v));
    }

    private static float floatValue(JSONObject object, String key, float fallback, float min, float max) {
        double v = object.optDouble(key, fallback);
        return (float) Math.max(min, Math.min(max, v));
    }

    private static String oneOf(String value, String... values) {
        for (String v : values) if (v.equals(value)) return value;
        return values[0];
    }

    private static List<String> parseOrder(JSONArray array, List<String> fallback) {
        if (array == null || array.length() == 0) return new ArrayList<>(fallback);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length() && result.size() < 4; i++) {
            String value = array.optString(i, "").trim().toLowerCase(java.util.Locale.US);
            if (("url".equals(value) || "go".equals(value) || "newtab".equals(value) || "tabs".equals(value)) && !result.contains(value)) {
                result.add(value);
            }
        }
        for (String value : fallback) if (!result.contains(value)) result.add(value);
        return result;
    }

    private static final class State {
        Toolbar toolbar;
        ViewGroup toolbarParent;
        int toolbarIndex;
        ViewGroup.LayoutParams toolbarLayoutParams;
        android.graphics.drawable.Drawable toolbarBackground;
        android.graphics.drawable.Drawable rootBackground;
        boolean overflowChanged;
        View.OnLayoutChangeListener menuListener;
        float toolbarElevation;
        int toolbarPaddingLeft;
        int toolbarPaddingTop;
        int toolbarPaddingRight;
        int toolbarPaddingBottom;
        LinearLayout inner;
        int innerOrientation;
        int innerPaddingLeft;
        int innerPaddingTop;
        int innerPaddingRight;
        int innerPaddingBottom;
        android.graphics.drawable.Drawable innerBackground;
        final List<View> originalOrder = new ArrayList<>();
        final List<ViewSnapshot> snapshots = new ArrayList<>();
        final List<ViewSnapshot> outerSnapshots = new ArrayList<>();
        final List<MenuSnapshot> actionMenus = new ArrayList<>();
    }

    private static final class ViewSnapshot {
        final View view;
        final ViewGroup.LayoutParams layoutParams;
        final android.graphics.drawable.Drawable background;
        final int visibility;
        final float elevation;
        final float translationX;
        final int paddingLeft;
        final int paddingTop;
        final int paddingRight;
        final int paddingBottom;
        final CharSequence text;
        final float textSize;
        final ColorStateList textColors;
        int strokeWidth = -1;
        int strokeColor = 0;
        ColorStateList strokeList;
        int insetTop;
        int insetBottom;
        int minWidth;
        int minHeight;
        float radius = -1f;
        ColorStateList backgroundTint;

        ViewSnapshot(View view) {
            this.view = view;
            this.layoutParams = copyParams(view.getLayoutParams());
            this.background = view.getBackground();
            this.visibility = view.getVisibility();
            this.elevation = view.getElevation();
            this.translationX = view.getTranslationX();
            this.paddingLeft = view.getPaddingLeft();
            this.paddingTop = view.getPaddingTop();
            this.paddingRight = view.getPaddingRight();
            this.paddingBottom = view.getPaddingBottom();
            if (view instanceof TextView) {
                TextView t = (TextView) view;
                this.text = t.getText();
                this.textSize = t.getTextSize();
                this.textColors = t.getTextColors();
            } else {
                this.text = null;
                this.textSize = -1f;
                this.textColors = null;
            }
            if (view instanceof MaterialCardView) {
                MaterialCardView c = (MaterialCardView) view;
                this.strokeWidth = c.getStrokeWidth();
                this.strokeColor = c.getStrokeColor();
                this.radius = c.getRadius();
            } else if (view instanceof MaterialButton) {
                MaterialButton b = (MaterialButton) view;
                this.strokeWidth = b.getStrokeWidth();
                this.strokeList = b.getStrokeColor();
                this.insetTop = b.getInsetTop();
                this.insetBottom = b.getInsetBottom();
                this.minWidth = b.getMinWidth();
                this.minHeight = b.getMinHeight();
                this.radius = b.getCornerRadius();
                this.backgroundTint = b.getBackgroundTintList();
            }
        }

        void restore() {
            try { view.setLayoutParams(copyParams(layoutParams)); } catch (Throwable ignored) {}
            try { view.setBackground(background); } catch (Throwable ignored) {}
            try { view.setVisibility(visibility); } catch (Throwable ignored) {}
            try { view.setElevation(elevation); } catch (Throwable ignored) {}
            try { view.setTranslationX(translationX); } catch (Throwable ignored) {}
            try { view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom); } catch (Throwable ignored) {}
            if (view instanceof TextView) {
                TextView t = (TextView) view;
                try { if (text != null) t.setText(text); } catch (Throwable ignored) {}
                try { if (textSize > 0) t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize); } catch (Throwable ignored) {}
                try { if (textColors != null) t.setTextColor(textColors); } catch (Throwable ignored) {}
            }
            if (view instanceof MaterialCardView) {
                MaterialCardView c = (MaterialCardView) view;
                try { c.setStrokeWidth(strokeWidth); } catch (Throwable ignored) {}
                try { c.setStrokeColor(strokeColor); } catch (Throwable ignored) {}
                try { c.setRadius(radius); } catch (Throwable ignored) {}
            } else if (view instanceof MaterialButton) {
                MaterialButton b = (MaterialButton) view;
                try { b.setStrokeWidth(strokeWidth); } catch (Throwable ignored) {}
                try { b.setStrokeColor(strokeList); } catch (Throwable ignored) {}
                try { b.setInsetTop(insetTop); b.setInsetBottom(insetBottom); } catch (Throwable ignored) {}
                try { b.setMinWidth(minWidth); b.setMinHeight(minHeight); } catch (Throwable ignored) {}
                try { b.setCornerRadius((int) radius); } catch (Throwable ignored) {}
                try { b.setBackgroundTintList(backgroundTint); } catch (Throwable ignored) {}
            }
        }
    }

    private static final class MenuSnapshot {
        final View view;
        final ViewGroup.LayoutParams lp;
        final float translationX;
        MenuSnapshot(View view) {
            this.view = view;
            this.lp = copyParams(view.getLayoutParams());
            this.translationX = view.getTranslationX();
        }
    }

    private static void restoreMenu(State state) {
        if (state.toolbar != null) {
            for (int i = 0; i < state.toolbar.getChildCount(); i++) {
                View child = state.toolbar.getChildAt(i);
                if (child instanceof ActionMenuView && child.getTranslationX() != 0f) {
                    try { child.setTranslationX(0f); } catch (Throwable ignored) {}
                }
            }
        }
        for (MenuSnapshot snapshot : state.actionMenus) {
            try { snapshot.view.setLayoutParams(copyParams(snapshot.lp)); } catch (Throwable ignored) {}
            try { snapshot.view.setTranslationX(snapshot.translationX); } catch (Throwable ignored) {}
        }
    }
}
