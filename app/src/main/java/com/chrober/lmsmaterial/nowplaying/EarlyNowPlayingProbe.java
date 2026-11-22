package com.chrober.lmsmaterial.nowplaying;

import org.json.JSONArray;

/**
 * Passive, bounded, URL-free test for whether Material's Now Playing controls
 * are mounted and occupy layout space. Merely having #app is NOT sufficient:
 * the initial np-only redirect and a still-loading Vue application both have
 * root elements long before any Now Playing UI can be displayed.
 *
 * Only used by an opt-in experiment; it does not mutate Material Skin, read
 * track/player data, make requests or install a JavaScript bridge.
 */
final class EarlyNowPlayingProbe {
    static final int MAX_ATTEMPTS = 8;
    static final long RETRY_DELAY_MS = 160L;
    static final int REQUIRED_CONSECUTIVE_PASSES = 2;

    static final class Result {
        final boolean app;
        final boolean controls;
        final boolean vue;
        final boolean visible;
        Result(boolean app, boolean controls, boolean vue, boolean visible) {
            this.app = app;
            this.controls = controls;
            this.vue = vue;
            this.visible = visible;
        }

        boolean qualified() { return app && controls && vue && visible; }
    }

    static String javascript() {
        return "(function(){try{" +
                "var app=document.getElementById('app');" +
                "var ctl=document.querySelector('.np-controls,.np-controls-wide');" +
                "var vm=app,vue=false,tries=0;" +
                "if(ctl)vm=ctl;" +
                "while(vm&&tries++<14){if(vm.__vue__){vue=true;break;}" +
                    "vm=vm.parentElement;}" +
                "var visible=false;" +
                "if(ctl&&typeof ctl.getClientRects==='function'){" +
                    "var rects=ctl.getClientRects();" +
                    "if(rects.length>0&&rects[0].width>4&&rects[0].height>4){" +
                        "var css=window.getComputedStyle(ctl);" +
                        "visible=!!css&&css.display!=='none'&&css.visibility!=='hidden';" +
                    "}" +
                "}" +
                "return JSON.stringify([app?1:0,ctl?1:0,vue?1:0,visible?1:0]);" +
            "}catch(e){return JSON.stringify([0,0,0,0]);}})()";
    }

    static Result parse(String rawCallback) {
        Result absent = new Result(false, false, false, false);
        if (rawCallback == null) return absent;
        try {
            // The outer JSON string comes from WebView evaluateJavascript.
            String inner = new JSONArray("[" + rawCallback + "]").getString(0);
            JSONArray fields = new JSONArray(inner);
            if (fields.length() != 4) return absent;
            boolean[] values = new boolean[4];
            for (int i = 0; i < values.length; i++) {
                Object field = fields.opt(i);
                if (!(field instanceof Number)) return absent;
                double n = ((Number) field).doubleValue();
                if (n != 0 && n != 1) return absent;
                values[i] = n == 1;
            }
            return new Result(values[0], values[1], values[2], values[3]);
        } catch (Exception ignored) {
            return absent;
        }
    }

    static boolean enabled(int sdk, boolean optedIn) {
        // API 22 has no onPageCommitVisible event.
        return sdk >= 23 && optedIn;
    }

    private EarlyNowPlayingProbe() { }
}
