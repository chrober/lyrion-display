package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;

/**
 * Passive WebView Performance API snapshot. The injected script never sends
 * requests, alters Material Skin, stores any preference or reads URLs/names.
 * It returns ONLY bounded numeric metrics, never user/server/resource data.
 *
 * Navigation Timing and Resource Timing describe the browser's view of
 * requests. They cannot by themselves separate Material Skin's Vue work from
 * the LMS player connection or prove that a WebSocket is connected.
 */
final class WebViewPagePerformance {
    private static final String[] FIELDS = {
            "nav_ttfb_ms", "nav_download_ms", "dom_interactive_ms",
            "dom_content_loaded_ms", "load_event_ms", "resource_count",
            "script_count", "script_max_ms", "style_count", "style_max_ms",
            "image_count", "image_max_ms", "data_count", "data_max_ms",
            "other_count", "other_max_ms", "resources_last_end_ms",
            "app_present", "np_controls_present", "images_pending"
    };

    /** ES5-compatible for older Android system WebViews, including API 22. */
    static String javascript() {
        return "(function(){try{" +
                "var p=window.performance;" +
                "if(!p||!p.timing)return JSON.stringify([]);" +
                "var t=p.timing,n=t.navigationStart;" +
                "function between(a,b){return a>0&&b>=a?Math.round(b-a):-1;}" +
                "function fromNav(a){return n>0&&a>=n?Math.round(a-n):-1;}" +
                "var es=(typeof p.getEntriesByType==='function')?" +
                    "p.getEntriesByType('resource'):[];" +
                "var counts=[0,0,0,0,0],max=[0,0,0,0,0],last=-1;" +
                "for(var i=0;i<es.length;i++){" +
                    "var e=es[i],k=e.initiatorType||'';" +
                    "var bucket=k==='script'?0:" +
                        "(k==='link'||k==='css')?1:" +
                        "k==='img'?2:" +
                        "(k==='fetch'||k==='xmlhttprequest')?3:4;" +
                    "counts[bucket]++;" +
                    "var duration=e.duration;" +
                    "if(typeof duration==='number'&&isFinite(duration))" +
                        "max[bucket]=Math.max(max[bucket],Math.round(duration));" +
                    "if(typeof e.responseEnd==='number'&&isFinite(e.responseEnd))" +
                        "last=Math.max(last,Math.round(e.responseEnd));" +
                "}" +
                "var pending=0,images=document.images||[];" +
                "for(var j=0;j<images.length;j++)if(!images[j].complete)pending++;" +
                "return JSON.stringify([" +
                    "between(t.requestStart,t.responseStart)," +
                    "between(t.responseStart,t.responseEnd)," +
                    "fromNav(t.domInteractive)," +
                    "fromNav(t.domContentLoadedEventEnd)," +
                    "fromNav(t.loadEventEnd)," +
                    "es.length," +
                    "counts[0],max[0],counts[1],max[1]," +
                    "counts[2],max[2],counts[3],max[3]," +
                    "counts[4],max[4],last," +
                    "(document.getElementById('app')?1:0)," +
                    "(document.querySelector('.np-controls')?1:0),pending" +
                "]);" +
            "}catch(e){return JSON.stringify([]);}})()";
    }

    static String report(String rawCallback) {
        if (rawCallback == null) return "phase=page_perf state=unavailable";
        try {
            // evaluateJavascript encodes a JS string as a JSON string.
            String encoded = new JSONArray("[" + rawCallback + "]").getString(0);
            JSONArray values = new JSONArray(encoded);
            if (values.length() != FIELDS.length)
                return "phase=page_perf state=unavailable";
            StringBuilder report = new StringBuilder("phase=page_perf state=ok");
            for (int i = 0; i < FIELDS.length; i++) {
                // Do not pass arbitrary content from a WebView into logcat.
                // Reject NaN/Infinity, fractions, enormous values, or strings.
                Object raw = values.opt(i);
                if (!(raw instanceof Number))
                    return "phase=page_perf state=unavailable";
                double n = ((Number) raw).doubleValue();
                if (!Double.isFinite(n) || n < -1 || n > 10000000 || Math.floor(n) != n)
                    return "phase=page_perf state=unavailable";
                report.append(' ').append(FIELDS[i]).append('=').append((long) n);
            }
            return report.toString();
        } catch (Exception invalid) {
            return "phase=page_perf state=unavailable";
        }
    }

    private WebViewPagePerformance() { }
}
