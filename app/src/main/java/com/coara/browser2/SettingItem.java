package com.coara.browser2;

public class SettingItem {
    private String title;
    private String description;
    private Runnable action;

    public SettingItem(String title, String description, Runnable action) {
        this.title = title;
        this.description = description;
        this.action = action;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Runnable getAction() {
        return action;
    }
}
