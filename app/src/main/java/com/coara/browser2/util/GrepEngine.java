package com.coara.browser2.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GrepEngine {

    public enum Mode { BASIC, EXTENDED, FIXED, PERL }

    public static final class Options {
        public Mode mode = Mode.FIXED;
        public boolean ignoreCase;
        public boolean invert;
        public boolean lineNumbers;
        public boolean countOnly;
        public boolean word;
        public boolean lineMatch;
        public boolean onlyMatching;
        public boolean filesWithMatches;
        public boolean filesWithoutMatch;
        public boolean text;
        public boolean recursive;
        public boolean contextSet;
        public int after;
        public int before;
        public long maxCount = -1;
        public int filenameMode;
        public final List<String> patterns = new ArrayList<>();
        public final List<String> includes = new ArrayList<>();
        public final List<String> excludes = new ArrayList<>();
    }

    public interface Sink {
        void write(String text);

        boolean cancelled();

        void progress(long lines, long matches);
    }

    public static final class Stats {
        public long lines;
        public long matches;
        public int files;
        public int filesMatched;
    }

    private static final int SAMPLE_BYTES = 65536;
    private static final int BINARY_CHECK = 8192;
    private static final int MAX_LINE_CHARS = 4_000_000;

    private final Options options;
    private final Pattern pattern;
    private final Sink sink;
    private final Stats stats = new Stats();
    private boolean outputStarted;
    private final boolean contextRequested;
    private long lastProgress;

    public GrepEngine(Options options, Sink sink) {
        this.options = options;
        this.sink = sink;
        this.pattern = compile(options);
        this.contextRequested = options.after > 0 || options.before > 0 || options.contextSet;
    }

    public Stats stats() {
        return stats;
    }

    public static Pattern compile(Options o) {
        List<String> parts = new ArrayList<>();
        for (String raw : o.patterns) {
            String[] split = raw.split("\n", -1);
            for (String one : split) {
                parts.add(one);
            }
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            String translated;
            switch (o.mode) {
                case FIXED:
                    translated = Pattern.quote(p);
                    break;
                case PERL:
                    translated = p;
                    break;
                case EXTENDED:
                    translated = posixToJava(p, true);
                    break;
                default:
                    translated = posixToJava(p, false);
                    break;
            }
            if (i > 0) {
                combined.append('|');
            }
            combined.append("(?:").append(translated).append(')');
        }
        String body = combined.toString();
        if (o.lineMatch) {
            body = "^(?:" + body + ")$";
        } else if (o.word) {
            body = "(?<![\\p{L}\\p{N}_])(?:" + body + ")(?![\\p{L}\\p{N}_])";
        }
        int flags = 0;
        if (o.ignoreCase) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return Pattern.compile(body, flags);
    }

    private static String posixClass(String name) {
        switch (name) {
            case "alpha": return "\\p{L}";
            case "digit": return "0-9";
            case "alnum": return "\\p{L}\\p{Nd}";
            case "upper": return "\\p{Lu}";
            case "lower": return "\\p{Ll}";
            case "space": return "\\s";
            case "blank": return " \\t";
            case "punct": return "\\p{Punct}";
            case "print": return "\\P{Cntrl}";
            case "graph": return "\\p{Graph}";
            case "cntrl": return "\\p{Cntrl}";
            case "xdigit": return "0-9A-Fa-f";
            case "word": return "\\w";
            default:
                throw new PatternSyntaxException("Invalid character class", name, -1);
        }
    }

    private static int bracket(String p, int start, StringBuilder out) {
        int n = p.length();
        int i = start + 1;
        out.append('[');
        if (i < n && p.charAt(i) == '^') {
            out.append('^');
            i++;
        }
        boolean first = true;
        boolean closed = false;
        while (i < n) {
            char c = p.charAt(i);
            if (c == ']' && !first) {
                out.append(']');
                i++;
                closed = true;
                break;
            }
            if (c == '[' && i + 1 < n && (p.charAt(i + 1) == ':' || p.charAt(i + 1) == '.' || p.charAt(i + 1) == '=')) {
                char kind = p.charAt(i + 1);
                int end = p.indexOf(kind + "]", i + 2);
                if (end > 0) {
                    String name = p.substring(i + 2, end);
                    if (kind == ':') {
                        out.append(posixClass(name));
                    } else {
                        for (int k = 0; k < name.length(); k++) {
                            char ch = name.charAt(k);
                            if (!Character.isLetterOrDigit(ch)) {
                                out.append('\\');
                            }
                            out.append(ch);
                        }
                    }
                    i = end + 2;
                    first = false;
                    continue;
                }
            }
            if (c == ']' || c == '\\' || c == '[' || c == '&' || c == '^') {
                out.append('\\').append(c);
            } else {
                out.append(c);
            }
            first = false;
            i++;
        }
        if (!closed) {
            throw new PatternSyntaxException("Unmatched [", p, start);
        }
        return i;
    }

    public static String posixToJava(String p, boolean ere) {
        StringBuilder out = new StringBuilder(p.length() + 16);
        int n = p.length();
        int i = 0;
        boolean atStart = true;
        while (i < n) {
            char c = p.charAt(i);
            if (c == '\\') {
                if (i + 1 >= n) {
                    throw new PatternSyntaxException("Trailing backslash", p, i);
                }
                char d = p.charAt(i + 1);
                i += 2;
                if (!ere && (d == '(' || d == '|')) {
                    out.append(d);
                    atStart = true;
                    continue;
                }
                if (!ere && (d == '+' || d == '?') && atStart) {
                    out.append('\\').append(d);
                    atStart = false;
                    continue;
                }
                if (!ere && (d == ')' || d == '{' || d == '}' || d == '+' || d == '?')) {
                    out.append(d);
                    atStart = false;
                    continue;
                }
                if (d == '<') {
                    out.append("\\b(?=\\w)");
                } else if (d == '>') {
                    out.append("\\b(?<=\\w)");
                } else if (d == 'w' || d == 'W' || d == 's' || d == 'S' || d == 'b' || d == 'B' || (d >= '1' && d <= '9')) {
                    out.append('\\').append(d);
                } else if (Character.isLetterOrDigit(d)) {
                    out.append(d);
                } else {
                    out.append('\\').append(d);
                }
                atStart = false;
                continue;
            }
            if (c == '[') {
                i = bracket(p, i, out);
                atStart = false;
                continue;
            }
            if (c == '*') {
                if (atStart && ere) {
                    i++;
                    continue;
                }
                out.append(atStart ? "\\*" : "*");
                i++;
                atStart = false;
                continue;
            }
            if (c == '^') {
                if (ere) {
                    out.append('^');
                    i++;
                    atStart = true;
                } else if (atStart) {
                    out.append('^');
                    i++;
                } else {
                    out.append("\\^");
                    i++;
                }
                continue;
            }
            if (c == '$') {
                boolean anchor = ere || i == n - 1
                        || p.startsWith("\\)", i + 1) || p.startsWith("\\|", i + 1);
                out.append(anchor ? "$" : "\\$");
                i++;
                atStart = false;
                continue;
            }
            if (ere) {
                if (c == '(' || c == '|') {
                    out.append(c);
                    i++;
                    atStart = true;
                    continue;
                }
                if (c == ')') {
                    out.append(c);
                    i++;
                    atStart = false;
                    continue;
                }
                if (c == '+' || c == '?') {
                    if (!atStart) {
                        out.append(c);
                    }
                    i++;
                    continue;
                }
                if (c == '{') {
                    int end = p.indexOf('}', i);
                    if (!atStart && end > 0 && p.substring(i, end + 1).matches("\\{\\d+(,\\d*)?\\}")) {
                        out.append(p, i, end + 1);
                        i = end + 1;
                    } else {
                        out.append("\\{");
                        i++;
                    }
                    atStart = false;
                    continue;
                }
                if (c == '}') {
                    out.append("\\}");
                    i++;
                    atStart = false;
                    continue;
                }
            } else if (c == '(' || c == ')' || c == '|' || c == '+' || c == '?' || c == '{' || c == '}') {
                out.append('\\').append(c);
                i++;
                atStart = false;
                continue;
            }
            out.append(c);
            i++;
            atStart = false;
        }
        return out.toString();
    }

    public static boolean globMatch(String glob, String name) {
        StringBuilder rx = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                rx.append(".*");
            } else if (c == '?') {
                rx.append('.');
            } else if (c == '[') {
                int end = glob.indexOf(']', i + 1);
                if (end > i + 1) {
                    String body = glob.substring(i + 1, end);
                    if (body.startsWith("!")) {
                        body = "^" + body.substring(1);
                    }
                    rx.append('[').append(body.replace("\\", "\\\\")).append(']');
                    i = end;
                } else {
                    rx.append("\\[");
                }
            } else {
                rx.append(Pattern.quote(String.valueOf(c)));
            }
        }
        try {
            return Pattern.compile(rx.toString(), Pattern.DOTALL).matcher(name).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    public boolean acceptsName(String name) {
        String base = name;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (!options.includes.isEmpty()) {
            boolean ok = false;
            for (String g : options.includes) {
                if (globMatch(g, base)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        for (String g : options.excludes) {
            if (globMatch(g, base)) {
                return false;
            }
        }
        return true;
    }

    private static Charset detectCharset(byte[] sample, int len) {
        if (len >= 3 && (sample[0] & 0xFF) == 0xEF && (sample[1] & 0xFF) == 0xBB && (sample[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (len >= 2 && (sample[0] & 0xFF) == 0xFE && (sample[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (len >= 2 && (sample[0] & 0xFF) == 0xFF && (sample[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult result = decoder.decode(ByteBuffer.wrap(sample, 0, len), java.nio.CharBuffer.allocate(len + 1), false);
        if (!result.isError()) {
            return StandardCharsets.UTF_8;
        }
        if (Charset.isSupported("windows-31j")) {
            return Charset.forName("windows-31j");
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static boolean isUtf16(Charset cs) {
        String n = cs.name().toUpperCase(Locale.ROOT);
        return n.startsWith("UTF-16");
    }

    private void emit(String s) {
        outputStarted = true;
        sink.write(s);
    }

    private String prefix(String name, boolean showName, long lineNo, char sep) {
        StringBuilder sb = new StringBuilder();
        if (showName) {
            sb.append(name).append(sep);
        }
        if (options.lineNumbers) {
            sb.append(lineNo).append(sep);
        }
        return sb.toString();
    }

    private void printLine(String name, boolean showName, long lineNo, char sep, String text, long[] lastPrinted, boolean startsGroup) {
        if (startsGroup && contextRequested && outputStarted && (lastPrinted[0] == 0 || lineNo > lastPrinted[0] + 1)) {
            emit("--\n");
        }
        emit(prefix(name, showName, lineNo, sep) + text + "\n");
        lastPrinted[0] = lineNo;
    }

    private String readLine(Reader reader, char[] buf, int[] state, StringBuilder sb) throws IOException {
        sb.setLength(0);
        boolean any = false;
        boolean truncated = false;
        while (true) {
            if (state[0] >= state[1]) {
                int n = reader.read(buf, 0, buf.length);
                if (n < 0) {
                    return any ? sb.toString() : null;
                }
                state[0] = 0;
                state[1] = n;
                if (n == 0) {
                    continue;
                }
            }
            int start = state[0];
            int end = state[1];
            int k = start;
            while (k < end && buf[k] != '\n') {
                k++;
            }
            any = true;
            int len = k - start;
            if (!truncated) {
                int room = MAX_LINE_CHARS - sb.length();
                if (len > room) {
                    sb.append(buf, start, Math.max(0, room));
                    truncated = true;
                } else {
                    sb.append(buf, start, len);
                }
            }
            if (k < end) {
                state[0] = k + 1;
                int l = sb.length();
                if (l > 0 && sb.charAt(l - 1) == '\r') {
                    sb.setLength(l - 1);
                }
                return sb.toString();
            }
            state[0] = end;
        }
    }

    public void searchStream(InputStream raw, String name, boolean showName, Charset forced) throws IOException {
        stats.files++;
        BufferedInputStream in = new BufferedInputStream(raw, SAMPLE_BYTES);
        in.mark(SAMPLE_BYTES + 1);
        byte[] sample = new byte[SAMPLE_BYTES];
        int sampleLen = 0;
        while (sampleLen < SAMPLE_BYTES) {
            int n = in.read(sample, sampleLen, SAMPLE_BYTES - sampleLen);
            if (n < 0) {
                break;
            }
            sampleLen += n;
        }
        in.reset();
        Charset charset = forced != null ? forced : detectCharset(sample, sampleLen);
        boolean binary = false;
        if (!options.text && !isUtf16(charset)) {
            int check = Math.min(sampleLen, BINARY_CHECK);
            for (int i = 0; i < check; i++) {
                if (sample[i] == 0) {
                    binary = true;
                    break;
                }
            }
        }
        sample = null;

        Reader reader = new InputStreamReader(in, charset);
        Matcher matcher = pattern.matcher("");
        StringBuilder sb = new StringBuilder(256);
        char[] buf = new char[16384];
        int[] state = new int[2];
        String[] ringText = options.before > 0 ? new String[options.before] : null;
        long[] ringNo = options.before > 0 ? new long[options.before] : null;
        int ringCount = 0;
        int ringHead = 0;
        long[] lastPrinted = new long[1];
        long lineNo = 0;
        long selectedCount = 0;
        int afterLeft = 0;
        boolean stopAtMax = false;
        boolean listMode = options.countOnly || options.filesWithMatches || options.filesWithoutMatch;

        String line;
        while ((line = readLine(reader, buf, state, sb)) != null) {
            lineNo++;
            stats.lines++;
            if (lineNo == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            if ((lineNo & 0x3FFF) == 0) {
                if (sink.cancelled()) {
                    break;
                }
                long now = System.nanoTime();
                if (now - lastProgress > 150_000_000L) {
                    lastProgress = now;
                    sink.progress(stats.lines, stats.matches);
                }
            }
            if (stopAtMax) {
                if (afterLeft > 0) {
                    printLine(name, showName, lineNo, '-', line, lastPrinted, false);
                    afterLeft--;
                    if (afterLeft == 0) {
                        break;
                    }
                    continue;
                }
                break;
            }
            matcher.reset(line);
            boolean found = matcher.find();
            boolean selected = found != options.invert;
            if (selected) {
                selectedCount++;
                stats.matches++;
                if (listMode) {
                    if (options.filesWithMatches || options.filesWithoutMatch) {
                        break;
                    }
                    if (options.maxCount >= 0 && selectedCount >= options.maxCount) {
                        break;
                    }
                    continue;
                }
                if (binary) {
                    emit("バイナリファイル " + name + " に一致\n");
                    break;
                }
                if (ringCount > 0) {
                    for (int k = 0; k < ringCount; k++) {
                        int idx = (ringHead + k) % ringText.length;
                        printLine(name, showName, ringNo[idx], '-', ringText[idx], lastPrinted, k == 0);
                    }
                    ringCount = 0;
                    ringHead = 0;
                }
                if (options.onlyMatching) {
                    if (!options.invert) {
                        boolean firstOut = true;
                        matcher.reset(line);
                        while (matcher.find()) {
                            if (matcher.end() == matcher.start()) {
                                continue;
                            }
                            printLine(name, showName, lineNo, ':', matcher.group(), lastPrinted, firstOut);
                            firstOut = false;
                        }
                    }
                } else {
                    printLine(name, showName, lineNo, ':', line, lastPrinted, ringCount == 0);
                }
                afterLeft = options.after;
                if (options.maxCount >= 0 && selectedCount >= options.maxCount) {
                    stopAtMax = true;
                    if (afterLeft == 0) {
                        break;
                    }
                }
            } else if (!listMode && !binary) {
                if (afterLeft > 0) {
                    printLine(name, showName, lineNo, '-', line, lastPrinted, false);
                    afterLeft--;
                } else if (ringText != null) {
                    int idx;
                    if (ringCount < ringText.length) {
                        idx = (ringHead + ringCount) % ringText.length;
                        ringCount++;
                    } else {
                        idx = ringHead;
                        ringHead = (ringHead + 1) % ringText.length;
                    }
                    ringText[idx] = line;
                    ringNo[idx] = lineNo;
                }
            }
        }
        if (selectedCount > 0) {
            stats.filesMatched++;
        }
        if (options.countOnly) {
            emit((showName ? name + ":" : "") + selectedCount + "\n");
        } else if (options.filesWithMatches && selectedCount > 0) {
            emit(name + "\n");
        } else if (options.filesWithoutMatch && selectedCount == 0) {
            emit(name + "\n");
        }
    }
}
