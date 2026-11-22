package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** No identifiers or arbitrary WebView data must escape through logcat. */
public final class WebViewPagePerformanceTest {
    @Test public void sanitizesNumericMetricsAndNeverLogsResourceNames() {
        JSONArray values = new JSONArray();
        long[] input = {
                91, 18, 402, 455, 470, 37,
                6, 175, 3, 44, 17, 211,
                4, 103, 7, 54, 409, 1, 1, 0
        };
        for (long value : input) values.put(value);
        String report = WebViewPagePerformance.report(JSONObject.quote(values.toString()));
        assertTrue(report.contains("phase=page_perf state=ok"));
        assertTrue(report.contains("nav_ttfb_ms=91"));
        assertTrue(report.contains("script_count=6"));
        assertTrue(report.contains("resources_last_end_ms=409"));
        assertTrue(report.contains("np_controls_present=1"));
        assertFalse(report.contains("http"));
        assertFalse(report.contains("192.168."));
    }

    @Test public void rejectsMalformedPayloadWithoutEchoingIt() {
        assertEquals("phase=page_perf state=unavailable",
                WebViewPagePerformance.report(null));
        assertEquals("phase=page_perf state=unavailable",
                WebViewPagePerformance.report("\"http://private-network/secret\""));
        assertEquals("phase=page_perf state=unavailable",
                WebViewPagePerformance.report(JSONObject.quote("[1,2,3]")));
        JSONArray values = new JSONArray();
        for (int i = 0; i < 20; i++) values.put(i == 4 ? 1000000000L : 0);
        assertEquals("phase=page_perf state=unavailable",
                WebViewPagePerformance.report(JSONObject.quote(values.toString())));
    }

    @Test public void passiveScriptUsesTimingWithoutFetchingOrTransmitting() {
        String script = WebViewPagePerformance.javascript();
        assertTrue(script.contains("p.timing"));
        assertTrue(script.contains("getEntriesByType('resource')"));
        assertTrue(script.contains("document.getElementById('app')"));
        assertFalse(script.contains("fetch("));
        assertFalse(script.contains("XMLHttpRequest("));
        assertFalse(script.contains("http://"));
    }
}
