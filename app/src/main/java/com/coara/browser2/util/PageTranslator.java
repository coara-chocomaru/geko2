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
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PageTranslator {

    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final String ONLINE_UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String ASSET_NAME = "page_translate.js";
    private static final int CHUNK_CHARS = 3000;
    private static final int CHUNK_SEGMENTS = 50;
    private static final long POLL_MS = 1000L;
    private static final long POLL_MAX_MS = 5000L;
    private static final long MODEL_HINT_DELAY_MS = 1500L;
    private static final String STATE_JS = "(function(){var s=window.__coaraTr;return s&&s.active?1:0;})()";
    private static final String POLL_JS = "(function(){var s=window.__coaraTr;return s?(s.dirty?1:0):-1;})()";
    private static final String RESTORE_JS = "(function(){var s=window.__coaraTr;if(s)s.restore();return 1;})()";

    private static final Map<BrowserView, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<String, Translator> TRANSLATORS = new HashMap<>();
    private static String extractScript;
    private static ExecutorService netExecutor;
    private static LanguageIdentifier identifier;
    private static boolean mlKitReady;

    private PageTranslator() {
    }

    private static final class Chunk {
        final int base;
        final String[] texts;

        Chunk(int base, String[] texts) {
            this.base = base;
            this.texts = texts;
        }
    }

    private static final class Session {
        final WeakReference<BrowserView> view;
        final boolean allowOnline;
        final boolean silent;
        final String target;
        final ArrayDeque<Chunk> mlQueue = new ArrayDeque<>();
        String source;
        boolean online;
        boolean started;
        boolean failed;
        boolean errorShown;
        boolean mlReady;
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
            this.source = TranslateSettings.AUTO.equals(fixedSource) ? null : fixedSource;
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
        ensureMlKit(view.getContext());
        toast(view, "翻訳を開始しています…");
        view.evaluateJavascript(STATE_JS, value -> {
            if ("1".equals(value)) {
                restore(view);
            } else if (value == null || "null".equals(value)) {
                toast(view, "翻訳処理を開始できませんでした。ページを読み込んでから再度お試しください");
            } else {
                start(view, allowOnline, false);
            }
        });
    }

    public static void onPageStarted(BrowserView view) {
        if (view != null) {
            SESSIONS.remove(view);
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
        if (!translatable(view.getUrl()) || SESSIONS.get(view) != null) {
            return;
        }
        ensureMlKit(context);
        start(view, allowOnline, true);
    }

    public static void release() {
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
        SESSIONS.clear();
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
        SESSIONS.remove(view);
        try {
            view.evaluateJavascript(RESTORE_JS, null);
        } catch (Exception ignored) {
        }
        toast(view, "元の表示に戻しました");
    }

    private static boolean alive(Session session) {
        BrowserView view = session.view.get();
        return view != null && SESSIONS.get(view) == session;
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
        for (int i = 0; i < count; i++) {
            texts[i] = array.optString(i);
        }
        int base = result.optInt("base", 0);
        String lang = result.optString("lang", "");
        if (session.source != null && session.source.equals(primary(session.target))) {
            abort(session, "翻訳元と翻訳先が同じ言語です");
            return;
        }
        if (session.source == null) {
            detect(session, texts, lang, base);
        } else {
            dispatch(session, base, texts);
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

    private static void detect(Session session, String[] texts, String htmlLang, int base) {
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
                    sample.append(text.trim()).append(' ');
                    if (sample.length() >= 1200) {
                        break;
                    }
                }
            }
        }
        final String detectionSample = sample.toString();
        try {
            identifier().identifyLanguage(detectionSample)
                    .addOnSuccessListener(tag -> onInitialLanguageDetected(session, tag, candidates, htmlLang, base, texts))
                    .addOnFailureListener(e -> onInitialLanguageDetected(session, "und", candidates, htmlLang, base, texts));
        } catch (Throwable t) {
            onInitialLanguageDetected(session, "und", candidates, htmlLang, base, texts);
        }
    }

    private static void onInitialLanguageDetected(Session session, String tag, ArrayList<String> candidates,
                                                   String htmlLang, int base, String[] texts) {
        if (!alive(session)) {
            return;
        }
        String detected = primary(tag);
        String target = primary(session.target);
        if (!detected.isEmpty() && !"und".equals(detected) && !detected.equals(target)) {
            onSource(session, detected, htmlLang, base, texts);
            return;
        }
        probeLanguageCandidates(session, candidates, 0, detected, htmlLang, base, texts);
    }

    private static void probeLanguageCandidates(Session session, ArrayList<String> candidates, int index,
                                                String initialDetected, String htmlLang, int base, String[] texts) {
        if (!alive(session)) {
            return;
        }
        if (index >= candidates.size() || index >= 8) {
            String target = primary(session.target);
            String pageLanguage = primary(htmlLang);
            if (session.allowOnline && !textsEmpty(texts)) {
                onSource(session, "auto", htmlLang, base, texts);
            } else if (target.equals(initialDetected) || target.equals(pageLanguage)) {
                abort(session, "このページは" + TranslateSettings.labelFor(session.target) + "です");
            } else if (!pageLanguage.isEmpty()) {
                onSource(session, pageLanguage, htmlLang, base, texts);
            } else {
                abort(session, "ページの言語を判定できませんでした");
            }
            return;
        }
        try {
            identifier().identifyLanguage(candidates.get(index))
                    .addOnSuccessListener(tag -> {
                        String detected = primary(tag);
                        if (!detected.isEmpty() && !"und".equals(detected)
                                && !detected.equals(primary(session.target))) {
                            onSource(session, detected, htmlLang, base, texts);
                        } else {
                            probeLanguageCandidates(session, candidates, index + 1, initialDetected,
                                    htmlLang, base, texts);
                        }
                    })
                    .addOnFailureListener(e -> probeLanguageCandidates(session, candidates, index + 1,
                            initialDetected, htmlLang, base, texts));
        } catch (Throwable t) {
            probeLanguageCandidates(session, candidates, index + 1, initialDetected, htmlLang, base, texts);
        }
    }

    private static boolean textsEmpty(String[] texts) {
        for (String text : texts) {
            if (text != null && !text.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void onSource(Session session, String tag, String htmlLang, int base, String[] texts) {
        BrowserView view = session.view.get();
        if (view == null || !alive(session)) {
            return;
        }
        String source = primary(tag);
        if (source.isEmpty() || "und".equals(source)) {
            String pageLanguage = primary(htmlLang);
            if (!pageLanguage.isEmpty() && !pageLanguage.equals(primary(session.target))) {
                source = pageLanguage;
            } else if (session.allowOnline) {
                source = "auto";
            } else {
                abort(session, "ページの言語を判定できませんでした");
                return;
            }
        }
        if (source.equals(primary(session.target))) {
            if (session.allowOnline) {
                source = "auto";
            } else {
                abort(session, "このページは" + TranslateSettings.labelFor(session.target) + "です");
                return;
            }
        }
        session.source = source;
        dispatch(session, base, texts);
    }

    private static void dispatch(Session session, int base, String[] texts) {
        session.started = true;
        int from = 0;
        while (from < texts.length) {
            int to = from;
            int chars = 0;
            while (to < texts.length && to - from < CHUNK_SEGMENTS && (to == from || chars + texts[to].length() <= CHUNK_CHARS)) {
                chars += texts[to].length();
                to++;
            }
            String[] part = new String[to - from];
            System.arraycopy(texts, from, part, 0, part.length);
            Chunk chunk = new Chunk(base + from, part);
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
        final String source = session.source;
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
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) {
                joined.append('\n');
            }
            joined.append(texts[i]);
        }
        String output = post(source, target, joined.toString());
        String[] parts = output.split("\n", -1);
        int end = parts.length;
        while (end > 0 && parts[end - 1].isEmpty()) {
            end--;
        }
        if (end == texts.length) {
            String[] result = new String[end];
            System.arraycopy(parts, 0, result, 0, end);
            return result;
        }
        String[] result = new String[texts.length];
        for (int i = 0; i < texts.length; i++) {
            String single = post(source, target, texts[i]);
            result[i] = single.replace("\n", " ").trim();
        }
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
            connection.setFixedLengthStreamingMode(payload.length);
            connection.getOutputStream().write(payload);
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("http " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
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
        Translator translator = translator(session.source, session.target);
        if (translator == null) {
            mlFail(session, "翻訳に失敗しました。ネットワーク接続を確認してください");
            return;
        }
        ensureModel(session, translator, () -> mlTranslate(session, translator, chunk, new String[chunk.texts.length], 0));
    }

    private static void ensureModel(Session session, Translator translator, Runnable ready) {
        if (session.mlReady) {
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
                        session.mlReady = true;
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

    private static void mlTranslate(Session session, Translator translator, Chunk chunk, String[] result, int index) {
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
                        mlTranslate(session, translator, chunk, result, index + 1);
                    })
                    .addOnFailureListener(e -> {
                        result[index] = "";
                        mlTranslate(session, translator, chunk, result, index + 1);
                    });
        } catch (Throwable t) {
            result[index] = "";
            mlTranslate(session, translator, chunk, result, index + 1);
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
        try {
            view.evaluateJavascript("(function(){var s=window.__coaraTr;if(s)s.apply("
                    + chunk.base + "," + array.toString() + ");})()", null);
        } catch (Exception ignored) {
        }
    }

    private static void finish(Session session) {
        session.pending--;
        if (session.pending <= 0) {
            session.pending = 0;
            schedulePoll(session);
        }
    }

    private static void abort(Session session, String message) {
        BrowserView view = session.view.get();
        if (view == null) {
            return;
        }
        SESSIONS.remove(view);
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

    private static LanguageIdentifier identifier() {
        if (identifier == null) {
            identifier = LanguageIdentification.getClient(
                    new LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build());
        }
        return identifier;
    }

    private static ExecutorService executor() {
        if (netExecutor == null) {
            netExecutor = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "coara-translate");
                thread.setDaemon(true);
                return thread;
            });
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
