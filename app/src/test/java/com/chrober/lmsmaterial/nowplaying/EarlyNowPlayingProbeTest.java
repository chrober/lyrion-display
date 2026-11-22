package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class EarlyNowPlayingProbeTest {
    private String encode(int app, int controls, int vue, int visible) {
        return JSONObject.quote(new JSONArray().put(app).put(controls)
                .put(vue).put(visible).toString());
    }

    @Test public void requiresActualMountedVisibleMaterialControls() {
        assertTrue(EarlyNowPlayingProbe.parse(encode(1, 1, 1, 1)).qualified());
        assertFalse(EarlyNowPlayingProbe.parse(encode(1, 0, 1, 1)).qualified());
        assertFalse(EarlyNowPlayingProbe.parse(encode(1, 1, 0, 1)).qualified());
        assertFalse(EarlyNowPlayingProbe.parse(encode(1, 1, 1, 0)).qualified());
        assertFalse(EarlyNowPlayingProbe.parse(encode(0, 1, 1, 1)).qualified());
        assertEquals(2, EarlyNowPlayingProbe.REQUIRED_CONSECUTIVE_PASSES);
        assertTrue(EarlyNowPlayingProbe.MAX_ATTEMPTS > 2);
    }

    @Test public void isOffByDefaultAndUnsupportedOnAndroidApi22() {
        assertFalse(EarlyNowPlayingProbe.enabled(22, true));
        assertFalse(EarlyNowPlayingProbe.enabled(25, false));
        assertTrue(EarlyNowPlayingProbe.enabled(25, true));
        assertTrue(EarlyNowPlayingProbe.enabled(29, true));
    }

    @Test public void rejectsUntrustedJsonWithoutEchoingData() {
        assertFalse(EarlyNowPlayingProbe.parse(null).qualified());
        assertFalse(EarlyNowPlayingProbe.parse("\"http://192.168.1.45/secret\"").qualified());
        assertFalse(EarlyNowPlayingProbe.parse(encode(1, 1, 2, 1)).qualified());
        assertFalse(EarlyNowPlayingProbe.parse(JSONObject.quote("[1,1,1]")).qualified());
        JSONArray injection = new JSONArray().put(1).put(1).put(1)
                .put("private-track-name");
        assertFalse(EarlyNowPlayingProbe.parse(JSONObject.quote(injection.toString())).qualified());
    }

    @Test public void onlyReadsDomStateAndDoesNotControlPlayback() {
        String script = EarlyNowPlayingProbe.javascript();
        assertTrue(script.contains("document.querySelector"));
        assertTrue(script.contains("getClientRects"));
        assertFalse(script.contains("fetch("));
        assertFalse(script.contains("XMLHttpRequest"));
        assertFalse(script.contains("localStorage.setItem"));
        assertFalse(script.contains("location.reload"));
        assertFalse(script.contains("http"));
    }
}
