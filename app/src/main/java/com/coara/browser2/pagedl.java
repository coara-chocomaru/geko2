package com.coara.browser2;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.coara.browser2.gecko.BrowserClient;
import com.coara.browser2.gecko.BrowserHost;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.CookieBridge;

import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class pagedl extends AppCompatActivity implements BrowserHost {
    private static final String TAG = "pagedl";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "Mozilla/5.0 (Android 10; Mobile; rv:157.0) Gecko/157.0 Firefox/157.0";
    private static final String ACCEPT_HEADER = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final long SAVE_TIMEOUT_MS = 90000L;
    private static final long READINESS_TIMEOUT_MS = 6000L;
    private static final int MAX_SCROLL_STEPS = 30;
    private static final int SCROLL_DELAY_MS = 140;
    private static final int READINESS_INTERVAL_MS = 400;
    private static final int REQUESTED_HTML_WAIT_MS = 1200;
    private static final String PREPARE_SCRIPT = "(function(){try{var a=document.images;for(var i=0;i<a.length;i++){try{a[i].loading='eager';}catch(e){}}var d=document.documentElement,b=document.body;return Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);}catch(e){return 0;}})()";
    private static final String READINESS_SCRIPT = "(function(){try{var p=0,a=document.images;for(var i=0;i<a.length;i++){if(!a[i].complete)p++;}var r=window.performance&&performance.getEntriesByType?performance.getEntriesByType('resource').length:0;return (document.readyState==='complete'?1000000000:0)+(Math.min(p,99)*1000000)+Math.min(r,999999);}catch(e){return 0;}})()";
    private static final String DUMP_SCRIPT = "(function(){var d=document.doctype;var t=d?'<!DOCTYPE '+d.name+'>\\n':'';return t+document.documentElement.outerHTML;})()";

    private EditText urlInput;
    private Switch jsSwitch;
    private Switch localPathSwitch;
    private Button saveButton;
    private BrowserView webView;
    private volatile boolean isSaving;
    private boolean basicAuthEnabled;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int saveGeneration;
    private int pageGeneration;
    private String requestedUrl = "";
    private boolean captureStarted;
    private Runnable saveTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pgdl);
        basicAuthEnabled = getIntent().getBooleanExtra(BasicAuthManager.EXTRA_BASIC_AUTH_ENABLED, false);

        urlInput = findViewById(R.id.urlInput);
        jsSwitch = findViewById(R.id.jsSwitch);
        localPathSwitch = findViewById(R.id.localPathSwitch);
        saveButton = findViewById(R.id.saveButton);
        webView = new BrowserView(this, true);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.webViewHost)).addView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        installClient();
        saveButton.setOnClickListener(v -> handleSaveButtonClick());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            handleSaveButtonClick();
            return true;
        });
    }

    private void installClient() {
        webView.setClient(new BrowserClient() {
            @Override
            public void onPageStarted(BrowserView view, String url) {
                if (isSaving) {
                    pageGeneration++;
                    captureStarted = false;
                }
            }

            @Override
            public void onLoadFailed(BrowserView view, LoadFailure failure) {
                if (isSaving && !captureStarted && requestedUrl.length() > 0) {
                    captureStarted = true;
                    failSaving("ページを読み込めませんでした");
                }
            }

            @Override
            public void onPageFinished(BrowserView view, String url) {
                if (!isSaving || captureStarted || requestedUrl.isEmpty()) {
                    return;
                }
                captureStarted = true;
                prepareCapture(saveGeneration, pageGeneration);
            }
        });
    }

    private void handleSaveButtonClick() {
        if (isSaving) {
            Toast.makeText(this, "保存処理中です", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webView == null) {
            Toast.makeText(this, "画面を開き直してください", Toast.LENGTH_LONG).show();
            return;
        }
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "URLを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (input.regionMatches(true, 0, "blob:", 0, 5)) {
            Toast.makeText(this, "blob: URLはサポートされていません", Toast.LENGTH_LONG).show();
            return;
        }
        final String url = normalizeInputUrl(input);
        if (url == null) {
            Toast.makeText(this, "http://、https://、または data: のURLを入力してください", Toast.LENGTH_LONG).show();
            return;
        }
        if (url.regionMatches(true, 0, "data:", 0, 5) && !isHtmlDataUrl(url)) {
            final int token = beginSavingState();
            executor.execute(() -> saveDataUrl(url, token));
            return;
        }
        final int token = beginSavingState();
        if (!jsSwitch.isChecked() && url.regionMatches(true, 0, "http", 0, 4)) {
            final String siteName = siteNameForUrl(url);
            executor.execute(() -> saveWithoutJavaScript(url, siteName, token));
            return;
        }
        requestedUrl = url;
        captureStarted = false;
        webView.getSettings().setJavaScriptEnabled(true);
        try {
            webView.loadUrl(url);
        } catch (Exception e) {
            Log.e(TAG, "ページ読み込み開始エラー", e);
            failSaving("ページを開けません: " + e.getMessage());
        }
    }

    private String normalizeInputUrl(String input) {
        String value = input.trim();
        if (value.regionMatches(true, 0, "data:", 0, 5)) {
            return value.indexOf(',') > 4 ? value : null;
        }
        if (!value.matches("(?i)^https?://.*")) {
            if (value.matches("(?i)^[a-z][a-z0-9+.-]*:.*")
                    && !value.matches("(?i)^[^/:\\s]+:\\d+(?:/.*)?$")) {
                return null;
            }
            value = "https://" + value;
        }
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
            return null;
        }
        return value;
    }

    private boolean isHtmlDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return false;
        }
        String header = dataUrl.substring(0, comma).toLowerCase(Locale.ROOT);
        return header.startsWith("data:text/html") || header.startsWith("data:application/xhtml+xml");
    }

    private int beginSavingState() {
        isSaving = true;
        saveGeneration++;
        pageGeneration = 0;
        requestedUrl = "";
        captureStarted = false;
        handler.removeCallbacksAndMessages(null);
        saveButton.setEnabled(false);
        saveButton.setText("保存中...");
        jsSwitch.setEnabled(false);
        localPathSwitch.setEnabled(false);
        saveTimeoutRunnable = () -> {
            if (isSaving) {
                failSaving("保存処理がタイムアウトしました。ページが重い場合は時間をおいて再試行してください。");
            }
        };
        handler.postDelayed(saveTimeoutRunnable, SAVE_TIMEOUT_MS);
        return saveGeneration;
    }

    private void prepareCapture(int token, int pageToken) {
        if (!isCurrentCapture(token, pageToken)) {
            return;
        }
        if (!jsSwitch.isChecked()) {
            handler.postDelayed(() -> dumpHtml(token, pageToken), REQUESTED_HTML_WAIT_MS);
            return;
        }
        webView.evaluateJavascript(PREPARE_SCRIPT, value -> {
            if (!isCurrentCapture(token, pageToken)) {
                return;
            }
            int height = parseJsInteger(value, webView.getHeight());
            int viewport = Math.max(1, webView.getHeight());
            if (height > viewport + 100) {
                scrollPageForCapture(token, pageToken, 0, 0, 0);
            } else {
                handler.postDelayed(() -> beginReadinessChecks(token, pageToken), 350L);
            }
        });
    }

    private void scrollPageForCapture(int token, int pageToken, int y, int steps, int bottomStreak) {
        if (!isCurrentCapture(token, pageToken)) {
            return;
        }
        if (steps >= MAX_SCROLL_STEPS) {
            finishLazyLoadPass(token, pageToken);
            return;
        }
        int viewport = Math.max(1, webView.getHeight());
        String script = "(function(){window.scrollTo(0," + y + ");var d=document.documentElement,b=document.body;return Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);})()";
        webView.evaluateJavascript(script, value -> {
            if (!isCurrentCapture(token, pageToken)) {
                return;
            }
            int height = parseJsInteger(value, y + viewport);
            int maxY = Math.max(0, height - viewport);
            int nextBottomStreak = y >= maxY ? bottomStreak + 1 : 0;
            if (steps + 1 >= MAX_SCROLL_STEPS || nextBottomStreak >= 2) {
                finishLazyLoadPass(token, pageToken);
                return;
            }
            int nextY = Math.min(maxY, y + Math.max(1, (int) (viewport * 0.8f)));
            if (nextY == y && nextBottomStreak > 0) {
                finishLazyLoadPass(token, pageToken);
                return;
            }
            handler.postDelayed(() -> scrollPageForCapture(token, pageToken, nextY, steps + 1, nextBottomStreak), SCROLL_DELAY_MS);
        });
    }

    private void finishLazyLoadPass(int token, int pageToken) {
        if (!isCurrentCapture(token, pageToken)) {
            return;
        }
        webView.evaluateJavascript("window.scrollTo(0,0);void(0)", null);
        handler.postDelayed(() -> beginReadinessChecks(token, pageToken), 350L);
    }

    private void beginReadinessChecks(int token, int pageToken) {
        checkReadiness(token, pageToken, SystemClock.elapsedRealtime(), -1, 0);
    }

    private void checkReadiness(int token, int pageToken, long startedAt, int previousResources, int stableCount) {
        if (!isCurrentCapture(token, pageToken)) {
            return;
        }
        if (!jsSwitch.isChecked()) {
            dumpHtml(token, pageToken);
            return;
        }
        webView.evaluateJavascript(READINESS_SCRIPT, value -> {
            if (!isCurrentCapture(token, pageToken)) {
                return;
            }
            long metric = parseJsLong(value, -1L);
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            if (metric < 0) {
                if (elapsed >= READINESS_TIMEOUT_MS) {
                    dumpHtml(token, pageToken);
                } else {
                    handler.postDelayed(() -> checkReadiness(token, pageToken, startedAt, previousResources, stableCount), READINESS_INTERVAL_MS);
                }
                return;
            }
            boolean ready = metric >= 1000000000L;
            long rest = metric % 1000000000L;
            long pendingImages = rest / 1000000L;
            int resources = (int) (rest % 1000000L);
            int nextStableCount = resources == previousResources ? stableCount + 1 : 0;
            if ((ready && pendingImages == 0 && nextStableCount >= 2) || elapsed >= READINESS_TIMEOUT_MS) {
                dumpHtml(token, pageToken);
            } else {
                handler.postDelayed(() -> checkReadiness(token, pageToken, startedAt, resources, nextStableCount), READINESS_INTERVAL_MS);
            }
        });
    }

    private void dumpHtml(final int token, final int pageToken) {
        if (!isCurrentCapture(token, pageToken) || webView == null) {
            return;
        }
        final String finalUrl = webView.getUrl() == null ? requestedUrl : webView.getUrl();
        webView.evaluateJavascript(DUMP_SCRIPT, value -> {
            if (!isCurrentCapture(token, pageToken)) {
                return;
            }
            String html = null;
            try {
                Object parsed = new JSONTokener(value == null ? "null" : value).nextValue();
                if (parsed instanceof String) {
                    html = (String) parsed;
                }
            } catch (Exception ignored) {
            }
            if (html == null || html.isEmpty()) {
                failSaving("ページ内容を取得できませんでした");
                return;
            }
            final String content = html;
            final String siteName = siteNameForUrl(finalUrl);
            executor.execute(() -> persistHtml(content, finalUrl, siteName, token));
        });
    }

    private boolean isCurrentSave(int token) {
        return isSaving && token == saveGeneration;
    }

    private boolean isCurrentCapture(int token, int pageToken) {
        return isSaving && token == saveGeneration && pageToken == pageGeneration && webView != null;
    }

    private int parseJsInteger(String value, int fallback) {
        long parsed = parseJsLong(value, fallback);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            return fallback;
        }
        return (int) parsed;
    }

    private long parseJsLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return (long) Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String siteNameForUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) {
                return safeFileName(host, "page");
            }
        } catch (Exception ignored) {
        }
        return "page";
    }

    private String safeFileName(String input, String fallback) {
        String value = input == null ? "" : input.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", "_");
        value = value.replaceAll("^[. _]+|[. _]+$", "");
        if (value.isEmpty()) {
            value = fallback;
        }
        if (value.length() > 80) {
            value = value.substring(0, 80);
        }
        return value;
    }

    private File getOutputDirectory(String siteName, String pageTitle) {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) {
            base = new File(getFilesDir(), "documents");
        }
        File root = new File(base, "page");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
        String folderName = safeFileName(siteName, "page") + "(" + safeFileName(pageTitle, "untitled") + ")_" + timestamp;
        File output = new File(root, folderName);
        if (!output.exists() && !output.mkdirs()) {
            Log.e(TAG, "フォルダー作成失敗: " + output.getAbsolutePath());
            return null;
        }
        if (!output.isDirectory()) {
            return null;
        }
        return output;
    }

    private void saveDataUrl(String dataUrl, int token) {
        File output = null;
        try {
            if (!isCurrentSave(token)) {
                return;
            }
            int comma = dataUrl.indexOf(',');
            if (comma < 0) {
                throw new IOException("data URLの形式が不正です");
            }
            String metadata = dataUrl.substring(5, comma);
            String payload = dataUrl.substring(comma + 1);
            String[] metadataParts = metadata.split(";");
            String mimeType = metadataParts.length > 0 && metadataParts[0].contains("/")
                    ? metadataParts[0].toLowerCase(Locale.ROOT)
                    : "text/plain";
            boolean base64 = false;
            for (String part : metadataParts) {
                if ("base64".equalsIgnoreCase(part.trim())) {
                    base64 = true;
                    break;
                }
            }
            File outputDir = getOutputDirectory("data_url", mimeType);
            if (outputDir == null) {
                throw new IOException("保存先フォルダーを作成できませんでした");
            }
            output = new File(outputDir, "data_content" + extensionForMimeType(mimeType));
            if (base64) {
                try (InputStream input = new Base64InputStream(new StringAsciiInputStream(payload), Base64.DEFAULT);
                     FileOutputStream stream = new FileOutputStream(output)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (!isCurrentSave(token)) {
                            throw new IOException("保存処理は終了しました");
                        }
                        stream.write(buffer, 0, count);
                    }
                    stream.flush();
                }
            } else {
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    writePercentDecodedPayload(payload, stream, token);
                    stream.flush();
                }
            }
            if (isCurrentSave(token)) {
                finishSaving("Data URL保存完了:\n" + output.getAbsolutePath());
            }
        } catch (Exception e) {
            if (!isCurrentSave(token)) {
                if (output != null && output.isFile()) {
                    output.delete();
                }
                return;
            }
            Log.e(TAG, "Data URL保存エラー", e);
            failSaving("Data URL保存エラー: " + e.getMessage());
        }
    }

    private void writePercentDecodedPayload(String payload, FileOutputStream output, int token) throws IOException {
        int index = 0;
        while (index < payload.length()) {
            if ((index & 0x3fff) == 0 && !isCurrentSave(token)) {
                throw new IOException("保存処理は終了しました");
            }
            char current = payload.charAt(index);
            if (current == '%' && index + 2 < payload.length()) {
                int high = Character.digit(payload.charAt(index + 1), 16);
                int low = Character.digit(payload.charAt(index + 2), 16);
                if (high >= 0 && low >= 0) {
                    output.write((high << 4) | low);
                    index += 3;
                    continue;
                }
            }
            int codePoint = payload.codePointAt(index);
            if (codePoint <= 0x7f) {
                output.write(codePoint);
            } else if (codePoint <= 0x7ff) {
                output.write(0xc0 | (codePoint >> 6));
                output.write(0x80 | (codePoint & 0x3f));
            } else if (codePoint <= 0xffff) {
                output.write(0xe0 | (codePoint >> 12));
                output.write(0x80 | ((codePoint >> 6) & 0x3f));
                output.write(0x80 | (codePoint & 0x3f));
            } else {
                output.write(0xf0 | (codePoint >> 18));
                output.write(0x80 | ((codePoint >> 12) & 0x3f));
                output.write(0x80 | ((codePoint >> 6) & 0x3f));
                output.write(0x80 | (codePoint & 0x3f));
            }
            index += Character.charCount(codePoint);
        }
    }

    private static final class StringAsciiInputStream extends InputStream {
        private final String source;
        private int position;

        StringAsciiInputStream(String source) {
            this.source = source;
        }

        @Override
        public int read() {
            if (position >= source.length()) {
                return -1;
            }
            return source.charAt(position++) & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position >= source.length()) {
                return -1;
            }
            int count = Math.min(length, source.length() - position);
            for (int i = 0; i < count; i++) {
                buffer[offset + i] = (byte) source.charAt(position++);
            }
            return count;
        }
    }

    private String extensionForMimeType(String mimeType) {
        if (mimeType == null) {
            return ".bin";
        }
        String type = mimeType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        switch (type) {
            case "text/html": return ".html";
            case "application/xhtml+xml": return ".xhtml";
            case "text/plain": return ".txt";
            case "text/css": return ".css";
            case "text/javascript":
            case "application/javascript": return ".js";
            case "application/json": return ".json";
            case "application/xml":
            case "text/xml": return ".xml";
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/svg+xml": return ".svg";
            case "application/pdf": return ".pdf";
            case "application/zip": return ".zip";
            case "audio/mpeg": return ".mp3";
            case "video/mp4": return ".mp4";
            default:
                int slash = type.indexOf('/');
                if (slash >= 0 && slash + 1 < type.length()) {
                    String subtype = type.substring(slash + 1).replaceAll("[^a-z0-9.+-]", "");
                    if (!subtype.isEmpty() && subtype.length() <= 12) {
                        return "." + subtype;
                    }
                }
                return ".bin";
        }
    }

    private void failSaving(String message) {
        finishSaving(message);
    }

    private void finishSaving(String message) {
        runOnUiThread(() -> {
            if (saveTimeoutRunnable != null) {
                handler.removeCallbacks(saveTimeoutRunnable);
                saveTimeoutRunnable = null;
            }
            isSaving = false;
            saveGeneration++;
            saveButton.setEnabled(webView != null);
            saveButton.setText("HTML保存開始");
            jsSwitch.setEnabled(true);
            localPathSwitch.setEnabled(true);
            Toast.makeText(pagedl.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void invalidateCurrentCapture() {
        if (saveTimeoutRunnable != null) {
            handler.removeCallbacks(saveTimeoutRunnable);
            saveTimeoutRunnable = null;
        }
        isSaving = false;
        saveGeneration++;
    }

    private void persistHtml(String htmlContent, String urlString, String siteName, int token) {
        try {
            if (!isCurrentSave(token)) {
                return;
            }
            String pageTitle = extractTitle(htmlContent);
            File outputDir = getOutputDirectory(siteName, pageTitle);
            if (outputDir == null) {
                failSaving("保存先フォルダーを作成できませんでした");
                return;
            }
            String content = htmlContent;
            if (localPathSwitch.isChecked()) {
                content = rewriteHtmlLocalPaths(content, urlString, outputDir);
            }
            File htmlFile = new File(outputDir, "page.html");
            Utils.writeStringToFile(htmlFile, content);
            if (htmlFile.exists()) {
                runOnUiThread(this::clearCacheAndCookies);
                finishSaving("HTML 保存完了：\n" + htmlFile.getAbsolutePath());
            } else {
                failSaving("HTML ファイルが存在しません");
            }
        } catch (Exception e) {
            Log.e(TAG, "HTML保存エラー", e);
            failSaving("エラー：" + e.getMessage());
        }
    }

    private void saveWithoutJavaScript(String urlString, String siteName, int token) {
        saveWithoutJavaScript(urlString, siteName, token, null, null, false);
    }

    private void saveWithoutJavaScript(
            String urlString,
            String siteName,
            int token,
            String username,
            String password,
            boolean authRetry) {
        HttpURLConnection conn = null;
        boolean waitingForAuth = false;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", ACCEPT_HEADER);
            conn.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
            String cookies = CookieBridge.getCookieHeader(urlString);
            if (cookies != null && !cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }
            String authorization = null;
            if (username != null && password != null) {
                authorization = BasicAuthManager.buildAuthorizationHeader(username, password);
            } else {
                authorization = BasicAuthManager.getAuthorizationHeaderForUrl(urlString, basicAuthEnabled);
            }
            if (authorization != null && !authorization.isEmpty()) {
                conn.setRequestProperty("Authorization", authorization);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                    && basicAuthEnabled && !authRetry) {
                String realm = extractBasicAuthRealm(conn.getHeaderField("WWW-Authenticate"));
                if (conn != null) {
                    conn.disconnect();
                    conn = null;
                }
                BasicAuthManager.markAuthenticationFailure(pagedl.this, webView, url.getHost(), realm);
                waitingForAuth = true;
                final String retryRealm = realm;
                runOnUiThread(() -> BasicAuthManager.requestCredentials(
                        pagedl.this,
                        webView,
                        url.getHost(),
                        retryRealm,
                        (retryUsername, retryPassword) -> {
                            if (retryUsername == null || retryPassword == null) {
                                failSaving("Basic認証をキャンセルしました");
                                return;
                            }
                            executor.execute(() -> saveWithoutJavaScript(
                                    urlString,
                                    siteName,
                                    token,
                                    retryUsername,
                                    retryPassword,
                                    true));
                        }));
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error code: " + responseCode);
                waitingForAuth = false;
                failSaving("HTTP error code: " + responseCode);
                return;
            }
            StringBuilder html = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append("\n");
                }
            }
            String htmlContent = html.toString();
            waitingForAuth = true;
            persistHtml(htmlContent, urlString, siteName, token);
        } catch (Exception e) {
            Log.e(TAG, "HTML保存エラー", e);
            waitingForAuth = true;
            failSaving("エラー：" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (!waitingForAuth) {
                isSaving = false;
            }
        }
    }

    private String extractBasicAuthRealm(String header) {
        if (header == null || header.isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?i)Basic\\s+realm\\s*=\\s*\\\"([^\\\"]*)\\\"").matcher(header);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private void clearCacheAndCookies() {
        webView.clearCache(true);
    }

    @Override
    public void onBackPressed() {
        if (isSaving) {
            Toast.makeText(this, "保存中です。完了またはタイムアウト後に戻ってください。", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private String rewriteHtmlLocalPaths(String html, String baseUrl, File dir) {
        Pattern pattern = Pattern.compile("(?i)(src|href)\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String attr = matcher.group(1);
            String originalUrl = matcher.group(2);
            try {
                URL resourceUrl = originalUrl.startsWith("http://") || originalUrl.startsWith("https://")
                        ? new URL(originalUrl)
                        : new URL(new URL(baseUrl), originalUrl);
                String fileName = new File(resourceUrl.getPath()).getName();
                if (fileName.isEmpty()) {
                    fileName = "index.html";
                }
                File resourceFile = new File(dir, fileName);
                downloadResource(resourceUrl.toString(), resourceFile);
                String localPath = "file://" + resourceFile.getAbsolutePath();
                matcher.appendReplacement(sb, attr + "=\"" + localPath + "\"");
            } catch (Exception e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void downloadResource(String resourceUrl, File destination) throws IOException {
        if (destination.exists()) {
            return;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(resourceUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            String cookies = CookieBridge.getCookieHeader(resourceUrl);
            if (cookies != null && !cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }
            String authorization = BasicAuthManager.getAuthorizationHeaderForUrl(resourceUrl, basicAuthEnabled);
            if (authorization != null && !authorization.isEmpty()) {
                conn.setRequestProperty("Authorization", authorization);
            }
            conn.setRequestProperty("User-Agent", USER_AGENT);
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String extractTitle(String html) {
        Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "untitled";
    }

    @Override
    protected void onDestroy() {
        invalidateCurrentCapture();
        BasicAuthManager.cancelPendingForActivity(pagedl.this);
        handler.removeCallbacksAndMessages(null);
        BrowserView current = webView;
        if (current != null) {
            View parentView = current.getParent() instanceof View ? (View) current.getParent() : null;
            if (parentView instanceof ViewGroup) {
                ((ViewGroup) parentView).removeView(current);
            }
            try {
                current.stopLoading();
                current.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public android.app.Activity hostActivity() {
        return this;
    }

    @Override
    public void chooseFiles(String[] mimeTypes, boolean multiple, boolean capture, ValueCallback<android.net.Uri[]> callback) {
        callback.onReceiveValue(null);
    }

    @Override
    public void requestAndroidPermissions(String[] permissions, ValueCallback<Boolean> callback) {
        callback.onReceiveValue(false);
    }

    @Override
    public void requestAuth(BrowserView view, String host, String realm, boolean previousFailed, String presetUser,
                            String presetPassword, BrowserHost.AuthCallback callback) {
        if (!basicAuthEnabled) {
            callback.cancel();
            return;
        }
        BasicAuthManager.requestCredentials(this, view, host, realm, (user, password) -> {
            if (user == null) {
                callback.cancel();
            } else {
                callback.proceed(user, password == null ? "" : password);
            }
        });
    }
}

class Utils {
    public static String readFileToString(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int readBytes = fis.read(data);
            if (readBytes != data.length) {
                throw new IOException("ファイル全体の読み込みに失敗しました");
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeStringToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
