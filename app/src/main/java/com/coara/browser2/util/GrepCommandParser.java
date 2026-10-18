package com.coara.browser2.util;

import java.util.ArrayList;
import java.util.List;

public final class GrepCommandParser {

    public static final class Result {
        public boolean commandStyle;
        public String error;
        public boolean patternGiven;
        public final List<String> ignoredFiles = new ArrayList<>();
    }

    private GrepCommandParser() {
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inToken = false;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\'') {
                int end = text.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("引用符が閉じていません");
                }
                cur.append(text, i + 1, end);
                inToken = true;
                i = end + 1;
            } else if (c == '"') {
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = text.charAt(i);
                    if (d == '\\' && i + 1 < n && "\"\\$`".indexOf(text.charAt(i + 1)) >= 0) {
                        cur.append(text.charAt(i + 1));
                        i += 2;
                    } else if (d == '"') {
                        closed = true;
                        i++;
                        break;
                    } else {
                        cur.append(d);
                        i++;
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException("引用符が閉じていません");
                }
                inToken = true;
            } else if (c == '\\' && i + 1 < n) {
                cur.append(text.charAt(i + 1));
                inToken = true;
                i += 2;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    inToken = false;
                }
                i++;
            } else {
                cur.append(c);
                inToken = true;
                i++;
            }
        }
        if (inToken) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private static int number(String v, String what) {
        try {
            long l = Long.parseLong(v.trim());
            if (l < 0) {
                throw new NumberFormatException();
            }
            return (int) Math.min(l, 100000);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " の数値が不正です: " + v);
        }
    }

    public static Result parse(String input, GrepEngine.Options o) {
        Result r = new Result();
        String text = input == null ? "" : input;
        String trimmed = text.trim();
        List<String> tokens;
        boolean command = false;
        try {
            tokens = trimmed.isEmpty() ? new ArrayList<String>() : tokenize(trimmed);
        } catch (IllegalArgumentException e) {
            tokens = null;
            if (trimmed.startsWith("grep ") || trimmed.startsWith("-")) {
                r.error = e.getMessage();
                return r;
            }
        }
        int start = 0;
        if (tokens != null && !tokens.isEmpty()) {
            String first = tokens.get(0);
            if (first.equals("grep") || first.equals("egrep") || first.equals("fgrep")) {
                command = true;
                start = 1;
                if (first.equals("egrep")) {
                    o.mode = GrepEngine.Mode.EXTENDED;
                } else if (first.equals("fgrep")) {
                    o.mode = GrepEngine.Mode.FIXED;
                }
            } else if (first.length() > 1 && first.charAt(0) == '-') {
                command = true;
            }
        }
        if (!command) {
            o.patterns.add(text);
            r.patternGiven = !text.isEmpty();
            return r;
        }
        r.commandStyle = true;
        int ctx = -1;
        boolean aSet = false;
        boolean bSet = false;
        boolean optionsDone = false;
        boolean positionalPattern = false;
        try {
            for (int t = start; t < tokens.size(); t++) {
                String tok = tokens.get(t);
                if (optionsDone || tok.equals("-") || !tok.startsWith("-")) {
                    if (!r.patternGiven && !positionalPattern) {
                        o.patterns.add(tok);
                        positionalPattern = true;
                        r.patternGiven = true;
                    } else {
                        r.ignoredFiles.add(tok);
                    }
                    continue;
                }
                if (tok.equals("--")) {
                    optionsDone = true;
                    continue;
                }
                if (tok.startsWith("--")) {
                    String name = tok.substring(2);
                    String value = null;
                    int eq = name.indexOf('=');
                    if (eq >= 0) {
                        value = name.substring(eq + 1);
                        name = name.substring(0, eq);
                    }
                    switch (name) {
                        case "ignore-case": o.ignoreCase = true; break;
                        case "no-ignore-case": o.ignoreCase = false; break;
                        case "invert-match": o.invert = true; break;
                        case "line-number": o.lineNumbers = true; break;
                        case "count": o.countOnly = true; break;
                        case "word-regexp": o.word = true; break;
                        case "line-regexp": o.lineMatch = true; break;
                        case "fixed-strings": o.mode = GrepEngine.Mode.FIXED; break;
                        case "extended-regexp": o.mode = GrepEngine.Mode.EXTENDED; break;
                        case "basic-regexp": o.mode = GrepEngine.Mode.BASIC; break;
                        case "perl-regexp": o.mode = GrepEngine.Mode.PERL; break;
                        case "only-matching": o.onlyMatching = true; break;
                        case "with-filename": o.filenameMode = 1; break;
                        case "no-filename": o.filenameMode = -1; break;
                        case "files-with-matches": o.filesWithMatches = true; break;
                        case "files-without-match": o.filesWithoutMatch = true; break;
                        case "text": o.text = true; break;
                        case "recursive":
                        case "dereference-recursive": o.recursive = true; break;
                        case "no-messages":
                        case "quiet":
                        case "silent":
                        case "color":
                        case "colour":
                        case "null":
                        case "line-buffered":
                        case "binary":
                        case "initial-tab":
                        case "byte-offset":
                            break;
                        case "binary-files":
                            if ("text".equals(value)) {
                                o.text = true;
                            }
                            break;
                        case "include":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.includes.add(value);
                            break;
                        case "exclude":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.excludes.add(value);
                            break;
                        case "max-count":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.maxCount = number(value, "-m");
                            break;
                        case "after-context":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.after = number(value, "-A");
                            aSet = true;
                            break;
                        case "before-context":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.before = number(value, "-B");
                            bSet = true;
                            break;
                        case "context":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            ctx = number(value, "-C");
                            break;
                        case "regexp":
                            if (value == null) {
                                value = tokens.get(++t);
                            }
                            o.patterns.add(value);
                            r.patternGiven = true;
                            break;
                        default:
                            r.error = "未対応のオプション: " + tok;
                            return r;
                    }
                    continue;
                }
                String cluster = tok.substring(1);
                int digits = -1;
                for (int k = 0; k < cluster.length(); k++) {
                    char c = cluster.charAt(k);
                    if (c >= '0' && c <= '9') {
                        digits = (digits < 0 ? 0 : digits * 10) + (c - '0');
                        ctx = Math.min(digits, 100000);
                        continue;
                    }
                    digits = -1;
                    switch (c) {
                        case 'i':
                        case 'y': o.ignoreCase = true; break;
                        case 'v': o.invert = true; break;
                        case 'n': o.lineNumbers = true; break;
                        case 'c': o.countOnly = true; break;
                        case 'w': o.word = true; break;
                        case 'x': o.lineMatch = true; break;
                        case 'F': o.mode = GrepEngine.Mode.FIXED; break;
                        case 'E': o.mode = GrepEngine.Mode.EXTENDED; break;
                        case 'G': o.mode = GrepEngine.Mode.BASIC; break;
                        case 'P': o.mode = GrepEngine.Mode.PERL; break;
                        case 'o': o.onlyMatching = true; break;
                        case 'H': o.filenameMode = 1; break;
                        case 'h': o.filenameMode = -1; break;
                        case 'l': o.filesWithMatches = true; break;
                        case 'L': o.filesWithoutMatch = true; break;
                        case 'a': o.text = true; break;
                        case 'r':
                        case 'R': o.recursive = true; break;
                        case 's':
                        case 'q':
                        case 'U':
                        case 'I':
                        case 'b':
                        case 'T':
                        case 'Z':
                        case 'z':
                            break;
                        case 'e':
                        case 'm':
                        case 'A':
                        case 'B':
                        case 'C': {
                            String value = cluster.substring(k + 1);
                            if (value.isEmpty()) {
                                if (t + 1 >= tokens.size()) {
                                    r.error = "-" + c + " に値が必要です";
                                    return r;
                                }
                                value = tokens.get(++t);
                            }
                            if (c == 'e') {
                                o.patterns.add(value);
                                r.patternGiven = true;
                            } else if (c == 'm') {
                                o.maxCount = number(value, "-m");
                            } else if (c == 'A') {
                                o.after = number(value, "-A");
                                aSet = true;
                            } else if (c == 'B') {
                                o.before = number(value, "-B");
                                bSet = true;
                            } else {
                                ctx = number(value, "-C");
                            }
                            k = cluster.length();
                            break;
                        }
                        default:
                            r.error = "未対応のオプション: -" + c;
                            return r;
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            r.error = "オプションの値が不足しています";
            return r;
        } catch (IllegalArgumentException e) {
            r.error = e.getMessage();
            return r;
        }
        if (aSet || bSet || ctx >= 0) {
            o.contextSet = true;
        }
        if (ctx >= 0) {
            if (!aSet) {
                o.after = ctx;
            }
            if (!bSet) {
                o.before = ctx;
            }
        }
        if (!r.patternGiven) {
            r.error = "パターンがありません";
        }
        return r;
    }
}
