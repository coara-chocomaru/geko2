package com.coara.browser2;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSessionToken;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import org.json.JSONException;
import org.json.JSONObject;

import com.coara.browser2.gecko.BrowserView;
import com.coara.browser2.util.BrowserUrlRouter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomTabActivity extends MainActivity {
    private static final int MENU_RELOAD = 0x710001;
    private static final int MENU_SHARE = 0x710002;
    private static final int MENU_OPEN_BROWSER = 0x710003;
    private static final int MENU_CLOSE = 0x710004;
    private static final int MENU_ACTION_BUTTON = 0x710005;
    private static final int MENU_COPY_URL = 0x710006;
    private static final int MENU_CUSTOM_BASE = 0x711000;
    private static final int MAX_CUSTOM_ITEMS = 5;
    private static final Object ACTIVE_LOCK = new Object();
    private static WeakReference<CustomTabActivity> activeActivity = new WeakReference<>(null);

    private CustomTabsSessionToken sessionToken;
    private MaterialToolbar customToolbar;
    private TextInputEditText urlText;
    private MaterialCardView urlContainer;
    private ImageView securityIcon;
    private View progressBar;
    private Bundle actionButtonBundle = new Bundle();
    private final List<ToolbarAction> customActions = new ArrayList<>();
    private int toolbarColor;
    private int titleVisibility = CustomTabsIntent.NO_TITLE;
    private boolean invalidCustomIntent;
    private boolean customTabsIntentRecognized;
    private boolean shareEnabled = true;
    private boolean tintActionButton;
    private boolean showing;
    private String verifiedPostMessageOrigin;
    private String activePostMessageChannelId;
    private boolean postMessageChannelCommandSent;
    private boolean postMessageChannelReady;
    private long postMessageChannelSequence;
    private boolean scrollHidingEnabled;
    private boolean toolbarHidden;
    private boolean pageFailedSinceStart;
    private int toolbarExpandedHeight;
    private int lastScrollY;
    private ValueAnimator toolbarAnimator;

    @Override
    protected boolean isCustomTabMode() {
        return true;
    }

    @Override
    protected boolean shouldRunCustomTabForIntent(Intent intent) {
        if (customTabsIntentRecognized || hasCustomTabsIntentExtras(intent)) {
            return true;
        }
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && isAllowedUrl(intent.getData());
    }

    private boolean hasCustomTabsIntentExtras(Intent intent) {
        if (intent == null) {
            return false;
        }
        try {
            if (intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)) {
                return true;
            }
            Bundle extras = intent.getExtras();
            if (extras == null) {
                return false;
            }
            for (String key : extras.keySet()) {
                if (key != null && (key.startsWith("android.support.customtabs.extra.")
                        || key.startsWith("androidx.browser.customtabs.extra."))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    protected String getCustomTabInitialUrl() {
        Uri data = getIntent() == null ? null : getIntent().getData();
        return isAllowedUrl(data) ? data.normalizeScheme().toString() : "about:blank";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Intent incoming = getIntent();
        sessionToken = extractSessionToken(incoming);
        customTabsIntentRecognized = hasCustomTabsIntentExtras(incoming);
        Intent safe = sanitizeIntent(incoming);
        invalidCustomIntent = safe.getData() == null || "about".equalsIgnoreCase(safe.getData().getScheme());
        setIntent(safe);
        applyIntentOptions(safe);
        super.onCreate(savedInstanceState);
        initializeCustomToolbar();
        if (invalidCustomIntent) {
            finish();
        }
    }

    @Override
    protected void onCustomTabIntent(Intent intent) {
        Intent safe = sanitizeIntent(intent);
        invalidCustomIntent = safe.getData() == null || "about".equalsIgnoreCase(safe.getData().getScheme());
        setIntent(safe);
        applyIntentOptions(safe);
        initializeCustomToolbar();
        Uri data = safe.getData();
        BrowserView current = getCustomTabBrowserView();
        if (invalidCustomIntent || current == null || !isAllowedUrl(data)) {
            finish();
            return;
        }
        current.stopLoading();
        current.loadUrl(data.normalizeScheme().toString());
        current.requestFocus();
    }

    @Override
    protected void onCustomTabPageStarted(BrowserView view) {
        pageFailedSinceStart = false;
        clearActivePostMessageChannel(view, true);
        lastScrollY = 0;
        setToolbarHidden(false);
        updateCustomToolbar(view);
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
            if (progressBar instanceof android.widget.ProgressBar) {
                ((android.widget.ProgressBar) progressBar).setProgress(0);
            }
        }
        dispatchNavigationEvent(CustomTabsCallback.NAVIGATION_STARTED);
    }

    @Override
    protected void onCustomTabPageChanged(BrowserView view) {
        updateCustomToolbar(view);
        tryStartPostMessageChannel(view);
    }

    @Override
    protected void onCustomTabPageFinished(BrowserView view) {
        updateCustomToolbar(view);
        tryStartPostMessageChannel(view);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (pageFailedSinceStart) {
            pageFailedSinceStart = false;
        } else {
            dispatchNavigationEvent(CustomTabsCallback.NAVIGATION_FINISHED);
        }
    }

    @Override
    protected void onCustomTabPageFailed(BrowserView view) {
        pageFailedSinceStart = true;
        updateCustomToolbar(view);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        clearActivePostMessageChannel(view, true);
        dispatchNavigationEvent(CustomTabsCallback.NAVIGATION_FAILED);
    }

    @Override
    protected void onCustomTabMessage(BrowserView view, JSONObject message) {
        if (view == null || view != getCustomTabBrowserView() || message == null) {
            return;
        }
        String type = message.optString("t", "");
        String id = message.optString("id", "");
        String origin = message.optString("origin", "");
        if (activePostMessageChannelId == null || !activePostMessageChannelId.equals(id)
                || verifiedPostMessageOrigin == null || !verifiedPostMessageOrigin.equals(origin)
                || !verifiedPostMessageOrigin.equals(pageOrigin(view.getUrl()))) {
            return;
        }
        if ("cct.channelReady".equals(type)) {
            postMessageChannelReady = true;
            postMessageChannelCommandSent = true;
            dispatchMessageChannelReady();
        } else if ("cct.channelError".equals(type)) {
            postMessageChannelReady = false;
            postMessageChannelCommandSent = false;
            activePostMessageChannelId = null;
        } else if ("cct.message".equals(type) && postMessageChannelReady) {
            String text = message.optString("message", "");
            if (text.length() <= 16 * 1024) {
                CustomTabsSessionToken token = sessionToken;
                if (token != null && token.getCallback() != null) {
                    try {
                        token.getCallback().onPostMessage(text, null);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
    }

    @Override
    protected void onCustomTabScrollChanged(int x, int y) {
        if (!scrollHidingEnabled || customToolbar == null) {
            lastScrollY = y;
            return;
        }
        int delta = y - lastScrollY;
        if (y <= 24) {
            setToolbarHidden(false);
        } else if (delta >= 12) {
            setToolbarHidden(true);
        } else if (delta <= -12) {
            setToolbarHidden(false);
        }
        lastScrollY = y;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        customTabsIntentRecognized = hasCustomTabsIntentExtras(intent);
        sessionToken = extractSessionToken(intent);
        Intent safe = sanitizeIntent(intent);
        setIntent(safe);
        applyIntentOptions(safe);
        super.onNewIntent(safe);
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (ACTIVE_LOCK) {
            activeActivity = new WeakReference<>(this);
            showing = true;
        }
        dispatchNavigationEvent(CustomTabsCallback.TAB_SHOWN);
        String validatedOrigin = CustomTabsService.getVerifiedPostMessageOrigin(sessionToken);
        if (validatedOrigin != null) {
            verifiedPostMessageOrigin = validatedOrigin;
        }
        updateCustomToolbar(getCustomTabBrowserView());
        tryStartPostMessageChannel(getCustomTabBrowserView());
    }

    @Override
    protected void onPause() {
        showing = false;
        dispatchNavigationEvent(CustomTabsCallback.TAB_HIDDEN);
        synchronized (ACTIVE_LOCK) {
            if (activeActivity.get() == this) {
                activeActivity = new WeakReference<>(null);
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        synchronized (ACTIVE_LOCK) {
            if (activeActivity.get() == this) {
                activeActivity = new WeakReference<>(null);
            }
        }
        clearActivePostMessageChannel(getCustomTabBrowserView(), false);
        customActions.clear();
        actionButtonBundle.clear();
        if (toolbarAnimator != null) {
            toolbarAnimator.cancel();
            toolbarAnimator = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (actionButtonBundle.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT) instanceof PendingIntent
                && actionButtonBundle.getParcelable(CustomTabsIntent.KEY_ICON) instanceof Bitmap) {
            String description = actionButtonBundle.getString(CustomTabsIntent.KEY_DESCRIPTION, "アプリのアクション");
            MenuItem action = menu.add(Menu.NONE, MENU_ACTION_BUTTON, 0, description);
            Bitmap icon = actionButtonBundle.getParcelable(CustomTabsIntent.KEY_ICON);
            action.setIcon(createActionButtonDrawable(icon));
            action.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            action.setContentDescription(description);
        }
        int order = 1;
        for (ToolbarAction action : customActions) {
            MenuItem item = menu.add(Menu.NONE, action.menuId, order++, action.title);
            if (action.icon != null && !action.icon.isRecycled()) {
                item.setIcon(new BitmapDrawable(getResources(), action.icon));
            }
            if (action.toolbarItem) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                item.setContentDescription(action.title);
            }
        }
        menu.add(Menu.NONE, MENU_RELOAD, 100, "再読み込み");
        menu.add(Menu.NONE, MENU_COPY_URL, 101, "URLをコピー");
        if (shareEnabled) {
            menu.add(Menu.NONE, MENU_SHARE, 102, "共有");
        }
        menu.add(Menu.NONE, MENU_OPEN_BROWSER, 103, "通常ブラウザで開く");
        menu.add(Menu.NONE, MENU_CLOSE, 104, "閉じる");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_RELOAD) {
            BrowserView view = getCustomTabBrowserView();
            if (view != null) {
                view.reload();
            }
            return true;
        }
        if (id == MENU_COPY_URL) {
            BrowserView view = getCustomTabBrowserView();
            String url = view == null ? null : view.getUrl();
            if (url != null && isAllowedUrl(Uri.parse(url))) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", url));
                    Toast.makeText(this, "URLをコピーしました", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (id == MENU_SHARE) {
            shareCurrentPage();
            return true;
        }
        if (id == MENU_OPEN_BROWSER) {
            openInFullBrowser();
            return true;
        }
        if (id == MENU_CLOSE) {
            finish();
            return true;
        }
        if (id == MENU_ACTION_BUTTON) {
            sendPendingIntent(actionButtonBundle.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT));
            return true;
        }
        for (ToolbarAction action : customActions) {
            if (action.menuId == id) {
                sendPendingIntent(action.pendingIntent);
                return true;
            }
        }
        return true;
    }

    private Drawable createActionButtonDrawable(Bitmap icon) {
        Drawable drawable = new BitmapDrawable(getResources(), icon);
        if (tintActionButton && drawable != null) {
            drawable = DrawableCompat.wrap(drawable.mutate());
            int foreground = ColorUtils.calculateLuminance(toolbarColor) > 0.52 ? Color.BLACK : Color.WHITE;
            DrawableCompat.setTint(drawable, foreground);
        }
        return drawable;
    }

    static boolean updateVisualsForSession(CustomTabsSessionToken token, Bundle bundle) {
        synchronized (ACTIVE_LOCK) {
            CustomTabActivity activity = activeActivity.get();
            if (activity == null || !activity.showing || token == null || activity.sessionToken == null
                    || !activity.sessionToken.equals(token)) {
                return false;
            }
            Bundle safe = CustomTabsService.sanitizeVisualsBundle(bundle);
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    activity.applyUpdatedVisuals(safe);
                }
            });
            return true;
        }
    }

    private void applyUpdatedVisuals(Bundle visuals) {
        if (visuals == null) {
            return;
        }
        if (visuals.containsKey(CustomTabsIntent.EXTRA_TOOLBAR_COLOR)) {
            toolbarColor = visuals.getInt(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, toolbarColor);
            if (customToolbar != null) {
                customToolbar.setBackgroundColor(toolbarColor);
            }
            int foreground = ColorUtils.calculateLuminance(toolbarColor) > 0.52 ? Color.BLACK : Color.WHITE;
            if (urlText != null) {
                urlText.setTextColor(foreground);
                urlText.setHintTextColor(foreground);
            }
            if (customToolbar != null) {
                Drawable navigation = customToolbar.getNavigationIcon();
                if (navigation != null) {
                    navigation = DrawableCompat.wrap(navigation.mutate());
                    DrawableCompat.setTint(navigation, foreground);
                    customToolbar.setNavigationIcon(navigation);
                }
            }
        }
        if (visuals.containsKey(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR)) {
            getWindow().setNavigationBarColor(visuals.getInt(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR, toolbarColor));
        }
        if (android.os.Build.VERSION.SDK_INT >= 28
                && visuals.containsKey(CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR)) {
            getWindow().setNavigationBarDividerColor(visuals.getInt(
                    CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR, toolbarColor));
        }
        if (visuals.containsKey(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON)) {
            tintActionButton = visuals.getBoolean(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON, false);
        }
        if (visuals.containsKey(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE)) {
            Bundle action = visuals.getBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE);
            actionButtonBundle = CustomTabsService.sanitizeActionButton(action);
        }
        invalidateOptionsMenu();
    }

    static void onPostMessageOriginVerified(CustomTabsSessionToken token, String origin) {
        synchronized (ACTIVE_LOCK) {
            CustomTabActivity activity = activeActivity.get();
            if (activity == null || token == null || activity.sessionToken == null
                    || !activity.sessionToken.equals(token)) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || activity.sessionToken == null
                        || !activity.sessionToken.equals(token)) {
                    return;
                }
                activity.clearActivePostMessageChannel(activity.getCustomTabBrowserView(), true);
                activity.verifiedPostMessageOrigin = origin;
                activity.tryStartPostMessageChannel(activity.getCustomTabBrowserView());
            });
        }
    }

    static void clearPostMessageChannelForSession(CustomTabsSessionToken token) {
        synchronized (ACTIVE_LOCK) {
            CustomTabActivity activity = activeActivity.get();
            if (activity == null || token == null || activity.sessionToken == null
                    || !activity.sessionToken.equals(token)) {
                return;
            }
            activity.runOnUiThread(() -> activity.clearActivePostMessageChannel(activity.getCustomTabBrowserView(), false));
        }
    }

    static boolean postMessageForSession(CustomTabsSessionToken token, String message) {
        if (token == null || message == null || message.length() > 16 * 1024) {
            return false;
        }
        synchronized (ACTIVE_LOCK) {
            CustomTabActivity activity = activeActivity.get();
            if (activity == null || !activity.showing || activity.sessionToken == null
                    || !activity.sessionToken.equals(token) || !activity.postMessageChannelReady
                    || activity.activePostMessageChannelId == null || activity.verifiedPostMessageOrigin == null) {
                return false;
            }
            String id = activity.activePostMessageChannelId;
            String origin = activity.verifiedPostMessageOrigin;
            BrowserView current = activity.getCustomTabBrowserView();
            if (current == null || !origin.equals(pageOrigin(current.getUrl()))) {
                return false;
            }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || !activity.showing || activity.sessionToken == null
                        || !activity.sessionToken.equals(token) || !activity.postMessageChannelReady
                        || !id.equals(activity.activePostMessageChannelId)
                        || !origin.equals(pageOrigin(activity.getCustomTabBrowserView() == null
                        ? null : activity.getCustomTabBrowserView().getUrl()))) {
                    return;
                }
                JSONObject command = new JSONObject();
                try {
                    command.put("t", "cct.send");
                    command.put("id", id);
                    command.put("origin", origin);
                    command.put("message", message);
                } catch (JSONException ignored) {
                    return;
                }
                BrowserView view = activity.getCustomTabBrowserView();
                if (view != null) {
                    view.sendCustomTabsMessage(command);
                }
            });
            return true;
        }
    }

    private void tryStartPostMessageChannel(BrowserView view) {
        if (view == null || verifiedPostMessageOrigin == null || postMessageChannelReady
                || postMessageChannelCommandSent || view.isLoading()
                || !verifiedPostMessageOrigin.equals(pageOrigin(view.getUrl()))) {
            return;
        }
        String id = Integer.toHexString(System.identityHashCode(this)) + Long.toHexString(SystemClock.elapsedRealtime())
                + Long.toHexString(++postMessageChannelSequence);
        JSONObject command = new JSONObject();
        try {
            command.put("t", "cct.open");
            command.put("id", id);
            command.put("origin", verifiedPostMessageOrigin);
        } catch (JSONException ignored) {
            return;
        }
        if (view.sendCustomTabsMessage(command)) {
            activePostMessageChannelId = id;
            postMessageChannelCommandSent = true;
        }
    }

    private void clearActivePostMessageChannel(BrowserView view, boolean keepGrant) {
        String oldId = activePostMessageChannelId;
        String oldOrigin = verifiedPostMessageOrigin;
        if (view != null && oldId != null && oldOrigin != null) {
            JSONObject command = new JSONObject();
            try {
                command.put("t", "cct.close");
                command.put("id", oldId);
                command.put("origin", oldOrigin);
                view.sendCustomTabsMessage(command);
            } catch (JSONException ignored) {
            }
        }
        activePostMessageChannelId = null;
        postMessageChannelCommandSent = false;
        postMessageChannelReady = false;
        if (!keepGrant) {
            verifiedPostMessageOrigin = null;
        }
    }

    private void dispatchMessageChannelReady() {
        CustomTabsSessionToken token = sessionToken;
        if (token == null || token.getCallback() == null) {
            return;
        }
        try {
            token.getCallback().onMessageChannelReady(null);
        } catch (RuntimeException ignored) {
        }
    }

    private static String pageOrigin(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443) {
                return null;
            }
            return "https://" + java.net.IDN.toASCII(uri.getHost(), java.net.IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private void setToolbarHidden(boolean hidden) {
        if (customToolbar == null || toolbarExpandedHeight <= 0 || toolbarHidden == hidden) {
            return;
        }
        if (toolbarAnimator != null) {
            toolbarAnimator.cancel();
            toolbarAnimator = null;
        }
        int currentHeight = customToolbar.getLayoutParams().height;
        int targetHeight = hidden ? 0 : toolbarExpandedHeight;
        if (currentHeight == targetHeight) {
            toolbarHidden = hidden;
            return;
        }
        toolbarAnimator = ValueAnimator.ofInt(Math.max(0, currentHeight), targetHeight);
        toolbarAnimator.setDuration(120L);
        toolbarAnimator.addUpdateListener(animation -> {
            if (customToolbar == null || isDestroyed()) {
                return;
            }
            android.view.ViewGroup.LayoutParams params = customToolbar.getLayoutParams();
            params.height = (Integer) animation.getAnimatedValue();
            customToolbar.setLayoutParams(params);
        });
        toolbarAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                toolbarHidden = hidden;
                toolbarAnimator = null;
            }
        });
        toolbarAnimator.start();
    }

    private void initializeCustomToolbar() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        customToolbar = findViewById(R.id.topAppBar);
        if (customToolbar != null && toolbarExpandedHeight == 0 && customToolbar.getLayoutParams() != null) {
            toolbarExpandedHeight = customToolbar.getLayoutParams().height;
        }
        urlText = findViewById(R.id.urlEditText);
        urlContainer = findViewById(R.id.urlContainer);
        securityIcon = findViewById(R.id.urlSecurityIcon);
        progressBar = findViewById(R.id.pageProgressBar);
        if (customToolbar == null || urlText == null || urlContainer == null) {
            return;
        }
        toolbarColor = getIntent().getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, getColorCompat(R.color.colorPrimary));
        customToolbar.setBackgroundColor(toolbarColor);
        tintActionButton = getIntent().getBooleanExtra(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON, false);
        int foreground = ColorUtils.calculateLuminance(toolbarColor) > 0.52 ? Color.BLACK : Color.WHITE;
        urlText.setTextColor(foreground);
        urlText.setHintTextColor(foreground);
        urlText.setFocusable(false);
        urlText.setFocusableInTouchMode(false);
        urlText.setCursorVisible(false);
        urlText.setLongClickable(true);
        urlText.setTextIsSelectable(true);
        urlContainer.setOnClickListener(view -> { });
        urlText.setOnClickListener(view -> { });
        urlText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                urlText.clearFocus();
                hideKeyboard();
            }
        });
        View newTab = findViewById(R.id.btnNewTab);
        if (newTab != null) {
            newTab.setVisibility(View.GONE);
        }
        View tabCounter = findViewById(R.id.tabCountTextView);
        if (tabCounter != null) {
            tabCounter.setVisibility(View.GONE);
        }
        View go = findViewById(R.id.btnGo);
        if (go != null) {
            go.setVisibility(View.GONE);
        }
        View star = findViewById(R.id.bookmarkStar);
        if (star != null) {
            star.setVisibility(View.GONE);
        }
        View menu = findViewById(R.id.themeOverflowButton);
        if (menu != null) {
            menu.setVisibility(View.GONE);
        }
        Bitmap closeIcon = null;
        try {
            closeIcon = getIntent().getParcelableExtra(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON);
        } catch (RuntimeException ignored) {
        }
        Drawable closeDrawable = closeIcon != null && !closeIcon.isRecycled()
                ? new BitmapDrawable(getResources(), closeIcon)
                : getDrawableCompat(android.R.drawable.ic_menu_close_clear_cancel);
        if (closeDrawable != null) {
            closeDrawable = DrawableCompat.wrap(closeDrawable.mutate());
            DrawableCompat.setTint(closeDrawable, foreground);
            customToolbar.setNavigationIcon(closeDrawable);
        }
        customToolbar.setNavigationContentDescription("閉じる");
        customToolbar.setNavigationOnClickListener(view -> finish());
        if (getIntent().hasExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR)) {
            getWindow().setNavigationBarColor(getIntent().getIntExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR, toolbarColor));
        }
        if (android.os.Build.VERSION.SDK_INT >= 28
                && getIntent().hasExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR)) {
            getWindow().setNavigationBarDividerColor(getIntent().getIntExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR, foreground));
        }
        shareEnabled = getIntent().getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, CustomTabsIntent.SHARE_STATE_DEFAULT)
                != CustomTabsIntent.SHARE_STATE_OFF;
        scrollHidingEnabled = getIntent().getBooleanExtra(CustomTabsIntent.EXTRA_ENABLE_URLBAR_HIDING, false);
        actionButtonBundle = CustomTabsService.sanitizeActionButton(getIntent().getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE));
        parseCustomActions(getIntent());
        updateCustomToolbar(getCustomTabBrowserView());
        invalidateOptionsMenu();
    }

    private void applyIntentOptions(Intent safeIntent) {
        actionButtonBundle = CustomTabsService.sanitizeActionButton(safeIntent.getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE));
        toolbarColor = safeIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, getColorCompat(R.color.colorPrimary));
        titleVisibility = safeIntent.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE);
        shareEnabled = customTabsIntentRecognized
                && safeIntent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, CustomTabsIntent.SHARE_STATE_DEFAULT)
                != CustomTabsIntent.SHARE_STATE_OFF;
        scrollHidingEnabled = safeIntent.getBooleanExtra(CustomTabsIntent.EXTRA_ENABLE_URLBAR_HIDING, false);
        tintActionButton = safeIntent.getBooleanExtra(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON, false);
        customActions.clear();
        parseCustomActions(safeIntent);
    }

    private void parseCustomActions(Intent intent) {
        customActions.clear();
        Bundle extras = intent == null ? null : intent.getExtras();
        if (extras == null) {
            return;
        }
        try {
            extras.setClassLoader(CustomTabsIntent.class.getClassLoader());
            ArrayList<Bundle> menuItems = extras.getParcelableArrayList(CustomTabsIntent.EXTRA_MENU_ITEMS);
            if (menuItems != null) {
                int index = 0;
                for (Bundle item : menuItems) {
                    if (index >= MAX_CUSTOM_ITEMS) {
                        break;
                    }
                    ToolbarAction action = readToolbarAction(item, MENU_CUSTOM_BASE + index, false);
                    if (action != null) {
                        customActions.add(action);
                        index++;
                    }
                }
            }
            ArrayList<Bundle> toolbarItems = extras.getParcelableArrayList(CustomTabsIntent.EXTRA_TOOLBAR_ITEMS);
            if (toolbarItems != null) {
                int index = customActions.size();
                for (Bundle item : toolbarItems) {
                    if (index >= MAX_CUSTOM_ITEMS) {
                        break;
                    }
                    ToolbarAction action = readToolbarAction(item, MENU_CUSTOM_BASE + index, true);
                    if (action != null) {
                        boolean duplicate = false;
                        for (ToolbarAction existing : customActions) {
                            if (existing.menuId == action.menuId) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            customActions.add(action);
                            index++;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            customActions.clear();
        }
    }

    private ToolbarAction readToolbarAction(Bundle source, int fallbackId, boolean toolbarItem) {
        if (source == null) {
            return null;
        }
        try {
            source.setClassLoader(CustomTabsIntent.class.getClassLoader());
            PendingIntent pendingIntent = source.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT);
            String title = source.getString(toolbarItem ? CustomTabsIntent.KEY_DESCRIPTION : CustomTabsIntent.KEY_MENU_ITEM_TITLE);
            if (title == null || title.trim().isEmpty()) {
                title = source.getString(CustomTabsIntent.KEY_DESCRIPTION);
            }
            if (title == null || title.trim().isEmpty() || pendingIntent == null) {
                return null;
            }
            int id = source.getInt(CustomTabsIntent.KEY_ID, fallbackId);
            if (id == CustomTabsIntent.TOOLBAR_ACTION_BUTTON_ID || id < 0) {
                id = fallbackId;
            }
            Bitmap icon = source.getParcelable(CustomTabsIntent.KEY_ICON);
            if (icon != null && (icon.isRecycled() || icon.getWidth() > 256 || icon.getHeight() > 256)) {
                icon = null;
            }
            return new ToolbarAction(fallbackId, id, title.trim().substring(0, Math.min(96, title.trim().length())), icon, pendingIntent, toolbarItem);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private void updateCustomToolbar(BrowserView view) {
        if (urlText == null || isFinishing() || isDestroyed()) {
            return;
        }
        String currentUrl = view == null ? null : view.getUrl();
        Uri uri = currentUrl == null ? null : Uri.parse(currentUrl);
        String origin = displayOrigin(uri);
        String title = view == null ? "" : sanitizeTitle(view.getTitle());
        String visibleText = origin;
        if (titleVisibility == CustomTabsIntent.SHOW_PAGE_TITLE && !title.isEmpty() && !origin.isEmpty()) {
            visibleText = origin + " · " + title;
        }
        urlText.setText(visibleText);
        urlText.setContentDescription(origin.isEmpty() ? "ページのアドレス" : "ページのオリジン " + origin
                + (title.isEmpty() ? "" : "、タイトル " + title));
        if (securityIcon != null) {
            boolean secure = uri != null && "https".equalsIgnoreCase(uri.getScheme());
            securityIcon.setVisibility(secure ? View.VISIBLE : View.GONE);
            securityIcon.setContentDescription(secure ? "HTTPS で保護されています" : "保護されていない通信");
        }
        if (progressBar instanceof android.widget.ProgressBar && view != null) {
            android.widget.ProgressBar bar = (android.widget.ProgressBar) progressBar;
            if (view.isLoading()) {
                bar.setVisibility(View.VISIBLE);
                bar.setProgress(Math.max(0, Math.min(100, view.getProgress())));
            } else {
                bar.setVisibility(View.GONE);
            }
        }
    }

    private String displayOrigin(Uri uri) {
        if (uri == null || !isAllowedUrl(uri) || uri.getHost() == null) {
            return "";
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        int port = uri.getPort();
        String authority = host;
        if (port != -1 && !("https".equals(scheme) && port == 443) && !("http".equals(scheme) && port == 80)) {
            authority += ":" + port;
        }
        if ("http".equals(scheme)) {
            return "保護されていない通信 · " + authority;
        }
        return authority;
    }

    private String sanitizeTitle(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), 128));
        boolean previousSpace = false;
        for (int i = 0; i < value.length() && result.length() < 96; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '\u202A' || c == '\u202B' || c == '\u202D'
                    || c == '\u202E' || c == '\u2066' || c == '\u2067' || c == '\u2068' || c == '\u2069') {
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!previousSpace && result.length() > 0) {
                    result.append(' ');
                }
                previousSpace = true;
            } else {
                result.append(c);
                previousSpace = false;
            }
        }
        return result.toString().trim();
    }

    private void shareCurrentPage() {
        BrowserView view = getCustomTabBrowserView();
        String url = view == null ? null : view.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url);
        String title = view.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            share.putExtra(Intent.EXTRA_SUBJECT, title.trim());
        }
        try {
            startActivity(Intent.createChooser(share, "ページを共有"));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void openInFullBrowser() {
        BrowserView view = getCustomTabBrowserView();
        String url = view == null ? null : view.getUrl();
        if (url == null || !isAllowedUrl(Uri.parse(url))) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void sendPendingIntent(PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return;
        }
        BrowserView view = getCustomTabBrowserView();
        String url = view == null ? null : view.getUrl();
        Intent fillIn = new Intent();
        if (url != null && isAllowedUrl(Uri.parse(url))) {
            fillIn.setData(Uri.parse(url));
        }
        try {
            pendingIntent.send(this, 0, fillIn);
        } catch (PendingIntent.CanceledException | SecurityException ignored) {
            Toast.makeText(this, "アプリのアクションを実行できませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchNavigationEvent(int event) {
        CustomTabsSessionToken token = sessionToken;
        if (token == null || token.getCallback() == null) {
            return;
        }
        try {
            token.getCallback().onNavigationEvent(event, null);
        } catch (RuntimeException ignored) {
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null && urlText != null) {
                manager.hideSoftInputFromWindow(urlText.getWindowToken(), 0);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isAllowedUrl(Uri uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        int port = uri.getPort();
        return port < 0 || port <= 65535;
    }

    private CustomTabsSessionToken extractSessionToken(Intent intent) {
        if (intent == null) {
            return null;
        }
        try {
            return CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Intent sanitizeIntent(Intent intent) {
        Intent safe = new Intent(Intent.ACTION_VIEW);
        Uri data = intent == null ? null : intent.getData();
        if (!isAllowedUrl(data) || !BrowserUrlRouter.isWebUrl(data.toString())) {
            safe.setData(Uri.parse("about:blank"));
            return safe;
        }
        safe.setData(data.normalizeScheme());
        if (intent == null || intent.getExtras() == null) {
            return safe;
        }
        Bundle extras;
        try {
            extras = intent.getExtras();
            extras.setClassLoader(CustomTabsIntent.class.getClassLoader());
        } catch (RuntimeException error) {
            return safe;
        }
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_TOOLBAR_COLOR);
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE);
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_SHARE_STATE);
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR);
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR);
        copyIntExtra(intent, safe, CustomTabsIntent.EXTRA_SECONDARY_TOOLBAR_COLOR);
        copyBooleanExtra(intent, safe, CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON);
        copyBooleanExtra(intent, safe, CustomTabsIntent.EXTRA_ENABLE_URLBAR_HIDING);
        try {
            Bitmap closeIcon = intent.getParcelableExtra(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON);
            if (closeIcon != null && !closeIcon.isRecycled() && closeIcon.getWidth() <= 256 && closeIcon.getHeight() <= 256) {
                safe.putExtra(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON, closeIcon);
            }
        } catch (RuntimeException ignored) {
        }
        Bundle actionButton = CustomTabsService.sanitizeActionButton(extras.getBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE));
        if (!actionButton.isEmpty()) {
            safe.putExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, actionButton);
        }
        copyActionList(extras, safe, CustomTabsIntent.EXTRA_MENU_ITEMS, false);
        copyActionList(extras, safe, CustomTabsIntent.EXTRA_TOOLBAR_ITEMS, true);
        return safe;
    }

    private void copyIntExtra(Intent source, Intent target, String key) {
        try {
            if (source.hasExtra(key)) {
                target.putExtra(key, source.getIntExtra(key, 0));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void copyBooleanExtra(Intent source, Intent target, String key) {
        try {
            if (source.hasExtra(key)) {
                target.putExtra(key, source.getBooleanExtra(key, false));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void copyActionList(Bundle source, Intent target, String key, boolean toolbarItems) {
        try {
            ArrayList<Bundle> raw = source.getParcelableArrayList(key);
            if (raw == null) {
                return;
            }
            ArrayList<Bundle> safe = new ArrayList<>();
            int limit = Math.min(MAX_CUSTOM_ITEMS, raw.size());
            for (int i = 0; i < limit; i++) {
                Bundle item = raw.get(i);
                if (item == null) {
                    continue;
                }
                item.setClassLoader(CustomTabsIntent.class.getClassLoader());
                PendingIntent pending = item.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT);
                String title = item.getString(toolbarItems ? CustomTabsIntent.KEY_DESCRIPTION : CustomTabsIntent.KEY_MENU_ITEM_TITLE);
                if (title == null || title.trim().isEmpty()) {
                    title = item.getString(CustomTabsIntent.KEY_DESCRIPTION);
                }
                if (pending == null || title == null || title.trim().isEmpty()) {
                    continue;
                }
                Bundle itemSafe = new Bundle();
                itemSafe.putParcelable(CustomTabsIntent.KEY_PENDING_INTENT, pending);
                itemSafe.putString(toolbarItems ? CustomTabsIntent.KEY_DESCRIPTION : CustomTabsIntent.KEY_MENU_ITEM_TITLE,
                        title.trim().substring(0, Math.min(96, title.trim().length())));
                itemSafe.putInt(CustomTabsIntent.KEY_ID, item.getInt(CustomTabsIntent.KEY_ID, i + 1));
                Bitmap icon = item.getParcelable(CustomTabsIntent.KEY_ICON);
                if (icon != null && !icon.isRecycled() && icon.getWidth() <= 256 && icon.getHeight() <= 256) {
                    itemSafe.putParcelable(CustomTabsIntent.KEY_ICON, icon);
                }
                safe.add(itemSafe);
            }
            target.putParcelableArrayListExtra(key, safe);
        } catch (RuntimeException ignored) {
        }
    }

    private int getColorCompat(int resourceId) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            return getColor(resourceId);
        }
        return getResources().getColor(resourceId);
    }

    private Drawable getDrawableCompat(int resourceId) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            return getDrawable(resourceId);
        }
        return getResources().getDrawable(resourceId);
    }

    private static final class ToolbarAction {
        final int menuId;
        final int originalId;
        final String title;
        final Bitmap icon;
        final PendingIntent pendingIntent;
        final boolean toolbarItem;

        ToolbarAction(int menuId, int originalId, String title, Bitmap icon, PendingIntent pendingIntent, boolean toolbarItem) {
            this.menuId = menuId;
            this.originalId = originalId;
            this.title = title;
            this.icon = icon;
            this.pendingIntent = pendingIntent;
            this.toolbarItem = toolbarItem;
        }
    }
}
