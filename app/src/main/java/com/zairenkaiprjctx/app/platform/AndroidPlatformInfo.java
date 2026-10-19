package com.zairenkaiprjctx.app.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zairenkaiprjctx.integration.registry.PlatformInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Android framework facts for the Kotlin integration layer ({@link PlatformInfo}).
 *
 * <p>Java: this is a thin adapter over Android framework APIs ({@link Build}, {@link
 * PowerManager}, {@link PackageManager}); it has no coroutine or Compose dependency and is
 * callable from either language. Only public APIs are used, and none needs a permission.
 * Every method is safe to call from any thread.
 */
public final class AndroidPlatformInfo implements PlatformInfo {
    private static final String ART_APEX = "com.android.art";

    private final Context context;
    @Nullable private final PowerManager power;

    public AndroidPlatformInfo(@NonNull Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.power = this.context.getSystemService(PowerManager.class);
    }

    @Override
    public int getSdkInt() {
        return Build.VERSION.SDK_INT;
    }

    @NonNull
    @Override
    public String getManufacturer() {
        return orEmpty(Build.MANUFACTURER);
    }

    @NonNull
    @Override
    public String getBrand() {
        return orEmpty(Build.BRAND);
    }

    @NonNull
    @Override
    public String getModel() {
        return orEmpty(Build.MODEL);
    }

    @NonNull
    @Override
    public String getDeviceCodename() {
        return orEmpty(Build.DEVICE);
    }

    @Nullable
    @Override
    public String getSocManufacturer() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? blankToNull(Build.SOC_MANUFACTURER) : null;
    }

    @Nullable
    @Override
    public String getSocModel() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? blankToNull(Build.SOC_MODEL) : null;
    }

    @NonNull
    @Override
    public List<String> getSupportedAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis == null ? Collections.<String>emptyList() : Collections.unmodifiableList(Arrays.asList(abis));
    }

    @NonNull
    public String getAndroidRelease() {
        return orEmpty(Build.VERSION.RELEASE);
    }

    /** {@code PowerManager.getCurrentThermalStatus()}; null when the service is unavailable. */
    @Nullable
    @Override
    public Integer thermalStatus() {
        return power == null ? null : power.getCurrentThermalStatus();
    }

    /** {@code PowerManager.getThermalHeadroom()} (API 30+); null when unsupported or NaN. */
    @Nullable
    @Override
    public Float thermalHeadroom(int forecastSeconds) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || power == null) return null;
        float h = power.getThermalHeadroom(forecastSeconds);
        return Float.isNaN(h) ? null : h;
    }

    @Override
    public boolean powerSaveMode() {
        return power != null && power.isPowerSaveMode();
    }

    /** Version code of the ART APEX via {@code MATCH_APEX} (API 29+); null if not reported. */
    @Nullable
    @Override
    public Long artApexVersion() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(ART_APEX, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Stable English key for a {@code PowerManager.THERMAL_STATUS_*} value (for logs and tests). */
    @NonNull
    public static String thermalStatusKey(@Nullable Integer status) {
        if (status == null) return "unavailable";
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unknown";
        }
    }

    @NonNull
    private static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
