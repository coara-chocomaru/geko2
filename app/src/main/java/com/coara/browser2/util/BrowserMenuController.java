package com.coara.browser2.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;

import com.coara.browser2.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

public final class BrowserMenuController {
    private static final int MAX_POPUP_HEIGHT_RATIO = 86;
    private static final int MIN_POPUP_WIDTH_DP = 248;
    private static final int MAX_POPUP_WIDTH_DP = 352;
    private static final Map<Activity, Binding> BINDINGS = new WeakHashMap<>();

    private BrowserMenuController() {
    }

    private static BrowserThemeManager.MenuStyle defaultStyleCache;

    private static BrowserThemeManager.MenuStyle styleOf(Activity activity) {
        BrowserThemeManager.ThemeInfo theme = BrowserThemeManager.getActiveTheme(activity);
        if (theme != null && theme.menuStyle != null && theme.menuStyle.customized) return theme.menuStyle;
        if (defaultStyleCache == null) {
            defaultStyleCache = BrowserThemeManager.MenuStyle.defaultStyle(BrowserThemeManager.getDefaultPalette());
        }
        return defaultStyleCache;
    }

    private static String styleKey(Activity activity) {
        BrowserThemeManager.ThemeInfo theme = BrowserThemeManager.getActiveTheme(activity);
        if (theme != null && theme.menuStyle != null && theme.menuStyle.customized) return theme.fileName + ":" + theme.modifiedAt;
        return "builtin";
    }

    public static final class QuickAction {
        public final int icon;
        public final String label;
        public final boolean enabled;
        public final Runnable action;

        public QuickAction(int icon, String label, boolean enabled, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.enabled = enabled;
            this.action = action;
        }
    }

    public interface QuickActionProvider {
        java.util.List<QuickAction> quickActions();
    }

    private static final Map<Activity, QuickActionProvider> QUICK = new WeakHashMap<>();

    public static void setQuickActions(Activity activity, QuickActionProvider provider) {
        if (activity == null) return;
        if (provider == null) QUICK.remove(activity); else QUICK.put(activity, provider);
    }

    public static void prepare(Activity activity, Menu menu) {
        if (activity == null || menu == null || activity.isFinishing() || activity.isDestroyed()) return;
        BrowserThemeManager.MenuStyle theme = styleOf(activity);
        Toolbar toolbar = activity.findViewById(R.id.topAppBar);
        View customButton = activity.findViewById(R.id.themeOverflowButton);
        if (toolbar == null || customButton == null) return;
        String key = styleKey(activity);
        Binding binding = BINDINGS.get(activity);
        if (binding == null || binding.toolbar != toolbar || binding.menu != menu || binding.customButton != customButton) {
            if (binding != null) restoreBinding(binding);
            binding = new Binding(toolbar, menu, customButton, key);
            BINDINGS.put(activity, binding);
        } else {
            binding.menu = menu;
            binding.key = key;
        }
        binding.active = true;
        binding.customMode = true;
        ensureToolbarListener(activity, binding);
        ensureActionMenuListener(activity, binding);
        applyCustomMode(activity, binding, theme);
        final Binding postedBinding = binding;
        toolbar.post(() -> {
            if (!postedBinding.active || !postedBinding.customMode) return;
            BrowserThemeManager.MenuStyle currentTheme = styleOf(activity);
            applyCustomMode(activity, postedBinding, currentTheme);
        });
    }

    public static void refresh(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Toolbar toolbar = activity.findViewById(R.id.topAppBar);
        if (toolbar == null) return;
        BrowserThemeManager.MenuStyle theme = styleOf(activity);
        prepare(activity, toolbar.getMenu());
    }

    public static void restore(Activity activity) {
        if (activity == null) return;
        Binding binding = BINDINGS.remove(activity);
        if (binding == null) {
            View custom = activity.findViewById(R.id.themeOverflowButton);
            if (custom != null) {
                custom.setOnClickListener(null);
                custom.setClickable(false);
                custom.setFocusable(false);
                custom.setVisibility(View.GONE);
                custom.setTranslationX(0f);
                custom.setTranslationY(0f);
            }
            Toolbar toolbar = activity.findViewById(R.id.topAppBar);
            if (toolbar != null) {
                toolbar.hideOverflowMenu();
                showStandardOverflow(toolbar);
            }
            return;
        }
        restoreBinding(binding);
    }

