package com.chrober.lmsmaterial.nowplaying;

/** API-22-safe policy for WebView visual readiness on Android API 23+. */
final class WebViewReadinessPolicy {
    private static final int VISUAL_STATE_API = 23;

    static boolean supportsVisualState(int sdk) {
        return sdk >= VISUAL_STATE_API;
    }

    private WebViewReadinessPolicy() { }
}
