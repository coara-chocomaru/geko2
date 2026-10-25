package com.coara.browser2.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Rational;

import com.coara.browser2.gecko.BrowserView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class PictureInPictureAspectRatio {
    private static final Rational DEFAULT_RATIO = new Rational(16, 9);
    private static final long CACHE_TTL_MS = 900L;
    private static final long QUERY_TIMEOUT_MS = 1400L;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<BrowserView, Rational> CACHE = new WeakHashMap<>();
    private static final Map<BrowserView, Long> LAST_QUERY = new WeakHashMap<>();
    private static final Map<BrowserView, PendingQuery> PENDING = new WeakHashMap<>();
    private static final Map<BrowserView, Boolean> FOUND = new WeakHashMap<>();
    private static long nextGeneration;

    private static final String QUERY_SCRIPT = "(function(){try{var best=null,visited=[];function consider(w,h,score){w=Math.round(Number(w)||0);h=Math.round(Number(h)||0);if(w>0&&h>0&&(!best||score>best.score))best={w:w,h:h,score:score};}function scan(d,depth){if(!d||depth>5||visited.indexOf(d)>=0)return;visited.push(d);var docStrong=false;var fs=null;try{fs=d.fullscreenElement||d.webkitFullscreenElement||null}catch(e){}var vids=[];try{vids=d.querySelectorAll('video')}catch(e){}for(var i=0;i<vids.length;i++){var v=vids[i],r=null,st=null;try{r=v.getBoundingClientRect();st=d.defaultView.getComputedStyle(v)}catch(e){}var playing=false,ready=false,full=false,pip=false,visible=false;try{playing=!v.paused&&!v.ended;ready=v.readyState>=2;full=fs===v||!!(fs&&fs.contains&&fs.contains(v));pip=d.pictureInPictureElement===v||v.webkitPresentationMode==='picture-in-picture';visible=!!r&&r.width>0&&r.height>0&&(!st||(st.display!=='none'&&st.visibility!=='hidden'&&Number(st.opacity)!==0))}catch(e){}if(!visible&&!playing&&!full&&!pip)continue;if(playing||full||pip)docStrong=true;var w=Number(v.videoWidth)||0,h=Number(v.videoHeight)||0;if((w<=0||h<=0)&&r){w=r.width;h=r.height}var area=r?Math.min(100000000,r.width*r.height):0;var score=(pip?1000000000000000:0)+(full?1000000000000:0)+(playing?1000000000:0)+(ready?10000000:0)+(visible?1000000:0)+area;consider(w,h,score)}var frames=[];try{frames=d.querySelectorAll('iframe,frame')}catch(e){}for(var j=0;j<frames.length;j++){var f=frames[j],fr=null,ffd=null,fullFrame=false;try{fr=f.getBoundingClientRect();fullFrame=fs===f||!!(fs&&fs.contains&&fs.contains(f));ffd=f.contentDocument}catch(e){}if(fullFrame&&fr&&fr.width>0&&fr.height>0)consider(fr.width,fr.height,1500000000);if(ffd){try{scan(ffd,depth+1)}catch(e){}}}if(depth<3&&!docStrong){var all=[];try{all=d.querySelectorAll('*')}catch(e){}for(var k=0;k<all.length;k++){try{if(all[k].shadowRoot)scan(all[k].shadowRoot,depth+1)}catch(e){}}}}scan(document,0);return best?{w:best.w,h:best.h,found:true}:{found:false}}catch(e){return {found:false}}})()";

    private static final class PendingQuery {
        final long generation;
        final List<Consumer<Rational>> callbacks = new ArrayList<>();
        Runnable timeout;

        PendingQuery(long generation) {
            this.generation = generation;
        }
    }

    private PictureInPictureAspectRatio() {
    }

    public static Rational defaultAspectRatio() {
        return DEFAULT_RATIO;
    }

    public static int videoState(BrowserView view) {
        if (view == null) {
            return 0;
        }
        synchronized (LOCK) {
            Boolean found = FOUND.get(view);
            return found == null ? 0 : (found ? 1 : -1);
        }
    }

    public static void reset(BrowserView view) {
        if (view == null) {
            return;
        }
        synchronized (LOCK) {
            FOUND.remove(view);
        }
    }

    public static Rational getCached(BrowserView view) {
        if (view == null) {
            return DEFAULT_RATIO;
        }
        synchronized (LOCK) {
            Rational ratio = CACHE.get(view);
            return ratio == null ? DEFAULT_RATIO : ratio;
        }
    }

    public static void query(BrowserView view, boolean force, Consumer<Rational> callback) {
        if (view == null || view.isDestroyed()) {
            dispatch(callback, DEFAULT_RATIO);
            return;
        }
        final PendingQuery request;
        final Rational cached;
        final long now = SystemClock.uptimeMillis();
        synchronized (LOCK) {
            cached = CACHE.get(view) == null ? DEFAULT_RATIO : CACHE.get(view);
            PendingQuery current = PENDING.get(view);
            if (current != null) {
                if (callback != null) {
                    current.callbacks.add(callback);
                }
                return;
            }
            Long last = LAST_QUERY.get(view);
            if (!force && last != null && now - last < CACHE_TTL_MS) {
                dispatch(callback, cached);
                return;
            }
            request = new PendingQuery(++nextGeneration);
            if (callback != null) {
                request.callbacks.add(callback);
            }
            PENDING.put(view, request);
            LAST_QUERY.put(view, now);
            request.timeout = () -> complete(view, request.generation, null);
            MAIN.postDelayed(request.timeout, QUERY_TIMEOUT_MS);
        }
        try {
            view.evaluateJavascript(QUERY_SCRIPT, value -> {
                Rational ratio = parse(value);
                Boolean found = parseFound(value);
                if (found != null) {
                    synchronized (LOCK) {
                        FOUND.put(view, found);
                    }
                }
                complete(view, request.generation, ratio);
            });
        } catch (Throwable error) {
            complete(view, request.generation, null);
        }
    }

    private static Boolean parseFound(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return new JSONObject(value).optBoolean("found", false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rational parse(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(value);
            if (!object.optBoolean("found", false)) {
                return null;
            }
            int width = object.optInt("w", 0);
            int height = object.optInt("h", 0);
            if (width <= 0 || height <= 0) {
                return null;
            }
            return normalize(width, height);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rational normalize(int width, int height) {
        double ratio = width / (double) height;
        if (ratio > 2.39d) {
            return new Rational(239, 100);
        }
        if (ratio < (1d / 2.39d)) {
            return new Rational(100, 239);
        }
        int a = width;
        int b = height;
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        if (a > 0) {
            width /= a;
            height /= a;
        }
        return new Rational(width, height);
    }

    private static void complete(BrowserView view, long generation, Rational result) {
        List<Consumer<Rational>> callbacks;
        Rational finalRatio;
        synchronized (LOCK) {
            PendingQuery request = PENDING.get(view);
            if (request == null || request.generation != generation) {
                return;
            }
            PENDING.remove(view);
            if (request.timeout != null) {
                MAIN.removeCallbacks(request.timeout);
            }
            if (result != null) {
                CACHE.put(view, result);
            }
            Rational cached = CACHE.get(view);
            finalRatio = cached == null ? DEFAULT_RATIO : cached;
            callbacks = new ArrayList<>(request.callbacks);
        }
        for (Consumer<Rational> callback : callbacks) {
            dispatch(callback, finalRatio);
        }
    }

    private static void dispatch(Consumer<Rational> callback, Rational ratio) {
        if (callback == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.accept(ratio);
        } else {
            MAIN.post(() -> callback.accept(ratio));
        }
    }
}
