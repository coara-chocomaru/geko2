package com.coara.browser2;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Base64;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import com.coara.browser2.gecko.ValueCallback;
import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.gecko.BrowserClient;
import com.coara.browser2.gecko.BrowserHost;
import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.BrowserSettings;
import com.coara.browser2.gecko.DownloadRequest;
import com.coara.browser2.gecko.NavHistory;
import com.coara.browser2.gecko.PageAgent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.coara.browser2.util.SafeBrowsingSupport;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.CookieSite;
import com.coara.browser2.util.DarkModeSwitcher;
import com.coara.browser2.util.CookieSitePolicy;
import com.coara.browser2.util.ExternalDownloadTabTracker;
import com.coara.browser2.util.DownloadSupport;
import com.coara.browser2.util.DownloadFallbackManager;
import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.AdBlockEngine;
import com.coara.browser2.util.PageTranslator;
import com.coara.browser2.util.TranslateSettingsDialog;
import com.coara.browser2.util.BrowserUrlRouter;
import com.coara.browser2.util.BrowserAssetLoader;
import com.coara.browser2.util.UiThread;
import com.coara.browser2.util.HistoryDialog;
import com.coara.browser2.model.HistoryItem;
import com.coara.browser2.plugin.PluginHost;
import com.coara.browser2.plugin.PluginManagerActivity;
import com.coara.browser2.plugin.PluginManager;
import com.coara.browser2.plugin.PluginRuntime;
import com.coara.browser2.util.FindInPageController;
import com.coara.browser2.util.KeyboardSettings;
import com.coara.browser2.util.NetworkStateGuard;
import com.coara.browser2.util.TouchpadController;
import com.coara.browser2.util.TouchpadOverlayController;
import com.coara.browser2.util.TouchpadSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("deprecation")
public class SecretActivity extends AppCompatActivity implements PluginHost, PluginManager.Listener, BrowserHost {
    private static final String PREF_NAME = "SecretBrowserPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_BASIC_AUTH = "basic_auth";
    private static final String KEY_ZOOM_ENABLED = "zoom_enabled";
    private static final String KEY_JS_ENABLED = "js_enabled";
    private static final String KEY_IMG_BLOCK_ENABLED = "img_block_enabled";
    private static final String KEY_UA_ENABLED = "ua_enabled";
    private static final String KEY_DESKUA_ENABLED = "deskua_enabled";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";
    private static final String START_PAGE = BrowserConstants.APP_ASSET_SECRET_PAGE;
    private static final int FILE_SELECT_CODE = 1001;
    private static final int MAX_HISTORY_SIZE = 100;
    private ValueCallback<Boolean> pendingPermissionCallback = null;
    private ActivityResultLauncher<String[]> permissionRequestLauncher;

    private BrowserView webView;
    private TextInputEditText urlEditText;
    private ImageView faviconImageView;
    private MaterialCardView urlContainer;
    private Drawable urlEditSavedIcon;
    private boolean urlEditMode;
    private int urlEditOriginalStrokeColor;
    private int urlEditOriginalStrokeWidth;
    private MaterialButton btnGo;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout webViewContainer;
    private ImageView copyButton;
    private ImageView clearButton;
    private ImageView urlSecurityIcon;
    private android.widget.ProgressBar pageProgressBar;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<String> permissionLauncher;
    private SharedPreferences pref;
    private final ExecutorService backgroundExecutor = UiThread.newPool(2);
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<HistoryItem> historyItems = new ArrayList<>();

    private boolean darkModeEnabled = false;
    private boolean basicAuthEnabled = false;
    private boolean zoomEnabled = true;
    private boolean jsEnabled = true;
    private boolean imgBlockEnabled = false;
    private static final String GALAKE_UA = "DoCoMo/2.0 SH902i(c100;TB)";
    private boolean uaEnabled = false;
    private boolean deskuaEnabled = false;

    private LruCache<String, Bitmap> faviconCache;
    private final Map<BrowserView, Runnable> pendingSpaHistoryTasks = new HashMap<>();
    private boolean defaultLoadsImagesAutomatically;
    private boolean defaultLoadsImagesAutomaticallyInitialized = false;
    private boolean fullScreenActive = false;
    private BrowserView fullScreenView = null;
    private int fullScreenPreviousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean pluginReloadPending = false;
    private boolean pluginReloadRunning = false;
    private int lastPluginRuntimeVersion = -1;
    private PluginManager pluginManager;
    public static class Bookmark {
        private final String title;
        private final String url;
        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    setContentView(R.layout.secret_main);

    toolbar = findViewById(R.id.topAppBar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
        getSupportActionBar().setTitle("");
    }

        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pluginManager = PluginManager.get(this);
        pluginManager.addListener(this);
        lastPluginRuntimeVersion = pluginManager.runtimeVersion();
        darkModeEnabled = pref.getBoolean(KEY_DARK_MODE, false);
        basicAuthEnabled = pref.getBoolean(KEY_BASIC_AUTH, false);
        zoomEnabled = pref.getBoolean(KEY_ZOOM_ENABLED, true);
        jsEnabled = pref.getBoolean(KEY_JS_ENABLED, true);
        imgBlockEnabled = pref.getBoolean(KEY_IMG_BLOCK_ENABLED, false);
        uaEnabled = pref.getBoolean(KEY_UA_ENABLED, false);
        deskuaEnabled = pref.getBoolean(KEY_DESKUA_ENABLED, false);
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(512, Math.min(maxMemory / 32, 2048));
        faviconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        loadBookmarks();
        historyItems.clear();
        if (pref != null) {
            pref.edit().remove(KEY_HISTORY).apply();
        }

