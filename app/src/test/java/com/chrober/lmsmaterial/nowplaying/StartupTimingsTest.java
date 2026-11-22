package com.chrober.lmsmaterial.nowplaying;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Host JVM coverage for monotonic startup phase metrics, without device IO. */
public final class StartupTimingsTest {
    @Test public void reportsOverlappedProbeAndWebViewPreparation() {
        StartupTimings timing = new StartupTimings(1000);
        timing.probeStarted(1010);
        timing.webViewStarted(1012);
        timing.webViewPrepared(1072);
        timing.probeFinished(1250);
        timing.navigationStarted(1253);
        timing.domReady(1430);
        timing.visualReady(1510, "callback");
        assertEquals("outcome=ready total_ms=510 probe_ms=240 webview_prepare_ms=60"
                        + " post_probe_ms=260 navigation_to_ready_ms=257"
                        + " dom_to_visual_ms=80 visual_state=callback",
                timing.report("ready", 1510));
    }

    @Test public void reportsMissingNavigationOnProbeFailure() {
        StartupTimings timing = new StartupTimings(500);
        timing.probeStarted(510);
        timing.webViewStarted(515);
        timing.webViewPrepared(575);
        timing.probeFinished(900);
        String report = timing.report("error", 910);
        assertTrue(report.contains("outcome=error"));
        assertTrue(report.contains("total_ms=410"));
        assertTrue(report.contains("probe_ms=390"));
        assertTrue(report.contains("navigation_to_ready_ms=-1"));
        assertTrue(report.contains("dom_to_visual_ms=-1"));
        assertTrue(report.contains("visual_state=none"));
    }

    @Test public void reportsFallbackSeparatelyFromConfirmedVisualCallback() {
        StartupTimings timing = new StartupTimings(100);
        timing.navigationStarted(140);
        timing.domReady(210);
        timing.visualReady(1710, "timeout");
        String report = timing.report("ready", 1710);
        assertTrue(report.contains("dom_to_visual_ms=1500"));
        assertTrue(report.contains("visual_state=timeout"));
        assertTrue(report.contains("total_ms=1610"));
    }

    @Test public void incompleteOrInvalidStageDoesNotInventDuration() {
        StartupTimings timing = new StartupTimings(100);
        timing.probeStarted(140);
        timing.probeFinished(120);
        String report = timing.report("error", 150);
        assertTrue(report.contains("probe_ms=-1"));
        assertTrue(report.contains("webview_prepare_ms=-1"));
        assertFalse(report.contains("http://"));
    }
}
