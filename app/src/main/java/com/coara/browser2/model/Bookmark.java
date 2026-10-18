package com.coara.browser2.model;

public final class Bookmark {
    private final String title;
    private final String url;
    private final String folder;

    public Bookmark(String title, String url) {
        this(title, url, "");
    }

    public Bookmark(String title, String url, String folder) {
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.folder = folder == null ? "" : folder.trim();
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getFolder() {
        return folder;
    }

    public Bookmark with(String newTitle, String newUrl, String newFolder) {
        return new Bookmark(newTitle, newUrl, newFolder);
    }
}
