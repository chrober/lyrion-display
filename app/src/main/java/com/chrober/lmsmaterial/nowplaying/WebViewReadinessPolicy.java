package com.chrober.lmsmaterial.nowplaying;

/** API-22-safe policy for optional WebView features introduced in API 23. */
final class WebViewReadinessPolicy {
    private static final int VISUAL_STATE_API = 23;

    static boolean supportsVisualState(int sdk) {
        return sdk >= VISUAL_STATE_API;
    }

    static boolean shouldPreRaster(int sdk, boolean explicitlyEnabled) {
        // Extra offscreen tiles can raise memory usage on older Fire TV Sticks.
        return explicitlyEnabled && supportsVisualState(sdk);
    }

    private WebViewReadinessPolicy() { }
}
