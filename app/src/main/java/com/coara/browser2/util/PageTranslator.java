package com.coara.browser2.util;

import android.content.Context;
import com.coara.browser2.gecko.BrowserView;
import android.widget.Toast;

import com.google.mlkit.common.MlKit;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PageTranslator {

    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final String ONLINE_UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String ASSET_NAME = "page_translate.js";
    private static final int CHUNK_CHARS = 3000;
    private static final int CHUNK_SEGMENTS = 50;
    private static final int MAX_LANGUAGE_ID_NODES = 160;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final long POLL_MS = 1000L;
    private static final long POLL_MAX_MS = 5000L;
    private static final long MODEL_HINT_DELAY_MS = 1500L;
    private static final String STATE_JS = "(function(){var s=window.__coaraTr;return s&&s.active?1:0;})()";
    private static final String POLL_JS = "(function(){var s=window.__coaraTr;return s?(s.dirty?1:0):-1;})()";
    private static final String RESTORE_JS = "(function(){var s=window.__coaraTr;if(s)s.restore();return 1;})()";
    private static final String COMPACT_JS = "(function(){var s=window.__coaraTr;if(s&&s.compact)s.compact();return 1;})()";

    private static final Map<BrowserView, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<String, Translator> TRANSLATORS = new HashMap<>();
    private static String extractScript;
    private static ExecutorService netExecutor;
    private static LanguageIdentifier identifier;
    private static boolean mlKitReady;

    private PageTranslator() {
    }

    private static final class Chunk {
        final int[] indexes;
        final String[] texts;
        final String source;

        Chunk(int[] indexes, String[] texts, String source) {
            this.indexes = indexes;
            this.texts = texts;
            this.source = source;
        }
    }

    private static final class Session {
        final WeakReference<BrowserView> view;
        final boolean allowOnline;
        final boolean silent;
        final String target;
        final boolean sourceFixed;
        final ArrayDeque<Chunk> mlQueue = new ArrayDeque<>();
        String source;
        boolean online;
        boolean started;
        boolean failed;
        boolean errorShown;
        final Set<String> readyModelKeys = new HashSet<>();
        boolean mlRunning;
        boolean polling;
        int pending;
        int initialRetries;
        long pollMs = POLL_MS;

        Session(BrowserView view, boolean allowOnline, boolean silent, String target, String fixedSource) {
            this.view = new WeakReference<>(view);
            this.allowOnline = allowOnline;
            this.online = allowOnline;
            this.silent = silent;
            this.target = target;
            this.sourceFixed = fixedSource != null && !TranslateSettings.AUTO.equals(fixedSource);
            this.source = sourceFixed ? fixedSource : null;
        }
    }

    private static boolean translatable(String url) {
        return url != null && !url.isEmpty()
                && (url.startsWith("http://") || url.startsWith("https://"))
                && !BrowserAssetLoader.isAppAssetUrl(url);
    }

    public static void toggle(BrowserView view, boolean allowOnline) {
        if (view == null) {
            return;
        }
        if (!translatable(view.getUrl())) {
            toast(view, "このページは翻訳できません");
            return;
        }
        if (GeckoNativePageTranslator.isActive(view)) {
            GeckoNativePageTranslator.restore(view, new GeckoNativePageTranslator.Callback() {
                @Override
                public void onSuccess() {
                    toast(view, "元の表示に戻しました");
                }

                @Override
                public void onFailure() {
                    toast(view, "原文に戻せませんでした。ページを再読み込みしてお試しください");
                }
            });
            return;
        }
        toast(view, "翻訳を開始しています…");
        view.evaluateJavascript(STATE_JS, value -> {
            if ("1".equals(value)) {
                restore(view);
            } else {
                startNativeOrLegacy(view, allowOnline, false);
            }
        });
    }

    private static void startNativeOrLegacy(BrowserView view, boolean allowOnline, boolean silent) {
        if (view == null || view.isDestroyed()) {
            return;
        }
        Context context = view.getContext();
        String target = TranslateSettings.getTarget(context, !allowOnline);
        String source = TranslateSettings.getSource(context, !allowOnline);
        GeckoNativePageTranslator.tryTranslate(view, source, target, new GeckoNativePageTranslator.Callback() {
            @Override
            public void onSuccess() {
                if (!silent) {
                    toast(view, "Firefox翻訳エンジンで翻訳を開始しました。初回は言語モデルをダウンロードします");
                }
            }

            @Override
            public void onFailure() {
                ensureMlKit(context);
                start(view, allowOnline, silent);
            }
        });
    }

    public static void onPageStarted(BrowserView view) {
        if (view != null) {
            Session session = SESSIONS.remove(view);
            cancelSession(session);
            GeckoNativePageTranslator.onPageStarted(view);
        }
    }

    public static void onPageFinished(BrowserView view, boolean allowOnline) {
        if (view == null) {
            return;
        }
        Context context = view.getContext();
        if (!TranslateSettings.isAutoTranslate(context, !allowOnline)) {
            return;
        }
        if (!translatable(view.getUrl()) || SESSIONS.get(view) != null || GeckoNativePageTranslator.isActive(view)) {
            return;
        }
        startNativeOrLegacy(view, allowOnline, true);
    }

    public static void release() {
        if (!SESSIONS.isEmpty()) {
            return;
        }
        releaseResources();
    }

    public static void releaseForViews(Iterable<BrowserView> views) {
        if (views != null) {
            for (BrowserView view : views) {
                releaseForView(view);
            }
        }
        if (SESSIONS.isEmpty()) {
            releaseResources();
        }
    }

    public static void releaseForView(BrowserView view) {
        if (view == null) {
            return;
        }
        GeckoNativePageTranslator.releaseForView(view);
        Session session = SESSIONS.remove(view);
        if (session != null) {
            session.failed = true;
            session.mlQueue.clear();
            session.pending = 0;
            try {
                if (!view.isDestroyed()) {
                    view.evaluateJavascript(RESTORE_JS, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void releaseResources() {
        for (Translator translator : TRANSLATORS.values()) {
            try {
                translator.close();
            } catch (Throwable ignored) {
            }
        }
        TRANSLATORS.clear();
        if (identifier != null) {
            try {
                identifier.close();
            } catch (Throwable ignored) {
            }
            identifier = null;
        }
        if (netExecutor != null) {
            netExecutor.shutdownNow();
            netExecutor = null;
        }
    }

    private static void ensureMlKit(Context context) {
        if (mlKitReady) {
            return;
        }
        try {
            MlKit.initialize(context.getApplicationContext());
            mlKitReady = true;
        } catch (Throwable ignored) {
        }
    }

    private static void start(BrowserView view, boolean allowOnline, boolean silent) {
        Context context = view.getContext();
        boolean secret = !allowOnline;
        Session session = new Session(view, allowOnline, silent,
                TranslateSettings.getTarget(context, secret),
                TranslateSettings.getSource(context, secret));
        SESSIONS.put(view, session);
        extract(session, true);
    }

    private static void restore(BrowserView view) {
        cancelSession(SESSIONS.remove(view));
        try {
            view.evaluateJavascript(RESTORE_JS, null);
        } catch (Exception ignored) {
        }
        toast(view, "元の表示に戻しました");
    }

    private static void cancelSession(Session session) {
        if (session == null) {
            return;
        }
        session.failed = true;
        session.mlQueue.clear();
        session.pending = 0;
    }

    private static boolean alive(Session session) {
        BrowserView view = session.view.get();
        return view != null && !session.failed && SESSIONS.get(view) == session;
    }

    private static void extract(Session session, boolean first) {
        BrowserView view = session.view.get();
        if (view == null || !alive(session)) {
            return;
        }
        String script = script(view.getContext());
        if (script.isEmpty()) {
            abort(session, "翻訳できませんでした");
            return;
        }
        try {
            view.evaluateJavascript("window.__coaraTrTarget=" + JSONObject.quote(session.target) + ";" + script,
                    value -> onExtracted(session, value, first));
        } catch (Exception e) {
            abort(session, "翻訳できませんでした");
        }
    }

    private static void onExtracted(Session session, String value, boolean first) {
        BrowserView view = session.view.get();
        if (view == null || !alive(session)) {
            return;
        }
        JSONObject result;
        try {
            result = new JSONObject(value);
        } catch (Exception e) {
            if (first) {
                abort(session, "翻訳できませんでした");
            } else {
                schedulePoll(session);
            }
            return;
        }
        JSONArray array = result.optJSONArray("texts");
        JSONArray indexArray = result.optJSONArray("indexes");
        JSONArray languageArray = result.optJSONArray("langs");
        int count = array == null ? 0 : array.length();
        if (count == 0) {
            if (first) {
                retryInitialExtraction(session);
            } else {
                session.pollMs = Math.min(POLL_MAX_MS, session.pollMs + 500L);
                schedulePoll(session);
            }
            return;
        }
        session.pollMs = POLL_MS;
        String[] texts = new String[count];
        String[] languages = new String[count];
        int[] indexes = new int[count];
        int base = result.optInt("base", 0);
        for (int i = 0; i < count; i++) {
            texts[i] = array.optString(i);
            languages[i] = languageArray == null ? "" : languageArray.optString(i, "");
            indexes[i] = indexArray == null ? base + i : indexArray.optInt(i, base + i);
        }
        String lang = result.optString("lang", "");
        if (session.sourceFixed && session.source != null
                && session.source.equals(primary(session.target))) {
            abort(session, "翻訳元と翻訳先が同じ言語です");
            return;
        }
        if (session.source == null) {
            detect(session, texts, lang, indexes, languages);
        } else if (session.sourceFixed) {
            dispatch(session, indexes, texts);
        } else {
            dispatchMixed(session, indexes, texts, languages, session.source);
        }
    }

    private static void retryInitialExtraction(Session session) {
        if (!alive(session)) {
            return;
        }
        if (session.initialRetries >= 6) {
            abort(session, "翻訳できるテキストがありません");
            return;
        }
        session.initialRetries++;
        UiThread.mainHandler().postDelayed(() -> {
            if (alive(session)) {
                extract(session, true);
            }
        }, 350L);
    }

    private static void detect(Session session, String[] texts, String htmlLang, int[] indexes, String[] languages) {
        ArrayList<String> candidates = new ArrayList<>();
        Set<String> uniqueCandidates = new HashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String value = text.trim();
            if (value.length() >= 18 && uniqueCandidates.add(value)) {
                candidates.add(value);
            }
        }
        Collections.sort(candidates, Comparator.comparingInt(String::length).reversed());
        if (candidates.size() > 12) {
            candidates.subList(12, candidates.size()).clear();
        }
        StringBuilder sample = new StringBuilder();
        for (String candidate : candidates) {
            if (sample.length() >= 1200) {
                break;
            }
            int remaining = 1200 - sample.length();
            sample.append(candidate, 0, Math.min(candidate.length(), remaining)).append(' ');
        }
        if (sample.length() < 24) {
            for (String text : texts) {
                if (text != null && !text.trim().isEmpty()) {
                    int remaining = Math.max(0, 1200 - sample.length());
                    if (remaining == 0) {
                        break;
                    }
                    String clean = text.trim();
                    sample.append(clean, 0, Math.min(clean.length(), remaining)).append(' ');
                }
            }
        }
        final String detectionSample = sample.toString();
        try {
            identifier().identifyLanguage(detectionSample)
                    .addOnSuccessListener(tag -> onInitialLanguageDetected(session, tag, htmlLang, indexes, texts, languages))
                    .addOnFailureListener(e -> onInitialLanguageDetected(session, "und", htmlLang, indexes, texts, languages));
        } catch (Throwable t) {
            onInitialLanguageDetected(session, "und", htmlLang, indexes, texts, languages);
        }
    }

    private static void onInitialLanguageDetected(Session session, String tag, String htmlLang, int[] indexes,
                                                  String[] texts, String[] languages) {
        if (!alive(session)) {
            return;
        }
        String detected = primary(tag);
        String target = primary(session.target);
        for (String language : languages) {
            String hint = primary(language);
            if (!hint.isEmpty() && !"und".equals(hint) && !hint.equals(target)
                    && TranslateLanguage.fromLanguageTag(hint) != null) {
                String fallback = !detected.isEmpty() && !"und".equals(detected) && !detected.equals(target)
                        ? detected : hint;
                onSource(session, fallback, htmlLang, indexes, texts, languages);
                return;
            }
        }
        String fallback = !detected.isEmpty() && !"und".equals(detected) ? detected : primary(htmlLang);
        onSource(session, fallback, htmlLang, indexes, texts, languages);
    }

    private static void onSource(Session session, String tag, String htmlLang, int[] indexes, String[] texts,
                                 String[] languages) {
        if (!alive(session)) {
            return;
        }
        String source = primary(tag);
        if (source.isEmpty() || "und".equals(source)) {
            String pageLanguage = primary(htmlLang);
            source = !pageLanguage.isEmpty() ? pageLanguage : "und";
        }
        session.source = source;
        if (session.sourceFixed) {
            dispatch(session, indexes, texts);
        } else {
            dispatchMixed(session, indexes, texts, languages, source);
        }
    }

    private static void dispatch(Session session, int[] indexes, String[] texts) {
        String source = primary(session.source);
        if (source.isEmpty() || "und".equals(source)) {
            mlFail(session, "ページの言語を判定できませんでした");
            return;
        }
        ArrayList<Integer> selectedIndexes = new ArrayList<>(texts.length);
        ArrayList<String> values = new ArrayList<>(texts.length);
        for (int i = 0; i < texts.length; i++) {
            if (texts[i] != null && !texts[i].trim().isEmpty() && indexes[i] >= 0) {
                selectedIndexes.add(indexes[i]);
                values.add(texts[i]);
            }
        }
        enqueueChunks(session, source, selectedIndexes, values);
    }

    private static void dispatchMixed(Session session, int[] indexes, String[] texts, String[] hints, String fallbackSource) {
        if (!alive(session)) {
            return;
        }
        String fallback = primary(fallbackSource);
        String target = primary(session.target);
        String[] resolved = new String[texts.length];
        int[] detectIndexes = new int[Math.min(texts.length, MAX_LANGUAGE_ID_NODES)];
        int detectCount = 0;
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i] == null ? "" : texts[i].trim();
            String hint = hints != null && i < hints.length ? primary(hints[i]) : "";
            if (!hint.isEmpty() && TranslateLanguage.fromLanguageTag(hint) != null) {
                resolved[i] = hint;
                continue;
            }
            if (text.length() >= 8) {
                if (detectCount < detectIndexes.length) {
                    resolved[i] = "und";
                    detectIndexes[detectCount++] = i;
                }
            } else {
                resolved[i] = fallback;
            }
        }
        if (detectCount == 0) {
            enqueueResolved(session, indexes, texts, resolved, fallback, target);
            return;
        }
        final int total = detectCount;
        final int[] completed = {0};
        final boolean[] dispatched = {false};
        Runnable[] finishCheck = new Runnable[1];
        finishCheck[0] = () -> {
            if (dispatched[0] || completed[0] < total || !alive(session)) {
                return;
            }
            dispatched[0] = true;
            enqueueResolved(session, indexes, texts, resolved, fallback, target);
        };
        for (int i = 0; i < detectCount; i++) {
            int textIndex = detectIndexes[i];
            String rawSample = texts[textIndex];
            final String sample = rawSample.length() > 350 ? rawSample.substring(0, 350) : rawSample;
            try {
                identifier().identifyLanguage(sample)
                        .addOnSuccessListener(tag -> {
                            String language = primary(tag);
                            if (!language.isEmpty() && !"und".equals(language)
                                    && TranslateLanguage.fromLanguageTag(language) != null) {
                                resolved[textIndex] = language;
                            }
                            completed[0]++;
                            finishCheck[0].run();
                        })
                        .addOnFailureListener(error -> {
                            completed[0]++;
                            finishCheck[0].run();
                        });
            } catch (Throwable error) {
                completed[0]++;
            }
        }
        finishCheck[0].run();
    }

    private static void enqueueResolved(Session session, int[] indexes, String[] texts, String[] resolved,
                                        String fallback, String target) {
        if (!alive(session)) {
            return;
        }
        Map<String, ArrayList<Integer>> groupedIndexes = new HashMap<>();
        Map<String, ArrayList<String>> groupedTexts = new HashMap<>();
        ArrayList<Integer> handledIndexes = new ArrayList<>(texts.length);
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i] == null ? "" : texts[i].trim();
            if (text.isEmpty() || indexes[i] < 0) {
                continue;
            }
            String resolvedSource = resolved[i];
            if (resolvedSource == null) {
                continue;
            }
            String source = resolvedSource;
            if (source.isEmpty() || "und".equals(source)) {
                source = fallback;
            }
            source = primary(source);
            handledIndexes.add(indexes[i]);
            if (source.isEmpty() || "und".equals(source) || source.equals(target)
                    || TranslateLanguage.fromLanguageTag(source) == null) {
                continue;
            }
            groupedIndexes.computeIfAbsent(source, key -> new ArrayList<>()).add(indexes[i]);
            groupedTexts.computeIfAbsent(source, key -> new ArrayList<>()).add(texts[i]);
        }
        BrowserView view = session.view.get();
        if (view != null && !handledIndexes.isEmpty()) {
            JSONArray handled = new JSONArray();
            for (int index : handledIndexes) {
                handled.put(index);
            }
            try {
                view.evaluateJavascript("(function(){var s=window.__coaraTr;if(s&&s.markDoneIndexes)s.markDoneIndexes("
                        + handled + ");})()", null);
            } catch (Throwable ignored) {
            }
        }
        if (groupedIndexes.isEmpty()) {
            if (view != null) {
                try {
                    view.evaluateJavascript(COMPACT_JS, null);
                } catch (Throwable ignored) {
                }
            }
            if (!session.started && !session.errorShown && !session.silent && handledIndexes.size() == texts.length) {
                session.errorShown = true;
                if (view != null) {
                    toast(view, fallback.equals(target)
                            ? "このページはすでに翻訳先の言語です"
                            : "翻訳できるテキストが見つかりませんでした");
                }
            }
            schedulePoll(session);
            return;
        }
        session.started = true;
        for (String source : groupedIndexes.keySet()) {
            enqueueChunks(session, source, groupedIndexes.get(source), groupedTexts.get(source));
        }
    }

    private static void enqueueChunks(Session session, String source, List<Integer> indexes, List<String> texts) {
        if (!alive(session) || indexes == null || texts == null || indexes.isEmpty()) {
            return;
        }
        int from = 0;
        while (from < texts.size()) {
            int to = from;
            int chars = 0;
            while (to < texts.size() && to - from < CHUNK_SEGMENTS
                    && (to == from || chars + texts.get(to).length() <= CHUNK_CHARS)) {
                chars += texts.get(to).length();
                to++;
            }
            int[] chunkIndexes = new int[to - from];
            String[] chunkTexts = new String[to - from];
            for (int i = from; i < to; i++) {
                chunkIndexes[i - from] = indexes.get(i);
                chunkTexts[i - from] = texts.get(i);
            }
            Chunk chunk = new Chunk(chunkIndexes, chunkTexts, source);
            session.pending++;
            if (session.online) {
                runOnline(session, chunk);
            } else {
                mlEnqueue(session, chunk);
            }
            from = to;
        }
    }

    private static void runOnline(Session session, Chunk chunk) {
        final String source = chunk.source;
        final String target = session.target;
        executor().execute(() -> {
            String[] translated = null;
            try {
                translated = onlineTranslate(source, target, chunk.texts);
            } catch (Throwable ignored) {
            }
            final String[] result = translated;
            UiThread.mainHandler().post(() -> {
                if (!alive(session)) {
                    return;
                }
                if (result != null) {
                    apply(session, chunk, result);
                    finish(session);
                } else {
                    session.online = false;
                    mlEnqueue(session, chunk);
                }
            });
        });
    }

    private static String[] onlineTranslate(String source, String target, String[] texts) throws Exception {
        String[] separators = createSeparators(texts.length - 1);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) {
                joined.append("\n\n").append(separators[i - 1]).append("\n\n");
            }
            joined.append(texts[i]);
        }
        String output = post(source, target, joined.toString());
        String[] result = splitSeparated(output, separators, texts.length);
        if (result != null) {
            return result;
        }
        result = new String[texts.length];
        for (int i = 0; i < texts.length; i++) {
            result[i] = post(source, target, texts[i]).replace("\n", " ").trim();
        }
        return result;
    }

    private static String[] createSeparators(int count) {
        String[] result = new String[Math.max(0, count)];
        String nonce = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        for (int i = 0; i < result.length; i++) {
            result[i] = "ZXQCOARA" + nonce + "SEP" + i + "QXZ";
        }
        return result;
    }

    private static String[] splitSeparated(String input, String[] separators, int expected) {
        if (input == null || expected < 1 || separators.length != expected - 1) {
            return null;
        }
        String[] result = new String[expected];
        int from = 0;
        for (int i = 0; i < separators.length; i++) {
            int index = input.indexOf(separators[i], from);
            if (index < from) {
                return null;
            }
            result[i] = input.substring(from, index).trim();
            from = index + separators[i].length();
        }
        result[expected - 1] = input.substring(from).trim();
        return result;
    }

    private static String onlineCode(String tag) {
        return "zh".equals(tag) ? "zh-CN" : tag;
    }

    private static String post(String source, String target, String text) throws Exception {
        String body = "client=gtx&sl=" + URLEncoder.encode(onlineCode(source), "UTF-8")
                + "&tl=" + URLEncoder.encode(onlineCode(target), "UTF-8")
                + "&dt=t&dj=1&ie=UTF-8&oe=UTF-8&q=" + URLEncoder.encode(text, "UTF-8");
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", ONLINE_UA);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setFixedLengthStreamingMode(payload.length);
            try (java.io.OutputStream request = connection.getOutputStream()) {
                request.write(payload);
            }
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("http " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > MAX_RESPONSE_BYTES) {
                        throw new java.io.IOException("translation response too large");
                    }
                    out.write(buffer, 0, read);
                }
                JSONObject json = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                JSONArray sentences = json.getJSONArray("sentences");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < sentences.length(); i++) {
                    result.append(sentences.getJSONObject(i).optString("trans", ""));
                }
                return result.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void mlEnqueue(Session session, Chunk chunk) {
        session.mlQueue.add(chunk);
        mlPump(session);
    }

    private static void mlPump(Session session) {
        if (session.mlRunning || session.failed) {
            return;
        }
        Chunk chunk = session.mlQueue.poll();
        if (chunk == null) {
            return;
        }
        session.mlRunning = true;
        if (!alive(session)) {
            session.mlRunning = false;
            return;
        }
        Translator translator = translator(chunk.source, session.target);
        if (translator == null) {
            mlFail(session, "翻訳に失敗しました。ネットワーク接続を確認してください");
            return;
        }
        ensureModel(session, translator, chunk.source, () -> mlTranslate(session, translator, chunk));
    }

    private static void ensureModel(Session session, Translator translator, String source, Runnable ready) {
        String modelKey = translatorKey(source, session.target);
        if (session.readyModelKeys.contains(modelKey)) {
            ready.run();
            return;
        }
        final Runnable hint = () -> {
            BrowserView view = session.view.get();
            if (view != null && alive(session)) {
                toast(view, "翻訳モデルを準備中です(初回のみダウンロードします)");
            }
        };
        UiThread.mainHandler().postDelayed(hint, MODEL_HINT_DELAY_MS);
        try {
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                    .addOnSuccessListener(unused -> {
                        UiThread.mainHandler().removeCallbacks(hint);
                        session.readyModelKeys.add(modelKey);
                        if (alive(session)) {
                            ready.run();
                        } else {
                            session.mlRunning = false;
                        }
                    })
                    .addOnFailureListener(e -> {
                        UiThread.mainHandler().removeCallbacks(hint);
                        mlFail(session, "翻訳モデルを取得できませんでした。ネットワーク接続を確認してください");
                    });
        } catch (Throwable t) {
            UiThread.mainHandler().removeCallbacks(hint);
            mlFail(session, "翻訳に失敗しました");
        }
    }

    private static void mlTranslate(Session session, Translator translator, Chunk chunk) {
        if (!alive(session)) {
            session.mlRunning = false;
            return;
        }
        String[] separators = createSeparators(chunk.texts.length - 1);
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < chunk.texts.length; i++) {
            if (i > 0) {
                input.append("\n\n").append(separators[i - 1]).append("\n\n");
            }
            input.append(chunk.texts[i]);
        }
        try {
            translator.translate(input.toString())
                    .addOnSuccessListener(translated -> {
                        String[] parts = splitSeparated(translated, separators, chunk.texts.length);
                        if (parts != null) {
                            apply(session, chunk, parts);
                            session.mlRunning = false;
                            finish(session);
                            mlPump(session);
                        } else {
                            mlTranslateSequential(session, translator, chunk, new String[chunk.texts.length], 0);
                        }
                    })
                    .addOnFailureListener(error -> mlTranslateSequential(
                            session, translator, chunk, new String[chunk.texts.length], 0));
        } catch (Throwable error) {
            mlTranslateSequential(session, translator, chunk, new String[chunk.texts.length], 0);
        }
    }

    private static void mlTranslateSequential(Session session, Translator translator, Chunk chunk,
                                              String[] result, int index) {
        if (!alive(session)) {
            session.mlRunning = false;
            return;
        }
        if (index >= result.length) {
            apply(session, chunk, result);
            session.mlRunning = false;
            finish(session);
            mlPump(session);
            return;
        }
        try {
            translator.translate(chunk.texts[index])
                    .addOnSuccessListener(text -> {
                        result[index] = text;
                        mlTranslateSequential(session, translator, chunk, result, index + 1);
                    })
                    .addOnFailureListener(error -> {
                        result[index] = chunk.texts[index];
                        mlTranslateSequential(session, translator, chunk, result, index + 1);
                    });
        } catch (Throwable error) {
            result[index] = chunk.texts[index];
            mlTranslateSequential(session, translator, chunk, result, index + 1);
        }
    }

    private static void mlFail(Session session, String message) {
        session.mlRunning = false;
        session.failed = true;
        session.pending = 0;
        session.mlQueue.clear();
        BrowserView view = session.view.get();
        if (view != null && alive(session) && !session.errorShown && !session.silent) {
            session.errorShown = true;
            toast(view, message);
        }
    }

    private static void apply(Session session, Chunk chunk, String[] result) {
        BrowserView view = session.view.get();
        if (view == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String item : result) {
            array.put(item == null ? "" : item);
        }
        JSONArray indexes = new JSONArray();
        for (int index : chunk.indexes) {
            indexes.put(index);
        }
        try {
            view.evaluateJavascript("(function(){var s=window.__coaraTr;if(s)s.applyIndexes("
                    + indexes.toString() + "," + array.toString() + ");})()", null);
        } catch (Exception ignored) {
        }
    }

    private static void finish(Session session) {
        session.pending--;
        if (session.pending <= 0) {
            session.pending = 0;
            BrowserView view = session.view.get();
            if (view != null && alive(session)) {
                try {
                    view.evaluateJavascript(COMPACT_JS, null);
                } catch (Throwable ignored) {
                }
            }
            schedulePoll(session);
        }
    }

    private static void abort(Session session, String message) {
        BrowserView view = session.view.get();
        if (view == null) {
            return;
        }
        SESSIONS.remove(view);
        cancelSession(session);
        try {
            view.evaluateJavascript(RESTORE_JS, null);
        } catch (Exception ignored) {
        }
        if (!session.silent && !message.isEmpty()) {
            toast(view, message);
        }
    }

    private static void schedulePoll(Session session) {
        if (session.polling || session.failed || !alive(session)) {
            return;
        }
        session.polling = true;
        UiThread.mainHandler().postDelayed(() -> {
            session.polling = false;
            poll(session);
        }, session.pollMs);
    }

    private static void poll(Session session) {
        BrowserView view = session.view.get();
        if (view == null || !alive(session)) {
            return;
        }
        if (!view.isAttachedToWindow() || session.pending > 0) {
            schedulePoll(session);
            return;
        }
        try {
            view.evaluateJavascript(POLL_JS, value -> {
                if (!alive(session)) {
                    return;
                }
                if ("1".equals(value)) {
                    extract(session, false);
                } else if ("0".equals(value)) {
                    session.pollMs = Math.min(POLL_MAX_MS, session.pollMs + 500L);
                    schedulePoll(session);
                } else {
                    SESSIONS.remove(view);
                }
            });
        } catch (Exception e) {
            SESSIONS.remove(view);
        }
    }

    private static String translatorKey(String sourceTag, String targetTag) {
        String from = TranslateLanguage.fromLanguageTag(sourceTag);
        String to = TranslateLanguage.fromLanguageTag(targetTag);
        return from == null || to == null ? "" : from + ">" + to;
    }

    private static Translator translator(String sourceTag, String targetTag) {
        String from = TranslateLanguage.fromLanguageTag(sourceTag);
        String to = TranslateLanguage.fromLanguageTag(targetTag);
        if (from == null || to == null || from.equals(to)) {
            return null;
        }
        String key = from + ">" + to;
        Translator translator = TRANSLATORS.get(key);
        if (translator == null) {
            translator = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(from)
                    .setTargetLanguage(to)
                    .build());
            TRANSLATORS.put(key, translator);
        }
        return translator;
    }

    static void identifyLanguage(Context context, String text, java.util.function.Consumer<String> callback) {
        if (callback == null) {
            return;
        }
        ensureMlKit(context);
        try {
            identifier().identifyLanguage(text == null ? "" : text)
                    .addOnSuccessListener(callback::accept)
                    .addOnFailureListener(error -> callback.accept("und"));
        } catch (Throwable t) {
            callback.accept("und");
        }
    }

    private static LanguageIdentifier identifier() {
        if (identifier == null) {
            identifier = LanguageIdentification.getClient(
                    new LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build());
        }
        return identifier;
    }

    private static ExecutorService executor() {
        if (netExecutor == null) {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), runnable -> {
                        Thread thread = new Thread(runnable, "coara-translate");
                        thread.setDaemon(true);
                        return thread;
                    });
            pool.allowCoreThreadTimeOut(true);
            netExecutor = pool;
        }
        return netExecutor;
    }

    private static String script(Context context) {
        if (extractScript == null) {
            try (InputStream in = context.getAssets().open(ASSET_NAME)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                extractScript = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }
        return extractScript;
    }

    private static String primary(String tag) {
        if (tag == null) {
            return "";
        }
        String lower = tag.trim().toLowerCase(Locale.ROOT);
        int cut = lower.indexOf('-');
        if (cut < 0) {
            cut = lower.indexOf('_');
        }
        return cut > 0 ? lower.substring(0, cut) : lower;
    }

    private static void toast(BrowserView view, String message) {
        try {
            Toast.makeText(view.getContext().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
