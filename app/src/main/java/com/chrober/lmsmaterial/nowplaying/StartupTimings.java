package com.chrober.lmsmaterial.nowplaying;

/**
 * Monotonic, local-only startup timings. The native log deliberately contains
 * no server URL, IP address, player identifier or other personal information.
 *
 * All times are supplied by the caller (SystemClock.elapsedRealtime()) so
 * formatting and missing-stage behavior can be tested on the host JVM.
 */
final class StartupTimings {
    private final long startedMs;
    private final boolean offscreenPreRaster;
    private long probeStartedMs = -1;
    private long probeFinishedMs = -1;
    private long webViewStartedMs = -1;
    private long webViewPreparedMs = -1;
    private long navigationStartedMs = -1;
    private long domReadyMs = -1;
    private long visualReadyMs = -1;
    private String visualState = "none";

    StartupTimings(long startedMs) {
        this(startedMs, false);
    }

    StartupTimings(long startedMs, boolean offscreenPreRaster) {
        this.startedMs = startedMs;
        this.offscreenPreRaster = offscreenPreRaster;
    }

    void probeStarted(long nowMs) { probeStartedMs = nowMs; }
    void probeFinished(long nowMs) { probeFinishedMs = nowMs; }
    void webViewStarted(long nowMs) { webViewStartedMs = nowMs; }
    void webViewPrepared(long nowMs) { webViewPreparedMs = nowMs; }
    void navigationStarted(long nowMs) { navigationStartedMs = nowMs; }
    void domReady(long nowMs) { domReadyMs = nowMs; }
    void visualReady(long nowMs, String state) {
        visualReadyMs = nowMs;
        visualState = state;
    }

    String report(String outcome, long nowMs) {
        return "outcome=" + outcome
                + " total_ms=" + delta(startedMs, nowMs)
                + " probe_ms=" + delta(probeStartedMs, probeFinishedMs)
                + " webview_prepare_ms=" + delta(webViewStartedMs, webViewPreparedMs)
                + " post_probe_ms=" + delta(probeFinishedMs, nowMs)
                + " navigation_to_ready_ms=" + delta(navigationStartedMs, nowMs)
                + " dom_to_visual_ms=" + delta(domReadyMs, visualReadyMs)
                + " visual_state=" + visualState
                + " preraster=" + (offscreenPreRaster ? "on" : "off");
    }

    private static long delta(long first, long last) {
        return first >= 0 && last >= first ? last - first : -1;
    }
}
