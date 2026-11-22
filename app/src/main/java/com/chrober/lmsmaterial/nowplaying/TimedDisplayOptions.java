package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;

/**
 * Times the *synchronous* execution of our existing display-options script
 * inside the WebView renderer. The callback's wall time can additionally
 * include renderer queueing, IPC and main-thread delivery.
 *
 * The original script is evaluated exactly once, and its state transitions,
 * including location.reload(), are preserved. All callback data is parsed
 * into a fixed status and bounded integer microseconds, never logged raw.
 */
final class TimedDisplayOptions {
    enum Status { READY, RELOAD, MISSING }

    static final class Result {
        final Status status;
        final long scriptMicros;
        Result(Status status, long scriptMicros) {
            this.status = status;
            this.scriptMicros = scriptMicros;
        }
    }

    static String wrap(String existingScript) {
        return "(function(){" +
                "var p=window.performance;" +
                "var started=(p&&typeof p.now==='function')?p.now():-1;" +
                "var state=" + existingScript + ";" +
                "var micros=-1;" +
                "if(started>=0){" +
                    "var elapsed=p.now()-started;" +
                    "if(isFinite(elapsed)&&elapsed>=0)" +
                        "micros=Math.round(elapsed*1000);" +
                "}" +
                "return JSON.stringify([state,micros]);" +
                "})()";
    }

    static Result parse(String callbackJson) {
        if (callbackJson == null) return new Result(Status.MISSING, -1);
        try {
            // Android evaluateJavascript serializes the JavaScript string
            // again as a JSON string. Never trust arbitrary source content.
            String inner = new JSONArray("[" + callbackJson + "]").getString(0);
            JSONArray data = new JSONArray(inner);
            if (data.length() != 2) return new Result(Status.MISSING, -1);
            String state = data.getString(0);
            Status status;
            if ("ready".equals(state)) status = Status.READY;
            else if ("reload".equals(state)) status = Status.RELOAD;
            else status = Status.MISSING;
            Object raw = data.opt(1);
            if (!(raw instanceof Number)) return new Result(status, -1);
            double us = ((Number) raw).doubleValue();
            if (!Double.isFinite(us) || Math.floor(us) != us
                    || us < -1 || us > 120000000) return new Result(status, -1);
            return new Result(status, (long) us);
        } catch (Exception ignored) {
            return new Result(Status.MISSING, -1);
        }
    }

    private TimedDisplayOptions() { }
}
