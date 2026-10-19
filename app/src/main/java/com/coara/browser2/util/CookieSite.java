package com.coara.browser2.util;

import java.util.Locale;

public final class CookieSite {
    private CookieSite() {
    }

    public static String siteOf(String host) {
        if (host == null) {
            return "";
        }
        String h = host.toLowerCase(Locale.US);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty() || h.indexOf(':') >= 0 || h.indexOf('.') < 0) {
            return h;
        }
        String[] labels = h.split("\\.");
        int n = labels.length;
        if (n <= 2) {
            return h;
        }
        boolean numeric = true;
        for (String l : labels) {
            if (l.isEmpty() || !Character.isDigit(l.charAt(0))) {
                numeric = false;
                break;
            }
        }
        if (numeric) {
            return h;
        }
        String last = labels[n - 1];
        String second = labels[n - 2];
        boolean compound = last.length() == 2 && (second.equals("co") || second.equals("com") || second.equals("ne")
                || second.equals("or") || second.equals("go") || second.equals("ac") || second.equals("ad")
                || second.equals("ed") || second.equals("gr") || second.equals("lg") || second.equals("net")
                || second.equals("org") || second.equals("gov") || second.equals("edu"));
        int keep = compound ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - keep; i < n; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(labels[i]);
        }
        return sb.toString();
    }
}
