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
import android.view.inputmethod.BaseInputConnection;
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

    private static final long DEBOUNCE_MS = 100L;
    private static final long COMPOSITION_RECHECK_MS = 70L;
    private static final long PAGE_RELOAD_SEARCH_DELAY_MS = 220L;

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
    private int lastOrdinal;
    private int searchGeneration;
    private boolean suppressWatcher;

    private final Runnable searchTask = new Runnable() {
        @Override
        public void run() {
            if (!isShowing() || input == null) {
                return;
            }
            if (hasComposingText()) {
                handler.postDelayed(this, COMPOSITION_RECHECK_MS);
                return;
            }
            search();
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
        } else {
            showNoResults(false);
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
        searchGeneration++;
        searchPending = false;
        clearActive();
        bar.setVisibility(View.GONE);
        showNoResults(false);
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
        searchGeneration++;
        searchPending = false;
        if (previous != null) {
            previous.clearMatches();
        }
        active = null;
        lastQuery = "";
        hasMatch = false;
        knownTotal = 0;
        showNoResults(false);
        if (!input.getText().toString().trim().isEmpty()) {
            scheduleSearch(0L);
        }
    }

    public void onPageStarted(BrowserView view) {
        if (!isShowing() || view == null || view != provider.current()) {
            return;
        }
        handler.removeCallbacks(searchTask);
        searchGeneration++;
        searchPending = false;
        view.clearMatches();
        active = view;
        lastQuery = "";
        hasMatch = false;
        knownTotal = 0;
        showNoResults(false);
    }

    public void onPageFinished(BrowserView view) {
        if (!isShowing() || view == null || view != provider.current()) {
            return;
        }
        if (!input.getText().toString().trim().isEmpty()) {
            scheduleSearch(PAGE_RELOAD_SEARCH_DELAY_MS);
        }
    }

    public void release() {
        handler.removeCallbacks(searchTask);
        searchGeneration++;
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
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressWatcher) {
                    return;
                }
                String query = editable.toString().trim();
                if (query.equals(lastTypedQuery)) {
                    return;
                }
                lastTypedQuery = query;
                searchGeneration++;
                searchPending = false;
                hasMatch = false;
                knownTotal = 0;
                if (count != null) {
                    count.setTextColor(defaultCountColor);
                    count.setText(query.isEmpty() ? "0/0" : "…");
                }
                scheduleSearch(DEBOUNCE_MS);
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

    private boolean hasComposingText() {
        if (input == null) {
            return false;
        }
        Editable editable = input.getText();
        return BaseInputConnection.getComposingSpanStart(editable) >= 0
                && BaseInputConnection.getComposingSpanEnd(editable) >= 0;
    }

    private void scheduleSearch(long delay) {
        handler.removeCallbacks(searchTask);
        handler.postDelayed(searchTask, Math.max(0L, delay));
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
        if (searchPending || hasMatch) {
            step(true);
        }
    }

    private void step(final boolean forward) {
        final BrowserView w = provider.current();
        final String q = input.getText().toString().trim();
        if (w == null || q.isEmpty()) {
            return;
        }
        if (!q.equals(lastQuery) || !w.hasFindState()) {
            scheduleSearch(0L);
            return;
        }
        final int generation = searchGeneration;
        try {
            boolean sent = w.findNextFast(q, forward, new BrowserView.FindCallback() {
                @Override
                public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                    if (generation != searchGeneration || w != provider.current()
                            || !q.equals(lastQuery) || !isShowing()) {
                        return;
                    }
                    if (numberOfMatches == 0 && knownTotal > 0 && hasMatch) {
                        publishResult(w, q, generation, lastOrdinal, knownTotal);
                        return;
                    }
                    publishResult(w, q, generation, activeMatchOrdinal, numberOfMatches);
                }
            });
            if (!sent) {
                scheduleSearch(0L);
            }
        } catch (Exception ignored) {
        }
    }

    private void search() {
        BrowserView view = provider.current();
        if (view == null) {
            clearActive();
            showNoResults(false);
            return;
        }
        String query = input.getText().toString().trim();
        if (active != null && active != view) {
            active.clearMatches();
        }
        active = view;
        lastQuery = query;
        if (query.isEmpty()) {
            view.clearMatches();
            hasMatch = false;
            searchPending = false;
            knownTotal = 0;
            showNoResults(false);
            return;
        }
        knownTotal = 0;
        hasMatch = false;
        lastOrdinal = 0;
        final int ticket = ++searchGeneration;
        searchPending = true;
        if (count != null) {
            count.setTextColor(defaultCountColor);
            count.setText("…");
        }
        view.findAllAsync(query, new BrowserView.FindCallback() {
            @Override
            public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                publishResult(view, query, ticket, activeMatchOrdinal, numberOfMatches);
            }
        });
    }

    private void publishResult(BrowserView view, String query, int ticket, int activeMatchOrdinal, int numberOfMatches) {
        if (ticket != searchGeneration || !isShowing() || view != provider.current() || active != view || !query.equals(lastQuery)) {
            return;
        }
        searchPending = false;
        hasMatch = numberOfMatches != 0;
        if (numberOfMatches > 0) {
            knownTotal = numberOfMatches;
        }
        if (numberOfMatches != 0) {
            lastOrdinal = Math.max(0, activeMatchOrdinal);
        } else {
            lastOrdinal = 0;
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
            count.setTextColor(Color.parseColor("#D93025"));
            count.setText("0/0");
        }
    }

    private void clearActive() {
        BrowserView view = active != null ? active : provider.current();
        if (view != null) {
            view.clearMatches();
        }
        active = null;
        lastQuery = "";
        hasMatch = false;
        searchPending = false;
        knownTotal = 0;
        lastOrdinal = 0;
    }

    private void showNoResults(boolean invalid) {
        hasMatch = false;
        searchPending = false;
        knownTotal = 0;
        lastOrdinal = 0;
        if (count != null) {
            count.setTextColor(invalid ? Color.parseColor("#D93025") : defaultCountColor);
            count.setText("0/0");
        }
    }
}
