package com.zairenkaiprjctx.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.test.core.app.ApplicationProvider;
import com.zairenkaiprjctx.app.platform.ManagerHandoff;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

/** The handoff to a root-manager app: Robolectric package manager, no real manager installed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = ZairenkaiApplication.class)
public class ManagerHandoffTest {
    private final Application app = ApplicationProvider.getApplicationContext();

    private File stagedZip() throws IOException {
        File dir = new File(app.getCacheDir(), "system-modules");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File f = new File(dir, "zairenkai_core-100.zip");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[] {'P', 'K', 3, 4});
        }
        return f;
    }

    private void installLauncher(String pkg) {
        ShadowPackageManager spm = shadowOf(app.getPackageManager());
        ComponentName cn = new ComponentName(pkg, pkg + ".MainActivity");
        spm.addActivityIfNotPresent(cn);
        IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
        f.addCategory(Intent.CATEGORY_LAUNCHER);
        spm.addIntentFilterForActivity(cn, f);
    }

    @Test
    public void withoutTheManagerAppNothingIsOpenedOrSaved() throws IOException {
        ManagerHandoff h = new ManagerHandoff(app);
        assertNull(h.installedManager(Arrays.asList("me.weishu.kernelsu", "com.rifsxd.ksunext")));
        ManagerHandoff.Result r = h.handOff(stagedZip(), Collections.singletonList("me.weishu.kernelsu"), true);
        assertFalse(r.opened);
        assertNull(r.savedAs);
        assertTrue(r.detail.contains("me.weishu.kernelsu"));
        assertNull(shadowOf(app).getNextStartedActivity());
    }

    @Test
    public void kernelSuReceivesTheVerifiedZipThroughTheFileProvider() throws IOException {
        installLauncher("com.rifsxd.ksunext");
        ManagerHandoff h = new ManagerHandoff(app);
        assertEquals("com.rifsxd.ksunext", h.installedManager(Arrays.asList("me.weishu.kernelsu", "com.rifsxd.ksunext")));
        ManagerHandoff.Result r = h.handOff(stagedZip(), Arrays.asList("me.weishu.kernelsu", "com.rifsxd.ksunext"), true);
        assertTrue(r.detail, r.opened);
        Intent sent = shadowOf(app).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, sent.getAction());
        assertEquals("application/zip", sent.getType());
        assertEquals("com.rifsxd.ksunext", sent.getPackage());
        assertEquals("content", sent.getData().getScheme());
        assertEquals(app.getPackageName() + ManagerHandoff.AUTHORITY_SUFFIX, sent.getData().getAuthority());
        assertTrue((sent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    @Test
    public void openingAManagerUsesItsLaunchIntentOnly() {
        installLauncher("com.topjohnwu.magisk");
        ManagerHandoff h = new ManagerHandoff(app);
        assertTrue(h.open("com.topjohnwu.magisk"));
        assertEquals("com.topjohnwu.magisk", shadowOf(app).getNextStartedActivity().getComponent().getPackageName());
        assertFalse(h.open("me.bmax.apatch"));
    }
}
