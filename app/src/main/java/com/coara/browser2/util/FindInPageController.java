package com.coara.browser2.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.coara.browser2.R;
import com.coara.browser2.gecko.BrowserView;

public final class FindInPageController {

    public interface Provider {
        BrowserView current();
    }

    private static final long DEBOUNCE_MS = 160L;

    private final Activity activity;
    private final SharedPreferences pref;
    private final Provider provider;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View bar;
    private EditText input;
    private TextView count;
    private int defaultCountColor;
    private BrowserView active;
    private String lastQuery = "";
    private String lastTypedQuery = "";
    private boolean hasMatch;
    private boolean searchPending;
    private int knownTotal;
    private int seq;
    private boolean suppressWatcher;

    private final Runnable searchTask = new Runnable() {
        @Override
        public void run() {
            search();
        }
    };

    private final BrowserView.FindCallback listener = new BrowserView.FindCallback() {
        @Override
        public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
            hasMatch = numberOfMatches != 0;
            if (numberOfMatches > 0) {
                knownTotal = numberOfMatches;
            }
            if (count == null) {
                return;
            }
            if (numberOfMatches != 0) {
                String shown = numberOfMatches > 0
                        ? String.valueOf(numberOfMatches)
                        : knownTotal > 0 ? String.valueOf(knownTotal) : "…";
                count.setTextColor(defaultCountColor);
                count.setText((activeMatchOrdinal + 1) + "/" + shown);
            } else {
                count.setTextColor(lastQuery.isEmpty() ? defaultCountColor : Color.parseColor("#D93025"));
                count.setText("0/0");
            }
        }
    };

    public FindInPageController(Activity activity, SharedPreferences pref, Provider provider) {
        this.activity = activity;
        this.pref = pref;
        this.provider = provider;
    }

    public boolean isShowing() {
        return bar != null && bar.getVisibility() == View.VISIBLE;
    }

    public void show() {
        if (bar == null) {
            build();
        }
        bar.setVisibility(View.VISIBLE);
        input.requestFocus();
        input.selectAll();
        if (input.getText().length() > 0) {
            scheduleSearch(0L);
        }
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hide() {
        if (bar == null) {
            return;
        }
        handler.removeCallbacks(searchTask);
        clearActive();
        bar.setVisibility(View.GONE);
        resetCount();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    public void onTabSwitched(BrowserView previous) {
        if (!isShowing()) {
            return;
        }
        handler.removeCallbacks(searchTask);
        if (previous != null) {
            try {
                previous.clearMatches();
            } catch (Exception ignored) {
            }
        }
        active = null;
        resetCount();
        if (input.getText().toString().trim().length() > 0) {
            scheduleSearch(0L);
        }
    }

    public void onPageStarted(BrowserView view) {
        if (!isShowing() || view == null || view != provider.current()) {
            return;
        }
        handler.removeCallbacks(searchTask);
        try {
            view.clearMatches();
        } catch (Exception ignored) {
        }
        active = view;
        resetCount();
    }

    public void onPageFinished(BrowserView view) {
        if (!isShowing() || view == null || view != provider.current()) {
            return;
        }
        if (input.getText().toString().trim().length() > 0) {
            scheduleSearch(250L);
        }
    }

    public void release() {
        handler.removeCallbacks(searchTask);
        seq++;
        searchPending = false;
        clearActive();
    }

    private void build() {
        bar = LayoutInflater.from(activity).inflate(R.layout.find_in_page_bar, null);
        input = bar.findViewById(R.id.etFindQuery);
        count = bar.findViewById(R.id.tvFindCount);
        defaultCountColor = count.getCurrentTextColor();
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        KeyboardSettings.applyToEditor(input, pref);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressWatcher) {
                    return;
                }
                String query = s.toString().trim();
                if (query.equals(lastTypedQuery)) {
                    return;
                }
                lastTypedQuery = query;
                seq++;
                searchPending = false;
                hasMatch = false;
                knownTotal = 0;
                if (count != null) {
                    count.setTextColor(defaultCountColor);
                    count.setText(query.isEmpty() ? "0/0" : "…");
                }
                BrowserView current = provider.current();
                long adaptive = current == null
                        ? DEBOUNCE_MS
                        : Math.max(DEBOUNCE_MS, Math.min(700L, current.lastFindMillis()));
                scheduleSearch(query.isEmpty() ? 0L : adaptive);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                submit();
                return true;
            }
            return false;
        });
        View next = bar.findViewById(R.id.btnFindNext);
        View prev = bar.findViewById(R.id.btnFindPrev);
        View close = bar.findViewById(R.id.btnFindClose);
        for (View button : new View[] {next, prev, close}) {
            button.setFocusable(false);
            button.setFocusableInTouchMode(false);
        }
        next.setOnClickListener(v -> step(true));
        prev.setOnClickListener(v -> step(false));
        close.setOnClickListener(v -> {
            suppressWatcher = true;
            input.setText("");
            lastTypedQuery = "";
            suppressWatcher = false;
            hide();
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        activity.addContentView(bar, params);
    }

    private void scheduleSearch(long delay) {
        handler.removeCallbacks(searchTask);
        handler.postDelayed(searchTask, delay);
    }

    private void submit() {
        String query = input.getText().toString().trim();
        BrowserView current = provider.current();
        if (query.isEmpty()) {
            handler.removeCallbacks(searchTask);
            search();
            return;
        }
        if (current == null || !query.equals(lastQuery) || active != current || !current.hasFindState()) {
            handler.removeCallbacks(searchTask);
            search();
            return;
        }
        if (searchPending) {
            return;
        }
        if (hasMatch) {
            step(true);
        } else {
            handler.removeCallbacks(searchTask);
            search();
        }
    }

    private void step(final boolean forward) {
        final BrowserView view = provider.current();
        final String query = input.getText().toString().trim();
        if (view == null || query.isEmpty()) {
            return;
        }
        if (!query.equals(lastQuery) || active != view || !view.hasFindState()) {
            scheduleSearch(0L);
            return;
        }
        if (searchPending || !hasMatch) {
            return;
        }
        final int ticket = ++seq;
        searchPending = true;
        try {
            boolean sent = view.findNext(forward, new BrowserView.FindCallback() {
                @Override
                public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                    if (ticket != seq || view != provider.current() || !query.equals(lastQuery)) {
                        return;
                    }
                    searchPending = false;
                    listener.onFindResult(activeMatchOrdinal, numberOfMatches);
                }
            });
            if (!sent) {
                searchPending = false;
                scheduleSearch(0L);
            }
        } catch (Exception ignored) {
            searchPending = false;
            scheduleSearch(0L);
        }
    }

    private void search() {
        BrowserView view = provider.current();
        if (view == null) {
            clearActive();
            resetCount();
            return;
        }
        String query = input.getText().toString().trim();
        if (active != null && active != view) {
            try {
                active.clearMatches();
            } catch (Exception ignored) {
            }
        }
        active = view;
        lastQuery = query;
        if (query.isEmpty()) {
            try {
                view.clearMatches();
            } catch (Exception ignored) {
            }
            resetCount();
            return;
        }
        knownTotal = 0;
        hasMatch = false;
        final int ticket = ++seq;
        searchPending = true;
        if (count != null) {
            count.setTextColor(defaultCountColor);
            count.setText("…");
        }
        view.findAllAsync(query, new BrowserView.FindCallback() {
            @Override
            public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                if (ticket != seq || active != provider.current() || !query.equals(lastQuery)) {
                    return;
                }
                searchPending = false;
                listener.onFindResult(activeMatchOrdinal, numberOfMatches);
            }
        });
    }

    private void clearActive() {
        BrowserView view = active != null ? active : provider.current();
        if (view != null) {
            try {
                view.clearMatches();
            } catch (Exception ignored) {
            }
        }
        active = null;
        lastQuery = "";
    }

    private void resetCount() {
        hasMatch = false;
        searchPending = false;
        knownTotal = 0;
        seq++;
        lastQuery = "";
        if (count != null) {
            count.setTextColor(defaultCountColor);
            count.setText("0/0");
        }
    }
}
