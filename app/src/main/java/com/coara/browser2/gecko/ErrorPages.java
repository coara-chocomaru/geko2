package com.coara.browser2.gecko;

import android.util.Base64;
import android.util.LruCache;

import org.mozilla.geckoview.WebRequestError;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class ErrorPages {
    public static final String ACTION_SCHEME = "coara-action:";
    public static final String KIND_SSL = "ssl";
    public static final String KIND_SAFE = "sb";

    private static final int MAX_PAGES_CACHE_KB = 1024;
    private static final LruCache<String, String> PAGES = new LruCache<String, String>(MAX_PAGES_CACHE_KB) {
        @Override
        protected int sizeOf(String key, String value) {
            long bytes = ((long) key.length() + value.length()) * 2L;
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (bytes + 1023L) / 1024L));
        }
    };

    private ErrorPages() {
    }

    public static String failedUrlFor(String dataUri) {
        if (dataUri == null || !dataUri.startsWith("data:")) {
            return null;
        }
        synchronized (PAGES) {
            return PAGES.get(dataUri);
        }
    }

    public static boolean isAction(String uri) {
        return uri != null && uri.regionMatches(true, 0, ACTION_SCHEME, 0, ACTION_SCHEME.length());
    }

    public static String actionOf(String uri) {
        String rest = uri.substring(ACTION_SCHEME.length());
        int q = rest.indexOf('?');
        return q >= 0 ? rest.substring(0, q) : rest;
    }

    public static String paramOf(String uri, String name) {
        int q = uri.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String part : uri.substring(q + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean isSecurityOverridable(WebRequestError e) {
        return e != null && e.category == WebRequestError.ERROR_CATEGORY_SECURITY
                && e.code != WebRequestError.ERROR_BAD_HSTS_CERT;
    }

    public static String describeCertificate(X509Certificate cert, String host) {
        if (cert == null) {
            return "証明書を検証できませんでした。信頼できない認証局、または証明書の不備の可能性があります。";
        }
        StringBuilder out = new StringBuilder();
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            append(out, "証明書の有効期限が切れています。");
        } catch (CertificateNotYetValidException e) {
            append(out, "証明書がまだ有効になっていません。");
        } catch (Exception ignored) {
        }
        if (host != null && !host.isEmpty() && !hostMatches(cert, host)) {
            append(out, "証明書の対象ホスト名とアクセス先が一致していません。");
        }
        try {
            if (cert.getIssuerX500Principal().equals(cert.getSubjectX500Principal())) {
                append(out, "自己署名証明書です。信頼できる認証局により発行されていません。");
            }
        } catch (Exception ignored) {
        }
        if (out.length() == 0) {
            append(out, "証明書を発行した認証局を信頼できない、または検証に失敗しました。");
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String text) {
        if (out.length() > 0) {
            out.append("\n");
        }
        out.append("・").append(text);
    }

    private static boolean hostMatches(X509Certificate cert, String host) {
        String h = host.toLowerCase(Locale.US);
        try {
            Collection<List<?>> names = cert.getSubjectAlternativeNames();
            boolean sawDns = false;
            if (names != null) {
                for (List<?> entry : names) {
                    if (entry.size() >= 2 && entry.get(0) instanceof Integer && ((Integer) entry.get(0)) == 2) {
                        sawDns = true;
                        if (nameMatches(String.valueOf(entry.get(1)).toLowerCase(Locale.US), h)) {
                            return true;
                        }
                    }
                }
            }
            if (sawDns) {
                return false;
            }
        } catch (Exception ignored) {
        }
        try {
            String dn = cert.getSubjectX500Principal().getName();
            for (String part : dn.split(",")) {
                String p = part.trim();
                if (p.regionMatches(true, 0, "CN=", 0, 3)) {
                    return nameMatches(p.substring(3).toLowerCase(Locale.US), h);
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean nameMatches(String pattern, String host) {
        if (pattern.equals(host)) {
            return true;
        }
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            int dot = host.indexOf('.');
            return dot > 0 && host.substring(dot).equals(suffix) && host.indexOf('.', dot + 1) >= 0;
        }
        return false;
    }

    public static String build(String failedUrl, WebRequestError error, boolean autoProceed) {
        String url = failedUrl == null ? "" : failedUrl;
        int cat = error == null ? WebRequestError.ERROR_CATEGORY_UNKNOWN : error.category;
        int code = error == null ? WebRequestError.ERROR_UNKNOWN : error.code;
        String title;
        String body;
        String kind = "";
        boolean canProceed = false;
        boolean danger = false;
        if (cat == WebRequestError.ERROR_CATEGORY_SAFEBROWSING) {
            danger = true;
            kind = KIND_SAFE;
            canProceed = code != WebRequestError.ERROR_HARMFULADDON_URI;
            title = "危険なページとして検出されました";
            body = safeBody(code);
        } else if (cat == WebRequestError.ERROR_CATEGORY_SECURITY) {
            danger = true;
            kind = KIND_SSL;
            canProceed = code != WebRequestError.ERROR_BAD_HSTS_CERT;
            title = "この接続ではプライバシーが保護されません";
            body = "HTTPS証明書を確認できないため、安全な接続を確立できませんでした。このまま開くと、通信内容が第三者に見られたり改ざんされたりする可能性があります。パスワードや認証コード、カード情報は入力しないでください。";
            if (code == WebRequestError.ERROR_BAD_HSTS_CERT) {
                body = "このサイトはHTTPSでの接続を強制しており、証明書に問題があるため続行できません。";
            }
        } else {
            switch (code) {
                case WebRequestError.ERROR_UNKNOWN_HOST:
                    title = "サイトが見つかりません";
                    body = "アドレスが正しいか、ネットワークに接続されているかを確認してください。";
                    break;
                case WebRequestError.ERROR_CONNECTION_REFUSED:
                    title = "接続が拒否されました";
                    body = "サーバーが接続を受け付けていません。時間をおいて再度お試しください。";
                    break;
                case WebRequestError.ERROR_NET_TIMEOUT:
                    title = "接続がタイムアウトしました";
                    body = "サーバーからの応答がありません。通信環境を確認して再読み込みしてください。";
                    break;
                case WebRequestError.ERROR_NET_RESET:
                case WebRequestError.ERROR_NET_INTERRUPT:
                    title = "接続が中断されました";
                    body = "読み込み中に通信が切断されました。再読み込みしてください。";
                    break;
                case WebRequestError.ERROR_OFFLINE:
                    title = "オフラインです";
                    body = "ネットワークに接続されていません。接続を確認してください。";
                    break;
                case WebRequestError.ERROR_REDIRECT_LOOP:
                    title = "リダイレクトが繰り返されています";
                    body = "ページの転送が終わらないため表示できません。Cookieを削除すると解決する場合があります。";
                    break;
                case WebRequestError.ERROR_PORT_BLOCKED:
                    title = "このポートへの接続は制限されています";
                    body = "安全上の理由により、指定されたポートへの接続はできません。";
                    break;
                case WebRequestError.ERROR_MALFORMED_URI:
                case WebRequestError.ERROR_UNKNOWN_PROTOCOL:
                    title = "アドレスが無効です";
                    body = "URLの形式が正しくないか、対応していない形式です。";
                    break;
                case WebRequestError.ERROR_FILE_NOT_FOUND:
                    title = "ファイルが見つかりません";
                    body = "指定された場所にファイルが存在しません。";
                    break;
                case WebRequestError.ERROR_FILE_ACCESS_DENIED:
                    title = "ファイルにアクセスできません";
                    body = "このファイルへのアクセス権限がありません。";
                    break;
                case WebRequestError.ERROR_PROXY_CONNECTION_REFUSED:
                case WebRequestError.ERROR_UNKNOWN_PROXY_HOST:
                    title = "プロキシに接続できません";
                    body = "プロキシ設定を確認してください。";
                    break;
                case WebRequestError.ERROR_CORRUPTED_CONTENT:
                case WebRequestError.ERROR_INVALID_CONTENT_ENCODING:
                case WebRequestError.ERROR_UNSAFE_CONTENT_TYPE:
                    title = "ページを表示できません";
                    body = "受信した内容が壊れているか、安全に表示できない形式です。";
                    break;
                case WebRequestError.ERROR_CONTENT_CRASHED:
                    title = "ページが異常終了しました";
                    body = "再読み込みしてください。";
                    break;
                case WebRequestError.ERROR_HTTPS_ONLY:
                    title = "安全な接続(HTTPS)を利用できません";
                    body = "このサイトはHTTPSに対応していないため、接続をブロックしました。";
                    break;
                default:
                    title = "ページを読み込めませんでした";
                    body = "原因を特定できませんでした。再読み込みするか、時間をおいてお試しください。";
                    break;
            }
        }
        String html;
        if (autoProceed) {
            html = autoProceedHtml(url);
        } else {
            html = pageHtml(title, body, url, cat, code, kind, canProceed, danger);
        }
        String data = "data:text/html;charset=utf-8;base64,"
                + Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        synchronized (PAGES) {
            PAGES.put(data, url);
        }
        return data;
    }

    private static String safeBody(int code) {
        switch (code) {
            case WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI:
                return "マルウェアの配布が確認されている可能性があります。不要なアプリやファイルのインストールを求められる場合があります。";
            case WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI:
                return "正規サイトを装い、ID・パスワード・認証コード・カード情報を入力させるフィッシングの可能性があります。";
            case WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI:
                return "ブラウザや端末の設定を望ましくない形に変更したり、不要なソフトウェアをインストールさせたりする可能性があります。";
            case WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI:
                return "有害または欺瞞的な内容を含む可能性があります。意図しない課金や情報の盗用につながる恐れがあります。";
            default:
                return "既知の危険なコンテンツとして登録されているため、表示をブロックしました。";
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                case '\'':
                    b.append("&#39;");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String autoProceedHtml(String url) {
        String target = ACTION_SCHEME + "reload?u=" + enc(url);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>接続中</title></head><body>"
                + "<script>(function(){var go=function(){location.href=\"" + target + "\";};"
                + "try{document.addCertException(true).then(go,go);}catch(e){go();}})();</script></body></html>";
    }

    private static String pageHtml(String title, String body, String url, int cat, int code,
                                   String kind, boolean canProceed, boolean danger) {
        String enc = enc(url);
        StringBuilder h = new StringBuilder(4096);
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
                .append(esc(title)).append("</title><style>")
                .append(":root{color-scheme:light dark;--bg:#fff;--fg:#202124;--sub:#5f6368;--card:#f6f7f8;--line:#dadce0;--acc:")
                .append(danger ? "#c5221f" : "#1e7a4f")
                .append(";--on:#fff}@media(prefers-color-scheme:dark){:root{--bg:#16181a;--fg:#e8eaed;--sub:#9aa0a6;--card:#202326;--line:#3c4043;--acc:")
                .append(danger ? "#f28b82" : "#7fd6a7")
                .append(";--on:#10130f}}")
                .append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.6 system-ui,sans-serif;padding:20px}")
                .append(".w{max-width:560px;margin:8vh auto 0}h1{font-size:20px;margin:0 0 10px;color:var(--acc)}p{margin:0 0 14px;color:var(--sub)}")
                .append(".u{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:10px 12px;word-break:break-all;font-size:13px;color:var(--fg);margin:0 0 16px}")
                .append(".c{font-size:12px;color:var(--sub);margin:0 0 18px}.b{display:flex;gap:10px;flex-wrap:wrap}")
                .append("a.k{display:inline-block;padding:11px 18px;border-radius:999px;text-decoration:none;font-weight:600;border:1px solid var(--acc);color:var(--acc)}")
                .append("a.p{background:var(--acc);color:var(--on)}</style></head><body><div class=\"w\"><h1>")
                .append(esc(title)).append("</h1><p>").append(esc(body)).append("</p>");
        if (!url.isEmpty()) {
            h.append("<div class=\"u\">").append(esc(url)).append("</div>");
        }
        h.append("<div class=\"c\">エラーコード: ").append(String.format(Locale.US, "0x%x / 0x%x", cat, code)).append("</div><div class=\"b\">");
        if (danger) {
            h.append("<a class=\"k p\" href=\"").append(ACTION_SCHEME).append("back\">前に戻る</a>");
            if (canProceed) {
                h.append("<a class=\"k\" href=\"").append(ACTION_SCHEME).append("proceed?k=").append(kind)
                        .append("&u=").append(enc).append("\">このページを開く</a>");
            }
        } else {
            h.append("<a class=\"k p\" href=\"").append(ACTION_SCHEME).append("reload?u=").append(enc).append("\">再読み込み</a>");
            h.append("<a class=\"k\" href=\"").append(ACTION_SCHEME).append("back\">前に戻る</a>");
        }
        h.append("</div></div></body></html>");
        return h.toString();
    }
}
