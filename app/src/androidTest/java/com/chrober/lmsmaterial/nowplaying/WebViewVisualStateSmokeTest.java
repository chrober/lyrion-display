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
 * Exercises the system WebView (not a mock) with an attached INVISIBLE view.
 * Runs on API 22 as a regression for missing API 23 methods, and on
 * API 25 (Fire OS 6 baseline) / API 29 with an actual visual-state callback.
 */
@RunWith(AndroidJUnit4.class)
public final class WebViewVisualStateSmokeTest {
    @Test public void loadedOffscreenPageIsShownOnVisualCommitOrApi22Fallback()
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
                view.setVisibility(View.INVISIBLE);
                if (Build.VERSION.SDK_INT >= 23) {
                    view.getSettings().setOffscreenPreRaster(true);
                    assertTrue(view.getSettings().getOffscreenPreRaster());
                } else {
                    assertFalse(WebViewReadinessPolicy.supportsVisualState(
                            Build.VERSION.SDK_INT));
                }
                container.addView(view, new FrameLayout.LayoutParams(160, 160));
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView page, String url) {
                        if (Build.VERSION.SDK_INT >= 23) {
                            page.postVisualStateCallback(42L,
                                    new WebView.VisualStateCallback() {
                                        @Override public void onComplete(long requestId) {
                                            assertEquals(42L, requestId);
                                            page.setVisibility(View.VISIBLE);
                                            shown.countDown();
                                        }
                                    });
                        } else {
                            // Ensure the API 22 fallback never invokes API 23.
                            page.setVisibility(View.VISIBLE);
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
