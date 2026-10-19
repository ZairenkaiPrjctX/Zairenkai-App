package com.zairenkaiprjctx.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import androidx.test.core.app.ApplicationProvider;
import com.zairenkaiprjctx.app.platform.AndroidPlatformInfo;
import com.zairenkaiprjctx.app.platform.ThermalStatusMonitor;
import com.zairenkaiprjctx.integration.registry.PlatformInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Java platform components, exercised from Java and through the Kotlin interface. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlatformJavaTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void implementsTheKotlinPlatformInterface() {
        PlatformInfo info = new AndroidPlatformInfo(context);
        assertEquals(Build.VERSION.SDK_INT, info.getSdkInt());
        assertNotNull(info.getSupportedAbis());
        assertNotNull(info.getDeviceCodename());
        // Robolectric has no ART APEX: the adapter reports "not reported", not a fake version.
        assertNull(info.artApexVersion());
        assertFalse(info.powerSaveMode());
    }

    @Test
    public void thermalStatusKeys() {
        assertEquals("none", AndroidPlatformInfo.thermalStatusKey(PowerManager.THERMAL_STATUS_NONE));
        assertEquals("severe", AndroidPlatformInfo.thermalStatusKey(PowerManager.THERMAL_STATUS_SEVERE));
        assertEquals("shutdown", AndroidPlatformInfo.thermalStatusKey(PowerManager.THERMAL_STATUS_SHUTDOWN));
        assertEquals("unavailable", AndroidPlatformInfo.thermalStatusKey(null));
        assertEquals("unknown", AndroidPlatformInfo.thermalStatusKey(42));
    }

    @Test
    public void thermalListenerRegistrationIsReleasedOnce() {
        ThermalStatusMonitor monitor = new ThermalStatusMonitor(context);
        assertTrue(monitor.isSupported());
        List<Integer> seen = new ArrayList<>();
        // Robolectric's PowerManager refuses the listener ("Listener failed to set"), as a device
        // without a thermal service would: start() must report that as null, not throw.
        ThermalStatusMonitor.Registration r = monitor.start(Runnable::run, seen::add);
        if (r != null) {
            r.close();
            r.close(); // idempotent: the second close must not throw
        }
        assertTrue(seen.isEmpty() || seen.size() == 1);
    }
}
