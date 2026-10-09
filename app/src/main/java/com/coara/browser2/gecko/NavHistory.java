package com.coara.browser2.gecko;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NavHistory {
    public static final class Item {
        private final String url;
        private final String title;

        public Item(String url, String title) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }
    }

    private final List<Item> items;
    private final int currentIndex;

    public NavHistory(List<Item> items, int currentIndex) {
        this.items = items == null ? new ArrayList<Item>() : new ArrayList<Item>(items);
        if (this.items.isEmpty()) {
            this.currentIndex = -1;
        } else {
            this.currentIndex = Math.max(0, Math.min(currentIndex, this.items.size() - 1));
        }
    }

    public static NavHistory empty() {
        return new NavHistory(Collections.<Item>emptyList(), -1);
    }

    public int getSize() {
        return items.size();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Item getItemAtIndex(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    public Item getCurrentItem() {
        return getItemAtIndex(currentIndex);
    }
}
