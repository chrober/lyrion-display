package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Base64;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercises actual WebView image loading, DOM mutation and fallback on API 22+. */
@RunWith(AndroidJUnit4.class)
public final class CoverTransitionSmokeTest {
    @Before public void clearConfiguration() {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private static byte[] png(int color) {
        Bitmap image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        image.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        image.recycle();
        return bytes.toByteArray();
    }

    private static final String MASKED =
            "!!document.querySelector('img[data-lyrion-cover-wait]')";
    private static final String OVERLAY =
            "!!document.querySelector('canvas[data-lyrion-cover-overlay]')";
    private static final String READY =
            "window.__lyrionCoverTransition && window.__lyrionCoverTransition.ready";
    private static final String STATUS = "window.__lyrionCoverTransition.status";

    private static final class Fixture implements AutoCloseable {
        final ActivityScenario<MainActivity> scenario;
        final AtomicReference<WebView> web = new AtomicReference<>();
        final CountDownLatch page = new CountDownLatch(1);
        final CountDownLatch requested = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final byte[] nextImage = png(Color.BLUE);

        Fixture(String revision, boolean compatibleDom) throws Exception {
            scenario = ActivityScenario.launch(MainActivity.class);
            try {
                String initial = Base64.encodeToString(png(Color.RED), Base64.NO_WRAP);
                String html = "<!doctype html><html><head><meta name='viewport' "
                        + "content='width=device-width, initial-scale=1'>"
                        + "<script>const LMS_MATERIAL_REVISION='" + revision + "';</script>"
                        + "<style>.np-image-landscape{position:relative;width:120px;height:120px}"
                        + ".np-cover{width:120px;height:120px;object-fit:contain;opacity:1}</style>"
                        + "</head><body><div id='app'><div id='np-page'><div class='"
                        + (compatibleDom ? "np-image-landscape" : "future-layout")
                        + "'><img class='np-cover' src='data:image/png;base64," + initial
                        + "'></div></div></div></body></html>";
                scenario.onActivity(activity -> {
                    WebView view = new WebView(activity);
                    web.set(view);
                    view.getSettings().setJavaScriptEnabled(true);
                    FrameLayout root = activity.findViewById(android.R.id.content);
                    root.addView(view, new FrameLayout.LayoutParams(200, 200));
                    view.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView view, String url) {
                            page.countDown();
                        }
                        private WebResourceResponse intercept(String url) {
                            if (!url.equals("https://cover.example.invalid/slow.png")) return null;
                            requested.countDown();
                            try {
                                if (!release.await(10, TimeUnit.SECONDS))
                                    return new WebResourceResponse("image/png", null,
                                            new ByteArrayInputStream(new byte[0]));
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            return new WebResourceResponse("image/png", null,
                                    new ByteArrayInputStream(nextImage));
                        }
                        @Override public WebResourceResponse shouldInterceptRequest(
                                WebView view, WebResourceRequest request) {
                            return intercept(request.getUrl().toString());
                        }
                        @SuppressWarnings("deprecation")
                        @Override public WebResourceResponse shouldInterceptRequest(
                                WebView view, String url) {
                            return intercept(url);
                        }
                    });
                    view.loadDataWithBaseURL(
                            "https://example.invalid/material/?css=msk--np-only&js=msk--np-only",
                            html, "text/html", "UTF-8", null);
                });
                assertTrue("Fixture page load", page.await(10, TimeUnit.SECONDS));
                await("document.querySelector('img.np-cover').complete && "
                        + "document.querySelector('img.np-cover').naturalWidth>0", "true");
            } catch (Exception | AssertionError failure) {
                close();
                throw failure;
            }
        }

        String eval(String script) throws Exception {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            scenario.onActivity(activity -> web.get().evaluateJavascript(script, raw -> {
                result.set(raw);
                done.countDown();
            }));
            assertTrue("JavaScript callback", done.await(5, TimeUnit.SECONDS));
            return result.get();
        }

