package com.coara.browser2.util;

import com.coara.browser2.model.Bookmark;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookmarkStore {

    public static final int SORT_MANUAL = 0;
    public static final int SORT_NEWEST = 1;
    public static final int SORT_TITLE = 2;
    public static final int SORT_SITE = 3;
    public static final String[] SORT_LABELS = {"追加順", "新しい順", "名前順", "サイト順"};

    private static final Pattern NETSCAPE_TOKEN = Pattern.compile(
        "<H3[^>]*>(.*?)</H3>|<A\\s[^>]*?HREF\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</A>|<DL[^>]*>|</DL>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Set<String> ROOT_FOLDERS = new HashSet<>();

    static {
        ROOT_FOLDERS.add("bookmarks");
        ROOT_FOLDERS.add("bookmarks bar");
        ROOT_FOLDERS.add("bookmarks menu");
        ROOT_FOLDERS.add("other bookmarks");
        ROOT_FOLDERS.add("mobile bookmarks");
        ROOT_FOLDERS.add("ブックマーク");
        ROOT_FOLDERS.add("ブックマーク バー");
        ROOT_FOLDERS.add("ブックマークバー");
        ROOT_FOLDERS.add("その他のブックマーク");
        ROOT_FOLDERS.add("モバイルのブックマーク");
    }

    private BookmarkStore() {
    }

    public static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        int hash = s.indexOf('#');
        if (hash >= 0) {
            s = s.substring(0, hash);
        }
        int scheme = s.indexOf("://");
        String rest = scheme >= 0 ? s.substring(scheme + 3) : s;
        int slash = rest.indexOf('/');
        String hostPart = slash >= 0 ? rest.substring(0, slash) : rest;
        String path = slash >= 0 ? rest.substring(slash) : "";
        hostPart = hostPart.toLowerCase(Locale.ROOT);
        if (hostPart.startsWith("www.")) {
            hostPart = hostPart.substring(4);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return hostPart + path;
    }

    public static int indexOfUrl(List<Bookmark> list, String url) {
        String key = normalizeUrl(url);
        if (key.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (normalizeUrl(list.get(i).getUrl()).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOfInstance(List<Bookmark> list, Bookmark target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    public static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        s = s.substring(0, end);
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(']') < colon) {
            s = s.substring(0, colon);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        return s;
    }

    public static String displayUrl(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0 && scheme <= 6) {
            s = s.substring(scheme + 3);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String displayTitle(Bookmark b) {
        String t = b.getTitle() == null ? "" : b.getTitle().trim();
        if (!t.isEmpty()) {
            return t;
        }
        String host = hostOf(b.getUrl());
        return host.isEmpty() ? b.getUrl() : host;
    }

    public static List<String> folders(List<Bookmark> list) {
        TreeSet<String> set = new TreeSet<>(Collator.getInstance(Locale.JAPAN));
        for (Bookmark b : list) {
            if (!b.getFolder().isEmpty()) {
                set.add(b.getFolder());
            }
        }
        return new ArrayList<>(set);
    }

    private static final String FOLDER_PREFS = "coara_bookmark_folders";
    private static final String FOLDER_KEY = "list";
    public static final int FOLDER_NAME_MAX = 60;

    public static String cleanFolderName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > FOLDER_NAME_MAX) {
            s = s.substring(0, FOLDER_NAME_MAX).trim();
        }
        return s;
    }

    private static final String DEFAULT_KEY = "default";

    public static String getDefaultFolder(Context context) {
        try {
            String v = context.getApplicationContext().getSharedPreferences(FOLDER_PREFS, Context.MODE_PRIVATE)
                .getString(DEFAULT_KEY, "");
            return cleanFolderName(v);
        } catch (Exception e) {
            return "";
        }
    }

    public static void setDefaultFolder(Context context, String folder) {
        try {
            context.getApplicationContext().getSharedPreferences(FOLDER_PREFS, Context.MODE_PRIVATE)
                .edit().putString(DEFAULT_KEY, cleanFolderName(folder)).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<String> loadExtraFolders(Context context) {
        List<String> out = new ArrayList<>();
        try {
            SharedPreferences sp = context.getApplicationContext().getSharedPreferences(FOLDER_PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(FOLDER_KEY, "");
            if (raw != null && !raw.isEmpty()) {
                for (String line : raw.split("\n")) {
                    String n = cleanFolderName(line);
                    if (!n.isEmpty() && !out.contains(n)) {
                        out.add(n);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void saveExtraFolders(Context context, Collection<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            String c = cleanFolderName(n);
            if (!c.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(c);
            }
        }
        try {
            context.getApplicationContext().getSharedPreferences(FOLDER_PREFS, Context.MODE_PRIVATE)
                .edit().putString(FOLDER_KEY, sb.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<String> allFolders(Context context, List<Bookmark> list) {
        TreeSet<String> set = new TreeSet<>(Collator.getInstance(Locale.JAPAN));
        set.addAll(loadExtraFolders(context));
        for (Bookmark b : list) {
            if (!b.getFolder().isEmpty()) {
                set.add(b.getFolder());
            }
        }
        return new ArrayList<>(set);
    }

    public static int countIn(List<Bookmark> list, String folder) {
        int n = 0;
        for (Bookmark b : list) {
            if (b.getFolder().equals(folder)) {
                n++;
            }
        }
        return n;
    }

    public static int renameFolder(List<Bookmark> list, String from, String to) {
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            Bookmark b = list.get(i);
            if (b.getFolder().equals(from)) {
                list.set(i, b.with(b.getTitle(), b.getUrl(), to));
                n++;
            }
        }
        return n;
    }

    public static int removeFolder(List<Bookmark> list, String folder, boolean deleteContents) {
        int n = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Bookmark b = list.get(i);
            if (b.getFolder().equals(folder)) {
                if (deleteContents) {
                    list.remove(i);
                } else {
                    list.set(i, b.with(b.getTitle(), b.getUrl(), ""));
                }
                n++;
            }
        }
        return n;
    }

    public static List<Bookmark> view(List<Bookmark> source, String query, String folder, int sortMode) {
        List<Bookmark> out = new ArrayList<>(source.size());
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String[] terms = q.isEmpty() ? new String[0] : q.split("\\s+");
        for (Bookmark b : source) {
            if (folder != null && !folder.equals(b.getFolder())) {
                continue;
            }
            if (terms.length > 0) {
                String hay = (displayTitle(b) + "\n" + b.getUrl() + "\n" + b.getFolder()).toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String t : terms) {
                    if (!hay.contains(t)) {
                        all = false;
                        break;
                    }
                }
                if (!all) {
                    continue;
                }
            }
            out.add(b);
        }
        if (sortMode == SORT_NEWEST) {
            Collections.reverse(out);
        } else if (sortMode == SORT_TITLE) {
            final Collator collator = Collator.getInstance(Locale.JAPAN);
            Collections.sort(out, new Comparator<Bookmark>() {
                @Override
                public int compare(Bookmark a, Bookmark b) {
                    return collator.compare(displayTitle(a), displayTitle(b));
                }
            });
        } else if (sortMode == SORT_SITE) {
            Collections.sort(out, new Comparator<Bookmark>() {
                @Override
                public int compare(Bookmark a, Bookmark b) {
                    return hostOf(a.getUrl()).compareToIgnoreCase(hostOf(b.getUrl()));
                }
            });
        }
        return out;
    }

    public static int merge(List<Bookmark> target, List<Bookmark> incoming) {
        Set<String> seen = new HashSet<>();
        for (Bookmark b : target) {
            seen.add(normalizeUrl(b.getUrl()));
        }
        int added = 0;
        for (Bookmark b : incoming) {
            String key = normalizeUrl(b.getUrl());
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            target.add(b);
            added++;
        }
        return added;
    }

    public static List<Bookmark> parseJson(String json) throws JSONException {
        String text = json == null ? "" : json.trim();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        JSONArray array;
        if (text.startsWith("{")) {
            JSONObject root = new JSONObject(text);
            array = root.optJSONArray("bookmarks");
            if (array == null) {
                throw new JSONException("bookmarks array not found");
            }
        } else {
            array = new JSONArray(text);
        }
        List<Bookmark> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String url = obj.optString("url", "").trim();
            if (url.isEmpty()) {
                continue;
            }
            out.add(new Bookmark(obj.optString("title", ""), url, obj.optString("folder", "")));
        }
        return out;
    }

    public static String toJson(List<Bookmark> list) {
        JSONArray array = new JSONArray();
        for (Bookmark b : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", b.getTitle());
                obj.put("url", b.getUrl());
                if (!b.getFolder().isEmpty()) {
                    obj.put("folder", b.getFolder());
                }
            } catch (JSONException ignored) {
            }
            array.put(obj);
        }
        return array.toString();
    }

    public static boolean looksLikeHtml(String text) {
        if (text == null) {
            return false;
        }
        String head = text.trim();
        if (head.length() > 2048) {
            head = head.substring(0, 2048);
        }
        String lower = head.toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype netscape-bookmark") || lower.contains("<dl>") || lower.contains("<a href");
    }

    public static List<Bookmark> parseNetscapeHtml(String html) {
        List<Bookmark> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        List<String> stack = new ArrayList<>();
        String pending = null;
        Matcher m = NETSCAPE_TOKEN.matcher(html);
        while (m.find()) {
            String token = m.group();
            String upper = token.length() >= 3 ? token.substring(0, 3).toUpperCase(Locale.ROOT) : token;
            if (m.group(1) != null) {
                pending = decodeEntities(stripTags(m.group(1))).trim();
            } else if (m.group(2) != null) {
                String url = decodeEntities(m.group(2)).trim();
                if (url.isEmpty() || url.regionMatches(true, 0, "javascript:", 0, 11) || url.regionMatches(true, 0, "place:", 0, 6)) {
                    continue;
                }
                String title = decodeEntities(stripTags(m.group(3))).trim();
                String folder = "";
                for (int i = stack.size() - 1; i >= 0; i--) {
                    if (!stack.get(i).isEmpty()) {
                        folder = stack.get(i);
                        break;
                    }
                }
                out.add(new Bookmark(title, url, folder));
            } else if (upper.startsWith("</D")) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                String name = pending == null ? "" : pending;
                if (ROOT_FOLDERS.contains(name.toLowerCase(Locale.ROOT))) {
                    name = "";
                }
                stack.add(name);
                pending = null;
            }
        }
        return out;
    }

    public static String toNetscapeHtml(List<Bookmark> list) {
        return toNetscapeHtml(list, new ArrayList<String>());
    }

    public static String toNetscapeHtml(List<Bookmark> list, Collection<String> extraFolders) {
        StringBuilder sb = new StringBuilder(list.size() * 96 + 256);
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
        for (Bookmark b : list) {
            if (b.getFolder().isEmpty()) {
                appendAnchor(sb, b, "    ");
            }
        }
        TreeSet<String> names = new TreeSet<>(Collator.getInstance(Locale.JAPAN));
        names.addAll(folders(list));
        for (String extra : extraFolders) {
            String c = cleanFolderName(extra);
            if (!c.isEmpty()) {
                names.add(c);
            }
        }
        for (String folder : names) {
            sb.append("    <DT><H3>").append(escapeHtml(folder)).append("</H3>\n    <DL><p>\n");
            for (Bookmark b : list) {
                if (folder.equals(b.getFolder())) {
                    appendAnchor(sb, b, "        ");
                }
            }
            sb.append("    </DL><p>\n");
        }
        sb.append("</DL><p>\n");
        return sb.toString();
    }

    private static void appendAnchor(StringBuilder sb, Bookmark b, String indent) {
        sb.append(indent).append("<DT><A HREF=\"").append(escapeHtml(b.getUrl())).append("\">")
            .append(escapeHtml(displayTitle(b))).append("</A>\n");
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String stripTags(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "");
    }

    private static String decodeEntities(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s == null ? "" : s;
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&");
    }
}
