package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;

/**
 * Read-only DOM snapshot triggered as soon as WebView commits the final page.
 * The callback can arrive later than the actual commit (possibly after
 * onPageFinished); the native trace logs that timing separately.
 */
final class WebViewCommitProbe {
    static final class Result {
        final int readyState; // 0 loading; 1 interactive; 2 complete; -1 unknown
        final int appPresent;
        final int controlsPresent;

        Result(int readyState, int appPresent, int controlsPresent) {
            this.readyState = readyState;
            this.appPresent = appPresent;
            this.controlsPresent = controlsPresent;
        }
    }

    static String javascript() {
        return "(function(){try{" +
                "var s=document.readyState;" +
                "var state=s==='loading'?0:s==='interactive'?1:s==='complete'?2:-1;" +
                "return JSON.stringify([state," +
                    "document.getElementById('app')?1:0," +
                    "document.querySelector('.np-controls,.np-controls-wide')?1:0]);" +
                "}catch(e){return JSON.stringify([-1,-1,-1]);}})()";
    }

    static Result parse(String callbackJson) {
        Result unknown = new Result(-1, -1, -1);
        if (callbackJson == null) return unknown;
        try {
            String inner = new JSONArray("[" + callbackJson + "]").getString(0);
            JSONArray data = new JSONArray(inner);
            if (data.length() != 3) return unknown;
            int[] numbers = new int[3];
            for (int i = 0; i < numbers.length; i++) {
                Object raw = data.opt(i);
                if (!(raw instanceof Number)) return unknown;
                double n = ((Number) raw).doubleValue();
                if (!Double.isFinite(n) || Math.floor(n) != n || n < -1 || n > 2)
                    return unknown;
                numbers[i] = (int) n;
            }
            if (numbers[1] > 1 || numbers[2] > 1) return unknown;
            return new Result(numbers[0], numbers[1], numbers[2]);
        } catch (Exception ignored) {
            return unknown;
        }
    }

    private WebViewCommitProbe() { }
}
