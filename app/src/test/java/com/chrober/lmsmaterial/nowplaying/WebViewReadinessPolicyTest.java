package com.chrober.lmsmaterial.nowplaying;

import org.junit.Test;

import static org.junit.Assert.*;

public final class WebViewReadinessPolicyTest {
    @Test public void androidFiveOneUsesLegacyJsReadyPath() {
        assertFalse(WebViewReadinessPolicy.supportsVisualState(22));
        assertFalse(WebViewReadinessPolicy.shouldPreRaster(22, true));
    }

    @Test public void fireOsSixCanUseVisualStateButPrerasterIsOptIn() {
        assertTrue(WebViewReadinessPolicy.supportsVisualState(25));
        assertFalse(WebViewReadinessPolicy.shouldPreRaster(25, false));
        assertTrue(WebViewReadinessPolicy.shouldPreRaster(25, true));
    }

    @Test public void api23IntroducesBothOptions() {
        assertFalse(WebViewReadinessPolicy.supportsVisualState(22));
        assertTrue(WebViewReadinessPolicy.supportsVisualState(23));
        assertFalse(WebViewReadinessPolicy.shouldPreRaster(23, false));
        assertTrue(WebViewReadinessPolicy.shouldPreRaster(23, true));
    }
}
