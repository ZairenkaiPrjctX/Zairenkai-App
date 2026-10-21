// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zairenkaiprjctx.integration.afid.AfidCatalog;
import com.zairenkaiprjctx.integration.afid.AfidDevice;
import com.zairenkaiprjctx.integration.exec.CommandOutcome;
import com.zairenkaiprjctx.integration.exec.CommandResult;
import com.zairenkaiprjctx.integration.exec.CommandRunner;
import com.zairenkaiprjctx.integration.exec.RootConsentStore;
import com.zairenkaiprjctx.integration.exec.RootShell;
import com.zairenkaiprjctx.integration.exec.RootState;
import com.zairenkaiprjctx.integration.exec.ShellWord;
import com.zairenkaiprjctx.integration.zart.PackageNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The Kotlin integration API as Java callers see it: statics, nullability, sealed results. */
public class JavaInteropTest {
    @Test
    public void staticValidatorsAndNullableResults() {
        assertTrue(PackageNames.isValid("com.example.app"));
        assertFalse(PackageNames.isValid("a;b"));
        assertNotNull(ShellWord.arg("power_saver"));
        assertNull(ShellWord.arg("$(id)")); // Kotlin `ShellWord?` arrives as a nullable reference
    }

    @Test
    public void javaImplementationsOfKotlinInterfaces() {
        final List<List<String>> calls = new ArrayList<>();
        CommandRunner runner = (argv, timeoutMs) -> {
            calls.add(argv);
            return new CommandOutcome.Finished(new CommandResult(0, "uid=0(root) gid=0(root)\n", ""));
        };
        RootConsentStore consent = new RootConsentStore() {
            private boolean granted;

            @Override
            public boolean getRootGranted() {
                return granted;
            }

            @Override
            public void setRootGranted(boolean value) {
                granted = value;
            }
        };
        RootShell root = new RootShell(runner, consent, () -> true);
        assertEquals(RootState.NOT_REQUESTED, root.getState());
        assertEquals(RootState.GRANTED, root.request());
        assertTrue(consent.getRootGranted());
        assertEquals(Arrays.asList("su", "-c", "id"), calls.get(0));
    }

    @Test
    public void kotlinDataFromJava() throws Exception {
        String text = new String(
                JavaInteropTest.class.getResourceAsStream("/fixtures/afid-two-devices.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        AfidCatalog catalog = AfidCatalog.Companion.parse(text);
        List<AfidDevice> hits = catalog.lookup("akita");
        assertEquals(1, hits.size());
        assertEquals("Pixel 8a", hits.get(0).getProductNames().get(0));
    }
}
