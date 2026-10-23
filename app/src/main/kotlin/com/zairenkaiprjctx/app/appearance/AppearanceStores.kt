// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.zairenkaiprjctx.app.R
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The photo wallpaper: a copy of the image the user picked with the system photo picker, scaled
 * down and kept in the app's private storage. No storage permission is needed and the original is
 * never modified; removing the wallpaper deletes the copy.
 */
class WallpaperStore(private val context: Context) {
    private val file = File(context.filesDir, "wallpaper.png")
    private val image = MutableStateFlow(load())

    /** Bumps whenever the stored photo changes, so screens reload it. */
    val photo: StateFlow<Bitmap?> = image.asStateFlow()

    private fun load(): Bitmap? = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null

    /** Decodes [uri] at most [maxSide] px on its longest side and stores it; false if it is not an image. */
    fun save(uri: Uri, maxSide: Int = 2048): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return false
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.value = bitmap
        true
    }.getOrDefault(false)

    fun clear() {
        file.delete()
        image.value = null
    }
}

/**
 * Launcher icon variants. Each is an `activity-alias` of MainActivity in the manifest with its own
 * adaptive icon; exactly one alias is enabled. [background] and [foreground] are the icon's colours.
 */
enum class LauncherIcon(val alias: String, @StringRes val label: Int, val background: Color, val foreground: Color) {
    MIDNIGHT(".launcher.Midnight", R.string.icon_midnight, Color(0xFF0F1417), Color(0xFF65D3FF)),
    OCEAN(".launcher.Ocean", R.string.icon_ocean, Color(0xFF006782), Color(0xFFFFFFFF)),
    MINT(".launcher.Mint", R.string.icon_mint, Color(0xFFC4F5E2), Color(0xFF00513F)),
    LIME(".launcher.Lime", R.string.icon_lime, Color(0xFFD9F99D), Color(0xFF1A3300)),
    AMBER(".launcher.Amber", R.string.icon_amber, Color(0xFFF59E0B), Color(0xFF2B1700)),
    CRIMSON(".launcher.Crimson", R.string.icon_crimson, Color(0xFFB3261E), Color(0xFFFFFFFF)),
    VIOLET(".launcher.Violet", R.string.icon_violet, Color(0xFF4C1D95), Color(0xFFE9D5FF)),
    COBALT(".launcher.Cobalt", R.string.icon_cobalt, Color(0xFF1D4ED8), Color(0xFFFFFFFF)),
    PAPER(".launcher.Paper", R.string.icon_paper, Color(0xFFFFFFFF), Color(0xFF111418)),
    BLACK(".launcher.Black", R.string.icon_black, Color(0xFF000000), Color(0xFFFFFFFF)),
    ;

    companion object {
        /** The variant whose background is nearest to [color] (Android cannot draw an arbitrary icon colour). */
        fun closestTo(color: Color): LauncherIcon = entries.minBy { v ->
            val dr = v.background.red - color.red
            val dg = v.background.green - color.green
            val db = v.background.blue - color.blue
            dr * dr * 0.3f + dg * dg * 0.59f + db * db * 0.11f
        }
    }
}

/** Switches the enabled launcher alias. Launchers may take a moment to show the new icon. */
class LauncherIconSwitcher(private val context: Context) {
    private val pm = context.packageManager

    private fun component(icon: LauncherIcon) = ComponentName(context.packageName, context.packageName + icon.alias)

    /** The enabled variant, or the default when none was changed (the manifest enables MIDNIGHT). */
    fun current(): LauncherIcon = LauncherIcon.entries.firstOrNull { icon ->
        when (pm.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.MIDNIGHT
            else -> false
        }
    } ?: LauncherIcon.MIDNIGHT

    /** Enables [icon] first, then disables the others, so a launcher entry exists at every step. */
    fun apply(icon: LauncherIcon) {
        pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        LauncherIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
