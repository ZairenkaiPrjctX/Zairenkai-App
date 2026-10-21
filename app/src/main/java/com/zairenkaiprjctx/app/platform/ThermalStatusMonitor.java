// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.platform;

import android.content.Context;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.Executor;

/**
 * Event-driven thermal status from {@code PowerManager.addThermalStatusListener} (API 29+).
 *
 * <p>Java: a callback-style framework listener with explicit registration and removal. The
 * Kotlin layer adapts it to a Flow with {@code callbackFlow}, unregistering on cancellation.
 * The platform delivers the current status right after registration, then every change.
 */
public final class ThermalStatusMonitor {
    /** Receives {@code PowerManager.THERMAL_STATUS_*} values on the given executor. */
    public interface Listener {
        void onThermalStatus(int status);
    }

    /** Handle returned by {@link #start}; {@link #close} is idempotent. */
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private final PowerManager power;

    public ThermalStatusMonitor(@NonNull Context context) {
        this.power = context.getApplicationContext().getSystemService(PowerManager.class);
    }

    /** False when the power service is missing (some test or emulator images). */
    public boolean isSupported() {
        return power != null;
    }

    /**
     * Starts listening, or returns null when the platform refuses the listener: the power service
     * is missing, or {@code addThermalStatusListener} throws (it throws a RuntimeException
     * "Listener failed to set" when the thermal service is unavailable). Callers must then poll
     * {@code getCurrentThermalStatus()} instead.
     */
    @Nullable
    public Registration start(@NonNull Executor executor, @NonNull Listener listener) {
        if (power == null) return null;
        final PowerManager.OnThermalStatusChangedListener l = listener::onThermalStatus;
        try {
            power.addThermalStatusListener(executor, l);
        } catch (RuntimeException e) {
            return null;
        }
        return new Registration() {
            private boolean closed;

            @Override
            public synchronized void close() {
                if (closed) return;
                closed = true;
                power.removeThermalStatusListener(l);
            }
        };
    }
}
