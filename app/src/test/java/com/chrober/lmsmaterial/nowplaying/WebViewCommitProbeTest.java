package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class WebViewCommitProbeTest {
    private static String encoded(int a, int b, int c) {
        return JSONObject.quote(new JSONArray().put(a).put(b).put(c).toString());
    }

    @Test public void parsesBoundedDomSnapshotWithoutEchoingDocumentData() {
        WebViewCommitProbe.Result result = WebViewCommitProbe.parse(encoded(1, 1, 0));
        assertEquals(1, result.readyState);
        assertEquals(1, result.appPresent);
        assertEquals(0, result.controlsPresent);
        assertTrue(WebViewCommitProbe.javascript().contains("document.readyState"));
        assertTrue(WebViewCommitProbe.javascript().contains("document.getElementById('app')"));
        assertFalse(WebViewCommitProbe.javascript().contains("location.href"));
        assertFalse(WebViewCommitProbe.javascript().contains("fetch("));
    }

    @Test public void rejectsMalformedOrImpossibleValues() {
        assertEquals(-1, WebViewCommitProbe.parse(null).readyState);
        assertEquals(-1, WebViewCommitProbe.parse(encoded(3, 1, 0)).readyState);
        assertEquals(-1, WebViewCommitProbe.parse(encoded(1, 2, 0)).appPresent);
        assertEquals(-1, WebViewCommitProbe.parse("\"secret URI\"").controlsPresent);
    }
}
