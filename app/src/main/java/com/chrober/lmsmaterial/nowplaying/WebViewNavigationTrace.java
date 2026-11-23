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
    private long finalPageCommitMs = -1;
    private long finalPageFirstOptionsMs = -1;
    private long commitProbeCallbackMs = -1;
    private int commitProbeReadyState = -1;
    private int commitProbeAppPresent = -1;
    private int commitProbeControlsPresent = -1;
    private int commitProbeAfterFinish = -1;
    private long lastOptionsStartedMs = -1;
    private long lastOptionsDurationMs = -1;
    private long lastOptionsScriptMicros = -1;
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
            finalPageCommitMs = -1;
            finalPageFirstOptionsMs = -1;
            commitProbeCallbackMs = -1;
            commitProbeReadyState = -1;
            commitProbeAppPresent = -1;
            commitProbeControlsPresent = -1;
            commitProbeAfterFinish = -1;
        }
    }

    void pageFinished(long ms, boolean stub) {
        if (stub) {
            stubFinishedMs = ms;
        } else {
            finalPageFinishedMs = ms;
        }
    }

    void pageCommitted(long ms, boolean stub) {
        if (!stub) finalPageCommitMs = ms;
    }

    void commitProbeCompleted(long ms, WebViewCommitProbe.Result data) {
        if (finalPageCommitMs < 0 || data == null) return;
        commitProbeCallbackMs = ms;
        commitProbeReadyState = data.readyState;
        commitProbeAppPresent = data.appPresent;
        commitProbeControlsPresent = data.controlsPresent;
        commitProbeAfterFinish = finalPageFinishedMs >= 0 ? 1 : 0;
    }

    int currentPageCount() { return pagesStarted; }

    void optionsEvaluationStarted(long ms) {
        optionsAttempts++;
        if (finalPageFirstOptionsMs < 0) finalPageFirstOptionsMs = ms;
        lastOptionsStartedMs = ms;
    }

    void optionsEvaluationFinished(long ms, boolean requestedReload) {
        optionsEvaluationFinished(ms, requestedReload, -1);
    }

    void optionsEvaluationFinished(long ms, boolean requestedReload, long scriptMicros) {
        lastOptionsDurationMs = elapsed(lastOptionsStartedMs, ms);
        lastOptionsScriptMicros = scriptMicros;
        if (requestedReload) optionReloads++;
    }

    String report(String outcome, long now) {
        return "phase=native outcome=" + outcome
                + " page_count=" + pagesStarted
                + " first_page_start_ms=" + elapsed(navigationStartedMs, firstPageStartedMs)
                + " np_stub_ms=" + elapsed(firstPageStartedMs, stubFinishedMs)
                + " final_page_start_ms=" + elapsed(navigationStartedMs, finalPageStartedMs)
                + " final_page_load_ms=" + elapsed(finalPageStartedMs, finalPageFinishedMs)
                + " final_commit_ms=" + elapsed(navigationStartedMs, finalPageCommitMs)
                + " commit_to_finish_ms=" + elapsed(finalPageCommitMs, finalPageFinishedMs)
                + " commit_to_first_options_ms=" + elapsed(finalPageCommitMs,
                        finalPageFirstOptionsMs)
                + " commit_probe_callback_ms=" + elapsed(finalPageCommitMs,
                        commitProbeCallbackMs)
                + " commit_probe_after_finish=" + commitProbeAfterFinish
                + " commit_doc_state=" + commitProbeReadyState
                + " commit_app_present=" + commitProbeAppPresent
                + " commit_controls_present=" + commitProbeControlsPresent
                + " final_load_to_ready_ms=" + elapsed(finalPageFinishedMs, now)
                + " options_attempts=" + optionsAttempts
                + " options_last_eval_ms=" + lastOptionsDurationMs
                + " options_js_exec_us=" + lastOptionsScriptMicros
                + " options_callback_gap_ms=" + callbackGapMs()
                + " options_reload_count=" + optionReloads;
    }

    private long callbackGapMs() {
        if (lastOptionsDurationMs < 0 || lastOptionsScriptMicros < 0) return -1;
        return Math.max(0L, Math.round(lastOptionsDurationMs
                - lastOptionsScriptMicros / 1000.0));
    }

    private static long elapsed(long first, long last) {
        return first >= 0 && last >= first ? last - first : -1;
    }
}
