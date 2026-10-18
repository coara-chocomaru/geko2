package com.coara.browser2.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TabGroupStore {

    public interface Sink {
        void write(String serialized);
    }

    public static final class Group {
        public final int id;
        public String name;
        public int color;

        Group(int id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    public static final int[] COLORS = {
        0xFF5F6368, 0xFF1A73E8, 0xFFD93025, 0xFFF9AB00, 0xFF188038, 0xFFD01884, 0xFF9334E6, 0xFF129EAF, 0xFFE8710A
    };
    public static final String[] COLOR_NAMES = {
        "グレー", "ブルー", "レッド", "イエロー", "グリーン", "ピンク", "パープル", "シアン", "オレンジ"
    };

    private final Map<Integer, Group> groups = new LinkedHashMap<>();
    private final Map<Integer, Integer> membership = new HashMap<>();
    private final Sink sink;
    private int nextId = 1;

    public TabGroupStore(String serialized, Sink sink) {
        this.sink = sink;
        parse(serialized);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void parse(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (String line : data.split("\n")) {
            String[] p = line.split("\t", -1);
            try {
                if (p.length >= 4 && p[0].equals("g")) {
                    int id = Integer.parseInt(p[1]);
                    int color = Integer.parseInt(p[2]);
                    groups.put(id, new Group(id, p[3], color));
                    if (id >= nextId) {
                        nextId = id + 1;
                    }
                } else if (p.length >= 3 && p[0].equals("m")) {
                    membership.put(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Iterator<Map.Entry<Integer, Integer>> it = membership.entrySet().iterator();
        while (it.hasNext()) {
            if (!groups.containsKey(it.next().getValue())) {
                it.remove();
            }
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Group g : groups.values()) {
            sb.append("g\t").append(g.id).append('\t').append(g.color).append('\t').append(g.name).append('\n');
        }
        for (Map.Entry<Integer, Integer> e : membership.entrySet()) {
            sb.append("m\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private void persist() {
        if (sink != null) {
            sink.write(serialize());
        }
    }

    public List<Group> groups() {
        return new ArrayList<>(groups.values());
    }

    public Group get(int groupId) {
        return groups.get(groupId);
    }

    public int groupOf(int tabId) {
        Integer g = membership.get(tabId);
        return g == null ? -1 : g;
    }

    public Group groupFor(int tabId) {
        Integer g = membership.get(tabId);
        return g == null ? null : groups.get(g);
    }

    public int count(int groupId) {
        int n = 0;
        for (Integer g : membership.values()) {
            if (g == groupId) {
                n++;
            }
        }
        return n;
    }

    public List<Integer> tabsOf(int groupId) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : membership.entrySet()) {
            if (e.getValue() == groupId) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private static String letters(int n) {
        StringBuilder sb = new StringBuilder();
        int v = n;
        while (v > 0) {
            v--;
            sb.insert(0, (char) ('A' + (v % 26)));
            v /= 26;
        }
        return sb.toString();
    }

    public String suggestName() {
        int n = 1;
        while (true) {
            String candidate = "グループ " + letters(n);
            boolean used = false;
            for (Group g : groups.values()) {
                if (g.name.equals(candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
            n++;
        }
    }

    public Group create(String name) {
        String n = clean(name);
        if (n.isEmpty()) {
            n = suggestName();
        }
        int id = nextId++;
        Group g = new Group(id, n, COLORS[(groups.size() + 1) % COLORS.length]);
        groups.put(id, g);
        persist();
        return g;
    }

    public void assign(int tabId, int groupId) {
        if (tabId < 0 || !groups.containsKey(groupId)) {
            return;
        }
        Integer old = membership.put(tabId, groupId);
        if (old == null || old != groupId) {
            if (old != null) {
                dropIfEmpty(old);
            }
            persist();
        }
    }

    public void remove(int tabId) {
        Integer old = membership.remove(tabId);
        if (old != null) {
            dropIfEmpty(old);
            persist();
        }
    }

    public void rename(int groupId, String name) {
        Group g = groups.get(groupId);
        String n = clean(name);
        if (g != null && !n.isEmpty() && !n.equals(g.name)) {
            g.name = n;
            persist();
        }
    }

    public void setColor(int groupId, int color) {
        Group g = groups.get(groupId);
        if (g != null && g.color != color) {
            g.color = color;
            persist();
        }
    }

    public void dissolve(int groupId) {
        if (groups.remove(groupId) == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> it = membership.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() == groupId) {
                it.remove();
            }
        }
        persist();
    }

    private void dropIfEmpty(int groupId) {
        if (count(groupId) == 0) {
            groups.remove(groupId);
        }
    }

    public void prune(Set<Integer> liveTabIds) {
        boolean changed = false;
        Iterator<Map.Entry<Integer, Integer>> it = membership.entrySet().iterator();
        while (it.hasNext()) {
            if (!liveTabIds.contains(it.next().getKey())) {
                it.remove();
                changed = true;
            }
        }
        Set<Integer> used = new HashSet<>(membership.values());
        Iterator<Integer> gi = groups.keySet().iterator();
        while (gi.hasNext()) {
            if (!used.contains(gi.next())) {
                gi.remove();
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }
}
