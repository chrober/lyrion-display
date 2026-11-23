package com.chrober.lmsmaterial.nowplaying;

import android.annotation.TargetApi;
import android.webkit.WebView;

/**
 * Isolate Android 6.0 (API 23) WebView symbols from the API 22 codepath.
 * ALL entry points must be guarded by SDK_INT >= 23 at their call sites.
 */
@TargetApi(23)
final class WebViewApi23 {
    interface OnVisualReady {
        void onReady(long requestId);
    }

    static void requestVisualState(WebView view, long requestId, OnVisualReady callback) {
        view.postVisualStateCallback(requestId, new WebView.VisualStateCallback() {
            @Override public void onComplete(long id) {
                callback.onReady(id);
            }
        });
    }

    private WebViewApi23() { }
}
