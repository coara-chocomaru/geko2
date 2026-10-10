package com.coara.browser2.gecko;

import android.graphics.Bitmap;

public interface BrowserClient {
    final class ContextHit {
        public static final int TYPE_NONE = 0;
        public static final int TYPE_IMAGE = 1;
        public static final int TYPE_VIDEO = 2;
        public static final int TYPE_AUDIO = 3;

        public final int type;
        public final String linkUri;
        public final String srcUri;
        public final String linkText;
        public final String title;

        public ContextHit(int type, String linkUri, String srcUri, String linkText, String title) {
            this.type = type;
            this.linkUri = linkUri;
            this.srcUri = srcUri;
            this.linkText = linkText;
            this.title = title;
        }

        public boolean hasLink() {
            return linkUri != null && !linkUri.isEmpty();
        }

        public boolean hasSource() {
            return srcUri != null && !srcUri.isEmpty();
        }
    }

    final class LoadFailure {
        public final String url;
        public final int category;
        public final int code;

        public LoadFailure(String url, int category, int code) {
            this.url = url;
            this.category = category;
            this.code = code;
        }
    }

    default boolean shouldOverrideUrlLoading(BrowserView view, String url, boolean mainFrame, boolean hasGesture, boolean redirect) {
        return false;
    }

    default void onPageStarted(BrowserView view, String url) {
    }

    default void onPageCommitVisible(BrowserView view, String url) {
    }

    default void onPageFinished(BrowserView view, String url) {
    }

    default void onProgressChanged(BrowserView view, int progress) {
    }

    default void onReceivedTitle(BrowserView view, String title) {
    }

    default void onReceivedIcon(BrowserView view, Bitmap icon) {
    }

    default void onUrlChanged(BrowserView view, String url, boolean sameDocument) {
    }

    default BrowserView onCreateWindow(BrowserView view, String url) {
        return null;
    }

    default void onCloseWindow(BrowserView view) {
    }

    default void onFullScreenChanged(BrowserView view, boolean fullScreen) {
    }

    default boolean onContextMenu(BrowserView view, ContextHit hit) {
        return false;
    }

    default void onDownloadRequested(BrowserView view, DownloadRequest request) {
    }

    default void onLoadFailed(BrowserView view, LoadFailure failure) {
    }

    default void onRenderProcessGone(BrowserView view, boolean crashed) {
    }

    default void onScrollChanged(BrowserView view, int x, int y) {
    }

    default void onHistoryChanged(BrowserView view, NavHistory history) {
    }

    default void onSessionStateChanged(BrowserView view) {
    }

    default void onSecurityChanged(BrowserView view, boolean secure) {
    }
}
