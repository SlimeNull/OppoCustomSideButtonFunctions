package com.slimenull.customsidebuttonfunctions.xposed

import com.slimenull.customsidebuttonfunctions.data.SettingsStore
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.onXposedFailure
import android.view.KeyEvent
import io.github.libxposed.api.XposedInterface

internal object SettingsReader {
    private var api: XposedInterface? = null

    fun bind(value: XposedInterface) {
        api = value
    }

    fun load(): AppSettings {
        val prefs = runCatching {
            api?.getRemotePreferences(SettingsStore.PREFS_NAME)
        }.onXposedFailure("load shared preferences").getOrNull()
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
