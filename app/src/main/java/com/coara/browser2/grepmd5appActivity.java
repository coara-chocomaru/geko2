package com.coara.browser2;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.coara.browser2.util.GrepCommandParser;
import com.coara.browser2.util.GrepEngine;
import com.coara.browser2.util.LogFileSaver;
import com.google.android.material.chip.Chip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.PatternSyntaxException;

public class grepmd5appActivity extends Activity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int PICK_FOLDER_REQUEST = 2;
    private static final int MAX_RESULT_CHARS = 6_000_000;
    private static final int MAX_DISPLAY_CHARS = 300_000;
    private static final int MAX_TREE_FILES = 50_000;
    private static final int MAX_TREE_DEPTH = 40;
    private static final String PREFS = "coara_grep";
    private static final String[] CHARSET_LABELS = {"文字コード: 自動", "UTF-8", "Shift_JIS", "EUC-JP", "ISO-8859-1", "UTF-16"};

    private static final class Item {
        final Uri uri;
        final String name;
        final long size;

        Item(Uri uri, String name, long size) {
            this.uri = uri;
            this.name = name;
            this.size = size;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GrepWorker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean cancelFlag = new AtomicBoolean();
    private final List<Item> items = new ArrayList<>();
    private Uri treeUri;
    private String treeName;
    private boolean busy;
    private String lastResult = "";
    private String lastLabel = "grep";

    private EditText grepInput;
    private EditText hashCompareInput;
    private EditText etAfter;
    private EditText etBefore;
    private EditText etContext;
    private EditText etMax;
    private TextView resultTextView;
    private TextView selectedInfoText;
    private TextView statusText;
    private TextView optionsToggle;
    private View optionsPanel;
    private ProgressBar progressBar;
    private Spinner charsetSpinner;
    private Button grepButton;
    private Button stopButton;
    private Button md5Button;
    private Button sha1Button;
    private Button sha256Button;
    private Button sha512Button;
    private Chip chipI, chipN, chipV, chipC, chipW, chipX, chipO, chipL, chipA;
    private Chip chipModeFixed, chipModeBasic, chipModeExtended, chipModePerl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grep);

        Button selectFileButton = findViewById(R.id.selectFileButton);
        Button selectFolderButton = findViewById(R.id.selectFolderButton);
        Button clearSelectionButton = findViewById(R.id.clearSelectionButton);
        Button copyResultButton = findViewById(R.id.copyResultButton);
        Button saveResultButton = findViewById(R.id.saveResultButton);
        Button clearResultButton = findViewById(R.id.clearResultButton);
        grepButton = findViewById(R.id.grepButton);
        stopButton = findViewById(R.id.stopButton);
        md5Button = findViewById(R.id.md5Button);
        sha1Button = findViewById(R.id.sha1Button);
        sha256Button = findViewById(R.id.sha256Button);
        sha512Button = findViewById(R.id.sha512Button);
        grepInput = findViewById(R.id.grepInput);
        hashCompareInput = findViewById(R.id.hashCompareInput);
        etAfter = findViewById(R.id.etAfter);
        etBefore = findViewById(R.id.etBefore);
        etContext = findViewById(R.id.etContext);
        etMax = findViewById(R.id.etMax);
        resultTextView = findViewById(R.id.resultTextView);
        selectedInfoText = findViewById(R.id.selectedInfoText);
        statusText = findViewById(R.id.statusText);
        optionsToggle = findViewById(R.id.optionsToggle);
        optionsPanel = findViewById(R.id.optionsPanel);
        progressBar = findViewById(R.id.progressBar);
        charsetSpinner = findViewById(R.id.charsetSpinner);
        chipI = findViewById(R.id.chipI);
        chipN = findViewById(R.id.chipN);
        chipV = findViewById(R.id.chipV);
        chipC = findViewById(R.id.chipC);
        chipW = findViewById(R.id.chipW);
        chipX = findViewById(R.id.chipX);
        chipO = findViewById(R.id.chipO);
        chipL = findViewById(R.id.chipL);
        chipA = findViewById(R.id.chipA);
        chipModeFixed = findViewById(R.id.chipModeFixed);
        chipModeBasic = findViewById(R.id.chipModeBasic);
        chipModeExtended = findViewById(R.id.chipModeExtended);
        chipModePerl = findViewById(R.id.chipModePerl);

        ArrayAdapter<String> charsetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CHARSET_LABELS);
        charsetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        charsetSpinner.setAdapter(charsetAdapter);

        restoreState();

        selectFileButton.setOnClickListener(v -> openFilePicker());
        selectFolderButton.setOnClickListener(v -> openFolderPicker());
        clearSelectionButton.setOnClickListener(v -> {
            items.clear();
            treeUri = null;
            treeName = null;
            updateSelectionInfo();
        });
        grepButton.setOnClickListener(v -> executeGrep());
        md5Button.setOnClickListener(v -> checkHash("MD5", "MD5"));
        sha1Button.setOnClickListener(v -> checkHash("SHA-1", "SHA1"));
        sha256Button.setOnClickListener(v -> checkHash("SHA-256", "SHA256"));
        sha512Button.setOnClickListener(v -> checkHash("SHA-512", "SHA512"));
        stopButton.setOnClickListener(v -> {
            if (busy) {
                cancelFlag.set(true);
                statusText.setText("停止中…");
            }
        });
        copyResultButton.setOnClickListener(v -> copyResult());
        saveResultButton.setOnClickListener(v -> saveResult(false));
        clearResultButton.setOnClickListener(v -> {
            if (busy) {
                Toast.makeText(this, "実行中は消去できません。", Toast.LENGTH_SHORT).show();
                return;
            }
            lastResult = "";
            resultTextView.setText("");
            statusText.setText("待機中");
        });
        optionsToggle.setOnClickListener(v -> {
            boolean show = optionsPanel.getVisibility() != View.VISIBLE;
            optionsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            optionsToggle.setText(show ? "詳細設定 ▲" : "詳細設定 ▼");
        });
        grepInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeGrep();
                return true;
            }
            return false;
        });
        updateSelectionInfo();
        updateBusy(false);
    }

    private void restoreState() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            chipI.setChecked(sp.getBoolean("i", true));
            chipN.setChecked(sp.getBoolean("n", true));
            chipV.setChecked(sp.getBoolean("v", false));
            chipC.setChecked(sp.getBoolean("c", false));
            chipW.setChecked(sp.getBoolean("w", false));
            chipX.setChecked(sp.getBoolean("x", false));
            chipO.setChecked(sp.getBoolean("o", false));
            chipL.setChecked(sp.getBoolean("l", false));
            chipA.setChecked(sp.getBoolean("a", false));
            int mode = sp.getInt("mode", 0);
            Chip[] modes = {chipModeFixed, chipModeBasic, chipModeExtended, chipModePerl};
            modes[Math.max(0, Math.min(3, mode))].setChecked(true);
            etAfter.setText(sp.getString("A", ""));
            etBefore.setText(sp.getString("B", ""));
            etContext.setText(sp.getString("C", ""));
            etMax.setText(sp.getString("m", ""));
            charsetSpinner.setSelection(Math.max(0, Math.min(CHARSET_LABELS.length - 1, sp.getInt("charset", 0))));
        } catch (Exception ignored) {
        }
    }

    private void persistState() {
        try {
            int mode = chipModePerl.isChecked() ? 3 : chipModeExtended.isChecked() ? 2 : chipModeBasic.isChecked() ? 1 : 0;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("i", chipI.isChecked())
                    .putBoolean("n", chipN.isChecked())
                    .putBoolean("v", chipV.isChecked())
                    .putBoolean("c", chipC.isChecked())
                    .putBoolean("w", chipW.isChecked())
                    .putBoolean("x", chipX.isChecked())
                    .putBoolean("o", chipO.isChecked())
                    .putBoolean("l", chipL.isChecked())
                    .putBoolean("a", chipA.isChecked())
                    .putInt("mode", mode)
                    .putString("A", etAfter.getText().toString())
                    .putString("B", etBefore.getText().toString())
                    .putString("C", etContext.getText().toString())
                    .putString("m", etMax.getText().toString())
                    .putInt("charset", charsetSpinner.getSelectedItemPosition())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        persistState();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelFlag.set(true);
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void openFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_FILE_REQUEST);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, PICK_FILE_REQUEST);
            } catch (Exception ex) {
                Toast.makeText(this, "ファイル選択を開けません。", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFolderPicker() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "フォルダ選択を開けません。", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == PICK_FILE_REQUEST) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) {
                        uris.add(u);
                    }
                }
            }
            if (uris.isEmpty() && data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) {
                Toast.makeText(this, "ファイルが選択されませんでした。", Toast.LENGTH_SHORT).show();
                return;
            }
            items.clear();
            treeUri = null;
            treeName = null;
            for (Uri u : uris) {
                String[] meta = queryMeta(u);
                items.add(new Item(u, meta[0] != null ? meta[0] : "unknown", parseSize(meta[1])));
            }
            updateSelectionInfo();
        } else if (requestCode == PICK_FOLDER_REQUEST) {
            Uri u = data.getData();
            if (u == null) {
                return;
            }
            items.clear();
            treeUri = u;
            try {
                String docId = DocumentsContract.getTreeDocumentId(u);
                int colon = docId.lastIndexOf(':');
                String tail = colon >= 0 ? docId.substring(colon + 1) : docId;
                treeName = tail.isEmpty() ? docId : tail;
            } catch (Exception e) {
                treeName = "folder";
            }
            updateSelectionInfo();
        }
    }

    private static long parseSize(String s) {
        try {
            return s == null ? -1 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String[] queryMeta(Uri uri) {
        String[] out = new String[2];
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && !c.isNull(n)) {
                    out[0] = c.getString(n);
                }
                if (s >= 0 && !c.isNull(s)) {
                    out[1] = String.valueOf(c.getLong(s));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void updateSelectionInfo() {
        if (treeUri != null) {
            selectedInfoText.setText("フォルダ(再帰): " + treeName);
        } else if (items.isEmpty()) {
            selectedInfoText.setText("ファイル未選択");
        } else if (items.size() == 1) {
            Item it = items.get(0);
            selectedInfoText.setText(it.name + (it.size >= 0 ? "  (" + formatSize(it.size) + ")" : ""));
        } else {
            StringBuilder sb = new StringBuilder(items.size() + "ファイル: ");
            for (int i = 0; i < items.size() && i < 3; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(items.get(i).name);
            }
            if (items.size() > 3) {
                sb.append(" …");
            }
            selectedInfoText.setText(sb.toString());
        }
    }

    private static String formatSize(long b) {
        if (b < 1024) {
            return b + " B";
        }
        double kb = b / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private boolean hasSource() {
        return treeUri != null || !items.isEmpty();
    }

    private void updateBusy(boolean b) {
        busy = b;
        grepButton.setEnabled(!b);
        md5Button.setEnabled(!b);
        sha1Button.setEnabled(!b);
        sha256Button.setEnabled(!b);
        sha512Button.setEnabled(!b);
        stopButton.setEnabled(b);
        progressBar.setVisibility(b ? View.VISIBLE : View.INVISIBLE);
        if (b) {
            progressBar.setIndeterminate(true);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(grepInput.getWindowToken(), 0);
        }
    }

    private static int intOf(EditText e, int def) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) {
            return def;
        }
        try {
            return (int) Math.min(100000, Long.parseLong(s));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private Charset selectedCharset() {
        try {
            switch (charsetSpinner.getSelectedItemPosition()) {
                case 1: return StandardCharsets.UTF_8;
                case 2: return Charset.isSupported("windows-31j") ? Charset.forName("windows-31j") : Charset.forName("Shift_JIS");
                case 3: return Charset.forName("EUC-JP");
                case 4: return StandardCharsets.ISO_8859_1;
                case 5: return StandardCharsets.UTF_16;
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private GrepEngine.Options buildOptions() {
        GrepEngine.Options o = new GrepEngine.Options();
        o.mode = chipModePerl.isChecked() ? GrepEngine.Mode.PERL
                : chipModeExtended.isChecked() ? GrepEngine.Mode.EXTENDED
                : chipModeBasic.isChecked() ? GrepEngine.Mode.BASIC : GrepEngine.Mode.FIXED;
        o.ignoreCase = chipI.isChecked();
        o.lineNumbers = chipN.isChecked();
        o.invert = chipV.isChecked();
        o.countOnly = chipC.isChecked();
        o.word = chipW.isChecked();
        o.lineMatch = chipX.isChecked();
        o.onlyMatching = chipO.isChecked();
        o.filesWithMatches = chipL.isChecked();
        o.text = chipA.isChecked();
        int ctx = intOf(etContext, -1);
        int a = intOf(etAfter, -1);
        int b = intOf(etBefore, -1);
        o.after = a >= 0 ? a : Math.max(0, ctx);
        o.before = b >= 0 ? b : Math.max(0, ctx);
        o.contextSet = a >= 0 || b >= 0 || ctx >= 0;
        int m = intOf(etMax, -1);
        o.maxCount = m > 0 ? m : -1;
        return o;
    }

    private void executeGrep() {
        if (busy) {
            return;
        }
        if (!hasSource()) {
            showResult("ファイルまたはフォルダを選択してください。", false);
            return;
        }
        String text = grepInput.getText().toString();
        if (text.trim().isEmpty()) {
            showResult("キーワードを入力してください。", false);
            return;
        }
        final GrepEngine.Options options = buildOptions();
        GrepCommandParser.Result parsed = GrepCommandParser.parse(text, options);
        if (parsed.error != null) {
            showResult("エラー: " + parsed.error, false);
            return;
        }
        final GrepEngine engine;
        try {
            final StringBuilder out = new StringBuilder();
            final long[] counters = new long[2];
            final GrepEngine.Sink sink = new GrepEngine.Sink() {
                private boolean full;

                @Override
                public void write(String t) {
                    if (full) {
                        return;
                    }
                    if (out.length() + t.length() > MAX_RESULT_CHARS) {
                        full = true;
                        out.append("\n… 出力上限に達したため打ち切りました\n");
                        cancelFlag.set(true);
                        return;
                    }
                    out.append(t);
                }

                @Override
                public boolean cancelled() {
                    return cancelFlag.get();
                }

                @Override
                public void progress(long lines, long matches) {
                    counters[0] = lines;
                    counters[1] = matches;
                    handler.post(() -> statusText.setText(String.format(Locale.US, "検索中… %d行 / %d件", lines, matches)));
                }
            };
            engine = new GrepEngine(options, sink);
            hideKeyboard();
            cancelFlag.set(false);
            updateBusy(true);
            statusText.setText("検索中…");
            lastLabel = "grep";
            final Charset charset = selectedCharset();
            final String label = text.trim();
            final List<Item> snapshot = new ArrayList<>(items);
            final Uri tree = treeUri;
            final long t0 = System.currentTimeMillis();
            executor.execute(() -> {
                runGrep(engine, options, charset, snapshot, tree, out);
                final GrepEngine.Stats st = engine.stats();
                final double sec = (System.currentTimeMillis() - t0) / 1000.0;
                final boolean cancelled = cancelFlag.get();
                String body = out.toString();
                if (body.isEmpty() && !cancelled) {
                    body = "マッチする行が見つかりませんでした。";
                }
                String summary = String.format(Locale.US, "\n--- %d件一致 / %d行 / %dファイル / %.2f秒%s\n",
                        st.matches, st.lines, st.files, sec, cancelled ? " (中断)" : "");
                final String full = body + (body.endsWith("\n") ? "" : "\n") + summary;
                final String saveText = "# grep " + label + "\n" + full;
                handler.post(() -> {
                    showResult(full, true);
                    statusText.setText(String.format(Locale.US, "完了: %d件一致 (%.2f秒)", st.matches, sec));
                    updateBusy(false);
                });
            });
        } catch (PatternSyntaxException e) {
            showResult("正規表現エラー: " + e.getDescription() + (e.getIndex() >= 0 ? " (位置 " + e.getIndex() + ")" : ""), false);
        } catch (RuntimeException e) {
            showResult("エラー: " + e.getMessage(), false);
            updateBusy(false);
        }
    }

    private void runGrep(GrepEngine engine, GrepEngine.Options options, Charset charset, List<Item> snapshot, Uri tree, StringBuilder out) {
        boolean showName = options.filenameMode == 1 || (options.filenameMode == 0 && (tree != null || snapshot.size() > 1));
        List<Item> targets;
        if (tree != null) {
            targets = new ArrayList<>();
            walkTree(tree, DocumentsContract.getTreeDocumentId(tree), "", 0, targets, engine);
        } else {
            targets = snapshot;
        }
        for (Item it : targets) {
            if (cancelFlag.get()) {
                break;
            }
            try (InputStream in = getContentResolver().openInputStream(it.uri)) {
                if (in == null) {
                    out.append("エラー: ").append(it.name).append(": 開けません\n");
                    continue;
                }
                engine.searchStream(in, it.name, showName, charset);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                out.append("エラー: ").append(it.name).append(": ").append(e.getMessage()).append('\n');
            }
        }
    }

    private void walkTree(Uri tree, String docId, String prefix, int depth, List<Item> sink, GrepEngine engine) {
        if (depth > MAX_TREE_DEPTH || sink.size() >= MAX_TREE_FILES || cancelFlag.get()) {
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        List<String[]> dirs = new ArrayList<>();
        try (Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
            if (c == null) {
                return;
            }
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? -1 : c.getLong(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    dirs.add(new String[]{id, name});
                } else if (sink.size() < MAX_TREE_FILES) {
                    String rel = prefix + name;
                    if (engine == null || engine.acceptsName(rel)) {
                        sink.add(new Item(DocumentsContract.buildDocumentUriUsingTree(tree, id), rel, size));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        for (String[] d : dirs) {
            walkTree(tree, d[0], prefix + d[1] + "/", depth + 1, sink, engine);
        }
    }

    private void checkHash(final String algorithm, final String label) {
        if (busy) {
            return;
        }
        if (!hasSource()) {
            showResult("ファイルまたはフォルダを選択してください。", false);
            return;
        }
        hideKeyboard();
        cancelFlag.set(false);
        updateBusy(true);
        statusText.setText(label + " 計算中…");
        lastLabel = label.toLowerCase(Locale.US) + "sum";
        final String expected = hashCompareInput.getText().toString().replaceAll("\\s+", "").toLowerCase(Locale.US);
        final List<Item> snapshot = new ArrayList<>(items);
        final Uri tree = treeUri;
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(algorithm).append("]\n");
            List<Item> targets;
            if (tree != null) {
                targets = new ArrayList<>();
                walkTree(tree, DocumentsContract.getTreeDocumentId(tree), "", 0, targets, null);
            } else {
                targets = snapshot;
            }
            int matched = 0;
            int done = 0;
            for (Item it : targets) {
                if (cancelFlag.get()) {
                    sb.append("(中断)\n");
                    break;
                }
                final String itemName = it.name;
                handler.post(() -> statusText.setText(label + " 計算中… " + itemName));
                String hex;
                try {
                    hex = digest(algorithm, it);
                } catch (IOException | NoSuchAlgorithmException | SecurityException e) {
                    sb.append("エラー: ").append(it.name).append(": ").append(e.getMessage()).append('\n');
                    continue;
                }
                if (hex == null) {
                    sb.append("(中断)\n");
                    break;
                }
                done++;
                sb.append(hex).append("  ").append(it.name);
                if (it.size >= 0) {
                    sb.append("  (").append(formatSize(it.size)).append(')');
                }
                if (!expected.isEmpty()) {
                    if (hex.equals(expected)) {
                        matched++;
                        sb.append("  → 一致 OK");
                    } else {
                        sb.append("  → 不一致 NG");
                    }
                }
                sb.append('\n');
            }
            if (!expected.isEmpty()) {
                String guess = lengthHint(expected.length());
                if (guess != null && !guess.equals(algorithm)) {
                    sb.append("注意: 入力した照合値の長さは ").append(guess).append(" 相当です\n");
                }
                sb.append(String.format(Locale.US, "照合: %d/%d 一致\n", matched, done));
            }
            final String result = sb.toString();
            handler.post(() -> {
                showResult(result, true);
                statusText.setText("完了");
                updateBusy(false);
            });
        });
    }

    private static String lengthHint(int len) {
        switch (len) {
            case 32: return "MD5";
            case 40: return "SHA-1";
            case 64: return "SHA-256";
            case 128: return "SHA-512";
            default: return null;
        }
    }

    private String digest(String algorithm, final Item it) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        try (InputStream in = getContentResolver().openInputStream(it.uri)) {
            if (in == null) {
                throw new IOException("開けません");
            }
            byte[] buf = new byte[65536];
            long total = 0;
            long lastUi = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelFlag.get()) {
                    return null;
                }
                md.update(buf, 0, n);
                total += n;
                long now = System.nanoTime();
                if (it.size > 0 && now - lastUi > 150_000_000L) {
                    lastUi = now;
                    final int pct = (int) Math.min(100, total * 100 / it.size);
                    handler.post(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(100);
                        progressBar.setProgress(pct);
                    });
                }
            }
        }
        byte[] bytes = md.digest();
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            hex[i * 2] = digits[(bytes[i] >> 4) & 0xF];
            hex[i * 2 + 1] = digits[bytes[i] & 0xF];
        }
        return new String(hex);
    }

    private void showResult(String text, boolean store) {
        lastResult = text;
        if (text.length() > MAX_DISPLAY_CHARS) {
            resultTextView.setText(text.substring(0, MAX_DISPLAY_CHARS)
                    + "\n… 表示は先頭 " + MAX_DISPLAY_CHARS + " 文字までです。コピー/保存で全文を取得できます。");
        } else {
            resultTextView.setText(text);
        }
        final View parent = (View) resultTextView.getParent();
        if (parent instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) parent).scrollTo(0, 0);
        }
    }

    private void copyResult() {
        String text = null;
        int s = resultTextView.getSelectionStart();
        int e = resultTextView.getSelectionEnd();
        if (s >= 0 && e >= 0 && s != e) {
            text = resultTextView.getText().subSequence(Math.min(s, e), Math.max(s, e)).toString();
        }
        if (text == null) {
            text = lastResult;
        }
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "コピーする結果がありません。", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("grep", text));
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveResult(boolean silent) {
        if (lastResult == null || lastResult.isEmpty()) {
            Toast.makeText(this, "保存する結果がありません。", Toast.LENGTH_SHORT).show();
            return;
        }
        saveText(lastLabel.equals("grep") ? "grep_log" : lastLabel + "_log", lastResult);
    }

    private void saveText(String type, String content) {
        String base = "log";
        if (treeUri != null && treeName != null) {
            base = treeName;
        } else if (!items.isEmpty()) {
            base = items.get(0).name;
        }
        final String safe = base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        final String fileName = type + "_" + (safe.length() > 60 ? safe.substring(0, 60) : safe) + "_" + LogFileSaver.timestamp() + ".txt";
        final Context appContext = getApplicationContext();
        final String body = content;
        Thread t = new Thread(() -> {
            final String path = LogFileSaver.save(appContext, fileName, body);
            handler.post(() -> Toast.makeText(appContext, path != null ? "ログを保存しました！ " + path : "ログの保存に失敗しました", Toast.LENGTH_LONG).show());
        }, "GrepSave");
        t.setDaemon(true);
        t.start();
    }
}
