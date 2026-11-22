package com.chrober.lmsmaterial.nowplaying;

import org.junit.Test;

import static org.junit.Assert.*;

/** Precise native milestones, without Android dependencies or network data. */
public final class WebViewNavigationTraceTest {
    @Test public void separatesStubRedirectFromFinalNavigationAndOptionReload() {
        WebViewNavigationTrace trace = new WebViewNavigationTrace();
        trace.navigationStarted(100);
        trace.pageStarted(105, true);
        trace.pageFinished(185, true);
        trace.pageStarted(195, false);
        trace.pageFinished(700, false);
        trace.optionsEvaluationStarted(710);
        trace.optionsEvaluationFinished(740, true);
        trace.pageStarted(800, false);
        trace.pageFinished(1300, false);
        trace.optionsEvaluationStarted(1310);
        trace.optionsEvaluationFinished(1340, false);
        String result = trace.report("ready", 1450);
        assertTrue(result.contains("page_count=3"));
        assertTrue(result.contains("first_page_start_ms=5"));
        assertTrue(result.contains("np_stub_ms=80"));
        assertTrue(result.contains("final_page_start_ms=700"));
        assertTrue(result.contains("final_page_load_ms=500"));
        assertTrue(result.contains("final_load_to_ready_ms=150"));
        assertTrue(result.contains("options_attempts=2"));
        assertTrue(result.contains("options_last_eval_ms=30"));
        assertTrue(result.contains("options_reload_count=1"));
        assertFalse(result.contains("192.168."));
    }

    @Test public void incompleteAndErrorTraceUsesMissingValueNotInventedDurations() {
        WebViewNavigationTrace trace = new WebViewNavigationTrace();
        trace.navigationStarted(20);
        trace.pageStarted(25, true);
        String result = trace.report("error", 90);
        assertTrue(result.contains("phase=native outcome=error"));
        assertTrue(result.contains("final_page_start_ms=-1"));
        assertTrue(result.contains("np_stub_ms=-1"));
        assertTrue(result.contains("final_page_load_ms=-1"));
        assertTrue(result.contains("options_attempts=0"));
    }
}
