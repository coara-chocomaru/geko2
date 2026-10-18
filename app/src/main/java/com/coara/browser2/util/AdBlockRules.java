package com.coara.browser2.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdBlockRules {

    private static final String[] HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "google-analytics.com", "adservice.google.com", "2mdn.net", "admob.com", "googleadapis.com",
        "app-measurement.com", "amazon-adsystem.com", "assoc-amazon.com", "assoc-amazon.jp",
        "ads.yahoo.com", "ad.yahoo.com", "adtech.yahooinc.com", "adserver.yahoo.com",
        "yads.c.yimg.jp", "ads.yahoo.co.jp", "yads.yahoo.co.jp", "yjtag.yahoo.co.jp", "log.fc.yahoo.co.jp",
        "i-mobile.co.jp", "nend.net", "adstir.com", "fluct.jp", "geniee.co.jp", "logly.co.jp",
        "microad.jp", "microad.net", "smartnews-ads.com", "ad.about.co.jp", "adcolony.com", "adingo.jp",
        "adlantis.jp", "amoad.com", "amoad.net", "apester.com", "ccc-fc.com", "everesttech.net",
        "gmossp-sp.jp", "im-apps.net", "impact-ad.jp", "insnative.com", "maio.jp", "o2on.jp",
        "popin.cc", "scaleout.co.jp", "ssp.geniee.co.jp", "valuecommerce.com", "zucks.net", "ad-mobile.jp",
        "taboola.com", "taboolanews.com", "outbrain.com", "criteo.com", "criteo.net", "pubmatic.com",
        "rubiconproject.com", "magnite.com", "openx.net", "adnxs.com", "adnxs-simple.com", "adsrvr.org",
        "casalemedia.com", "contextweb.com", "crwdcntrl.net", "demdex.net", "exelator.com", "lijit.com",
        "mathtag.com", "ml314.com", "nexac.com", "360yield.com", "bluekai.com", "agkn.com",
        "adsafeprotected.com", "moatads.com", "scorecardresearch.com", "quantserve.com", "adform.net",
        "adroll.com", "bidswitch.net", "brightroll.com", "conversantmedia.com", "dotomi.com",
        "doubleverify.com", "e-planning.net", "eyeota.net", "ezoic.net", "freewheel.tv", "gumgum.com",
        "indexexchange.com", "inmobi.com", "integralads.com", "iponweb.net", "lotame.com",
        "mediamath.com", "mookie1.com", "nativo.com", "netmng.com", "omnitagjs.com", "owneriq.net",
        "pippio.com", "pool.admedo.com", "rlcdn.com", "rtbhouse.com", "serving-sys.com",
        "sharethrough.com", "simpli.fi", "smaato.net", "sonobi.com", "sovrn.com", "spotxchange.com",
        "stackadapt.com", "tapad.com", "teads.tv", "thetradedesk.com", "tidaltv.com", "tribalfusion.com",
        "triplelift.com", "tremorhub.com", "valueclick.com", "vungle.com", "yumenetworks.com",
        "zedo.com", "zeotap.com", "popads.net", "popcash.net", "propellerads.com", "propellerpops.com",
        "adcash.com", "popmyads.com", "onclickads.net", "clickadu.com", "hilltopads.net", "adsterra.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "trafficstars.com", "popunder.ru",
        "popunderjs.com", "mouseflow.com", "fullstory.com", "mixpanel.com", "segment.io", "kissmetrics.com",
        "statcounter.com", "mc.yandex.ru", "metrika.yandex.ru", "appmetrica.yandex.net",
        "pushengage.com", "pushwoosh.com", "cleverpush.com", "webpushr.com", "izooto.com", "pushnami.com",
        "adnium.com", "bidvertiser.com", "buysellads.com", "chitika.com", "clicksor.com", "cpalead.com",
        "engagebdr.com", "epom.com", "infolinks.com", "kargo.com", "kontera.com", "liveintent.com",
        "liverail.com", "loopme.me", "magnetic.com", "nexage.com", "nuffnang.com", "peer39.com",
        "pixalate.com", "platform161.com", "pulsepoint.com", "revcontent.com", "rhythmone.com",
        "rocketfuel.com", "sekindo.com", "smartadserver.com", "sociomantic.com", "spotx.tv",
        "stickyadstv.com", "tapjoy.com", "teracent.com", "tremorvideo.com", "undertone.com",
        "visiblemeasures.com", "yieldbot.com", "yieldmo.com", "yieldoptimizer.com", "zergnet.com",
        "zemanta.com", "adfox.ru", "adfox.yandex.ru", "an.yandex.ru", "awaps.yandex.ru",
        "ads.adfox.ru", "banners.adfox.ru", "ads.rambler.ru", "ad.dumedia.ru", "ads.digitalcaramel.com",
        "ad.mail.ru", "ads.imgsmail.ru", "ads-twitter.com", "analytics.twitter.com", "ads.linkedin.com",
        "px.ads.linkedin.com", "snap.licdn.com", "analytics.tiktok.com", "bat.bing.com", "ads.bing.com",
        "hotjar.com", "clarity.ms", "adsco.re", "pagead2.googlesyndication.com", "ads.pubmatic.com",
        "adsymptotic.com", "adsby.bidtheatre.com", "advertising.com", "ads.stickyadstv.com",
        "mopub.com", "applovin.com", "unityads.unity3d.com", "ironsrc.com",
        "supersonicads.com", "chartboost.com",
        "ads.twitter.com", "static.ads-twitter.com", "ads.tiktok.com", "business-api.tiktok.com",
        "adition.com", "adzerk.net", "carbonads.com", "carbonads.net", "srv.carbonads.net", "exponential.com",
        "tynt.com", "sizmek.com", "flashtalking.com", "adblade.com", "bidr.io", "btloader.com", "connatix.com",
        "cpmstar.com", "media.net", "ad-delivery.net", "adskeeper.com", "mgid.com", "playwire.com",
        "ay.delivery", "bttrack.com", "adthrive.com", "raptive.com", "mediavine.com", "adlightning.com",
        "adnuntius.com", "seedtag.com", "unruly.co", "unrulymedia.com", "smartclip.net", "improvedigital.com",
        "33across.com", "admixer.net", "rubiconproject.net", "gadsme.com",
        "a8.net", "accesstrade.net", "afl.rakuten.co.jp", "moshimo.com", "trafficgate.net", "fout.jp",
        "rtoaster.jp", "adnetwork.yahoo.co.jp", "ads-api.twitter.com",
        "ads.google.com", "ads.youtube.com", "adsterra.net", "bidgear.com", "monetag.com",
        "richpush.co", "notix.io", "pushails.com", "evadav.com", "pushprofit.net", "galaksion.com",
        "tsyndicate.com", "trafficfactory.biz", "ero-advertising.com", "realsrv.com", "syndication.realsrv.com",
        "adspyglass.com", "yllix.com", "a-ads.com", "coinzilla.com", "coinzillatag.com", "bitmedia.io",
        "cointraffic.io", "ad.gt", "nativeads.com", "revcontent.net", "content.ad",
        "contentad.net", "adblockanalytics.com", "pagefair.com", "pagefair.net", "adsrvmedia.net", "tsyndicate.net", "viglink.com", "skimlinks.com",
        "skimresources.com", "linksynergy.com", "shareasale.com", "impactradius.com", "cj.com",
        "dpbolvw.net", "tkqlhce.com", "jdoqocy.com", "anrdoezrs.net", "awin1.com", "zenaps.com"
    };

    private static final String[] PATH_RULES = {
        "www.google.com/pagead/",
        "www.google.com/ads/",
        "www.google.co.jp/pagead/",
        "www.googletagmanager.com/gtm.js",
        "www.googletagmanager.com/gtag/js",
        "www.facebook.com/tr/",
        "www.facebook.com=/tr",
        "connect.facebook.net*fbevents.js",
        "www.youtube.com/pagead/",
        "m.youtube.com/pagead/",
        "www.youtube.com/api/stats/ads",
        "m.youtube.com/api/stats/ads",
        "www.youtube.com/ptracking",
        "m.youtube.com/ptracking",
        "s.youtube.com/api/stats/ads",
        "www.google.co.jp/ads/"
    };

    private static final String[] AD_HOST_PREFIXES = {
        "ads.", "ad.", "adserver.", "adservice.", "adsrv.", "adx.", "banner.", "banners.", "pagead.", "adclick.", "adtrack."
    };

    private static final String[] AD_PATH_TOKENS = {
        "/ads/", "/adserver/", "/adframe", "/adview", "/adclick", "/adsense/", "/banners/", "/banner-ads/",
        "/banner_ads/", "/advert/", "/adverts/", "/advertising/", "/adimages/", "/ad-images/", "/ad_images/",
        "/pagead/", "/doubleclick/", "/show_ads", "adsbygoogle.js", "/prebid", "/adsystem/", "/adserve/",
        "/adrequest", "/ad_banner", "/ad-banner", "/adbanner"
    };

    private static final String[] SECOND_LEVEL = {
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp", "ed.jp", "gr.jp", "co.uk", "org.uk", "ac.uk", "gov.uk",
        "com.au", "net.au", "org.au", "co.nz", "co.kr", "or.kr", "com.br", "com.cn", "com.tw", "com.hk",
        "com.sg", "co.in", "co.id", "com.mx", "com.tr", "co.za", "com.ar", "com.ua", "co.th", "com.my"
    };

    private static final String[] HAS_SELECTORS = {
        "ytd-rich-item-renderer:has(ytd-ad-slot-renderer)",
        "ytd-rich-section-renderer:has(ytd-ad-slot-renderer)",
        "ytd-rich-section-renderer:has(ytd-statement-banner-renderer)",
        "ytd-item-section-renderer:has(ytd-ad-slot-renderer)",
        "ytd-rich-item-renderer:has(.ytd-display-ad-renderer)",
        "ytm-rich-item-renderer:has(ad-slot-renderer)",
        "ytm-item-section-renderer:has(ad-slot-renderer)",
        "ytm-rich-section-renderer:has(ad-slot-renderer)"
    };

    private static final String[] AD_SELECTORS = {
        "ins.adsbygoogle",
        ".adsbygoogle",
        "[id^=\"google_ads_\"]",
        "[id^=\"aswift_\"]",
        "[id^=\"div-gpt-ad\"]",
        "[id^=\"taboola-\"]",
        "[id^=\"outbrain-\"]",
        "[id^=\"criteo-\"]",
        "[id^=\"amzn_assoc_\"]",
        "[data-ad-slot]",
        "[data-ad-client]",
        "[data-google-query-id]",
        "[data-ad-unit]",
        "[data-adunit]",
        "[data-ad-format]",
        "iframe[id^=\"google_ads_iframe\"]",
        "iframe[id^=\"aswift\"]",
        "iframe[name^=\"google_ads\"]",
        "iframe[src*=\"doubleclick.net\"]",
        "iframe[src*=\"googlesyndication.com\"]",
        "iframe[src*=\"googleadservices.com\"]",
        "iframe[src*=\"amazon-adsystem.com\"]",
        "iframe[src*=\"ads.yahoo.co.jp\"]",
        "iframe[src*=\"yads.yahoo.co.jp\"]",
        "iframe[src*=\"taboola.com\"]",
        "iframe[src*=\"outbrain.com\"]",
        "iframe[src*=\"criteo.com\"]",
        "iframe[src*=\"/pagead/\"]",
        ".ad-slot",
        ".ad_slot",
        ".ad-unit",
        ".ad_unit",
        ".ad-container",
        ".ad_container",
        ".ad-wrapper",
        ".ad_wrapper",
        ".ad-holder",
        ".ad_holder",
        ".ad-frame",
        ".ad_frame",
        ".adbanner",
        ".ad-banner-wrap",
        ".banner-ad",
        ".banner_ad",
        ".google-ad",
        ".google_ad",
        ".googleAd",
        ".dfp-ad",
        ".gpt-ad",
        ".taboola",
        ".trc_related_container",
        ".OUTBRAIN",
        ".ob-widget",
        ".sponsored-ad",
        ".popunder",
        ".pop-under",
        "[aria-label=\"advertisement\" i]",
        "[aria-label=\"広告\"]",
        "[data-testid=\"ad\"]",
        "[data-ad]",
        "#masthead-ad",
        "#player-ads",
        ".ytp-ad-overlay-container",
        ".ytp-ad-image-overlay",
        "ytd-promoted-sparkles-web-renderer",
        "ytd-display-ad-renderer",
        "ytd-promoted-video-renderer",
        "ytd-in-feed-ad-layout-renderer",
        "ytd-ad-slot-renderer",
        "ytd-banner-promo-renderer",
        "ytd-statement-banner-renderer",
        "ytd-companion-slot-renderer",
        "ytd-action-companion-ad-renderer",
        "ytm-promoted-sparkles-web-renderer",
        "ytm-companion-ad-renderer",
        "ytm-promoted-video-renderer",
        "ytm-statement-banner-renderer",
        "ad-slot-renderer",
        ".ima-ad-container",
        "#onetrust-banner-sdk",
        "#onetrust-consent-sdk",
        ".onetrust-pc-dark-filter",
        "#CybotCookiebotDialog",
        "#CybotCookiebotDialogBodyUnderlay",
        ".cc-window",
        ".cc-banner",
        "#cookie-law-info-bar",
        "#cmplz-cookiebanner-container",
        "#cookiescript_injected",
        ".cookie-notice-container",
        "#cookie-notice",
        ".iubenda-cs-container",
        "iframe[src*=\"/ads/\"]",
        "iframe[src*=\"adserver\"]",
        "iframe[title=\"Advertisement\" i]",
        "iframe[title*=\"広告\"]",
        "iframe[title*=\"3rd party ad content\" i]",
        "iframe[id*=\"google_ads\"]",
        "iframe[src*=\"a8.net\"]",
        "iframe[src*=\"i-mobile.co.jp\"]",
        "iframe[src*=\"nend.net\"]",
        "iframe[src*=\"adstir.com\"]",
        "iframe[src*=\"microad\"]",
        "iframe[src*=\"mgid.com\"]",
        "iframe[src*=\"adnxs.com\"]",
        "iframe[src*=\"pubmatic.com\"]",
        "iframe[src*=\"rubiconproject.com\"]",
        "iframe[src*=\"adsrvr.org\"]",
        ".ad-box",
        ".adbox",
        ".ad-area",
        ".ad_area",
        ".ads-box",
        ".ads-wrap",
        ".ads-container",
        ".ads-banner",
        ".ad_banner",
        ".ad-sidebar",
        ".top-ad",
        ".bottom-ad",
        ".sidebar-ad",
        ".header-ad",
        ".footer-ad",
        ".inline-ad",
        ".in-article-ad",
        ".native-ad",
        ".nativead",
        ".advertisement",
        ".advertisements",
        ".advert",
        ".advert-banner",
        "#advertisement",
        ".sponsored-content",
        ".sponsored-post",
        ".sponsored-link",
        ".sponsored-links",
        ".sponsored-listing",
        "[data-sponsored]",
        "[data-is-sponsored=\"true\"]",
        "[data-promoted]",
        "[aria-label=\"Sponsored\" i]",
        "[aria-label=\"スポンサー\"]",
        "[aria-label=\"プロモーション\"]",
        "[id^=\"dfp-\"]",
        "[id^=\"gpt-ad\"]",
        "[id^=\"ad-slot\"]",
        "[id^=\"adcontainer\"]",
        "[id^=\"yads\"]",
        "[id^=\"yjad\"]",
        "[class*=\"yjAd\"]",
        "[id^=\"mgid_\"]",
        ".mgbox",
        "ytd-enforcement-message-view-model",
        "ytd-player-legacy-desktop-watch-ads-renderer",
        "ytd-video-masthead-ad-v3-renderer",
        "ytd-brand-video-singleton-renderer",
        "ytd-brand-video-shelf-renderer",
        "ytd-carousel-ad-renderer",
        "ytd-search-pyv-renderer",
        ".ytp-ad-message-overlay",
        ".ytp-ad-overlay-slot"
    };

    private static final Set<String> HOST_SET = new HashSet<>(Arrays.asList(HOSTS));
    private static final Map<String, String[]> PATH_MAP = new HashMap<>();

    static {
        Map<String, java.util.List<String>> tmp = new HashMap<>();
        for (String rule : PATH_RULES) {
            String host;
            String needle;
            int star = rule.indexOf('*');
            int eq = rule.indexOf('=');
            if (eq >= 0) {
                host = rule.substring(0, eq);
                needle = rule.substring(eq);
                star = -1;
            } else if (star >= 0) {
                host = rule.substring(0, star);
                needle = rule.substring(star + 1);
            } else {
                int slash = rule.indexOf('/');
                host = rule.substring(0, slash);
                needle = rule.substring(slash);
            }
            java.util.List<String> list = tmp.get(host);
            if (list == null) {
                list = new java.util.ArrayList<>();
                tmp.put(host, list);
            }
            list.add(eq >= 0 ? needle : (star >= 0 ? "*" + needle : needle));
        }
        for (Map.Entry<String, java.util.List<String>> e : tmp.entrySet()) {
            PATH_MAP.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
    }

    private AdBlockRules() {
    }

    public static String lower(String s) {
        if (s == null) {
            return null;
        }
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return s.toLowerCase(Locale.ROOT);
            }
        }
        return s;
    }

    public static String matchHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        String h = host;
        while (true) {
            if (HOST_SET.contains(h)) {
                return h;
            }
            int dot = h.indexOf('.');
            if (dot < 0) {
                return null;
            }
            h = h.substring(dot + 1);
            if (h.indexOf('.') < 0) {
                return HOST_SET.contains(h) ? h : null;
            }
        }
    }

    private static boolean isSelf(String pageHost, String matched) {
        if (pageHost == null) {
            return false;
        }
        return pageHost.equals(matched) || pageHost.endsWith("." + matched);
    }

    private static boolean matchPath(String host, String path) {
        if (path == null) {
            path = "";
        }
        String h = host;
        while (true) {
            String[] rules = PATH_MAP.get(h);
            if (rules != null) {
                for (String r : rules) {
                    char first = r.charAt(0);
                    if (first == '*') {
                        if (path.contains(r.substring(1))) {
                            return true;
                        }
                    } else if (first == '=') {
                        if (path.equals(r.substring(1))) {
                            return true;
                        }
                    } else if (path.startsWith(r)) {
                        return true;
                    }
                }
            }
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) {
                return false;
            }
            h = h.substring(dot + 1);
        }
    }

    public static String registrable(String host) {
        if (host == null) {
            return "";
        }
        int last = host.lastIndexOf('.');
        if (last <= 0) {
            return host;
        }
        int prev = host.lastIndexOf('.', last - 1);
        if (prev < 0) {
            return host;
        }
        String tail2 = host.substring(prev + 1);
        for (String sl : SECOND_LEVEL) {
            if (tail2.equals(sl)) {
                int p3 = host.lastIndexOf('.', prev - 1);
                return p3 < 0 ? host : host.substring(p3 + 1);
            }
        }
        return tail2;
    }

    private static boolean trustedPage(String pageHost) {
        String r = registrable(pageHost);
        return r.equals("youtube.com") || r.equals("google.com") || r.equals("google.co.jp")
            || r.equals("youtube-nocookie.com") || r.equals("gstatic.com") || r.equals("ytimg.com");
    }

    public static boolean isThirdParty(String host, String pageHost) {
        if (pageHost == null || pageHost.isEmpty() || host == null) {
            return false;
        }
        if (host.equals(pageHost)) {
            return false;
        }
        return !registrable(host).equals(registrable(pageHost));
    }

    private static boolean labelCount(String host, int min) {
        int n = 1;
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) == '.') {
                n++;
            }
        }
        return n >= min;
    }

    private static boolean adHostPrefix(String host) {
        if (!labelCount(host, 3)) {
            return false;
        }
        for (String p : AD_HOST_PREFIXES) {
            if (host.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean adPathToken(String path) {
        if (path == null || path.length() < 4) {
            return false;
        }
        String p = lower(path);
        for (String t : AD_PATH_TOKENS) {
            if (p.contains(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldBlock(String host, String path, String pageHost) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String matched = matchHost(host);
        if (matched != null) {
            return !isSelf(pageHost, matched);
        }
        if (matchPath(host, path)) {
            return pageHost == null || !(pageHost.equals(host) || pageHost.endsWith("." + host));
        }
        if (isThirdParty(host, pageHost) && !trustedPage(pageHost)) {
            return adHostPrefix(host) || adPathToken(path);
        }
        return false;
    }

    public static String cosmeticCss() {
        StringBuilder sb = new StringBuilder(4096);
        for (int i = 0; i < AD_SELECTORS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(AD_SELECTORS[i]);
        }
        sb.append("{display:none!important;visibility:hidden!important;}");
        return sb.toString();
    }

    public static String cosmeticHasCss() {
        StringBuilder sb = new StringBuilder(512);
        for (int i = 0; i < HAS_SELECTORS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(HAS_SELECTORS[i]);
        }
        sb.append("{display:none!important;}");
        return sb.toString();
    }

    private static String hostsJson() {
        StringBuilder sb = new StringBuilder(HOSTS.length * 20);
        sb.append('[');
        for (int i = 0; i < HOSTS.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsQuote(HOSTS[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public static String[] selectors() {
        return AD_SELECTORS.clone();
    }

    private static String jsQuote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String rulesJson() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            o.put("hosts", new org.json.JSONArray(Arrays.asList(HOSTS)));
            org.json.JSONObject paths = new org.json.JSONObject();
            for (Map.Entry<String, String[]> e : PATH_MAP.entrySet()) {
                paths.put(e.getKey(), new org.json.JSONArray(Arrays.asList(e.getValue())));
            }
            o.put("paths", paths);
            o.put("prefixes", new org.json.JSONArray(Arrays.asList(AD_HOST_PREFIXES)));
            o.put("tokens", new org.json.JSONArray(Arrays.asList(AD_PATH_TOKENS)));
            o.put("second", new org.json.JSONArray(Arrays.asList(SECOND_LEVEL)));
        } catch (org.json.JSONException ignored) {
        }
        return o.toString();
    }

    public static String pageWorldWrapper(String code) {
        return "(function(){try{window.eval(" + jsQuote(code) + ");}catch(e){}})();";
    }

    public static String buildScript() {
        StringBuilder sb = new StringBuilder(16384);
        for (String line : SCRIPT_LINES) {
            sb.append(line).append('\n');
        }
        String out = sb.toString();
        out = out.replace("__CSSHAS__", jsQuote(cosmeticHasCss()));
        out = out.replace("__CSS__", jsQuote(cosmeticCss()));
        out = out.replace("__HOSTS__", hostsJson());
        return out;
    }

    private static final String[] SCRIPT_LINES = {
        "(function(){",
        "var W=window;",
        "if(W.__coaraAb){try{W.__coaraAb.on();}catch(e){}return;}",
        "var css=__CSS__;",
        "var cssHas=__CSSHAS__;",
        "var HOSTS=__HOSTS__;",
        "var set={};",
        "for(var hi=0;hi<HOSTS.length;hi++){set[HOSTS[hi]]=1;}",
        "var on=true,st=null,st2=null,timer=0,prev=null,mo=null,sweepTimer=0,queue=[],ytMo=null;",
        "var host=(location.hostname||'').toLowerCase();",
        "var isYt=/(^|\\.)(youtube\\.com|youtube-nocookie\\.com)$/.test(host);",
        "function endsWith(s,x){return s.length>=x.length&&s.lastIndexOf(x)===s.length-x.length;}",
        "function sameSite(a,b){return a===b||endsWith(a,'.'+b)||endsWith(b,'.'+a);}",
        "function blockedHost(h){",
        "if(!h)return false;",
        "while(true){",
        "if(set[h]===1)return true;",
        "var d=h.indexOf('.');",
        "if(d<0)return false;",
        "h=h.substring(d+1);",
        "if(h.indexOf('.')<0)return set[h]===1;}}",
        "function hostOf(u){",
        "if(!u)return '';",
        "var m=/^(?:https?:)?\\/\\/([^\\/?#:]+)/i.exec(u);",
        "return m?m[1].toLowerCase():'';}",
        "function mountOne(o,text){",
        "if(o&&o.parentNode){o.disabled=!on;return o;}",
        "var p=document.head||document.documentElement;",
        "if(!p)return null;",
        "var s=document.createElement('style');",
        "s.setAttribute('data-coara-ab','1');",
        "s.textContent=text;",
        "p.appendChild(s);",
        "s.disabled=!on;",
        "return s;}",
        "function mount(){",
        "st=mountOne(st,css);",
        "st2=mountOne(st2,cssHas);",
        "return !!(st&&st2);}",
        "if(!mount()){",
        "var mm=new MutationObserver(function(){if(mount()){mm.disconnect();}});",
        "mm.observe(document,{childList:true,subtree:true});}",
        "function hide(el){",
        "if(!el||!el.setAttribute)return;",
        "try{",
        "el.setAttribute('data-coara-ab-hid','1');",
        "el.style.setProperty('display','none','important');",
        "}catch(e){}}",
        "function unhideAll(){",
        "try{",
        "var l=document.querySelectorAll('[data-coara-ab-hid]');",
        "for(var i=0;i<l.length;i++){",
        "l[i].removeAttribute('data-coara-ab-hid');",
        "l[i].style.removeProperty('display');}",
        "}catch(e){}}",
        "function checkEl(el){",
        "var t=el.tagName;",
        "if(t!=='IFRAME'&&t!=='IMG'&&t!=='EMBED'&&t!=='OBJECT')return;",
        "var s=el.getAttribute('src')||el.getAttribute('data')||'';",
        "var h=hostOf(s);",
        "if(h&&!sameSite(h,host)&&blockedHost(h)){hide(el);}}",
        "var LABEL=/^(広告|PR|ＰＲ|\\[PR\\]|【PR】|【広告】|［PR］|スポンサー|スポンサー広告|スポンサードリンク|プロモーション|Sponsored|Promoted|Advertisement|Ad|AD|Ads|Sponsored content|Promoted content)$/;",
        "var ITEM=/(^|[\\s_-])(card|item|post|entry|story|tile|article|result|listing)([\\s_-]|$)/i;",
        "function labelCheck(tn){",
        "var v=tn.nodeValue;",
        "if(!v||v.length>24)return;",
        "v=v.replace(/^\\s+|\\s+$/g,'');",
        "if(!v||!LABEL.test(v))return;",
        "var el=tn.parentElement;",
        "if(!el||el.children.length>0)return;",
        "var c=el,n=0;",
        "while(c&&n<6){",
        "c=c.parentElement;",
        "n++;",
        "if(!c)return;",
        "var tg=c.tagName;",
        "if(tg==='BODY'||tg==='HTML'||tg==='MAIN'||tg==='NAV'||tg==='FOOTER'||tg==='HEADER'||tg==='UL'||tg==='OL')return;",
        "var cn=typeof c.className==='string'?c.className:'';",
        "if(tg==='LI'||tg==='ARTICLE'||c.getAttribute('role')==='listitem'||ITEM.test(cn)){",
        "var tl=(c.textContent||'').length;",
        "var par=c.parentElement;",
        "if(tl>v.length+2&&tl<700&&par&&par.children.length>1){hide(c);}",
        "return;}}}",
        "function enqueue(n){",
        "if(queue.length<400){queue.push(n);}",
        "if(!sweepTimer){sweepTimer=setTimeout(flush,200);}}",
        "function flush(){",
        "sweepTimer=0;",
        "if(!on||document.hidden){queue=[];return;}",
        "var q=queue;",
        "queue=[];",
        "var budget=12000;",
        "for(var i=0;i<q.length;i++){",
        "var n=q[i];",
        "if(!n||n.nodeType!==1||n.isConnected===false)continue;",
        "checkEl(n);",
        "if(n.querySelectorAll){",
        "var f=n.querySelectorAll('iframe,img[src],embed,object');",
        "for(var j=0;j<f.length&&j<300;j++){checkEl(f[j]);}}",
        "if(!isYt&&budget>0){",
        "var w=document.createTreeWalker(n,4,null,false);",
        "var tn;",
        "while(budget>0&&(tn=w.nextNode())){budget--;labelCheck(tn);}}}}",
        "function startSweep(){",
        "if(mo||!window.MutationObserver)return;",
        "mo=new MutationObserver(function(recs){",
        "if(!on)return;",
        "for(var i=0;i<recs.length;i++){",
        "var a=recs[i].addedNodes;",
        "for(var j=0;j<a.length;j++){if(a[j].nodeType===1){enqueue(a[j]);}}}});",
        "mo.observe(document,{childList:true,subtree:true});",
        "var first=function(){if(document.body){enqueue(document.body);}};",
        "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',first);}else{first();}",
        "W.addEventListener('load',first);}",
        "function stopSweep(){",
        "if(mo){mo.disconnect();mo=null;}",
        "queue=[];}",
        "try{",
        "var wo=W.open;",
        "W.open=function(){",
        "try{if(on&&navigator.userActivation&&!navigator.userActivation.isActive){return null;}}catch(e){}",
        "return wo.apply(this,arguments);};",
        "}catch(e){}",
        "if(isYt){",
        "var FEED={adSlotRenderer:1,promotedSparklesWebRenderer:1,promotedSparklesTextSearchRenderer:1,displayAdRenderer:1,inFeedAdLayoutRenderer:1,bannerPromoRenderer:1,statementBannerRenderer:1,compactPromotedVideoRenderer:1,promotedVideoRenderer:1,searchPyvRenderer:1,videoMastheadAdV3Renderer:1,brandVideoSingletonRenderer:1,brandVideoShelfRenderer:1,carouselAdRenderer:1,actionCompanionAdRenderer:1,playerLegacyDesktopWatchAdsRenderer:1,adBreakServiceRenderer:1};",
        "var AKEYS=['adPlacements','playerAds','adSlots','adBreakHeartbeatParams'];",
        "var ARE=/adPlacements|playerAds|adSlots|adSlotRenderer|promotedSparkles|displayAdRenderer|inFeedAdLayoutRenderer|bannerPromoRenderer|statementBannerRenderer|compactPromotedVideoRenderer|promotedVideoRenderer|searchPyvRenderer|videoMastheadAdV3Renderer|brandVideoSingletonRenderer|brandVideoShelfRenderer|carouselAdRenderer|actionCompanionAdRenderer|playerLegacyDesktopWatchAdsRenderer|adBreakServiceRenderer/;",
        "var YURL=/\\/youtubei\\/v1\\/(player|next|browse|search|reel|guide)/;",
        "var isObj=function(x){return x&&typeof x==='object';};",
        "var feedKey=function(it){",
        "for(var k in it){",
        "if(FEED[k]===1)return true;",
        "var v=it[k];",
        "if(isObj(v)&&!Array.isArray(v)){",
        "for(var k2 in v){",
        "if(FEED[k2]===1)return true;",
        "var v2=v[k2];",
        "if(isObj(v2)&&!Array.isArray(v2)){",
        "for(var k3 in v2){if(FEED[k3]===1)return true;}}}}}",
        "return false;};",
        "var prune=function(o,d){",
        "if(!isObj(o)||d>16)return;",
        "if(Array.isArray(o)){",
        "for(var i=o.length-1;i>=0;i--){",
        "var it=o[i];",
        "if(isObj(it)){",
        "if(!Array.isArray(it)&&feedKey(it)){o.splice(i,1);continue;}",
        "prune(it,d+1);}}",
        "return;}",
        "for(var k in o){",
        "if(!Object.prototype.hasOwnProperty.call(o,k))continue;",
        "if(AKEYS.indexOf(k)>=0){try{delete o[k];}catch(e){}continue;}",
        "var v=o[k];",
        "if(isObj(v)){prune(v,d+1);}}};",
        "var jp=JSON.parse;",
        "JSON.parse=function(t){",
        "var r=jp.apply(this,arguments);",
        "try{if(on&&isObj(r)&&typeof t==='string'&&ARE.test(t)){prune(r,0);}}catch(e){}",
        "return r;};",
        "var of=W.fetch;",
        "if(typeof of==='function'){",
        "W.fetch=function(input){",
        "var p=of.apply(this,arguments);",
        "if(!on)return p;",
        "var u='';",
        "try{u=typeof input==='string'?input:((input&&input.url)||'');}catch(e){}",
        "if(!YURL.test(u))return p;",
        "return p.then(function(res){",
        "try{",
        "if(!res||!res.ok)return res;",
        "return res.clone().text().then(function(t){",
        "if(!ARE.test(t))return res;",
        "var o;",
        "try{o=jp.call(JSON,t);}catch(e){return res;}",
        "prune(o,0);",
        "return new Response(JSON.stringify(o),{status:res.status,statusText:res.statusText,headers:res.headers});",
        "},function(){return res;});",
        "}catch(e){return res;}});};}",
        "var hookGlobal=function(name){",
        "var val;",
        "try{",
        "Object.defineProperty(W,name,{configurable:true,enumerable:true,",
        "get:function(){return val;},",
        "set:function(v){",
        "if(on&&isObj(v)){try{prune(v,0);}catch(e){}}",
        "val=v;}});",
        "}catch(e){}};",
        "hookGlobal('ytInitialPlayerResponse');",
        "hookGlobal('ytInitialData');}",
        "function tick(){",
        "if(!on||document.hidden)return;",
        "var pl=document.querySelector('#movie_player,.html5-video-player');",
        "if(!pl)return;",
        "if(!pl.__coaraO&&window.MutationObserver){",
        "pl.__coaraO=1;",
        "new MutationObserver(tick).observe(pl,{attributes:true,attributeFilter:['class']});}",
        "var cl=pl.classList;",
        "var ad=cl.contains('ad-showing')||cl.contains('ad-interrupting');",
        "var v=pl.querySelector('video.html5-main-video')||pl.querySelector('video');",
        "if(ad){",
        "var b=pl.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button,.ytp-ad-skip-button-slot button');",
        "if(b){b.click();}",
        "if(v){",
        "if(!prev){prev={m:v.muted,r:v.playbackRate};}",
        "v.muted=true;",
        "v.playbackRate=16;",
        "var d=v.duration;",
        "if(d>0&&isFinite(d)&&v.currentTime<d-0.1){v.currentTime=d;}}",
        "}else if(prev){",
        "if(v){v.muted=prev.m;v.playbackRate=prev.r;}",
        "prev=null;}",
        "var c=pl.querySelector('.ytp-ad-overlay-close-button');",
        "if(c){c.click();}",
        "var en=document.querySelector('ytd-enforcement-message-view-model');",
        "if(en){",
        "var bt=en.querySelector('#dismiss-button button,button[aria-label*=\"Close\" i],button[aria-label*=\"閉じる\"]');",
        "if(bt){bt.click();}",
        "if(en.parentNode){en.parentNode.removeChild(en);}",
        "var bd=document.querySelector('tp-yt-iron-overlay-backdrop');",
        "if(bd){bd.style.setProperty('display','none','important');}",
        "if(v&&v.paused&&!ad){try{v.play();}catch(e){}}}}",
        "function startYt(){",
        "if(timer||!isYt)return;",
        "timer=setInterval(tick,500);",
        "tick();}",
        "function stopYt(){",
        "if(timer){clearInterval(timer);timer=0;}",
        "prev=null;}",
        "W.__coaraAb={",
        "on:function(){on=true;mount();startYt();startSweep();},",
        "off:function(){",
        "on=false;",
        "if(st){st.disabled=true;}",
        "if(st2){st2.disabled=true;}",
        "stopYt();",
        "stopSweep();",
        "unhideAll();}};",
        "startYt();",
        "startSweep();",
        "})();"
    };

    public static final String SCRIPT_OFF = "(function(){try{if(window.__coaraAb){window.__coaraAb.off();}}catch(e){}})();";
}
