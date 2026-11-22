package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Ensure cold-start WebView preparation never bypasses the HTTP trust gate. */
@RunWith(AndroidJUnit4.class)
public final class ColdStartSmokeTest {
    @Test public void preparedWebViewDoesNotNavigateUntilMaterialProbeSucceeds()
            throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().clear().commit();

        CountDownLatch probeReceived = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        CountDownLatch webViewRequest = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(
                0, 4, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        try (Socket client = server.accept()) {
                            client.setSoTimeout(5000);
                            BufferedReader request = new BufferedReader(new InputStreamReader(
                                    client.getInputStream(), StandardCharsets.US_ASCII));
                            String line = request.readLine();
                            if (line == null) continue;
                            while ((line = request.readLine()) != null && !line.isEmpty()) {
                                // Drain HTTP request headers before responding.
                            }
                            int count = requests.incrementAndGet();
                            if (count == 1) {
                                probeReceived.countDown();
                                if (!releaseProbe.await(20, TimeUnit.SECONDS)) return;
                            } else {
                                webViewRequest.countDown();
                            }
                            String body = "<html><body><div id='app'></div>"
                                    + "<!-- msk--np-only -->"
                                    + "</body></html>";
                            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                            String headers = "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: text/html; charset=utf-8\r\n"
                                    + "Content-Length: " + bytes.length + "\r\n"
                                    + "Connection: close\r\n\r\n";
                            OutputStream output = client.getOutputStream();
                            output.write(headers.getBytes(StandardCharsets.US_ASCII));
                            output.write(bytes);
                            output.flush();
                        }
                    }
                } catch (SocketException closed) {
                    // The test closes the server in its finally block.
                } catch (Exception unexpected) {
                    throw new AssertionError("Loopback server failed", unexpected);
                }
            }, "lyrion-qa-probe-server");
            responder.setDaemon(true);
            responder.start();
            try {
                app.getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                        .edit().putString("server", "http://127.0.0.1:" + server.getLocalPort())
                        .putString("player_id", "test-player").commit();
                try (ActivityScenario<MainActivity> scenario =
                             ActivityScenario.launch(MainActivity.class)) {
                    assertTrue("HTTP preflight should reach loopback test server",
                            probeReceived.await(10, TimeUnit.SECONDS));
                    scenario.onActivity(activity -> {
                        try {
                            Field field = MainActivity.class.getDeclaredField("browser");
                            field.setAccessible(true);
                            WebView prepared = (WebView) field.get(activity);
                            assertNotNull("WebView should be prepared while probe is pending",
                                    prepared);
                            assertNull("Unverified WebView must not navigate",
                                    prepared.getUrl());
                        } catch (ReflectiveOperationException e) {
                            throw new AssertionError("Cannot inspect WebView", e);
                        }
                    });
                    assertEquals("Only the native preflight should have requested the server",
                            1, requests.get());
                    releaseProbe.countDown();
                    assertTrue("WebView should navigate after a verified probe response",
                            webViewRequest.await(10, TimeUnit.SECONDS));
                }
            } finally {
                releaseProbe.countDown();
                server.close();
                responder.join(2000);
            }
        }
    }
}
