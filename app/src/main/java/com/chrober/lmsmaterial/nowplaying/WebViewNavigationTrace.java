package com.chrober.lmsmaterial.nowplaying;

/**
 * In-memory, monotonic milestones for a guarded cold connection.
 *
 * Never retains URLs, player identifiers, network addresses, resource names,
 * or any Lyrion metadata. The initial np-only stub can redirect/reload before
 * the actual Material Skin page finishes.
 */
final class WebViewNavigationTrace {
    private long navigationStartedMs = -1;
    private long firstPageStartedMs = -1;
    private long stubFinishedMs = -1;
    private long finalPageStartedMs = -1;
    private long finalPageFinishedMs = -1;
    private long lastOptionsStartedMs = -1;
    private long lastOptionsDurationMs = -1;
    private int pagesStarted;
    private int optionsAttempts;
    private int optionReloads;

    void navigationStarted(long ms) { navigationStartedMs = ms; }

    void pageStarted(long ms, boolean stub) {
        pagesStarted++;
        if (firstPageStartedMs < 0) firstPageStartedMs = ms;
        if (!stub) {
            finalPageStartedMs = ms;
            finalPageFinishedMs = -1;
        }
    }

    void pageFinished(long ms, boolean stub) {
        if (stub) {
            stubFinishedMs = ms;
        } else {
            finalPageFinishedMs = ms;
        }
    }

    void optionsEvaluationStarted(long ms) {
        optionsAttempts++;
        lastOptionsStartedMs = ms;
    }

    void optionsEvaluationFinished(long ms, boolean requestedReload) {
        lastOptionsDurationMs = elapsed(lastOptionsStartedMs, ms);
        if (requestedReload) optionReloads++;
    }

    String report(String outcome, long now) {
        return "phase=native outcome=" + outcome
                + " page_count=" + pagesStarted
                + " first_page_start_ms=" + elapsed(navigationStartedMs, firstPageStartedMs)
                + " np_stub_ms=" + elapsed(firstPageStartedMs, stubFinishedMs)
                + " final_page_start_ms=" + elapsed(navigationStartedMs, finalPageStartedMs)
                + " final_page_load_ms=" + elapsed(finalPageStartedMs, finalPageFinishedMs)
                + " final_load_to_ready_ms=" + elapsed(finalPageFinishedMs, now)
                + " options_attempts=" + optionsAttempts
                + " options_last_eval_ms=" + lastOptionsDurationMs
                + " options_reload_count=" + optionReloads;
    }

    private static long elapsed(long first, long last) {
        return first >= 0 && last >= first ? last - first : -1;
    }
}