        void await(String script, String expected) throws Exception {
            long deadline = SystemClock.elapsedRealtime() + 4500;
            String value;
            do {
                value = eval(script);
                if (expected.equals(value)) return;
                SystemClock.sleep(40);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertEquals(script, expected, value);
        }

        void install() throws Exception {
            eval(CoverTransition.javascript(ApplicationProvider.getApplicationContext()));
        }

        void replaceCover() throws Exception {
            eval("(function(){var old=document.querySelector('img.np-cover');"
                    + "var next=old.cloneNode(false);"
                    + "next.src='https://cover.example.invalid/slow.png';"
                    + "old.parentNode.replaceChild(next,old);return true;})()");
            assertTrue("Replacement cover requested", requested.await(4, TimeUnit.SECONDS));
        }

        @Override public void close() {
            release.countDown();
            scenario.onActivity(activity -> {
                WebView view = web.get();
                if (view != null) {
                    if (view.getParent() instanceof FrameLayout)
                        ((FrameLayout) view.getParent()).removeView(view);
                    view.stopLoading();
                    view.destroy();
                }
            });
            scenario.close();
        }
    }

    @Test public void delayedReplacementKeepsOldArtworkUntilNewImageIsReady() throws Exception {
        try (Fixture f = new Fixture("6.5.0", true)) {
            f.install();
            f.await(STATUS, "\"active\"");
            f.await(READY, "true");
            assertEquals("false", f.eval(OVERLAY));
            f.replaceCover();
            f.await(MASKED, "true");
            assertEquals("true", f.eval(OVERLAY));
            assertEquals("\"hidden\"", f.eval(
                    "getComputedStyle(document.querySelector('img.np-cover')).visibility"));
            f.release.countDown();
            f.await(MASKED, "false");
            // Verify an actual intermediate rendered opacity, not just removal.
            f.await("(function(){var c=document.querySelector('canvas[data-lyrion-cover-overlay]');"
                    + "if(!c)return false;var o=parseFloat(getComputedStyle(c).opacity);"
                    + "return o>0 && o<1;})()", "true");
            f.await(OVERLAY, "false");
            f.await("window.__lyrionCoverTransition.completedFades", "1");
            assertEquals("0", f.eval("window.__lyrionCoverTransition.timedOutFades"));
            f.await(READY, "true");
            assertEquals("\"active\"", f.eval(STATUS));
            assertEquals("\"visible\"", f.eval(
                    "getComputedStyle(document.querySelector('img.np-cover')).visibility"));
        }
    }

    @Test public void unexpectedDomWhileLoadingImmediatelyRestoresNativeRendering() throws Exception {
        try (Fixture f = new Fixture("6.5.0", true)) {
            f.install();
            f.await(READY, "true");
            f.replaceCover();
            f.await(MASKED, "true");
            f.eval("document.querySelector('img.np-cover').className='future-cover'");
            f.await(STATUS, "\"unsupported-dom\"");
            assertEquals("false", f.eval(MASKED));
            assertEquals("false", f.eval(OVERLAY));
            assertEquals("\"visible\"", f.eval(
                    "getComputedStyle(document.querySelector('img.future-cover')).visibility"));
            f.release.countDown();
        }
    }

    @Test public void unknownVersionAndUnknownInitialDomNeverInstallOverlay() throws Exception {
        try (Fixture f = new Fixture("6.6.0", true)) {
            f.install();
            assertEquals("\"unsupported-version\"", f.eval(STATUS));
            assertEquals("false", f.eval(OVERLAY));
            assertEquals("false", f.eval(MASKED));
        }
        try (Fixture f = new Fixture("6.5.0", false)) {
            f.install();
            assertEquals("\"unsupported-dom\"", f.eval(STATUS));
            assertEquals("false", f.eval(OVERLAY));
            assertEquals("false", f.eval(MASKED));
        }
    }
}
