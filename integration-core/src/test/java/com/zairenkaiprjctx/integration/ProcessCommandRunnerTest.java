package com.zairenkaiprjctx.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.zairenkaiprjctx.integration.exec.CommandOutcome;
import com.zairenkaiprjctx.integration.exec.ProcessCommandRunner;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Runs real processes on the host (POSIX sh), not fakes. */
public class ProcessCommandRunnerTest {
    private static List<String> sh(String script) {
        return Arrays.asList("/bin/sh", "-c", script);
    }

    @Test
    public void finishedWithExitCodeAndBothStreams() {
        CommandOutcome o = new ProcessCommandRunner().run(sh("printf out; printf err >&2; exit 3"), 10_000);
        assertTrue(o instanceof CommandOutcome.Finished);
        CommandOutcome.Finished f = (CommandOutcome.Finished) o;
        assertEquals(3, f.getResult().getExitCode());
        assertEquals("out", f.getResult().getStdout());
        assertEquals("err", f.getResult().getStderr());
    }

    @Test
    public void argumentsAreNotInterpretedByAShell() {
        // Passed as one argv element to printf, so ';' and '$(...)' stay literal.
        CommandOutcome o = new ProcessCommandRunner().run(Arrays.asList("printf", "%s", "a; $(id)"), 10_000);
        assertEquals("a; $(id)", ((CommandOutcome.Finished) o).getResult().getStdout());
    }

    @Test
    public void timeoutKillsTheProcess() {
        long t0 = System.nanoTime();
        CommandOutcome o = new ProcessCommandRunner().run(sh("sleep 30"), 300);
        assertSame(CommandOutcome.TimedOut.INSTANCE, o);
        assertTrue("returned promptly", (System.nanoTime() - t0) / 1_000_000 < 10_000);
    }

    @Test
    public void oversizedOutputIsRejectedNotTruncated() {
        CommandOutcome o = new ProcessCommandRunner(1024).run(sh("head -c 5000 /dev/zero"), 10_000);
        assertSame(CommandOutcome.OutputTooLarge.INSTANCE, o);
        CommandOutcome exact = new ProcessCommandRunner(1024).run(sh("head -c 1024 /dev/zero"), 10_000);
        assertEquals(1024, ((CommandOutcome.Finished) exact).getResult().getStdout().length());
    }

    @Test
    public void missingProgramIsASpawnFailure() {
        CommandOutcome o = new ProcessCommandRunner().run(Arrays.asList("/nonexistent/zairenkai-test"), 1000);
        assertTrue(o instanceof CommandOutcome.SpawnFailed);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyArgvIsRejected() {
        new ProcessCommandRunner().run(Arrays.<String>asList(), 1000);
    }
}
