package com.coara.browser2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.coara.browser2.gecko.BrowserClient;
import com.coara.browser2.gecko.BrowserHost;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.util.BrowserAssetLoader;
import com.coara.browser2.util.BrowserConstants;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class exec extends Activity implements BrowserHost {

    private BrowserView webView;
    private Process currentProcess;
    private ScheduledExecutorService timeoutExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exec);
        webView = new BrowserView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.webview_host)).addView(webView);
        webView.setBridgeListener(new BrowserView.BridgeListener() {
            @Override
            public void onBridgeCall(BrowserView view, String function, JSONArray args) {
                if ("executeCommand".equals(function)) {
                    executeCommand(args.optString(0, ""));
                } else if ("stopCommand".equals(function)) {
                    stopCommand();
                }
            }
        });
        webView.setClient(new BrowserClient() {
            @Override
            public boolean shouldOverrideUrlLoading(BrowserView view, String url, boolean mainFrame,
                                                    boolean hasGesture, boolean redirect) {
                if (!mainFrame || url == null) {
                    return false;
                }
                if (BrowserAssetLoader.isAppAssetUrl(url) || url.startsWith("about:") || url.startsWith("data:")) {
                    return false;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }
        });
        webView.loadUrl(BrowserConstants.APP_ASSET_EXEC_PAGE);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.destroy();
            } catch (Exception ignored) {
            }
        }
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "showToast('コマンドを入力してください。')", null));
            return;
        }
        executeCommandInternal(command);
    }

    private void stopCommand() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            runOnUiThread(() -> webView.evaluateJavascript(
                    "appendOutput('INFO: コマンドが強制終了されました\\n')", null));
        } else {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "showToast('実行中のプロセスはありません。')", null));
        }
    }

    @Override
    public Activity hostActivity() {
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
        callback.cancel();
    }

    private void executeCommandInternal(String command) {
        runOnUiThread(() -> webView.evaluateJavascript("clearOutput()", null));
        try {
            currentProcess = Runtime.getRuntime().exec(command);
            StringBuilder outputBuilder = new StringBuilder();

            final Process process = currentProcess;
            timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
            timeoutExecutor.schedule(() -> {
                if (process.isAlive()) {
                    process.destroy();
                    runOnUiThread(() -> webView.evaluateJavascript(
                            "appendOutput('INFO: タイムアウトにより強制終了されました\\n')", null));
                }
            }, 30, TimeUnit.SECONDS);
            timeoutExecutor.shutdown();

            ExecutorService outputExecutor = Executors.newSingleThreadExecutor();
            outputExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                        final String finalLine = line;
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "appendOutput('" + escapeForJS(finalLine) + "\\n')", null));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        outputBuilder.append("ERROR: ").append(line).append("\n");
                        final String finalLine = line;
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "appendOutput('ERROR: " + escapeForJS(finalLine) + "\\n')", null));
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> webView.evaluateJavascript(
                            "appendOutput('ERROR: " + escapeForJS(e.getMessage()) + "\\n')", null));
                }
            });
            outputExecutor.shutdown();
        } catch (IOException e) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "appendOutput('ERROR: " + escapeForJS(e.getMessage()) + "')", null));
        }
    }

    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "");
    }
}
