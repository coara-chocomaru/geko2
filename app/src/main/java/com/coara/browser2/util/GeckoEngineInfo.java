package com.coara.browser2.util;

import android.content.Context;
import android.os.Build;

import com.coara.browser2.gecko.BrowserEngine;

import java.util.Arrays;

public final class GeckoEngineInfo {
    private GeckoEngineInfo() {
    }

    private static String token(String ua, String key) {
        if (ua == null) {
            return "不明";
        }
        int i = ua.indexOf(key);
        if (i < 0) {
            return "不明";
        }
        int start = i + key.length();
        int end = start;
        while (end < ua.length() && ua.charAt(end) != ' ' && ua.charAt(end) != ')' && ua.charAt(end) != ';') {
            end++;
        }
        return ua.substring(start, end);
    }

    public static String collect(Context context) {
        StringBuilder out = new StringBuilder();
        String ua = null;
        boolean ready = false;
        try {
            if (BrowserEngine.isInitialized()) {
                BrowserEngine engine = BrowserEngine.get();
                ua = engine.getDefaultUserAgent();
                ready = engine.isAgentReady();
            }
        } catch (Throwable ignored) {
        }
        out.append("Gecko エンジン\n\n");
        out.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("ABI: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        out.append("Firefox バージョン: ").append(token(ua, "Firefox/")).append('\n');
        out.append("Gecko ビルド: ").append(token(ua, "Gecko/")).append('\n');
        out.append("内蔵エージェント: ").append(ready ? "有効" : "未接続").append('\n');
        if (ua != null) {
            out.append("User-Agent: ").append(ua).append('\n');
        }
        out.append("\nこのアプリはMozillaのGeckoViewエンジンを内蔵しており、端末のWebViewには依存しません。\n");
        out.append("Firefox拡張機能(WebExtensions)に対応し、拡張機能の管理画面から追加できます。");
        return out.toString();
    }
}
