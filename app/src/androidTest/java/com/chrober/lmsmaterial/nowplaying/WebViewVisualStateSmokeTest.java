package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Exercises the standard attached, visible system WebView (not a mock).
 * API 22 uses the legacy completion path. API 25/29 use visual-state callback.
 */
@RunWith(AndroidJUnit4.class)
public final class WebViewVisualStateSmokeTest {
    @Test public void loadedPageCompletesVisualCommitOrApi22Fallback()
            throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().clear().commit();

        CountDownLatch shown = new CountDownLatch(1);
        AtomicReference<WebView> webView = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout container = activity.findViewById(android.R.id.content);
                WebView view = new WebView(activity);
                webView.set(view);
                view.setTag("webview-visual-qa");
                assertEquals(View.VISIBLE, view.getVisibility());
                if (Build.VERSION.SDK_INT < 23) {
                    assertFalse(WebViewReadinessPolicy.supportsVisualState(
                            Build.VERSION.SDK_INT));
                }
                container.addView(view, new FrameLayout.LayoutParams(160, 160));
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView page, String url) {
                        if (Build.VERSION.SDK_INT >= 23) {
                            WebViewApi23.requestVisualState(page, 42L, requestId -> {
                                assertEquals(42L, requestId);
                                shown.countDown();
                            });
                        } else {
                            // Ensure the API 22 fallback never invokes API 23.
                            shown.countDown();
                        }
                    }
                });
                view.loadDataWithBaseURL("https://example.invalid/",
                        "<html><body><p>Visual state smoke test</p></body></html>",
                        "text/html", "UTF-8", null);
            });
            assertTrue("Attached WebView must become render-ready",
                    shown.await(18, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                assertNotNull(webView.get());
                assertEquals(View.VISIBLE, webView.get().getVisibility());
            });
        }
    }
}
