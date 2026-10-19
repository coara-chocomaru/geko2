package com.coara.browser2.gecko;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoSession;

final class TabContentBlocking implements ContentBlocking.Delegate {
    private final BrowserView view;

    TabContentBlocking(BrowserView view) {
        this.view = view;
    }

    @Override
    public void onContentBlocked(@NonNull GeckoSession session, @NonNull ContentBlocking.BlockEvent event) {
        if (!event.isBlocking()
                || event.getCookieBehaviorCategory() == ContentBlocking.CookieBehavior.ACCEPT_ALL) {
            return;
        }
        Context c = view.getContext();
        int depth = 0;
        while (c != null && depth++ < 8) {
            if (c instanceof BrowserHost) {
                ((BrowserHost) c).onThirdPartyCookieBlocked(view, event.uri);
                return;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                return;
            }
        }
    }
}