        urlEditText = findViewById(R.id.urlEditText);
        urlContainer = findViewById(R.id.urlContainer);
        urlEditOriginalStrokeColor = urlContainer.getStrokeColor();
        urlEditOriginalStrokeWidth = urlContainer.getStrokeWidth();
        urlEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
        KeyboardSettings.applyToEditor(urlEditText, pref);
        urlEditText.setImeOptions(urlEditText.getImeOptions() | 0x01000000);
        urlEditText.setInputType(urlEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlEditText.setPrivateImeOptions("nm");
        faviconImageView = findViewById(R.id.favicon);
        btnGo = findViewById(R.id.btnGo);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        webViewContainer = findViewById(R.id.webViewContainer);

        webView = createNewWebView();
        webViewContainer.addView(webView);
        webView.loadUrl(START_PAGE);
        boolean shouldClear = getIntent()
            .getBooleanExtra(MainActivity.EXTRA_CLEAR_HISTORY, false);
        if (shouldClear) {
            clear0();
       }

        if (!defaultLoadsImagesAutomaticallyInitialized && webView != null) {
            defaultLoadsImagesAutomatically = webView.getSettings().getLoadsImagesAutomatically();
            defaultLoadsImagesAutomaticallyInitialized = true;
        }
        btnGo.setVisibility(View.GONE);

        copyButton = new ImageView(this);
        copyButton.setImageResource(R.drawable.ic_copy);
        copyButton.setAlpha(0.5f);
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(v -> {
            String url = urlEditText.getText().toString().trim();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL", url);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(SecretActivity.this, "URLをコピーしました", Toast.LENGTH_SHORT).show();
        });
        ViewGroup parentView = (ViewGroup) urlEditText.getParent();
        urlSecurityIcon = findViewById(R.id.urlSecurityIcon);
        pageProgressBar = findViewById(R.id.pageProgressBar);
        int buttonSize = Math.round(36 * getResources().getDisplayMetrics().density);
        int buttonPad = Math.round(8 * getResources().getDisplayMetrics().density);
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        copyButton.setPadding(buttonPad, buttonPad, buttonPad, buttonPad);
        copyButton.setBackgroundResource(ripple.resourceId);
        copyButton.setContentDescription("URLをコピー");
        copyButton.setLayoutParams(new LinearLayout.LayoutParams(buttonSize, buttonSize));
        clearButton = new ImageView(this);
        clearButton.setImageResource(R.drawable.ic_close);
        clearButton.setAlpha(0.6f);
        clearButton.setPadding(buttonPad, buttonPad, buttonPad, buttonPad);
        clearButton.setBackgroundResource(ripple.resourceId);
        clearButton.setContentDescription("入力を消去");
        clearButton.setVisibility(View.GONE);
        clearButton.setLayoutParams(new LinearLayout.LayoutParams(buttonSize, buttonSize));
        clearButton.setOnClickListener(v -> {
            urlEditText.setText("");
            urlEditText.requestFocus();
        });
        parentView.addView(clearButton);
        parentView.addView(copyButton);
        urlEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateClearButton();
            }
        });
        View.OnLongClickListener urlBarLongClick = v -> {
            if (urlEditMode) {
                return false;
            }
            showUrlBarMenu(v);
            return true;
        };
        urlContainer.setOnLongClickListener(urlBarLongClick);
        urlEditText.setOnLongClickListener(urlBarLongClick);

        urlEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterUrlEditMode();
            } else {
                exitUrlEditMode();
            }
        });
        urlContainer.setOnClickListener(v -> {
            if (!urlEditText.hasFocus()) {
                urlEditText.requestFocus();
                enterUrlEditMode();
            } else if (!urlEditMode) {
                enterUrlEditMode();
            } else {
                urlEditText.setSelection(urlEditText.length());
            }
        });
        urlEditText.setOnClickListener(v -> {
            if (!urlEditMode) {
                enterUrlEditMode();
            }
        });
        faviconImageView.setOnClickListener(v -> {
            if (urlEditMode) {
                exitUrlEditMode();
            }
        });

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
            swipeRefreshLayout.setOnChildScrollUpCallback((parent1, child) -> true);
            swipeRefreshLayout.setOnRefreshListener(() -> { });
        }
            
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(this, "ストレージ権限が必要です。アプリを終了します", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        permissionRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    ValueCallback<Boolean> callback = pendingPermissionCallback;
                    pendingPermissionCallback = null;
                    if (callback == null) return;
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (granted == null || !granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    callback.onReceiveValue(allGranted);
                });
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    ValueCallback<Uri[]> callback = filePathCallback;
                    filePathCallback = null;
                    if (callback == null) return;
                    Uri[] picked = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        ClipData clip = data.getClipData();
                        if (clip != null && clip.getItemCount() > 0) {
                            picked = new Uri[clip.getItemCount()];
                            for (int k = 0; k < picked.length; k++) {
                                picked[k] = clip.getItemAt(k).getUri();
                            }
                        } else if (data.getData() != null) {
                            picked = new Uri[]{data.getData()};
                        }
                    }
                    callback.onReceiveValue(picked);
                });

        urlEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
               (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
                loadUrl();
                exitUrlEditMode();
                return true;
            }
            return false;
        });

        handleIntent(getIntent());
        urlEditText.clearFocus();
        webViewContainer.setFocusableInTouchMode(true);
        webViewContainer.requestFocus();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
        if (findController != null && findController.isShowing()) {
            findController.hide();
            return;
        }
        if (urlEditMode || (urlEditText != null && urlEditText.hasFocus() && !KeyboardSettings.isAlwaysVisible(pref))) {
            exitUrlEditMode();
            return;
        }
        if (fullScreenActive) {
            exitFullScreen();
            return;
        }
        BrowserView current = getCurrentWebView();
        if (current != null && current.canGoBack()) {
            current.goBack();
            return;
        }
        Toast.makeText(SecretActivity.this, "履歴がありません", Toast.LENGTH_SHORT).show();
         }
       });
    }

    private void clear0() {
        BrowserView webView = getCurrentWebView();
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearMatches();
            clearTabs();
        }
        historyItems.clear();
        if (pref != null) {
            pref.edit().remove(KEY_HISTORY).apply();
        }
        purgeSecretTabPersistence();
     }


    private final Runnable reloadOpenTabsForPluginsRunnable = new Runnable() {
        @Override
        public void run() {
            reloadOpenTabsForPlugins();
        }
    };

    private void schedulePluginReload() {
        pluginReloadPending = true;
        UiThread.mainHandler().removeCallbacks(reloadOpenTabsForPluginsRunnable);
        UiThread.mainHandler().postDelayed(reloadOpenTabsForPluginsRunnable, 150L);
    }

    private void reloadOpenTabsForPlugins() {
        if (!pluginReloadPending || pluginReloadRunning || isFinishing() || isDestroyed() || pluginManager == null) {
            return;
        }
        pluginReloadRunning = true;
        pluginManager.reloadIfChanged();
        PluginRuntime.applyRegistrationsThen(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    reloadTabsForSettings();
                    lastPluginRuntimeVersion = pluginManager.runtimeVersion();
                    pluginReloadPending = false;
                    UiThread.mainHandler().removeCallbacks(reloadOpenTabsForPluginsRunnable);
                } finally {
                    pluginReloadRunning = false;
                }
            }
        });
    }

    @Override
    public void onPluginsChanged() {
    }

    @Override
    public void onPluginAutoDisabled(com.coara.browser2.plugin.PluginInfo info, String reason) {
    }

    @Override
    public void onPluginRuntimeChanged() {
        pluginReloadPending = true;
        if (hasWindowFocus()) {
            schedulePluginReload();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String url = data.toString();
                if (BrowserUrlRouter.isWebUrl(url)) {
                    historyItems.clear();
                    if (pref != null) {
                        pref.edit().remove(KEY_HISTORY).apply();
                    }
                    resetPrivateSession();
                    createNewTab(url);
                    BrowserView current = getCurrentWebView();
                    if (current != null) {
                        ExternalDownloadTabTracker.markExternal(current, url);
                    }
                } else {
                    BrowserUrlRouter.handleUrlLoading(this, getCurrentWebView(), url);
                }
            }
            setIntent(new Intent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && faviconCache != null) {
            try {
                faviconCache.trimToSize(Math.max(1, faviconCache.maxSize() / 2));
            } catch (Exception ignored) {
            }
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL && faviconCache != null) {
            try {
                faviconCache.evictAll();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        if (networkStateGuard == null) {
            networkStateGuard = new NetworkStateGuard(this, () -> java.util.Collections.singletonList(getCurrentWebView()));
        }
        networkStateGuard.start();
        super.onResume();
        com.coara.browser2.fx.FxExtensionManager.get().setForeground(this);
        BrowserView activeWebView = getCurrentWebView();
        if (activeWebView != null) {
            try {
                activeWebView.resumeTimers();
                activeWebView.onResume();
            } catch (Exception ignored) {
            }
        }
        TouchpadController.setCurrentWebView(getCurrentWebView());
        if (urlEditMode) {
            KeyboardSettings.onResume(this, urlEditText, pref);
        } else {
            KeyboardSettings.onResumeWebView(this, getCurrentWebView(), pref);
        }
        if (pluginManager != null) {
            pluginManager.reloadIfChanged();
            int runtimeVersion = pluginManager.runtimeVersion();
            if (runtimeVersion != lastPluginRuntimeVersion) {
                lastPluginRuntimeVersion = runtimeVersion;
                pluginReloadPending = true;
            }
        }
        if (pluginReloadPending) {
            schedulePluginReload();
        }
        TouchpadOverlayController.onHostResume(this, pref);
            invalidateOptionsMenu();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (pluginReloadPending) {
                schedulePluginReload();
            }
            if (KeyboardSettings.isAlwaysVisible(pref) && !urlEditMode) {
                KeyboardSettings.showKeyboardForWebView(this, getCurrentWebView(), pref);
            }
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (deskuaEnabled) {
            BrowserEngine.get().updateDesktopViewport(this);
        }
        TouchpadOverlayController.onConfigurationChanged(this, pref);
    }

    @Override
    protected void onPause() {
        if (networkStateGuard != null) {
            networkStateGuard.stop();
        }
        TouchpadOverlayController.onHostPause(this);
        PluginRuntime.onHostPause(this);
        com.coara.browser2.fx.FxExtensionManager.get().clearForeground(this);
        BrowserView activeWebView = getCurrentWebView();
        if (activeWebView != null) {
            try {
                activeWebView.onPause();
                activeWebView.pauseTimers();
            } catch (Exception ignored) {
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        PageTranslator.release();
        if (findController != null) {
            findController.release();
        }
        TouchpadOverlayController.hide(this);
        BasicAuthManager.cancelPendingForActivity(SecretActivity.this);
        UiThread.mainHandler().removeCallbacks(reloadOpenTabsForPluginsRunnable);
        if (pluginManager != null) {
            pluginManager.removeListener(this);
        }
        pendingSpaHistoryTasks.clear();
        BrowserView current = webView;
        if (current != null) {
            TouchpadController.clearCurrentWebView(current);
            webViewContainer.removeView(current);
            try {
                current.stopLoading();
                current.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        webViewContainer.removeAllViews();
        backgroundExecutor.shutdown();
        super.onDestroy();
    }


    private void purgeSecretTabPersistence() {
        deleteSecretStateFiles();
        deleteRecursively(new File(getFilesDir(), "favicons"));
    }

    private void deleteSecretStateFiles() {
        File dir = getFilesDir();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.getName().startsWith("tab_state_")) {
                
                file.delete();
            }
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        
        file.delete();
    }

    @Override
    public Activity hostActivity() {
        return this;
    }

    @Override
    public void chooseFiles(String[] mimeTypes, boolean multiple, boolean capture, ValueCallback<Uri[]> callback) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        filePathCallback = callback;
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (mimeTypes != null && mimeTypes.length == 1) {
                intent.setType(mimeTypes[0]);
            } else {
                intent.setType("*/*");
                if (mimeTypes != null && mimeTypes.length > 1) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                }
            }
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
            fileChooserLauncher.launch(intent);
        } catch (Exception e) {
            filePathCallback = null;
            Toast.makeText(SecretActivity.this, "ファイル選択エラー", Toast.LENGTH_SHORT).show();
            callback.onReceiveValue(null);
        }
    }

    @Override
    public void requestAndroidPermissions(String[] permissions, ValueCallback<Boolean> callback) {
        if (permissions == null || permissions.length == 0) {
            callback.onReceiveValue(true);
            return;
        }
        if (pendingPermissionCallback != null) {
            pendingPermissionCallback.onReceiveValue(false);
        }
        pendingPermissionCallback = callback;
        try {
            permissionRequestLauncher.launch(permissions);
        } catch (Exception e) {
            pendingPermissionCallback = null;
            callback.onReceiveValue(false);
        }
    }

    @Override
    public void requestStorageAccess(BrowserView view, String topUrlHint, String frameOrigin,
                                     ValueCallback<Boolean> callback) {
        try {
            if (CookieSitePolicy.blockAll(pref)) {
                callback.onReceiveValue(false);
                return;
            }
            String live = view.getUrl();
            String topUrl = live != null && (live.startsWith("http://") || live.startsWith("https://")) ? live : topUrlHint;
            String topHost = CookieSitePolicy.hostOf(topUrl);
            String frameHost = CookieSitePolicy.hostOf(frameOrigin);
            if (topHost.isEmpty() || frameHost.isEmpty()) {
                callback.onReceiveValue(false);
                return;
            }
            if (CookieSite.siteOf(topHost).equals(CookieSite.siteOf(frameHost))) {
                callback.onReceiveValue(true);
                return;
            }
            callback.onReceiveValue(CookieSitePolicy.isAllowed(pref, topUrl));
        } catch (Exception e) {
            callback.onReceiveValue(false);
        }
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
        if (previousFailed) {
            BasicAuthManager.markAuthenticationFailure(this, view, host, realm);
        }
        BasicAuthManager.requestCredentials(this, view, host, realm, (user, password) -> {
            if (user == null) {
                callback.cancel();
            } else {
                callback.proceed(user, password == null ? "" : password);
            }
        });
    }

    private boolean showContextMenu(BrowserClient.ContextHit hit) {
        if (hit == null) {
            return false;
        }
        final boolean hasLink = hit.hasLink();
        final boolean isImage = hit.type == BrowserClient.ContextHit.TYPE_IMAGE && hit.hasSource();
        final boolean isMedia = (hit.type == BrowserClient.ContextHit.TYPE_VIDEO
                || hit.type == BrowserClient.ContextHit.TYPE_AUDIO) && hit.hasSource();
        if (!hasLink && !isImage && !isMedia) {
            return false;
        }
        final String target = hasLink ? hit.linkUri : hit.srcUri;
        final boolean targetIsData = target.startsWith("data:");
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("リンクをコピー");
        actions.add(() -> copyLink(target));
        if (targetIsData) {
            if (isImage && !hasLink) {
                labels.add("画像を保存");
                actions.add(() -> saveImage(hit.srcUri));
            } else {
                labels.add("リンク先を新しいタブで開く");
                actions.add(() -> createNewTab(target));
            }
        } else {
            labels.add("リンクをダウンロード");
            actions.add(() -> handleDownload(target, null, null, null, 0));
            labels.add("リンク先を新しいタブで開く");
            actions.add(() -> createNewTab(target));
            if (isImage) {
                labels.add("画像を保存");
                actions.add(() -> saveImage(hit.srcUri));
            }
        }
        new MaterialAlertDialogBuilder(SecretActivity.this)
            .setTitle("オプションを選択")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                if (which >= 0 && which < actions.size()) {
                    actions.get(which).run();
                }
            }).show();
        return true;
    }

    private void handleBridgeCall(BrowserView owner, String function, JSONArray args) {
        if (owner == null || function == null) {
            return;
        }
        try {
            if ("touchpadNative".equals(function)) {
                int kind = args.optInt(0);
                float a = (float) args.optDouble(1, 0d);
                float b = (float) args.optDouble(2, 0d);
                TouchpadController.onHostRequest(owner, kind, a, b);
            } else if ("onPointerLockChanged".equals(function)) {
                if (owner == getCurrentWebView()) {
                    TouchpadController.setPointerLocked(owner, args.optBoolean(0));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDownloadRequest(BrowserView source, DownloadRequest request) {
        if (source == null || request == null || DownloadSupport.isBlank(request.url)) {
            return;
        }
        if (com.coara.browser2.fx.FxExtensionManager.get().offerInstall(SecretActivity.this, request.url, request.mimeType,
                () -> startDownloadFromRequest(source, request))) {
            return;
        }
        long downloadId = startDownloadFromRequest(source, request);
        if (downloadId > 0 && ExternalDownloadTabTracker.isExternal(source)) {
            if (ExternalDownloadTabTracker.shouldCloseWhenDownloadStarts(source, request.url, request.contentDisposition, request.mimeType)) {
                DownloadSupport.scheduleExternalTabCloseWhenStarted(SecretActivity.this, source, downloadId, () -> closeTab(source));
            } else if (ExternalDownloadTabTracker.shouldCloseAfterDownload(source, request.url)) {
                DownloadSupport.scheduleExternalTabClose(SecretActivity.this, source, downloadId, request.url, () -> closeTab(source));
            }
        }
    }

    private long startDownloadFromRequest(BrowserView source, DownloadRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(SecretActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            if (permissionLauncher != null) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                Toast.makeText(SecretActivity.this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
            }
            return -1;
        }
        String referer = source != null ? source.getUrl() : null;
        long id = request.hasStream()
                ? DownloadFallbackManager.startStreamDownload(this, source, request, basicAuthEnabled, referer)
                : DownloadFallbackManager.startImmediateFallback(this, source, request.url, request.userAgent,
                        request.contentDisposition, request.mimeType, request.contentLength, referer,
                        basicAuthEnabled, "Downloading file...");
        if (id > 0) {
            Toast.makeText(this, "ダウンロードを開始しました", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "ダウンロードに失敗しました", Toast.LENGTH_SHORT).show();
        }
        return id;
    }

    private void enterFullScreen(BrowserView view) {
        if (fullScreenActive) {
            return;
        }
        fullScreenActive = true;
        fullScreenView = view;
        fullScreenPreviousOrientation = getRequestedOrientation();
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void leaveFullScreen() {
        if (!fullScreenActive) {
            return;
        }
        fullScreenActive = false;
        fullScreenView = null;
        if (toolbar != null) {
            toolbar.setVisibility(View.VISIBLE);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        setRequestedOrientation(fullScreenPreviousOrientation);
    }

    private void navigateBackFromError(BrowserView view) {
        if (view == null) {
            return;
        }
        if (view.canGoBack()) {
            view.goBack();
        } else {
            view.loadUrl(START_PAGE);
        }
    }

    private BrowserView createNewWebView() {
        final BrowserView webView = new BrowserView(this, true);
        KeyboardSettings.configureWebView(webView, pref);
        webView.setBackgroundColorValue(darkModeEnabled ? 0xFF2A2A2E : Color.WHITE);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        BrowserSettings settings = webView.getSettings();
        settings.setZoomEnabled(zoomEnabled);
        settings.setJavaScriptEnabled(jsEnabled);
        settings.setCacheMode(BrowserSettings.CACHE_BYPASS);
        settings.setLoadsImagesAutomatically(!imgBlockEnabled);

        if (deskuaEnabled) {
            BrowserEngine.get().updateDesktopViewport(this);
        }
        applyTabModes(webView);

        PluginRuntime.attach(this, webView);

        webView.setBridgeListener(new BrowserView.BridgeListener() {
            @Override
            public void onBridgeCall(BrowserView view, String function, JSONArray args) {
                handleBridgeCall(view, function, args);
            }
        });

        webView.setClient(new BrowserClient() {
            @Override
            public void onLoadFailed(BrowserView view, LoadFailure failure) {
                if (view == getCurrentWebView() && SafeBrowsingSupport.handles(failure)) {
                    UiThread.mainHandler().post(() -> SafeBrowsingSupport.show(SecretActivity.this, view, failure,
                            () -> navigateBackFromError(view)));
                }
            }

            @Override
            public void onErrorAction(BrowserView view, String action, String url) {
                if ("back".equals(action)) {
                    navigateBackFromError(view);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(BrowserView view, String url, boolean mainFrame,
                                                    boolean hasGesture, boolean redirect) {
                if (url == null || url.isEmpty()) {
                    return false;
                }
                ExternalDownloadTabTracker.onMainFrameNavigation(view, mainFrame, hasGesture, redirect, url);
                return BrowserUrlRouter.handleUrlLoading(SecretActivity.this, view, url, mainFrame);
            }

            @Override
            public void onPageStarted(BrowserView view, String url) {
                PageTranslator.onPageStarted(view);
                ExternalDownloadTabTracker.onPageStarted(view, url);
                PluginRuntime.onPageStarted(view, url);
                AdBlockEngine.onPageStarted(view);
                if (findController != null) {
                    findController.onPageStarted(view);
                }
                TouchpadController.onPageStarted(view);
                clearPendingSpaHistory(view);
                urlEditText.setText(url);
                updateUrlSecurityIcon();
            }

            @Override
            public void onPageCommitVisible(BrowserView view, String url) {
                PluginRuntime.onPageCommitVisible(view, url);
            }

            @Override
            public void onUrlChanged(BrowserView view, String url, boolean sameDocument) {
                if (view != getCurrentWebView() || url == null) {
                    return;
                }
                if (sameDocument) {
                    scheduleSpaHistoryUpdate(view, url);
                }
                if (!urlEditMode && !url.equals(START_PAGE)) {
                    urlEditText.setText(url);
                }
            }

            @Override
            public void onPageFinished(BrowserView view, String url) {
                if (url == null) {
                    url = "";
                }
                ExternalDownloadTabTracker.onPageFinished(view, url);
                if (findController != null) {
                    findController.onPageFinished(view);
                }
                PluginRuntime.onPageFinished(view, url);
                TouchpadController.onPageFinished(view);
                KeyboardSettings.configureWebView(view, pref);
                PageTranslator.onPageFinished(view, false);
                if (url.equals(START_PAGE)) {
                    faviconImageView.setVisibility(View.GONE);
                    urlEditText.setText("");
                } else {
                    faviconImageView.setVisibility(View.VISIBLE);
                    if (view == getCurrentWebView()) {
                        urlEditText.setText(url);
                    }
                }
                updateUrlSecurityIcon();
                clearPendingSpaHistory(view);
                addHistory(url, view.getTitle());
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setEnabled(false);
                }
            }

            @Override
            public void onRenderProcessGone(BrowserView view, boolean crashed) {
                PluginRuntime.onRenderProcessGone(view, crashed);
                String lastUrl = null;
                try {
                    lastUrl = view.getUrl();
                } catch (Exception ignored) {
                }
                final String recoverUrl = (lastUrl == null || lastUrl.isEmpty()) ? START_PAGE : lastUrl;
                UiThread.mainHandler().post(() -> {
                    try {
                        closeTab(view);
                        BrowserView replacement = getCurrentWebView();
                        if (replacement != null) {
                            replacement.loadUrl(recoverUrl);
                        }
                    } catch (Exception ignored) {
                    }
                });
                Toast.makeText(SecretActivity.this,
                        crashed ? "ページの表示中に問題が発生したため復旧しました" : "タブを再読み込みしました",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReceivedIcon(BrowserView view, Bitmap icon) {
                if (icon == null) {
                    return;
                }
                if (view == getCurrentWebView()) {
                    if (!urlEditMode) faviconImageView.setImageBitmap(icon);
                }
                String currentUrl = view.getUrl();
                if (currentUrl != null) {
                    faviconCache.put(currentUrl, icon);
                }
            }

            @Override
            public void onFullScreenChanged(BrowserView view, boolean fullScreen) {
                if (fullScreen) {
                    enterFullScreen(view);
                } else {
                    leaveFullScreen();
                }
            }

            @Override
            public boolean onContextMenu(BrowserView view, ContextHit hit) {
                return showContextMenu(hit);
            }

            @Override
            public void onDownloadRequested(BrowserView view, DownloadRequest request) {
                handleDownloadRequest(view, request);
            }
        });
        return webView;
    }

    private void closeTab(BrowserView target) {
        if (target == null || target != webView) {
            return;
        }
        ExternalDownloadTabTracker.clear(target);
        try {
            target.stopLoading();
        } catch (Exception ignored) {
        }
        try {
            target.destroy();
        } catch (Exception ignored) {
        }
        webViewContainer.removeView(target);
        webView = createNewWebView();
        webViewContainer.addView(webView);
        webView.loadUrl(START_PAGE);
        urlEditText.setText(START_PAGE);
    }

    private long handleDownload(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        if (DownloadSupport.isBlank(url)) {
            Toast.makeText(SecretActivity.this, "ダウンロードURLが見つかりません", Toast.LENGTH_SHORT).show();
            return -1;
        }
        if (DownloadSupport.isDataUrl(url)) {
            if (!DownloadSupport.saveDataUrl(SecretActivity.this, url, null, mimeType)) {
                Toast.makeText(SecretActivity.this, "データURLの保存に失敗しました", Toast.LENGTH_SHORT).show();
            }
            return -1;
        }
        if (DownloadSupport.isBlobUrl(url)) {
            Toast.makeText(SecretActivity.this, "対応していないURLのためダウンロードできません", Toast.LENGTH_LONG).show();
            return -1;
        }
        if (!BrowserUrlRouter.isWebUrl(url)) {
            Toast.makeText(SecretActivity.this, "対応していないURLのためダウンロードできません", Toast.LENGTH_LONG).show();
            return -1;
        }
        BrowserView current = getCurrentWebView();
        String ua = userAgent;
        if (DownloadSupport.isBlank(ua) && current != null) {
            ua = current.getSettings().getUserAgentString();
        }
        return startDownloadFromRequest(current, DownloadRequest.forUrl(url, null, mimeType, contentLength,
                contentDisposition, ua, current != null ? current.getUrl() : null));
    }

    private void saveImage(String imageUrl) {
    try {
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            int commaIndex = imageUrl.indexOf(',');
            if (commaIndex == -1) {
                Toast.makeText(SecretActivity.this, "無効なデータURL", Toast.LENGTH_SHORT).show();
                return;
            }
            String metadata = imageUrl.substring(5, commaIndex); 
            boolean isBase64 = metadata.contains("base64");
            String mimeType = "image/*";
            if (metadata.contains(";")) {
                mimeType = metadata.split(";")[0];
            }
            byte[] imageData;
            if (isBase64) {
                String base64Data = imageUrl.substring(commaIndex + 1);
                imageData = Base64.decode(base64Data, Base64.DEFAULT);
            } else {
                String dataPart = imageUrl.substring(commaIndex + 1);
                imageData = dataPart.getBytes("UTF-8");
            }
            String fileName = "saved_image_" + System.currentTimeMillis();
            if (mimeType.equalsIgnoreCase("image/png")) {
                fileName += ".png";
            } else if (mimeType.equalsIgnoreCase("image/jpeg")) {
                fileName += ".jpg";
            } else if (mimeType.equalsIgnoreCase("image/bmp")) {
                fileName += ".bmp";
            } else if (mimeType.equalsIgnoreCase("image/gif")) {
                fileName += ".gif";
            } else if (mimeType.equalsIgnoreCase("image/img")) {
                fileName += ".img";
            }
            File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
            File file = new File(picturesDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(imageData);
                fos.flush();
            }
            Toast.makeText(SecretActivity.this,
                "画像の保存が完了しました\n保存先: " + file.getAbsolutePath(),
                Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(SecretActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(SecretActivity.this,
                "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
            return;
        }
        final String sourceUrl = imageUrl;
        BrowserView currentWebView = getCurrentWebView();
        final String refererUrl = currentWebView != null ? currentWebView.getUrl() : null;
        final String userAgent = currentWebView != null ? currentWebView.getSettings().getUserAgentString() : null;
        Toast.makeText(SecretActivity.this, "画像の保存を開始しました", Toast.LENGTH_SHORT).show();
        backgroundExecutor.execute(() -> saveRemoteImage(sourceUrl, refererUrl, userAgent));
        return;
    } catch (Exception e) {
        Toast.makeText(SecretActivity.this,
            "画像の保存に失敗しました", Toast.LENGTH_SHORT).show();
        e.printStackTrace();
    }
}
    private void saveRemoteImage(String sourceUrl, String refererUrl, String userAgent) {
        HttpURLConnection connection = null;
        com.coara.browser2.util.PublicStorageWriter.Target target = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            String cookies = com.coara.browser2.util.CookieBridge.getCookieHeader(sourceUrl);
            if (!DownloadSupport.isBlank(cookies)) {
                connection.setRequestProperty("Cookie", cookies);
            }
            String authorization = BasicAuthManager.getAuthorizationHeaderForUrl(sourceUrl, basicAuthEnabled);
            if (!DownloadSupport.isBlank(authorization)) {
                connection.setRequestProperty("Authorization", authorization);
            }
            if (!DownloadSupport.isBlank(refererUrl) && BrowserUrlRouter.isWebUrl(refererUrl)) {
                connection.setRequestProperty("Referer", refererUrl);
            }
            if (!DownloadSupport.isBlank(userAgent)) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            int code = connection.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code);
            }
            String headerMime = connection.getContentType();
            if (headerMime != null && headerMime.indexOf(';') > 0) {
                headerMime = headerMime.substring(0, headerMime.indexOf(';')).trim();
            }
            String mime = DownloadSupport.normalizeMimeType(headerMime, sourceUrl);
            if (DownloadSupport.isBlank(mime)) {
                mime = "image/*";
            }
            String fileName = DownloadSupport.resolveFileName(sourceUrl, connection.getHeaderField("Content-Disposition"), mime);
            target = com.coara.browser2.util.PublicStorageWriter.openPicturesTarget(SecretActivity.this, fileName, mime);
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    target.outputStream.write(buffer, 0, read);
                }
            }
            target.outputStream.flush();
            com.coara.browser2.util.PublicStorageWriter.finish(SecretActivity.this, target);
            final String displayPath = target.displayPath;
            runOnUiThread(() -> Toast.makeText(SecretActivity.this,
                    "画像の保存が完了しました\n保存先: " + displayPath, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            if (target != null) {
                com.coara.browser2.util.PublicStorageWriter.abort(SecretActivity.this, target);
            }
            runOnUiThread(() -> Toast.makeText(SecretActivity.this, "画像の保存に失敗しました", Toast.LENGTH_SHORT).show());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void exportBookmarksToFile() {
        final String bookmarksJson = pref.getString(KEY_BOOKMARKS, "[]");
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final File file;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            file = new File(downloadDir, "JSON-bookmark" + timeStamp + ".txt");
        } else {
            file = new File(downloadDir, timeStamp + "-bookmark.json");
        }
        backgroundExecutor.execute(() -> {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bookmarksJson.getBytes("UTF-8"));
                fos.flush();
                runOnUiThread(() ->
                    Toast.makeText(SecretActivity.this, "ブックマークをエクスポートしました: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(SecretActivity.this, "ブックマークのエクスポートに失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
                e.printStackTrace();
            }
        });
    }
    @Override
    public void onPluginOpenTab(String url) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            runOnUiThread(() -> createNewTab(url));
        }
    }

    private void showPluginCommands() {
        final BrowserView current = getCurrentWebView();
        final java.util.List<PluginRuntime.PluginCommand> commands = PluginRuntime.getCommands(current);
        if (commands.isEmpty()) {
            Toast.makeText(SecretActivity.this, "このページで使えるコマンドはありません", Toast.LENGTH_SHORT).show();
            return;
        }
        View content = getLayoutInflater().inflate(com.coara.browser2.R.layout.dialog_plugin_commands, null, false);
        LinearLayout container = content.findViewById(com.coara.browser2.R.id.pluginCommandContainer);
        Map<String, java.util.List<PluginRuntime.PluginCommand>> groups = new LinkedHashMap<>();
        for (PluginRuntime.PluginCommand command : commands) {
            String key = command.pluginId == null || command.pluginId.isEmpty() ? command.pluginTitle : command.pluginId;
            java.util.List<PluginRuntime.PluginCommand> group = groups.get(key);
            if (group == null) {
                group = new java.util.ArrayList<>();
                groups.put(key, group);
            }
            group.add(command);
        }
        for (java.util.List<PluginRuntime.PluginCommand> group : groups.values()) {
            PluginRuntime.PluginCommand first = group.get(0);
            View header = getLayoutInflater().inflate(com.coara.browser2.R.layout.item_plugin_command_group, container, false);
            ((TextView) header.findViewById(com.coara.browser2.R.id.pluginCommandGroupTitle)).setText(first.pluginTitle);
            ((TextView) header.findViewById(com.coara.browser2.R.id.pluginCommandGroupCount)).setText(group.size() + "件");
            container.addView(header);
            for (PluginRuntime.PluginCommand command : group) {
                View item = getLayoutInflater().inflate(com.coara.browser2.R.layout.item_plugin_command, container, false);
                String title = command.title == null || command.title.isEmpty() ? command.commandId : command.title;
                ((TextView) item.findViewById(com.coara.browser2.R.id.pluginCommandTitle)).setText(title);
                ((TextView) item.findViewById(com.coara.browser2.R.id.pluginCommandId)).setText("コマンドID: " + command.commandId);
                final String commandId = command.commandId;
                item.setOnClickListener(v -> PluginRuntime.runCommand(current, commandId));
                container.addView(item);
            }
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(SecretActivity.this)
                .setTitle("拡張機能コマンド")
                .setView(content)
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void createNewTab(String url) {
        BrowserView current = getCurrentWebView();
        if (current == null) {
            webView = createNewWebView();
            webViewContainer.removeAllViews();
            webViewContainer.addView(webView);
            current = webView;
        } else {
            ExternalDownloadTabTracker.clear(current);
            try {
                current.stopLoading();
                current.clearHistory();
            } catch (Exception ignored) {
            }
        }
                current.loadUrl(url);
        urlEditText.setText(url);
    }

    private BrowserView getCurrentWebView() {
        return webView;
    }

    private void updatePullToRefreshState(BrowserView webView, String url) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(false);
        }
    }

    private void clearPendingSpaHistory(BrowserView webView) {
        if (webView == null) return;
        Runnable pending = pendingSpaHistoryTasks.remove(webView);
        if (pending != null) {
            UiThread.mainHandler().removeCallbacks(pending);
        }
    }

    private void scheduleSpaHistoryUpdate(final BrowserView owner, final String url) {
        if (owner == null || url == null || url.isEmpty()) return;
        clearPendingSpaHistory(owner);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    if (owner != getCurrentWebView()) return;
                    String current = owner.getUrl();
                    if (current == null || !current.equals(url)) return;
                    updatePullToRefreshState(owner, url);
                    urlEditText.setText(url);
                    updateUrlSecurityIcon();
                    addHistory(url, owner.getTitle());
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setEnabled(false);
                    }
                } finally {
                    pendingSpaHistoryTasks.remove(owner);
                }
            }
        };
        pendingSpaHistoryTasks.put(owner, task);
        UiThread.mainHandler().postDelayed(task, 180L);
    }

    private void updateClearButton() {
        if (clearButton == null) {
            return;
        }
        boolean show = urlEditMode && urlEditText != null && urlEditText.length() > 0;
        clearButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateUrlSecurityIcon() {
        if (urlSecurityIcon == null) {
            return;
        }
        BrowserView current = getCurrentWebView();
        String url = current != null ? current.getUrl() : null;
        if (urlEditMode || url == null) {
            urlSecurityIcon.setVisibility(View.GONE);
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            urlSecurityIcon.setImageResource(R.drawable.ic_lock_small);
            urlSecurityIcon.setContentDescription("保護された通信");
            urlSecurityIcon.setVisibility(View.VISIBLE);
        } else if (lower.startsWith("http://")) {
            urlSecurityIcon.setImageResource(R.drawable.ic_warning_small);
            urlSecurityIcon.setContentDescription("保護されていない通信");
            urlSecurityIcon.setVisibility(View.VISIBLE);
        } else {
            urlSecurityIcon.setVisibility(View.GONE);
        }
    }

    private void showUrlBarMenu(View anchor) {
        final BrowserView current = getCurrentWebView();
        final String url = current != null ? current.getUrl() : null;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, "URLをコピー").setEnabled(url != null && !url.isEmpty());
        menu.getMenu().add(0, 2, 2, "貼り付けて移動");
        menu.getMenu().add(0, 3, 3, "URLを共有").setEnabled(url != null && !url.isEmpty());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && url != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("URL", url));
                    Toast.makeText(SecretActivity.this, "URLをコピーしました", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (id == 2) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence text = null;
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    text = cm.getPrimaryClip().getItemAt(0).coerceToText(SecretActivity.this);
                }
                if (text == null || text.toString().trim().isEmpty()) {
                    Toast.makeText(SecretActivity.this, "クリップボードが空です", Toast.LENGTH_SHORT).show();
                } else {
                    urlEditText.setText(text.toString().trim());
                    loadUrl();
                }
                return true;
            }
            if (id == 3 && url != null) {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, url);
                    startActivity(Intent.createChooser(send, "共有"));
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void enterUrlEditMode() {
        if (urlEditMode || urlEditText == null) return;
        urlEditMode = true;
        urlEditSavedIcon = faviconImageView != null ? faviconImageView.getDrawable() : null;
        if (faviconImageView != null) {
            faviconImageView.setImageResource(R.drawable.ic_close);
            faviconImageView.setVisibility(View.VISIBLE);
            faviconImageView.setContentDescription("URL編集を終了");
        }
        if (copyButton != null) copyButton.setVisibility(View.VISIBLE);
        updateClearButton();
        updateUrlSecurityIcon();
        if (urlContainer != null) {
            urlContainer.setStrokeColor(ContextCompat.getColor(this, R.color.colorPrimary));
            urlContainer.setStrokeWidth(2);
        }
        urlEditText.selectAll();
        urlEditText.post(() -> {
            if (urlEditText != null && urlEditText.hasFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void exitUrlEditMode() {
        if (!urlEditMode) return;
        urlEditMode = false;
        if (copyButton != null) copyButton.setVisibility(View.GONE);
        if (clearButton != null) clearButton.setVisibility(View.GONE);
        updateUrlSecurityIcon();
        if (faviconImageView != null) {
            if (urlEditSavedIcon != null) {
                faviconImageView.setImageDrawable(urlEditSavedIcon);
            } else {
                faviconImageView.setImageResource(R.drawable.transparent_vector);
            }
            faviconImageView.setVisibility(View.VISIBLE);
            faviconImageView.setContentDescription("Favicon");
        }
        urlEditSavedIcon = null;
        if (urlContainer != null) {
            urlContainer.setStrokeColor(urlEditOriginalStrokeColor);
            urlContainer.setStrokeWidth(urlEditOriginalStrokeWidth);
        }
        if (urlEditText != null) {
            if (KeyboardSettings.isAlwaysVisible(pref)) {
                KeyboardSettings.showKeyboardForWebView(this, getCurrentWebView(), pref);
            } else {
                KeyboardSettings.hideKeyboard(this, urlEditText);
            }
        }
    }

    private void loadUrl() {
        String input = urlEditText.getText().toString().trim();
        if (input.isEmpty()) {
            return;
        }
        String url = BrowserUrlRouter.normalizeUserInput(input);
        if (url.isEmpty()) {
            return;
        }
        BrowserView current = getCurrentWebView();
        if (current == null) {
            return;
        }
        ExternalDownloadTabTracker.clear(current);
        if (BrowserUrlRouter.handleUrlLoading(this, current, url)) {
            return;
        }
                current.loadUrl(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar_menu2, menu);
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem darkModeItem = menu.findItem(R.id.action_dark_mode);
        darkModeItem.setVisible(true);
        darkModeItem.setCheckable(true);
        darkModeItem.setChecked(darkModeEnabled);
        MenuItem uaItem = menu.findItem(R.id.action_ua);
        if (uaItem != null) uaItem.setChecked(uaEnabled);
        MenuItem deskuaItem = menu.findItem(R.id.action_deskua);
        if (deskuaItem != null) deskuaItem.setChecked(deskuaEnabled);
        MenuItem zoomItem = menu.findItem(R.id.action_zoom_toggle);
        if (zoomItem != null) zoomItem.setChecked(zoomEnabled);
        MenuItem jsItem = menu.findItem(R.id.action_js);
        if (jsItem != null) jsItem.setChecked(jsEnabled);
        MenuItem imgItem = menu.findItem(R.id.action_img);
        if (imgItem != null) imgItem.setChecked(imgBlockEnabled);
        MenuItem basicAuthItem = menu.findItem(R.id.action_basic_auth);
        if (basicAuthItem != null) basicAuthItem.setChecked(basicAuthEnabled);
        MenuItem adBlockItem = menu.findItem(R.id.action_adblock);
        if (adBlockItem != null) {
            adBlockItem.setChecked(AdBlockEngine.isEnabled());
            adBlockItem.setTitle(AdBlockEngine.menuTitle());
        }
        return super.onPrepareOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_dark_mode) {
            darkModeEnabled = !darkModeEnabled;
            item.setChecked(darkModeEnabled);
            pref.edit().putBoolean(KEY_DARK_MODE, darkModeEnabled).apply();
            updateDarkMode();
            Toast.makeText(this, "ダークモード " + (darkModeEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_adblock) {
            boolean enable = !AdBlockEngine.isEnabled();
            AdBlockEngine.setEnabled(this, enable);
            item.setChecked(enable);
            Toast.makeText(this, "AdBlock " + (enable ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            BrowserView current = getCurrentWebView();
            if (current != null) {
                current.reload();
            }
            return true;
        } else if (id == R.id.action_Settings) {
            startActivity(new Intent(SecretActivity.this, SettingsActivity.class));
        } else if (id == R.id.action_keyboard_settings) {
            KeyboardSettings.showSettingsDialog(this, urlEditText, getCurrentWebView(), pref);
        } else if (id == R.id.action_touchpad) {
            TouchpadOverlayController.show(SecretActivity.this, pref);
        } else if (id == R.id.action_Secret) {
            clearSecretDataAndReturnToMain();
            return true;
        } else if (id == R.id.action_plugins) {
            startActivity(new Intent(SecretActivity.this, PluginManagerActivity.class));
        } else if (id == R.id.action_plugin_commands) {
            showPluginCommands();
        } else if (id == R.id.action_fx_addons) {
            startActivity(new Intent(SecretActivity.this, com.coara.browser2.fx.FxExtensionsActivity.class));
        } else if (id == R.id.action_fx_actions) {
            BrowserView fxView = getCurrentWebView();
            com.coara.browser2.fx.FxActionsDialog.show(this, fxView == null ? null : fxView.getGeckoSession());
        } else if (id == R.id.action_downloads) {
            startActivity(new Intent(SecretActivity.this, DownloadHistoryActivity.class));
        } else if (id == R.id.action_qr) {
            startActivity(new Intent(SecretActivity.this, QrCodeActivity.class));
        } else if (id == R.id.action_pgdl) {
            Intent intent = new Intent(SecretActivity.this, pagedl.class);
            intent.putExtra(BasicAuthManager.EXTRA_BASIC_AUTH_ENABLED, basicAuthEnabled);
            startActivity(intent);
        } else if (id == R.id.action_txtphoto) {
            startActivity(new Intent(SecretActivity.this, txtphoto.class));
        } else if (id == R.id.action_num) {
            startActivity(new Intent(SecretActivity.this, num.class));
        } else if (id == R.id.action_asciiart) {
            startActivity(new Intent(SecretActivity.this, asciiart.class));
        } else if (id == R.id.action_grep) {
            startActivity(new Intent(SecretActivity.this, grepmd5appActivity.class));
        } else if (id == R.id.action_htmlview) {
            startActivity(new Intent(SecretActivity.this, htmlview.class));
        } else if (id == R.id.action_bookmark_management) {
            showBookmarksManagementDialog();
        } else if (id == R.id.action_find_in_page) {
            showFindInPageBar();
            return true;
        } else if (id == R.id.action_add_bookmark) {
            addBookmark();
        } else if (id == R.id.action_history) {
            showHistoryDialog();
        } else if (id == R.id.action_export) {
            exportBookmarksToFile();
        } else if (id == R.id.action_import) {
            importBookmarksFromFile();
        } else if (id == R.id.action_Dhistory) {
            Intent intent = new Intent(SecretActivity.this, DownloadHistoryActivity.class);
            intent.putExtra("clear_history", true);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_zoom_toggle) {
            if (item.isChecked()) {
                disableZoom();
                zoomEnabled = false;
            } else {
                enableZoom();
                zoomEnabled = true;
            }
            item.setChecked(zoomEnabled);
            pref.edit().putBoolean(KEY_ZOOM_ENABLED, zoomEnabled).apply();
        } else if (id == R.id.action_js) {
            if (item.isChecked()) {
                disablejs();
                jsEnabled = false;
            } else {
                enablejs();
                jsEnabled = true;
            }
            item.setChecked(jsEnabled);
            pref.edit().putBoolean(KEY_JS_ENABLED, jsEnabled).apply();
        } else if (id == R.id.action_img) {
            if (item.isChecked()) {
                disableimgunlock();
                imgBlockEnabled = false;
            } else {
                enableimgblock();
                imgBlockEnabled = true;
            }
            item.setChecked(imgBlockEnabled);
            pref.edit().putBoolean(KEY_IMG_BLOCK_ENABLED, imgBlockEnabled).apply();
        } else if (id == R.id.action_ua) {
            uaEnabled = !uaEnabled;
            if (uaEnabled) {
                deskuaEnabled = false;
            }
            pref.edit().putBoolean(KEY_UA_ENABLED, uaEnabled).putBoolean(KEY_DESKUA_ENABLED, deskuaEnabled).apply();
            item.setChecked(uaEnabled);
            Toast.makeText(this, uaEnabled ? "ガラケーUA有効" : "ガラケーUA解除", Toast.LENGTH_SHORT).show();
            onTabModeChanged();
            invalidateOptionsMenu();
        } else if (id == R.id.action_deskua) {
            deskuaEnabled = !deskuaEnabled;
            if (deskuaEnabled) {
                uaEnabled = false;
            }
            pref.edit().putBoolean(KEY_UA_ENABLED, uaEnabled).putBoolean(KEY_DESKUA_ENABLED, deskuaEnabled).apply();
            item.setChecked(deskuaEnabled);
            Toast.makeText(this, deskuaEnabled ? "デスクトップ表示有効" : "デスクトップ表示無効", Toast.LENGTH_SHORT).show();
            onTabModeChanged();
            invalidateOptionsMenu();
        } else if (id == R.id.action_basic_auth) {
            if (!basicAuthEnabled) {
                basicAuthEnabled = true;
                item.setChecked(true);
                Toast.makeText(SecretActivity.this, "Basic認証 ON", Toast.LENGTH_SHORT).show();
            } else {
                basicAuthEnabled = false;
                item.setChecked(false);
                clearBasicAuthCacheAndReload();
                Toast.makeText(SecretActivity.this, "Basic認証 OFF", Toast.LENGTH_SHORT).show();
            }
            pref.edit().putBoolean(KEY_BASIC_AUTH, basicAuthEnabled).apply();
        } else if (id == R.id.action_clear_history) {
            BrowserView current = getCurrentWebView();
            if (current != null) {
                current.clearHistory();
            }
            historyItems.clear();
            if (pref != null) {
                pref.edit().remove(KEY_HISTORY).apply();
            }
            clearWebStorage();
            clearPageCache();
            clearTabs();
            BasicAuthManager.clear(SecretActivity.this);
            urlEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            urlEditText.setRawInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            urlEditText.setPrivateImeOptions("nm");
            urlEditText.setAutofillHints("");
            urlEditText.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            String currentText = urlEditText.getText().toString();
            urlEditText.setText("");
            urlEditText.setText(currentText);
            Toast.makeText(SecretActivity.this, "履歴、フォームデータ、検索候補、及びタブとCookieを消去しました", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_negapoji) {
            applyNegapoji();
        } else if (id == R.id.action_translate) {
            translatePageToJapanese();
            return true;
        } else if (id == R.id.action_translate_settings) {
            TranslateSettingsDialog.show(this, true);
            return true;
        } else if (id == R.id.action_screenshot) {
            takeScreenshot();
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearBasicAuthCacheAndReload() {
        BasicAuthManager.clear(SecretActivity.this);
        BrowserView current = getCurrentWebView();
        if (current != null) {
            current.stopLoading();
            current.clearCache(true);
            String currentUrl = current.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty()) {
                current.loadUrl(currentUrl);
            } else {
                current.reload();
            }
        }
    }

    private void translatePageToJapanese() {
        BrowserView target = getCurrentWebView();
        if (target == null) {
            Toast.makeText(SecretActivity.this, "翻訳するページが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        PageTranslator.toggle(target, false);
    }
    private void clearSecretDataAndReturnToMain() {
        BrowserView current = webView;
        if (current != null) {
            try {
                current.stopLoading();
                current.clearHistory();
                current.clearCache(true);
                current.clearFormData();
                current.clearMatches();
                current.removeAllViews();
            } catch (Exception ignored) {
            }
            try {
                current.destroy();
            } catch (Exception ignored) {
            }
        }
        webView = null;
        webViewContainer.removeAllViews();
        historyItems.clear();
        if (faviconCache != null) {
            faviconCache.evictAll();
        }

        clearWebStorage();

        BasicAuthManager.clear(SecretActivity.this);

        if (urlEditText != null) {
            urlEditText.setText("");
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        finish();
    }

    private void applyNegapoji() {
        String js = "javascript:(function() {" +
                "document.documentElement.style.filter = 'invert(1)';" +
                "var media = document.getElementsByTagName('img');" +
                "for (var i = 0; i < media.length; i++) {" +
                "    media[i].style.filter = 'invert(1)';" +
                "}" +
                "})()";
        getCurrentWebView().evaluateJavascript(js, null);
    }

    private void clearWebStorage() {
        resetPrivateSession();
    }

    private void resetPrivateSession() {
        BrowserView current = getCurrentWebView();
        if (current != null) {
            closeTab(current);
        }
    }

    private void applyTabModes(BrowserView view) {
        BrowserSettings s = view.getSettings();
        s.setUserAgentString(uaEnabled ? GALAKE_UA : null);
        s.setDesktopMode(deskuaEnabled && !uaEnabled);
    }

    private void onTabModeChanged() {
        BrowserEngine.get().updateDesktopViewport(this);
        BrowserView only = getCurrentWebView();
        if (only != null) {
            applyTabModes(only);
        }
        reloadTabsForSettings();
    }

    private void reloadCurrentPage() {
        BrowserView current = getCurrentWebView();
        if (current == null) {
            return;
        }
        String u = current.getUrl();
        if (u == null || u.isEmpty() || u.equals("about:blank")) {
            return;
        }
        try {
            PluginRuntime.prepareForFullReload(current);
            current.reloadForSettings();
        } catch (Exception ignored) {
        }
    }

    private void reloadTabsForSettings() {
        reloadCurrentPage();
    }

    private void clearPageCache() {
        BrowserView current = getCurrentWebView();
        if (current != null) {
            current.clearCache(true);
        }
    }

    private void clearTabs() {
        BrowserView current = getCurrentWebView();
        if (current == null) {
            webView = createNewWebView();
            webViewContainer.removeAllViews();
            webViewContainer.addView(webView);
            current = webView;
        }
        ExternalDownloadTabTracker.clear(current);
        try {
            current.stopLoading();
            current.clearHistory();
        } catch (Exception ignored) {
        }
        current.loadUrl(START_PAGE);
        if (current.getParent() != webViewContainer) {
            webViewContainer.removeAllViews();
            webViewContainer.addView(current);
        }
    }

        private void takeScreenshot() {
        com.coara.browser2.util.ScreenshotSaver.capture(this, getCurrentWebView(), backgroundExecutor);
    }

    private void updateDarkMode() {
        BrowserEngine.get().setDarkMode(darkModeEnabled);
        DarkModeSwitcher.settle(getCurrentWebView(), darkModeEnabled, () -> {
            if (!isFinishing() && !isDestroyed()) {
                reloadTabsForSettings();
            }
        });
    }

                            private void disablejs() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setJavaScriptEnabled(false);
        Toast.makeText(SecretActivity.this, "JavaScript無効", Toast.LENGTH_SHORT).show();
    }

    private void enablejs() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setJavaScriptEnabled(jsEnabled);
        Toast.makeText(SecretActivity.this, "JavaScript有効", Toast.LENGTH_SHORT).show();
    }

    private void enableZoom() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setZoomEnabled(true);
        Toast.makeText(SecretActivity.this, "ズームを有効にしました", Toast.LENGTH_SHORT).show();
    }

    private void disableZoom() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setZoomEnabled(false);
        reloadCurrentPage();
        Toast.makeText(SecretActivity.this, "ズームを無効にしました", Toast.LENGTH_SHORT).show();
    }

    private void enableimgblock() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setLoadsImagesAutomatically(false);
        reloadCurrentPage();
        Toast.makeText(SecretActivity.this, "画像ブロック有効", Toast.LENGTH_SHORT).show();
    }

    private void disableimgunlock() {
        BrowserView webView = getCurrentWebView();
        webView.getSettings().setLoadsImagesAutomatically(defaultLoadsImagesAutomatically);
        webView.clearCache(true);
        webView.reload();
        Toast.makeText(SecretActivity.this, "画像ブロック無効", Toast.LENGTH_SHORT).show();
    }
    private FindInPageController findController;
    private NetworkStateGuard networkStateGuard;

    private FindInPageController findController() {
        if (findController == null) {
            findController = new FindInPageController(this, pref, this::getCurrentWebView);
        }
        return findController;
    }

    private void showFindInPageBar() {
        findController().show();
    }

    private void hideFindInPageBar() {
        findController().hide();
    }

    private void showHistoryDialog() {
        HistoryDialog.show(this, historyItems, new HistoryDialog.Host() {
            @Override
            public Bitmap favicon(String url) {
                return faviconCache.get(url);
            }

            @Override
            public void open(String url) {
                BrowserView current = getCurrentWebView();
                if (current != null) {
                    current.loadUrl(url);
                }
            }

            @Override
            public void copy(String url) {
                copyLink(url);
            }

            @Override
            public void changed() {
                saveHistory();
            }
        });
    }


    private void showBookmarksManagementDialog() {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "ブックマークがありません", Toast.LENGTH_SHORT).show();
            return;
        }
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("ブックマーク")
            .setNegativeButton("閉じる", null)
            .setView(recyclerView)
            .create();
        BookmarkAdapter adapter = new BookmarkAdapter(bookmarks, true, dialog);
        recyclerView.setAdapter(adapter);
        dialog.show();
    }

    private void showEditBookmarkDialog(final int position, final BookmarkAdapter adapter) {
        Bookmark bm = bookmarks.get(position);
        View editView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_bookmark, null);
        EditText etTitle = editView.findViewById(R.id.editTitle);
        EditText etUrl = editView.findViewById(R.id.editUrl);
        etTitle.setText(bm.getTitle());
        etUrl.setText(bm.getUrl());
        new MaterialAlertDialogBuilder(SecretActivity.this)
            .setTitle("ブックマーク")
            .setView(editView)
            .setPositiveButton("保存", (dialog, which) -> {
                String newTitle = etTitle.getText().toString().trim();
                String newUrl = etUrl.getText().toString().trim();
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    newUrl = "http://" + newUrl;
                }
                bookmarks.set(position, new Bookmark(newTitle, newUrl));
                saveBookmarks();
                adapter.notifyDataSetChanged();
                Toast.makeText(SecretActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private void addBookmark() {
        String currentUrl = getCurrentWebView().getUrl();
        String title = getCurrentWebView().getTitle();
        if (currentUrl != null && !currentUrl.isEmpty()) {
            bookmarks.add(new Bookmark(title, currentUrl));
            saveBookmarks();
            Toast.makeText(SecretActivity.this, "ブックマークを追加しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadBookmarks() {
        bookmarks.clear();
        String jsonStr = pref.getString(KEY_BOOKMARKS, "[]");
        if (!jsonStr.isEmpty()) {
            parseAndAddBookmarks(jsonStr);
        }
    }
    private final ActivityResultLauncher<Intent> filePickerLauncher =
    registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK &&
                result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    handleFileImport(uri);
                } else {
                    Toast.makeText(SecretActivity.this,
                        "ファイルが選択されませんでした", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(SecretActivity.this,
                    "ファイル選択がキャンセルされました", Toast.LENGTH_SHORT).show();
            }
        });
    private void importBookmarksFromFile() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        intent.setType("*/*");
    } else {
        intent.setType("application/json");
    }
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    filePickerLauncher.launch(intent);
    }
    private void handleFileImport(Uri uri) {
    try {
        String json = readTextFromUri(uri);
        parseAndImportBookmarks(json);
        Toast.makeText(SecretActivity.this, "ブックマークをインポートしました", Toast.LENGTH_SHORT).show();
    } catch (IOException e) {
        Toast.makeText(SecretActivity.this, "ファイルの読み取りに失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        e.printStackTrace();
    } catch (JSONException e) {
        Toast.makeText(SecretActivity.this, "JSON解析エラー: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        e.printStackTrace();
      }
    }
    private String readTextFromUri(Uri uri) throws IOException {
    StringBuilder builder = new StringBuilder();
    try (InputStream inputStream = getContentResolver().openInputStream(uri);
         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
          }
       }
       return builder.toString();
      }
    private void parseAndImportBookmarks(String jsonStr) throws JSONException {
        JSONArray array = new JSONArray(jsonStr);
        bookmarks.clear();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String title = obj.optString("title", "Untitled");
            String url = obj.optString("url", "");
            if (!url.isEmpty()) {
                bookmarks.add(new Bookmark(title, url));
                backgroundExecutor.execute(() -> {
                    Bitmap favicon = fetchFavicon(url);
                    if (favicon != null) {
                        runOnUiThread(() -> faviconCache.put(url, favicon));
                    }
                });
            }
        }
        saveBookmarks();
    }

    private void parseAndAddBookmarks(String jsonStr) {
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String title = obj.optString("title", "Untitled");
                String url = obj.optString("url", "");
                if (!url.isEmpty()) {
                    bookmarks.add(new Bookmark(title, url));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveBookmarks() {
        JSONArray array = new JSONArray();
        for (Bookmark bm : bookmarks) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", bm.getTitle());
                obj.put("url", bm.getUrl());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            array.put(obj);
        }
        pref.edit().putString(KEY_BOOKMARKS, array.toString()).apply();
    }
private void loadHistory() {
    historyItems.clear();
}

private void saveHistory() {
    if (pref != null) {
        pref.edit().remove(KEY_HISTORY).apply();
    }
}

private String normalizeUrl(String url) {  
    try {
        Uri uri = Uri.parse(url);
        return uri.buildUpon().fragment(null).build().toString();  
    } catch (Exception e) {
        return url;
    }
}

private void addHistory(String url, String title) {
    if (url == null || url.isEmpty() || url.equals(START_PAGE))
        return;
    String lowerUrl = url.toLowerCase(java.util.Locale.ROOT);
    if (lowerUrl.startsWith("about:") || lowerUrl.startsWith("data:") || lowerUrl.startsWith("javascript:")
            || lowerUrl.startsWith("blob:") || lowerUrl.startsWith("chrome-error:")
            || lowerUrl.startsWith("file:///android_asset") || lowerUrl.startsWith("file:///android_res")
            || lowerUrl.startsWith("https://appassets.androidplatform.net/assets/")
            || lowerUrl.startsWith("https://appassets.androidplatform.net/res/"))
        return;
    String normalizedUrl = normalizeUrl(url);
    if (!historyItems.isEmpty() && normalizeUrl(historyItems.get(historyItems.size() - 1).getUrl()).equals(normalizedUrl))
        return;
    historyItems.add(new HistoryItem(title == null ? "" : title, url, System.currentTimeMillis()));
    while (historyItems.size() > MAX_HISTORY_SIZE) {
        historyItems.remove(0);
    }
}
    public void exitFullScreen() {
        if (!fullScreenActive) {
            return;
        }
        BrowserView target = fullScreenView;
        leaveFullScreen();
        if (target != null) {
            try {
                target.exitFullScreen();
            } catch (Exception ignored) {
            }
        }
    }

    private void copyLink(String link) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("リンク", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(SecretActivity.this, "リンクをコピーしました", Toast.LENGTH_SHORT).show();
    }

    private Bitmap fetchFavicon(String bookmarkUrl) {
        try {
            URL urlObj = new URL(bookmarkUrl);
            String protocol = urlObj.getProtocol();
            String host = urlObj.getHost();
            String faviconUrl = protocol + "://" + host + "/favicon.ico";
            HttpURLConnection connection = (HttpURLConnection) new URL(faviconUrl).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(is);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {
        private final List<Bookmark> items;
        private final boolean managementMode;
        private final AlertDialog dialog;
        public BookmarkAdapter(List<Bookmark> items, boolean managementMode, AlertDialog dialog) {
            this.items = items;
            this.managementMode = managementMode;
            this.dialog = dialog;
        }
        @Override
        public BookmarkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
            return new BookmarkViewHolder(view);
        }
        @Override
        public void onBindViewHolder(BookmarkViewHolder holder, int position) {
            Bookmark bm = items.get(position);
            holder.title.setText(bm.getTitle());
            holder.url.setText(bm.getUrl());
            Bitmap icon = faviconCache.get(bm.getUrl());
            if (icon != null) {
                holder.favicon.setImageBitmap(icon);
            } else {
                holder.favicon.setImageResource(R.drawable.transparent_vector);
            }
            holder.itemView.setOnClickListener(v -> {
                getCurrentWebView().loadUrl(bm.getUrl());
                dialog.dismiss();
            });
            if (managementMode) {
            holder.itemView.setOnLongClickListener(v -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return true;
                String[] options = {"編集", "削除"};
                new MaterialAlertDialogBuilder(SecretActivity.this)
                        .setTitle("操作を選択")
                        .setItems(options, (dialogInterface, which) -> {
                            if (which == 0) {
                                showEditBookmarkDialog(currentPosition, this);
                            } else if (which == 1) {
                                items.remove(currentPosition);
                                notifyItemRemoved(currentPosition);
                                saveBookmarks();
                                Toast.makeText(SecretActivity.this, "削除しました", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
                return true;
            });
        }
    }
        @Override
        public int getItemCount() { return items.size(); }
        class BookmarkViewHolder extends RecyclerView.ViewHolder {
            ImageView favicon;
            TextView title;
            TextView url;
            public BookmarkViewHolder(View itemView) {
                super(itemView);
                favicon = itemView.findViewById(R.id.bookmarkFavicon);
                title = itemView.findViewById(R.id.bookmarkTitle);
                url = itemView.findViewById(R.id.bookmarkUrl);
            }
        }
    }
}
