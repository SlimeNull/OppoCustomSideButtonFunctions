package com.slimenull.customsidebuttonfunctions.xposed

import com.slimenull.customsidebuttonfunctions.data.SettingsStore
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import android.view.KeyEvent
import de.robv.android.xposed.XSharedPreferences

internal object SettingsReader {
    private const val PACKAGE = "com.slimenull.customsidebuttonfunctions"
    private var preferences: XSharedPreferences? = null

    fun load(): AppSettings {
        val prefs = runCatching {
            (preferences ?: XSharedPreferences(PACKAGE, SettingsStore.PREFS_NAME).also { preferences = it }).apply {
                makeWorldReadable()
                reload()
            }
        }.getOrNull()
        return prefs?.let(SettingsStore::fromPreferences) ?: AppSettings()
    }

    fun peekKeyCode(): Int = load().keyCode

    /** Vendor buttons often expose the Linux BTN_TRIGGER_HAPPY value as scanCode, not keyCode. */
    fun matches(event: KeyEvent): Boolean {
        return matches(event, load())
    }

    fun matches(event: KeyEvent, settings: AppSettings): Boolean {
        val configuredCode = settings.keyCode
        return event.keyCode == configuredCode || event.scanCode == configuredCode
    }
}
