package com.coara.browser2.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class TranslateSettings {

    public static final String AUTO = "auto";
    public static final String DEFAULT_TARGET = "ja";

    private static final String PREFS_MAIN = "coara_translate";
    private static final String PREFS_SECRET = "coara_translate_secret";
    private static final String KEY_TARGET = "target";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_AUTO_TRANSLATE = "auto_translate";

    private static final String[][] LANGUAGES = {
            {"ja", "日本語"},
            {"en", "英語"},
            {"zh-CN", "中国語（簡体字）"},
            {"zh-TW", "中国語（繁体字）"},
            {"ko", "韓国語"},
            {"es", "スペイン語"},
            {"fr", "フランス語"},
            {"de", "ドイツ語"},
            {"pt", "ポルトガル語"},
            {"ru", "ロシア語"},
            {"it", "イタリア語"},
            {"ar", "アラビア語"},
            {"sq", "アルバニア語"},
            {"az", "アゼルバイジャン語"},
            {"bn", "ベンガル語"},
            {"eu", "バスク語"},
            {"bs", "ボスニア語"},
            {"bg", "ブルガリア語"},
            {"ca", "カタルーニャ語"},
            {"hr", "クロアチア語"},
            {"cs", "チェコ語"},
            {"da", "デンマーク語"},
            {"nl", "オランダ語"},
            {"et", "エストニア語"},
            {"fi", "フィンランド語"},
            {"gl", "ガリシア語"},
            {"el", "ギリシャ語"},
            {"gu", "グジャラート語"},
            {"he", "ヘブライ語"},
            {"hi", "ヒンディー語"},
            {"hu", "ハンガリー語"},
            {"is", "アイスランド語"},
            {"id", "インドネシア語"},
            {"kn", "カンナダ語"},
            {"lv", "ラトビア語"},
            {"lt", "リトアニア語"},
            {"ms", "マレー語"},
            {"ml", "マラヤーラム語"},
            {"nb", "ノルウェー語（ブークモール）"},
            {"fa", "ペルシア語"},
            {"pl", "ポーランド語"},
            {"ro", "ルーマニア語"},
            {"sr", "セルビア語"},
            {"sk", "スロバキア語"},
            {"sl", "スロベニア語"},
            {"sv", "スウェーデン語"},
            {"ta", "タミル語"},
            {"te", "テルグ語"},
            {"th", "タイ語"},
            {"tr", "トルコ語"},
            {"uk", "ウクライナ語"},
            {"vi", "ベトナム語"}
    };
    private TranslateSettings() {
    }

    private static SharedPreferences prefs(Context context, boolean secret) {
        return context.getApplicationContext().getSharedPreferences(secret ? PREFS_SECRET : PREFS_MAIN, Context.MODE_PRIVATE);
    }

    public static int languageCount() {
        return LANGUAGES.length;
    }

    public static String languageTag(int index) {
        return LANGUAGES[index][0];
    }

    public static String languageLabel(int index) {
        return LANGUAGES[index][1];
    }

    public static int indexOf(String tag) {
        String normalized = "zh".equalsIgnoreCase(tag) ? "zh-CN" : tag;
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i][0].equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    public static String labelFor(String tag) {
        int index = indexOf(tag);
        return index >= 0 ? LANGUAGES[index][1] : tag;
    }

    public static String getTarget(Context context, boolean secret) {
        String value = prefs(context, secret).getString(KEY_TARGET, DEFAULT_TARGET);
        if ("zh".equalsIgnoreCase(value)) {
            value = "zh-CN";
        }
        return indexOf(value) >= 0 ? value : DEFAULT_TARGET;
    }

    public static String getSource(Context context, boolean secret) {
        String value = prefs(context, secret).getString(KEY_SOURCE, AUTO);
        if ("zh".equalsIgnoreCase(value)) {
            value = "zh-CN";
        }
        return AUTO.equals(value) || indexOf(value) >= 0 ? value : AUTO;
    }

    public static boolean isAutoTranslate(Context context, boolean secret) {
        return prefs(context, secret).getBoolean(KEY_AUTO_TRANSLATE, false);
    }

    public static void save(Context context, boolean secret, String source, String target, boolean autoTranslate) {
        prefs(context, secret).edit()
                .putString(KEY_SOURCE, source)
                .putString(KEY_TARGET, target)
                .putBoolean(KEY_AUTO_TRANSLATE, autoTranslate)
                .apply();
    }
}
