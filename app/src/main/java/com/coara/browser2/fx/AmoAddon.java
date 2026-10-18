package com.coara.browser2.fx;

public final class AmoAddon {
    public final String guid;
    public final String name;
    public final String summary;
    public final String iconUrl;
    public final String downloadUrl;
    public final String pageUrl;
    public final String author;
    public final double rating;
    public final int ratingCount;
    public final long users;
    public final boolean recommended;
    public final String[] permissions;

    public AmoAddon(String guid, String name, String summary, String iconUrl, String downloadUrl, String pageUrl,
                    String author, double rating, int ratingCount, long users, boolean recommended, String[] permissions) {
        this.guid = guid;
        this.name = name;
        this.summary = summary;
        this.iconUrl = iconUrl;
        this.downloadUrl = downloadUrl;
        this.pageUrl = pageUrl;
        this.author = author;
        this.rating = rating;
        this.ratingCount = ratingCount;
        this.users = users;
        this.recommended = recommended;
        this.permissions = permissions;
    }
}
