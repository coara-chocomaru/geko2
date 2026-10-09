package com.coara.browser2;

import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.coara.browser2.gecko.BrowserClient;
import com.coara.browser2.gecko.BrowserHost;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.util.CookieBridge;
import org.json.JSONTokener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.coara.browser2.util.BasicAuthManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Base64;
import android.view.View;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
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
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile; rv:89.0) Gecko/89.0 Firefox/89.0";
    private static final String ACCEPT_HEADER = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7";

    private EditText urlInput;
    private Switch jsSwitch;
    private Switch localPathSwitch;
    private Button saveButton;
    private BrowserView webView;
    private volatile boolean isSaving = false;
    private boolean basicAuthEnabled;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        webView.getSettings().setJavaScriptEnabled(jsSwitch.isChecked());
        jsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                webView.getSettings().setJavaScriptEnabled(isChecked));

        saveButton.setOnClickListener(v -> handleSaveButtonClick());
    }

    private void handleSaveButtonClick() {
        final String urlString = urlInput.getText().toString().trim();
        if (urlString.isEmpty()) {
            Toast.makeText(this, "URLを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        final String siteName = urlString.replaceAll("[^a-zA-Z0-9]", "_");
        isSaving = true;
        if (urlString.startsWith("blob:")) {
            Toast.makeText(this, "blob: URLはサポートされていません", Toast.LENGTH_LONG).show();
            isSaving = false;
            return;
        }
        if (urlString.startsWith("data:")) {
            executor.execute(() -> saveDataUrl(urlString, siteName));
            return;
        }
        if (jsSwitch.isChecked()) {
            saveWithJavaScriptEnabled(urlString, siteName);
        } else {
            executor.execute(() -> saveWithoutJavaScript(urlString, siteName));
        }
    }

    private void saveWithJavaScriptEnabled(final String urlString, final String siteName) {
        webView.setClient(new BrowserClient() {
            private boolean handled = false;

            @Override
            public void onPageStarted(BrowserView view, String url) {
                handled = false;
            }

            @Override
            public void onLoadFailed(BrowserView view, LoadFailure failure) {
                if (!handled) {
                    handled = true;
                    runOnUiThread(() -> Toast.makeText(pagedl.this, "ページを読み込めませんでした", Toast.LENGTH_LONG).show());
                    isSaving = false;
                }
            }

            @Override
            public void onPageFinished(BrowserView view, String url) {
                if (handled) {
                    return;
                }
                handled = true;
                handler.postDelayed(() -> view.evaluateJavascript(
                        "(function(){var d=document.doctype;var t=d?'<!DOCTYPE '+d.name+'>\\n':'';return t+document.documentElement.outerHTML;})()",
                        value -> {
                            String html = null;
                            try {
                                Object parsed = new JSONTokener(value == null ? "null" : value).nextValue();
                                if (parsed instanceof String) {
                                    html = (String) parsed;
                                }
                            } catch (Exception ignored) {
                            }
                            if (html == null || html.isEmpty()) {
                                runOnUiThread(() -> Toast.makeText(pagedl.this, "ページ内容を取得できませんでした", Toast.LENGTH_LONG).show());
                                isSaving = false;
                                return;
                            }
                            final String content = html;
                            executor.execute(() -> persistHtml(content, urlString, siteName));
                        }), 3000);
            }
        });
        webView.loadUrl(urlString);
    }

    private void persistHtml(String htmlContent, String urlString, String siteName) {
        try {
            String pageTitle = extractTitle(htmlContent);
            String safePageTitle = pageTitle.replaceAll("[^a-zA-Z0-9]", "_");
            File outputDir = getOutputDirectory(siteName, safePageTitle);
            if (outputDir == null) {
                return;
            }
            String content = htmlContent;
            if (localPathSwitch.isChecked()) {
                content = rewriteHtmlLocalPaths(content, urlString, outputDir);
            }
            File htmlFile = new File(outputDir, "page.html");
            Utils.writeStringToFile(htmlFile, content);
            if (htmlFile.exists()) {
                runOnUiThread(() -> {
                    Toast.makeText(pagedl.this, "HTML 保存完了：\n" + htmlFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    clearCacheAndCookies();
                });
            } else {
                runOnUiThread(() ->
                        Toast.makeText(pagedl.this, "HTML ファイルが存在しません", Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "HTML保存エラー", e);
            runOnUiThread(() ->
                    Toast.makeText(pagedl.this, "エラー：" + e.getMessage(), Toast.LENGTH_LONG).show());
        } finally {
            isSaving = false;
        }
    }

    private void saveWithoutJavaScript(String urlString, String siteName) {
        saveWithoutJavaScript(urlString, siteName, null, null, false);
    }

    private void saveWithoutJavaScript(
            String urlString,
            String siteName,
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
                                isSaving = false;
                                Toast.makeText(pagedl.this, "Basic認証をキャンセルしました", Toast.LENGTH_LONG).show();
                                return;
                            }
                            executor.execute(() -> saveWithoutJavaScript(
                                    urlString,
                                    siteName,
                                    retryUsername,
                                    retryPassword,
                                    true));
                        }));
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error code: " + responseCode);
                runOnUiThread(() ->
                        Toast.makeText(pagedl.this, "HTTP error code: " + responseCode, Toast.LENGTH_LONG).show());
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
            String pageTitle = extractTitle(htmlContent);
            String safePageTitle = pageTitle.replaceAll("[^a-zA-Z0-9]", "_");
            File outputDir = getOutputDirectory(siteName, safePageTitle);
            if (outputDir == null) {
                return;
            }
            if (localPathSwitch.isChecked()) {
                htmlContent = rewriteHtmlLocalPaths(htmlContent, urlString, outputDir);
            }
            File htmlFile = new File(outputDir, "page.html");
            Utils.writeStringToFile(htmlFile, htmlContent);
            if (htmlFile.exists()) {
                runOnUiThread(() -> {
                    Toast.makeText(pagedl.this, "HTML 保存完了：\n" + htmlFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    clearCacheAndCookies();
                });
            } else {
                runOnUiThread(() ->
                        Toast.makeText(pagedl.this, "HTML ファイルが存在しません", Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "HTML保存エラー", e);
            runOnUiThread(() ->
                    Toast.makeText(pagedl.this, "エラー：" + e.getMessage(), Toast.LENGTH_LONG).show());
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
            Toast.makeText(this, "保存中はバックキーが無効です", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
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

    private String getExtensionForMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        if (mimeType.contains("html")) {
            return ".html";
        } else if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
            return ".jpg";
        } else if (mimeType.contains("png")) {
            return ".png";
        } else if (mimeType.contains("gif")) {
            return ".gif";
        } else if (mimeType.contains("pdf")) {
            return ".pdf";
        }
        return "";
    }

    private void saveDataUrl(String dataUrl, String siteName) {
        String datetime = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File baseDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "page");
        File outputDir = new File(baseDir, datetime);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            isSaving = false;
            return;
        }
        saveDataUrlContent(dataUrl, outputDir, datetime);
    }

    private void saveDataUrlContent(String dataUrl, File outputDir, String baseName) {
        try {
            int commaIndex = dataUrl.indexOf(",");
            if (commaIndex == -1) {
                throw new IOException("data URL の形式が不正です");
            }
            String header = dataUrl.substring(0, commaIndex);
            String base64Data = dataUrl.substring(commaIndex + 1);
            String mimeType = "application/octet-stream";
            Pattern pattern = Pattern.compile("data:([^;]+);base64");
            Matcher matcher = pattern.matcher(header);
            if (matcher.find()) {
                mimeType = matcher.group(1);
            }
            String extension = getExtensionForMimeType(mimeType);
            String fileName = baseName + extension;
            File outFile = new File(outputDir, fileName);
            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(data);
            }
            runOnUiThread(() -> {
                Toast.makeText(pagedl.this, "保存完了：\n" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                clearCacheAndCookies();
            });
        } catch (Exception e) {
            Log.e(TAG, "Data URL 保存エラー", e);
            runOnUiThread(() ->
                    Toast.makeText(pagedl.this, "Data URL 保存エラー：" + e.getMessage(), Toast.LENGTH_LONG).show());
        } finally {
            isSaving = false;
        }
    }

    private File getOutputDirectory(String siteName, String safePageTitle) {
        File baseDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "page");
        File outputDir = new File(baseDir, siteName + "(" + safePageTitle + ")/データ保存");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "フォルダ作成失敗: " + outputDir.getAbsolutePath());
            runOnUiThread(() ->
                    Toast.makeText(pagedl.this, "フォルダ作成失敗: " + outputDir.getAbsolutePath(), Toast.LENGTH_LONG).show());
            return null;
        }
        return outputDir;
    }

    @Override
    protected void onDestroy() {
        BasicAuthManager.cancelPendingForActivity(pagedl.this);
        if (webView != null) {
            try {
                webView.destroy();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
        executor.shutdownNow();
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

class MimeParser {
    public static String rewriteContentLocations(String mhtContent, File dir) {
        Pattern pattern = Pattern.compile("(?im)^(Content-Location:\\s*)(http[s]?://\\S+)$");
        Matcher matcher = pattern.matcher(mhtContent);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String originalUrl = matcher.group(2).trim();
            try {
                URL url = new URL(originalUrl);
                String fileName = new File(url.getPath()).getName();
                if (fileName.isEmpty()) {
                    fileName = "index.html";
                }
                String localPath = "file://" + dir.getAbsolutePath() + "/" + fileName;
                String replacement = matcher.group(1) + localPath;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } catch (Exception e) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
