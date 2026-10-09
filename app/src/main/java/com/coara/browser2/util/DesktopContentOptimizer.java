package com.coara.browser2.util;

import com.coara.browser2.gecko.BrowserView;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesktopContentOptimizer {
    private static final String STYLE_ID = "__coara_desktop_content_optimizer";
    private static final String VERSION = "3";
    private static final int MAX_SCRIPT_CHARS = 16000;
    private static final Pattern FIREFOX_VERSION = Pattern.compile("Firefox/(\\d+)");
    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/(\\d+)");
    private static final Map<BrowserView, Boolean> ACTIVE = Collections.synchronizedMap(new WeakHashMap<BrowserView, Boolean>());
    private static final String CSS = "*,*::before,*::after{box-sizing:border-box;}" +
            "html{width:100%;max-width:100%;}" +
            "body{max-width:100vw;min-width:0 !important;overflow-x:auto;-webkit-text-size-adjust:100%;}" +
            "img,video,canvas,object,embed{max-width:100% !important;height:auto;}" +
            "svg{max-width:100%;}" +
            "iframe,input,select,textarea,button{max-width:100%;box-sizing:border-box;}" +
            "table{max-width:100% !important;}" +
            "pre,code{max-width:100%;overflow-x:auto;}" +
            "[role='main'],main,article,section,aside,header,footer,nav,form{max-width:100%;min-width:0;}";
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static String cachedScript;

    private DesktopContentOptimizer() {
    }

    public static String desktopUserAgent(String base) {
        if (base != null) {
            Matcher firefox = FIREFOX_VERSION.matcher(base);
            if (firefox.find()) {
                String v = firefox.group(1);
                return "Mozilla/5.0 (X11; Linux x86_64; rv:" + v + ".0) Gecko/20100101 Firefox/" + v + ".0";
            }
            Matcher matcher = CHROME_VERSION.matcher(base);
            if (matcher.find()) {
                return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + matcher.group(1) + ".0.0.0 Safari/537.36";
            }
            return base.replace("Mobile", "").replace("Android", "");
        }
        return "";
    }

    public static void apply(BrowserView webView, boolean enabled) {
        if (webView == null) {
            return;
        }
        if (!enabled) {
            if (ACTIVE.remove(webView) != null) {
                clear(webView);
            }
            return;
        }
        try {
            float density = webView.getResources().getDisplayMetrics().density;
            if (density <= 0f) {
                density = 1f;
            }
            int widthPx = webView.getWidth() > 0 ? webView.getWidth() : webView.getResources().getDisplayMetrics().widthPixels;
            int heightPx = webView.getHeight() > 0 ? webView.getHeight() : webView.getResources().getDisplayMetrics().heightPixels;
            int widthDp = Math.max(1, Math.round(widthPx / density));
            int heightDp = Math.max(1, Math.round(heightPx / density));
            String script = scriptFor(widthDp, heightDp);
            if (script == null) {
                return;
            }
            ACTIVE.put(webView, Boolean.TRUE);
            webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    public static void clear(BrowserView webView) {
        if (webView == null) {
            return;
        }
        ACTIVE.remove(webView);
        try {
            String script = "(function(){try{" +
                    "var s=document.getElementById(" + JSONObject.quote(STYLE_ID) + ");" +
                    "if(s&&s.parentNode)s.parentNode.removeChild(s);" +
                    "var o=window.__coaraDesk;if(o&&o.stop){try{o.stop();}catch(e1){}}window.__coaraDesk=null;" +
                    "var d=document.documentElement;if(d){d.removeAttribute('data-coara-device-width');d.removeAttribute('data-coara-device-height');}" +
                    "}catch(e){}})();";
            webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized String scriptFor(int widthDp, int heightDp) {
        if (cachedScript != null && cachedWidth == widthDp && cachedHeight == heightDp) {
            return cachedScript;
        }
        String script = buildScript(widthDp, heightDp);
        if (script.length() > MAX_SCRIPT_CHARS) {
            return null;
        }
        cachedWidth = widthDp;
        cachedHeight = heightDp;
        cachedScript = script;
        return script;
    }

    private static String buildScript(int widthDp, int heightDp) {
        return "(function(){try{" +
                "var d=document.documentElement;if(!d)return;" +
                "var W=" + widthDp + ",H=" + heightDp + ",V=" + JSONObject.quote(VERSION) + ",ID=" + JSONObject.quote(STYLE_ID) + ";" +
                "var CONTENT='width=device-width,initial-scale=1,minimum-scale=0.5,maximum-scale=5,user-scalable=yes,viewport-fit=cover';" +
                "var S=window.__coaraDesk;" +
                "if(S&&S.v===V&&S.run){S.w=W;S.h=H;S.run(true);return;}" +
                "if(S&&S.stop){try{S.stop();}catch(e0){}}" +
                "var CSS=" + JSONObject.quote(CSS) + ";" +
                "var timer=0,idle=0,stopped=false,mo=null;" +
                "var ensure=function(){" +
                    "var head=document.head||d;" +
                    "var m=document.querySelector('meta[name=viewport]');" +
                    "if(!m){m=document.createElement('meta');m.name='viewport';head.insertBefore(m,head.firstChild);}" +
                    "if(m.getAttribute('content')!==CONTENT)m.setAttribute('content',CONTENT);" +
                    "var s=document.getElementById(ID);" +
                    "if(!s||s.getAttribute('data-v')!==V||!s.parentNode){" +
                        "if(s&&s.parentNode)s.parentNode.removeChild(s);" +
                        "s=document.createElement('style');s.id=ID;s.setAttribute('data-v',V);" +
                        "s.appendChild(document.createTextNode(CSS));head.appendChild(s);" +
                    "}" +
                    "if(d.getAttribute('data-coara-device-width')!==String(api.w))d.setAttribute('data-coara-device-width',api.w);" +
                    "if(d.getAttribute('data-coara-device-height')!==String(api.h))d.setAttribute('data-coara-device-height',api.h);" +
                "};" +
                "var overflowing=function(vw){var b=document.body;return Math.max(d.scrollWidth,b?b.scrollWidth:0)>vw+2;};" +
                "var adapt=function(){" +
                    "timer=0;if(stopped)return;var changed=0;try{" +
                    "var vw=Math.max(1,d.clientWidth||window.innerWidth||api.w);" +
                    "if(!overflowing(vw)){idle=0;return;}" +
                    "var body=document.body;if(!body)return;" +
                    "var list=body.getElementsByTagName('*');var count=Math.min(list.length,800);var ops=[];" +
                    "for(var i=0;i<count;i++){var el=list[i];if(!el||el.__cdo)continue;" +
                        "var tag=el.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='META'||tag==='LINK'||tag==='NOSCRIPT')continue;" +
                        "var r;try{r=el.getBoundingClientRect();}catch(e){continue;}" +
                        "if(!r||r.width<=vw*1.35)continue;" +
                        "var cs;try{cs=window.getComputedStyle(el);}catch(e2){continue;}" +
                        "var pos=cs.position;if(pos==='fixed'||pos==='sticky')continue;" +
                        "ops.push([el,tag,r.width,cs.display,cs.whiteSpace,parseFloat(cs.minWidth)||0]);" +
                    "}" +
                    "for(var k=0;k<ops.length;k++){var o=ops[k],e=o[0],t=o[1],w=o[2],disp=o[3],st=e.style;" +
                        "e.__cdo=1;changed++;" +
                        "if(o[5]>vw)st.minWidth='0';" +
                        "if(o[4]==='nowrap'&&w>vw*1.45&&t!=='BUTTON'&&t!=='INPUT'&&t!=='SELECT')st.whiteSpace='normal';" +
                        "if(t==='TABLE'||t==='PRE'||t==='IMG'||t==='VIDEO'||t==='CANVAS'||t==='SVG'||t==='svg'||t==='IFRAME'||t==='INPUT'||t==='SELECT'||t==='TEXTAREA'){st.maxWidth='100%';st.boxSizing='border-box';}" +
                        "else if(disp==='flex'||disp==='inline-flex'||disp==='grid'||disp==='inline-grid'){st.maxWidth='100%';st.minWidth='0';if(disp.indexOf('flex')===0||disp==='inline-flex')st.flexWrap='wrap';}" +
                        "else if(w>vw*1.8){st.maxWidth='100%';st.minWidth='0';}" +
                    "}" +
                    "}catch(e3){}" +
                    "if(changed===0)idle++;else{idle=0;schedule();}" +
                "};" +
                "var schedule=function(){if(timer||stopped||idle>=3)return;timer=setTimeout(adapt,220<<idle);};" +
                "var onResize=function(){idle=0;ensure();schedule();};" +
                "var api={v:V,w:W,h:H," +
                    "run:function(force){if(stopped)return;if(force)idle=0;ensure();schedule();}," +
                    "stop:function(){stopped=true;if(timer){clearTimeout(timer);timer=0;}" +
                        "try{window.removeEventListener('resize',onResize);}catch(a){}" +
                        "if(mo){try{mo.disconnect();}catch(b){}mo=null;}}};" +
                "window.__coaraDesk=api;" +
                "ensure();" +
                "window.addEventListener('resize',onResize,{passive:true});" +
                "if(window.MutationObserver){try{mo=new MutationObserver(function(list){" +
                    "for(var i=0;i<list.length;i++){if(list[i].addedNodes&&list[i].addedNodes.length){schedule();return;}}" +
                "});mo.observe(d,{childList:true,subtree:true});}catch(ex){}}" +
                "adapt();" +
                "}catch(e){}})();";
    }
}
