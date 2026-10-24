package com.coara.browser2.util;

import android.os.Handler;
import android.os.Looper;

import com.coara.browser2.gecko.BrowserView;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.TranslationsController;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GeckoNativePageTranslator {
    public interface Callback {
        void onSuccess();
        void onFailure();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<BrowserView, Boolean> ACTIVE = new WeakHashMap<>();
    private static final Map<BrowserView, String> DETECTED = new WeakHashMap<>();
    private static final Map<BrowserView, Pending> PENDING = new WeakHashMap<>();
    private static final String SAMPLE_JS = "(function(){var d=document.documentElement;return {lang:(d&&d.lang)||'',text:((document.body&&document.body.innerText)||'').slice(0,2400)};})()";

    private GeckoNativePageTranslator() {
    }

    private static final class Pending {
        final WeakReference<BrowserView> view;
        final String url;
        final Callback callback;
        final AtomicBoolean failed = new AtomicBoolean();
        final AtomicBoolean successReported = new AtomicBoolean();
        Runnable timeout;

        Pending(BrowserView view, Callback callback) {
            this.view = new WeakReference<>(view);
            this.url = view.getUrl();
            this.callback = callback;
        }
    }

    public static void install(GeckoSession session, BrowserView view) {
        if (session == null || view == null) {
            return;
        }
        try {
            session.setTranslationsSessionDelegate(new TranslationsController.SessionTranslation.Delegate() {
                @Override
                public void onTranslationStateChange(GeckoSession sourceSession,
                        TranslationsController.SessionTranslation.TranslationState state) {
                    if (state == null) {
                        return;
                    }
                    if (state.detectedLanguages != null && state.detectedLanguages.docLangTag != null
                            && !state.detectedLanguages.docLangTag.trim().isEmpty()) {
                        synchronized (DETECTED) {
                            DETECTED.put(view, state.detectedLanguages.docLangTag);
                        }
                    }
                    if (state.error != null && !state.error.trim().isEmpty()) {
                        synchronized (ACTIVE) {
                            ACTIVE.remove(view);
                        }
                        Pending pending;
                        synchronized (PENDING) {
                            pending = PENDING.remove(view);
                        }
                        failPending(pending);
                    } else if (Boolean.TRUE.equals(state.hasVisibleChange)) {
                        synchronized (ACTIVE) {
                            ACTIVE.put(view, true);
                        }
                        finishVisibleChange(view);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static boolean isActive(BrowserView view) {
        synchronized (ACTIVE) {
            return Boolean.TRUE.equals(ACTIVE.get(view));
        }
    }

    public static void tryTranslate(BrowserView view, String source, String target, Callback callback) {
        if (view == null || callback == null || view.isDestroyed()) {
            fail(callback);
            return;
        }
        GeckoSession session = view.getGeckoSession();
        if (session == null || session.getSessionTranslation() == null) {
            fail(callback);
            return;
        }
        Pending pending = new Pending(view, callback);
        synchronized (PENDING) {
            clearPendingLocked(view);
            PENDING.put(view, pending);
            pending.timeout = () -> failPending(view, pending);
            MAIN.postDelayed(pending.timeout, 180000L);
        }
        TranslationsController.RuntimeTranslation.isTranslationsEngineSupported().accept(
                supported -> {
                    if (!current(pending)) {
                        return;
                    }
                    if (!Boolean.TRUE.equals(supported)) {
                        failPending(view, pending);
                        return;
                    }
                    TranslationsController.RuntimeTranslation.listSupportedLanguages().accept(
                            support -> {
                                if (!current(pending) || support == null || support.fromLanguages == null
                                        || support.toLanguages == null) {
                                    failPending(view, pending);
                                    return;
                                }
                                String targetCode = findCode(target, support.toLanguages);
                                if (targetCode == null) {
                                    failPending(view, pending);
                                    return;
                                }
                                if (source != null && !TranslateSettings.AUTO.equals(source)) {
                                    String sourceCode = findCode(source, support.fromLanguages);
                                    if (sourceCode == null || equivalent(sourceCode, targetCode)) {
                                        failPending(view, pending);
                                    } else {
                                        invokeTranslate(pending, session, sourceCode, targetCode);
                                    }
                                    return;
                                }
                                resolveAutomaticSource(pending, view, session, support.fromLanguages, targetCode);
                            }, error -> failPending(view, pending));
                }, error -> failPending(view, pending));
    }

    public static void restore(BrowserView view, Callback callback) {
        if (view == null || view.isDestroyed()) {
            fail(callback);
            return;
        }
        GeckoSession session = view.getGeckoSession();
        if (session == null || session.getSessionTranslation() == null) {
            fail(callback);
            return;
        }
        try {
            session.getSessionTranslation().restoreOriginalPage().accept(value -> {
                synchronized (ACTIVE) {
                    ACTIVE.remove(view);
                }
                clearPending(view);
                succeed(callback);
            }, error -> fail(callback));
        } catch (Throwable t) {
            fail(callback);
        }
    }

    public static void onPageStarted(BrowserView view) {
        if (view == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.remove(view);
        }
        synchronized (DETECTED) {
            DETECTED.remove(view);
        }
        clearPending(view);
    }

    public static void releaseForView(BrowserView view) {
        onPageStarted(view);
    }

    private static void resolveAutomaticSource(Pending pending, BrowserView view, GeckoSession session,
            List<TranslationsController.Language> fromLanguages, String targetCode) {
        String detected;
        synchronized (DETECTED) {
            detected = DETECTED.get(view);
        }
        String code = findCode(detected, fromLanguages);
        if (code != null && !equivalent(code, targetCode)) {
            invokeTranslate(pending, session, code, targetCode);
            return;
        }
        try {
            view.evaluateJavascript(SAMPLE_JS, value -> {
                if (!current(pending)) {
                    return;
                }
                String pageLang = "";
                String text = "";
                try {
                    JSONObject object = new JSONObject(value);
                    pageLang = object.optString("lang", "");
                    text = object.optString("text", "");
                } catch (Throwable ignored) {
                }
                String sourceCode = findCode(pageLang, fromLanguages);
                if (sourceCode != null && !equivalent(sourceCode, targetCode)) {
                    invokeTranslate(pending, session, sourceCode, targetCode);
                    return;
                }
                if (text.trim().length() < 24) {
                    failPending(view, pending);
                    return;
                }
                PageTranslator.identifyLanguage(view.getContext(), text, language -> {
                    if (!current(pending)) {
                        return;
                    }
                    String identified = findCode(language, fromLanguages);
                    if (identified == null || equivalent(identified, targetCode)) {
                        failPending(view, pending);
                    } else {
                        invokeTranslate(pending, session, identified, targetCode);
                    }
                });
            });
        } catch (Throwable t) {
            failPending(view, pending);
        }
    }

    private static void invokeTranslate(Pending pending, GeckoSession session, String source, String target) {
        BrowserView view = pending.view.get();
        if (view == null || !current(pending)) {
            return;
        }
        try {
            TranslationsController.SessionTranslation.TranslationOptions options =
                    new TranslationsController.SessionTranslation.TranslationOptions.Builder()
                            .downloadModel(true)
                            .build();
            session.getSessionTranslation().translate(source, target, options).accept(value -> {
                if (!current(pending)) {
                    return;
                }
                synchronized (ACTIVE) {
                    ACTIVE.put(view, true);
                }
                succeedPending(pending);
            }, error -> failPending(view, pending));
        } catch (Throwable t) {
            failPending(view, pending);
        }
    }

    private static boolean current(Pending pending) {
        if (pending == null || pending.failed.get()) {
            return false;
        }
        BrowserView view = pending.view.get();
        if (view == null) {
            pending.failed.set(true);
            return false;
        }
        if (view.isDestroyed() || !sameUrl(pending.url, view.getUrl())) {
            cancelPending(view, pending);
            return false;
        }
        synchronized (PENDING) {
            return PENDING.get(view) == pending;
        }
    }

    private static boolean sameUrl(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String findCode(String input, List<TranslationsController.Language> languages) {
        if (input == null || input.trim().isEmpty() || languages == null) {
            return null;
        }
        String cleaned = input.trim().replace('_', '-');
        for (TranslationsController.Language language : languages) {
            if (language != null && language.code != null && language.code.equalsIgnoreCase(cleaned)) {
                return language.code;
            }
        }
        String normalized = primary(cleaned);
        String best = null;
        for (TranslationsController.Language language : languages) {
            if (language == null || language.code == null) {
                continue;
            }
            String code = primary(language.code);
            if (code.equalsIgnoreCase(normalized)) {
                if (best == null || language.code.toLowerCase(Locale.ROOT).endsWith("-cn")
                        || language.code.toLowerCase(Locale.ROOT).endsWith("-hans")) {
                    best = language.code;
                }
            }
        }
        if (best != null) {
            return best;
        }
        String alias = normalized;
        if ("no".equals(alias)) {
            alias = "nb";
        } else if ("iw".equals(alias)) {
            alias = "he";
        } else if ("in".equals(alias)) {
            alias = "id";
        }
        for (TranslationsController.Language language : languages) {
            if (language != null && language.code != null && primary(language.code).equalsIgnoreCase(alias)) {
                return language.code;
            }
        }
        return null;
    }

    private static String primary(String tag) {
        if (tag == null) {
            return "";
        }
        String value = tag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int cut = value.indexOf('-');
        return cut < 0 ? value : value.substring(0, cut);
    }

    private static boolean equivalent(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.equalsIgnoreCase(second)) {
            return true;
        }
        if (primary(first).equals("zh") && primary(second).equals("zh")) {
            return false;
        }
        return primary(first).equals(primary(second));
    }

    private static void failPending(BrowserView view, Pending pending) {
        if (view == null || pending == null) {
            failPending(pending);
            return;
        }
        synchronized (PENDING) {
            if (PENDING.get(view) != pending) {
                return;
            }
            PENDING.remove(view);
        }
        if (pending.timeout != null) {
            MAIN.removeCallbacks(pending.timeout);
        }
        failPending(pending);
    }

    private static void failPending(Pending pending) {
        if (pending == null || !pending.failed.compareAndSet(false, true)) {
            return;
        }
        if (pending.timeout != null) {
            MAIN.removeCallbacks(pending.timeout);
        }
        BrowserView view = pending.view.get();
        if (view != null) {
            synchronized (ACTIVE) {
                ACTIVE.remove(view);
            }
        }
        MAIN.post(pending.callback::onFailure);
    }

    private static void cancelPending(BrowserView view, Pending pending) {
        synchronized (PENDING) {
            if (PENDING.get(view) == pending) {
                PENDING.remove(view);
            }
        }
        pending.failed.set(true);
        if (pending.timeout != null) {
            MAIN.removeCallbacks(pending.timeout);
        }
    }

    private static void clearPending(BrowserView view) {
        if (view == null) {
            return;
        }
        Pending pending;
        synchronized (PENDING) {
            pending = PENDING.remove(view);
        }
        if (pending != null) {
            if (pending.timeout != null) {
                MAIN.removeCallbacks(pending.timeout);
            }
            pending.failed.set(true);
        }
    }

    private static void clearPending(BrowserView view, Pending pending) {
        synchronized (PENDING) {
            if (PENDING.get(view) == pending) {
                PENDING.remove(view);
            }
        }
        pending.failed.set(true);
        if (pending.timeout != null) {
            MAIN.removeCallbacks(pending.timeout);
        }
    }

    private static void finishVisibleChange(BrowserView view) {
        Pending pending;
        synchronized (PENDING) {
            pending = PENDING.remove(view);
        }
        if (pending == null) {
            return;
        }
        pending.failed.set(true);
        if (pending.timeout != null) {
            MAIN.removeCallbacks(pending.timeout);
        }
        succeedPending(pending);
    }

    private static void clearPendingLocked(BrowserView view) {
        Pending old = PENDING.remove(view);
        if (old != null) {
            old.failed.set(true);
            if (old.timeout != null) {
                MAIN.removeCallbacks(old.timeout);
            }
        }
    }

    private static void succeedPending(Pending pending) {
        if (pending != null && pending.successReported.compareAndSet(false, true)) {
            succeed(pending.callback);
        }
    }

    private static void succeed(Callback callback) {
        if (callback == null) {
            return;
        }
        MAIN.post(callback::onSuccess);
    }

    private static void fail(Callback callback) {
        if (callback == null) {
            return;
        }
        MAIN.post(callback::onFailure);
    }
}
