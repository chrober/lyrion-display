package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Optional, fail-closed foreground artwork adapter. Never gates page readiness. */
final class CoverTransition {
    private CoverTransition() { }

    static String javascript(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("cover-transition.js");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void install(Context context, WebView view) {
        try {
            view.evaluateJavascript(javascript(context), raw -> {
                // Do not log arbitrary WebView content, URLs or player information.
                if (raw != null && raw.matches("\\\"(active|checking|unsupported-mode|unsupported-version|unsupported-webview|unsupported-dom|render-fallback|snapshot-unavailable|decode-fallback|load-timeout|changed-layout|page-hidden)\\\""))
                    Log.i("LyrionCoverTransition", "state=" + raw);
            });
        } catch (Exception ignored) {
            Log.i("LyrionCoverTransition", "state=unavailable");
        }
    }
}
