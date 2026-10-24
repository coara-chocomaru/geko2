package com.coara.browser2;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.KeyguardManager;
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
import android.os.SystemClock;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
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
import com.coara.browser2.gecko.BrowserSettings;
import com.coara.browser2.gecko.BrowserEngine;
import com.coara.browser2.gecko.NavHistory;
import com.coara.browser2.gecko.DownloadRequest;
import com.coara.browser2.gecko.PageAgent;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.coara.browser2.util.BrowserUrlRouter;
import com.coara.browser2.util.PictureInPictureSettings;
import com.coara.browser2.util.BrowserAssetLoader;
import com.coara.browser2.model.Bookmark;
import com.google.android.material.snackbar.Snackbar;
import com.coara.browser2.util.AdBlockEngine;
import com.coara.browser2.util.BookmarkDialog;
import com.coara.browser2.util.BookmarkStore;
import com.coara.browser2.util.PageTranslator;
import com.coara.browser2.util.TranslateSettingsDialog;
import com.coara.browser2.webview.TabOverviewDialog;
import com.coara.browser2.util.TabStateStore;
import com.coara.browser2.util.SafeBrowsingSupport;
import com.coara.browser2.util.TabHistoryStore;
import com.coara.browser2.util.SwipeRefreshPolicy;
import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.CookiePolicyUi;
import com.coara.browser2.util.CookieSite;
import com.coara.browser2.util.DarkModeSwitcher;
import com.coara.browser2.util.CookieSitePolicy;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.ExternalDownloadTabTracker;
import com.coara.browser2.util.DownloadSupport;
import com.coara.browser2.util.DownloadFallbackManager;
import com.coara.browser2.util.UiThread;
import com.coara.browser2.util.HistoryDialog;
import com.coara.browser2.model.HistoryItem;
import com.coara.browser2.plugin.PluginHost;
import com.coara.browser2.plugin.PluginManagerActivity;
import com.coara.browser2.plugin.PluginManager;
import com.coara.browser2.plugin.PluginRuntime;
import com.coara.browser2.util.BackgroundWallpaper;
import com.coara.browser2.util.BrowserVisualSettings;
import com.coara.browser2.util.BrowserMenuController;
import com.coara.browser2.util.BrowserThemeManager;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements PluginHost, PluginManager.Listener, BrowserHost {

    private static final String PREF_NAME = BrowserConstants.PREF_NAME;
    private static final String KEY_CURRENT_TAB_ID = BrowserConstants.KEY_CURRENT_TAB_ID;
    private static final String KEY_NEXT_TAB_ID = "next_tab_id";
    private static final String KEY_DARK_MODE = BrowserConstants.KEY_DARK_MODE;
    private static final String KEY_BASIC_AUTH = BrowserConstants.KEY_BASIC_AUTH;
    private static final String KEY_ZOOM_ENABLED = BrowserConstants.KEY_ZOOM_ENABLED;
    private static final String KEY_JS_ENABLED = BrowserConstants.KEY_JS_ENABLED;
    private static final String KEY_IMG_BLOCK_ENABLED = BrowserConstants.KEY_IMG_BLOCK_ENABLED;
    private static final String KEY_UA_ENABLED = BrowserConstants.KEY_UA_ENABLED;
    private static final String KEY_DESKUA_ENABLED = BrowserConstants.KEY_DESKUA_ENABLED;
    private static final String KEY_PICTURE_IN_PICTURE_ENABLED = "picture_in_picture_enabled";
    private static final String KEY_TABS = BrowserConstants.KEY_TABS;
    private static final String KEY_CURRENT_TAB = BrowserConstants.KEY_CURRENT_TAB;
    private static final String KEY_BOOKMARKS = BrowserConstants.KEY_BOOKMARKS;
    private static final String KEY_HISTORY = BrowserConstants.KEY_HISTORY;
    private static final String START_PAGE = BrowserConstants.START_PAGE;
    private static final int FILE_SELECT_CODE = BrowserConstants.FILE_SELECT_CODE;
    private static final int MAX_TABS = BrowserConstants.MAX_TABS;
    private static final int MAX_HISTORY_SIZE = BrowserConstants.MAX_HISTORY_SIZE;
    private static final int MAX_TAB_HISTORY_SIZE = BrowserConstants.MAX_HISTORY_SIZE;
    private static final String SENTINEL_FILENAME = BrowserConstants.SENTINEL_FILENAME;
    public static final String EXTRA_CLEAR_HISTORY = BrowserConstants.EXTRA_CLEAR_HISTORY;
    private static final long MAX_BACKGROUND_IMAGE_BYTES = 64L * 1024L * 1024L;

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
    private MaterialButton btnNewTab;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout webViewContainer;
    private TextView tabCountTextView;
    private ImageView copyButton;
    private ImageView clearButton;
    private ImageView urlSecurityIcon;
    private android.widget.ProgressBar pageProgressBar;
    private final Runnable hidePageProgressRunnable = () -> {
        if (pageProgressBar != null && !isFinishing()) {
            pageProgressBar.setVisibility(View.INVISIBLE);
            pageProgressBar.setIndeterminate(false);
            pageProgressBar.setProgress(0);
        }
    };
    private ImageView backgroundImageView;
    private volatile int backgroundLoadToken = 0;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String> backgroundImageLauncher;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<String> permissionLauncher;
    private SharedPreferences pref;
    private final ExecutorService backgroundExecutor = UiThread.newPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));
    private final ArrayList<BrowserView> webViews = new ArrayList<>();
    private int currentTabIndex = 0;
    private int nextTabId = 0;
    private final Set<BrowserView> externalTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<HistoryItem> historyItems = Collections.synchronizedList(new ArrayList<>());
    private boolean darkModeEnabled = false;
    private boolean basicAuthEnabled = false;
    private boolean zoomEnabled = false;
    private boolean jsEnabled = false;
    private boolean imgBlockEnabled = false;
    private static final String GALAKE_UA = "DoCoMo/2.0 SH902i(c100;TB)";
    private boolean uaEnabled = false;
    private boolean deskuaEnabled = false;

    private LruCache<String, Bitmap> faviconCache;
    private static final int MAX_FAVICON_SIDE = 96;
    private static final int MAX_FAVICON_BYTES = 512 * 1024;
    private static final int MAX_CACHED_FAVICON_BYTES = 512 * 1024;
    private final Set<String> faviconMissing = Collections.synchronizedSet(new java.util.HashSet<String>());
    private final Set<String> faviconLoading = Collections.synchronizedSet(new java.util.HashSet<String>());
    private final ExecutorService faviconExecutor = Executors.newFixedThreadPool(2);
    private boolean defaultLoadsImagesAutomatically;
    private boolean defaultLoadsImagesAutomaticallyInitialized = false;
    private boolean fullScreenActive = false;
    private BrowserView fullScreenView = null;
    private int fullScreenPreviousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Map<BrowserView, Bitmap> tabSnapshots = new LinkedHashMap<>();
    private static final int MAX_MEMORY_SNAPSHOTS = 6;
    private final Map<BrowserView, Runnable> pendingSpaHistoryTasks = new HashMap<>();
    
    
    
    private final Map<BrowserView, Boolean> pullToRefreshEligibleCache = new HashMap<>();
    private final Map<BrowserView, PendingTab> pendingTabs = new HashMap<>();
    private final Set<BrowserView> staleTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Map<BrowserView, TabHistoryStore.State> tabHistoryStates = new HashMap<>();
    private final Set<BrowserView> seedAlignedTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Set<BrowserView> backNavigationTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Map<BrowserView, ArrayList<TabHistoryStore.Entry>> historyPrefix = new WeakHashMap<>();
    private final Map<BrowserView, TabHistoryStore.State> restoreSeeds = new WeakHashMap<>();
    private final Map<BrowserView, VirtualBackGuard> virtualBackGuards = new WeakHashMap<>();

    private static final class VirtualBackGuard {
        final String url;
        final long at;

        VirtualBackGuard(String url, long at) {
            this.url = url;
            this.at = at;
        }
    }
    private final Set<BrowserView> popupTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Map<BrowserView, Runnable> stateSaveTasks = new HashMap<>();
    private final ExecutorService persistExecutor = UiThread.newPool(1);
    private final Set<BrowserView> dirtyStateTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Set<BrowserView> dirtySnapshotTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private boolean historyDirty = false;
    private boolean pluginReloadPending = false;
    private boolean pluginReloadRunning = false;
    private int lastPluginRuntimeVersion = -1;
    private TabHistoryStore tabHistoryStore;
    private PluginManager pluginManager;
    private final Runnable pluginReloadRunnable = new Runnable() {
        @Override
        public void run() {
            reloadOpenTabsForPlugins();
        }
    };
    private final Runnable historySaveRunnable = new Runnable() {
        @Override
        public void run() {
            flushHistory();
        }
    };

    private static final class PendingTab {
        final int id;
        final String url;
        final String title;
        final boolean useSavedState;

        PendingTab(int id, String url, String title, boolean useSavedState) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.useSavedState = useSavedState;
        }
    }

    private final ActivityResultLauncher<Intent> launchProtectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    com.coara.browser2.util.PinLockManager.markUnlocked();
                } else {
                    moveTaskToBack(true);
                    finish();
                }
            });
    static {
    }

    private boolean redirectingCustomTabLaunch;

    protected boolean isCustomTabMode() {
        return false;
    }

    protected String getCustomTabInitialUrl() {
        return null;
    }

    protected boolean shouldRunCustomTabForIntent(Intent intent) {
        return true;
    }

    protected void onCustomTabIntent(Intent intent) {
    }

    protected void onCustomTabPageStarted(BrowserView view) {
    }

    protected void onCustomTabPageChanged(BrowserView view) {
    }

    protected void onCustomTabPageFinished(BrowserView view) {
    }

    protected void onCustomTabPageFailed(BrowserView view) {
    }

    protected void onCustomTabMessage(BrowserView view, JSONObject message) {
    }

    protected void onCustomTabScrollChanged(int x, int y) {
    }

    protected final BrowserView getCustomTabBrowserView() {
        if (currentTabIndex < 0 || currentTabIndex >= webViews.size()) {
            return null;
        }
        return webViews.get(currentTabIndex);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isCustomTabMode() && !shouldRunCustomTabForIntent(getIntent())) {
            if (!forwardUnmarkedCustomTabIntent(getIntent())) {
                redirectingCustomTabLaunch = true;
                finish();
            }
            return;
        }
        if (!isCustomTabMode() && forwardCustomTabsIntent(getIntent(), true)) {
            return;
        }
        setContentView(R.layout.activity_main);

        if (!isCustomTabMode()) {
            maybeRequestLaunchProtection();
        }

        toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        tabHistoryStore = new TabHistoryStore(getFilesDir(), MAX_TAB_HISTORY_SIZE);
        BrowserMenuController.setQuickActions(this, this::buildQuickActions);
        pluginManager = PluginManager.get(this);
        pluginManager.addListener(this);
        lastPluginRuntimeVersion = pluginManager.runtimeVersion();
        if (!isCustomTabMode()) {
            checkSentinelAndClearTabsIfNecessary();
        }
        ensureCacheSentinelExists();
        darkModeEnabled = pref.getBoolean(KEY_DARK_MODE, false);
        basicAuthEnabled = pref.getBoolean(KEY_BASIC_AUTH, false);
        zoomEnabled = pref.getBoolean(KEY_ZOOM_ENABLED, true);
        jsEnabled = pref.getBoolean(KEY_JS_ENABLED, true);
        imgBlockEnabled = pref.getBoolean(KEY_IMG_BLOCK_ENABLED, false);
        uaEnabled = pref.getBoolean(KEY_UA_ENABLED, false);
        deskuaEnabled = pref.getBoolean(KEY_DESKUA_ENABLED, false);
        final int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(512, Math.min(maxMemory / 32, 4 * 1024));
        faviconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
        if (!isCustomTabMode()) {
            loadBookmarks();
            loadHistory();
        }
        urlEditText = findViewById(R.id.urlEditText);
        urlContainer = findViewById(R.id.urlContainer);
        urlEditOriginalStrokeColor = urlContainer.getStrokeColor();
        urlEditOriginalStrokeWidth = urlContainer.getStrokeWidth();
        urlEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
        KeyboardSettings.applyToEditor(urlEditText, pref);
        faviconImageView = findViewById(R.id.favicon);
        setupBookmarkStar();
        btnGo = findViewById(R.id.btnGo);
        btnNewTab = findViewById(R.id.btnNewTab);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.white);
        webViewContainer = findViewById(R.id.webViewContainer);
        backgroundImageView = findViewById(R.id.backgroundImageView);
        tabCountTextView = findViewById(R.id.tabCountTextView);
        tabCountTextView.setOnClickListener(v -> showTabsDialog());

        if (!isCustomTabMode() && pref.contains(KEY_TABS)) {
            loadTabsState();
        } else {
            BrowserView initialWebView = createNewWebView();
            initialWebView.setTag(nextTabId);
            nextTabId++;
            webViews.add(initialWebView);
            currentTabIndex = 0;
            webViewContainer.addView(initialWebView);
            String initialUrl = isCustomTabMode() ? getCustomTabInitialUrl() : START_PAGE;
            if (initialUrl == null || initialUrl.isEmpty()) {
                initialUrl = isCustomTabMode() ? "about:blank" : START_PAGE;
            }
            if (isCustomTabMode()) {
                markExternalTab(initialWebView, initialUrl);
            }
            initialWebView.loadUrl(initialUrl);
            updatePullToRefreshState(initialWebView, initialUrl);
        }
        updateTabCount();

        if (!defaultLoadsImagesAutomaticallyInitialized && !webViews.isEmpty()) {
            defaultLoadsImagesAutomatically = webViews.get(0).getSettings().getLoadsImagesAutomatically();
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
            Toast.makeText(MainActivity.this, "URLをコピーしました", Toast.LENGTH_SHORT).show();
        });
        ViewGroup parentView = (ViewGroup) urlEditText.getParent();
        urlSecurityIcon = findViewById(R.id.urlSecurityIcon);
        pageProgressBar = findViewById(R.id.pageProgressBar);
        if (pageProgressBar != null) {
            pageProgressBar.setMax(100);
            pageProgressBar.setProgress(0);
            pageProgressBar.setVisibility(View.INVISIBLE);
        }
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

        swipeRefreshLayout.setOnChildScrollUpCallback((parent1, child) -> {
            BrowserView current = getCurrentWebView();
            if (current == null || TouchpadController.isScreenTouchBlocked()) return true;
            Boolean cached = pullToRefreshEligibleCache.get(current);
            if (cached == null) {
                
                cached = SwipeRefreshPolicy.shouldEnablePullToRefresh(current, current.getUrl());
                pullToRefreshEligibleCache.put(current, cached);
            }
            return SwipeRefreshPolicy.shouldBlockPullToRefresh(current, cached);
        });
        swipeRefreshLayout.setOnRefreshListener(() -> {
            BrowserView current = getCurrentWebView();
            if (current != null) current.reload();
            UiThread.postDelayed(() -> {
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 15000);
        });
            
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
        backgroundImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        saveSelectedBackgroundImage(uri);
                    }
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

        btnNewTab.setOnClickListener(v -> createNewTab());
        btnGo.setOnClickListener(v -> {
            loadUrl();
            exitUrlEditMode();
        });

        loadBackgroundImage();
        if (!isCustomTabMode()) {
            handleIntent(getIntent());
        }
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
        if (current != null && goBackInCurrentTab(current)) {
            return;
        }
        if (current != null && externalTabs.contains(current)) {
            if (isCustomTabMode()) {
                finish();
            } else {
                returnToCallingApp(current);
            }
            return;
        }
        Toast.makeText(MainActivity.this, "履歴がありません", Toast.LENGTH_SHORT).show();
         }
     });
        BrowserThemeManager.applyToActivity(this);
    }

    private void returnToCallingApp(BrowserView tab) {
        boolean lastTab = webViews.size() <= 1;
        if (externalTabs.contains(tab)) {
            closeTab(tab);
            if (!lastTab && !isFinishing()) {
                moveTaskToBack(true);
            }
            return;
        }
        moveTaskToBack(true);
    }

    private void markExternalTab(BrowserView tab, String url) {
        ExternalDownloadTabTracker.markExternal(tab, url);
        externalTabs.add(tab);
    }

    private void saveBundleToFile(Bundle bundle, String fileName) {
        TabStateStore.saveBundleToFile(getFilesDir(), bundle, fileName);
    }

    private Bundle loadBundleFromFile(String fileName) {
        return TabStateStore.loadBundleFromFile(getFilesDir(), fileName);
    }

    private com.coara.browser2.util.TabGroupStore tabGroupStore;

    private com.coara.browser2.util.TabGroupStore getTabGroupStore() {
        if (tabGroupStore == null) {
            tabGroupStore = new com.coara.browser2.util.TabGroupStore(
                    pref.getString("tab_groups_v1", ""),
                    serialized -> pref.edit().putString("tab_groups_v1", serialized).apply());
        }
        return tabGroupStore;
    }

    private void pruneTabGroups() {
        java.util.Set<Integer> live = new java.util.HashSet<>();
        synchronized (webViews) {
            for (BrowserView w : webViews) {
                int id = getTabId(w);
                if (id >= 0) {
                    live.add(id);
                }
            }
        }
        getTabGroupStore().prune(live);
    }

    private int getTabId(BrowserView view) {
        if (view == null) {
            return -1;
        }
        Object tag = view.getTag();
        return tag instanceof Integer ? (Integer) tag : -1;
    }

    private TabHistoryStore.State getTabHistoryState(BrowserView view) {
        if (view == null || tabHistoryStore == null) {
            return null;
        }
        TabHistoryStore.State state = tabHistoryStates.get(view);
        if (state == null) {
            int id = getTabId(view);
            if (id < 0) {
                return null;
            }
            state = tabHistoryStore.load(id);
            tabHistoryStates.put(view, state);
        }
        return state;
    }

    private void syncTabHistoryFromWebView(BrowserView view) {
        if (view == null) {
            return;
        }
        try {
            mergeHistoryFromEngine(view, view.copyBackForwardList(), true);
        } catch (Exception ignored) {
        }
    }

    private static String normalizeEntryUrl(String u) {
        if (u == null) {
            return "";
        }
        String t = u.trim();
        int hash = t.indexOf('#');
        if (hash >= 0) {
            t = t.substring(0, hash);
        }
        while (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private static String canonicalEntryUrl(String u) {
        if (u != null && u.startsWith("moz-extension://")) {
            int slash = u.indexOf('/', 16);
            if (slash > 0) {
                String path = u.substring(slash);
                if (path.startsWith("/pages/index.html")) {
                    return START_PAGE + path.substring("/pages/index.html".length());
                }
            }
        }
        return u;
    }

    private static TabHistoryStore.Entry canonicalEntry(TabHistoryStore.Entry e) {
        String c = canonicalEntryUrl(e.getUrl());
        return c.equals(e.getUrl()) ? e : new TabHistoryStore.Entry(c, e.getTitle());
    }

    private static List<TabHistoryStore.Entry> canonicalEntries(List<TabHistoryStore.Entry> in) {
        ArrayList<TabHistoryStore.Entry> out = new ArrayList<>(in.size());
        for (TabHistoryStore.Entry e : in) {
            out.add(canonicalEntry(e));
        }
        return out;
    }

    private static boolean isStartLike(String u) {
        return u != null && (u.startsWith(START_PAGE) || u.startsWith("data:text/html"));
    }

    private static boolean sameEntryUrl(String a, String b) {
        if (isStartLike(a) && isStartLike(b)) {
            return true;
        }
        return normalizeEntryUrl(a).equals(normalizeEntryUrl(b));
    }

    private void seedHistory(BrowserView view, TabHistoryStore.State stored) {
        if (view == null) {
            return;
        }
        virtualBackGuards.remove(view);
        seedAlignedTabs.remove(view);
        if (stored == null || stored.isEmpty()) {
            restoreSeeds.remove(view);
            historyPrefix.remove(view);
            return;
        }
        List<TabHistoryStore.Entry> all = canonicalEntries(stored.getEntries());
        int cur = Math.max(0, Math.min(stored.getCurrentIndex(), all.size() - 1));
        historyPrefix.put(view, new ArrayList<>(all.subList(0, cur)));
        restoreSeeds.put(view, new TabHistoryStore.State(all, cur));
    }

    private void resetHistoryTracking(BrowserView view) {
        if (view == null) {
            return;
        }
        historyPrefix.remove(view);
        restoreSeeds.remove(view);
        virtualBackGuards.remove(view);
        seedAlignedTabs.remove(view);
    }

    private void mergeHistoryFromEngine(BrowserView view, NavHistory h, boolean sync) {
        if (view == null || tabHistoryStore == null || h == null || h.getSize() == 0 || pendingTabs.containsKey(view)) {
            return;
        }
        final int id = getTabId(view);
        if (id < 0 || TabHistoryStore.isDead(id)) {
            return;
        }
        ArrayList<TabHistoryStore.Entry> geckoEntries = new ArrayList<>();
        int geckoCur = -1;
        for (int i = 0; i < h.getSize(); i++) {
            NavHistory.Item item = h.getItemAtIndex(i);
            if (item == null) {
                continue;
            }
            String u = item.getUrl();
            if (u.isEmpty() || u.equals("about:blank")) {
                continue;
            }
            geckoEntries.add(new TabHistoryStore.Entry(canonicalEntryUrl(u), item.getTitle()));
            if (i == h.getCurrentIndex()) {
                geckoCur = geckoEntries.size() - 1;
            }
        }
        if (geckoEntries.isEmpty()) {
            return;
        }
        if (geckoCur < 0) {
            geckoCur = geckoEntries.size() - 1;
        }
        VirtualBackGuard guard = virtualBackGuards.get(view);
        if (guard != null) {
            if (sameEntryUrl(geckoEntries.get(geckoCur).getUrl(), guard.url)
                    || SystemClock.uptimeMillis() - guard.at > 6000L) {
                virtualBackGuards.remove(view);
            } else {
                return;
            }
        }
        ArrayList<TabHistoryStore.Entry> prefix = historyPrefix.get(view);
        if (prefix == null) {
            prefix = new ArrayList<>();
        }
        TabHistoryStore.State seed = restoreSeeds.get(view);
        if (seed != null && !seed.isEmpty()) {
            List<TabHistoryStore.Entry> s = seed.getEntries();
            int off = seed.getCurrentIndex() - geckoCur;
            boolean ok = off >= 0 && off + geckoEntries.size() <= s.size();
            if (ok) {
                for (int i = 0; i < geckoEntries.size(); i++) {
                    if (!sameEntryUrl(s.get(off + i).getUrl(), geckoEntries.get(i).getUrl())) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                prefix = new ArrayList<>(s.subList(0, off));
                seedAlignedTabs.add(view);
            } else {
                restoreSeeds.remove(view);
                if (!seedAlignedTabs.contains(view)) {
                    prefix = new ArrayList<>();
                }
            }
        }
        TabHistoryStore.State prev = getTabHistoryState(view);
        ArrayList<TabHistoryStore.Entry> entries = new ArrayList<>(prefix);
        while (!entries.isEmpty() && sameEntryUrl(entries.get(entries.size() - 1).getUrl(), geckoEntries.get(0).getUrl())) {
            entries.remove(entries.size() - 1);
        }
        int realBase = entries.size();
        entries.addAll(geckoEntries);
        int cur = realBase + geckoCur;
        TabHistoryStore.State next = new TabHistoryStore.State();
        next.replace(entries, cur, MAX_TAB_HISTORY_SIZE);
        int start = Math.max(0, entries.size() - Math.max(1, MAX_TAB_HISTORY_SIZE));
        if (realBase < prefix.size()) {
            prefix = new ArrayList<>(prefix.subList(0, realBase));
        }
        if (start > 0) {
            prefix = new ArrayList<>(prefix.subList(Math.min(start, prefix.size()), prefix.size()));
        }
        historyPrefix.put(view, prefix);
        if (prev != null && prev.contentEquals(next)) {
            return;
        }
        tabHistoryStates.put(view, next);
        persistHistory(id, next, sync);
    }

    private void persistHistory(final int id, final TabHistoryStore.State state, boolean sync) {
        if (tabHistoryStore == null) {
            return;
        }
        if (persistExecutor.isShutdown()) {
            tabHistoryStore.save(id, state);
            return;
        }
        try {
            persistExecutor.execute(() -> tabHistoryStore.save(id, state));
        } catch (Exception e) {
            tabHistoryStore.save(id, state);
        }
    }

    

    private void saveTabHistoryState(BrowserView view) {
        syncTabHistoryFromWebView(view);
    }

    private void navigateBackFromError(BrowserView view) {
        if (view == null) {
            return;
        }
        if (!goBackInCurrentTab(view)) {
            view.loadUrl(START_PAGE);
        }
    }

    private boolean goBackInCurrentTab(BrowserView view) {
        if (view == null) {
            return false;
        }
        NavHistory engineHistory = view.copyBackForwardList();
        if (view.canGoBack() || (engineHistory != null && engineHistory.getCurrentIndex() > 0)) {
            backNavigationTabs.add(view);
            view.goBack();
            return true;
        }
        ArrayList<TabHistoryStore.Entry> prefix = historyPrefix.get(view);
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        TabHistoryStore.Entry previous = prefix.get(prefix.size() - 1);
        if (previous == null || previous.getUrl().isEmpty()) {
            return false;
        }
        String target = BrowserAssetLoader.normalizeLegacyAssetUrl(previous.getUrl());
        ArrayList<TabHistoryStore.Entry> remaining = new ArrayList<>(prefix.subList(0, prefix.size() - 1));
        ArrayList<TabHistoryStore.Entry> all = new ArrayList<>(remaining);
        all.add(previous);
        TabHistoryStore.State next = new TabHistoryStore.State(all, remaining.size());
        historyPrefix.put(view, remaining);
        restoreSeeds.remove(view);
        tabHistoryStates.put(view, next);
        int id = getTabId(view);
        if (id >= 0) {
            persistHistory(id, next, false);
        }
        virtualBackGuards.put(view, new VirtualBackGuard(target, SystemClock.uptimeMillis()));
        backNavigationTabs.add(view);
        try {
            view.loadUrlReplacingHistory(target);
            return true;
        } catch (Exception ignored) {
            backNavigationTabs.remove(view);
            virtualBackGuards.remove(view);
            prefix.add(previous);
            historyPrefix.put(view, prefix);
            return false;
        }
    }

    private boolean canGoBackAny(BrowserView view) {
        if (view == null) {
            return false;
        }
        if (view.canGoBack()) {
            return true;
        }
        NavHistory h = view.copyBackForwardList();
        if (h != null && h.getCurrentIndex() > 0) {
            return true;
        }
        ArrayList<TabHistoryStore.Entry> prefix = historyPrefix.get(view);
        return prefix != null && !prefix.isEmpty();
    }

    private List<BrowserMenuController.QuickAction> buildQuickActions() {
        final BrowserView cur = getCurrentWebView();
        String url = cur == null ? null : cur.getUrl();
        boolean hasPage = url != null && !url.isEmpty() && !url.equals(START_PAGE);
        List<BrowserMenuController.QuickAction> out = new ArrayList<>();
        out.add(new BrowserMenuController.QuickAction(R.drawable.ic_arrow_back, "戻る", canGoBackAny(cur),
                () -> getOnBackPressedDispatcher().onBackPressed()));
        out.add(new BrowserMenuController.QuickAction(R.drawable.ic_arrow_forward, "進む", cur != null && cur.canGoForward(),
                () -> {
                    BrowserView v = getCurrentWebView();
                    if (v != null) {
                        v.goForward();
                    }
                }));
        out.add(new BrowserMenuController.QuickAction(R.drawable.ic_refresh, "更新", cur != null,
                () -> {
                    BrowserView v = getCurrentWebView();
                    if (v != null) {
                        v.reload();
                    }
                }));
        boolean bookmarked = hasPage && isBookmarkedUrl(url);
        out.add(new BrowserMenuController.QuickAction(bookmarked ? R.drawable.ic_star_filled : R.drawable.ic_star_outline,
                "ブックマーク", hasPage,
                () -> {
                    if (toolbar != null) {
                        toolbar.getMenu().performIdentifierAction(R.id.action_add_bookmark, 0);
                    }
                }));
        final String shareUrl = url;
        out.add(new BrowserMenuController.QuickAction(R.drawable.ic_share, "共有", hasPage,
                () -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_TEXT, shareUrl);
                        startActivity(Intent.createChooser(send, "共有"));
                    } catch (Exception ignored) {
                    }
                }));
        return out;
    }

    private void schedulePluginReload() {
        pluginReloadPending = true;
        UiThread.mainHandler().removeCallbacks(pluginReloadRunnable);
        UiThread.mainHandler().postDelayed(pluginReloadRunnable, 180L);
    }

    private void reloadOpenTabsForPlugins() {
        if (!pluginReloadPending || pluginReloadRunning || isFinishing() || isDestroyed()) {
            return;
        }
        if (pluginManager == null) {
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
                    UiThread.mainHandler().removeCallbacks(pluginReloadRunnable);
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
        if (!hasWindowFocus()) {
            return;
        }
        schedulePluginReload();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String url = data.toString();
                if (BrowserUrlRouter.isWebUrl(url)) {
                    createNewExternalTab(url);
                } else {
                    BrowserUrlRouter.handleIncomingViewIntent(this, getCurrentWebView(), data);
                }
            }
            setIntent(new Intent());
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.trim().isEmpty()) {
                String extracted = extractUrlFromSharedText(sharedText.trim());
                String url = BrowserUrlRouter.normalizeUserInput(extracted);
                if (!url.isEmpty() && isSafeExternalLoadUrl(url)) {
                    createNewExternalTab(url);
                }
            }
            setIntent(new Intent());
        } else if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            String query = intent.getStringExtra(android.app.SearchManager.QUERY);
            if (query != null && !query.trim().isEmpty()) {
                String url = BrowserUrlRouter.normalizeUserInput(query.trim());
                if (!url.isEmpty() && isSafeExternalLoadUrl(url)) {
                    createNewExternalTab(url);
                }
            }
            setIntent(new Intent());
        }
    }

    private static boolean isSafeExternalLoadUrl(String url) {
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String extractUrlFromSharedText(String text) {
        try {
            java.util.regex.Matcher matcher = android.util.Patterns.WEB_URL.matcher(text);
            if (matcher.find()) {
                return text.substring(matcher.start(), matcher.end());
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    private boolean forwardUnmarkedCustomTabIntent(Intent source) {
        android.net.Uri data = source == null ? null : source.getData();
        if (data == null || !BrowserUrlRouter.isWebUrl(data.toString())) {
            return false;
        }
        Intent target = new Intent(this, MainActivity.class);
        target.setAction(Intent.ACTION_VIEW);
        target.setData(data);
        int safeFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY;
        target.setFlags(source.getFlags() & safeFlags);
        try {
            redirectingCustomTabLaunch = true;
            startActivity(target);
            finish();
            return true;
        } catch (RuntimeException error) {
            redirectingCustomTabLaunch = false;
            return false;
        }
    }

    private boolean forwardCustomTabsIntent(Intent source, boolean finishCurrentActivity) {
        if (source == null || !Intent.ACTION_VIEW.equals(source.getAction()) || source.getData() == null) {
            return false;
        }
        boolean hasCustomTabsMarker = false;
        try {
            if (source.hasExtra(CustomTabsIntent.EXTRA_SESSION)) {
                hasCustomTabsMarker = true;
            } else if (source.getExtras() != null) {
                for (String key : source.getExtras().keySet()) {
                    if (key != null && (key.startsWith("android.support.customtabs.extra.")
                            || key.startsWith("androidx.browser.customtabs.extra."))) {
                        hasCustomTabsMarker = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (!hasCustomTabsMarker) {
            return false;
        }
        Intent target = new Intent(this, CustomTabActivity.class);
        target.setAction(Intent.ACTION_VIEW);
        target.setData(source.getData());
        try {
            if (source.getExtras() != null) {
                target.putExtras(source.getExtras());
            }
        } catch (RuntimeException ignored) {
        }
        int safeFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY;
        target.setFlags(source.getFlags() & safeFlags);
        try {
            redirectingCustomTabLaunch = finishCurrentActivity;
            startActivity(target);
            if (finishCurrentActivity) {
                finish();
            }
            return true;
        } catch (RuntimeException error) {
            redirectingCustomTabLaunch = false;
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isCustomTabMode()) {
            if (!shouldRunCustomTabForIntent(intent)) {
                if (!forwardUnmarkedCustomTabIntent(intent)) {
                    redirectingCustomTabLaunch = true;
                    finish();
                }
                return;
            }
            onCustomTabIntent(intent);
            return;
        }
        if (forwardCustomTabsIntent(intent, false)) {
            return;
        }
        handleIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (com.coara.browser2.gecko.BrowserEngine.isInitialized()) {
            com.coara.browser2.gecko.BrowserEngine.get()
                    .handleWebAuthnActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        if (redirectingCustomTabLaunch) {
            super.onResume();
            return;
        }
        BrowserEngine.get().getAgent().setThirdPartyCookieListener(this::onThirdPartyCookieFromAgent);
        if (networkStateGuard == null) {
            networkStateGuard = new NetworkStateGuard(this, () -> {
                synchronized (webViews) {
                    return new ArrayList<>(webViews);
                }
            });
        }
        networkStateGuard.start();
        super.onResume();
        com.coara.browser2.fx.FxExtensionManager.get().setForeground(this);
        com.coara.browser2.fx.FxExtensionManager.get().setTabHost(fxTabHost);
        BrowserThemeManager.applyToActivity(this);
        BrowserMenuController.refresh(this);
        TouchpadController.setCurrentWebView(getCurrentWebView());
        if (pluginManager != null) {
            pluginManager.reloadIfChanged();
            int runtimeVersion = pluginManager.runtimeVersion();
            if (runtimeVersion != lastPluginRuntimeVersion) {
                lastPluginRuntimeVersion = runtimeVersion;
                pluginReloadPending = true;
            }
        }
        refreshThirdPartyCookiePolicy();
        resumeAllWebViewTimers();
        resumeCurrentWebView();
        if (urlEditMode) {
            KeyboardSettings.onResume(this, urlEditText, pref);
        } else {
            KeyboardSettings.onResumeWebView(this, getCurrentWebView(), pref);
        }
        if (pluginReloadPending) {
            schedulePluginReload();
        }
        applyBrowserThemeToAllWebViews();
        if (!BackgroundWallpaper.isReady() && BrowserVisualSettings.getBackgroundPath(pref) != null && BrowserVisualSettings.isTransparencyEnabled(pref)) {
            loadBackgroundImage();
        }
        TouchpadOverlayController.onHostResume(this, pref);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (redirectingCustomTabLaunch) {
            return;
        }
        if (hasFocus) {
            BrowserMenuController.refresh(this);
            if (pluginReloadPending) {
                schedulePluginReload();
            }
        }
        if (hasFocus && KeyboardSettings.isAlwaysVisible(pref) && !urlEditMode && !webViews.isEmpty()) {
            KeyboardSettings.showKeyboardForWebView(this, getCurrentWebView(), pref);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (redirectingCustomTabLaunch || !isPictureInPictureEnabled() || isFinishing()) {
            return;
        }
        BrowserView current = getCurrentWebView();
        if (current != null && current.isMediaPlaying()) {
            current.requestPictureInPicture();
        }
    }

    @Override
    protected void onPause() {
        if (redirectingCustomTabLaunch) {
            super.onPause();
            return;
        }
        BrowserEngine.get().getAgent().setThirdPartyCookieListener(null);
        if (networkStateGuard != null) {
            networkStateGuard.stop();
        }
        TouchpadOverlayController.onHostPause(this);
        PluginRuntime.onHostPause(this);
        com.coara.browser2.fx.FxExtensionManager.get().clearForeground(this);
        pauseCurrentWebView();
        pauseAllWebViewTimers();
        flushHistory();
        saveTabsState();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (redirectingCustomTabLaunch) {
            return;
        }
        if (deskuaEnabled) {
            BrowserEngine.get().updateDesktopViewport(this);
        }
        TouchpadOverlayController.onConfigurationChanged(this, pref);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (redirectingCustomTabLaunch) {
            return;
        }
        trimBrowserMemory(level);
    }

    @Override
    protected void onDestroy() {
        UiThread.mainHandler().removeCallbacks(hidePageProgressRunnable);
        if (redirectingCustomTabLaunch) {
            super.onDestroy();
            return;
        }
        com.coara.browser2.fx.FxExtensionManager.get().clearTabHost(fxTabHost);
        PageTranslator.releaseForViews(new ArrayList<>(webViews));
        if (findController != null) {
            findController.release();
        }
        BrowserMenuController.restore(this);
        TouchpadOverlayController.hide(this);
        BasicAuthManager.cancelPendingForActivity(MainActivity.this);
        UiThread.mainHandler().removeCallbacks(historySaveRunnable);
        UiThread.mainHandler().removeCallbacks(pluginReloadRunnable);
        flushHistory();
        saveTabsState();
        if (pluginManager != null) {
            pluginManager.removeListener(this);
        }
        pendingSpaHistoryTasks.clear();
        if (!webViews.isEmpty() && currentTabIndex >= 0 && currentTabIndex < webViews.size()) {
            TouchpadController.clearCurrentWebView(webViews.get(currentTabIndex));
        }
        releaseAllWebViews(!isChangingConfigurations());
        clearBackgroundBitmap();
        backgroundExecutor.shutdown();
        faviconExecutor.shutdown();
        persistExecutor.shutdown();
        stateSaveTasks.clear();
        BrowserThemeManager.release(this);
        super.onDestroy();
    }

    private void captureSnapshotIfDirty() {
        if (webViews.isEmpty() || currentTabIndex < 0 || currentTabIndex >= webViews.size()) {
            return;
        }
        BrowserView current = webViews.get(currentTabIndex);
        if (dirtyStateTabs.contains(current)) {
            captureTabSnapshot(current);
        }
    }

    private void ensureTabLoaded(BrowserView webView) {
        if (webView == null) {
            return;
        }
        final PendingTab pending = pendingTabs.remove(webView);
        if (pending == null) {
            return;
        }
        try {
            restoreCookiesForTab(pending.id, pending.url);
        } catch (Exception ignored) {
        }
        TabHistoryStore.State historyState = tabHistoryStates.get(webView);
        if (historyState == null) {
            historyState = tabHistoryStore != null ? tabHistoryStore.load(pending.id) : new TabHistoryStore.State();
            tabHistoryStates.put(webView, historyState);
        }
        TabHistoryStore.Entry current = historyState.getCurrent();
        final String target = current != null ? current.getUrl() : pending.url;
        Bundle state = pending.useSavedState ? loadBundleFromFile("tab_state_" + pending.id + ".dat") : null;
        boolean sessionRestored = false;
        if (state != null) {
            String marker = BrowserView.savedStateUrl(state);
            if (marker != null && (current == null || current.getUrl().equals(marker))) {
                try {
                    sessionRestored = webView.restoreState(state);
                } catch (Exception ignored) {
                    sessionRestored = false;
                }
            }
        }
        if (sessionRestored) {
            seedHistory(webView, historyState);
            final BrowserView guarded = webView;
            UiThread.mainHandler().postDelayed(() -> {
                if (!webViews.contains(guarded) || pendingTabs.containsKey(guarded) || target == null || target.isEmpty()) {
                    return;
                }
                String now = guarded.getUrl();
                if (now == null || now.isEmpty() || now.equals("about:blank")) {
                    seedHistory(guarded, tabHistoryStates.get(guarded));
                    guarded.loadUrl(target);
                }
            }, 3500L);
            return;
        }
        seedHistory(webView, historyState);
        if (target != null && !target.isEmpty()) {
            try {
                webView.loadUrl(target);
            } catch (Exception ignored) {
            }
        }
    }

    private Bitmap loadSnapshotIfNeeded(BrowserView webView) {
        if (webView == null) {
            return null;
        }
        Bitmap existing = tabSnapshots.get(webView);
        if (existing != null && !existing.isRecycled()) {
            return existing;
        }
        Object tag = webView.getTag();
        if (!(tag instanceof Integer)) {
            return null;
        }
        File snapFile = new File(getFilesDir(), "tab_snapshot_" + tag + ".png");
        if (!snapFile.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options snapOptions = new BitmapFactory.Options();
            snapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bm = BitmapFactory.decodeFile(snapFile.getAbsolutePath(), snapOptions);
            if (bm != null) {
                tabSnapshots.put(webView, bm);
                trimSnapshotMemory(webView);
            }
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    private void scheduleHistorySave() {
        historyDirty = true;
        UiThread.mainHandler().removeCallbacks(historySaveRunnable);
        UiThread.mainHandler().postDelayed(historySaveRunnable, 2000L);
    }

    private void flushHistory() {
        UiThread.mainHandler().removeCallbacks(historySaveRunnable);
        if (historyDirty) {
            historyDirty = false;
            saveHistory();
        }
    }

    private void pauseCurrentWebView() {
        BrowserView current = null;
        try {
            if (!webViews.isEmpty() && currentTabIndex >= 0 && currentTabIndex < webViews.size()) {
                current = webViews.get(currentTabIndex);
            }
        } catch (Exception ignored) {
        }
        if (current != null) {
            try {
                current.onPause();
            } catch (Exception ignored) {
            }
        }
    }

    private void resumeCurrentWebView() {
        BrowserView current = null;
        try {
            if (!webViews.isEmpty() && currentTabIndex >= 0 && currentTabIndex < webViews.size()) {
                current = webViews.get(currentTabIndex);
            }
        } catch (Exception ignored) {
        }
        if (current != null) {
            try {
                current.onResume();
            } catch (Exception ignored) {
            }
        }
    }


    private void pauseAllWebViewTimers() {
        synchronized (webViews) {
            for (BrowserView webView : new ArrayList<>(webViews)) {
                if (webView == null) continue;
                try {
                    webView.pauseTimers();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void resumeAllWebViewTimers() {
        synchronized (webViews) {
            for (BrowserView webView : new ArrayList<>(webViews)) {
                if (webView == null) continue;
                try {
                    webView.resumeTimers();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void releaseAllWebViews(boolean preserveBackgroundMedia) {
        synchronized (webViews) {
            ArrayList<BrowserView> tabsToRelease = new ArrayList<>(webViews);
            webViews.clear();
            webViewContainer.removeAllViews();
            for (BrowserView tab : tabsToRelease) {
                clearPendingSpaHistory(tab);
                pullToRefreshEligibleCache.remove(tab);
                pendingTabs.remove(tab);
                tabHistoryStates.remove(tab);
                backNavigationTabs.remove(tab);
                boolean preserve = preserveBackgroundMedia && tab.shouldPreserveForBackgroundPlayback();
                if (preserve) {
                    try {
                        tab.destroyPreservingBackgroundPlayback();
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                try {
                    tab.stopLoading();
                } catch (Exception ignored) {
                }
                try {
                    tab.onPause();
                } catch (Exception ignored) {
                }
                try {
                    tab.destroy();
                } catch (Exception ignored) {
                }
            }
            clearTabSnapshots();
        }
    }

    private void clearTabSnapshots() {
        for (Bitmap bitmap : tabSnapshots.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                try {
                    bitmap.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        tabSnapshots.clear();
    }

    @SuppressWarnings("deprecation")
    private void trimBrowserMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            if (faviconCache != null) {
                try {
                    faviconCache.trimToSize(Math.max(1, faviconCache.maxSize() / 2));
                } catch (Exception ignored) {
                }
            }
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                if (faviconCache != null) {
                    try {
                        faviconCache.evictAll();
                    } catch (Exception ignored) {
                    }
                }
                clearTabSnapshots();
                clearBackgroundBitmap();
            }
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            clearTabSnapshots();
        }
    }

    

    private void saveTabStateBundle(BrowserView webView, int id) {
        saveTabStateBundle(webView, id, true);
    }

    private void saveTabStateBundle(BrowserView webView, int id, boolean sync) {
        if (webView == null || id < 0 || TabHistoryStore.isDead(id)) {
            return;
        }
        try {
            final BrowserView.StateSource source = webView.captureStateSource();
            dirtyStateTabs.remove(webView);
            if (source == null) {
                return;
            }
            final String name = "tab_state_" + id + ".dat";
            final int tabId = id;
            Runnable write = () -> {
                if (TabHistoryStore.isDead(tabId)) {
                    return;
                }
                Bundle state = source.toBundle();
                if (!state.isEmpty()) {
                    saveBundleToFile(state, name);
                }
            };
            if (persistExecutor.isShutdown()) {
                write.run();
            } else {
                persistExecutor.execute(write);
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleTabStateSave(final BrowserView view) {
        if (view == null || pendingTabs.containsKey(view)) {
            return;
        }
        dirtyStateTabs.add(view);
        if (stateSaveTasks.containsKey(view)) {
            return;
        }
        Runnable task = () -> {
            stateSaveTasks.remove(view);
            if (!webViews.contains(view) || pendingTabs.containsKey(view)) {
                return;
            }
            int id = getTabId(view);
            if (id >= 0) {
                saveTabStateBundle(view, id, false);
            }
        };
        stateSaveTasks.put(view, task);
        UiThread.mainHandler().postDelayed(task, 2500L);
    }

    private void saveTabsState() {
        if (isCustomTabMode()) {
            return;
        }
        synchronized (webViews) {
            if (webViews.isEmpty() || tabHistoryStore == null) {
                return;
            }
            JSONArray tabsArray = new JSONArray();
            BrowserView currentTab = (currentTabIndex >= 0 && currentTabIndex < webViews.size())
                    ? webViews.get(currentTabIndex) : null;
            int currentTabId = -1;
            for (BrowserView webView : webViews) {
                if (webView == null) {
                    continue;
                }
                Object tag = webView.getTag();
                int id;
                if (tag instanceof Integer) {
                    id = (Integer) tag;
                } else {
                    id = nextTabId++;
                    webView.setTag(id);
                }
                if (externalTabs.contains(webView)) {
                    if (tabHistoryStore != null) {
                        tabHistoryStore.delete(id);
                    }
                    safeDeleteFile(new File(getFilesDir(), "tab_state_" + id + ".dat"));
                    safeDeleteFile(new File(getFilesDir(), "tab_snapshot_" + id + ".png"));
                    dirtyStateTabs.remove(webView);
                    dirtySnapshotTabs.remove(webView);
                    continue;
                }
                if (webView == currentTab) {
                    currentTabId = id;
                }
                PendingTab pending = pendingTabs.get(webView);
                String url;
                String title;
                if (pending != null) {
                    url = pending.url;
                    title = pending.title;
                    TabHistoryStore.State historyState = tabHistoryStates.get(webView);
                    if (historyState != null) {
                        tabHistoryStore.save(id, historyState);
                    }
                } else {
                    url = webView.getUrl();
                    title = webView.getTitle();
                    saveTabHistoryState(webView);
                    if (webView == currentTab || dirtyStateTabs.remove(webView)) {
                        saveTabStateBundle(webView, id);
                    }
                }
                if (url == null) {
                    url = "";
                }
                if (title == null) {
                    title = "";
                }
                JSONObject tabObj = new JSONObject();
                try {
                    tabObj.put("id", id);
                    tabObj.put("url", url);
                    tabObj.put("title", title);
                } catch (JSONException ignored) {
                }
                tabsArray.put(tabObj);

                if (dirtySnapshotTabs.remove(webView)) {
                    final Bitmap finalSnap = tabSnapshots.get(webView);
                    if (finalSnap != null && !finalSnap.isRecycled()) {
                        final int finalIdForSnap = id;
                        backgroundExecutor.execute(() -> {
                            try {
                                if (finalSnap.isRecycled()) {
                                    return;
                                }
                                if (TabHistoryStore.isDead(finalIdForSnap)) {
                                    return;
                                }
                                File outFile = new File(getFilesDir(), "tab_snapshot_" + finalIdForSnap + ".png");
                                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                    finalSnap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
                                    fos.flush();
                                }
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
            }
            if (currentTabId < 0 && !webViews.isEmpty()) {
                BrowserView current = getCurrentWebView();
                currentTabId = getTabId(current);
                if (currentTabId < 0) {
                    currentTabId = nextTabId++;
                    current.setTag(currentTabId);
                }
            }
            pref.edit()
                    .putString(KEY_TABS, tabsArray.toString())
                    .putInt(KEY_CURRENT_TAB_ID, currentTabId)
                    .putInt(KEY_NEXT_TAB_ID, nextTabId)
                    .apply();
        }
    }


    private void loadTabsState() {
        synchronized (webViews) {
            String tabsJsonStr = pref.getString(KEY_TABS, "[]");
            int currentTabId = pref.getInt(KEY_CURRENT_TAB_ID, -1);
            try {
                ArrayList<BrowserView> oldTabs = new ArrayList<>(webViews);
                webViews.clear();
                webViewContainer.removeAllViews();
                for (BrowserView old : oldTabs) {
                    Bitmap bm = tabSnapshots.remove(old);
                    if (bm != null && !bm.isRecycled()) {
                        try {
                            bm.recycle();
                        } catch (Exception ignored) {
                        }
                    }
                    pullToRefreshEligibleCache.remove(old);
                    pendingTabs.remove(old);
                    tabHistoryStates.remove(old);
                    backNavigationTabs.remove(old);
                    try {
                        old.stopLoading();
                        old.destroy();
                    } catch (Exception ignored) {
                    }
                }

                JSONArray tabsArray = new JSONArray(tabsJsonStr);
                int maxId = -1;
                for (int i = 0; i < tabsArray.length(); i++) {
                    JSONObject tabObj = tabsArray.getJSONObject(i);
                    int id = tabObj.getInt("id");
                    String url = BrowserAssetLoader.normalizeLegacyAssetUrl(tabObj.optString("url", START_PAGE));
                    String title = tabObj.optString("title", "");
                    BrowserView webView = createNewWebView();
                    webView.setTag(id);
                    webViews.add(webView);
                    if (id > maxId) {
                        maxId = id;
                    }
                    TabHistoryStore.State historyState = tabHistoryStore != null
                            ? tabHistoryStore.load(id) : new TabHistoryStore.State();
                    tabHistoryStates.put(webView, historyState);
                    pendingTabs.put(webView, new PendingTab(id, url, title, true));
                    webView.onPause();
                }
                nextTabId = Math.max(Math.max(0, maxId + 1), pref.getInt(KEY_NEXT_TAB_ID, 0));
                purgeOrphanTabFiles(tabsArray);
                if (webViews.isEmpty()) {
                    BrowserView initialWebView = createNewWebView();
                    initialWebView.setTag(nextTabId);
                    nextTabId++;
                    webViews.add(initialWebView);
                    currentTabIndex = 0;
                    webViewContainer.addView(initialWebView);
                    initialWebView.loadUrl(START_PAGE);
                    updatePullToRefreshState(initialWebView, START_PAGE);
                } else {
                    currentTabIndex = 0;
                    boolean found = false;
                    for (int i = 0; i < webViews.size(); i++) {
                        if (getTabId(webViews.get(i)) == currentTabId) {
                            currentTabIndex = i;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        currentTabIndex = 0;
                    }
                    BrowserView current = getCurrentWebView();
                    ensureTabLoaded(current);
                    webViewContainer.addView(current);
                    try {
                        current.onResume();
                    } catch (Exception ignored) {
                    }
                    updatePullToRefreshState(current, current.getUrl());
                }
            } catch (JSONException e) {
                e.printStackTrace();
                tabHistoryStates.clear();
                historyPrefix.clear();
                restoreSeeds.clear();
                pendingTabs.clear();
                BrowserView initialWebView = createNewWebView();
                initialWebView.setTag(nextTabId);
                nextTabId++;
                webViews.clear();
                webViews.add(initialWebView);
                currentTabIndex = 0;
                webViewContainer.removeAllViews();
                webViewContainer.addView(initialWebView);
                initialWebView.loadUrl(START_PAGE);
            }
            pruneTabGroups();
            updateTabCount();
        }
    }


    private void purgeOrphanTabFiles(JSONArray tabsArray) {
        final Set<String> live = new java.util.HashSet<>();
        for (int i = 0; i < tabsArray.length(); i++) {
            JSONObject o = tabsArray.optJSONObject(i);
            if (o != null) {
                live.add(String.valueOf(o.optInt("id", -1)));
            }
        }
        final File dir = getFilesDir();
        final int limit = nextTabId;
        backgroundExecutor.execute(() -> {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                String n = f.getName();
                String id = null;
                if (n.startsWith("tab_state_") && n.endsWith(".dat")) {
                    id = n.substring(10, n.length() - 4);
                } else if (n.startsWith("tab_snapshot_") && n.endsWith(".png")) {
                    id = n.substring(13, n.length() - 4);
                } else if (n.startsWith("tab_history_") && n.endsWith(".json")) {
                    id = n.substring(12, n.length() - 5);
                }
                if (id != null && !live.contains(id)) {
                    try {
                        if (Integer.parseInt(id) < limit) {
                            safeDeleteFile(f);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });
    }

    private void updateTabCount() {
        if (tabCountTextView != null) {
            tabCountTextView.setText(String.valueOf(webViews.size()));
        }
    }
    private void checkSentinelAndClearTabsIfNecessary() {
        File filesDir = getFilesDir();
        File sentinel = new File(filesDir, SENTINEL_FILENAME);
        if (!sentinel.exists()) {
            try {
                sentinel.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            SharedPreferences.Editor editor = pref.edit();
            editor.remove(KEY_TABS);
            editor.remove(KEY_CURRENT_TAB_ID);
            editor.apply();
            if (tabHistoryStore != null) {
                tabHistoryStore.deleteAll();
            }
            webViews.clear();
        }
    }
    private void ensureCacheSentinelExists() {
        File filesDir = getFilesDir();
        File sentinel = new File(filesDir, SENTINEL_FILENAME);
        if (!sentinel.exists()) {
            try {
                sentinel.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        File cacheDir = getCacheDir();
        File cacheSentinel = new File(cacheDir, SENTINEL_FILENAME);
        if (!cacheSentinel.exists()) {
            try {
                cacheSentinel.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private boolean cookiePromptShowing;
    private static final java.util.Map<String, Boolean> COOKIE_PROMPTED_SITES = Collections.synchronizedMap(
            new java.util.HashMap<String, Boolean>());

    @Override
    public void requestStorageAccess(BrowserView view, String topUrlHint, String frameOrigin,
                                     ValueCallback<Boolean> callback) {
        cookieGate(view, topUrlHint, frameOrigin, true, callback);
    }

    private void onThirdPartyCookieFromAgent(String topUrl, String requestUrl) {
        BrowserView current = getCurrentWebView();
        if (current == null || isFinishing() || isDestroyed() || pendingTabs.containsKey(current)) {
            return;
        }
        String currentUrl = current.getUrl();
        if (!BrowserUrlRouter.isWebUrl(currentUrl)) {
            return;
        }
        String topSite = CookieSite.siteOf(CookieSitePolicy.hostOf(topUrl));
        if (topSite.isEmpty() || !topSite.equals(CookieSite.siteOf(CookieSitePolicy.hostOf(currentUrl)))) {
            return;
        }
        cookieGate(current, topUrl, requestUrl, false, new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
            }
        });
    }

    @Override
    public void onThirdPartyCookieBlocked(BrowserView view, String blockedUri) {
        if (view != getCurrentWebView() || pendingTabs.containsKey(view)) {
            return;
        }
        cookieGate(view, null, blockedUri, false, new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
            }
        });
    }

    private void cookieGate(final BrowserView view, String topUrlHint, final String frameOrigin,
                            final boolean storageAccess, final ValueCallback<Boolean> callback) {
        try {
            if (CookieSitePolicy.blockAll(pref)) {
                callback.onReceiveValue(false);
                return;
            }
            String live = view.getUrl();
            final String topUrl = BrowserUrlRouter.isWebUrl(live) ? live : topUrlHint;
            if (!BrowserUrlRouter.isWebUrl(topUrl)) {
                callback.onReceiveValue(false);
                return;
            }
            final String topHost = CookieSitePolicy.hostOf(topUrl);
            final String frameHost = CookieSitePolicy.hostOf(frameOrigin);
            if (topHost.isEmpty() || frameHost.isEmpty()) {
                callback.onReceiveValue(false);
                return;
            }
            final String topSite = CookieSite.siteOf(topHost);
            if (topSite.equals(CookieSite.siteOf(frameHost))) {
                callback.onReceiveValue(true);
                return;
            }
            int siteOverride = CookieSitePolicy.override(pref, topUrl);
            if (siteOverride > 0 || (siteOverride == 0 && CookieSitePolicy.globalAllowed(pref))) {
                callback.onReceiveValue(true);
                return;
            }
            if (siteOverride < 0) {
                callback.onReceiveValue(false);
                return;
            }
            if (COOKIE_PROMPTED_SITES.containsKey(topSite) || cookiePromptShowing
                    || view != getCurrentWebView() || isFinishing() || isDestroyed() || urlEditMode) {
                callback.onReceiveValue(false);
                return;
            }
            COOKIE_PROMPTED_SITES.put(topSite, Boolean.TRUE);
            cookiePromptShowing = true;
            boolean shown = CookiePolicyUi.showPrompt(this, topHost, frameHost, storageAccess,
                    new CookiePolicyUi.PromptListener() {
                        @Override
                        public void onAllow() {
                            if (CookieSitePolicy.setOverride(pref, topUrl, 1)) {
                                refreshThirdPartyCookiePolicy();
                                callback.onReceiveValue(true);
                                reloadIfSameSite(view, topSite);
                            } else {
                                Toast.makeText(MainActivity.this, "Cookie設定を保存できませんでした", Toast.LENGTH_SHORT).show();
                                callback.onReceiveValue(false);
                            }
                        }

                        @Override
                        public void onBlockAlways() {
                            if (CookieSitePolicy.setOverride(pref, topUrl, -1)) {
                                refreshThirdPartyCookiePolicy();
                                reloadIfSameSite(view, topSite);
                            } else {
                                Toast.makeText(MainActivity.this, "Cookie設定を保存できませんでした", Toast.LENGTH_SHORT).show();
                            }
                            callback.onReceiveValue(false);
                        }

                        @Override
                        public void onDeny() {
                            callback.onReceiveValue(false);
                        }

                        @Override
                        public void onClosed() {
                            cookiePromptShowing = false;
                        }
                    });
            if (!shown) {
                cookiePromptShowing = false;
                callback.onReceiveValue(false);
            }
        } catch (Exception e) {
            cookiePromptShowing = false;
            callback.onReceiveValue(false);
        }
    }

    private void reloadIfSameSite(BrowserView view, String topSite) {
        if (view == getCurrentWebView()
                && topSite.equals(CookieSite.siteOf(CookieSitePolicy.hostOf(view.getUrl())))) {
            view.reload();
        }
    }

    private int cookiePolicyStamp = Integer.MIN_VALUE;

    private void applyCookieBehaviorFor(String url) {
        int behavior;
        if (CookieSitePolicy.blockAll(pref)) {
            behavior = org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NONE;
        } else if (CookieSitePolicy.isAllowed(pref, url)) {
            behavior = org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_ALL;
        } else {
            behavior = org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY;
        }
        BrowserEngine.get().setCookieBehavior(behavior);
    }

    private void refreshThirdPartyCookiePolicy() {
        int stamp = CookieSitePolicy.revision();
        BrowserView current = getCurrentWebView();
        if (current != null) {
            PendingTab pending = pendingTabs.get(current);
            applyCookieBehaviorFor(pending != null ? pending.url : current.getUrl());
        } else {
            applyCookieBehaviorFor(null);
        }
        cookiePolicyStamp = stamp;
    }

    private void showSiteThirdPartyCookieDialog() {
        final BrowserView current = getCurrentWebView();
        if (current == null) {
            return;
        }
        final PendingTab pending = pendingTabs.get(current);
        final String url = pending != null ? pending.url : current.getUrl();
        if (CookieSitePolicy.hostOf(url).isEmpty() || !BrowserUrlRouter.isWebUrl(url)) {
            Toast.makeText(this, "このページでは設定できません", Toast.LENGTH_SHORT).show();
            return;
        }
        CookiePolicyUi.showSiteDialog(this, pref, url, () -> {
            refreshThirdPartyCookiePolicy();
            if (pending == null) {
                current.reload();
            }
        });
    }

    private BrowserView createNewWebView() {
        final BrowserView webView = new BrowserView(this);
        KeyboardSettings.configureWebView(webView, pref);
        webView.setBackgroundColorValue(darkModeEnabled ? 0xFF2A2A2E : Color.WHITE);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        BrowserSettings settings = webView.getSettings();
        settings.setZoomEnabled(zoomEnabled);
        settings.setJavaScriptEnabled(jsEnabled);
        settings.setLoadsImagesAutomatically(!imgBlockEnabled);

        if (deskuaEnabled) {
            BrowserEngine.get().updateDesktopViewport(this);
        }
        applyTabModes(webView);

        applyBrowserVisualSettings(webView);
        PluginRuntime.attach(this, webView);

        webView.setBridgeListener(new BrowserView.BridgeListener() {
            @Override
            public void onBridgeCall(BrowserView view, String function, JSONArray args) {
                handleBridgeCall(view, function, args);
            }
        });

        webView.setClient(new BrowserClient() {
            @Override
            public boolean shouldOverrideUrlLoading(BrowserView view, String url, boolean mainFrame,
                                                    boolean hasGesture, boolean redirect) {
                if (url == null || url.isEmpty()) {
                    return false;
                }
                ExternalDownloadTabTracker.onMainFrameNavigation(view, mainFrame, hasGesture, redirect, url);
                return BrowserUrlRouter.handleUrlLoading(MainActivity.this, view, url, mainFrame,
                        hasGesture, redirect);
            }

            @Override
            public void onPageStarted(BrowserView view, String url) {
                BrowserThemeManager.onPageStarted(view);
                PageTranslator.onPageStarted(view);
                ExternalDownloadTabTracker.onPageStarted(view, url);
                PluginRuntime.onPageStarted(view, url);
                AdBlockEngine.onPageStarted(view);
                if (view == getCurrentWebView()) {
                    applyCookieBehaviorFor(url);
                }
                if (findController != null) {
                    findController.onPageStarted(view);
                }
                TouchpadController.onPageStarted(view);
                clearPendingSpaHistory(view);
                if (view == getCurrentWebView()) {
                    updatePageLoadProgress(view, 0, false);
                    urlEditText.setText(url);
                    updatePullToRefreshState(view, url);
                    showCachedFavicon(url);
                    updateBookmarkStar();
                    if (isCustomTabMode()) {
                        onCustomTabPageStarted(view);
                    }
                }
            }

            @Override
            public void onProgressChanged(BrowserView view, int progress) {
                if (progress >= 100 && !view.isLoading()) {
                    updatePageLoadProgress(view, 100, true);
                } else {
                    updatePageLoadProgress(view, progress, false);
                }
            }

            @Override
            public void onPageCommitVisible(BrowserView view, String url) {
                PluginRuntime.onPageCommitVisible(view, url);
                BrowserThemeManager.applyPageTheme(view, MainActivity.this, url);
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
                updatePullToRefreshState(view, url);
                if (isCustomTabMode()) {
                    onCustomTabPageChanged(view);
                }
            }

            @Override
            public void onPageFinished(BrowserView view, String url) {
                updatePageLoadProgress(view, 100, true);
                if (url == null) {
                    url = "";
                }
                if (isCustomTabMode() && view == getCurrentWebView()) {
                    onCustomTabPageFinished(view);
                }
                ExternalDownloadTabTracker.onPageFinished(view, url);
                if (findController != null) {
                    findController.onPageFinished(view);
                }
                PluginRuntime.onPageFinished(view, url);
                TouchpadController.onPageFinished(view);
                KeyboardSettings.configureWebView(view, pref);
                PageTranslator.onPageFinished(view, true);
                applyBrowserVisualSettings(view);
                if (url.startsWith(BrowserConstants.APP_ASSET_BASE_URL)) {
                    BrowserThemeManager.applyToWebView(view, MainActivity.this);
                }
                dirtyStateTabs.add(view);
                scheduleSnapshot(view, 1200L);
                if (url.equals(START_PAGE)) {
                    faviconImageView.setVisibility(View.GONE);
                    if (view == getCurrentWebView()) {
                        urlEditText.setText("");
                    }
                } else {
                    faviconImageView.setVisibility(View.VISIBLE);
                    if (view == getCurrentWebView()) {
                        urlEditText.setText(url);
                        updatePullToRefreshState(view, url);
                        showCachedFavicon(url);
                    }
                }
                clearPendingSpaHistory(view);
                if (view == getCurrentWebView()) {
                    updateBookmarkStar();
                }
                boolean backNavigation = backNavigationTabs.remove(view);
                mergeHistoryFromEngine(view, view.copyBackForwardList(), false);
                if (!backNavigation) {
                    addHistory(url, view.getTitle());
                }
                if (swipeRefreshLayout.isRefreshing() && view == getCurrentWebView()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onLoadFailed(BrowserView view, LoadFailure failure) {
                updatePageLoadProgress(view, 100, true);
                if (isCustomTabMode() && view == getCurrentWebView()) {
                    onCustomTabPageFailed(view);
                }
                if (swipeRefreshLayout.isRefreshing() && view == getCurrentWebView()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                if (view == getCurrentWebView() && SafeBrowsingSupport.handles(failure)) {
                    UiThread.mainHandler().post(() -> SafeBrowsingSupport.show(MainActivity.this, view, failure,
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
            public void onRenderProcessGone(BrowserView view, boolean crashed) {
                try {
                    PluginRuntime.onRenderProcessGone(view, crashed);
                    String lastUrl = null;
                    try {
                        lastUrl = view.getUrl();
                    } catch (Exception ignored) {
                    }
                    final String recoverUrl = BrowserAssetLoader.normalizeLegacyAssetUrl(
                            (lastUrl == null || lastUrl.isEmpty()) ? START_PAGE : lastUrl);
                    UiThread.mainHandler().post(() -> {
                        try {
                            recoverFromRenderProcessGone(view, recoverUrl);
                        } catch (Exception ignored) {
                        }
                    });
                    Toast.makeText(MainActivity.this,
                            crashed ? "ページの表示中に問題が発生したため復旧しました" : "タブを再読み込みしました",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public BrowserView onCreateWindow(BrowserView view, String url) {
                return openPopupTab();
            }

            @Override
            public void onCloseWindow(BrowserView view) {
                UiThread.mainHandler().post(() -> {
                    if (webViews.contains(view) && webViews.size() > 1 && popupTabs.contains(view)) {
                        closeTab(view);
                    }
                });
            }

            @Override
            public void onHistoryChanged(BrowserView view, NavHistory history) {
                mergeHistoryFromEngine(view, history, false);
            }

            @Override
            public void onReceivedTitle(BrowserView view, String title) {
                if (title == null || title.isEmpty()) {
                    return;
                }
                String current = view.getUrl();
                if (current != null) {
                    updateHistoryTitle(current, title);
                }
                dirtyStateTabs.add(view);
                if (isCustomTabMode() && view == getCurrentWebView()) {
                    onCustomTabPageChanged(view);
                }
            }

            @Override
            public void onSessionStateChanged(BrowserView view) {
                scheduleTabStateSave(view);
            }

            @Override
            public void onCustomTabsAgentReady(BrowserView view) {
                if (isCustomTabMode() && view == getCurrentWebView()) {
                    UiThread.mainHandler().post(() -> onCustomTabPageChanged(view));
                }
            }

            @Override
            public void onCustomTabsMessage(BrowserView view, JSONObject message) {
                if (isCustomTabMode() && view == getCurrentWebView()) {
                    UiThread.mainHandler().post(() -> onCustomTabMessage(view, message));
                }
            }

            @Override
            public void onScrollChanged(BrowserView view, int x, int y) {
                if (view == getCurrentWebView()) {
                    if (isCustomTabMode()) {
                        onCustomTabScrollChanged(x, y);
                    }
                    scheduleSnapshot(view, 1800L);
                }
            }

            @Override
            public void onReceivedIcon(BrowserView view, Bitmap icon) {
                if (icon == null) {
                    return;
                }
                if (view == getCurrentWebView()) {
                    if (!urlEditMode) faviconImageView.setImageBitmap(icon);
                }
                storeFavicon(view.getUrl(), icon);
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
            public void onPictureInPictureRequested(BrowserView view, String placementToken) {
                if (!isPictureInPictureEnabled()) {
                    view.rejectPictureInPictureRequest();
                    return;
                }
                if (!com.coara.browser2.MediaPipActivity.launch(MainActivity.this, view, placementToken)) {
                    view.setPictureInPictureActive(false);
                }
            }

            @Override
            public void onPictureInPictureClosed(BrowserView view) {
                com.coara.browser2.MediaPipActivity.closeForSource(view);
                view.setPictureInPictureActive(false);
            }

            @Override
            public void onMediaSessionActivated(BrowserView view, org.mozilla.geckoview.MediaSession mediaSession,
                                                String title, String artist) {
                com.coara.browser2.MediaPlaybackService.onSessionActivated(
                        MainActivity.this, mediaSession, title, artist);
            }

            @Override
            public void onMediaSessionDeactivated(BrowserView view, org.mozilla.geckoview.MediaSession mediaSession) {
                com.coara.browser2.MediaPlaybackService.onSessionDeactivated(MainActivity.this, mediaSession);
            }

            @Override
            public void onMediaPlaybackChanged(BrowserView view, org.mozilla.geckoview.MediaSession mediaSession,
                                               boolean playing, boolean stopped, String title, String artist) {
                if (playing) {
                    com.coara.browser2.MediaPlaybackService.requestNotificationPermission(MainActivity.this);
                }
                com.coara.browser2.MediaPlaybackService.onPlaybackState(
                        MainActivity.this, mediaSession, playing, stopped, title, artist);
            }

            @Override
            public void onMediaMetadataChanged(BrowserView view, org.mozilla.geckoview.MediaSession mediaSession,
                                               String title, String artist) {
                com.coara.browser2.MediaPlaybackService.onMetadata(MainActivity.this, mediaSession, title, artist);
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
            Toast.makeText(MainActivity.this, "ファイル選択エラー", Toast.LENGTH_SHORT).show();
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

    private final com.coara.browser2.fx.FxExtensionManager.TabHost fxTabHost = new com.coara.browser2.fx.FxExtensionManager.TabHost() {
        @Override
        public org.mozilla.geckoview.GeckoSession openExtensionTab(boolean active) {
            BrowserView child = openPopupTab(active);
            return child == null ? null : child.createChildSession();
        }

        @Override
        public boolean closeExtensionSession(org.mozilla.geckoview.GeckoSession session) {
            BrowserView target = findViewBySession(session);
            if (target == null || webViews.size() <= 1) {
                return false;
            }
            UiThread.mainHandler().post(() -> {
                if (webViews.contains(target)) {
                    closeTab(target);
                }
            });
            return true;
        }

        @Override
        public void activateSession(org.mozilla.geckoview.GeckoSession session) {
            BrowserView target = findViewBySession(session);
            if (target == null) {
                return;
            }
            UiThread.mainHandler().post(() -> {
                int idx = webViews.indexOf(target);
                if (idx >= 0) {
                    switchToTab(idx);
                }
            });
        }

        @Override
        public void openUrl(String url) {
            openInNewTab(url);
        }
    };

    private BrowserView findViewBySession(org.mozilla.geckoview.GeckoSession session) {
        if (session == null) {
            return null;
        }
        for (BrowserView v : new ArrayList<>(webViews)) {
            if (v.getGeckoSession() == session) {
                return v;
            }
        }
        return null;
    }

    private BrowserView openPopupTab() {
        return openPopupTab(true);
    }

    private BrowserView openPopupTab(boolean activate) {
        if (webViews.size() >= MAX_TABS) {
            Toast.makeText(this, "最大タブ数に達しました", Toast.LENGTH_SHORT).show();
            return null;
        }
        BrowserView child = createNewWebView();
        child.setTag(nextTabId);
        nextTabId++;
        webViews.add(child);
        popupTabs.add(child);
        tabHistoryStates.put(child, new TabHistoryStore.State());
        updateTabCount();
        if (activate) {
            switchToTab(webViews.size() - 1);
        }
        return child;
    }

    private void openInNewTab(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (webViews.size() >= MAX_TABS) {
            Toast.makeText(this, "最大タブ数に達しました", Toast.LENGTH_SHORT).show();
            return;
        }
        createNewTab(url);
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
                actions.add(() -> openInNewTab(target));
            }
        } else {
            labels.add("リンクをダウンロード");
            actions.add(() -> handleDownload(target, null, null, null, 0));
            labels.add("リンク先を新しいタブで開く");
            actions.add(() -> openInNewTab(target));
            if (isImage) {
                labels.add("画像を保存");
                actions.add(() -> saveImage(hit.srcUri));
            }
        }
        new MaterialAlertDialogBuilder(MainActivity.this)
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
        if (com.coara.browser2.fx.FxExtensionManager.get().offerInstall(MainActivity.this, request.url, request.mimeType,
                () -> startDownloadFromRequest(source, request))) {
            return;
        }
        long downloadId = startDownloadFromRequest(source, request);
        if (downloadId > 0 && ExternalDownloadTabTracker.isExternal(source)) {
            if (ExternalDownloadTabTracker.shouldCloseWhenDownloadStarts(source, request.url, request.contentDisposition, request.mimeType)) {
                DownloadSupport.scheduleExternalTabCloseWhenStarted(MainActivity.this, source, downloadId, () -> closeTab(source));
            } else if (ExternalDownloadTabTracker.shouldCloseAfterDownload(source, request.url)) {
                DownloadSupport.scheduleExternalTabClose(MainActivity.this, source, downloadId, request.url, () -> closeTab(source));
            }
        }
    }

    private long startDownloadFromRequest(BrowserView source, DownloadRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            if (permissionLauncher != null) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                Toast.makeText(MainActivity.this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
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

    private void recoverFromRenderProcessGone(BrowserView crashed, String recoverUrl) {
        synchronized (webViews) {
            int index = webViews.indexOf(crashed);
            if (index == -1) {
                try {
                    crashed.destroy();
                } catch (Exception ignored) {
                }
                return;
            }

            Object tag = crashed.getTag();
            boolean wasCurrent = (index == currentTabIndex);
            boolean wasExternal = externalTabs.contains(crashed);
            externalTabs.remove(crashed);
            ExternalDownloadTabTracker.clear(crashed);

            Bitmap bm = tabSnapshots.remove(crashed);
            if (bm != null && !bm.isRecycled()) {
                try {
                    bm.recycle();
                } catch (Exception ignored) {
                }
            }
            pullToRefreshEligibleCache.remove(crashed);
            pendingTabs.remove(crashed);

            try {
                android.view.ViewParent parent = crashed.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(crashed);
                }
            } catch (Exception ignored) {
            }
            try {
                crashed.stopLoading();
            } catch (Exception ignored) {
            }
            try {
                crashed.destroy();
            } catch (Exception ignored) {
            }

            BrowserView replacement = createNewWebView();
            if (tag != null) {
                replacement.setTag(tag);
            }
            TabHistoryStore.State carried = tabHistoryStates.remove(crashed);
            if (carried != null) {
                tabHistoryStates.put(replacement, carried);
            }
            backNavigationTabs.remove(crashed);
            resetHistoryTracking(crashed);
            popupTabs.remove(crashed);
            webViews.set(index, replacement);
            if (wasExternal) {
                externalTabs.add(replacement);
                ExternalDownloadTabTracker.markExternal(replacement, recoverUrl);
            }
            int recoveredId = tag instanceof Integer ? (Integer) tag : -1;
            pendingTabs.put(replacement, new PendingTab(recoveredId, recoverUrl, "", false));
            if (wasCurrent) {
                webViewContainer.addView(replacement);
                ensureTabLoaded(replacement);
                urlEditText.setText(recoverUrl);
                updatePullToRefreshState(replacement, recoverUrl);
            } else {
                replacement.onPause();
            }
        }
    }


    private void showTextColorDialog() {
        final String[] labels = BrowserVisualSettings.getTextColorLabels();
        final String[] values = BrowserVisualSettings.getTextColorValues();
        final int[] selected = {BrowserVisualSettings.getTextColorIndex(pref)};
        new MaterialAlertDialogBuilder(this)
                .setTitle("テキストカラー選択")
                .setSingleChoiceItems(labels, selected[0], (dialog, which) -> selected[0] = which)
                .setPositiveButton("保存", (dialog, which) -> {
                    BrowserVisualSettings.setTextColor(pref, values[selected[0]]);
                    applyBrowserVisualSettingsToAllWebViews();
                    Toast.makeText(this, "テキストカラーを保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showFontFamilyDialog() {
        final String[] labels = BrowserVisualSettings.getFontLabels();
        final String[] values = BrowserVisualSettings.getFontValues();
        final int[] selected = {BrowserVisualSettings.getFontIndex(pref)};
        new MaterialAlertDialogBuilder(this)
                .setTitle("フォント切り替え")
                .setSingleChoiceItems(labels, selected[0], (dialog, which) -> selected[0] = which)
                .setPositiveButton("保存", (dialog, which) -> {
                    BrowserVisualSettings.setFontFamily(pref, values[selected[0]]);
                    applyBrowserVisualSettingsToAllWebViews();
                    Toast.makeText(this, "フォント設定を保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showBackgroundSettingsDialog() {
        String transparency = BrowserVisualSettings.isTransparencyEnabled(pref) ? "ON" : "OFF";
        String path = BrowserVisualSettings.getBackgroundPath(pref);
        String imageState = path == null ? "未設定" : "設定済み";
        int percent = BrowserVisualSettings.getBackgroundTransparencyPercent(pref);
        String[] items = new String[]{
                "背景透過設定  [" + transparency + "]",
                "背景透過率  [" + percent + "%]",
                "背景画像設定  [" + imageState + "]",
                "背景画像を削除"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("背景画像設定")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showBackgroundTransparencyDialog();
                    } else if (which == 1) {
                        showBackgroundTransparencyPercentDialog();
                    } else if (which == 2) {
                        if (backgroundImageLauncher != null) {
                            backgroundImageLauncher.launch("image/*");
                        }
                    } else {
                        clearBackgroundImage();
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showBackgroundTransparencyPercentDialog() {
        final int initial = BrowserVisualSettings.getBackgroundTransparencyPercent(pref);
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final TextView valueText = new TextView(this);
        valueText.setText("" + initial + "%");
        valueText.setGravity(Gravity.CENTER);
        valueText.setTextSize(18);
        container.addView(valueText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(120);
        seekBar.setProgress(Math.max(1, Math.min(120, initial <= 0 ? 1 : initial)));
        container.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint("1～120（0=デフォルト）");
        input.setText(String.valueOf(initial));
        input.setSelectAllOnFocus(true);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final int[] current = {initial};
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    current[0] = Math.max(1, Math.min(120, progress));
                    valueText.setText(current[0] + "%");
                    input.setText(String.valueOf(current[0]));
                    input.setSelection(input.length());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                int value = parsePercent(input.getText() == null ? null : input.getText().toString(), current[0]);
                current[0] = value;
                seekBar.setProgress(value);
                valueText.setText(value + "%");
                input.setText(String.valueOf(value));
                input.setSelection(input.length());
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("背景透過率")
                .setMessage("1～120%で適用範囲を細かく調整できます。100%を超える値ではページ内の背景要素まで段階的に広げます。0を入力するとデフォルトに戻します。")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    int value = parsePercent(input.getText() == null ? null : input.getText().toString(), current[0]);
                    BrowserVisualSettings.setBackgroundTransparencyPercent(pref, value);
                    if (value > 0 && !BrowserVisualSettings.isTransparencyEnabled(pref)) {
                        BrowserVisualSettings.setTransparencyEnabled(pref, true);
                    }
                    applyBrowserVisualSettingsToAllWebViews();
                    Toast.makeText(this, "背景透過率を保存しました: " + value + "%", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private int parsePercent(String text, int fallback) {
        try {
            if (text == null) return Math.max(0, Math.min(120, fallback));
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return Math.max(0, Math.min(120, fallback));
            int value = Integer.parseInt(trimmed);
            if (value <= 0) return 0;
            return Math.max(1, Math.min(120, value));
        } catch (Exception ignored) {
            return Math.max(0, Math.min(120, fallback));
        }
    }

    private void showBackgroundTransparencyDialog() {
        boolean enabled = BrowserVisualSettings.isTransparencyEnabled(pref);
        new MaterialAlertDialogBuilder(this)
                .setTitle("背景透過設定")
                .setSingleChoiceItems(new String[]{"OFF", "ON"}, enabled ? 1 : 0, (dialog, which) -> {
                    boolean next = which == 1;
                    BrowserVisualSettings.setTransparencyEnabled(pref, next);
                    applyBrowserVisualSettingsToAllWebViews();
                    updateBackgroundImageVisibility();
                    dialog.dismiss();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void applyBrowserVisualSettings(BrowserView target) {
        if (target == null || pref == null) {
            return;
        }
        BrowserVisualSettings.applyToWebView(target, pref);
    }

    private void applyBrowserVisualSettingsToAllWebViews() {
        boolean hasCustomCss = BrowserVisualSettings.hasCustomVisualCss(pref);
        synchronized (webViews) {
            for (BrowserView target : new ArrayList<>(webViews)) {
                applyBrowserVisualSettings(target);
                if (!hasCustomCss) {
                    BrowserVisualSettings.clearInjectedStyle(target);
                }
            }
        }
        updateBackgroundImageVisibility();
    }

    private void updateBackgroundImageVisibility() {
        if (backgroundImageView != null) {
            backgroundImageView.setImageDrawable(null);
            backgroundImageView.setVisibility(View.GONE);
        }
    }

    private void saveSelectedBackgroundImage(Uri uri) {
        if (uri == null) {
            return;
        }
        final File target = new File(getFilesDir(), "background_image");
        final File temporary = new File(getFilesDir(), "background_image.tmp");
        final File backup = new File(getFilesDir(), "background_image.bak");
        final int operationToken = ++backgroundLoadToken;

        backgroundExecutor.execute(() -> {
            boolean hadOld = target.exists();
            try {
                InputStream input = getContentResolver().openInputStream(uri);
                if (input == null) {
                    throw new IOException("background stream unavailable");
                }
                try (InputStream in = input;
                     FileOutputStream out = new FileOutputStream(temporary, false)) {
                    byte[] buffer = new byte[32768];
                    long total = 0L;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (read <= 0) {
                            continue;
                        }
                        total += read;
                        if (total > MAX_BACKGROUND_IMAGE_BYTES) {
                            throw new IOException("background image too large");
                        }
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    try {
                        out.getFD().sync();
                    } catch (Exception ignored) {
                    }
                }

                if (backup.exists()) {
                    backup.delete();
                }
                if (hadOld && !target.renameTo(backup)) {
                    temporary.delete();
                    throw new IOException("background backup failed");
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete();
                    if (hadOld && backup.exists()) {
                        backup.renameTo(target);
                    }
                    throw new IOException("background replace failed");
                }

                boolean decoded = BackgroundWallpaper.load(target);
                if (!decoded) {
                    target.delete();
                    if (hadOld && backup.exists()) {
                        backup.renameTo(target);
                    }
                    throw new IOException("background decode failed");
                }

                runOnUiThread(() -> {
                    if (operationToken != backgroundLoadToken || isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        return;
                    }
                    BrowserVisualSettings.setBackgroundPath(pref, target.getAbsolutePath());
                    applyBrowserVisualSettingsToAllWebViews();
                    if (backup.exists()) {
                        backup.delete();
                    }
                    Toast.makeText(this, "背景画像を保存しました", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                temporary.delete();
                if (target.exists() && !hadOld) {
                    target.delete();
                }
                if (hadOld && backup.exists() && !target.exists()) {
                    backup.renameTo(target);
                }
                runOnUiThread(() -> {
                    if (operationToken == backgroundLoadToken && !isFinishing()
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed())) {
                        Toast.makeText(this, "背景画像の保存に失敗しました", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadBackgroundImage() {
        if (pref == null) {
            return;
        }
        final String path = BrowserVisualSettings.getBackgroundPath(pref);
        final int operationToken = ++backgroundLoadToken;
        if (path == null) {
            clearBackgroundBitmap();
            applyBrowserVisualSettingsToAllWebViews();
            return;
        }

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            File backup = new File(getFilesDir(), "background_image.bak");
            if (backup.exists() && backup.isFile() && backup.renameTo(file)) {
                file = new File(path);
            }
        }
        if (!file.exists() || !file.isFile()) {
            BrowserVisualSettings.setBackgroundPath(pref, null);
            BrowserVisualSettings.setTransparencyEnabled(pref, false);
            BrowserVisualSettings.setBackgroundTransparencyPercent(pref, 0);
            clearBackgroundBitmap();
            applyBrowserVisualSettingsToAllWebViews();
            return;
        }

        final File resolvedFile = file;
        backgroundExecutor.execute(() -> {
            final boolean ok = BackgroundWallpaper.load(resolvedFile);
            runOnUiThread(() -> {
                if (operationToken != backgroundLoadToken || isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    return;
                }
                if (!ok) {
                    BackgroundWallpaper.clear();
                }
                applyBrowserVisualSettingsToAllWebViews();
            });
        });
    }



    private void clearBackgroundImage() {
        ++backgroundLoadToken;
        BrowserVisualSettings.setBackgroundPath(pref, null);
        BrowserVisualSettings.setTransparencyEnabled(pref, false);
        BrowserVisualSettings.setBackgroundTransparencyPercent(pref, 0);
        File target = new File(getFilesDir(), "background_image");
        File temporary = new File(getFilesDir(), "background_image.tmp");
        File backup = new File(getFilesDir(), "background_image.bak");
        target.delete();
        temporary.delete();
        backup.delete();
        clearBackgroundBitmap();
        applyBrowserVisualSettingsToAllWebViews();
        updateBackgroundImageVisibility();
        Toast.makeText(this, "背景画像を削除しました", Toast.LENGTH_SHORT).show();
    }

    private void clearBackgroundBitmap() {
        ++backgroundLoadToken;
        BackgroundWallpaper.clear();
        updateBackgroundImageVisibility();
    }

    private void showLaunchProtectionDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_pin_settings, null);
        com.google.android.material.switchmaterial.SwitchMaterial switchLaunchProtection =
                dialogView.findViewById(R.id.switchPinLock);
        TextView statusText = dialogView.findViewById(R.id.pinStatusText);

        boolean currentlyEnabled = com.coara.browser2.util.PinLockManager.isEnabled(this);
        switchLaunchProtection.setChecked(currentlyEnabled);

        if (currentlyEnabled && !com.coara.browser2.util.PinLockManager.isDeviceSecure(this)) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("端末の画面ロックが見つからないため、この設定は次回起動時まで一時的に無効として扱われます。");
        }

        switchLaunchProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                com.coara.browser2.util.PinLockManager.setEnabled(MainActivity.this, false);
                statusText.setVisibility(View.GONE);
                return;
            }

            if (com.coara.browser2.util.PinLockManager.isDeviceSecure(MainActivity.this)) {
                com.coara.browser2.util.PinLockManager.setEnabled(MainActivity.this, true);
                statusText.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "次回起動時から端末の画面ロックで保護されます", Toast.LENGTH_SHORT).show();
            } else {
                buttonView.setChecked(false);
                com.coara.browser2.util.PinLockManager.setEnabled(MainActivity.this, false);

                statusText.setVisibility(View.VISIBLE);
                statusText.setText("端末に画面ロック（PIN・パターン・パスワードなど）が設定されていないため有効化できません。"
                        + "先に端末のロック画面設定を行ってください。");

                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("端末の画面ロックが未設定です")
                        .setMessage("この機能を使うには、先に端末本体にPIN・パターン・パスワードのいずれかを設定してください。設定画面を開きますか？")
                        .setPositiveButton("設定を開く", (d, w) -> {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("あとで", null)
                        .show();
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("起動保護")
                .setView(dialogView)
                .setPositiveButton("閉じる", null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void maybeRequestLaunchProtection() {
        if (!com.coara.browser2.util.PinLockManager.shouldPromptLock(this)) {
            return;
        }
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager == null) {
            return;
        }
        try {
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                    "起動保護", "続行するには端末の画面ロックで認証してください");
            if (intent == null) {
                return;
            }
            launchProtectionLauncher.launch(intent);
        } catch (Exception ignored) {
        }
    }

    private void closeTab(BrowserView webView) {
        synchronized (webViews) {
            int index = webViews.indexOf(webView);
            if (index == -1) {
                return;
            }
            Runnable saveTask = stateSaveTasks.remove(webView);
            if (saveTask != null) {
                UiThread.mainHandler().removeCallbacks(saveTask);
            }
            Runnable snapshotTask = snapshotTasks.remove(webView);
            if (snapshotTask != null) {
                UiThread.mainHandler().removeCallbacks(snapshotTask);
            }
            dirtyStateTabs.remove(webView);
            dirtySnapshotTabs.remove(webView);

            Object tag = webView.getTag();
            int id = (tag instanceof Integer) ? (Integer) tag : -1;

            if (webViews.size() <= 1) {
                if (externalTabs.contains(webView)) {
                    Object lastTag = webView.getTag();
                    int lastId = lastTag instanceof Integer ? (Integer) lastTag : -1;
                    externalTabs.remove(webView);
                    ExternalDownloadTabTracker.clear(webView);
                    if (lastId >= 0 && tabHistoryStore != null) {
                        tabHistoryStore.delete(lastId);
                    }
                    if (lastId >= 0) {
                        safeDeleteFile(new File(getFilesDir(), "tab_state_" + lastId + ".dat"));
                        safeDeleteFile(new File(getFilesDir(), "tab_snapshot_" + lastId + ".png"));
                    }
                    try {
                        webView.stopLoading();
                        webView.destroy();
                    } catch (Exception ignored) {
                    }
                    webViews.clear();
                    tabHistoryStates.remove(webView);
                    backNavigationTabs.remove(webView);
                    currentTabIndex = 0;
                    updateTabCount();
                    finish();
                    return;
                }
                ExternalDownloadTabTracker.clear(webView);
                try {
                    webView.stopLoading();
                    webView.clearHistory();
                } catch (Exception ignored) {
                }
                int tabId = getTabId(webView);
                if (tabId >= 0 && tabHistoryStore != null) {
                    tabHistoryStore.delete(tabId);
                }
                tabHistoryStates.remove(webView);
                backNavigationTabs.remove(webView);
                currentTabIndex = 0;
                webView.loadUrl(START_PAGE);
                runOnUiThread(() -> {
                    try {
                        updatePullToRefreshState(webView, START_PAGE);
                        updateTabCount();
                    } catch (Exception ignored) {
                    }
                });
                return;
            }

            webViews.remove(index);
            getTabGroupStore().remove(getTabId(webView));
            externalTabs.remove(webView);
            ExternalDownloadTabTracker.clear(webView);
            Bitmap bm = tabSnapshots.remove(webView);
            if (bm != null && !bm.isRecycled()) {
                try {
                    bm.recycle();
                } catch (Exception ignored) {
                }
            }
            clearPendingSpaHistory(webView);
            pullToRefreshEligibleCache.remove(webView);
            pendingTabs.remove(webView);
            tabHistoryStates.remove(webView);
            backNavigationTabs.remove(webView);
            if (id != -1) {
                if (tabHistoryStore != null) {
                    tabHistoryStore.delete(id);
                }
                File snapFile = new File(getFilesDir(), "tab_snapshot_" + id + ".png");
                safeDeleteFile(new File(getFilesDir(), "tab_state_" + id + ".dat"));
                if (snapFile.exists()) {
                    snapFile.delete();
                }
            }
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {
            }

            if (currentTabIndex > index) {
                currentTabIndex--;
            } else if (currentTabIndex >= webViews.size()) {
                currentTabIndex = webViews.size() - 1;
            }
            if (currentTabIndex < 0) {
                currentTabIndex = 0;
            }

            runOnUiThread(() -> {
                try {
                    webViewContainer.removeAllViews();
                    BrowserView newCurrent = getCurrentWebView();
                    ensureTabLoaded(newCurrent);
                    webViewContainer.addView(newCurrent);
                    try {
                        newCurrent.onResume();
                    } catch (Exception ignored) {
                    }
                    urlEditText.setText(newCurrent.getUrl());
                    updatePullToRefreshState(newCurrent, newCurrent.getUrl());
                    updateTabCount();
                } catch (Exception ignored) {
                }
            });
        }
    }



    private long handleDownload(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        if (DownloadSupport.isBlank(url)) {
            Toast.makeText(MainActivity.this, "ダウンロードURLが見つかりません", Toast.LENGTH_SHORT).show();
            return -1;
        }
        if (DownloadSupport.isDataUrl(url)) {
            if (!DownloadSupport.saveDataUrl(MainActivity.this, url, null, mimeType)) {
                Toast.makeText(MainActivity.this, "データURLの保存に失敗しました", Toast.LENGTH_SHORT).show();
            }
            return -1;
        }
        if (DownloadSupport.isBlobUrl(url)) {
            Toast.makeText(MainActivity.this, "対応していないURLのためダウンロードできません", Toast.LENGTH_LONG).show();
            return -1;
        }
        if (!BrowserUrlRouter.isWebUrl(url)) {
            Toast.makeText(MainActivity.this, "対応していないURLのためダウンロードできません", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(MainActivity.this, "無効なデータURL", Toast.LENGTH_SHORT).show();
                    return;
                }
                String metadata = imageUrl.substring(5, commaIndex);
                boolean isBase64 = metadata.contains("base64");
                String mimeType = "image/png";
                if (metadata.contains(";")) {
                    mimeType = metadata.split(";")[0].trim();
                } else if (!metadata.isEmpty()) {
                    mimeType = metadata.trim();
                }

                byte[] imageData;
                if (isBase64) {
                    String base64Data = imageUrl.substring(commaIndex + 1);
                    imageData = Base64.decode(base64Data, Base64.DEFAULT);
                } else {
                    String dataPart = imageUrl.substring(commaIndex + 1);
                    imageData = dataPart.getBytes("UTF-8");
                }

                String fileName = buildTimestampFileName("saved_image_", mimeType);
                com.coara.browser2.util.PublicStorageWriter.Target target =
                        com.coara.browser2.util.PublicStorageWriter.openPicturesTarget(MainActivity.this, fileName, mimeType);
                try {
                    target.outputStream.write(imageData);
                    target.outputStream.flush();
                    com.coara.browser2.util.PublicStorageWriter.finish(MainActivity.this, target);
                } catch (Exception writeError) {
                    com.coara.browser2.util.PublicStorageWriter.abort(MainActivity.this, target);
                    throw writeError;
                }
                Toast.makeText(MainActivity.this,
                        "画像の保存が完了しました\n保存先: " + target.displayPath,
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this,
                        "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
                return;
            }

            final String sourceUrl = imageUrl;
            BrowserView currentWebView = getCurrentWebView();
            final String refererUrl = currentWebView != null ? currentWebView.getUrl() : null;
            final String userAgent = currentWebView != null ? currentWebView.getSettings().getUserAgentString() : null;
            Toast.makeText(MainActivity.this, "画像の保存を開始しました", Toast.LENGTH_SHORT).show();
            backgroundExecutor.execute(() -> saveRemoteImage(sourceUrl, refererUrl, userAgent));
            return;
        } catch (Exception e) {
            Toast.makeText(MainActivity.this,
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
            if (!isBlank(cookies)) {
                connection.setRequestProperty("Cookie", cookies);
            }
            String authorization = BasicAuthManager.getAuthorizationHeaderForUrl(sourceUrl, basicAuthEnabled);
            if (!isBlank(authorization)) {
                connection.setRequestProperty("Authorization", authorization);
            }
            if (!isBlank(refererUrl) && BrowserUrlRouter.isWebUrl(refererUrl)) {
                connection.setRequestProperty("Referer", refererUrl);
            }
            if (!isBlank(userAgent)) {
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
            String mime = normalizeMimeType(headerMime, sourceUrl);
            if (isBlank(mime)) {
                mime = "image/*";
            }
            String fileName = getAccurateFileName(sourceUrl, connection.getHeaderField("Content-Disposition"), mime);
            target = com.coara.browser2.util.PublicStorageWriter.openPicturesTarget(MainActivity.this, fileName, mime);
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    target.outputStream.write(buffer, 0, read);
                }
            }
            target.outputStream.flush();
            com.coara.browser2.util.PublicStorageWriter.finish(MainActivity.this, target);
            final String displayPath = target.displayPath;
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "画像の保存が完了しました\n保存先: " + displayPath, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            if (target != null) {
                com.coara.browser2.util.PublicStorageWriter.abort(MainActivity.this, target);
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "画像の保存に失敗しました", Toast.LENGTH_SHORT).show());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void toggleAdBlock(MenuItem item) {
        boolean enable = !AdBlockEngine.isEnabled();
        AdBlockEngine.setEnabled(this, enable);
        item.setChecked(enable);
        Toast.makeText(this, "AdBlock " + (enable ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        BrowserView current = getCurrentWebView();
        if (current != null) {
            current.reload();
        }
    }

    private void exportBookmarksToFile() {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "ブックマークがありません", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] formats = {"JSON (バックアップ・復元用)", "HTML (Chrome等で取り込み可)"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("エクスポート形式")
            .setItems(formats, (d, which) -> exportBookmarks(which == 1))
            .setNegativeButton("キャンセル", null)
            .show();
    }

    private void exportBookmarks(final boolean html) {
        final String content = html ? BookmarkStore.toNetscapeHtml(bookmarks, BookmarkStore.loadExtraFolders(this)) : BookmarkStore.toJson(bookmarks);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String fileName = timeStamp + (html ? "-bookmark.html" : "-bookmark.json");
        final String mime = html ? "text/html" : "application/json";
        backgroundExecutor.execute(() -> {
            com.coara.browser2.util.PublicStorageWriter.Target target = null;
            try {
                target = com.coara.browser2.util.PublicStorageWriter.openDownloadsTarget(
                        MainActivity.this, fileName, mime);
                target.outputStream.write(content.getBytes("UTF-8"));
                target.outputStream.flush();
                com.coara.browser2.util.PublicStorageWriter.finish(MainActivity.this, target);
                final String displayPath = target.displayPath;
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "ブックマークをエクスポートしました: " + displayPath, Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                com.coara.browser2.util.PublicStorageWriter.abort(MainActivity.this, target);
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "ブックマークのエクスポートに失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(MainActivity.this, "このページで使えるコマンドはありません", Toast.LENGTH_SHORT).show();
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle("拡張機能コマンド")
                .setView(content)
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void createNewExternalTab(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (webViews.size() >= MAX_TABS) {
            BrowserView removed = webViews.remove(0);
            externalTabs.remove(removed);
            ExternalDownloadTabTracker.clear(removed);
            Bitmap removedSnapshot = tabSnapshots.remove(removed);
            if (removedSnapshot != null && !removedSnapshot.isRecycled()) {
                try {
                    removedSnapshot.recycle();
                } catch (Exception ignored) {
                }
            }
            Object removedTag = removed.getTag();
            if (removedTag instanceof Integer) {
                int removedId = (Integer) removedTag;
                if (tabHistoryStore != null) {
                    tabHistoryStore.delete(removedId);
                }
                tabHistoryStates.remove(removed);
                backNavigationTabs.remove(removed);
                safeDeleteFile(new File(getFilesDir(), "tab_state_" + removedId + ".dat"));
                safeDeleteFile(new File(getFilesDir(), "tab_snapshot_" + removedId + ".png"));
            }
            try {
                removed.stopLoading();
                removed.destroy();
            } catch (Exception ignored) {
            }
            if (currentTabIndex > 0) {
                currentTabIndex--;
            } else {
                currentTabIndex = 0;
            }
        }
        BrowserView newWebView = createNewWebView();
        newWebView.setTag(nextTabId);
        nextTabId++;
        webViews.add(newWebView);
        tabHistoryStates.put(newWebView, new TabHistoryStore.State());
        updateTabCount();
        switchToTab(webViews.size() - 1);
        markExternalTab(newWebView, url);
        newWebView.loadUrl(url);
    }

    private void createNewTab() {
        if (webViews.size() >= MAX_TABS) {
            BrowserView removed = webViews.remove(0);
            Bitmap removedSnapshot = tabSnapshots.remove(removed);
            if (removedSnapshot != null && !removedSnapshot.isRecycled()) {
                try {
                    removedSnapshot.recycle();
                } catch (Exception ignored) {
                }
            }
            Object removedTag = removed.getTag();
            if (removedTag instanceof Integer) {
                int removedId = (Integer) removedTag;
                if (tabHistoryStore != null) {
                    tabHistoryStore.delete(removedId);
                }
                tabHistoryStates.remove(removed);
                backNavigationTabs.remove(removed);
                safeDeleteFile(new File(getFilesDir(), "tab_state_" + removedId + ".dat"));
                File snapFile = new File(getFilesDir(), "tab_snapshot_" + removedId + ".png");
                if (snapFile.exists()) {
                    snapFile.delete();
                }
            }
            try {
                removed.stopLoading();
                removed.destroy();
            } catch (Exception ignored) {
            }
            if (currentTabIndex > 0) {
                currentTabIndex--;
            } else {
                currentTabIndex = 0;
            }
        }
        BrowserView newWebView = createNewWebView();
        newWebView.setTag(nextTabId);
        nextTabId++;
        webViews.add(newWebView);
        tabHistoryStates.put(newWebView, new TabHistoryStore.State());
        updateTabCount();
        switchToTab(webViews.size() - 1);
        getCurrentWebView().loadUrl(START_PAGE);
    }


    private void createNewTab(String url) {
        if (webViews.size() >= MAX_TABS) {
            Toast.makeText(this, "最大タブ数に達しました", Toast.LENGTH_SHORT).show();
            return;
        }
        int openerGroup = getTabGroupStore().groupOf(getTabId(getCurrentWebView()));
        BrowserView newWebView = createNewWebView();
        newWebView.setTag(nextTabId);
        nextTabId++;
        webViews.add(newWebView);
        tabHistoryStates.put(newWebView, new TabHistoryStore.State());
        if (openerGroup >= 0) {
            getTabGroupStore().assign(getTabId(newWebView), openerGroup);
        }
        updateTabCount();
        switchToTab(webViews.size() - 1);
        newWebView.loadUrl(url);
    }


    private void switchToTab(int index) {
        if (index < 0 || index >= webViews.size() || index == currentTabIndex) return;
        BrowserView current = getCurrentWebView();
        if (current != null) {
            if (KeyboardSettings.isAlwaysVisible(pref)) {
                KeyboardSettings.hideKeyboardForWebView(this, current);
            }
            saveTabHistoryState(current);
            captureTabSnapshot(current);
            try {
                current.onPause();
            } catch (Exception ignored) {
            }
        }
        webViewContainer.removeAllViews();
        currentTabIndex = index;
        BrowserView next = getCurrentWebView();
        TouchpadController.setCurrentWebView(next);
        KeyboardSettings.configureWebView(next, pref);
        ensureTabLoaded(next);
        if (staleTabs.remove(next)) {
            reloadTabNow(next);
        }
        webViewContainer.addView(next);
        if (findController != null) {
            findController.onTabSwitched(current);
        }
        if (next.isLoading()) {
            updatePageLoadProgress(next, next.getProgress(), false);
        } else if (pageProgressBar != null) {
            UiThread.mainHandler().removeCallbacks(hidePageProgressRunnable);
            pageProgressBar.setVisibility(View.INVISIBLE);
        }
        try {
            next.onResume();
        } catch (Exception ignored) {
        }
        if (KeyboardSettings.isAlwaysVisible(pref) && !urlEditMode) {
            KeyboardSettings.showKeyboardForWebView(this, next, pref);
        }
        applyBrowserVisualSettings(next);
        BrowserThemeManager.applyToWebView(next, this);
        urlEditText.setText(next.getUrl());
        showCachedFavicon(next.getUrl());
        updatePullToRefreshState(next, next.getUrl());
        updateBookmarkStar();
        refreshThirdPartyCookiePolicy();
    }

    private BrowserView getCurrentWebView() {
        return webViews.get(currentTabIndex);
    }

    private void updatePullToRefreshState(BrowserView webView, String url) {
        if (swipeRefreshLayout == null) return;
        boolean enabled = SwipeRefreshPolicy.shouldEnablePullToRefresh(webView, url);
        if (webView != null) {
            pullToRefreshEligibleCache.put(webView, enabled);
        }
        swipeRefreshLayout.setEnabled(enabled);
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
                    addHistory(url, owner.getTitle());
                    dirtyStateTabs.add(owner);
                } finally {
                    pendingSpaHistoryTasks.remove(owner);
                }
            }
        };
        pendingSpaHistoryTasks.put(owner, task);
        UiThread.mainHandler().postDelayed(task, 180L);
    }

    private void enterUrlEditMode() {
        if (urlEditMode || urlEditText == null) return;
        if (findController != null && findController.isShowing()) {
            findController.hide();
        }
        if (!webViews.isEmpty()) {
            KeyboardSettings.hideKeyboardForWebView(this, getCurrentWebView());
        }
        urlEditMode = true;
        updateBookmarkStar();
        urlEditSavedIcon = faviconImageView != null ? faviconImageView.getDrawable() : null;
        if (faviconImageView != null) {
            faviconImageView.setImageResource(R.drawable.ic_close);
            faviconImageView.setVisibility(View.VISIBLE);
            faviconImageView.setContentDescription("URL編集を終了");
            if (BrowserThemeManager.isEnabled(this)) {
                faviconImageView.setColorFilter(BrowserThemeManager.fieldContentColor(this, Color.BLACK));
            } else {
                faviconImageView.clearColorFilter();
            }
        }
        if (btnNewTab != null) btnNewTab.setVisibility(View.GONE);
        if (tabCountTextView != null) tabCountTextView.setVisibility(View.GONE);
        if (copyButton != null) {
            if (BrowserThemeManager.isEnabled(this)) {
                copyButton.setColorFilter(BrowserThemeManager.fieldContentColor(this, Color.BLACK));
                copyButton.setAlpha(0.85f);
            } else {
                copyButton.clearColorFilter();
                copyButton.setAlpha(0.5f);
            }
            copyButton.setVisibility(View.VISIBLE);
        }
        updateClearButton();
        if (urlContainer != null) {
            urlEditOriginalStrokeColor = urlContainer.getStrokeColor();
            urlEditOriginalStrokeWidth = urlContainer.getStrokeWidth();
            urlContainer.setStrokeColor(BrowserThemeManager.accentColor(this, ContextCompat.getColor(this, R.color.colorPrimary)));
            urlContainer.setStrokeWidth(Math.max(2, urlEditOriginalStrokeWidth));
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
        updateBookmarkStar();
        if (copyButton != null) copyButton.setVisibility(View.GONE);
        if (clearButton != null) clearButton.setVisibility(View.GONE);
        if (btnNewTab != null) btnNewTab.setVisibility(View.VISIBLE);
        if (tabCountTextView != null) tabCountTextView.setVisibility(View.VISIBLE);
        if (faviconImageView != null) {
            if (urlEditSavedIcon != null) {
                faviconImageView.setImageDrawable(urlEditSavedIcon);
            } else {
                faviconImageView.setImageResource(R.drawable.transparent_vector);
            }
            faviconImageView.setVisibility(View.VISIBLE);
            faviconImageView.setContentDescription("Favicon");
            faviconImageView.clearColorFilter();
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
        getMenuInflater().inflate(R.menu.top_app_bar_menu, menu);
        BrowserMenuController.prepare(this, menu);
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
        if (jsItem != null) jsItem.setChecked(!jsEnabled);   
        MenuItem imgItem = menu.findItem(R.id.action_img);
        if (imgItem != null) imgItem.setChecked(imgBlockEnabled);
        MenuItem basicAuthItem = menu.findItem(R.id.action_basic_auth);
        if (basicAuthItem != null) basicAuthItem.setChecked(basicAuthEnabled);
        MenuItem adBlockItem = menu.findItem(R.id.action_adblock);
        if (adBlockItem != null) {
            adBlockItem.setChecked(AdBlockEngine.isEnabled());
            adBlockItem.setTitle(AdBlockEngine.menuTitle());
        }
        MenuItem addBookmarkItem = menu.findItem(R.id.action_add_bookmark);
        if (addBookmarkItem != null) {
            BrowserView currentView = getCurrentWebView();
            String currentUrl = currentView != null ? currentView.getUrl() : null;
            boolean bookmarked = currentUrl != null && isBookmarkedUrl(currentUrl);
            addBookmarkItem.setTitle(bookmarked ? "ブックマークを編集" : "ブックマーク登録");
        }
        MenuItem offscreenPreRasterItem = menu.findItem(R.id.action_offscreen_pre_raster);
        if (offscreenPreRasterItem != null) {
            offscreenPreRasterItem.setChecked(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && BrowserVisualSettings.isOffscreenPreRasterEnabled(pref));
            offscreenPreRasterItem.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        }
        MenuItem pictureInPictureItem = menu.findItem(R.id.action_picture_in_picture);
        if (pictureInPictureItem != null) {
            boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
            boolean enabled = isPictureInPictureEnabled();
            pictureInPictureItem.setCheckable(true);
            pictureInPictureItem.setChecked(enabled);
            pictureInPictureItem.setEnabled(supported);
            pictureInPictureItem.setTitle(enabled ? "小窓再生 (PiP) ON" : "小窓再生 (PiP) OFF");
        }
        MenuItem pictureInPictureSystemSettingsItem = menu.findItem(R.id.action_picture_in_picture_system_settings);
        if (pictureInPictureSystemSettingsItem != null) {
            pictureInPictureSystemSettingsItem.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE));
        }
        boolean prepared = super.onPrepareOptionsMenu(menu);
        BrowserMenuController.prepare(this, menu);
        return prepared;
    }
    @Override
    @SuppressWarnings("deprecation")
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_picture_in_picture) {
            togglePictureInPicture(item);
            return true;
        } else if (id == R.id.action_picture_in_picture_system_settings) {
            PictureInPictureSettings.open(this);
            return true;
        } else if (id == R.id.action_tabs) {
            showTabsDialog();
        } else if (id == R.id.action_adblock) {
            toggleAdBlock(item);
            return true;
        } else if (id == R.id.action_text_color) {
            showTextColorDialog();
            return true;
        } else if (id == R.id.action_font_family) {
            showFontFamilyDialog();
            return true;
        } else if (id == R.id.action_background_settings) {
            showBackgroundSettingsDialog();
            return true;
        } else if (id == R.id.action_custom_theme) {
            startActivity(new Intent(MainActivity.this, BrowserThemeActivity.class));
            return true;
        } else if (id == R.id.action_offscreen_pre_raster) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                boolean enabled = !BrowserVisualSettings.isOffscreenPreRasterEnabled(pref);
                BrowserVisualSettings.setOffscreenPreRasterEnabled(pref, enabled);
                applyBrowserVisualSettingsToAllWebViews();
                item.setChecked(enabled);
                Toast.makeText(this, "先読みレンダリング " + (enabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Android 6.0以上で利用できます", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_dark_mode) {
            darkModeEnabled = !darkModeEnabled;
            item.setChecked(darkModeEnabled);
            pref.edit().putBoolean(KEY_DARK_MODE, darkModeEnabled).apply();
            updateDarkMode();
            Toast.makeText(this, "ダークモード " + (darkModeEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_Settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        } else if (id == R.id.action_keyboard_settings) {
            KeyboardSettings.showSettingsDialog(this, urlEditText, getCurrentWebView(), pref);
        } else if (id == R.id.action_touchpad) {
            TouchpadOverlayController.show(MainActivity.this, pref);
        } else if (id == R.id.action_pin_settings) {
            showLaunchProtectionDialog();
        } else if (id == R.id.action_Secret) {
            Intent intent = new Intent(MainActivity.this, SecretActivity.class);
            intent.putExtra(EXTRA_CLEAR_HISTORY, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (id == R.id.action_plugins) {
            startActivity(new Intent(MainActivity.this, PluginManagerActivity.class));
        } else if (id == R.id.action_plugin_commands) {
            showPluginCommands();
        } else if (id == R.id.action_fx_addons) {
            startActivity(new Intent(MainActivity.this, com.coara.browser2.fx.FxExtensionsActivity.class));
        } else if (id == R.id.action_fx_actions) {
            BrowserView fxView = getCurrentWebView();
            com.coara.browser2.fx.FxActionsDialog.show(this, fxView == null ? null : fxView.getGeckoSession());
        } else if (id == R.id.action_downloads) {
            startActivity(new Intent(MainActivity.this, DownloadHistoryActivity.class));
        } else if (id == R.id.action_qr) {
            startActivity(new Intent(MainActivity.this, QrCodeActivity.class));
        } else if (id == R.id.action_pgdl) {
            Intent intent = new Intent(MainActivity.this, pagedl.class);
            intent.putExtra(BasicAuthManager.EXTRA_BASIC_AUTH_ENABLED, basicAuthEnabled);
            startActivity(intent);
        } else if (id == R.id.action_txtphoto) {
            startActivity(new Intent(MainActivity.this, txtphoto.class));
        } else if (id == R.id.action_num) {
            startActivity(new Intent(MainActivity.this, num.class));
        } else if (id == R.id.action_asciiart) {
            startActivity(new Intent(MainActivity.this, asciiart.class));
        } else if (id == R.id.action_grep) {
            startActivity(new Intent(MainActivity.this, grepmd5appActivity.class));
        } else if (id == R.id.action_htmlview) {
            startActivity(new Intent(MainActivity.this, htmlview.class));
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
            Intent intent = new Intent(MainActivity.this, DownloadHistoryActivity.class);
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
            applyZoomSettingToAllWebViews();
        } else if (id == R.id.action_js) {
            jsEnabled = !jsEnabled;
            if (jsEnabled) {
                enablejs();
            } else {
                disablejs();
            }
            item.setChecked(!jsEnabled);
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
                pref.edit().putBoolean(KEY_BASIC_AUTH, true).apply();
                Toast.makeText(MainActivity.this, "Basic認証 ON", Toast.LENGTH_SHORT).show();
            } else {
                basicAuthEnabled = false;
                item.setChecked(false);
                pref.edit().putBoolean(KEY_BASIC_AUTH, false).apply();
                clearBasicAuthCacheAndReload();
                Toast.makeText(MainActivity.this, "Basic認証 OFF", Toast.LENGTH_SHORT).show();
            }
            invalidateOptionsMenu();
        } else if (id == R.id.action_clear_cookies) {
            showClearCookiesDialog();
            return true;
        } else if (id == R.id.action_clear_cache) {
            showClearCacheDialog();
            return true;
        } else if (id == R.id.action_clear_history) {
            clearAllBrowsingData();
            urlEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            urlEditText.setRawInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            urlEditText.setPrivateImeOptions("nm");
            urlEditText.setAutofillHints("");
            urlEditText.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            String currentText = urlEditText.getText().toString();
            urlEditText.setText("");
            urlEditText.setText(currentText);
            Toast.makeText(MainActivity.this, "履歴、検索候補、及びタブとCookieを消去しました", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_negapoji) {
            applyNegapoji();
        } else if (id == R.id.action_translate) {
            translatePageToJapanese();
            return true;
        } else if (id == R.id.action_translate_settings) {
            TranslateSettingsDialog.show(this, false);
            return true;
        } else if (id == R.id.action_clear_tabs) {
            clearTabs();
        } else if (id == R.id.action_screenshot) {
            takeScreenshot();
        } else if (id == R.id.action_site_third_party_cookies) {
            showSiteThirdPartyCookieDialog();
            return true;
        } else if (id == R.id.action_export_all_cookies_zip) {
            new AlertDialog.Builder(this)
                    .setTitle("全タブのCookieを書き出しますか?")
                    .setMessage("出力されるzipにはログイン状態(セッション)を再現できるCookieが含まれます。他人に渡すとアカウントを乗っ取られる恐れがあります。保存先はダウンロードフォルダです。")
                    .setPositiveButton("書き出す", (dialog, which) -> {
                        ArrayList<String> urlList = new ArrayList<>();
                        synchronized (webViews) {
                            for (BrowserView tab : webViews) {
                                PendingTab pending = pendingTabs.get(tab);
                                urlList.add(pending != null ? pending.url : tab.getUrl());
                            }
                        }
                        exportAllTabCookies(urlList);
                    })
                    .setNegativeButton("キャンセル", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearBasicAuthCacheAndReload() {
        BasicAuthManager.clear(MainActivity.this);
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

    protected final boolean isPictureInPictureEnabled() {
        return pref != null && pref.getBoolean(KEY_PICTURE_IN_PICTURE_ENABLED, false);
    }

    protected final void togglePictureInPictureSetting() {
        if (pref == null) {
            return;
        }
        boolean enabled = !isPictureInPictureEnabled();
        pref.edit().putBoolean(KEY_PICTURE_IN_PICTURE_ENABLED, enabled).apply();
        if (!enabled) {
            com.coara.browser2.MediaPipActivity.closeActive();
        }
        invalidateOptionsMenu();
        Toast.makeText(this, enabled ? "小窓再生を有効にしました" : "小窓再生を無効にしました", Toast.LENGTH_SHORT).show();
    }

    private void togglePictureInPicture(MenuItem item) {
        togglePictureInPictureSetting();
        if (item != null) {
            item.setChecked(isPictureInPictureEnabled());
        }
    }

    private void updatePageLoadProgress(BrowserView view, int progress, boolean complete) {
        if (isCustomTabMode() || view == null || view != getCurrentWebView() || pageProgressBar == null) {
            return;
        }
        android.os.Handler handler = UiThread.mainHandler();
        handler.removeCallbacks(hidePageProgressRunnable);
        if (complete) {
            pageProgressBar.setVisibility(View.VISIBLE);
            pageProgressBar.setIndeterminate(false);
            pageProgressBar.setProgress(100, true);
            handler.postDelayed(hidePageProgressRunnable, 240L);
            return;
        }
        pageProgressBar.setVisibility(View.VISIBLE);
        if (progress <= 0) {
            pageProgressBar.setProgress(0);
            pageProgressBar.setIndeterminate(true);
            return;
        }
        if (pageProgressBar.isIndeterminate()) {
            pageProgressBar.setIndeterminate(false);
            pageProgressBar.setProgress(0);
        }
        int visibleProgress = Math.max(1, Math.min(96, progress));
        if (visibleProgress > pageProgressBar.getProgress()) {
            pageProgressBar.setProgress(visibleProgress, true);
        } else if (visibleProgress < pageProgressBar.getProgress() && visibleProgress < 90) {
            pageProgressBar.setProgress(visibleProgress);
        }
    }

    private void translatePageToJapanese() {
        BrowserView target = getCurrentWebView();
        if (target == null) {
            Toast.makeText(MainActivity.this, "翻訳するページが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        PageTranslator.toggle(target, true);
    }

    private void applyNegapoji() {
        String js = "javascript:(function(){" +
                "document.documentElement.style.filter='invert(1)';" +
                "var media=document.getElementsByTagName('img');" +
                "for(var i=0;i<media.length;i++){media[i].style.filter='invert(1)';}" +
                "})();";
        getCurrentWebView().evaluateJavascript(js, null);
    }

    private void clearWebStorage() {
        BrowserEngine.get().clearDomStorage();
    }

    private void applyTabModes(BrowserView view) {
        BrowserSettings s = view.getSettings();
        s.setUserAgentString(uaEnabled ? GALAKE_UA : null);
        s.setDesktopMode(deskuaEnabled && !uaEnabled);
    }

    private void onTabModeChanged() {
        BrowserEngine.get().updateDesktopViewport(this);
        for (BrowserView v : new ArrayList<>(webViews)) {
            applyTabModes(v);
        }
        reloadTabsForSettings();
    }

    private void reloadCurrentPage() {
        BrowserView current = getCurrentWebView();
        if (current != null) {
            reloadTabNow(current);
        }
    }

    private void reloadTabNow(BrowserView view) {
        staleTabs.remove(view);
        if (view == null || pendingTabs.containsKey(view)) {
            return;
        }
        String u = view.getUrl();
        if (u == null || u.isEmpty() || u.equals("about:blank")) {
            return;
        }
        try {
            saveTabHistoryState(view);
            PluginRuntime.prepareForFullReload(view);
            view.reloadForSettings();
        } catch (Exception ignored) {
        }
    }

    private interface StorageClearTask {
        org.mozilla.geckoview.GeckoResult<Void> run();
    }

    private void runStorageClear(final String label, final StorageClearTask task) {
        final boolean[] finished = {false};
        final Runnable timeout = () -> {
            if (!finished[0]) {
                finished[0] = true;
                Toast.makeText(MainActivity.this, label + "の消去を実行しました", Toast.LENGTH_SHORT).show();
            }
        };
        UiThread.mainHandler().postDelayed(timeout, 4000L);
        try {
            org.mozilla.geckoview.GeckoResult<Void> result = task.run();
            if (result == null) {
                UiThread.mainHandler().removeCallbacks(timeout);
                finished[0] = true;
                Toast.makeText(MainActivity.this, label + "を消去できませんでした", Toast.LENGTH_SHORT).show();
                return;
            }
            result.accept(value -> UiThread.mainHandler().post(() -> {
                if (!finished[0]) {
                    finished[0] = true;
                    UiThread.mainHandler().removeCallbacks(timeout);
                    Toast.makeText(MainActivity.this, label + "を消去しました", Toast.LENGTH_SHORT).show();
                }
            }), error -> UiThread.mainHandler().post(() -> {
                if (!finished[0]) {
                    finished[0] = true;
                    UiThread.mainHandler().removeCallbacks(timeout);
                    Toast.makeText(MainActivity.this, label + "を消去できませんでした", Toast.LENGTH_SHORT).show();
                }
            }));
        } catch (Throwable t) {
            UiThread.mainHandler().removeCallbacks(timeout);
            finished[0] = true;
            Toast.makeText(MainActivity.this, label + "を消去できませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearCookiesDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("クッキーを消去")
                .setMessage("一時クッキーを全て消しますか？")
                .setNegativeButton("はい", (dialog, which) -> {
                    runStorageClear("クッキー", () -> BrowserEngine.get().clearCookies());
                })
                .setPositiveButton("いいえ", null)
                .show();
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("キャッシュを消去")
                .setMessage("一時キャッシュを全て消しますか？")
                .setNegativeButton("はい", (dialog, which) -> {
                    runStorageClear("キャッシュ", () -> BrowserEngine.get().clearCaches());
                })
                .setPositiveButton("いいえ", null)
                .show();
    }

    private void reloadTabsForSettings() {
        BrowserView cur = getCurrentWebView();
        for (BrowserView v : new ArrayList<>(webViews)) {
            if (pendingTabs.containsKey(v)) {
                continue;
            }
            if (v == cur) {
                reloadTabNow(v);
            } else {
                staleTabs.add(v);
            }
        }
    }

    private void clearPageCache() {
        for (BrowserView webView : webViews) {
            webView.clearCache(true);
        }
    }
    private void clearSSL() {
        BrowserEngine.get().clearAuthSessions();
        for (BrowserView webView : webViews) {
            webView.clearMatches();
        }
    }


    private void clearAllBrowsingData() {
        synchronized (historyItems) {
            historyItems.clear();
        }
        historyDirty = false;
        UiThread.mainHandler().removeCallbacks(historySaveRunnable);
        saveHistory();

        ArrayList<BrowserView> all;
        synchronized (webViews) {
            all = new ArrayList<>(webViews);
        }

        for (BrowserView w : all) {
            clearPendingSpaHistory(w);
            Runnable saveTask = stateSaveTasks.remove(w);
            if (saveTask != null) {
                UiThread.mainHandler().removeCallbacks(saveTask);
            }
            Runnable snapshotTask = snapshotTasks.remove(w);
            if (snapshotTask != null) {
                UiThread.mainHandler().removeCallbacks(snapshotTask);
            }
            try {
                w.clearCache(true);
                w.clearFormData();
                w.clearMatches();
            } catch (Exception ignored) {
            }
        }

        stateSaveTasks.clear();
        snapshotTasks.clear();
        BrowserEngine.get().clearAllBrowsingData();
        clearTabs();

        dirtyStateTabs.clear();
        dirtySnapshotTabs.clear();
        historyPrefix.clear();
        restoreSeeds.clear();
        virtualBackGuards.clear();
        seedAlignedTabs.clear();
        backNavigationTabs.clear();
        tabHistoryStates.clear();
        pendingTabs.clear();
        staleTabs.clear();
        externalTabs.clear();
        popupTabs.clear();
        pullToRefreshEligibleCache.clear();

        BrowserView previous = webViews.isEmpty() ? null : getCurrentWebView();
        if (previous != null) {
            clearPendingSpaHistory(previous);
            stateSaveTasks.remove(previous);
            snapshotTasks.remove(previous);
            tabHistoryStates.remove(previous);
            pendingTabs.remove(previous);
            staleTabs.remove(previous);
            externalTabs.remove(previous);
            popupTabs.remove(previous);
            backNavigationTabs.remove(previous);
            dirtyStateTabs.remove(previous);
            dirtySnapshotTabs.remove(previous);
            pullToRefreshEligibleCache.remove(previous);
            resetHistoryTracking(previous);
            ExternalDownloadTabTracker.clear(previous);
            Bitmap oldSnapshot = tabSnapshots.remove(previous);
            if (oldSnapshot != null && !oldSnapshot.isRecycled()) {
                try {
                    oldSnapshot.recycle();
                } catch (Exception ignored) {
                }
            }
            TouchpadController.clearCurrentWebView(previous);
        }

        BrowserView replacement = createNewWebView();
        int replacementId = nextTabId++;
        replacement.setTag(replacementId);
        if (previous != null) {
            int previousIndex = webViews.indexOf(previous);
            if (previousIndex >= 0) {
                webViews.set(previousIndex, replacement);
                currentTabIndex = previousIndex;
            } else {
                webViews.clear();
                webViews.add(replacement);
                currentTabIndex = 0;
            }
            try {
                previous.stopLoading();
                previous.destroy();
            } catch (Exception ignored) {
            }
        } else {
            webViews.clear();
            webViews.add(replacement);
            currentTabIndex = 0;
        }

        tabHistoryStates.clear();
        tabHistoryStates.put(replacement, new TabHistoryStore.State());
        webViewContainer.removeAllViews();
        webViewContainer.addView(replacement);
        TouchpadController.setCurrentWebView(replacement);
        updateTabCount();

        try {
            persistExecutor.execute(() -> {
                File[] files = getFilesDir().listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if ((name.startsWith("tab_state_") && name.endsWith(".dat"))
                                || (name.startsWith("tab_history_") && name.endsWith(".json"))) {
                            safeDeleteFile(file);
                        }
                    }
                }
                if (tabHistoryStore != null) {
                    tabHistoryStore.deleteAll();
                }
            });
        } catch (Exception ignored) {
            File[] files = getFilesDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if ((name.startsWith("tab_state_") && name.endsWith(".dat"))
                            || (name.startsWith("tab_history_") && name.endsWith(".json"))) {
                        safeDeleteFile(file);
                    }
                }
            }
            if (tabHistoryStore != null) {
                tabHistoryStore.deleteAll();
            }
        }

        try {
            backgroundExecutor.execute(() -> {
                File[] files = getFilesDir().listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (name.startsWith("tab_snapshot_") && name.endsWith(".png")) {
                            safeDeleteFile(file);
                        }
                    }
                }
            });
        } catch (Exception ignored) {
            File[] files = getFilesDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.startsWith("tab_snapshot_") && name.endsWith(".png")) {
                        safeDeleteFile(file);
                    }
                }
            }
        }

        replacement.loadUrl(START_PAGE);
        updatePullToRefreshState(replacement, START_PAGE);
        purgeFaviconFiles();
        if (faviconCache != null) {
            faviconCache.evictAll();
        }
        BasicAuthManager.clear(MainActivity.this);
        saveTabsState();
        urlEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlEditText.setRawInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlEditText.setPrivateImeOptions("nm");
        urlEditText.setAutofillHints("");
        urlEditText.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        urlEditText.setText("");
        Toast.makeText(MainActivity.this, "履歴、検索候補、及びタブとCookieを消去しました", Toast.LENGTH_SHORT).show();
    }

    private void purgeTabFiles() {
        final File dir = getFilesDir();
        backgroundExecutor.execute(() -> {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                String n = f.getName();
                if ((n.startsWith("tab_state_") && n.endsWith(".dat")) || (n.startsWith("tab_snapshot_") && n.endsWith(".png"))) {
                    safeDeleteFile(f);
                }
            }
        });
    }

    private void purgeFaviconFiles() {
        final File dir = new File(getFilesDir(), "favicons");
        final Set<String> keep = new java.util.HashSet<>();
        for (Bookmark b : new ArrayList<>(bookmarks)) {
            String fk = faviconKey(b.getUrl());
            if (fk != null) {
                keep.add(getFaviconFilename(fk));
            }
        }
        backgroundExecutor.execute(() -> {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                if (!keep.contains(f.getName())) {
                    safeDeleteFile(f);
                }
            }
        });
    }

    private void clearTabs() {
        synchronized (webViews) {
            BrowserView current = getCurrentWebView();
            if (current == null) {
                return;
            }

            ArrayList<BrowserView> snapshot = new ArrayList<>(webViews);
            for (BrowserView w : snapshot) {
                if (w == current) {
                    continue;
                }
                externalTabs.remove(w);
                ExternalDownloadTabTracker.clear(w);
                Object tag = w.getTag();
                int id = (tag instanceof Integer) ? (Integer) tag : -1;
                Bitmap bm = tabSnapshots.remove(w);
                if (bm != null && !bm.isRecycled()) {
                    try {
                        bm.recycle();
                    } catch (Exception ignored) {
                    }
                }
                pullToRefreshEligibleCache.remove(w);
                pendingTabs.remove(w);
                tabHistoryStates.remove(w);
                backNavigationTabs.remove(w);
                if (id != -1) {
                    if (tabHistoryStore != null) {
                        tabHistoryStore.delete(id);
                    }
                    File snapFile = new File(getFilesDir(), "tab_snapshot_" + id + ".png");
                    safeDeleteFile(new File(getFilesDir(), "tab_state_" + id + ".dat"));
                    if (snapFile.exists()) {
                        snapFile.delete();
                    }
                }
                try {
                    w.stopLoading();
                    w.destroy();
                } catch (Exception ignored) {
                }
            }

            webViews.clear();
            webViews.add(current);
            currentTabIndex = 0;
            int currentId = getTabId(current);
            if (currentId >= 0 && tabHistoryStore != null) {
                tabHistoryStore.delete(currentId);
            }
            tabHistoryStates.remove(current);
            resetHistoryTracking(current);
            backNavigationTabs.remove(current);
            try {
                current.clearHistory();
                current.loadUrl(START_PAGE);
            } catch (Exception ignored) {
            }
            webViewContainer.removeAllViews();
            webViewContainer.addView(current);
            updateTabCount();
        }
    }


        private void takeScreenshot() {
        com.coara.browser2.util.ScreenshotSaver.capture(this, getCurrentWebView(), backgroundExecutor);
    }

    private void updateDarkMode() {
        BrowserEngine.get().setDarkMode(darkModeEnabled);
        applyBrowserVisualSettingsToAllWebViews();
        DarkModeSwitcher.settle(getCurrentWebView(), darkModeEnabled, () -> {
            if (!isFinishing() && !isDestroyed()) {
                reloadTabsForSettings();
            }
        });
    }

                            private void disablejs() {
        for (BrowserView v : new ArrayList<>(webViews)) {
            v.getSettings().setJavaScriptEnabled(false);
        }
        Toast.makeText(MainActivity.this, "JavaScript無効", Toast.LENGTH_SHORT).show();
        reloadTabsForSettings();
    }

    private void enablejs() {
        for (BrowserView v : new ArrayList<>(webViews)) {
            v.getSettings().setJavaScriptEnabled(true);
        }
        Toast.makeText(MainActivity.this, "JavaScript有効", Toast.LENGTH_SHORT).show();
        reloadTabsForSettings();
    }

    private void applyZoomSettingToAllWebViews() {
        synchronized (webViews) {
            for (BrowserView target : new ArrayList<>(webViews)) {
                try {
                    BrowserSettings settings = target.getSettings();
                    settings.setZoomEnabled(zoomEnabled);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void applyBrowserThemeToAllWebViews() {
        synchronized (webViews) {
            for (BrowserView target : new ArrayList<>(webViews)) {
                try {
                    BrowserThemeManager.applyToWebView(target, this);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enableZoom() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setZoomEnabled(true);
        Toast.makeText(MainActivity.this, "ズームを有効にしました", Toast.LENGTH_SHORT).show();
    }

    private void disableZoom() {
        BrowserSettings settings = getCurrentWebView().getSettings();
        settings.setZoomEnabled(false);
        reloadCurrentPage();
        Toast.makeText(MainActivity.this, "ズームを無効にしました", Toast.LENGTH_SHORT).show();
    }

    private void enableimgblock() {
        for (BrowserView v : new ArrayList<>(webViews)) {
            v.getSettings().setLoadsImagesAutomatically(false);
        }
        Toast.makeText(MainActivity.this, "画像ブロック有効", Toast.LENGTH_SHORT).show();
        reloadTabsForSettings();
    }

    private void disableimgunlock() {
        for (BrowserView v : new ArrayList<>(webViews)) {
            v.getSettings().setLoadsImagesAutomatically(defaultLoadsImagesAutomatically);
        }
        Toast.makeText(MainActivity.this, "画像ブロック無効", Toast.LENGTH_SHORT).show();
        reloadTabsForSettings();
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
        if (urlEditMode) {
            exitUrlEditMode();
        }
        if (urlEditText != null) {
            urlEditText.clearFocus();
        }
        findController().show();
    }

    private void hideFindInPageBar() {
        findController().hide();
    }

    private void showHistoryDialog() {
        HistoryDialog.show(this, historyItems, new HistoryDialog.Host() {
            @Override
            public Bitmap favicon(String url) {
                return getFaviconBitmap(url);
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

    
    private void showTabsDialog() {
        BrowserView currentForSnapshot = getCurrentWebView();
        if (currentForSnapshot == null || pendingTabs.containsKey(currentForSnapshot)) {
            openTabsDialog();
            return;
        }
        final boolean[] opened = {false};
        final Runnable open = () -> {
            if (opened[0]) {
                return;
            }
            opened[0] = true;
            if (!isFinishing() && !isDestroyed()) {
                openTabsDialog();
            }
        };
        UiThread.mainHandler().postDelayed(open, 450L);
        captureTabSnapshot(currentForSnapshot, open);
    }

    private void openTabsDialog() {
        TabOverviewDialog.show(
                this,
                new TabOverviewDialog.Host() {
                    @Override
                    public int getTabCount() {
                        synchronized (webViews) {
                            return webViews.size();
                        }
                    }

                    @Override
                    public BrowserView getTabAt(int index) {
                        synchronized (webViews) {
                            if (index < 0 || index >= webViews.size()) return null;
                            return webViews.get(index);
                        }
                    }

                    @Override
                    public Bitmap getTabSnapshot(BrowserView webView) {
                        return loadSnapshotIfNeeded(webView);
                    }

                    @Override
                    public String getTabFallbackTitle(BrowserView webView) {
                        PendingTab pending = pendingTabs.get(webView);
                        if (pending == null) {
                            return null;
                        }
                        return pending.title.isEmpty() ? pending.url : pending.title;
                    }

                    @Override
                    public int getCurrentTabIndex() {
                        return currentTabIndex;
                    }

                    @Override
                    public com.coara.browser2.util.TabGroupStore getGroupStore() {
                        return getTabGroupStore();
                    }

                    @Override
                    public int getTabId(BrowserView webView) {
                        return MainActivity.this.getTabId(webView);
                    }

                    @Override
                    public void switchToTab(int index) {
                        MainActivity.this.switchToTab(index);
                    }

                    @Override
                    public void closeTabAt(int index) {
                        BrowserView target = getTabAt(index);
                        if (target != null) {
                            MainActivity.this.closeTab(target);
                        }
                    }

                    @Override
                    public void createNewTab() {
                        MainActivity.this.createNewTab();
                    }

                    @Override
                    public void refreshTabCount() {
                        updateTabCount();
                    }

                    @Override
                    public void dismissCurrentKeyboard() {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null && getCurrentFocus() != null) {
                            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                        }
                    }
                }
        );
    }


    private final Set<BrowserView> snapshotInFlight = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());

    private void captureTabSnapshot(BrowserView webView) {
        captureTabSnapshot(webView, null);
    }

    private void captureTabSnapshot(final BrowserView webView, final Runnable done) {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0
                || pendingTabs.containsKey(webView) || isFinishing() || isDestroyed()
                || !webView.isCaptureReady() || !snapshotInFlight.add(webView)) {
            if (done != null) {
                done.run();
            }
            return;
        }
        final Runnable release = () -> snapshotInFlight.remove(webView);
        UiThread.mainHandler().postDelayed(release, 4000L);
        webView.capture(bitmap -> {
            if (bitmap == null || bitmap.isRecycled()) {
                snapshotInFlight.remove(webView);
                if (done != null) {
                    done.run();
                }
                return;
            }
            backgroundExecutor.execute(() -> {
                Bitmap small = null;
                try {
                    int maxWidth = 300;
                    int sw = bitmap.getWidth();
                    int sh = bitmap.getHeight();
                    float scale = sw > maxWidth ? (float) maxWidth / (float) sw : 1f;
                    int tw = Math.max(1, Math.round(sw * scale));
                    int th = Math.max(1, Math.round(sh * scale));
                    small = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(small);
                    canvas.drawColor(Color.WHITE);
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                    canvas.drawBitmap(bitmap, null, new android.graphics.Rect(0, 0, tw, th), paint);
                    if (isBlackFrame(small)) {
                        small.recycle();
                        small = null;
                    }
                } catch (Throwable t) {
                    small = null;
                } finally {
                    try {
                        bitmap.recycle();
                    } catch (Throwable ignored) {
                    }
                }
                final Bitmap result = small;
                runOnUiThread(() -> {
                    UiThread.mainHandler().removeCallbacks(release);
                    snapshotInFlight.remove(webView);
                    if (result != null && webViews.contains(webView)) {
                        Bitmap old = tabSnapshots.put(webView, result);
                        if (old != null && old != result && !old.isRecycled()) {
                            old.recycle();
                        }
                        dirtySnapshotTabs.add(webView);
                        trimSnapshotMemory(webView);
                    } else if (result != null && !result.isRecycled()) {
                        result.recycle();
                    }
                    if (done != null) {
                        done.run();
                    }
                });
            });
        });
    }

    private static boolean isBlackFrame(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int steps = 12;
        for (int yi = 0; yi < steps; yi++) {
            for (int xi = 0; xi < steps; xi++) {
                int x = Math.min(w - 1, (int) ((xi + 0.5f) * w / steps));
                int y = Math.min(h - 1, (int) ((yi + 0.5f) * h / steps));
                int c = bitmap.getPixel(x, y);
                if (((c >> 16) & 0xFF) > 6 || ((c >> 8) & 0xFF) > 6 || (c & 0xFF) > 6) {
                    return false;
                }
            }
        }
        return true;
    }

    private final Map<BrowserView, Runnable> snapshotTasks = new WeakHashMap<>();

    private void scheduleSnapshot(final BrowserView view, long delayMs) {
        if (view == null) {
            return;
        }
        Runnable old = snapshotTasks.remove(view);
        if (old != null) {
            UiThread.mainHandler().removeCallbacks(old);
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                snapshotTasks.remove(view);
                if (view == getCurrentWebView() && !view.isLoading() && hasWindowFocus()) {
                    captureTabSnapshot(view);
                }
            }
        };
        snapshotTasks.put(view, task);
        UiThread.mainHandler().postDelayed(task, delayMs);
    }

    private void trimSnapshotMemory(BrowserView keep) {
        if (tabSnapshots.size() <= MAX_MEMORY_SNAPSHOTS) {
            return;
        }
        java.util.Iterator<Map.Entry<BrowserView, Bitmap>> it = tabSnapshots.entrySet().iterator();
        while (it.hasNext() && tabSnapshots.size() > MAX_MEMORY_SNAPSHOTS) {
            Map.Entry<BrowserView, Bitmap> entry = it.next();
            BrowserView owner = entry.getKey();
            if (owner == keep) {
                continue;
            }
            dirtySnapshotTabs.remove(owner);
            it.remove();
        }
    }

    private final BookmarkDialog.Host bookmarkHost = new BookmarkDialog.Host() {
        @Override
        public Bitmap favicon(String url) {
            return getFaviconBitmap(url);
        }

        @Override
        public void open(String url) {
            BrowserView current = getCurrentWebView();
            if (current != null) {
                current.loadUrl(url);
            }
        }

        @Override
        public void openInNewTab(String url) {
            createNewTab(url);
        }

        @Override
        public void changed() {
            saveBookmarks();
        }
    };

    private ImageView bookmarkStarView;

    private void setupBookmarkStar() {
        bookmarkStarView = findViewById(R.id.bookmarkStar);
        if (bookmarkStarView == null) {
            return;
        }
        bookmarkStarView.setOnClickListener(v -> {
            addBookmark();
            updateBookmarkStar();
        });
        bookmarkStarView.setOnLongClickListener(v -> {
            showBookmarksManagementDialog();
            return true;
        });
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
                    Toast.makeText(MainActivity.this, "URLをコピーしました", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (id == 2) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence text = null;
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    text = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                }
                if (text == null || text.toString().trim().isEmpty()) {
                    Toast.makeText(MainActivity.this, "クリップボードが空です", Toast.LENGTH_SHORT).show();
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

    private void updateBookmarkStar() {
        updateUrlSecurityIcon();
        if (bookmarkStarView == null) {
            return;
        }
        BrowserView current = getCurrentWebView();
        String url = current != null ? current.getUrl() : null;
        boolean usable = url != null && !url.isEmpty() && !url.equals(START_PAGE) && !urlEditMode;
        if (!usable) {
            bookmarkStarView.setVisibility(View.GONE);
            return;
        }
        boolean bookmarked = isBookmarkedUrl(url);
        bookmarkStarView.setImageResource(bookmarked ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        bookmarkStarView.setVisibility(View.VISIBLE);
    }

    private void showBookmarksManagementDialog() {
        BookmarkDialog.show(this, bookmarks, pref, bookmarkHost);
    }

    private void addBookmark() {
        BrowserView current = getCurrentWebView();
        String currentUrl = current != null ? current.getUrl() : null;
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "追加できるページがありません", Toast.LENGTH_SHORT).show();
            return;
        }
        int existing = BookmarkStore.indexOfUrl(bookmarks, currentUrl);
        if (existing >= 0) {
            BookmarkDialog.showEditor(this, bookmarks, existing, bookmarkHost, null);
            return;
        }
        String title = current.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = BookmarkStore.hostOf(currentUrl);
        }
        final String defaultFolder = BookmarkStore.getDefaultFolder(this);
        final Bookmark added = new Bookmark(title.trim(), currentUrl, defaultFolder);
        bookmarks.add(added);
        saveBookmarks();
        prefetchFavicons(java.util.Collections.singletonList(added));
        try {
            Snackbar.make(findViewById(android.R.id.content),
                defaultFolder.isEmpty() ? "ブックマークに追加しました" : "「" + defaultFolder + "」に追加しました", Snackbar.LENGTH_LONG)
                .setAction("編集", v -> BookmarkDialog.showEditor(this, bookmarks,
                    BookmarkStore.indexOfInstance(bookmarks, added), bookmarkHost, null))
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "ブックマークを追加しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void prefetchFavicons(final List<Bookmark> targets) {
        final List<String> urls = new ArrayList<>();
        for (Bookmark b : targets) {
            if (urls.size() >= 50) {
                break;
            }
            urls.add(b.getUrl());
        }
        backgroundExecutor.execute(() -> {
            for (String url : urls) {
                File cached = new File(new File(getFilesDir(), "favicons"), getFaviconFilename(url));
                if (cached.exists()) {
                    continue;
                }
                Bitmap favicon = fetchFavicon(url);
                if (favicon != null) {
                    runOnUiThread(() -> faviconCache.put(url, favicon));
                    saveFaviconToFile(url, favicon);
                }
            }
        });
    }

    private java.util.HashSet<String> bookmarkKeyCache;
    private int bookmarkKeyCacheSize = -1;

    private boolean isBookmarkedUrl(String url) {
        if (bookmarkKeyCache == null || bookmarkKeyCacheSize != bookmarks.size()) {
            java.util.HashSet<String> keys = new java.util.HashSet<>(Math.max(16, bookmarks.size() * 2));
            for (Bookmark b : bookmarks) {
                keys.add(BookmarkStore.normalizeUrl(b.getUrl()));
            }
            bookmarkKeyCache = keys;
            bookmarkKeyCacheSize = bookmarks.size();
        }
        String key = BookmarkStore.normalizeUrl(url);
        return !key.isEmpty() && bookmarkKeyCache.contains(key);
    }

    private void loadBookmarks() {
        bookmarkKeyCache = null;
        bookmarks.clear();
        String jsonStr = pref.getString(KEY_BOOKMARKS, "[]");
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                bookmarks.addAll(BookmarkStore.parseJson(jsonStr));
            } catch (JSONException e) {
                e.printStackTrace();
            }
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
                    Toast.makeText(MainActivity.this,
                        "ファイルが選択されませんでした", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this,
                    "ファイル選択がキャンセルされました", Toast.LENGTH_SHORT).show();
            }
        });
    private void importBookmarksFromFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "application/json", "text/html", "text/plain", "application/octet-stream"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        filePickerLauncher.launch(intent);
    }

    private void handleFileImport(Uri uri) {
        try {
            String text = readTextFromUri(uri);
            List<Bookmark> incoming = BookmarkStore.looksLikeHtml(text)
                ? BookmarkStore.parseNetscapeHtml(text)
                : BookmarkStore.parseJson(text);
            if (incoming.isEmpty()) {
                Toast.makeText(MainActivity.this, "取り込めるブックマークがありません", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmBookmarkImport(incoming);
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "ファイルの読み取りに失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (JSONException e) {
            Toast.makeText(MainActivity.this, "JSON解析エラー: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void confirmBookmarkImport(final List<Bookmark> incoming) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("ブックマークをインポート")
            .setMessage(incoming.size() + "件を読み込みました。\n現在のブックマーク: " + bookmarks.size() + "件")
            .setPositiveButton("追加(重複は除外)", (d, w) -> {
                List<Bookmark> before = new ArrayList<>(bookmarks);
                int added = BookmarkStore.merge(bookmarks, incoming);
                saveBookmarks();
                List<Bookmark> fresh = new ArrayList<>(bookmarks);
                fresh.removeAll(before);
                prefetchFavicons(fresh);
                Toast.makeText(MainActivity.this, added + "件を追加しました", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("すべて置き換え", (d, w) -> {
                bookmarks.clear();
                BookmarkStore.merge(bookmarks, incoming);
                saveBookmarks();
                prefetchFavicons(bookmarks);
                Toast.makeText(MainActivity.this, bookmarks.size() + "件に置き換えました", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("キャンセル", null)
            .show();
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
    private void saveBookmarks() {
        bookmarkKeyCache = null;
        pref.edit().putString(KEY_BOOKMARKS, BookmarkStore.toJson(bookmarks)).apply();
        updateBookmarkStar();
    }
    private void loadHistory() {
    historyItems.clear();
    String jsonStr = pref.getString(KEY_HISTORY, "[]");
    if (jsonStr == null || jsonStr.trim().isEmpty()) {
        return;
    }
    try {
        JSONArray array = new JSONArray(jsonStr);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String title = obj.optString("title", "");
            String url = obj.optString("url", "");
            if (url.isEmpty()) {
                continue;
            }
            long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            historyItems.add(new HistoryItem(title, url, timestamp));
        }
    } catch (JSONException e) {
        e.printStackTrace();
    }
}

private void saveHistory() {
    ArrayList<HistoryItem> copy;
    synchronized (historyItems) {
        copy = new ArrayList<>(historyItems);
    }
    JSONArray array = new JSONArray();
    for (HistoryItem item : copy) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("title", item.getTitle());
            obj.put("url", item.getUrl());
            obj.put("timestamp", item.getTimestamp());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        array.put(obj);
    }
    pref.edit().putString(KEY_HISTORY, array.toString()).apply();
}

private boolean shouldRecordHistory(String url) {
    if (url == null || url.isEmpty() || url.equals(START_PAGE)) {
        return false;
    }
    String lower = url.toLowerCase(Locale.ROOT);
    return !(lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("javascript:")
            || lower.startsWith("blob:") || lower.startsWith("chrome-error:")
            || lower.startsWith("file:///android_asset") || lower.startsWith("file:///android_res")
            || lower.startsWith("https://appassets.androidplatform.net/assets/")
            || lower.startsWith("https://appassets.androidplatform.net/res/")
            || lower.startsWith(BrowserConstants.APP_ASSET_BASE_URL)
            || lower.startsWith("moz-extension:") || lower.startsWith("resource:")
            || lower.startsWith(com.coara.browser2.gecko.ErrorPages.ACTION_SCHEME));
}

private void updateHistoryTitle(String url, String title) {
    synchronized (historyItems) {
        if (historyItems.isEmpty()) {
            return;
        }
        int idx = historyItems.size() - 1;
        HistoryItem last = historyItems.get(idx);
        if (last.getUrl().equals(url) && (last.getTitle() == null || last.getTitle().isEmpty())) {
            historyItems.set(idx, new HistoryItem(title, url, last.getTimestamp()));
            scheduleHistorySave();
        }
    }
}

private void addHistory(String url, String title) {
    if (!shouldRecordHistory(url)) {
        return;
    }
    synchronized (historyItems) {
        if (!historyItems.isEmpty()) {
            HistoryItem last = historyItems.get(historyItems.size() - 1);
            if (last.getUrl().equals(url)) {
                if ((last.getTitle() == null || last.getTitle().isEmpty()) && title != null && !title.isEmpty()) {
                    historyItems.set(historyItems.size() - 1, new HistoryItem(title, url, last.getTimestamp()));
                    scheduleHistorySave();
                }
                return;
            }
        }
        historyItems.add(new HistoryItem(title == null ? "" : title, url, System.currentTimeMillis()));
        while (historyItems.size() > MAX_HISTORY_SIZE) {
            historyItems.remove(0);
        }
    }
    scheduleHistorySave();
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
        Toast.makeText(MainActivity.this, "リンクをコピーしました", Toast.LENGTH_SHORT).show();
    }

                        private static String faviconKey(String url) {
        if (url == null) {
            return null;
        }
        try {
            String host = Uri.parse(url).getHost();
            if (host == null || host.isEmpty()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String md5Name(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            final char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder sb = new StringBuilder(36);
            for (byte b : hash) {
                sb.append(hex[(b >> 4) & 0x0f]).append(hex[b & 0x0f]);
            }
            return sb.append(".png").toString();
        } catch (Exception e) {
            return Integer.toString(text.hashCode()) + ".png";
        }
    }

    private String getFaviconFilename(String key) {
        return md5Name("h:" + key);
    }

    private Bitmap normalizeFavicon(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return null;
        }
        int max = 96;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        if (w <= max && h <= max) {
            return src;
        }
        float scale = Math.min((float) max / w, (float) max / h);
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
    }

    private void storeFavicon(String url, Bitmap icon) {
        final String key = faviconKey(url);
        if (key == null || icon == null) {
            return;
        }
        final Bitmap bitmap = normalizeFavicon(icon);
        if (bitmap == null) {
            return;
        }
        Bitmap previous = faviconCache.get(key);
        if (previous != null && (previous == bitmap || previous.sameAs(bitmap))) {
            return;
        }
        faviconCache.put(key, bitmap);
        faviconMissing.remove(key);
        faviconExecutor.execute(() -> saveFaviconToFile(key, bitmap));
    }

    private void saveFaviconToFile(String key, Bitmap bitmap) {
        File dir = new File(getFilesDir(), "favicons");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            return;
        }
        File file = new File(dir, getFaviconFilename(key));
        File tmp = new File(dir, file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.flush();
        } catch (IOException e) {
            safeDeleteFile(tmp);
            return;
        }
        if (!tmp.renameTo(file)) {
            safeDeleteFile(file);
            if (!tmp.renameTo(file)) {
                safeDeleteFile(tmp);
            }
        }
    }

    private Bitmap readFaviconFromDisk(String key, String url) {
        File dir = new File(getFilesDir(), "favicons");
        File file = new File(dir, getFaviconFilename(key));
        if (!file.exists() && url != null) {
            file = new File(dir, md5Name(url));
        }
        if (!file.isFile()) {
            return null;
        }
        long length = file.length();
        if (length <= 0) {
            return null;
        }
        if (length > MAX_CACHED_FAVICON_BYTES) {
            try {
                file.delete();
            } catch (Throwable ignored) {
            }
            return null;
        }
        try (InputStream input = new java.io.BufferedInputStream(new FileInputStream(file), 8192)) {
            byte[] data = readLimitedBytes(input, MAX_CACHED_FAVICON_BYTES);
            return decodeFaviconBytes(data);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readLimitedBytes(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, maxBytes));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) {
                continue;
            }
            total += count;
            if (total > maxBytes) {
                return null;
            }
            output.write(buffer, 0, count);
        }
        return total == 0 ? null : output.toByteArray();
    }

    private Bitmap decodeFaviconBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / sample > MAX_FAVICON_SIDE * 2
                || bounds.outHeight / sample > MAX_FAVICON_SIDE * 2) {
            if (sample > (1 << 20)) {
                return null;
            }
            sample <<= 1;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inDither = true;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        Bitmap normalized = normalizeFavicon(bitmap);
        if (bitmap != null && normalized != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return normalized;
    }

    private Bitmap fetchFavicon(String pageUrl) {
        HttpURLConnection connection = null;
        try {
            URL base = new URL(pageUrl);
            if (!("http".equalsIgnoreCase(base.getProtocol()) || "https".equalsIgnoreCase(base.getProtocol()))) {
                return null;
            }
            boolean startedHttps = "https".equalsIgnoreCase(base.getProtocol());
            URL current = new URL(base.getProtocol() + "://" + base.getHost()
                    + (base.getPort() > 0 ? ":" + base.getPort() : "") + "/favicon.ico");
            for (int hop = 0; hop < 4; hop++) {
                connection = (HttpURLConnection) current.openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(true);
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.5");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    connection = null;
                    if (location == null || location.isEmpty()) {
                        return null;
                    }
                    URL next = new URL(current, location);
                    String protocol = next.getProtocol();
                    if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                            || (startedHttps && !"https".equalsIgnoreCase(protocol))) {
                        return null;
                    }
                    current = next;
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    return null;
                }
                long contentLength = connection.getContentLengthLong();
                if (contentLength > MAX_FAVICON_BYTES) {
                    return null;
                }
                try (InputStream input = new java.io.BufferedInputStream(connection.getInputStream(), 8192)) {
                    byte[] data = readLimitedBytes(input, MAX_FAVICON_BYTES);
                    return decodeFaviconBytes(data);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private void showCachedFavicon(String url) {
        if (faviconImageView == null || urlEditMode || url == null || url.equals(START_PAGE)) {
            return;
        }
        String key = faviconKey(url);
        if (key == null) {
            return;
        }
        Bitmap cached = faviconCache.get(key);
        if (cached != null) {
            faviconImageView.setImageBitmap(cached);
        } else {
            bindFavicon(faviconImageView, url);
        }
    }
    private void bindFavicon(final ImageView target, final String url) {
        final String key = faviconKey(url);
        target.setTag(key);
        if (key == null) {
            target.setImageResource(R.drawable.transparent_vector);
            return;
        }
        Bitmap cached = faviconCache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageResource(R.drawable.transparent_vector);
        if (!faviconLoading.add(key)) {
            return;
        }
        faviconExecutor.execute(() -> {
            Bitmap loaded = null;
            try {
                loaded = readFaviconFromDisk(key, url);
                if (loaded == null && !faviconMissing.contains(key)) {
                    loaded = fetchFavicon(url);
                    if (loaded != null) {
                        saveFaviconToFile(key, loaded);
                    } else {
                        faviconMissing.add(key);
                    }
                }
            } finally {
                faviconLoading.remove(key);
            }
            final Bitmap result = loaded;
            if (result == null) {
                return;
            }
            runOnUiThread(() -> {
                faviconCache.put(key, result);
                if (key.equals(target.getTag())) {
                    target.setImageBitmap(result);
                }
            });
        });
    }

    private final java.util.HashSet<String> faviconMisses = new java.util.HashSet<>();

    private Bitmap getFaviconBitmap(String url) {
        final String key = faviconKey(url);
        if (key == null) return null;
        Bitmap cached = faviconCache.get(key);
        if (cached != null) return cached;
        if (faviconMisses.contains(url)) return null;
        Bitmap loaded = readFaviconFromDisk(key, url);
        if (loaded != null) {
            faviconCache.put(key, loaded);
        }
        if (loaded == null) {
            if (faviconMisses.size() > 2000) {
                faviconMisses.clear();
            }
            faviconMisses.add(url);
        }
        return loaded;
    }

    private void saveCookiesForTab(final int id, final String url) {
        try {
            if (id == -1 || url == null || CookieSitePolicy.isBlockAllActive() || !BrowserUrlRouter.isWebUrl(url)) return;
            if (!BrowserEngine.get().isAgentReady()) return;
            BrowserEngine.get().getAgent().cookiesGet(PageAgent.json("url", url), new PageAgent.CookieCallback() {
                @Override
                public void onResult(boolean ok, JSONArray list, int count) {
                    if (!ok || list == null || list.length() == 0) return;
                    final String payload = list.toString();
                    backgroundExecutor.execute(() -> {
                        try {
                            File out = new File(getFilesDir(), "tab_cookies_" + id + ".json");
                            try (FileOutputStream fos = new FileOutputStream(out)) {
                                fos.write(payload.getBytes("UTF-8"));
                                fos.flush();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void restoreCookiesForTab(final int id, final String url) {
        try {
            if (id == -1 || url == null || CookieSitePolicy.isBlockAllActive()) return;
            File in = new File(getFilesDir(), "tab_cookies_" + id + ".json");
            if (!in.exists()) return;
            String payload = null;
            try (FileInputStream fis = new FileInputStream(in)) {
                byte[] data = new byte[(int) in.length()];
                int r = fis.read(data);
                if (r > 0) payload = new String(data, 0, r, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (payload == null || !BrowserEngine.get().isAgentReady()) return;
            JSONArray list = new JSONArray(payload);
            BrowserEngine.get().getAgent().cookiesSet(list, new PageAgent.CookieCallback() {
                @Override
                public void onResult(boolean ok, JSONArray result, int count) {
                }
            });
        } catch (Exception ignored) {}
    }

    private void safeDeleteFile(File f) {
        try {
            if (f != null && f.exists()) {
                f.delete();
            }
        } catch (Exception ignored) {}
    }


    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeMimeType(String mimeType, String sourceUrl) {
        if (!isBlank(mimeType)) {
            return mimeType.trim().toLowerCase(Locale.ROOT);
        }
        String inferred = inferMimeTypeFromUrl(sourceUrl);
        return isBlank(inferred) ? null : inferred;
    }

    private String inferMimeTypeFromUrl(String sourceUrl) {
        if (isBlank(sourceUrl)) {
            return null;
        }
        try {
            String path = Uri.parse(sourceUrl).getPath();
            if (isBlank(path)) {
                path = sourceUrl;
            }
            String ext = MimeTypeMap.getFileExtensionFromUrl(path);
            if (!isBlank(ext)) {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
                if (!isBlank(mime)) {
                    return mime;
                }
                ext = ext.toLowerCase(Locale.ROOT);
                if ("jpg".equals(ext) || "jpeg".equals(ext)) return "image/jpeg";
                if ("png".equals(ext)) return "image/png";
                if ("gif".equals(ext)) return "image/gif";
                if ("webp".equals(ext)) return "image/webp";
                if ("bmp".equals(ext)) return "image/bmp";
                if ("svg".equals(ext)) return "image/svg+xml";
                if ("html".equals(ext) || "htm".equals(ext)) return "text/html";
                if ("json".equals(ext)) return "application/json";
                if ("pdf".equals(ext)) return "application/pdf";
                if ("zip".equals(ext)) return "application/zip";
                if ("apk".equals(ext)) return "application/vnd.android.package-archive";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveExtensionFromMimeType(String mimeType) {
        if (isBlank(mimeType)) {
            return "";
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(normalized);
        if (!isBlank(ext)) {
            return "." + ext;
        }
        if (normalized.contains("jpeg")) return ".jpg";
        if (normalized.contains("png")) return ".png";
        if (normalized.contains("gif")) return ".gif";
        if (normalized.contains("webp")) return ".webp";
        if (normalized.contains("bmp")) return ".bmp";
        if (normalized.contains("svg")) return ".svg";
        if (normalized.contains("html")) return ".html";
        if (normalized.contains("json")) return ".json";
        if (normalized.contains("pdf")) return ".pdf";
        if (normalized.contains("zip")) return ".zip";
        if (normalized.contains("apk")) return ".apk";
        if (normalized.equals("application/octet-stream")) return ".bin";
        return "";
    }

    private String ensureExtensionForFileName(String fileName, String mimeType, String sourceUrl) {
        if (isBlank(fileName)) {
            fileName = "download_" + System.currentTimeMillis();
        }
        int dotIndex = fileName.lastIndexOf('.');
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        boolean hasExtension = dotIndex > lastSeparator && dotIndex < fileName.length() - 1;
        if (!hasExtension) {
            String extension = resolveExtensionFromMimeType(mimeType);
            if (isBlank(extension)) {
                String inferred = inferMimeTypeFromUrl(sourceUrl);
                extension = resolveExtensionFromMimeType(inferred);
            }
            if (!isBlank(extension)) {
                fileName += extension;
            }
        }
        return fileName;
    }

    private String buildTimestampFileName(String prefix, String mimeType) {
        String normalizedPrefix = isBlank(prefix) ? "download_" : prefix;
        String fileName = normalizedPrefix + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String extension = resolveExtensionFromMimeType(mimeType);
        if (!isBlank(extension)) {
            fileName += extension;
        }
        return fileName;
    }


    private String getAccurateFileName(String url, String contentDisposition, String mimeType) {
        String fileName = null;
        if (!isBlank(contentDisposition)) {
            fileName = parseFilenameFromContentDisposition(contentDisposition);
        }
        if (isBlank(fileName)) {
            fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        }
        if (isBlank(fileName)) {
            fileName = buildTimestampFileName("download_", mimeType);
        }
        fileName = sanitizeFileName(fileName);
        fileName = ensureExtensionForFileName(fileName, mimeType, url);
        return fileName;
    }



    private static final Pattern CD_FILENAME_STAR = Pattern.compile("filename\\*\\s*=\\s*([^']+)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CD_FILENAME = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_NAME_ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern FILE_NAME_TRAILING = Pattern.compile("[. ]+$");

    private String parseFilenameFromContentDisposition(String contentDisposition) {
        Matcher matcher = CD_FILENAME_STAR.matcher(contentDisposition);
        if (matcher.find()) {
            String charset = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            try {
                return URLDecoder.decode(value, charset);
            } catch (Exception ignored) {
                try {
                    return URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                    return value;
                }
            }
        }
        matcher = CD_FILENAME.matcher(contentDisposition);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }



    private String sanitizeFileName(String name) {
        if (isBlank(name)) {
            return "download_" + System.currentTimeMillis();
        }
        name = name.trim();
        name = FILE_NAME_ILLEGAL.matcher(name).replaceAll("_");
        name = DownloadSupport.sanitizeFileName(name);
        name = FILE_NAME_TRAILING.matcher(name).replaceAll("");
        if (name.isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        if (name.length() > 200) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < name.length() - 1) {
                String base = name.substring(0, dotIndex);
                String ext = name.substring(dotIndex);
                if (base.length() > 200 - ext.length()) {
                    base = base.substring(0, Math.max(1, 200 - ext.length() - 1));
                }
                name = base + ext;
            } else {
                name = name.substring(0, 200);
            }
        }
        return name;
    }

    private void exportAllTabCookies(final ArrayList<String> urls) {
        final ArrayList<String> results = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            results.add(null);
        }
        if (urls.isEmpty() || !BrowserEngine.get().isAgentReady()) {
            AllTabsCookiesZipExporter.export(this, urls, results);
            return;
        }
        final int[] remaining = {urls.size()};
        for (int i = 0; i < urls.size(); i++) {
            final int index = i;
            String url = urls.get(i);
            if (url == null || !BrowserUrlRouter.isWebUrl(url)) {
                remaining[0]--;
                continue;
            }
            BrowserEngine.get().getAgent().cookiesGet(PageAgent.json("url", url), new PageAgent.CookieCallback() {
                @Override
                public void onResult(boolean ok, JSONArray list, int count) {
                    if (ok && list != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int k = 0; k < list.length(); k++) {
                            JSONObject c = list.optJSONObject(k);
                            if (c == null) continue;
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(c.optString("name", "")).append('=').append(c.optString("value", ""));
                        }
                        results.set(index, sb.toString());
                    }
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        AllTabsCookiesZipExporter.export(MainActivity.this, urls, results);
                    }
                }
            });
        }
        if (remaining[0] <= 0) {
            AllTabsCookiesZipExporter.export(this, urls, results);
        }
    }

    private static class AllTabsCookiesZipExporter {
        public static void export(Context context, ArrayList<String> urls, ArrayList<String> cookieStrings) {
            if (urls.isEmpty()) {
                Toast.makeText(context, "タブがありません", Toast.LENGTH_SHORT).show();
                return;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String zipName = "cookies_all_" + timestamp + ".zip";
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            File zipFile = new File(downloadDir, zipName);
            boolean hasCookie = false;
            try (FileOutputStream fos = new FileOutputStream(zipFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i);
                    if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) {
                        continue;
                    }
                    String cookieString = cookieStrings.get(i);
                    if (cookieString == null || cookieString.trim().isEmpty()) {
                        continue;
                    }
                    List<CookieEntry> cookies = parseCookieString(cookieString, url);
                    StringBuilder sb = new StringBuilder();
                    sb.append("# Netscape HTTP Cookie File\n");
                    sb.append("# Generated by Coara Gecko - All Tabs\n");
                    sb.append("# Tab ").append(i+1).append("\n");
                    sb.append("# URL: ").append(url).append("\n\n");
                    for (CookieEntry c : cookies) {
                        sb.append(c.toNetscapeLine()).append("\n");
                    }
                    String domain = getSafeDomain(url);
                    String entryName = "cookies_tab" + (i+1) + "_" + domain + ".txt";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    hasCookie = true;
                }
            } catch (Exception e) {
                Toast.makeText(context, "ZIP保存に失敗しました", Toast.LENGTH_LONG).show();
                e.printStackTrace();
                return;
            }
            if (!hasCookie) {
                zipFile.delete();
                Toast.makeText(context, "Cookieが見つかりませんでした", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, "全タブのcookies.zipを保存しました\n" + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
        private static List<CookieEntry> parseCookieString(String cookieString, String pageUrl) {
            List<CookieEntry> list = new ArrayList<>();
            try {
                URI uri = new URI(pageUrl);
                String host = uri.getHost();
                if (host == null) host = "";
                String domain = host.startsWith(".") ? host : "." + host;
                boolean isSecure = pageUrl.toLowerCase(Locale.ROOT).startsWith("https://");
                String[] pairs = cookieString.split(";\\s*");
                for (String pair : pairs) {
                    if (pair.trim().isEmpty()) continue;
                    String[] nv = pair.split("=", 2);
                    if (nv.length != 2) continue;
                    String name = nv[0].trim();
                    String value = nv[1].trim();
                    if (!name.isEmpty()) {
                        list.add(new CookieEntry(domain, name, value, isSecure));
                    }
                }
            } catch (Exception e) {
                String[] pairs = cookieString.split(";\\s*");
                for (String pair : pairs) {
                    String[] nv = pair.split("=", 2);
                    if (nv.length == 2) {
                        list.add(new CookieEntry(".unknown", nv[0].trim(), nv[1].trim(), false));
                    }
                }
            }
            return list;
        }
        private static String getSafeDomain(String url) {
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                if (host == null) return "unknown";
                return host.replaceAll("[^a-zA-Z0-9.-]", "_");
            } catch (Exception e) {
                return "unknown";
            }
        }
        private static class CookieEntry {
            final String domain;
            final String name;
            final String value;
            final boolean secure;
            CookieEntry(String domain, String name, String value, boolean secure) {
                this.domain = domain;
                this.name = name;
                this.value = value;
                this.secure = secure;
            }
            String toNetscapeLine() {
                return domain + "\tTRUE\t/\t" + (secure ? "TRUE" : "FALSE") + "\t0\t" + name + "\t" + value;
            }
        }
    }
}
