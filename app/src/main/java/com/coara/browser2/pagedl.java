package com.coara.browser2;

import android.content.Intent;
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
import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserHost;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.CookieBridge;

import org.json.JSONArray;
import org.json.JSONObject;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String DUMP_SCRIPT = "(function(){var out=[],seen={};function add(u){try{if(!u)return;var a=new URL(u,document.baseURI).href;var h=a.indexOf('#');if(h>=0)a=a.slice(0,h);if(!/^https?:/i.test(a)||seen[a])return;seen[a]=1;out.push(a);}catch(e){}}var q=document.querySelectorAll('img,source,video,audio,script[src],link[href],input[type=image],embed,iframe[src],frame[src],object[data]');for(var i=0;i<q.length&&out.length<1200;i++){var e=q[i];if(e.tagName==='LINK'){var r=(e.getAttribute('rel')||'').toLowerCase();if(r.indexOf('stylesheet')<0&&r.indexOf('icon')<0&&r.indexOf('preload')<0)continue;add(e.getAttribute('href'));continue;}add(e.currentSrc||e.getAttribute('src'));add(e.getAttribute('data-src'));add(e.getAttribute('poster'));if(e.tagName==='OBJECT')add(e.getAttribute('data'));var ss=e.getAttribute('srcset');if(ss){var p=ss.split(',');for(var k=0;k<p.length;k++){add(p[k].trim().split(/\\s+/)[0]);}}}try{var en=performance.getEntriesByType('resource');for(var j=0;j<en.length&&out.length<1200;j++){var t=en[j].initiatorType;if(t==='xmlhttprequest'||t==='fetch'||t==='beacon'||t==='other')continue;add(en[j].name);}}catch(e){}var d=document.doctype;var dt=d?'<!DOCTYPE '+d.name+'>\\n':'';return JSON.stringify({u:location.href,t:document.title,h:dt+document.documentElement.outerHTML,r:out});})()";
    private static final Pattern BASE_HREF = Pattern.compile("(?i)<base\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern RESOURCE_ATTR = Pattern.compile("(?i)\\b(src|href|poster|data-src)\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SRCSET_ATTR = Pattern.compile("(?i)\\bsrcset\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern CSS_URL = Pattern.compile("(?i)url\\(\\s*(?:\"([^\"]+)\"|'([^']+)'|([^)\\s\"']+))\\s*\\)");
    private static final Pattern CSS_IMPORT = Pattern.compile("(?i)@import\\s+[\"']([^\"']+)[\"']");
    private static final int MAX_CSS_DEPTH = 3;
    private static final int MAX_CSS_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 8;
    private static final long MAX_HTML_BYTES = 60L * 1024L * 1024L;
    private static final int MAX_RESOURCES = 600;
    private static final int FETCH_THREADS = 8;
    private static final long MAX_RESOURCE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 120L * 1024L * 1024L;

    private EditText urlInput;
    private Switch jsSwitch;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (BrowserEngine.isInitialized()) {
            BrowserEngine.get().handleWebAuthnActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pgdl);
        basicAuthEnabled = getIntent().getBooleanExtra(BasicAuthManager.EXTRA_BASIC_AUTH_ENABLED, false);

        urlInput = findViewById(R.id.urlInput);
        jsSwitch = findViewById(R.id.jsSwitch);
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
        final String fallbackUrl = webView.getUrl() == null ? requestedUrl : webView.getUrl();
        webView.evaluateJavascript(DUMP_SCRIPT, value -> {
            if (!isCurrentCapture(token, pageToken)) {
                return;
            }
            String html = null;
            String pageUrl = fallbackUrl;
            String title = "";
            ArrayList<String> resources = new ArrayList<>();
            try {
                Object parsed = new JSONTokener(value == null ? "null" : value).nextValue();
                if (parsed instanceof String) {
                    JSONObject object = new JSONObject((String) parsed);
                    html = object.optString("h", null);
                    String u = object.optString("u", "");
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        pageUrl = u;
                    }
                    title = object.optString("t", "");
                    JSONArray list = object.optJSONArray("r");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            String item = list.optString(i, "");
                            if (!item.isEmpty()) {
                                resources.add(item);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            if (html == null || html.isEmpty()) {
                failSaving("ページ内容を取得できませんでした");
                return;
            }
            final String content = html;
            final String finalUrl = pageUrl;
            final String finalTitle = title;
            final String siteName = siteNameForUrl(finalUrl);
            executor.execute(() -> persistMht(content, finalUrl, finalTitle, siteName, resources, token));
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
            saveButton.setText("MHT保存開始");
            jsSwitch.setEnabled(true);
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

    private void persistMht(String html, String pageUrl, String title, String siteName,
                            List<String> resources, int token) {
        File tempDir = null;
        try {
            if (!isCurrentSave(token)) {
                return;
            }
            String pageTitle = title == null || title.trim().isEmpty() ? extractTitle(html) : title.trim();
            File outputDir = getOutputDirectory(siteName, pageTitle);
            if (outputDir == null) {
                failSaving("保存先フォルダーを作成できませんでした");
                return;
            }
            tempDir = new File(getCacheDir(), "pagedl_" + token + "_" + SystemClock.elapsedRealtime());
            if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
                failSaving("作業フォルダーを作成できませんでした");
                return;
            }
            List<String> targets = resources;
            if (targets == null || targets.isEmpty()) {
                targets = extractResourceUrls(html, pageUrl);
            }
            List<FetchedResource> fetched = fetchResources(targets, html, pageUrl, tempDir, token);
            if (!isCurrentSave(token)) {
                return;
            }
            File mhtFile = new File(outputDir, "page_archive.mht");
            writeMht(mhtFile, html, pageUrl, pageTitle, fetched, token);
            if (!isCurrentSave(token)) {
                mhtFile.delete();
                return;
            }
            if (mhtFile.isFile() && mhtFile.length() > 0L) {
                finishSaving("MHT保存完了(リソース " + fetched.size() + "件):\n" + mhtFile.getAbsolutePath());
            } else {
                failSaving("MHTファイルを作成できませんでした");
            }
        } catch (Exception e) {
            Log.e(TAG, "MHT保存エラー", e);
            if (isCurrentSave(token)) {
                failSaving("MHT保存エラー: " + e.getMessage());
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static final class FetchedResource {
        final String url;
        final String contentType;
        final File file;

        FetchedResource(String url, String contentType, File file) {
            this.url = url;
            this.contentType = contentType;
            this.file = file;
        }
    }

    private List<String> extractResourceUrls(String html, String pageUrl) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        URL base;
        try {
            base = new URL(pageUrl);
        } catch (Exception e) {
            return new ArrayList<>();
        }
        Matcher baseMatcher = BASE_HREF.matcher(html);
        if (baseMatcher.find()) {
            try {
                base = new URL(base, baseMatcher.group(1));
            } catch (Exception ignored) {
            }
        }
        Matcher m = RESOURCE_ATTR.matcher(html);
        while (m.find() && out.size() < MAX_RESOURCES) {
            addResolved(out, base, m.group(2));
        }
        Matcher srcset = SRCSET_ATTR.matcher(html);
        while (srcset.find() && out.size() < MAX_RESOURCES) {
            for (String part : srcset.group(1).split(",")) {
                String candidate = part.trim();
                int space = candidate.indexOf(' ');
                addResolved(out, base, space > 0 ? candidate.substring(0, space) : candidate);
            }
        }
        out.remove(pageUrl);
        return new ArrayList<>(out);
    }

    private static void addResolved(Set<String> out, URL base, String raw) {
        if (raw == null) {
            return;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.startsWith("#") || value.regionMatches(true, 0, "data:", 0, 5)
                || value.regionMatches(true, 0, "javascript:", 0, 11)) {
            return;
        }
        try {
            URL resolved = new URL(base, value);
            String protocol = resolved.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return;
            }
            String text = resolved.toString();
            int hash = text.indexOf('#');
            out.add(hash >= 0 ? text.substring(0, hash) : text);
        } catch (Exception ignored) {
        }
    }

    private static boolean isPrivateHost(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            return true;
        }
        if (h.startsWith("[")) {
            return h.equals("[::1]") || h.startsWith("[fe80") || h.startsWith("[fc") || h.startsWith("[fd");
        }
        String[] parts = h.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            return a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isCss(String contentType, String url) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/css")) {
            return true;
        }
        if (url == null) {
            return false;
        }
        int q = url.indexOf('?');
        String path = (q >= 0 ? url.substring(0, q) : url).toLowerCase(Locale.ROOT);
        return path.endsWith(".css");
    }

    private static void collectCssUrls(String css, String baseUrl, Set<String> out, int limit) {
        URL base;
        try {
            base = new URL(baseUrl);
        } catch (Exception e) {
            return;
        }
        Matcher m = CSS_URL.matcher(css);
        while (m.find() && out.size() < limit) {
            String raw = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            addResolved(out, base, raw);
        }
        Matcher imports = CSS_IMPORT.matcher(css);
        while (imports.find() && out.size() < limit) {
            addResolved(out, base, imports.group(1));
        }
    }

    private static String readText(File file, int maxBytes) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), maxBytes)];
            int filled = 0;
            while (filled < buffer.length) {
                int read = in.read(buffer, filled, buffer.length - filled);
                if (read < 0) {
                    break;
                }
                filled += read;
            }
            return new String(buffer, 0, filled, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private List<FetchedResource> fetchResources(List<String> seeds, String html, String pageUrl, File tempDir,
                                                 final int token) {
        final ArrayList<FetchedResource> result = new ArrayList<>();
        String pageHost = "";
        try {
            pageHost = new URL(pageUrl).getHost();
        } catch (Exception ignored) {
        }
        final boolean pagePrivate = isPrivateHost(pageHost);
        final ConcurrentHashMap<String, String> cookieCache = new ConcurrentHashMap<>();
        final AtomicLong totalBytes = new AtomicLong();
        final AtomicInteger counter = new AtomicInteger();
        final LinkedHashSet<String> seen = new LinkedHashSet<>();
        seen.add(pageUrl);
        LinkedHashSet<String> wave = new LinkedHashSet<>();
        if (seeds != null) {
            for (String u : seeds) {
                if (seen.add(u)) {
                    wave.add(u);
                }
            }
        }
        LinkedHashSet<String> inline = new LinkedHashSet<>();
        collectCssUrls(html, pageUrl, inline, MAX_RESOURCES);
        for (String u : inline) {
            if (seen.add(u)) {
                wave.add(u);
            }
        }
        final String referer = pageUrl;
        for (int depth = 0; depth <= MAX_CSS_DEPTH && !wave.isEmpty(); depth++) {
            if (!isCurrentSave(token)) {
                break;
            }
            final ExecutorService pool = Executors.newFixedThreadPool(FETCH_THREADS);
            final ArrayList<Future<FetchedResource>> futures = new ArrayList<>();
            for (String u : wave) {
                if (counter.get() >= MAX_RESOURCES) {
                    break;
                }
                final String resourceUrl = u;
                try {
                    if (!pagePrivate && isPrivateHost(new URL(resourceUrl).getHost())) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
                final File target = new File(tempDir, "r" + counter.getAndIncrement());
                futures.add(pool.submit(new Callable<FetchedResource>() {
                    @Override
                    public FetchedResource call() {
                        return fetchOne(resourceUrl, referer, target, cookieCache, totalBytes, token);
                    }
                }));
            }
            pool.shutdown();
            LinkedHashSet<String> next = new LinkedHashSet<>();
            for (Future<FetchedResource> f : futures) {
                try {
                    FetchedResource r = f.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (r != null) {
                        result.add(r);
                        if (isCss(r.contentType, r.url) && r.file.length() <= MAX_CSS_BYTES) {
                            LinkedHashSet<String> found = new LinkedHashSet<>();
                            collectCssUrls(readText(r.file, MAX_CSS_BYTES), r.url, found, MAX_RESOURCES);
                            for (String u : found) {
                                if (seen.size() < MAX_RESOURCES * 2 && seen.add(u)) {
                                    next.add(u);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    f.cancel(true);
                }
                if (!isCurrentSave(token)) {
                    pool.shutdownNow();
                    break;
                }
            }
            wave = next;
        }
        return result;
    }

    private String cookieFor(String url, ConcurrentHashMap<String, String> cookieCache) {
        try {
            URL u = new URL(url);
            String origin = u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
            String cached = cookieCache.get(origin);
            if (cached == null) {
                String fresh = CookieBridge.getCookieHeader(url);
                cached = fresh == null ? "" : fresh;
                cookieCache.put(origin, cached);
            }
            return cached;
        } catch (Exception e) {
            return "";
        }
    }

    private HttpURLConnection openFollowing(String startUrl, String referer, ConcurrentHashMap<String, String> cookieCache,
                                            boolean page, String authorizationOverride) throws IOException {
        String current = startUrl;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            URL url = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", page ? ACCEPT_HEADER : "*/*");
            conn.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
            if (referer != null && !referer.isEmpty()) {
                conn.setRequestProperty("Referer", referer);
            }
            String cookies = cookieFor(current, cookieCache);
            if (cookies != null && !cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }
            String authorization = authorizationOverride != null ? authorizationOverride
                    : BasicAuthManager.getAuthorizationHeaderForUrl(current, basicAuthEnabled);
            if (authorization != null && !authorization.isEmpty()) {
                conn.setRequestProperty("Authorization", authorization);
            }
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("リダイレクト先が不正です");
                }
                URL next = new URL(url, location);
                String protocol = next.getProtocol();
                if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                    throw new IOException("未対応のリダイレクト先です");
                }
                current = next.toString();
                continue;
            }
            return conn;
        }
        throw new IOException("リダイレクトが多すぎます");
    }

    private FetchedResource fetchOne(String resourceUrl, String referer, File target,
                                     ConcurrentHashMap<String, String> cookieCache, AtomicLong totalBytes, int token) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!isCurrentSave(token) || totalBytes.get() >= MAX_TOTAL_BYTES) {
                return null;
            }
            HttpURLConnection conn = null;
            try {
                conn = openFollowing(resourceUrl, referer, cookieCache, false, null);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    if (code >= 500 && attempt == 0) {
                        continue;
                    }
                    return null;
                }
                if (conn.getContentLengthLong() > MAX_RESOURCE_BYTES) {
                    return null;
                }
                long written = 0L;
                boolean complete;
                try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        written += count;
                        if (written > MAX_RESOURCE_BYTES || totalBytes.addAndGet(count) > MAX_TOTAL_BYTES
                                || !isCurrentSave(token)) {
                            written = -1L;
                            break;
                        }
                        out.write(buffer, 0, count);
                    }
                    complete = written >= 0L;
                }
                if (!complete) {
                    target.delete();
                    return null;
                }
                String type = conn.getContentType();
                return new FetchedResource(resourceUrl,
                        type == null || type.isEmpty() ? "application/octet-stream" : type, target);
            } catch (Exception e) {
                target.delete();
                if (attempt == 1) {
                    return null;
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null;
    }

    private void writeMht(File file, String html, String pageUrl, String title, List<FetchedResource> parts, int token)
            throws IOException {
        String boundary = "----=_Coara_" + Long.toHexString(System.nanoTime()) + "_MHT";
        String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(new Date());
        try (OutputStream raw = new FileOutputStream(file);
             java.io.BufferedOutputStream out = new java.io.BufferedOutputStream(raw, 65536)) {
            writeAscii(out, "From: <Saved by Coara Browser>\r\n");
            writeAscii(out, "Subject: =?utf-8?B?" + Base64.encodeToString(
                    (title == null || title.isEmpty() ? "untitled" : title).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP) + "?=\r\n");
            writeAscii(out, "Date: " + date + "\r\n");
            writeAscii(out, "MIME-Version: 1.0\r\n");
            writeAscii(out, "Content-Type: multipart/related;\r\n\ttype=\"text/html\";\r\n\tboundary=\"" + boundary + "\"\r\n\r\n");
            writeAscii(out, "--" + boundary + "\r\n");
            writeAscii(out, "Content-Type: text/html; charset=\"utf-8\"\r\n");
            writeAscii(out, "Content-Transfer-Encoding: base64\r\n");
            if (pageUrl != null && (pageUrl.startsWith("http://") || pageUrl.startsWith("https://"))) {
                writeAscii(out, "Content-Location: " + pageUrl + "\r\n");
            }
            writeAscii(out, "\r\n");
            writeBase64(out, html.getBytes(StandardCharsets.UTF_8));
            writeAscii(out, "\r\n");
            for (FetchedResource part : parts) {
                if (!isCurrentSave(token)) {
                    return;
                }
                writeAscii(out, "--" + boundary + "\r\n");
                writeAscii(out, "Content-Type: " + sanitizeHeader(part.contentType) + "\r\n");
                writeAscii(out, "Content-Transfer-Encoding: base64\r\n");
                writeAscii(out, "Content-Location: " + sanitizeHeader(part.url) + "\r\n\r\n");
                try (InputStream in = new FileInputStream(part.file)) {
                    writeBase64(out, in);
                }
                writeAscii(out, "\r\n");
            }
            writeAscii(out, "--" + boundary + "--\r\n");
            out.flush();
        }
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void writeAscii(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeBase64(OutputStream out, byte[] data) throws IOException {
        for (int offset = 0; offset < data.length; offset += 57) {
            int length = Math.min(57, data.length - offset);
            out.write(Base64.encode(data, offset, length, Base64.NO_WRAP));
            out.write('\r');
            out.write('\n');
        }
    }

    private static void writeBase64(OutputStream out, InputStream in) throws IOException {
        byte[] buffer = new byte[57 * 256];
        while (true) {
            int filled = 0;
            while (filled < buffer.length) {
                int read = in.read(buffer, filled, buffer.length - filled);
                if (read < 0) {
                    break;
                }
                filled += read;
            }
            if (filled == 0) {
                return;
            }
            for (int offset = 0; offset < filled; offset += 57) {
                int length = Math.min(57, filled - offset);
                out.write(Base64.encode(buffer, offset, length, Base64.NO_WRAP));
                out.write('\r');
                out.write('\n');
            }
            if (filled < buffer.length) {
                return;
            }
        }
    }

    private void saveWithoutJavaScript(String urlString, String siteName, int token) {
        saveWithoutJavaScript(urlString, siteName, token, null, false);
    }

    private void saveWithoutJavaScript(String urlString, String siteName, int token, String authorization,
                                       boolean authRetry) {
        HttpURLConnection conn = null;
        boolean deferred = false;
        try {
            ConcurrentHashMap<String, String> cookieCache = new ConcurrentHashMap<>();
            conn = openFollowing(urlString, null, cookieCache, true, authorization);
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && basicAuthEnabled && !authRetry) {
                final String realm = extractBasicAuthRealm(conn.getHeaderField("WWW-Authenticate"));
                final String host = conn.getURL().getHost();
                conn.disconnect();
                conn = null;
                deferred = true;
                BasicAuthManager.markAuthenticationFailure(pagedl.this, webView, host, realm);
                runOnUiThread(() -> BasicAuthManager.requestCredentials(pagedl.this, webView, host, realm,
                        (retryUsername, retryPassword) -> {
                            if (retryUsername == null || retryPassword == null) {
                                failSaving("Basic認証をキャンセルしました");
                                return;
                            }
                            final String header = BasicAuthManager.buildAuthorizationHeader(retryUsername, retryPassword);
                            executor.execute(() -> saveWithoutJavaScript(urlString, siteName, token, header, true));
                        }));
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                failSaving("HTTP error code: " + responseCode);
                return;
            }
            String contentType = conn.getContentType();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(
                    (int) Math.max(8192L, Math.min(conn.getContentLengthLong(), 8L * 1024L * 1024L)));
            try (InputStream in = conn.getInputStream()) {
                byte[] chunk = new byte[16384];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    if (!isCurrentSave(token)) {
                        return;
                    }
                    buffer.write(chunk, 0, count);
                    if (buffer.size() > MAX_HTML_BYTES) {
                        failSaving("HTMLが大きすぎます");
                        return;
                    }
                }
            }
            byte[] bytes = buffer.toByteArray();
            String title = extractTitle(new String(bytes, 0, Math.min(bytes.length, 262144), charsetOf(contentType)));
            File outputDir = getOutputDirectory(siteName, title);
            if (outputDir == null) {
                failSaving("保存先フォルダーを作成できませんでした");
                return;
            }
            File htmlFile = new File(outputDir, "page.html");
            try (OutputStream out = new FileOutputStream(htmlFile)) {
                out.write(bytes);
                out.flush();
            }
            if (isCurrentSave(token)) {
                finishSaving("HTML保存完了:\n" + htmlFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "HTML保存エラー", e);
            if (isCurrentSave(token)) {
                failSaving("エラー: " + e.getMessage());
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static java.nio.charset.Charset charsetOf(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("(?i)charset\\s*=\\s*\"?([A-Za-z0-9_.:-]+)").matcher(contentType);
            if (m.find()) {
                try {
                    return java.nio.charset.Charset.forName(m.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        return StandardCharsets.UTF_8;
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

    @Override
    public void onBackPressed() {
        if (isSaving) {
            Toast.makeText(this, "保存中です。完了またはタイムアウト後に戻ってください。", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
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
    public void requestStorageAccess(BrowserView view, String topUrlHint, String frameOrigin,
                                     ValueCallback<Boolean> callback) {
        callback.onReceiveValue(false);
    }

    @Override
    public void onThirdPartyCookieBlocked(BrowserView view, String blockedUri) {
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
