package com.coara.browser2.gecko;

import org.mozilla.geckoview.WebResponse;

import java.io.InputStream;
import java.util.Map;

public final class DownloadRequest {
    public final String url;
    public final String fileName;
    public final String mimeType;
    public final long contentLength;
    public final String contentDisposition;
    public final String userAgent;
    public final String referrer;
    public final Map<String, String> headers;
    private final WebResponse response;

    DownloadRequest(String url, String fileName, String mimeType, long contentLength, String contentDisposition,
                    String userAgent, String referrer, Map<String, String> headers, WebResponse response) {
        this.url = url;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.contentLength = contentLength;
        this.contentDisposition = contentDisposition;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.headers = headers;
        this.response = response;
    }

    public boolean hasStream() {
        return response != null && response.body != null;
    }

    public InputStream openStream() {
        return response == null ? null : response.body;
    }

    public void setReadTimeoutMillis(long timeoutMillis) {
        if (response != null) {
            response.setReadTimeoutMillis(timeoutMillis);
        }
    }

    public static DownloadRequest forUrl(String url, String fileName, String mimeType, long contentLength,
                                         String contentDisposition, String userAgent, String referrer) {
        return new DownloadRequest(url, fileName, mimeType, contentLength, contentDisposition, userAgent, referrer,
                java.util.Collections.<String, String>emptyMap(), null);
    }
}
