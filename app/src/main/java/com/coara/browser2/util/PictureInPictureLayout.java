package com.coara.browser2.util;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;

public final class PictureInPictureLayout {
    private static final String ENTER_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip;if(S&&S.on){return true}"
            + "var CSS='.__cpt{position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;"
            + "width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;"
            + "min-width:0!important;min-height:0!important;margin:0!important;padding:0!important;border:0!important;"
            + "transform:none!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;"
            + "visibility:visible!important;display:block!important;opacity:1!important}"
            + ".__cpa{transform:none!important;filter:none!important;perspective:none!important;contain:none!important;"
            + "clip-path:none!important;mask:none!important;backdrop-filter:none!important;will-change:auto!important;"
            + "overflow:visible!important;opacity:1!important;z-index:2147483647!important}"
            + "html.__cpr,body.__cpr{overflow:hidden!important;background:#000!important}';"
            + "var best=null,blind=null,roots=[],els=[],styles=[],visited=[];"
            + "function mark(el,cls){try{el.classList.add(cls);els.push([el,cls])}catch(e){}}"
            + "function style(root){if(roots.indexOf(root)>=0)return;roots.push(root);"
            + "try{var d=root.ownerDocument||root;var st=d.createElement('style');st.textContent=CSS;"
            + "(root.head||root.documentElement||root).appendChild(st);styles.push(st)}catch(e){}}"
            + "function lock(d){try{if(d.documentElement)mark(d.documentElement,'__cpr');if(d.body)mark(d.body,'__cpr')}catch(e){}}"
            + "function climb(el){var n=el,first=true;while(n){if(n.nodeType===1){mark(n,first?'__cpt':'__cpa');first=false;"
            + "style(n.getRootNode());lock(n.ownerDocument)}n=n.parentNode||n.host}}"
            + "function scan(d,path,depth){if(!d||depth>5||visited.indexOf(d)>=0)return;visited.push(d);var vids=[];"
            + "try{vids=d.querySelectorAll('video')}catch(e){}"
            + "for(var i=0;i<vids.length;i++){var v=vids[i],r=null,playing=false;"
            + "try{r=v.getBoundingClientRect();playing=!v.paused&&!v.ended&&v.readyState>=2}catch(e){}"
            + "if(!playing)continue;var a=r?r.width*r.height:0;if(!best||a>best.a)best={el:v,p:path.slice(),a:a}}"
            + "var fr=[];try{fr=d.querySelectorAll('iframe,frame')}catch(e){}"
            + "for(var j=0;j<fr.length;j++){var f=fr[j],fd=null,rr=null;"
            + "try{fd=f.contentDocument;rr=f.getBoundingClientRect()}catch(e){}"
            + "if(fd){path.push(f);scan(fd,path,depth+1);path.pop()}"
            + "else if(rr&&rr.width>=160&&rr.height>=90){var ar=rr.width*rr.height;"
            + "if(!blind||ar>blind.a)blind={el:f,p:path.slice(),a:ar}}}"
            + "var hosts=[];try{hosts=d.querySelectorAll('*')}catch(e){}"
            + "for(var z=0;z<hosts.length;z++){try{if(hosts[z].shadowRoot)scan(hosts[z].shadowRoot,path,depth+1)}catch(e){}}}"
            + "scan(document,[],0);"
            + "var t=best||blind;if(!t)return false;"
            + "for(var k=0;k<t.p.length;k++){climb(t.p[k])}"
            + "climb(t.el);"
            + "var vid=t.el.tagName==='VIDEO'?t.el:null,vo=false;"
            + "try{var dw=document.wrappedJSObject;if(dw&&typeof exportFunction==='function'){"
            + "Object.defineProperty(dw,'hidden',{configurable:true,get:exportFunction(function(){return false},window)});"
            + "Object.defineProperty(dw,'visibilityState',{configurable:true,get:exportFunction(function(){return 'visible'},window)});"
            + "vo=true}}catch(e){}"
            + "window.__coaraPip={on:true,els:els,styles:styles,video:vid,vo:vo};return true"
            + "}catch(e){return false}})()";

