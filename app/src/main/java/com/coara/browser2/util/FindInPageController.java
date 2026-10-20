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
    private static final int MAX_RESTORE_STEPS = 400;

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
    private boolean hasMatch;
    private int knownTotal;
    private int lastOrdinal;
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
            lastOrdinal = activeMatchOrdinal;
            if (numberOfMatches > 0) {
                knownTotal = numberOfMatches;
            }
            if (count == null) {
                return;
            }
            if (numberOfMatches != 0) {
                String shown = numberOfMatches > 0 ? String.valueOf(numberOfMatches) : (knownTotal > 0 ? String.valueOf(knownTotal) : "1000+");
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
        if (input.getText().length() > 0) {
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
            public void beforeTextChanged(CharSequence s, int start, int c, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatcher) {
                    scheduleSearch(DEBOUNCE_MS);
                }
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
        for (View b : new View[] {next, prev, close}) {
            b.setFocusable(false);
            b.setFocusableInTouchMode(false);
        }
        next.setOnClickListener(v -> step(true));
        prev.setOnClickListener(v -> step(false));
        close.setOnClickListener(v -> {
            suppressWatcher = true;
            input.setText("");
            suppressWatcher = false;
            hide();
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        activity.addContentView(bar, params);
    }

    private void scheduleSearch(long delay) {
        handler.removeCallbacks(searchTask);
        handler.postDelayed(searchTask, delay);
    }

    private void submit() {
        String q = input.getText().toString().trim();
        if (!q.equals(lastQuery)) {
            handler.removeCallbacks(searchTask);
            search();
        } else {
            step(true);
        }
    }

    private void step(final boolean forward) {
        final BrowserView w = provider.current();
        final String q = input.getText().toString().trim();
        if (w == null || q.isEmpty()) {
            return;
        }
        if (!hasMatch || !q.equals(lastQuery)) {
            scheduleSearch(0L);
            return;
        }
        final int ticket = ++seq;
        try {
            w.findNext(q, forward, new BrowserView.FindCallback() {
                @Override
                public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                    if (ticket != seq || w != provider.current()) {
                        return;
                    }
                    if (numberOfMatches == 0) {
                        recover(w, q, forward, ticket);
                        return;
                    }
                    listener.onFindResult(activeMatchOrdinal, numberOfMatches);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void recover(final BrowserView w, final String q, final boolean forward, final int ticket) {
        final int total = knownTotal;
        final int from = lastOrdinal;
        w.findAllAsync(q, new BrowserView.FindCallback() {
            @Override
            public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                if (ticket != seq || w != provider.current()) {
                    return;
                }
                if (numberOfMatches == 0) {
                    listener.onFindResult(0, 0);
                    return;
                }
                int count = numberOfMatches > 0 ? numberOfMatches : total;
                int target = 0;
                if (count > 0) {
                    target = forward ? (from + 1) % count : (from - 1 + count) % count;
                }
                advance(w, q, ticket, activeMatchOrdinal, numberOfMatches, Math.min(target, MAX_RESTORE_STEPS));
            }
        });
    }

    private void advance(final BrowserView w, final String q, final int ticket, final int ordinal, final int total, final int remaining) {
        if (remaining <= 0) {
            listener.onFindResult(ordinal, total);
            return;
        }
        w.findNext(q, true, new BrowserView.FindCallback() {
            @Override
            public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                if (ticket != seq || w != provider.current()) {
                    return;
                }
                if (numberOfMatches == 0) {
                    listener.onFindResult(ordinal, total);
                    return;
                }
                advance(w, q, ticket, activeMatchOrdinal, numberOfMatches, remaining - 1);
            }
        });
    }

    private void search() {
        BrowserView w = provider.current();
        if (w == null) {
            return;
        }
        String q = input.getText().toString().trim();
        if (active != null && active != w) {
            try {
                active.clearMatches();
                            } catch (Exception ignored) {
            }
        }
        active = w;
        lastQuery = q;
        if (q.isEmpty()) {
            try {
                w.clearMatches();
            } catch (Exception ignored) {
            }
            resetCount();
            return;
        }
        final String query = q;
        final int ticket = ++seq;
        w.findAllAsync(q, new BrowserView.FindCallback() {
            @Override
            public void onFindResult(int activeMatchOrdinal, int numberOfMatches) {
                if (ticket == seq && active == provider.current() && query.equals(lastQuery)) {
                    listener.onFindResult(activeMatchOrdinal, numberOfMatches);
                }
            }
        });
    }

    private void clearActive() {
        BrowserView w = active != null ? active : provider.current();
        if (w != null) {
            try {
                w.clearMatches();
            } catch (Exception ignored) {
            }
        }
        active = null;
        lastQuery = "";
    }

    private void resetCount() {
        hasMatch = false;
        knownTotal = 0;
        lastOrdinal = 0;
        seq++;
        lastQuery = "";
        if (count != null) {
            count.setTextColor(defaultCountColor);
            count.setText("0/0");
        }
    }
}
