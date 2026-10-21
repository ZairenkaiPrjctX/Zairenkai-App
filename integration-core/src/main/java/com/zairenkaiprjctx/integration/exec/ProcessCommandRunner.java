// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.exec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a fixed argv with {@link ProcessBuilder}: no shell, a timeout, and a cap on stdout.
 *
 * <p>Implemented in Java because it is plain JVM process and stream handling, with explicit
 * threads, used unchanged on Android and in host tests. It implements the Kotlin
 * {@link CommandRunner} interface and returns the Kotlin {@link CommandOutcome} sealed types.
 *
 * <p>Concurrency: {@link #run} blocks the calling thread until the process exits or the timeout
 * passes. Two daemon threads drain stdout and stderr so a chatty process cannot block on a full
 * pipe. Callers must invoke it off the main thread (the app uses Dispatchers.IO).
 */
public final class ProcessCommandRunner implements CommandRunner {
    private static final int STDERR_CAP = 16 * 1024;
    private static final int STDERR_KEPT = 2000;
    private final int maxOutputBytes;

    public ProcessCommandRunner() {
        this(1 << 20);
    }

    public ProcessCommandRunner(int maxOutputBytes) {
        if (maxOutputBytes <= 0) throw new IllegalArgumentException("maxOutputBytes must be positive");
        this.maxOutputBytes = maxOutputBytes;
    }

    @NotNull
    @Override
    public CommandOutcome run(@NotNull List<String> argv, long timeoutMs) {
        if (argv.isEmpty()) throw new IllegalArgumentException("empty argv");
        final Process process;
        try {
            process = new ProcessBuilder(new ArrayList<>(argv)).start();
        } catch (IOException | SecurityException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new CommandOutcome.SpawnFailed(detail);
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // The child may already have exited; stdin is not used.
        }
        Drain out = new Drain(process.getInputStream(), maxOutputBytes, "cmd-stdout");
        Drain err = new Drain(process.getErrorStream(), STDERR_CAP, "cmd-stderr");
        out.start();
        err.start();
        boolean exited;
        try {
            exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return CommandOutcome.TimedOut.INSTANCE;
        }
        if (!exited) {
            process.destroyForcibly();
            return CommandOutcome.TimedOut.INSTANCE;
        }
        out.await();
        err.await();
        if (out.overflowed) return CommandOutcome.OutputTooLarge.INSTANCE;
        String stderr = new String(err.bytes, StandardCharsets.UTF_8);
        if (stderr.length() > STDERR_KEPT) stderr = stderr.substring(0, STDERR_KEPT);
        return new CommandOutcome.Finished(
                new CommandResult(process.exitValue(), new String(out.bytes, StandardCharsets.UTF_8), stderr));
    }

    /**
     * Reads at most {@code cap} bytes and drains the rest. {@code result[1]} is non-empty when
     * more than {@code cap} bytes arrived (the output must then be treated as unusable).
     */
    @NotNull
    static byte[][] readCapped(@NotNull InputStream input, int cap) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        boolean over = false;
        try (InputStream in = input) {
            int n;
            while ((n = in.read(chunk)) >= 0) {
                int room = cap - buf.size();
                if (n > room) {
                    if (room > 0) buf.write(chunk, 0, room);
                    over = true;
                } else {
                    buf.write(chunk, 0, n);
                }
            }
        }
        return new byte[][] {buf.toByteArray(), over ? new byte[] {1} : new byte[0]};
    }

    private static final class Drain extends Thread {
        private final InputStream input;
        private final int cap;
        volatile byte[] bytes = new byte[0];
        volatile boolean overflowed;

        Drain(InputStream input, int cap, String name) {
            super(name);
            this.input = input;
            this.cap = cap;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                byte[][] r = readCapped(input, cap);
                bytes = r[0];
                overflowed = r[1].length > 0;
            } catch (IOException e) {
                // The stream closed early (process killed): keep what was read.
            }
        }

        void await() {
            try {
                join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
