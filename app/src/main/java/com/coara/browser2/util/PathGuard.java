package com.coara.browser2.util;

import java.io.File;
import java.io.IOException;

public final class PathGuard {
    private PathGuard() {
    }

    public static boolean isSafeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 200) {
            return false;
        }
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == 0 || c < 32 || c == 127) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 400 || path.charAt(0) == '/') {
            return false;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c") || lower.indexOf('\\') >= 0) {
            return false;
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c == 0 || c < 32 || c == 127) {
                    return false;
                }
            }
        }
        return true;
    }

    public static File child(File dir, String name) {
        if (dir == null || !isSafeName(name)) {
            return null;
        }
        File file = new File(dir, name);
        try {
            String parent = dir.getCanonicalPath();
            String actual = file.getCanonicalPath();
            if (actual.equals(parent) || !actual.startsWith(parent + File.separator)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }
}
