package com.slimenull.customsidebuttonfunctions.xposed

import android.os.ParcelFileDescriptor
import com.slimenull.customsidebuttonfunctions.data.SettingsStore
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import android.view.KeyEvent
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject

internal object SettingsReader {
    @Volatile
    private var api: XposedInterface? = null
    @Volatile
    private var latestSettings = AppSettings()

    fun bind(value: XposedInterface) {
        api = value
    }

    fun load(): AppSettings {
        readRemote()?.let { latestSettings = it }
        return latestSettings
    }

    private fun readRemote(): AppSettings? {
        val module = api ?: return null
        return try {
            val descriptor = module.openRemoteFile(SettingsStore.REMOTE_FILE_NAME)
            if (descriptor.statSize <= 0L) {
                descriptor.close()
                null
            } else {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    SettingsStore.fromJson(JSONObject(input.readBytes().toString(StandardCharsets.UTF_8)))
                }
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (error: Throwable) {
            XposedBridge.log("CustomSideButtonFunctions: load remote settings file failed: ${error.message}")
            XposedBridge.log(error)
            null
        }
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