    private static final String EXIT_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip;if(!S)return true;"
            + "S.els.forEach(function(p){try{p[0].classList.remove(p[1])}catch(e){}});"
            + "S.styles.forEach(function(s){try{s.remove()}catch(e){}});"
            + "if(S.vo){try{var dw=document.wrappedJSObject;delete dw.hidden;delete dw.visibilityState}catch(e){}}"
            + "window.__coaraPip=null;return true"
            + "}catch(e){return false}})()";

    private static final String PROBE_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip;var v=S&&S.video;"
            + "var r={vis:document.visibilityState,hid:document.hidden};"
            + "if(v){r.p=!!v.paused;r.e=!!v.ended;r.rs=v.readyState;r.t=v.currentTime;r.f=!!document.fullscreenElement}"
            + "else{r.nv=true}return r"
            + "}catch(e){return {err:String(e)}}})()";

    private static final String RESUME_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip;if(!S||!S.video)return false;"
            + "var p=S.video.play();if(p&&p.catch){p.catch(function(){})}return true"
            + "}catch(e){return false}})()";

    private static final String RESTART_POSITION_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip,v=S&&S.video;if(!v)return false;"
            + "var ended=false;try{ended=!!v.ended||(isFinite(v.duration)&&v.duration>0&&v.currentTime>=v.duration-0.35)}catch(e){}"
            + "if(ended){v.currentTime=0;return true}return false"
            + "}catch(e){return false}})()";

    private static final String SEEK_BACK_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip,v=S&&S.video;if(!v||!isFinite(v.duration)||v.duration<=0||!isFinite(v.currentTime))return false;"
            + "v.currentTime=Math.max(0,v.currentTime-10);return true"
            + "}catch(e){return false}})()";

    private static final String SEEK_FORWARD_SCRIPT = "(function(){try{"
            + "var S=window.__coaraPip,v=S&&S.video;if(!v||!isFinite(v.duration)||v.duration<=0||!isFinite(v.currentTime))return false;"
            + "v.currentTime=Math.min(v.duration,v.currentTime+10);return true"
            + "}catch(e){return false}})()";

    private PictureInPictureLayout() {
    }

    public static void enter(BrowserView view) {
        run(view, ENTER_SCRIPT);
    }

    public static void exit(BrowserView view) {
        run(view, EXIT_SCRIPT);
    }

    public static void resume(BrowserView view) {
        run(view, RESUME_SCRIPT);
    }

    public static void restartPosition(BrowserView view) {
        run(view, RESTART_POSITION_SCRIPT);
    }

    public static void restartPosition(BrowserView view, ValueCallback<String> callback) {
        if (view == null || view.isDestroyed()) {
            callback.onReceiveValue("false");
            return;
        }
        try {
            view.evaluateJavascript(RESTART_POSITION_SCRIPT, callback);
        } catch (Throwable error) {
            callback.onReceiveValue("false");
        }
    }

    public static void seekBy(BrowserView view, int seconds, ValueCallback<String> callback) {
        if (view == null || view.isDestroyed()) {
            callback.onReceiveValue("false");
            return;
        }
        String script;
        if (seconds < 0) {
            script = SEEK_BACK_SCRIPT;
        } else if (seconds > 0) {
            script = SEEK_FORWARD_SCRIPT;
        } else {
            callback.onReceiveValue("false");
            return;
        }
        try {
            view.evaluateJavascript(script, callback);
        } catch (Throwable error) {
            callback.onReceiveValue("false");
        }
    }

    public static void probe(BrowserView view, ValueCallback<String> callback) {
        if (view == null || view.isDestroyed()) {
            callback.onReceiveValue("null");
            return;
        }
        try {
            view.evaluateJavascript(PROBE_SCRIPT, callback);
        } catch (Throwable error) {
            callback.onReceiveValue("null");
        }
    }

    private static void run(BrowserView view, String script) {
        if (view == null || view.isDestroyed()) {
            return;
        }
        try {
            view.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
        }
    }
}
