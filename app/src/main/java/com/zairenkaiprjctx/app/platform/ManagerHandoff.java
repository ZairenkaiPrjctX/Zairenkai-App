package com.zairenkaiprjctx.app.platform;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Hands a verified module zip to the root manager's own app when the app cannot install it
 * from the manager's CLI. KernelSU's manager accepts {@code ACTION_VIEW} on a
 * {@code content://} zip (its manifest declares the filter) and asks the user to confirm.
 * Magisk and APatch declare no such filter, so the zip is saved to Downloads and the manager
 * app is opened; the user picks the file there. Nothing here installs anything.
 */
public final class ManagerHandoff {
    public static final String AUTHORITY_SUFFIX = ".modules";

    /** Outcome of a handoff, for the UI. {@code savedAs} is set when the zip went to Downloads. */
    public static final class Result {
        public final boolean opened;
        @Nullable public final String savedAs;
        @NonNull public final String detail;

        Result(boolean opened, @Nullable String savedAs, @NonNull String detail) {
            this.opened = opened;
            this.savedAs = savedAs;
            this.detail = detail;
        }
    }

    private final Context context;

    public ManagerHandoff(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** The first of {@code packages} that is installed and launchable, or null. */
    @Nullable
    public String installedManager(@NonNull List<String> packages) {
        PackageManager pm = context.getPackageManager();
        for (String p : packages) {
            if (pm.getLaunchIntentForPackage(p) != null) return p;
        }
        return null;
    }

    @NonNull
    public Result handOff(@NonNull File zip, @NonNull List<String> packages, boolean acceptsZipIntent) {
        String pkg = installedManager(packages);
        if (pkg == null) {
            return new Result(false, null, "The manager app is not installed or is hidden (" + String.join(", ", packages) + ").");
        }
        if (acceptsZipIntent) {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + AUTHORITY_SUFFIX, zip);
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/zip")
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(view);
                return new Result(true, null, "Opened " + pkg + " with the verified package.");
            } catch (ActivityNotFoundException e) {
                // Fall through to the Downloads path.
            }
        }
        String saved;
        try {
            saved = saveToDownloads(zip);
        } catch (IOException e) {
            return new Result(false, null, "The package could not be saved to Downloads: " + e.getMessage());
        }
        return new Result(open(pkg), saved, "Saved to " + saved + ". In the manager app, install a module from storage and choose this file.");
    }

    /** Opens the manager app (for example to remove a module where no CLI command exists). */
    public boolean open(@NonNull String pkg) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(launch);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** MediaStore Downloads (API 29, the minSdk) needs no storage permission. Returns the visible path. */
    @NonNull
    private String saveToDownloads(@NonNull File zip) throws IOException {
        ContentResolver cr = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, zip.getName());
        v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri item = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (item == null) throw new IOException("MediaStore refused the file");
        try (InputStream in = new FileInputStream(zip); OutputStream out = cr.openOutputStream(item)) {
            if (out == null) throw new IOException("no output stream");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            cr.delete(item, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(item, v, null, null);
        return "Download/" + zip.getName();
    }
}
