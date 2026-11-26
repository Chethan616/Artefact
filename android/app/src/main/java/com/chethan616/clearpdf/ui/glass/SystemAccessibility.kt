package com.chethan616.clearpdf.ui.glass

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What the system's accessibility settings ask of the app's look. They decide only the defaults: a
 * choice made in the app's own Settings always stands (#158).
 */
object SystemAccessibility {
    /** Developer options or Accessibility ▸ "Remove animations": the animator scale is 0. */
    fun animationsOff(context: Context): Boolean =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    /** "High contrast text", or (Android 14+) the system contrast raised to medium or high. */
    fun highContrast(context: Context): Boolean {
        // not a public constant, but a plain readable secure setting on every Android this app runs on
        val text = runCatching { Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false)
        if (text) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ui = context.getSystemService(UiModeManager::class.java)
            if (ui != null && runCatching { ui.contrast }.getOrDefault(0f) >= 0.5f) return true
        }
        return false
    }
}
