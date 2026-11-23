package com.chrober.lmsmaterial.nowplaying;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression coverage for the retained API 23 visual-state callback. */
public final class WebViewReadinessPolicyTest {
    @Test public void androidFiveOneKeepsLegacyJsReadyPath() {
        assertFalse(WebViewReadinessPolicy.supportsVisualState(22));
    }

    @Test public void api23AndFireOsSixSupportVisualState() {
        assertTrue(WebViewReadinessPolicy.supportsVisualState(23));
        assertTrue(WebViewReadinessPolicy.supportsVisualState(25));
        assertTrue(WebViewReadinessPolicy.supportsVisualState(29));
    }
}