    private static void restoreBinding(Binding binding) {
        if (binding == null) return;
        binding.active = false;
        binding.customMode = false;
        if (binding.toolbar != null) binding.toolbar.hideOverflowMenu();
        if (binding.session != null) {
            binding.session.dismissAll();
            binding.session = null;
        }
        detachToolbarListener(binding);
        detachActionMenuListener(binding);
        if (binding.customButton != null) {
            binding.customButton.setOnClickListener(null);
            binding.customButton.setClickable(false);
            binding.customButton.setFocusable(false);
            binding.customButton.setVisibility(View.GONE);
            binding.customButton.setTranslationX(0f);
            binding.customButton.setTranslationY(0f);
        }
        restoreStandardOverflow(binding);
        binding.standardOverflow = null;
    }

    private static void ensureToolbarListener(Activity activity, Binding binding) {
        if (binding.toolbar == null || binding.toolbarLayoutListener != null) return;
        binding.toolbarLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (!binding.active || !binding.customMode) return;
            BrowserThemeManager.MenuStyle current = styleOf(activity);
            ensureActionMenuListener(activity, binding);
            applyCustomMode(activity, binding, current);
        };
        binding.toolbar.addOnLayoutChangeListener(binding.toolbarLayoutListener);
    }

    private static void ensureActionMenuListener(Activity activity, Binding binding) {
        if (binding == null || binding.toolbar == null) return;
        ActionMenuView menuView = findActionMenuView(binding.toolbar);
        if (menuView == null) return;
        if (binding.actionMenuView == menuView && binding.actionMenuLayoutListener != null) return;
        detachActionMenuListener(binding);
        binding.actionMenuView = menuView;
        binding.actionMenuLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (!binding.active || !binding.customMode) return;
            BrowserThemeManager.MenuStyle currentTheme = styleOf(activity);
            applyCustomMode(activity, binding, currentTheme);
        };
        menuView.addOnLayoutChangeListener(binding.actionMenuLayoutListener);
    }

    private static void detachToolbarListener(Binding binding) {
        if (binding != null && binding.toolbar != null && binding.toolbarLayoutListener != null) {
            binding.toolbar.removeOnLayoutChangeListener(binding.toolbarLayoutListener);
            binding.toolbarLayoutListener = null;
        }
    }

    private static void detachActionMenuListener(Binding binding) {
        if (binding != null && binding.actionMenuView != null && binding.actionMenuLayoutListener != null) {
            binding.actionMenuView.removeOnLayoutChangeListener(binding.actionMenuLayoutListener);
        }
        if (binding != null) {
            binding.actionMenuView = null;
            binding.actionMenuLayoutListener = null;
        }
    }

    private static void applyCustomMode(Activity activity, Binding binding, BrowserThemeManager.MenuStyle style) {
        if (binding == null || !binding.active || !binding.customMode || style == null) return;
        View standard = findStandardOverflow(binding.toolbar);
        if (standard != null) {
            if (standard.getVisibility() == View.VISIBLE && binding.toolbar != null) binding.toolbar.hideOverflowMenu();
            concealStandard(binding, standard, style);
        }
        bindCustomButton(activity, binding, style);
        alignCustomButton(binding, standard);
        if (!binding.alignPending && binding.toolbar != null) {
            binding.alignPending = true;
            binding.toolbar.post(() -> {
                binding.alignPending = false;
                if (!binding.active || !binding.customMode) return;
                alignCustomButton(binding, findStandardOverflow(binding.toolbar));
            });
        }
    }

    private static void concealStandard(Binding binding, View standard, BrowserThemeManager.MenuStyle style) {
        captureStandardState(binding, standard);
        binding.standardOverflow = standard;
        int size = dp(style.buttonSizeDp, standard.getResources().getDisplayMetrics().density);
        if (standard.getMinimumWidth() != size) standard.setMinimumWidth(size);
        if (standard.getMinimumHeight() != size) standard.setMinimumHeight(size);
        if (standard.getPaddingLeft() != 0 || standard.getPaddingTop() != 0 || standard.getPaddingRight() != 0 || standard.getPaddingBottom() != 0) {
            standard.setPadding(0, 0, 0, 0);
        }
        if (standard.isEnabled()) standard.setEnabled(false);
        if (standard.isClickable()) standard.setClickable(false);
        if (standard.isFocusable()) standard.setFocusable(false);
        if (standard.getAlpha() != 0f) standard.setAlpha(0f);
        if (standard.getVisibility() != View.INVISIBLE) standard.setVisibility(View.INVISIBLE);
    }

    private static void alignCustomButton(Binding binding, View standard) {
        View custom = binding.customButton;
        if (custom == null || standard == null) return;
        if (!standard.isLaidOut() || !custom.isLaidOut() || standard.getWidth() <= 0 || custom.getWidth() <= 0) return;
        int[] a = binding.locA;
        int[] b = binding.locB;
        standard.getLocationInWindow(a);
        custom.getLocationInWindow(b);
        float dx = a[0] + (standard.getWidth() - custom.getWidth()) / 2f - b[0];
        float dy = a[1] + (standard.getHeight() - custom.getHeight()) / 2f - b[1];
        View bar = binding.toolbar;
        if (bar != null && bar.getWidth() > 0 && bar.getHeight() > 0) {
            int[] c = binding.locC;
            bar.getLocationInWindow(c);
            float minLeft = c[0] + bar.getPaddingLeft();
            float maxRight = c[0] + bar.getWidth() - bar.getPaddingRight();
            float minTop = c[1] + bar.getPaddingTop();
            float maxBottom = c[1] + bar.getHeight() - bar.getPaddingBottom();
            float left = b[0] + dx;
            float top = b[1] + dy;
            if (left + custom.getWidth() > maxRight) dx -= left + custom.getWidth() - maxRight;
            if (left + dx < minLeft) dx += minLeft - (left + dx);
            if (top + custom.getHeight() > maxBottom) dy -= top + custom.getHeight() - maxBottom;
            if (top + dy < minTop) dy += minTop - (top + dy);
        }
        if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) return;
        custom.setTranslationX(custom.getTranslationX() + dx);
        custom.setTranslationY(custom.getTranslationY() + dy);
    }

    private static void bindCustomButton(Activity activity, Binding binding, BrowserThemeManager.MenuStyle style) {
        if (binding == null || binding.customButton == null || style == null) return;
        View button = binding.customButton;
        if (button.getVisibility() != View.VISIBLE) button.setVisibility(View.VISIBLE);
        if (!binding.clickBound) {
            binding.clickBound = true;
            button.setClickable(true);
            button.setFocusable(true);
            button.setEnabled(true);
            button.setContentDescription("メニュー");
            button.setOnClickListener(v -> {
                if (!binding.active || !binding.customMode || binding.customButton != v || binding.menu == null) return;
                BrowserThemeManager.MenuStyle current = styleOf(activity);
                View standard = findStandardOverflow(binding.toolbar);
                if (standard != null) concealStandard(binding, standard, current);
                show(activity, v, binding.menu, current);
            });
        }
        if (!binding.styledKey.equals(binding.key)) {
            binding.styledKey = binding.key;
            button.setTranslationX(0f);
            button.setTranslationY(0f);
            styleCustomButton(button, style);
        }
    }

    private static void captureStandardState(Binding binding, View standard) {
        if (binding == null || standard == null) return;
        if (!binding.standardStates.containsKey(standard)) {
            binding.standardStates.put(standard, new StandardOverflowState(standard.getVisibility(), standard.isEnabled(), standard.getAlpha(), standard.isClickable(), standard.isFocusable(),
                    standard.getMinimumWidth(), standard.getMinimumHeight(),
                    standard.getPaddingLeft(), standard.getPaddingTop(), standard.getPaddingRight(), standard.getPaddingBottom()));
        }
    }

    private static void restoreStandardOverflow(Binding binding) {
        if (binding == null) return;
        View standard = findStandardOverflow(binding.toolbar);
        if (standard == null) standard = binding.standardOverflow;
        if (standard == null) return;
        StandardOverflowState state = binding.standardStates.get(standard);
        if (state == null) {
            standard.setEnabled(true);
            standard.setVisibility(View.VISIBLE);
            standard.setAlpha(1f);
            standard.setClickable(true);
            standard.setFocusable(true);
        } else {
            standard.setEnabled(state.enabled);
            standard.setVisibility(state.visibility);
            standard.setAlpha(state.alpha);
            standard.setClickable(state.clickable);
            standard.setFocusable(state.focusable);
            standard.setMinimumWidth(state.minWidth);
            standard.setMinimumHeight(state.minHeight);
            standard.setPadding(state.padLeft, state.padTop, state.padRight, state.padBottom);
        }
    }

    private static void showStandardOverflow(Toolbar toolbar) {
        if (toolbar == null) return;
        View standard = findStandardOverflow(toolbar);
        if (standard == null) return;
        standard.setEnabled(true);
        standard.setVisibility(View.VISIBLE);
        standard.setAlpha(1f);
        standard.setClickable(true);
        standard.setFocusable(true);
    }

    private static ActionMenuView findActionMenuView(Toolbar toolbar) {
        if (toolbar == null) return null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView) return (ActionMenuView) child;
        }
        return null;
    }

    public static View overflowOf(Toolbar toolbar) {
        return findStandardOverflow(toolbar);
    }

    public static void realign(Toolbar toolbar) {
        if (toolbar == null) return;
        android.content.Context context = toolbar.getContext();
        while (context instanceof android.content.ContextWrapper && !(context instanceof Activity)) {
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (!(context instanceof Activity)) return;
        final Binding binding = BINDINGS.get((Activity) context);
        if (binding == null || !binding.active || !binding.customMode || binding.toolbar != toolbar) return;
        toolbar.post(() -> {
            if (!binding.active || !binding.customMode) return;
            alignCustomButton(binding, findStandardOverflow(toolbar));
        });
    }

    private static View findStandardOverflow(Toolbar toolbar) {
        ActionMenuView menuView = findActionMenuView(toolbar);
        if (menuView == null) return null;
        for (int j = menuView.getChildCount() - 1; j >= 0; j--) {
            View item = menuView.getChildAt(j);
            if (isOverflowButton(item)) return item;
        }
        return null;
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private static boolean isOverflowButton(View item) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp instanceof ActionMenuView.LayoutParams && ((ActionMenuView.LayoutParams) lp).isOverflowButton) return true;
        return item.getClass().getName().endsWith("OverflowMenuButton");
    }

    private static void styleCustomButton(View view, BrowserThemeManager.MenuStyle style) {
        if (!(view instanceof ImageView) || style == null) return;
        ImageView image = (ImageView) view;
        float density = view.getResources().getDisplayMetrics().density;
        int touchSize = dp(style.buttonSizeDp, density);
        int iconSize = Math.min(dp(style.buttonIconSizeDp, density), touchSize);
        int pad = Math.max(0, (touchSize - iconSize) / 2);
        Drawable icon = AppCompatResources.getDrawable(view.getContext(), R.drawable.ic_more_vert);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, style.iconTint);
            image.setImageDrawable(icon);
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        image.setBackground(overflowBackground(style, density));
        image.setPadding(pad, pad, pad, pad);
        ViewGroup.LayoutParams lp = image.getLayoutParams();
        if (lp != null && (lp.width != touchSize || lp.height != touchSize)) {
            lp.width = touchSize;
            lp.height = touchSize;
            image.setLayoutParams(lp);
        }
    }

    private static void show(Activity activity, View anchor, Menu menu, BrowserThemeManager.MenuStyle style) {
        if (activity == null || anchor == null || menu == null || style == null ||
                activity.isFinishing() || activity.isDestroyed()) return;
        Binding binding = BINDINGS.get(activity);
        if (binding != null && binding.session != null) binding.session.dismissAll();
        float density = activity.getResources().getDisplayMetrics().density;
        int requestedWidth = dp(style.maxWidthDp, density);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int availableWidth = Math.max(dp(1, density), screenWidth - dp(16, density));
        int minWidth = Math.min(dp(MIN_POPUP_WIDTH_DP, density), availableWidth);
        int width = Math.max(minWidth, Math.min(dp(MAX_POPUP_WIDTH_DP, density), requestedWidth));
        width = Math.min(width, availableWidth);
        int maxHeight = activity.getWindow().getDecorView().getHeight();
        if (maxHeight <= 0) maxHeight = activity.getResources().getDisplayMetrics().heightPixels;
        maxHeight = Math.max(dp(180, density), Math.min(maxHeight, Math.round(maxHeight * MAX_POPUP_HEIGHT_RATIO / 100f)));

        LinearLayout surface = new LinearLayout(activity);
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(dp(style.paddingHorizontalDp, density), dp(style.paddingVerticalDp, density),
                dp(style.paddingHorizontalDp, density), dp(style.paddingVerticalDp, density));
        surface.setBackground(popupBackground(style, density));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        surface.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = new PopupWindow(surface, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setFocusable(true);
        popup.setClippingEnabled(true);
        popup.setElevation(dp(style.elevationDp, density));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        PopupSession session = new PopupSession(activity, popup, content, surface, scroll, menu, style, maxHeight, density);
        popup.setOnDismissListener(() -> {
            Binding current = BINDINGS.get(activity);
            if (current != null && current.session == session) current.session = null;
        });
        session.render();
        if (binding != null) binding.session = session;
        showAtAnchor(activity, popup, anchor, width, density);
    }

    private static void showAtAnchor(Activity activity, PopupWindow popup, View anchor, int width, float density) {
        int[] anchorLocation = new int[2];
        int[] decorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        activity.getWindow().getDecorView().getLocationOnScreen(decorLocation);
        int anchorLeft = anchorLocation[0] - decorLocation[0];
        int anchorTop = anchorLocation[1] - decorLocation[1];
        int anchorBottom = anchorTop + anchor.getHeight();
        int decorWidth = activity.getWindow().getDecorView().getWidth();
        int decorHeight = activity.getWindow().getDecorView().getHeight();
        int popupHeight = popup.getContentView().getMeasuredHeight();
        int x = anchorLeft + anchor.getWidth() - width;
        BrowserThemeManager.ThemeInfo theme = BrowserThemeManager.getActiveTheme(activity);
        if (theme != null && theme.layout != null && "start".equals(theme.layout.menuPosition)) x = anchorLeft;
        x = Math.max(dp(6, density), Math.min(x, Math.max(dp(6, density), decorWidth - width - dp(6, density))));
        boolean above = anchorBottom + popupHeight + dp(6, density) > decorHeight;
        int y = above ? anchorTop - popupHeight - dp(6, density) : anchorBottom + dp(6, density);
        y = Math.max(dp(6, density), Math.min(y, Math.max(dp(6, density), decorHeight - popupHeight - dp(6, density))));
        popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.LEFT, x, y);
    }

    private static final class PopupSession {
        final Activity activity;
        final PopupWindow popup;
        final LinearLayout content;
        final LinearLayout surface;
        final ScrollView scrollView;
        final BrowserThemeManager.MenuStyle style;
        final int maxHeight;
        final float density;
        final Deque<Menu> stack = new ArrayDeque<>();
        final Deque<CharSequence> titles = new ArrayDeque<>();
        final Deque<Integer> scrollPositions = new ArrayDeque<>();
        final Menu rootMenu;
        Menu currentMenu;
        CharSequence currentTitle;

        PopupSession(Activity activity, PopupWindow popup, LinearLayout content, LinearLayout surface, ScrollView scrollView,
                     Menu rootMenu, BrowserThemeManager.MenuStyle style, int maxHeight, float density) {
            this.activity = activity;
            this.popup = popup;
            this.content = content;
            this.surface = surface;
            this.scrollView = scrollView;
            this.rootMenu = rootMenu;
            this.currentMenu = rootMenu;
            this.style = style;
            this.maxHeight = maxHeight;
            this.density = density;
        }

        void render() {
            content.removeAllViews();
            if (!stack.isEmpty()) addBackRow(); else addQuickRow();
            boolean showIcons = hasVisibleIcons(currentMenu);
            boolean addedAny = false;
            int visibleIndex = 0;
            for (int i = 0; i < currentMenu.size(); i++) {
                MenuItem item = currentMenu.getItem(i);
                if (!item.isVisible()) continue;
                if (addedAny && item.hasSubMenu()) {
                    addGroupSpacing();
                    if (style.useDividers) addDivider();
                }
                addItem(item, showIcons, visibleIndex);
                visibleIndex++;
                addedAny = true;
            }
            int popupWidth = popup.getWidth() > 0 ? popup.getWidth() : dp(style.maxWidthDp, density);
            int innerWidth = Math.max(1, popupWidth - surface.getPaddingLeft() - surface.getPaddingRight());
            content.measure(View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            scrollView.measure(View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            surface.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            int measured = Math.max(dp(48, density), Math.min(maxHeight, surface.getMeasuredHeight()));
            popup.setHeight(measured);
            if (popup.isShowing()) popup.update(popup.getWidth(), measured);
            int targetScroll = scrollPositions.isEmpty() ? 0 : scrollPositions.peekLast();
            content.post(() -> scrollView.scrollTo(0, targetScroll));
        }

        void addQuickRow() {
            QuickActionProvider provider = QUICK.get(activity);
            if (provider == null) return;
            java.util.List<QuickAction> list;
            try {
                list = provider.quickActions();
            } catch (Throwable t) {
                return;
            }
            if (list == null || list.isEmpty()) return;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int cellHeight = dp(62, density);
            for (final QuickAction q : list) {
                LinearLayout cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(rowBackground(style, density, false, false));
                ImageView icon = new ImageView(activity);
                Drawable d = AppCompatResources.getDrawable(activity, q.icon);
                if (d != null) {
                    d = DrawableCompat.wrap(d.mutate());
                    DrawableCompat.setTint(d, q.enabled ? style.iconTint : style.secondary);
                    icon.setImageDrawable(d);
                }
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(24, density), dp(24, density)));
                TextView label = textView(q.label, q.enabled ? style.text : style.secondary, 11, false, activity);
                label.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = dp(4, density);
                cell.addView(label, llp);
                cell.setEnabled(q.enabled);
                cell.setAlpha(q.enabled ? 1f : 0.48f);
                cell.setContentDescription(q.label);
                if (q.enabled) {
                    cell.setOnClickListener(v -> {
                        dismissAll();
                        if (q.action != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(q.action);
                    });
                }
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, cellHeight, 1f);
                clp.setMargins(dp(2, density), 0, dp(2, density), 0);
                row.addView(cell, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellHeight);
            rlp.bottomMargin = dp(6, density);
            content.addView(row, rlp);
            if (style.useDividers) addDivider();
        }

        void addBackRow() {
            LinearLayout row = baseRow(true);
            TextView arrow = textView("‹", style.accent, style.headerTextSizeSp, false, activity);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(style.trailingWidthDp, density), ViewGroup.LayoutParams.MATCH_PARENT));
            TextView title = textView(currentTitle, style.text, style.headerTextSizeSp, true, activity);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            titleLp.setMargins(dp(style.iconGapDp, density), 0, 0, 0);
            row.addView(title, titleLp);
            row.setBackground(rowBackground(style, density, true, false));
            row.setOnClickListener(v -> {
                if (!stack.isEmpty()) {
                    scrollPositions.pollLast();
                    currentMenu = stack.pop();
                    currentTitle = titles.isEmpty() ? null : titles.pop();
                    render();
                }
            });
            addRow(row);
        }

        void addItem(MenuItem item, boolean showIcons, int index) {
            LinearLayout row = baseRow(item.hasSubMenu());
            row.setBackground(rowBackground(style, density, item.hasSubMenu(), style.alternateRows && (index & 1) == 1));
            if (showIcons) addIcon(row, item.getIcon());
            int titleColor = item.hasSubMenu() ? style.headerText : (item.isEnabled() ? style.text : style.secondary);
            TextView title = textView(item.getTitle(), titleColor,
                    item.hasSubMenu() ? style.headerTextSizeSp : style.textSizeSp, item.hasSubMenu(), activity);
            if ("monospace".equals(style.fontFamily)) {
                title.setTypeface(android.graphics.Typeface.MONOSPACE, item.hasSubMenu() ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            } else if ("serif".equals(style.fontFamily)) {
                title.setTypeface(android.graphics.Typeface.SERIF, item.hasSubMenu() ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            } else if ("sans-serif-medium".equals(style.fontFamily)) {
                title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", item.hasSubMenu() ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            } else {
                title.setTypeface(android.graphics.Typeface.create("sans-serif", item.hasSubMenu() ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            }
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            titleLp.setMargins(showIcons ? dp(style.iconGapDp, density) : dp(4, density), 0, dp(style.iconGapDp, density), 0);
            row.addView(title, titleLp);
            if (item.hasSubMenu()) {
                TextView arrow = textView("›", style.secondary, style.headerTextSizeSp + 2, false, activity);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(style.trailingWidthDp, density), ViewGroup.LayoutParams.MATCH_PARENT));
            } else if (item.isCheckable()) {
                String glyph = checkGlyph(item.isChecked(), style.checkStyle);
                TextView check = textView(glyph, style.accent, style.textSizeSp + (item.isChecked() ? 3 : 1), true, activity);
                check.setGravity(Gravity.CENTER);
                check.setContentDescription(item.isChecked() ? "選択中" : "未選択");
                row.addView(check, new LinearLayout.LayoutParams(dp(style.trailingWidthDp, density), ViewGroup.LayoutParams.MATCH_PARENT));
            }
            row.setEnabled(item.isEnabled());
            row.setAlpha(item.isEnabled() ? 1f : 0.48f);
            row.setContentDescription(item.hasSubMenu() ? item.getTitle() + "、サブメニュー" : item.getTitle());
            if (item.isEnabled()) {
                row.setOnClickListener(v -> {
                    if (item.hasSubMenu()) {
                        scrollPositions.addLast(scrollView.getScrollY());
                        stack.push(currentMenu);
                        titles.push(currentTitle == null ? "" : currentTitle);
                        currentMenu = item.getSubMenu();
                        currentTitle = item.getTitle();
                        render();
                    } else {
                        currentMenu.performIdentifierAction(item.getItemId(), 0);
                        dismissAll();
                    }
                });
            }
            addRow(row);
        }

        private String checkGlyph(boolean checked, String checkStyle) {
            if (!checked) return "□";
            if ("dot".equals(checkStyle)) return "•";
            if ("circle".equals(checkStyle)) return "☑";
            return "✓";
        }

        boolean hasVisibleIcons(Menu menu) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (!item.isVisible()) continue;
                if (item.getIcon() != null) return true;
                if (item.hasSubMenu() && hasVisibleIcons(item.getSubMenu())) return true;
            }
            return false;
        }

        void addIcon(LinearLayout row, Drawable source) {
            ImageView icon = new ImageView(activity);
            if (source != null) {
                Drawable drawable = source.getConstantState() == null ? source.mutate() : source.getConstantState().newDrawable();
                drawable = DrawableCompat.wrap(drawable.mutate());
                DrawableCompat.setTint(drawable, style.iconTint);
                icon.setImageDrawable(drawable);
            }
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(dp(style.iconSizeDp, density), dp(style.iconSizeDp, density)));
        }

        LinearLayout baseRow(boolean header) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(style.rowPaddingHorizontalDp, density), 0, dp(style.rowPaddingHorizontalDp, density), 0);
            row.setMinimumHeight(dp(style.itemHeightDp, density));
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            return row;
        }

        void addRow(View row) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(style.itemHeightDp, density));
            int gap = dp(style.itemGapDp, density);
            if (gap > 0) lp.bottomMargin = gap;
            content.addView(row, lp);
        }

        void addGroupSpacing() {
            int spacing = dp(style.groupSpacingDp, density);
            if (spacing <= 0) return;
            View spacer = new View(activity);
            content.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, spacing));
        }

        void addDivider() {
            View divider = new View(activity);
            divider.setBackgroundColor(style.divider);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1, density)));
            int half = dp(Math.max(1, style.groupSpacingDp / 2), density);
            lp.setMargins(dp(8, density), half, dp(8, density), half);
            content.addView(divider, lp);
        }

        void dismissAll() {
            popup.dismiss();
        }
    }

    private static TextView textView(CharSequence text, int color, int size, boolean bold, Activity activity) {
        TextView view = new TextView(activity);
        view.setText(text == null ? "" : text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMaxLines(1);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private static GradientDrawable popupBackground(BrowserThemeManager.MenuStyle style, float density) {
        GradientDrawable background;
        if ("gradient".equals(style.surfacePattern)) {
            background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{style.background, blend(style.background, style.accent, 0.10f)});
        } else if ("frosted".equals(style.surfacePattern)) {
            background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{style.background, withAlpha(blend(style.background, style.accent, 0.16f), Color.alpha(style.background))});
        } else if ("paper".equals(style.surfacePattern)) {
            background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{style.background, blend(style.background, style.headerBackground, 0.10f)});
        } else if ("terminal".equals(style.surfacePattern)) {
            background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{style.background, blend(style.background, style.accent, 0.05f)});
        } else {
            background = new GradientDrawable();
            background.setColor(style.background);
        }
        background.setCornerRadius(dp(style.radiusDp, density));
        if (style.strokeWidthDp > 0) background.setStroke(dp(style.strokeWidthDp, density), style.stroke);
        return background;
    }

    private static StateListDrawable overflowBackground(BrowserThemeManager.MenuStyle style, float density) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(style.itemBackground);
        normal.setCornerRadius(dp(style.itemRadiusDp, density));
        if (style.strokeWidthDp > 0) {
            normal.setStroke(dp(style.strokeWidthDp, density), style.stroke);
        }
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(style.pressedBackground);
        pressed.setCornerRadius(dp(style.itemRadiusDp, density));
        if (style.strokeWidthDp > 0) {
            pressed.setStroke(dp(style.strokeWidthDp, density), style.stroke);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static int blend(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int alpha = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * a);
        int red = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * a);
        int green = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * a);
        int blue = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a);
        return Color.argb(alpha, red, green, blue);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static StateListDrawable rowBackground(BrowserThemeManager.MenuStyle style, float density, boolean header, boolean alternate) {
        int normalColor;
        if (header) {
            normalColor = style.headerBackground;
            if ("accent".equals(style.headerPattern)) normalColor = blend(style.headerBackground, style.accent, 0.16f);
            else if ("terminal".equals(style.headerPattern)) normalColor = blend(style.headerBackground, style.accent, 0.08f);
        } else {
            float alternateAmount = "stripe".equals(style.rowPattern) ? 0.10f : 0.14f;
            normalColor = alternate ? blend(style.itemBackground, style.pressedBackground, alternateAmount) : style.itemBackground;
            if ("glass".equals(style.rowPattern)) normalColor = blend(normalColor, style.headerBackground, 0.10f);
        }
        int radius = style.itemRadiusDp;
        if ("pill".equals(style.rowPattern)) radius = 30;
        else if ("flat".equals(style.rowPattern)) radius = 3;
        else if ("terminal".equals(style.rowPattern)) radius = 5;
        else if ("glass".equals(style.rowPattern)) radius = Math.max(8, style.itemRadiusDp);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(radius, density));
        if (style.strokeWidthDp > 0 && (header || "card".equals(style.rowPattern) || "glass".equals(style.rowPattern) || "outline".equals(style.headerPattern))) {
            normal.setStroke(dp(style.strokeWidthDp, density), header ? style.stroke : withAlpha(style.stroke, 150));
        }
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(style.pressedBackground);
        pressed.setCornerRadius(dp(radius, density));
        if (style.strokeWidthDp > 0 && header) pressed.setStroke(dp(style.strokeWidthDp, density), style.stroke);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static int dp(int value, float density) {
        return Math.round(value * density);
    }

    private static final class Binding {
        final Toolbar toolbar;
        final View customButton;
        final Map<View, StandardOverflowState> standardStates = new WeakHashMap<>();
        String key;
        Menu menu;
        boolean active;
        boolean customMode;
        PopupSession session;
        View standardOverflow;
        boolean clickBound;
        boolean alignPending;
        String styledKey = "";
        final int[] locA = new int[2];
        final int[] locB = new int[2];
        final int[] locC = new int[2];
        ActionMenuView actionMenuView;
        View.OnLayoutChangeListener toolbarLayoutListener;
        View.OnLayoutChangeListener actionMenuLayoutListener;

        Binding(Toolbar toolbar, Menu menu, View customButton, String key) {
            this.toolbar = toolbar;
            this.menu = menu;
            this.customButton = customButton;
            this.key = key;
        }
    }

    private static final class StandardOverflowState {
        final int visibility;
        final boolean enabled;
        final float alpha;
        final boolean clickable;
        final boolean focusable;
        final int minWidth;
        final int minHeight;
        final int padLeft;
        final int padTop;
        final int padRight;
        final int padBottom;

        StandardOverflowState(int visibility, boolean enabled, float alpha, boolean clickable, boolean focusable,
                              int minWidth, int minHeight, int padLeft, int padTop, int padRight, int padBottom) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.padLeft = padLeft;
            this.padTop = padTop;
            this.padRight = padRight;
            this.padBottom = padBottom;
            this.visibility = visibility;
            this.enabled = enabled;
            this.alpha = alpha;
            this.clickable = clickable;
            this.focusable = focusable;
        }
    }
}
