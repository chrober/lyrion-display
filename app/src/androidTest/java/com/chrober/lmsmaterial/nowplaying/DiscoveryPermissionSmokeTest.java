package com.chrober.lmsmaterial.nowplaying;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Check the actual Wi-Fi multicast lock after removing ACCESS_WIFI_STATE.
 * Runs on API 22/25/29/35; supports the app's eIPAD UDP discovery.
 */
@RunWith(AndroidJUnit4.class)
public final class DiscoveryPermissionSmokeTest {
    @Test public void multicastLockWorksWithoutWifiStatePermission() {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager pm = context.getPackageManager();
        String name = context.getPackageName();
        assertEquals("Do not request unnecessary Wi-Fi state access",
                PackageManager.PERMISSION_DENIED,
                pm.checkPermission(Manifest.permission.ACCESS_WIFI_STATE, name));
        assertEquals("Multicast permission is required by UDP server discovery",
                PackageManager.PERMISSION_GRANTED,
                pm.checkPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE, name));

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        // A device without a Wi-Fi service may still work via manual server entry.
        Assume.assumeNotNull(wifi);
        WifiManager.MulticastLock lock = wifi.createMulticastLock("lyrion-ci-discovery");
        lock.setReferenceCounted(false);
        try {
            lock.acquire();
            assertTrue("Multicast lock must be acquired", lock.isHeld());
        } finally {
            if (lock.isHeld()) lock.release();
        }
    }
}
