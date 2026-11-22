package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class TimedDisplayOptionsTest {
    private static String androidJsonResult(String status, long micros) {
        JSONArray pair = new JSONArray();
        pair.put(status);
        pair.put(micros);
        return JSONObject.quote(pair.toString());
    }

    @Test public void wrapperRunsOriginalScriptExactlyOnceAndTimesExecution() {
        String original = "(function(){return 'ready';})()";
        String wrapped = TimedDisplayOptions.wrap(original);
        assertTrue(wrapped.contains("var state=" + original + ";"));
        assertTrue(wrapped.contains("Math.round(elapsed*1000)"));
        assertTrue(wrapped.contains("JSON.stringify([state,micros])"));
        assertEquals(wrapped.indexOf(original), wrapped.lastIndexOf(original));
    }

    @Test public void parsesReadyReloadAndUnknownWithoutRawDataLeak() {
        TimedDisplayOptions.Result ready = TimedDisplayOptions.parse(
                androidJsonResult("ready", 1532));
        assertEquals(TimedDisplayOptions.Status.READY, ready.status);
        assertEquals(1532, ready.scriptMicros);

        TimedDisplayOptions.Result reload = TimedDisplayOptions.parse(
                androidJsonResult("reload", 0));
        assertEquals(TimedDisplayOptions.Status.RELOAD, reload.status);
        assertEquals(0, reload.scriptMicros);

        TimedDisplayOptions.Result missing = TimedDisplayOptions.parse(
                androidJsonResult("not-ready", 25));
        assertEquals(TimedDisplayOptions.Status.MISSING, missing.status);
    }

    @Test public void malformedOrOversizedValuesCannotEscapeToLogs() {
        assertEquals(TimedDisplayOptions.Status.MISSING,
                TimedDisplayOptions.parse("\"https://private.invalid\"").status);
        assertEquals(-1, TimedDisplayOptions.parse(null).scriptMicros);
        assertEquals(-1, TimedDisplayOptions.parse(
                androidJsonResult("ready", 120000001)).scriptMicros);
        JSONArray invalid = new JSONArray();
        invalid.put("ready");
        invalid.put("1.1");
        assertEquals(-1, TimedDisplayOptions.parse(
                JSONObject.quote(invalid.toString())).scriptMicros);
    }
}
