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
import com.coara.browser2.util.BrowserAssetLoader;
import com.coara.browser2.webview.TabOverviewDialog;
import com.coara.browser2.util.TabStateStore;
import com.coara.browser2.util.SafeBrowsingSupport;
import com.coara.browser2.util.TabHistoryStore;
import com.coara.browser2.util.SwipeRefreshPolicy;
import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.BasicAuthManager;
import com.coara.browser2.util.ExternalDownloadTabTracker;
import com.coara.browser2.util.DownloadSupport;
import com.coara.browser2.util.DownloadFallbackManager;
import com.coara.browser2.util.UiThread;
import com.coara.browser2.plugin.PluginHost;
import com.coara.browser2.plugin.PluginManagerActivity;
import com.coara.browser2.plugin.PluginManager;
import com.coara.browser2.plugin.PluginRuntime;
import com.coara.browser2.util.BrowserVisualSettings;
import com.coara.browser2.util.BrowserMenuController;
import com.coara.browser2.util.BrowserThemeManager;
import com.coara.browser2.util.KeyboardSettings;
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

    private View findInPageBarView;
    private EditText etFindQuery;
    private TextView tvFindCount;
    private Button btnFindPrev, btnFindNext, btnFindClose;
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
    private ImageView backgroundImageView;
    private Bitmap backgroundBitmap;
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
    private int currentMatchIndex = 0;
    private int totalMatches = 0;
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
    private final Set<String> faviconMissing = Collections.synchronizedSet(new java.util.HashSet<String>());
    private final Set<String> faviconLoading = Collections.synchronizedSet(new java.util.HashSet<String>());
    private final ExecutorService faviconExecutor = Executors.newFixedThreadPool(2);
    private boolean defaultLoadsImagesAutomatically;
    private boolean defaultLoadsImagesAutomaticallyInitialized = false;
    private boolean fullScreenActive = false;
    private BrowserView fullScreenView = null;
    private int fullScreenPreviousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Map<BrowserView, Bitmap> tabSnapshots = new HashMap<>();
    private final Map<BrowserView, Runnable> pendingSpaHistoryTasks = new HashMap<>();
    
    
    
    private final Map<BrowserView, Boolean> pullToRefreshEligibleCache = new HashMap<>();
    private final Map<BrowserView, PendingTab> pendingTabs = new HashMap<>();
    private final Set<BrowserView> reconcileTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Set<BrowserView> staleTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Map<BrowserView, TabHistoryStore.State> tabHistoryStates = new HashMap<>();
    private final Set<BrowserView> backNavigationTabs = Collections.newSetFromMap(new WeakHashMap<BrowserView, Boolean>());
    private final Map<BrowserView, Integer> virtualBase = new WeakHashMap<>();
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

    public static class HistoryItem {
        private final String title;
        private final String url;
        private final long timestamp;

        public HistoryItem(String title, String url) {
            this(title, url, System.currentTimeMillis());
        }

        public HistoryItem(String title, String url, long timestamp) {
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public long getTimestamp() { return timestamp; }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        maybeRequestLaunchProtection();

        toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        tabHistoryStore = new TabHistoryStore(getFilesDir(), MAX_TAB_HISTORY_SIZE);
        pluginManager = PluginManager.get(this);
        pluginManager.addListener(this);
        lastPluginRuntimeVersion = pluginManager.runtimeVersion();
        checkSentinelAndClearTabsIfNecessary();
        ensureCacheSentinelExists();
        darkModeEnabled = pref.getBoolean(KEY_DARK_MODE, false);
        basicAuthEnabled = pref.getBoolean(KEY_BASIC_AUTH, false);
        zoomEnabled = pref.getBoolean(KEY_ZOOM_ENABLED, true);
        jsEnabled = pref.getBoolean(KEY_JS_ENABLED, true);
        imgBlockEnabled = pref.getBoolean(KEY_IMG_BLOCK_ENABLED, false);
        uaEnabled = pref.getBoolean(KEY_UA_ENABLED, false);
        deskuaEnabled = pref.getBoolean(KEY_DESKUA_ENABLED, false);
        final int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(1024, Math.min(maxMemory / 16, 8 * 1024));
        faviconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
        loadBookmarks();
        loadHistory();
        urlEditText = findViewById(R.id.urlEditText);
        urlContainer = findViewById(R.id.urlContainer);
        urlEditOriginalStrokeColor = urlContainer.getStrokeColor();
        urlEditOriginalStrokeWidth = urlContainer.getStrokeWidth();
        urlEditText.setImeOptions(EditorInfo.IME_ACTION_GO);
        KeyboardSettings.applyToEditor(urlEditText, pref);
        faviconImageView = findViewById(R.id.favicon);
        btnGo = findViewById(R.id.btnGo);
        btnNewTab = findViewById(R.id.btnNewTab);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.white);
        webViewContainer = findViewById(R.id.webViewContainer);
        backgroundImageView = findViewById(R.id.backgroundImageView);
        tabCountTextView = findViewById(R.id.tabCountTextView);
        tabCountTextView.setOnClickListener(v -> showTabsDialog());

        if (pref.contains(KEY_TABS)) {
            loadTabsState();
        } else {
            BrowserView initialWebView = createNewWebView();
            initialWebView.setTag(nextTabId);
            nextTabId++;
            webViews.add(initialWebView);
            currentTabIndex = 0;
            webViewContainer.addView(initialWebView);
            initialWebView.loadUrl(START_PAGE);
            updatePullToRefreshState(initialWebView, START_PAGE);
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
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        copyButton.setLayoutParams(layoutParams);
        parentView.addView(copyButton);

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
            if (current == null) return true;
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
        handleIntent(getIntent());
        urlEditText.clearFocus();
        webViewContainer.setFocusableInTouchMode(true);
        webViewContainer.requestFocus();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
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
        if (current != null && externalTabs.remove(current)) {
            returnToCallingApp(current);
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

    private int virtualCount(BrowserView view) {
        Integer b = virtualBase.get(view);
        return b == null ? 0 : b;
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
            geckoEntries.add(new TabHistoryStore.Entry(u, item.getTitle()));
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
        TabHistoryStore.State prev = getTabHistoryState(view);
        if (reconcileTabs.remove(view) && prev != null && !prev.isEmpty()) {
            int storedIdx = Math.max(0, prev.getCurrentIndex());
            TabHistoryStore.Entry storedCur = prev.getCurrent();
            int base;
            if (storedCur != null && storedCur.getUrl().equals(geckoEntries.get(geckoCur).getUrl())) {
                base = Math.max(0, storedIdx - geckoCur);
            } else if (geckoEntries.size() >= prev.size()) {
                base = 0;
            } else {
                base = storedIdx;
            }
            virtualBase.put(view, Math.min(base, prev.size()));
        }
        ArrayList<TabHistoryStore.Entry> entries = new ArrayList<>();
        int base = virtualCount(view);
        if (prev != null && base > 0) {
            List<TabHistoryStore.Entry> pe = prev.getEntries();
            for (int i = 0; i < base && i < pe.size(); i++) {
                entries.add(pe.get(i));
            }
        }
        int realBase = entries.size();
        entries.addAll(geckoEntries);
        int cur = realBase + geckoCur;
        TabHistoryStore.State next = new TabHistoryStore.State();
        next.replace(entries, cur, MAX_TAB_HISTORY_SIZE);
        int start = Math.max(0, entries.size() - Math.max(1, MAX_TAB_HISTORY_SIZE));
        virtualBase.put(view, Math.max(0, realBase - start));
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
        int base = virtualCount(view);
        TabHistoryStore.State state = getTabHistoryState(view);
        if (base <= 0 || state == null) {
            return false;
        }
        List<TabHistoryStore.Entry> all = state.getEntries();
        if (base > all.size()) {
            return false;
        }
        TabHistoryStore.Entry previous = all.get(base - 1);
        if (previous == null || previous.getUrl().isEmpty()) {
            return false;
        }
        TabHistoryStore.State next = new TabHistoryStore.State(new ArrayList<>(all.subList(0, base)), base - 1);
        virtualBase.put(view, base - 1);
        tabHistoryStates.put(view, next);
        int id = getTabId(view);
        if (id >= 0) {
            persistHistory(id, next, false);
        }
        backNavigationTabs.add(view);
        try {
            view.loadUrlReplacingHistory(BrowserAssetLoader.normalizeLegacyAssetUrl(previous.getUrl()));
            return true;
        } catch (Exception ignored) {
            backNavigationTabs.remove(view);
            return false;
        }
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
                    BrowserUrlRouter.handleUrlLoading(this, getCurrentWebView(), url);
                }
            }
            setIntent(new Intent());
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.trim().isEmpty()) {
                String extracted = extractUrlFromSharedText(sharedText.trim());
                String url = BrowserUrlRouter.normalizeUserInput(extracted);
                if (!url.isEmpty()) {
                    createNewExternalTab(url);
                }
            }
            setIntent(new Intent());
        } else if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            String query = intent.getStringExtra(android.app.SearchManager.QUERY);
            if (query != null && !query.trim().isEmpty()) {
                String url = BrowserUrlRouter.normalizeUserInput(query.trim());
                if (!url.isEmpty()) {
                    createNewExternalTab(url);
                }
            }
            setIntent(new Intent());
        }
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (backgroundImageView != null && backgroundBitmap == null && BrowserVisualSettings.getBackgroundPath(pref) != null) {
            loadBackgroundImage();
        }
        TouchpadOverlayController.onHostResume(this, pref);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
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
    protected void onPause() {
        TouchpadOverlayController.onHostPause(this);
        PluginRuntime.onHostPause(this);
        captureSnapshotIfDirty();
        pauseCurrentWebView();
        pauseAllWebViewTimers();
        flushHistory();
        saveTabsState();
        super.onPause();
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
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        trimBrowserMemory(level);
    }

    @Override
    protected void onDestroy() {
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
        releaseAllWebViews();
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
            int storedIdx = historyState.isEmpty() ? 0 : Math.max(0, historyState.getCurrentIndex());
            virtualBase.put(webView, storedIdx);
            reconcileTabs.add(webView);
            final BrowserView guarded = webView;
            UiThread.mainHandler().postDelayed(() -> {
                if (!webViews.contains(guarded) || pendingTabs.containsKey(guarded) || target == null || target.isEmpty()) {
                    return;
                }
                String now = guarded.getUrl();
                if (now == null || now.isEmpty() || now.equals("about:blank")) {
                    int idx = tabHistoryStates.get(guarded) != null ? Math.max(0, tabHistoryStates.get(guarded).getCurrentIndex()) : 0;
                    virtualBase.put(guarded, idx);
                    guarded.loadUrl(target);
                }
            }, 3500L);
            return;
        }
        virtualBase.put(webView, current != null ? Math.max(0, historyState.getCurrentIndex()) : 0);
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

    private void releaseAllWebViews() {
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
                synchronized (webViews) {
                    if (!webViews.isEmpty()) {
                        try {
                            webViews.get(0).clearCache(false);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            clearTabSnapshots();
            clearBackgroundBitmap();
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
                                    finalSnap.compress(Bitmap.CompressFormat.PNG, 80, fos);
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
    private BrowserView createNewWebView() {
        final BrowserView webView = new BrowserView(this);
        KeyboardSettings.configureWebView(webView, pref);
        webView.setBackgroundColorValue(Color.WHITE);
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
                return BrowserUrlRouter.handleUrlLoading(MainActivity.this, view, url, mainFrame);
            }

            @Override
            public void onPageStarted(BrowserView view, String url) {
                ExternalDownloadTabTracker.onPageStarted(view, url);
                PluginRuntime.onPageStarted(view, url);
                TouchpadController.onPageStarted(view);
                clearPendingSpaHistory(view);
                if (view == getCurrentWebView()) {
                    urlEditText.setText(url);
                    updatePullToRefreshState(view, url);
                    showCachedFavicon(url);
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
            }

            @Override
            public void onPageFinished(BrowserView view, String url) {
                if (url == null) {
                    url = "";
                }
                ExternalDownloadTabTracker.onPageFinished(view, url);
                PluginRuntime.onPageFinished(view, url);
                TouchpadController.onPageFinished(view);
                KeyboardSettings.configureWebView(view, pref);
                applyBrowserVisualSettings(view);
                if (url.startsWith(BrowserConstants.APP_ASSET_BASE_URL)) {
                    BrowserThemeManager.applyToWebView(view, MainActivity.this);
                }
                dirtyStateTabs.add(view);
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
            }

            @Override
            public void onSessionStateChanged(BrowserView view) {
                scheduleTabStateSave(view);
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

    private BrowserView openPopupTab() {
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
        switchToTab(webViews.size() - 1);
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

            if (wasCurrent) {
                try {
                    webViewContainer.removeView(crashed);
                } catch (Exception ignored) {
                }
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
            virtualBase.remove(crashed);
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
        if (backgroundImageView == null || pref == null) {
            return;
        }
        String path = BrowserVisualSettings.getBackgroundPath(pref);
        boolean visible = BrowserVisualSettings.isTransparencyEnabled(pref)
                && path != null
                && new File(path).exists()
                && backgroundBitmap != null
                && !backgroundBitmap.isRecycled();
        backgroundImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void saveSelectedBackgroundImage(Uri uri) {
        if (uri == null || backgroundImageView == null) {
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

                int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
                int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
                Bitmap decoded = decodeBackgroundBitmap(target, targetWidth, targetHeight);
                if (decoded == null) {
                    target.delete();
                    if (hadOld && backup.exists()) {
                        backup.renameTo(target);
                    }
                    throw new IOException("background decode failed");
                }

                runOnUiThread(() -> {
                    if (operationToken != backgroundLoadToken || isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        if (!decoded.isRecycled()) {
                            decoded.recycle();
                        }
                        return;
                    }
                    BrowserVisualSettings.setBackgroundPath(pref, target.getAbsolutePath());
                    clearBackgroundBitmap();
                    backgroundBitmap = decoded;
                    backgroundImageView.setImageBitmap(decoded);
                    backgroundImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    updateBackgroundImageVisibility();
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
        if (backgroundImageView == null || pref == null) {
            return;
        }
        final String path = BrowserVisualSettings.getBackgroundPath(pref);
        final int operationToken = ++backgroundLoadToken;
        if (path == null) {
            clearBackgroundBitmap();
            updateBackgroundImageVisibility();
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
            updateBackgroundImageVisibility();
            return;
        }

        int targetWidth = Math.max(1, backgroundImageView.getWidth());
        int targetHeight = Math.max(1, backgroundImageView.getHeight());
        if (targetWidth <= 1) {
            targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        }
        if (targetHeight <= 1) {
            targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        }

        final File resolvedFile = file;
        final int finalTargetWidth = targetWidth;
        final int finalTargetHeight = targetHeight;
        backgroundExecutor.execute(() -> {
            Bitmap decoded = null;
            try {
                decoded = decodeBackgroundBitmap(resolvedFile, finalTargetWidth, finalTargetHeight);
                if (decoded == null) {
                    throw new IOException("background decode failed");
                }
                Bitmap result = decoded;
                runOnUiThread(() -> {
                    if (operationToken != backgroundLoadToken || isFinishing()
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        if (!result.isRecycled()) {
                            result.recycle();
                        }
                        return;
                    }
                    clearBackgroundBitmap();
                    backgroundBitmap = result;
                    backgroundImageView.setImageBitmap(result);
                    backgroundImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    updateBackgroundImageVisibility();
                });
            } catch (Exception e) {
                if (decoded != null && !decoded.isRecycled()) {
                    decoded.recycle();
                }
                runOnUiThread(() -> {
                    if (operationToken == backgroundLoadToken && !isFinishing()
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed())) {
                        updateBackgroundImageVisibility();
                    }
                });
            }
        });
    }

    private Bitmap decodeBackgroundBitmap(File file, int targetWidth, int targetHeight) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sample = 1;
            while ((bounds.outWidth / sample) > targetWidth * 2
                    || (bounds.outHeight / sample) > targetHeight * 2) {
                sample <<= 1;
                if (sample >= 512) {
                    break;
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                options.inPurgeable = true;
                options.inInputShareable = true;
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
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
        if (backgroundImageView != null) {
            backgroundImageView.setImageDrawable(null);
        }
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            try {
                backgroundBitmap.recycle();
            } catch (Exception ignored) {
            }
        }
        backgroundBitmap = null;
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
            externalTabs.remove(webView);
            ExternalDownloadTabTracker.clear(webView);
            Bitmap bm = tabSnapshots.remove(webView);
            if (bm != null && !bm.isRecycled()) {
                try {
                    bm.recycle();
                } catch (Exception ignored) {
                }
            }
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

    private void exportBookmarksToFile() {
        final String bookmarksJson = pref.getString(KEY_BOOKMARKS, "[]");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String fileName = timeStamp + "-bookmark.json";
        backgroundExecutor.execute(() -> {
            com.coara.browser2.util.PublicStorageWriter.Target target = null;
            try {
                target = com.coara.browser2.util.PublicStorageWriter.openDownloadsTarget(
                        MainActivity.this, fileName, "application/json");
                target.outputStream.write(bookmarksJson.getBytes("UTF-8"));
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
        BrowserView newWebView = createNewWebView();
        newWebView.setTag(nextTabId);
        nextTabId++;
        webViews.add(newWebView);
        tabHistoryStates.put(newWebView, new TabHistoryStore.State());
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
        if (!webViews.isEmpty()) {
            KeyboardSettings.hideKeyboardForWebView(this, getCurrentWebView());
        }
        urlEditMode = true;
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
        if (copyButton != null) copyButton.setVisibility(View.GONE);
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
        MenuItem offscreenPreRasterItem = menu.findItem(R.id.action_offscreen_pre_raster);
        if (offscreenPreRasterItem != null) {
            offscreenPreRasterItem.setChecked(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && BrowserVisualSettings.isOffscreenPreRasterEnabled(pref));
            offscreenPreRasterItem.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        }
        boolean prepared = super.onPrepareOptionsMenu(menu);
        BrowserMenuController.prepare(this, menu);
        return prepared;
    }
    @Override
    @SuppressWarnings("deprecation")
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_tabs) {
            showTabsDialog();
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
            Toast.makeText(MainActivity.this, "履歴、フォームデータ、検索候補、及びタブとCookieを消去しました", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_negapoji) {
            applyNegapoji();
        } else if (id == R.id.action_translate) {
            translatePageToJapanese();
            return true;
        } else if (id == R.id.action_clear_tabs) {
            clearTabs();
        } else if (id == R.id.action_screenshot) {
            takeScreenshot();
        } else if (id == R.id.action_export_all_cookies_zip) {
            ArrayList<BrowserView> tabList;
            ArrayList<String> urlList = new ArrayList<>();
            synchronized (webViews) {
                tabList = new ArrayList<>(webViews);
                for (BrowserView tab : tabList) {
                    PendingTab pending = pendingTabs.get(tab);
                    urlList.add(pending != null ? pending.url : tab.getUrl());
                }
            }
            exportAllTabCookies(urlList);
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

    private void translatePageToJapanese() {
        String currentUrl = getCurrentWebView().getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(MainActivity.this, "翻訳するページが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encodedUrl = URLEncoder.encode(currentUrl, "UTF-8");
            String translateUrl = "https://translate.google.com/translate?hl=ja&sl=auto&tl=ja&u=" + encodedUrl;
            getCurrentWebView().loadUrl(translateUrl);
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(MainActivity.this, "翻訳中にエラーが発生しました", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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
            try {
                w.clearHistory();
                w.clearCache(true);
                w.clearFormData();
                w.clearMatches();
            } catch (Exception ignored) {
            }
        }
        BrowserEngine.get().clearAllBrowsingData();
        clearTabs();
        dirtyStateTabs.clear();
        dirtySnapshotTabs.clear();
        purgeTabFiles();
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
        Toast.makeText(MainActivity.this, "履歴、フォームデータ、検索候補、及びタブとCookieを消去しました", Toast.LENGTH_SHORT).show();
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
    View rootView = getWindow().getDecorView().getRootView();
    int width = rootView.getWidth();
    int height = rootView.getHeight();
    if (width <= 0 || height <= 0) {
        Toast.makeText(MainActivity.this, "スクリーンショット取得エラー: ビューサイズが無効", Toast.LENGTH_SHORT).show();
        return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Handler handler = UiThread.mainHandler();
        PixelCopy.request(getWindow(), bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                saveScreenshot(bitmap);
            } else {
                Toast.makeText(MainActivity.this, "スクリーンショットの取得に失敗しました", Toast.LENGTH_SHORT).show();
            }
        }, handler);
    } else {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        rootView.draw(canvas);
        saveScreenshot(bitmap);
    }
}

private void saveScreenshot(Bitmap bitmap) {
    backgroundExecutor.execute(() -> {
        try {
            File screenshotDir = new File(Environment.getExternalStorageDirectory(), "DCIM/Screenshot");
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = timeStamp + ".png";
            File screenshotFile = new File(screenshotDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(screenshotFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, fos); 
                fos.flush();
            }
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, "スクリーンショットを保存しました: " + screenshotFile.getAbsolutePath(), Toast.LENGTH_LONG).show()
            );
        } catch (Exception e) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, "スクリーンショット保存中にエラー: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    });
}

    private void updateDarkMode() {
        BrowserEngine.get().setDarkMode(darkModeEnabled);
        reloadTabsForSettings();
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

    private void showFindInPageBar() {
    if (findInPageBarView == null) {
        LayoutInflater inflater = LayoutInflater.from(this);
        findInPageBarView = inflater.inflate(R.layout.find_in_page_bar, null);
        etFindQuery = findInPageBarView.findViewById(R.id.etFindQuery);
        tvFindCount = findInPageBarView.findViewById(R.id.tvFindCount);
        btnFindPrev = findInPageBarView.findViewById(R.id.btnFindPrev);
        btnFindNext = findInPageBarView.findViewById(R.id.btnFindNext);
        btnFindClose = findInPageBarView.findViewById(R.id.btnFindClose);

        etFindQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        KeyboardSettings.applyToEditor(etFindQuery, pref);
        etFindQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performFindInPage();
                    return true;
                }
                return false;
            }
        });
        etFindQuery.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    performFindInPage();
                    return true;
                }
                return false;
            }
        });

        btnFindNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (totalMatches > 0) {
                    getCurrentWebView().findNext(true, findCallback);
                }
            }
        });
        btnFindPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (totalMatches > 0) {
                    getCurrentWebView().findNext(false, findCallback);
                }
            }
        });
        btnFindClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etFindQuery.setText("");
                hideFindInPageBar();
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        addContentView(findInPageBarView, params);
    }
    findInPageBarView.setVisibility(View.VISIBLE);
    etFindQuery.requestFocus();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
        imm.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT);
    }
}
private void performFindInPage() {
    String query = etFindQuery.getText().toString().trim();
    if (query.isEmpty()) {
        getCurrentWebView().clearMatches();
        tvFindCount.setText("0/0");
        totalMatches = 0;
        return;
    }
    
    getCurrentWebView().clearMatches();
    currentMatchIndex = 0;
    getCurrentWebView().findAllAsync(query, findCallback);
}
private final BrowserView.FindCallback findCallback = new BrowserView.FindCallback() {
    @Override
    public void onFindResult(int activeIndex, int total) {
        currentMatchIndex = activeIndex;
        totalMatches = total;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvFindCount == null) {
                    return;
                }
                if (totalMatches > 0) {
                    tvFindCount.setText((currentMatchIndex + 1) + "/" + totalMatches);
                } else {
                    tvFindCount.setText("0/0");
                }
            }
        });
    }
};
private void hideFindInPageBar() {
    if (findInPageBarView != null) {
        findInPageBarView.setVisibility(View.GONE);
        getCurrentWebView().clearMatches();
        if (tvFindCount != null) {
            tvFindCount.setText("0/0");
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFindQuery.getWindowToken(), 0);
        }
    }
}
private void showHistoryDialog() {
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("履歴")
            .setPositiveButton("閉じる", null)
            .setView(recyclerView)
            .create();
        HistoryAdapter adapter = new HistoryAdapter(historyItems, dialog);
        recyclerView.setAdapter(adapter);
        dialog.show();
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
                || !snapshotInFlight.add(webView)) {
            if (done != null) {
                done.run();
            }
            return;
        }
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
                    int maxWidth = 360;
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
                    snapshotInFlight.remove(webView);
                    if (result != null && webViews.contains(webView)) {
                        tabSnapshots.put(webView, result);
                        dirtySnapshotTabs.add(webView);
                    }
                    if (done != null) {
                        done.run();
                    }
                });
            });
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
        new MaterialAlertDialogBuilder(MainActivity.this)
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
                Toast.makeText(MainActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(MainActivity.this, "ブックマークを追加しました", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(MainActivity.this, "ブックマークをインポートしました", Toast.LENGTH_SHORT).show();
    } catch (IOException e) {
        Toast.makeText(MainActivity.this, "ファイルの読み取りに失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        e.printStackTrace();
    } catch (JSONException e) {
        Toast.makeText(MainActivity.this, "JSON解析エラー: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                faviconExecutor.execute(() -> {
                    String key = faviconKey(url);
                    if (key == null || faviconCache.get(key) != null || readFaviconFromDisk(key, url) != null) {
                        return;
                    }
                    Bitmap favicon = normalizeFavicon(fetchFavicon(url));
                    if (favicon != null) {
                        faviconCache.put(key, favicon);
                        saveFaviconToFile(key, favicon);
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
        if (!file.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private Bitmap fetchFavicon(String pageUrl) {
        try {
            URL base = new URL(pageUrl);
            String target = base.getProtocol() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + "/favicon.ico";
            for (int hop = 0; hop < 4; hop++) {
                HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setInstanceFollowRedirects(false);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location == null) {
                        return null;
                    }
                    target = new URL(new URL(target), location).toString();
                    continue;
                }
                if (code == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = connection.getInputStream()) {
                        return BitmapFactory.decodeStream(is);
                    } finally {
                        connection.disconnect();
                    }
                }
                connection.disconnect();
                return null;
            }
        } catch (Exception ignored) {
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
                    loaded = normalizeFavicon(fetchFavicon(url));
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

    private static final class HistoryRow {
    static final int TYPE_HEADER = 0;
    static final int TYPE_ITEM = 1;

    final int type;
    final String header;
    final HistoryItem item;

    private HistoryRow(int type, String header, HistoryItem item) {
        this.type = type;
        this.header = header;
        this.item = item;
    }

    static HistoryRow header(String header) {
        return new HistoryRow(TYPE_HEADER, header, null);
    }

    static HistoryRow item(HistoryItem item) {
        return new HistoryRow(TYPE_ITEM, null, item);
    }
}

private class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<HistoryItem> sourceItems;
    private final List<HistoryRow> rows = new ArrayList<>();
    private final AlertDialog dialog;
    private final SimpleDateFormat headerFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
    private final SimpleDateFormat groupKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

    HistoryAdapter(List<HistoryItem> items, AlertDialog dialog) {
        this.sourceItems = items;
        this.dialog = dialog;
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        String lastGroupKey = null;
        ArrayList<HistoryItem> copy;
        synchronized (sourceItems) {
            copy = new ArrayList<>(sourceItems);
        }
        Collections.reverse(copy);
        for (HistoryItem item : copy) {
            String groupKey = groupKeyFormat.format(new Date(item.getTimestamp()));
            if (!groupKey.equals(lastGroupKey)) {
                rows.add(HistoryRow.header(headerFormat.format(new Date(item.getTimestamp()))));
                lastGroupKey = groupKey;
            }
            rows.add(HistoryRow.item(item));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == HistoryRow.TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_history_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        HistoryRow row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).date.setText(row.header);
            return;
        }
        HistoryItem item = row.item;
        HistoryViewHolder historyHolder = (HistoryViewHolder) holder;
        historyHolder.title.setText((item.getTitle() != null && !item.getTitle().isEmpty()) ? item.getTitle() : item.getUrl());
        historyHolder.url.setText(item.getUrl());
        bindFavicon(historyHolder.favicon, item.getUrl());
        historyHolder.itemView.setOnClickListener(v -> {
            getCurrentWebView().loadUrl(item.getUrl());
            dialog.dismiss();
        });
        historyHolder.itemView.setOnLongClickListener(v -> {
            int currentPosition = historyHolder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return true;
            HistoryRow currentRow = rows.get(currentPosition);
            if (currentRow.type != HistoryRow.TYPE_ITEM || currentRow.item == null) return true;
            HistoryItem currentItem = currentRow.item;
            String[] options = {"URLコピー", "削除"};
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("操作を選択")
                    .setItems(options, (dialogInterface, which) -> {
                        if (which == 0) {
                            copyLink(currentItem.getUrl());
                        } else if (which == 1) {
                            sourceItems.remove(currentItem);
                            rebuildRows();
                            notifyDataSetChanged();
                            saveHistory();
                            Toast.makeText(MainActivity.this, "削除しました", Toast.LENGTH_SHORT).show();
                        }
                    }).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView date;

        HeaderViewHolder(View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.historyDateHeader);
        }
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView favicon;
        TextView title;
        TextView url;

        HistoryViewHolder(View itemView) {
            super(itemView);
            favicon = itemView.findViewById(R.id.historyFavicon);
            title = itemView.findViewById(R.id.historyTitle);
            url = itemView.findViewById(R.id.historyUrl);
        }
    }
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
            bindFavicon(holder.favicon, bm.getUrl());
            holder.itemView.setOnClickListener(v -> {
                getCurrentWebView().loadUrl(bm.getUrl());
                dialog.dismiss();
            });
            if (managementMode) {
            holder.itemView.setOnLongClickListener(v -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return true;
                String[] options = {"編集", "削除"};
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("操作を選択")
                        .setItems(options, (dialogInterface, which) -> {
                            if (which == 0) {
                                showEditBookmarkDialog(currentPosition, this);
                            } else if (which == 1) {
                                items.remove(currentPosition);
                                notifyItemRemoved(currentPosition);
                                saveBookmarks();
                                Toast.makeText(MainActivity.this, "削除しました", Toast.LENGTH_SHORT).show();
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

    private void saveCookiesForTab(final int id, final String url) {
        try {
            if (id == -1 || url == null || !BrowserUrlRouter.isWebUrl(url)) return;
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
            if (id == -1 || url == null) return;
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
