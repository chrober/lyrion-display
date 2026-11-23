package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Run the passive ES5 timing snapshot in a real API 22/25/29 system WebView. */
@RunWith(AndroidJUnit4.class)
public final class WebViewPagePerformanceSmokeTest {
    @Test public void performanceSnapshotProducesSafeMetricsOnRealWebView() throws Exception {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().clear().commit();
        CountDownLatch sampled = new CountDownLatch(3);
        AtomicReference<String> report = new AtomicReference<>();
        AtomicReference<TimedDisplayOptions.Result> timed = new AtomicReference<>();
        AtomicReference<WebViewCommitProbe.Result> commitProbe = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout container = activity.findViewById(android.R.id.content);
                WebView view = new WebView(activity);
                view.getSettings().setJavaScriptEnabled(true);
                container.addView(view, new FrameLayout.LayoutParams(160, 160));
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView page, String url) {
                        page.evaluateJavascript(WebViewPagePerformance.javascript(), raw -> {
                            report.set(WebViewPagePerformance.report(raw));
                            sampled.countDown();
                        });
                        page.evaluateJavascript(TimedDisplayOptions.wrap(
                                "(function(){return 'ready';})()"), raw -> {
                            timed.set(TimedDisplayOptions.parse(raw));
                            sampled.countDown();
                        });
                        page.evaluateJavascript(WebViewCommitProbe.javascript(), raw -> {
                            commitProbe.set(WebViewCommitProbe.parse(raw));
                            sampled.countDown();
                        });
                    }
                });
                view.loadDataWithBaseURL("https://example.invalid/",
                        "<html><body><div id='app'><div class='np-controls' " +
                        "style='width:80px;height:40px'></div></div></body></html>",
                        "text/html", "UTF-8", null);
            });
            assertTrue("Timing callback must return on system WebView",
                    sampled.await(20, TimeUnit.SECONDS));
            assertNotNull(report.get());
            assertTrue(report.get(), report.get().contains("phase=page_perf state=ok"));
            assertTrue(report.get(), report.get().contains("app_present=1"));
            assertTrue(report.get(), report.get().contains("np_controls_present=1"));
            assertFalse(report.get(), report.get().contains("example.invalid"));
            assertNotNull(timed.get());
            assertEquals(TimedDisplayOptions.Status.READY, timed.get().status);
            assertTrue("Synchronous WebView script time should be measured",
                    timed.get().scriptMicros >= 0);
            assertNotNull(commitProbe.get());
            assertTrue("DOM should exist when finished",
                    commitProbe.get().readyState >= 1);
            assertEquals(1, commitProbe.get().appPresent);
            assertEquals(1, commitProbe.get().controlsPresent);
        }
    }
}
