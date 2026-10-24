package com.coara.browser2.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FxPermissions {
    private static final Map<String, String> LABELS = new HashMap<>();

    static {
        LABELS.put("activeTab", "現在のタブへのアクセス(クリック時のみ)");
        LABELS.put("alarms", "アラームの設定");
        LABELS.put("background", "バックグラウンドでの実行");
        LABELS.put("bookmarks", "ブックマークの読み取りと変更");
        LABELS.put("browserSettings", "ブラウザ設定の読み取りと変更");
        LABELS.put("browsingData", "閲覧データの消去");
        LABELS.put("captivePortal", "キャプティブポータルの状態の取得");
        LABELS.put("clipboardRead", "クリップボードからのデータ取得");
        LABELS.put("clipboardWrite", "クリップボードへのデータ書き込み");
        LABELS.put("contextMenus", "コンテキストメニューの追加");
        LABELS.put("contextualIdentities", "コンテナの管理");
        LABELS.put("cookies", "Cookieの読み取りと変更");
        LABELS.put("devtools", "開発者ツールの拡張");
        LABELS.put("dns", "DNS解決");
        LABELS.put("downloads", "ファイルのダウンロードと履歴の管理");
        LABELS.put("downloads.open", "ダウンロードしたファイルを開く");
        LABELS.put("find", "ページ内検索");
        LABELS.put("geolocation", "位置情報へのアクセス");
        LABELS.put("history", "閲覧履歴へのアクセス");
        LABELS.put("identity", "ログイン認証");
        LABELS.put("idle", "端末のアイドル状態の取得");
        LABELS.put("management", "拡張機能の管理");
        LABELS.put("menus", "メニューの追加");
        LABELS.put("nativeMessaging", "他のアプリとの通信");
        LABELS.put("nativeMessagingFromContent", "ページ内コンテンツスクリプトからネイティブアプリとの通信");
        LABELS.put("geckoViewAddons", "GeckoView本体との特権通信");
        LABELS.put("notifications", "通知の表示");
        LABELS.put("pageCapture", "ページの保存");
        LABELS.put("privacy", "プライバシー設定の読み取りと変更");
        LABELS.put("proxy", "プロキシ設定の制御");
        LABELS.put("search", "検索エンジンでの検索");
        LABELS.put("sessions", "最近閉じたタブの取得と復元");
        LABELS.put("storage", "拡張機能のデータ保存");
        LABELS.put("tabHide", "タブの非表示");
        LABELS.put("tabs", "タブ情報(URL・タイトルなど)の取得");
        LABELS.put("theme", "テーマの変更");
        LABELS.put("topSites", "よく見るサイトの取得");
        LABELS.put("unlimitedStorage", "無制限のデータ保存");
        LABELS.put("webNavigation", "ページ遷移の監視");
        LABELS.put("webRequest", "通信内容の監視");
        LABELS.put("webRequestBlocking", "通信のブロックと変更");
        LABELS.put("webRequestFilterResponse", "通信内容のフィルタ");
        LABELS.put("userScripts", "ユーザースクリプトの実行");
        LABELS.put("scripting", "ページへのスクリプト挿入");
        LABELS.put("declarativeNetRequest", "通信ルールによるブロック");
        LABELS.put("declarativeNetRequestFeedback", "ブロックされた通信の確認");
        LABELS.put("declarativeNetRequestWithHostAccess", "サイト別の通信ルール");
        LABELS.put("offscreen", "非表示ドキュメントの作成");
        LABELS.put("sidePanel", "サイドパネルの表示");
        LABELS.put("<all_urls>", "すべてのサイトのデータへのアクセス");
        LABELS.put("*://*/*", "すべてのサイトのデータへのアクセス");
        LABELS.put("http://*/*", "すべてのHTTPサイトのデータへのアクセス");
        LABELS.put("https://*/*", "すべてのHTTPSサイトのデータへのアクセス");
        LABELS.put("file://*/*", "端末内ファイルの読み取りと変更");
        LABELS.put("ftp://*/*", "すべてのFTPサイトのデータへのアクセス");
    }

    private FxPermissions() {
    }

    public static String label(String permission) {
        String known = LABELS.get(permission);
        if (known != null) {
            return known;
        }
        if (permission.startsWith("*://") || permission.startsWith("http://") || permission.startsWith("https://")
                || permission.startsWith("ftp://") || permission.startsWith("file://") || permission.contains("://")) {
            return permission + " のデータへのアクセス";
        }
        return permission;
    }

    public static String dataLabel(String permission) {
        switch (permission) {
            case "none":
                return "データ収集なし";
            case "authenticationInfo":
                return "認証情報";
            case "bookmarksInfo":
                return "ブックマーク情報";
            case "browsingActivity":
                return "閲覧アクティビティ";
            case "financialAndPaymentInfo":
                return "金融・支払い情報";
            case "healthInfo":
                return "健康情報";
            case "locationInfo":
                return "位置情報";
            case "personalCommunications":
                return "個人的な通信";
            case "personallyIdentifyingInfo":
                return "個人を特定できる情報";
            case "searchTerms":
                return "検索語句";
            case "technicalAndInteraction":
                return "技術情報と操作データ";
            case "websiteActivity":
                return "ウェブサイトのアクティビティ";
            case "websiteContent":
                return "ウェブサイトのコンテンツ";
            default:
                return permission;
        }
    }

    public static List<String> labels(String[] permissions, String[] origins) {
        List<String> out = new ArrayList<>();
        boolean allSites = false;
        if (permissions != null) {
            for (String p : permissions) {
                if (p.equals("<all_urls>") || p.equals("*://*/*")) {
                    allSites = true;
                }
            }
        }
        if (permissions != null) {
            for (String p : permissions) {
                String l = label(p);
                if (!out.contains(l)) {
                    out.add(l);
                }
            }
        }
        if (origins != null) {
            for (String o : origins) {
                String l = label(o);
                if (allSites && (o.equals("<all_urls>") || o.equals("*://*/*"))) {
                    l = label("<all_urls>");
                }
                if (!out.contains(l)) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    public static boolean isBroad(String[] permissions, String[] origins) {
        for (String[] list : new String[][]{permissions, origins}) {
            if (list == null) {
                continue;
            }
            for (String p : list) {
                if (p.equals("<all_urls>") || p.equals("*://*/*") || p.equals("http://*/*")
                        || p.equals("https://*/*") || p.equals("file://*/*") || p.equals("ftp://*/*")) {
                    return true;
                }
            }
        }
        return false;
    }
}
