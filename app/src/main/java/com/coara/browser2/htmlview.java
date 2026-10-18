package com.coara.browser2;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class htmlview extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_WRITE = 100;
    private static final int REQUEST_CODE_PICK_HTML = 101;
    private static final int FULL_HIGHLIGHT_THRESHOLD = 50000;
    private static final int INITIAL_HIGHLIGHT_RANGE = 12000;
    private static final int MAX_UNDO_ENTRIES = 12;
    private static final int MAX_UNDO_HISTORY_CHARS = 2000000;
    private static final int MAX_SEARCH_MATCHES = 10000;
    private static final long HIGHLIGHT_DELAY_MS = 260;
    private static final long VIEWPORT_DELAY_MS = 90;
    private static final long UNDO_THRESHOLD_MS = 1000;

    private static final int TAG_COLOR = 0xFF569CD6;
    private static final int ATTRIBUTE_COLOR = 0xFF9CDCFE;
    private static final int VALUE_COLOR = 0xFFCE9178;
    private static final int COMMENT_COLOR = 0xFF6A9955;
    private static final int DECLARATION_COLOR = 0xFFC586C0;
    private static final int ENTITY_COLOR = 0xFFDCDCAA;
    private static final int KEYWORD_COLOR = 0xFFC586C0;
    private static final int STRING_COLOR = 0xFFCE9178;
    private static final int NUMBER_COLOR = 0xFFB5CEA8;
    private static final int FUNCTION_COLOR = 0xFFDCDCAA;
    private static final int CSS_PROPERTY_COLOR = 0xFF9CDCFE;
    private static final int CSS_SELECTOR_COLOR = 0xFFD7BA7D;

    private EditText urlInput;
    private Button loadButton;
    private Button loadFromStorageButton;
    private Button editButton;
    private Button saveButton;
    private Button searchButton;
    private EditText htmlEditText;
    private FloatingActionButton revertFab;
    private RelativeLayout searchOverlay;
    private EditText searchQueryEditText;
    private TextView searchResultCountTextView;
    private TextView lineNumberTextView;
    private TextView sourceStatusTextView;
    private ScrollView scrollView;
    private Button searchNextButton;
    private Button searchPrevButton;
    private Button closeSearchButton;

    private KeyListener editorKeyListener;
    private String originalHtml = "";
    private final Stack<String> editHistory = new Stack<>();
    private final ArrayList<Integer> searchMatchPositions = new ArrayList<>();
    private boolean isEditing;
    private volatile boolean isLoading;
    private boolean isUpdating;
    private long lastUndoTimestamp;
    private int currentSearchIndex = -1;
    private int editRevision;
    private int highlightedRevision = -1;
    private int lastAppliedRevision = -1;
    private int currentLineCount = 1;
    private int currentCharacterCount;
    private int[] syntaxSpans;
    private int searchRevision;
    private Future<?> highlightFuture;
    private Future<?> searchFuture;
    private Runnable highlightRunnable;
    private Runnable viewportRunnable;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService highlightExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ViewTreeObserver.OnScrollChangedListener scrollChangedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.htmlview);

        urlInput = findViewById(R.id.urlInput);
        loadButton = findViewById(R.id.loadButton);
        loadFromStorageButton = findViewById(R.id.loadFromStorageButton);
        editButton = findViewById(R.id.editButton);
        saveButton = findViewById(R.id.saveButton);
        htmlEditText = findViewById(R.id.htmlEditText);
        revertFab = findViewById(R.id.revertFab);
        searchButton = findViewById(R.id.searchButton);
        searchOverlay = findViewById(R.id.searchOverlay);
        searchQueryEditText = findViewById(R.id.searchQueryEditText);
        searchResultCountTextView = findViewById(R.id.searchResultCountTextView);
        searchNextButton = findViewById(R.id.searchNextButton);
        searchPrevButton = findViewById(R.id.searchPrevButton);
        closeSearchButton = findViewById(R.id.closeSearchButton);
        lineNumberTextView = findViewById(R.id.lineNumberTextView);
        sourceStatusTextView = findViewById(R.id.sourceStatusTextView);
        scrollView = findViewById(R.id.scrollView);

        editorKeyListener = htmlEditText.getKeyListener();
        htmlEditText.setKeyListener(null);
        htmlEditText.setTextIsSelectable(true);
        htmlEditText.setHorizontallyScrolling(true);
        htmlEditText.setLayerType(View.LAYER_TYPE_NONE, null);
        updateStatusText();

        urlInput.setOnEditorActionListener((view, actionId, event) -> {
            loadButton.performClick();
            return true;
        });

        loadButton.setOnClickListener(v -> {
            String urlString = urlInput.getText().toString().trim();
            if (isLoading) {
                Toast.makeText(this, "読み込み中です。", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                Toast.makeText(this, "http:// または https:// で始まるURLを入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            fetchHtml(urlString);
        });

        loadFromStorageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/html");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "HTMLファイルを選択"), REQUEST_CODE_PICK_HTML);
        });

        editButton.setOnClickListener(v -> toggleEditing());

        htmlEditText.addTextChangedListener(new TextWatcher() {
            private String beforeChange;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isUpdating && isEditing && System.currentTimeMillis() - lastUndoTimestamp >= UNDO_THRESHOLD_MS) {
                    beforeChange = s.toString();
                } else {
                    beforeChange = null;
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating) {
                    return;
                }
                editRevision++;
                highlightedRevision = -1;
                if (highlightRunnable != null) {
                    uiHandler.removeCallbacks(highlightRunnable);
                }
                if (highlightFuture != null) {
                    highlightFuture.cancel(true);
                    highlightFuture = null;
                }
                if (viewportRunnable != null) {
                    uiHandler.removeCallbacks(viewportRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating || !isEditing) {
                    return;
                }
                if (beforeChange != null && !beforeChange.contentEquals(s)) {
                    pushUndoSnapshot(beforeChange);
                    lastUndoTimestamp = System.currentTimeMillis();
                }
                highlightRunnable = () -> requestHighlight();
                uiHandler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS);
            }
        });

        revertFab.setOnClickListener(v -> undoEdit());

        saveButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_WRITE);
            } else {
                saveHtmlToFile();
            }
        });

        searchButton.setOnClickListener(v -> showSearchOverlay());
        searchQueryEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchNextButton.setOnClickListener(v -> moveToNextSearchMatch());
        searchPrevButton.setOnClickListener(v -> moveToPreviousSearchMatch());
        closeSearchButton.setOnClickListener(v -> hideSearchOverlay());

        scrollChangedListener = () -> {
            if (viewportRunnable != null) {
                uiHandler.removeCallbacks(viewportRunnable);
            }
            viewportRunnable = () -> applyVisibleHighlight();
            uiHandler.postDelayed(viewportRunnable, VIEWPORT_DELAY_MS);
        };
        scrollView.getViewTreeObserver().addOnScrollChangedListener(scrollChangedListener);
    }

    private void toggleEditing() {
        if (!isEditing) {
            editHistory.clear();
            pushUndoSnapshot(htmlEditText.getText().toString());
            lastUndoTimestamp = System.currentTimeMillis();
            isEditing = true;
            htmlEditText.setKeyListener(editorKeyListener);
            htmlEditText.setFocusableInTouchMode(true);
            editButton.setText("閲覧");
            htmlEditText.requestFocus();
            Toast.makeText(this, "編集モードに入りました", Toast.LENGTH_SHORT).show();
        } else {
            isEditing = false;
            htmlEditText.setKeyListener(null);
            editButton.setText("編集");
            Object service = getSystemService(Context.INPUT_METHOD_SERVICE);
            if (service instanceof InputMethodManager) {
                ((InputMethodManager) service).hideSoftInputFromWindow(htmlEditText.getWindowToken(), 0);
            }
            Toast.makeText(this, "閲覧モードに戻りました", Toast.LENGTH_SHORT).show();
            requestHighlight();
        }
        updateStatusText();
    }

    private void pushUndoSnapshot(String text) {
        if (text == null) {
            return;
        }
        if (!editHistory.empty() && editHistory.peek().equals(text)) {
            return;
        }
        editHistory.push(text);
        while (editHistory.size() > MAX_UNDO_ENTRIES) {
            editHistory.removeElementAt(0);
        }
        int retainedCharacters = 0;
        for (String snapshot : editHistory) {
            retainedCharacters += snapshot.length();
        }
        while (editHistory.size() > 1 && retainedCharacters > MAX_UNDO_HISTORY_CHARS) {
            retainedCharacters -= editHistory.remove(0).length();
        }
    }

    private void undoEdit() {
        if (!isEditing || editHistory.isEmpty()) {
            Toast.makeText(this, isEditing ? "これ以上前はありません" : "編集モードで使用してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentText = htmlEditText.getText().toString();
        String previousText;
        if (editHistory.size() > 1) {
            previousText = editHistory.pop();
        } else {
            previousText = editHistory.peek();
            if (currentText.equals(previousText)) {
                Toast.makeText(this, "これ以上前はありません", Toast.LENGTH_SHORT).show();
                return;
            }
            editHistory.clear();
            editHistory.push(previousText);
        }
        int selection = Math.max(0, htmlEditText.getSelectionStart());
        isUpdating = true;
        Editable editable = htmlEditText.getText();
        editable.replace(0, editable.length(), previousText);
        int position = Math.min(previousText.length(), selection);
        htmlEditText.setSelection(position);
        isUpdating = false;
        editRevision++;
        highlightedRevision = -1;
        lastUndoTimestamp = System.currentTimeMillis();
        requestHighlight();
        Toast.makeText(this, "変更を元に戻しました", Toast.LENGTH_SHORT).show();
    }

    private void showSearchOverlay() {
        searchOverlay.setVisibility(View.VISIBLE);
        searchButton.setVisibility(View.INVISIBLE);
        searchQueryEditText.setText("");
        searchQueryEditText.requestFocus();
        searchResultCountTextView.setText("件数: 0");
        searchMatchPositions.clear();
        currentSearchIndex = -1;
    }

    private void hideSearchOverlay() {
        searchOverlay.setVisibility(View.GONE);
        searchButton.setVisibility(View.VISIBLE);
        searchRevision++;
        if (searchFuture != null) {
            searchFuture.cancel(true);
            searchFuture = null;
        }
        clearSearchHighlights();
    }

    private void performSearch(String query) {
        final int revision = ++searchRevision;
        final String source = htmlEditText.getText().toString();
        if (searchFuture != null) {
            searchFuture.cancel(true);
            searchFuture = null;
        }
        if (query == null || query.isEmpty()) {
            searchMatchPositions.clear();
            currentSearchIndex = -1;
            clearSearchHighlights();
            searchResultCountTextView.setText("件数: 0");
            return;
        }
        searchFuture = searchExecutor.submit(() -> {
            ArrayList<Integer> matches = new ArrayList<>();
            int index = 0;
            while ((index = source.indexOf(query, index)) >= 0) {
                matches.add(index);
                if (matches.size() >= MAX_SEARCH_MATCHES) {
                    break;
                }
                index += Math.max(1, query.length());
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
            uiHandler.post(() -> {
                if (revision != searchRevision || isFinishing()) {
                    return;
                }
                searchMatchPositions.clear();
                searchMatchPositions.addAll(matches);
                currentSearchIndex = matches.isEmpty() ? -1 : 0;
                String countText = matches.size() >= MAX_SEARCH_MATCHES ? "件数: " + MAX_SEARCH_MATCHES + "+" : "件数: " + matches.size();
                searchResultCountTextView.setText(countText);
                if (!matches.isEmpty()) {
                    highlightCurrentSearchMatch();
                } else {
                    clearSearchHighlights();
                }
            });
        });
    }

    private void clearSearchHighlights() {
        Editable editable = htmlEditText.getText();
        BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
    }

    private void highlightCurrentSearchMatch() {
        clearSearchHighlights();
        if (currentSearchIndex < 0 || currentSearchIndex >= searchMatchPositions.size()) {
            return;
        }
        Editable editable = htmlEditText.getText();
        int start = searchMatchPositions.get(currentSearchIndex);
        int end = start + searchQueryEditText.getText().length();
        if (start < 0 || end > editable.length() || start >= end) {
            return;
        }
        editable.setSpan(new BackgroundColorSpan(0xFF665C00), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        htmlEditText.setSelection(start, end);
        htmlEditText.post(() -> {
            if (htmlEditText.getLayout() == null) {
                return;
            }
            int line = htmlEditText.getLayout().getLineForOffset(start);
            int y = htmlEditText.getLayout().getLineTop(line);
            scrollView.smoothScrollTo(0, Math.max(0, y));
        });
    }

    private void moveToNextSearchMatch() {
        if (!searchMatchPositions.isEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchMatchPositions.size();
            highlightCurrentSearchMatch();
        }
    }

    private void moveToPreviousSearchMatch() {
        if (!searchMatchPositions.isEmpty()) {
            currentSearchIndex = (currentSearchIndex - 1 + searchMatchPositions.size()) % searchMatchPositions.size();
            highlightCurrentSearchMatch();
        }
    }

    private void fetchHtml(String urlString) {
        isLoading = true;
        loadButton.setEnabled(false);
        ioExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "CoaraHTMLView/2.0");
                int statusCode = connection.getResponseCode();
                if (statusCode < 200 || statusCode >= 400) {
                    throw new java.io.IOException("HTTP " + statusCode);
                }
                Charset charset = getResponseCharset(connection.getContentType());
                StringBuilder result = new StringBuilder();
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        result.append(buffer, 0, count);
                    }
                }
                String html = result.toString();
                uiHandler.post(() -> {
                    loadButton.setEnabled(true);
                    installSource(html, urlString);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                uiHandler.post(() -> {
                    isLoading = false;
                    loadButton.setEnabled(true);
                    Toast.makeText(this, "HTMLの取得に失敗しました: " + message, Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private Charset getResponseCharset(String contentType) {
        if (contentType != null) {
            Matcher matcher = Pattern.compile("(?i)charset\\s*=\\s*[\\\"]?([^;\\\" ]+)").matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private void installSource(String source, String sourceName) {
        isLoading = false;
        isEditing = false;
        editHistory.clear();
        originalHtml = source == null ? "" : source;
        if (sourceName != null && (sourceName.startsWith("http://") || sourceName.startsWith("https://"))) {
            urlInput.setText(sourceName);
        }
        isUpdating = true;
        htmlEditText.setText(originalHtml);
        htmlEditText.setSelection(0);
        htmlEditText.setKeyListener(null);
        editButton.setText("編集");
        isUpdating = false;
        editRevision++;
        highlightedRevision = -1;
        requestHighlight();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_HTML && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                readHtmlFromUri(uri);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void readHtmlFromUri(Uri uri) {
        if (isLoading) {
            Toast.makeText(this, "読み込み中です。", Toast.LENGTH_SHORT).show();
            return;
        }
        isLoading = true;
        loadButton.setEnabled(false);
        ioExecutor.execute(() -> {
            try {
                StringBuilder result = new StringBuilder();
                ContentResolver resolver = getContentResolver();
                try (InputStream input = resolver.openInputStream(uri)) {
                    if (input == null) {
                        throw new java.io.IOException("ファイルを開けません");
                    }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        char[] buffer = new char[8192];
                        int count;
                        while ((count = reader.read(buffer)) != -1) {
                            result.append(buffer, 0, count);
                        }
                    }
                }
                String html = result.toString();
                uiHandler.post(() -> {
                    loadButton.setEnabled(true);
                    installSource(html, null);
                    urlInput.setText(uri.getLastPathSegment() == null ? "ローカルHTML" : uri.getLastPathSegment());
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                uiHandler.post(() -> {
                    isLoading = false;
                    loadButton.setEnabled(true);
                    Toast.makeText(this, "HTMLファイルの読み込みに失敗しました: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestHighlight() {
        if (isFinishing()) {
            return;
        }
        final int revision = editRevision;
        final String source = htmlEditText.getText().toString();
        if (highlightFuture != null) {
            highlightFuture.cancel(true);
        }
        highlightFuture = highlightExecutor.submit(() -> {
            int[] spans = SyntaxHighlighter.highlight(source);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            String lineNumbers = buildLineNumbers(source);
            int lineCount = 1;
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    lineCount++;
                }
            }
            HighlightResult result = new HighlightResult(spans, lineNumbers, lineCount, source.length());
            uiHandler.post(() -> {
                if (revision != editRevision || isFinishing()) {
                    return;
                }
                syntaxSpans = result.spans;
                highlightedRevision = revision;
                currentLineCount = result.lineCount;
                currentCharacterCount = result.characterCount;
                lineNumberTextView.setText(result.lineNumbers);
                int digits = String.valueOf(Math.max(1, result.lineCount)).length();
                lineNumberTextView.setMinWidth(dp(18 + digits * 8));
                updateStatusText();
                applyVisibleHighlight();
            });
        });
    }

    private String buildLineNumbers(String source) {
        int lineCount = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lineCount++;
            }
        }
        StringBuilder numbers = new StringBuilder(Math.min(lineCount * 4, 2_000_000));
        for (int line = 1; line <= lineCount; line++) {
            numbers.append(line);
            if (line < lineCount) {
                numbers.append('\n');
            }
            if ((line & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                throw new CancellationException();
            }
        }
        return numbers.toString();
    }

    private void applyVisibleHighlight() {
        if (highlightedRevision != editRevision || syntaxSpans == null || isFinishing()) {
            return;
        }
        int textLength = htmlEditText.getText().length();
        int start = 0;
        int end = textLength;
        if (textLength <= FULL_HIGHLIGHT_THRESHOLD && lastAppliedRevision == editRevision) {
            return;
        }
        if (textLength > FULL_HIGHLIGHT_THRESHOLD) {
            android.text.Layout layout = htmlEditText.getLayout();
            if (layout == null) {
                end = Math.min(textLength, INITIAL_HIGHLIGHT_RANGE);
            } else {
                int top = Math.max(0, scrollView.getScrollY() - htmlEditText.getTop());
                int bottom = Math.max(top, top + scrollView.getHeight());
                int firstLine = Math.max(0, layout.getLineForVertical(top) - 12);
                int lastLine = Math.min(layout.getLineCount() - 1, layout.getLineForVertical(bottom) + 12);
                start = layout.getLineStart(firstLine);
                end = layout.getLineEnd(lastLine);
            }
        }
        Editable editable = htmlEditText.getText();
        ForegroundColorSpan[] oldSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : oldSpans) {
            editable.removeSpan(span);
        }
        int count = syntaxSpans.length / 3;
        int low = 0;
        int high = count;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (syntaxSpans[middle * 3] < start) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        int first = low;
        while (first > 0 && syntaxSpans[(first - 1) * 3 + 1] > start) {
            first--;
        }
        for (int i = first; i < count; i++) {
            int offset = i * 3;
            int spanStart = syntaxSpans[offset];
            int spanEnd = syntaxSpans[offset + 1];
            if (spanStart >= end) {
                break;
            }
            int color = syntaxSpans[offset + 2];
            if (spanEnd > start && spanStart < spanEnd && spanEnd <= editable.length()) {
                editable.setSpan(new ForegroundColorSpan(color), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        lastAppliedRevision = editRevision;
    }

    private void updateStatusText() {
        String mode = isEditing ? "編集モード" : "閲覧モード";
        sourceStatusTextView.setText("HTMLソース  |  " + currentLineCount + " 行  |  " + currentCharacterCount + " 文字  |  " + mode);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void saveHtmlToFile() {
        final String currentText = htmlEditText.getText().toString();
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String fileName = timeStamp + (currentText.equals(originalHtml) ? ".html" : "_edited.html");
        ioExecutor.execute(() -> {
            try {
                String savedLocation;
                byte[] data = currentText.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "text/html");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        throw new java.io.IOException("Downloadへの保存先を作成できません");
                    }
                    try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                        if (output == null) {
                            throw new java.io.IOException("保存先を開けません");
                        }
                        output.write(data);
                        output.flush();
                    }
                    savedLocation = uri.toString();
                } else {
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                        throw new java.io.IOException("Downloadフォルダーを作成できません");
                    }
                    File file = new File(downloadDir, fileName);
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
                        output.write(data);
                        output.flush();
                    }
                    savedLocation = file.getAbsolutePath();
                }
                String location = savedLocation;
                uiHandler.post(() -> Toast.makeText(this, "保存しました: " + location, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                uiHandler.post(() -> Toast.makeText(this, "保存に失敗しました: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (searchOverlay != null && searchOverlay.getVisibility() == View.VISIBLE) {
            hideSearchOverlay();
            return;
        }
        if (isEditing) {
            toggleEditing();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (highlightRunnable != null) {
            uiHandler.removeCallbacks(highlightRunnable);
        }
        if (viewportRunnable != null) {
            uiHandler.removeCallbacks(viewportRunnable);
        }
        if (highlightFuture != null) {
            highlightFuture.cancel(true);
        }
        if (searchFuture != null) {
            searchFuture.cancel(true);
        }
        if (scrollChangedListener != null && scrollView != null && scrollView.getViewTreeObserver().isAlive()) {
            scrollView.getViewTreeObserver().removeOnScrollChangedListener(scrollChangedListener);
        }
        ioExecutor.shutdownNow();
        highlightExecutor.shutdownNow();
        searchExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveHtmlToFile();
            } else {
                Toast.makeText(this, "保存にはストレージ権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static final class HighlightResult {
        final int[] spans;
        final String lineNumbers;
        final int lineCount;
        final int characterCount;

        HighlightResult(int[] spans, String lineNumbers, int lineCount, int characterCount) {
            this.spans = spans;
            this.lineNumbers = lineNumbers;
            this.lineCount = lineCount;
            this.characterCount = characterCount;
        }
    }

    private static final class SyntaxHighlighter {
        private static final String JS_KEYWORDS = "|as|async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|of|return|static|super|switch|this|throw|try|typeof|var|void|while|with|yield|true|false|null|undefined|NaN|Infinity|";
        private static final String CSS_AT_RULES = "|charset|container|font-face|import|keyframes|media|namespace|page|supports|";
        private final String source;
        private final SpanBuilder spans = new SpanBuilder();
        private int index;

        private SyntaxHighlighter(String source) {
            this.source = source;
        }

        static int[] highlight(String source) {
            SyntaxHighlighter highlighter = new SyntaxHighlighter(source);
            highlighter.scanHtml();
            return highlighter.spans.toArray();
        }

        private void scanHtml() {
            int length = source.length();
            while (index < length) {
                if ((index & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (startsWith(index, "<!--")) {
                    int close = source.indexOf("-->", index + 4);
                    int end = close < 0 ? length : close + 3;
                    spans.add(index, end, COMMENT_COLOR);
                    index = end;
                    continue;
                }
                char current = source.charAt(index);
                if (current == '<' && index + 1 < length && (source.charAt(index + 1) == '!' || source.charAt(index + 1) == '?')) {
                    int endMark = findTagEnd(index + 2);
                    int end = endMark < 0 ? length : endMark + 1;
                    spans.add(index, end, DECLARATION_COLOR);
                    index = end;
                    continue;
                }
                if (current == '<' && looksLikeTagStart(index)) {
                    TagInfo tag = scanTag(index);
                    if (tag.end <= index) {
                        index++;
                        continue;
                    }
                    index = tag.end;
                    if (!tag.closing && !tag.selfClosing && tag.name.equalsIgnoreCase("script")) {
                        int closeStart = findClosingTag(index, "script");
                        int contentEnd = closeStart < 0 ? length : closeStart;
                        scanEmbedded(index, contentEnd, false);
                        index = contentEnd;
                    } else if (!tag.closing && !tag.selfClosing && tag.name.equalsIgnoreCase("style")) {
                        int closeStart = findClosingTag(index, "style");
                        int contentEnd = closeStart < 0 ? length : closeStart;
                        scanEmbedded(index, contentEnd, true);
                        index = contentEnd;
                    }
                    continue;
                }
                if (current == '&') {
                    int semicolon = source.indexOf(';', index + 1);
                    if (semicolon > index + 1 && semicolon - index <= 16 && isEntityBody(index + 1, semicolon)) {
                        spans.add(index, semicolon + 1, ENTITY_COLOR);
                        index = semicolon + 1;
                        continue;
                    }
                }
                index++;
            }
        }

        private boolean looksLikeTagStart(int position) {
            if (position + 1 >= source.length()) {
                return false;
            }
            char next = source.charAt(position + 1);
            if (next == '/') {
                return position + 2 < source.length() && isNameStart(source.charAt(position + 2));
            }
            return isNameStart(next);
        }

        private TagInfo scanTag(int start) {
            int endMarker = findTagEnd(start + 1);
            int end = endMarker < 0 ? source.length() : endMarker + 1;
            int cursor = start + 1;
            spans.add(start, start + 1, TAG_COLOR);
            boolean closing = false;
            if (cursor < end && source.charAt(cursor) == '/') {
                closing = true;
                spans.add(cursor, cursor + 1, TAG_COLOR);
                cursor++;
            }
            while (cursor < end && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            int nameStart = cursor;
            while (cursor < end && isNamePart(source.charAt(cursor))) {
                cursor++;
            }
            String name = source.substring(nameStart, cursor);
            if (nameStart < cursor) {
                spans.add(nameStart, cursor, TAG_COLOR);
            }
            boolean selfClosing = false;
            while (cursor < end) {
                char c = source.charAt(cursor);
                if (Character.isWhitespace(c)) {
                    cursor++;
                    continue;
                }
                if (c == '>') {
                    spans.add(cursor, cursor + 1, TAG_COLOR);
                    cursor++;
                    break;
                }
                if (c == '/') {
                    spans.add(cursor, cursor + 1, TAG_COLOR);
                    selfClosing = true;
                    cursor++;
                    continue;
                }
                int attributeStart = cursor;
                while (cursor < end) {
                    c = source.charAt(cursor);
                    if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') {
                        break;
                    }
                    cursor++;
                }
                if (cursor == attributeStart) {
                    cursor++;
                    continue;
                }
                spans.add(attributeStart, cursor, ATTRIBUTE_COLOR);
                while (cursor < end && Character.isWhitespace(source.charAt(cursor))) {
                    cursor++;
                }
                if (cursor < end && source.charAt(cursor) == '=') {
                    spans.add(cursor, cursor + 1, TAG_COLOR);
                    cursor++;
                    while (cursor < end && Character.isWhitespace(source.charAt(cursor))) {
                        cursor++;
                    }
                    if (cursor < end && (source.charAt(cursor) == '\'' || source.charAt(cursor) == '"')) {
                        char quote = source.charAt(cursor++);
                        while (cursor < end && source.charAt(cursor) != quote) {
                            cursor++;
                        }
                        if (cursor < end) {
                            cursor++;
                        }
                        spans.add(attributeStartAfterEquals(attributeStart, end), cursor, VALUE_COLOR);
                    } else {
                        int valueStart = cursor;
                        while (cursor < end && !Character.isWhitespace(source.charAt(cursor)) && source.charAt(cursor) != '>') {
                            cursor++;
                        }
                        if (valueStart < cursor) {
                            spans.add(valueStart, cursor, VALUE_COLOR);
                        }
                    }
                }
            }
            if (endMarker < 0) {
                end = source.length();
            }
            return new TagInfo(name, end, closing, selfClosing);
        }

        private int attributeStartAfterEquals(int attributeStart, int end) {
            int cursor = attributeStart;
            while (cursor < end && source.charAt(cursor) != '=') {
                cursor++;
            }
            if (cursor < end) {
                cursor++;
            }
            while (cursor < end && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            return cursor;
        }

        private int findTagEnd(int from) {
            char quote = 0;
            for (int i = from; i < source.length(); i++) {
                char c = source.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '>') {
                    return i;
                }
                if ((i & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                    return -1;
                }
            }
            return -1;
        }

        private int findClosingTag(int from, String tagName) {
            for (int i = from; i < source.length() - tagName.length() - 2; i++) {
                if (source.charAt(i) == '<' && source.charAt(i + 1) == '/' && source.regionMatches(true, i + 2, tagName, 0, tagName.length())) {
                    int afterName = i + 2 + tagName.length();
                    if (afterName >= source.length() || Character.isWhitespace(source.charAt(afterName)) || source.charAt(afterName) == '>' || source.charAt(afterName) == '/') {
                        return i;
                    }
                }
                if ((i & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                    return -1;
                }
            }
            return -1;
        }

        private void scanEmbedded(int from, int to, boolean css) {
            int i = from;
            while (i < to) {
                if ((i & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                    return;
                }
                char c = source.charAt(i);
                if (c == '/' && i + 1 < to && source.charAt(i + 1) == '*') {
                    int close = source.indexOf("*/", i + 2);
                    int end = close < 0 || close + 2 > to ? to : close + 2;
                    spans.add(i, end, COMMENT_COLOR);
                    i = end;
                    continue;
                }
                if (!css && c == '/' && i + 1 < to && source.charAt(i + 1) == '/') {
                    int end = i + 2;
                    while (end < to && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
                        end++;
                    }
                    spans.add(i, end, COMMENT_COLOR);
                    i = end;
                    continue;
                }
                if (c == '\'' || c == '"' || (!css && c == '`')) {
                    char quote = c;
                    int end = i + 1;
                    while (end < to) {
                        char value = source.charAt(end);
                        if (value == '\\') {
                            end = Math.min(to, end + 2);
                            continue;
                        }
                        if (value == quote) {
                            end++;
                            break;
                        }
                        end++;
                    }
                    spans.add(i, end, STRING_COLOR);
                    i = end;
                    continue;
                }
                if (Character.isDigit(c)) {
                    int end = i + 1;
                    while (end < to) {
                        char value = source.charAt(end);
                        if (Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == 'x' || value == 'X') {
                            end++;
                        } else {
                            break;
                        }
                    }
                    spans.add(i, end, NUMBER_COLOR);
                    i = end;
                    continue;
                }
                if (css && (c == '#' || c == '.')) {
                    int end = i + 1;
                    while (end < to && isWordPart(source.charAt(end))) {
                        end++;
                    }
                    if (end > i + 1) {
                        spans.add(i, end, CSS_SELECTOR_COLOR);
                        i = end;
                        continue;
                    }
                }
                if (Character.isLetter(c) || c == '_' || c == '$' || (css && c == '@')) {
                    int end = i + 1;
                    while (end < to && (isWordPart(source.charAt(end)) || (css && source.charAt(end) == '-'))) {
                        end++;
                    }
                    String word = source.substring(i, end);
                    if (!css) {
                        if (JS_KEYWORDS.contains("|" + word + "|")) {
                            spans.add(i, end, KEYWORD_COLOR);
                        } else {
                            int next = skipWhitespace(source, end, to);
                            if (next < to && source.charAt(next) == '(') {
                                spans.add(i, end, FUNCTION_COLOR);
                            }
                        }
                    } else {
                        int next = skipWhitespace(source, end, to);
                        if (word.startsWith("@") && CSS_AT_RULES.contains("|" + word.substring(1).toLowerCase(Locale.ROOT) + "|")) {
                            spans.add(i, end, DECLARATION_COLOR);
                        } else if (next < to && source.charAt(next) == ':') {
                            spans.add(i, end, CSS_PROPERTY_COLOR);
                        } else {
                            int previous = previousNonWhitespace(source, i - 1, from);
                            if (previous < from || source.charAt(previous) == '{' || source.charAt(previous) == '}' || source.charAt(previous) == ',' || source.charAt(previous) == ';') {
                                spans.add(i, end, CSS_SELECTOR_COLOR);
                            }
                        }
                    }
                    i = end;
                    continue;
                }
                if (css && c == '@') {
                    int end = i + 1;
                    while (end < to && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '-')) {
                        end++;
                    }
                    spans.add(i, end, DECLARATION_COLOR);
                    i = end;
                    continue;
                }
                i++;
            }
        }

        private int skipWhitespace(String text, int from, int limit) {
            int i = from;
            while (i < limit && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            return i;
        }

        private int previousNonWhitespace(String text, int from, int limit) {
            int i = from;
            while (i >= limit && Character.isWhitespace(text.charAt(i))) {
                i--;
            }
            return i;
        }

        private boolean startsWith(int position, String value) {
            return position + value.length() <= source.length() && source.regionMatches(position, value, 0, value.length());
        }

        private boolean isEntityBody(int start, int end) {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '#' || c == 'x' || c == 'X')) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isNameStart(char c) {
            return Character.isLetter(c) || c == '_' || c == ':';
        }

        private static boolean isNamePart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '.';
        }

        private static boolean isWordPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }

        private static final class TagInfo {
            final String name;
            final int end;
            final boolean closing;
            final boolean selfClosing;

            TagInfo(String name, int end, boolean closing, boolean selfClosing) {
                this.name = name;
                this.end = end;
                this.closing = closing;
                this.selfClosing = selfClosing;
            }
        }

        private static final class SpanBuilder {
            private int[] data = new int[2048];
            private int size;

            void add(int start, int end, int color) {
                if (start < 0 || end <= start) {
                    return;
                }
                if (size + 3 > data.length) {
                    int[] expanded = new int[Math.max(data.length * 2, size + 3)];
                    System.arraycopy(data, 0, expanded, 0, size);
                    data = expanded;
                }
                data[size++] = start;
                data[size++] = end;
                data[size++] = color;
            }

            int[] toArray() {
                int[] result = new int[size];
                System.arraycopy(data, 0, result, 0, size);
                return result;
            }
        }
    }
}
