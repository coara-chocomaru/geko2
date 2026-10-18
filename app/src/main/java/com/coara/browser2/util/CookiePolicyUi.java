package com.coara.browser2.util;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.coara.browser2.SettingsActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public final class CookiePolicyUi {
    private static final int BG = 0xFFFFFFFF;
    private static final int TEXT = 0xFF202124;
    private static final int SUB = 0xFF5F6368;
    private static final int PRIMARY = 0xFF1A73E8;
    private static final int PRIMARY_SOFT = 0xFFE8F0FE;
    private static final int BORDER = 0xFFDADCE0;
    private static final int CARD = 0xFFF8F9FA;
    private static final int DANGER = 0xFFD93025;

    private static final int BTN_PRIMARY = 0;
    private static final int BTN_OUTLINE = 1;
    private static final int BTN_DANGER_TEXT = 2;
    private static final int BTN_TEXT = 3;

    public interface PromptListener {
        void onAllow();

        void onBlockAlways();

        void onDeny();

        void onClosed();
    }

    private CookiePolicyUi() {
    }

    private static int dp(View v, float value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable shape(int fill, int stroke, int strokeWidthPx, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            d.setStroke(strokeWidthPx, stroke);
        }
        return d;
    }

    private static TextView label(Activity a, CharSequence text, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0f, 1.15f);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private static TextView button(Activity a, String text, int kind) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(a, 48));
        b.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
        b.setClickable(true);
        b.setFocusable(true);
        float radius = dp(a, 24);
        GradientDrawable base;
        int rippleColor;
        if (kind == BTN_PRIMARY) {
            base = shape(PRIMARY, PRIMARY, 0, radius);
            b.setTextColor(Color.WHITE);
            rippleColor = 0x33FFFFFF;
        } else if (kind == BTN_OUTLINE) {
            base = shape(BG, BORDER, dp(a, 1), radius);
            b.setTextColor(PRIMARY);
            rippleColor = 0x221A73E8;
        } else if (kind == BTN_DANGER_TEXT) {
            base = shape(Color.TRANSPARENT, Color.TRANSPARENT, 0, radius);
            b.setTextColor(DANGER);
            rippleColor = 0x22D93025;
        } else {
            base = shape(Color.TRANSPARENT, Color.TRANSPARENT, 0, radius);
            b.setTextColor(PRIMARY);
            rippleColor = 0x221A73E8;
        }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private static LinearLayout column(Activity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 20));
        c.setBackgroundColor(BG);
        return c;
    }

    private static View grabber(Activity a) {
        View g = new View(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 36), dp(a, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(a, 4);
        lp.bottomMargin = dp(a, 14);
        g.setLayoutParams(lp);
        g.setBackground(shape(BORDER, BORDER, 0, dp(a, 2)));
        return g;
    }

    private static View headerRow(Activity a, String title, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView chip = new TextView(a);
        chip.setText("🍪");
        chip.setTextSize(22f);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(shape(PRIMARY_SOFT, PRIMARY_SOFT, 0, dp(a, 24)));
        row.addView(chip, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(a, 14);
        row.addView(texts, tlp);

        texts.addView(label(a, title, 18f, TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = label(a, subtitle, 13f, SUB, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(a, 2);
            texts.addView(sub, slp);
        }
        return row;
    }

    private static View infoCard(Activity a, String[][] rows) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(CARD, BORDER, dp(a, 1), dp(a, 16)));
        card.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        for (int i = 0; i < rows.length; i++) {
            TextView k = label(a, rows[i][0], 11f, SUB, false);
            TextView v = label(a, rows[i][1], 14f, TEXT, true);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                klp.topMargin = dp(a, 8);
            }
            card.addView(k, klp);
            card.addView(v);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 16);
        card.setLayoutParams(lp);
        return card;
    }

    private static TextView paragraph(Activity a, String text, float sp, int color, int topDp) {
        TextView t = label(a, text, sp, color, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, topDp);
        t.setLayoutParams(lp);
        return t;
    }

    private static BottomSheetDialog sheet(Activity a, View content) {
        final BottomSheetDialog dialog = new BottomSheetDialog(a);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            try {
                FrameLayout sheetView = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (sheetView != null) {
                    float r = dp(a, 24);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(BG);
                    bg.setCornerRadii(new float[] {r, r, r, r, 0f, 0f, 0f, 0f});
                    sheetView.setBackground(bg);
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheetView);
                    behavior.setSkipCollapsed(true);
                    behavior.setMaxWidth(dp(a, 560));
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            } catch (Throwable ignored) {
            }
        });
        return dialog;
    }

    private static boolean unusable(Activity a) {
        return a == null || a.isFinishing() || a.isDestroyed();
    }

    public static boolean showPrompt(final Activity a, String topHost, String frameHost,
                                     boolean storageAccess, final PromptListener listener) {
        if (unusable(a) || listener == null) {
            return false;
        }
        try {
            LinearLayout root = column(a);
            root.addView(grabber(a));
            root.addView(headerRow(a, "サードパーティCookieをブロックしました", topHost));
            root.addView(infoCard(a, new String[][] {
                    {"表示中のサイト", topHost},
                    {"Cookieを要求した埋め込み元", frameHost == null || frameHost.isEmpty() ? "不明" : frameHost},
                    {"要求の種類", storageAccess ? "ストレージへのアクセス" : "Cookieの保存"}
            }));
            root.addView(paragraph(a,
                    "許可すると、このサイトに埋め込まれた広告・動画・ログイン連携などがCookieを使えるようになり、追跡されやすくなります。"
                            + "ログインや埋め込みが動かないときだけ許可してください。", 13f, SUB, 14));

            final boolean[] decided = {false};
            final BottomSheetDialog dialog = sheet(a, root);

            TextView allow = button(a, "このサイトで許可", BTN_PRIMARY);
            allow.setOnClickListener(v -> {
                if (decided[0]) {
                    return;
                }
                decided[0] = true;
                dialog.dismiss();
                listener.onAllow();
            });
            TextView deny = button(a, "今回は拒否", BTN_OUTLINE);
            deny.setOnClickListener(v -> {
                if (decided[0]) {
                    return;
                }
                decided[0] = true;
                dialog.dismiss();
                listener.onDeny();
            });
            TextView block = button(a, "このサイトでは常にブロック", BTN_DANGER_TEXT);
            block.setOnClickListener(v -> {
                if (decided[0]) {
                    return;
                }
                decided[0] = true;
                dialog.dismiss();
                listener.onBlockAlways();
            });
            LinearLayout.LayoutParams first = (LinearLayout.LayoutParams) allow.getLayoutParams();
            first.topMargin = dp(a, 18);
            root.addView(allow);
            root.addView(deny);
            root.addView(block);
            root.addView(paragraph(a,
                    "あとから「メニュー › サイト別サードパーティCookie」または「info › Cookieのブロックと許可」で変更できます。",
                    11f, SUB, 10));

            dialog.setOnCancelListener(d -> {
                if (decided[0]) {
                    return;
                }
                decided[0] = true;
                listener.onDeny();
            });
            dialog.setOnDismissListener(d -> {
                if (!decided[0]) {
                    decided[0] = true;
                    listener.onDeny();
                }
                listener.onClosed();
            });
            dialog.show();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private interface Choice {
        void pick(int index);
    }

    private static View optionCard(Activity a, String title, String desc, boolean selected, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        card.setBackground(selected
                ? shape(PRIMARY_SOFT, PRIMARY, dp(a, 2), dp(a, 16))
                : shape(BG, BORDER, dp(a, 1), dp(a, 16)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(click);

        View radio = new View(a);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        if (selected) {
            dot.setColor(PRIMARY);
            dot.setStroke(dp(a, 5), PRIMARY_SOFT);
        } else {
            dot.setColor(BG);
            dot.setStroke(dp(a, 2), SUB);
        }
        radio.setBackground(dot);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22));
        rlp.rightMargin = dp(a, 14);
        card.addView(radio, rlp);

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(a, title, 15f, TEXT, true));
        if (desc != null && !desc.isEmpty()) {
            TextView d = label(a, desc, 12f, SUB, false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(a, 3);
            texts.addView(d, dlp);
        }
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 10);
        card.setLayoutParams(lp);
        return card;
    }

    private static void addChoices(Activity a, LinearLayout root, String[] titles, String[] descs,
                                   int selected, final Choice choice) {
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            root.addView(optionCard(a, titles[i], descs[i], i == selected, v -> choice.pick(index)));
        }
    }

    public static void showSiteDialog(final Activity a, final SharedPreferences pref, final String url,
                                      final Runnable onChanged) {
        if (unusable(a) || pref == null) {
            return;
        }
        final String host = CookieSitePolicy.hostOf(url);
        final LinearLayout root = column(a);
        root.addView(grabber(a));
        final BottomSheetDialog dialog = sheet(a, root);

        if (CookieSitePolicy.blockAll(pref)) {
            root.addView(headerRow(a, "クッキーを常にブロック中", host));
            root.addView(paragraph(a,
                    "すべてのCookieの保存と送信を拒否する設定になっているため、サイトごとの許可は適用されません。"
                            + "サイト別に許可するには「info › Cookieのブロックと許可」で設定を変更してください。", 13f, SUB, 14));
            TextView open = button(a, "設定を開く", BTN_PRIMARY);
            ((LinearLayout.LayoutParams) open.getLayoutParams()).topMargin = dp(a, 18);
            open.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    a.startActivity(new Intent(a, SettingsActivity.class));
                } catch (Throwable ignored) {
                }
            });
            TextView close = button(a, "閉じる", BTN_TEXT);
            close.setOnClickListener(v -> dialog.dismiss());
            root.addView(open);
            root.addView(close);
            dialog.show();
            return;
        }

        final boolean globalAllowed = CookieSitePolicy.globalAllowed(pref);
        final int current = CookieSitePolicy.override(pref, url);
        root.addView(headerRow(a, "このサイトのサードパーティCookie", host));
        String[] titles = {"許可", "ブロック", "既定に従う"};
        String[] descs = {
                "このサイトに埋め込まれたコンテンツのCookieを許可します。",
                "このサイトに埋め込まれたコンテンツのCookieを常にブロックします。",
                "アプリ全体の設定に従います(現在: " + (globalAllowed ? "許可" : "ブロック") + ")。"
        };
        int selected = current > 0 ? 0 : (current < 0 ? 1 : 2);
        addChoices(a, root, titles, descs, selected, index -> {
            int mode = index == 0 ? 1 : (index == 1 ? -1 : 0);
            dialog.dismiss();
            if (!CookieSitePolicy.setOverride(pref, url, mode)) {
                Toast.makeText(a, "保存できませんでした", Toast.LENGTH_SHORT).show();
                return;
            }
            if (onChanged != null) {
                onChanged.run();
            }
            Toast.makeText(a, "設定を反映しました", Toast.LENGTH_SHORT).show();
        });
        TextView close = button(a, "閉じる", BTN_TEXT);
        ((LinearLayout.LayoutParams) close.getLayoutParams()).topMargin = dp(a, 14);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);
        dialog.show();
    }

    public static void showGlobalDialog(final Activity a, final SharedPreferences pref, final Runnable onChanged) {
        if (unusable(a) || pref == null) {
            return;
        }
        final LinearLayout root = column(a);
        root.addView(grabber(a));
        final BottomSheetDialog dialog = sheet(a, root);
        root.addView(headerRow(a, "Cookieのブロックと許可", "Cookieの扱いを選びます"));
        String[] titles = {
                "サードパーティCookieをブロック(推奨)",
                "サードパーティCookieを許可",
                "クッキーを常にブロック&拒否"
        };
        String[] descs = {
                "別サイトから埋め込まれたコンテンツのCookieを拒否します。ログイン状態など通常のCookieは使えます。",
                "互換性を優先します。広告や解析による追跡を受けやすくなります。",
                "すべてのCookie(閲覧中のサイト自身のものも含む)の保存と送信を拒否します。ログイン状態やカートは保持されず、ダウンロード時のCookie送信も止まります。"
        };
        addChoices(a, root, titles, descs, CookieSitePolicy.mode(pref), index -> {
            CookieSitePolicy.setMode(pref, index);
            dialog.dismiss();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        TextView manage = button(a, "サイトごとの設定を管理", BTN_OUTLINE);
        ((LinearLayout.LayoutParams) manage.getLayoutParams()).topMargin = dp(a, 18);
        manage.setOnClickListener(v -> {
            dialog.dismiss();
            showSiteManager(a, pref, onChanged);
        });
        TextView close = button(a, "閉じる", BTN_TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(manage);
        root.addView(close);
        dialog.show();
    }

    public static void showSiteManager(final Activity a, final SharedPreferences pref, final Runnable onChanged) {
        if (unusable(a) || pref == null) {
            return;
        }
        final LinearLayout root = column(a);
        root.addView(grabber(a));
        final BottomSheetDialog dialog = sheet(a, root);
        root.addView(headerRow(a, "サイトごとのCookie設定", "許可・ブロックしたサイトの一覧"));
        final boolean[] changed = {false};
        addSection(a, root, "Cookieを常に許可するサイト", true, pref, changed);
        addSection(a, root, "Cookieを常にブロックするサイト", false, pref, changed);

        TextView reset = button(a, "すべてのサイト設定をリセット", BTN_DANGER_TEXT);
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).topMargin = dp(a, 18);
        reset.setOnClickListener(v -> {
            CookieSitePolicy.clearOverrides(pref);
            changed[0] = true;
            dialog.dismiss();
            Toast.makeText(a, "サイト別設定をリセットしました", Toast.LENGTH_SHORT).show();
        });
        TextView close = button(a, "閉じる", BTN_TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(reset);
        root.addView(close);
        dialog.setOnDismissListener(d -> {
            if (changed[0] && onChanged != null) {
                onChanged.run();
            }
        });
        dialog.show();
    }

    private static void addSection(final Activity a, final LinearLayout root, String title, final boolean allowList,
                                   final SharedPreferences pref, final boolean[] changed) {
        TextView head = label(a, title, 13f, PRIMARY, true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(a, 18);
        root.addView(head, hlp);
        List<String> hosts = CookieSitePolicy.sites(pref, allowList);
        if (hosts.isEmpty()) {
            root.addView(paragraph(a, "なし", 13f, SUB, 6));
            return;
        }
        for (final String host : hosts) {
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 12), dp(a, 2), dp(a, 4), dp(a, 2));
            row.setBackground(shape(CARD, BORDER, dp(a, 1), dp(a, 14)));
            TextView name = label(a, host, 14f, TEXT, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView remove = button(a, "削除", BTN_DANGER_TEXT);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            remove.setLayoutParams(rlp);
            remove.setMinWidth(dp(a, 64));
            remove.setOnClickListener(v -> {
                if (CookieSitePolicy.setOverrideHost(pref, host, 0)) {
                    changed[0] = true;
                    ((ViewGroup) row.getParent()).removeView(row);
                }
            });
            row.addView(remove);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(a, 6);
            root.addView(row, lp);
        }
    }
}
