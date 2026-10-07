package com.slimenull.customsidebuttonfunctions.data

import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import com.slimenull.customsidebuttonfunctions.onXposedFailure
import com.slimenull.customsidebuttonfunctions.xposed.XposedBridge
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import com.slimenull.customsidebuttonfunctions.model.CursorLongPressAction
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_CURSOR_LONG_PRESS_MS
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_CURSOR_REPEAT_INTERVAL_MS
import com.slimenull.customsidebuttonfunctions.model.MAX_CURSOR_LONG_PRESS_MS
import com.slimenull.customsidebuttonfunctions.model.MAX_CURSOR_REPEAT_INTERVAL_MS
import com.slimenull.customsidebuttonfunctions.model.MIN_CURSOR_LONG_PRESS_MS
import com.slimenull.customsidebuttonfunctions.model.MIN_CURSOR_REPEAT_INTERVAL_MS
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_UNKNOWN_MORSE_TOAST
import com.slimenull.customsidebuttonfunctions.model.MorseBinding
import com.slimenull.customsidebuttonfunctions.model.OperationMode
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Stores a complete settings snapshot in LibXposed's shared remote file. */
object SettingsStore {
    const val PREFS_NAME = "settings"
    const val REMOTE_FILE_NAME = "settings.json"

    @Volatile
    private var remoteService: XposedService? = null
    @Volatile
    private var pendingRemoteSettings: AppSettings? = null
    @Volatile
    private var applicationContext: Context? = null
    private var remoteBridgeInitialized = false

    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEY_CODE = "key_code"
    private const val KEY_INPUT_PATH = "input_device_path"
    private const val KEY_LONG_PRESS_MS = "long_press_ms"
    private const val KEY_DOUBLE_WINDOW_MS = "double_click_window_ms"
    private const val KEY_SINGLE_ACTION = "single_action"
    private const val KEY_DOUBLE_ACTION = "double_action"
    private const val KEY_LONG_ACTION = "long_action"
    private const val KEY_OPERATION_MODE = "operation_mode"
    private const val KEY_MORSE_LONG_PRESS_MS = "morse_long_press_ms"
    private const val KEY_MORSE_COMMAND_WINDOW_MS = "morse_command_window_ms"
    private const val KEY_MORSE_PRESS_VIBRATION = "morse_press_vibration"
    private const val KEY_MORSE_LONG_VIBRATION = "morse_long_vibration"
    private const val KEY_MORSE_IMMEDIATE_EXECUTION = "morse_immediate_execution"
    private const val KEY_MORSE_BINDINGS = "morse_bindings"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_TOAST_ENABLED = "toast_enabled"
    private const val KEY_TOAST_TEXT = "toast_text"
    private const val KEY_UNKNOWN_MORSE_FEEDBACK = "unknown_morse_feedback"
    private const val KEY_UNKNOWN_MORSE_TOAST_TEXT = "unknown_morse_toast_text"
    private const val KEY_WAKE_SCREEN = "wake_screen_when_off"
    private const val KEY_CURSOR_CONTROL_MODE = "cursor_control_mode"
    private const val KEY_CURSOR_LONG_PRESS_ACTION = "cursor_long_press_action"
    private const val KEY_CURSOR_LONG_PRESS_MS = "cursor_long_press_ms"
    private const val KEY_CURSOR_REPEAT_INTERVAL_MS = "cursor_repeat_interval_ms"

    @Synchronized
    fun initializeRemoteStorage(context: Context? = null) {
        context?.let { applicationContext = it.applicationContext }
        if (remoteBridgeInitialized) return
        remoteBridgeInitialized = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                remoteService = service
                val pending = pendingRemoteSettings ?: migrateSettingsIfRemoteEmpty(service) ?: return
                if (writeRemote(service, pending)) pendingRemoteSettings = null
            }

            override fun onServiceDied(service: XposedService) {
                if (remoteService === service) remoteService = null
            }
        })
    }

    fun load(context: Context): AppSettings {
        initializeRemoteStorage(context)
        return readRemote(remoteService) ?: fromPreferences(preferences(context))
    }

    fun save(settings: AppSettings) {
        initializeRemoteStorage()
        if (!writeRemote(settings)) pendingRemoteSettings = settings
    }

    private fun migrateSettingsIfRemoteEmpty(service: XposedService): AppSettings? {
        return try {
            if (readRemote(service) != null) return null

            // Preserve settings written by the previous 102 preference implementation.
            val oldRemote = runCatching { service.getRemotePreferences(PREFS_NAME) }
                .onXposedFailure("read legacy remote preferences")
                .getOrNull()
            if (oldRemote?.all?.isNotEmpty() == true) return fromPreferences(oldRemote)

            val context = applicationContext ?: return null
            fromPreferences(preferences(context))
        } catch (error: Throwable) {
            XposedBridge.log("CustomSideButtonFunctions: migrate existing settings failed: ${error.message}")
            XposedBridge.log(error)
            null
        }
    }

    private fun writeRemote(settings: AppSettings): Boolean = remoteService?.let { writeRemote(it, settings) } == true

    private fun writeRemote(service: XposedService, settings: AppSettings): Boolean = runCatching {
        val descriptor = service.openRemoteFile(REMOTE_FILE_NAME)
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            output.channel.truncate(0)
            output.write(toJson(settings).toString().toByteArray(StandardCharsets.UTF_8))
            output.channel.force(true)
        }
        true
    }.onXposedFailure("save remote settings file").getOrDefault(false)

    private fun readRemote(service: XposedService?): AppSettings? {
        if (service == null) return null
        return try {
            val descriptor = service.openRemoteFile(REMOTE_FILE_NAME)
            if (descriptor.statSize <= 0L) {
                descriptor.close()
                null
            } else {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    fromJson(JSONObject(input.readBytes().toString(StandardCharsets.UTF_8)))
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

    private fun preferences(context: Context): SharedPreferences = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun fromPreferences(preferences: SharedPreferences): AppSettings = AppSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        keyCode = preferences.getInt(KEY_KEY_CODE, 735),
        inputDevicePath = preferences.getString(KEY_INPUT_PATH, "/dev/input/event0") ?: "/dev/input/event0",
        longPressMs = preferences.getLong(KEY_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        doubleClickWindowMs = preferences.getLong(KEY_DOUBLE_WINDOW_MS, 300L).coerceIn(100L, 800L),
        singleAction = action(preferences.getString(KEY_SINGLE_ACTION, null)),
        doubleAction = action(preferences.getString(KEY_DOUBLE_ACTION, null), ActionType.NONE),
        longAction = action(preferences.getString(KEY_LONG_ACTION, null), ActionType.SCREENSHOT),
        operationMode = preferences.getString(KEY_OPERATION_MODE, null)
            ?.let { runCatching { OperationMode.valueOf(it) }.onXposedFailure("parse operation mode").getOrNull() }
            ?: OperationMode.SIMPLE,
        morseLongPressMs = preferences.getLong(KEY_MORSE_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        morseCommandWindowMs = preferences.getLong(KEY_MORSE_COMMAND_WINDOW_MS, 300L).coerceIn(100L, 800L),
        morsePressVibrationEnabled = preferences.getBoolean(KEY_MORSE_PRESS_VIBRATION, false),
        morseLongVibrationEnabled = preferences.getBoolean(KEY_MORSE_LONG_VIBRATION, true),
        morseImmediateExecutionEnabled = preferences.getBoolean(KEY_MORSE_IMMEDIATE_EXECUTION, true),
        morseBindings = readMorseBindings(preferences.getString(KEY_MORSE_BINDINGS, null)),
        singleCustom = readCustom(preferences, "single_"),
        doubleCustom = readCustom(preferences, "double_"),
        longCustom = readCustom(preferences, "long_"),
        vibrationEnabled = preferences.getBoolean(KEY_VIBRATION_ENABLED, true),
        toastEnabled = preferences.getBoolean(KEY_TOAST_ENABLED, false),
        toastText = preferences.getString(KEY_TOAST_TEXT, "侧键操作已执行") ?: "侧键操作已执行",
        unknownMorseFeedbackEnabled = preferences.getBoolean(KEY_UNKNOWN_MORSE_FEEDBACK, false),
        unknownMorseToastText = preferences.getString(KEY_UNKNOWN_MORSE_TOAST_TEXT, DEFAULT_UNKNOWN_MORSE_TOAST)
            ?: DEFAULT_UNKNOWN_MORSE_TOAST,
        wakeScreenWhenOff = preferences.getBoolean(KEY_WAKE_SCREEN, false),
        cursorControlMode = preferences.getString(KEY_CURSOR_CONTROL_MODE, null)
            ?.let { runCatching { CursorControlMode.valueOf(it) }.onXposedFailure("parse cursor control mode").getOrNull() }
            ?: CursorControlMode.DISABLED,
        cursorLongPressAction = preferences.getString(KEY_CURSOR_LONG_PRESS_ACTION, null)
            ?.let {
                runCatching { CursorLongPressAction.valueOf(it) }
                    .onXposedFailure("parse cursor long-press action")
                    .getOrNull()
            }
            ?: CursorLongPressAction.NONE,
        cursorLongPressMs = preferences.getLong(KEY_CURSOR_LONG_PRESS_MS, DEFAULT_CURSOR_LONG_PRESS_MS)
            .coerceIn(MIN_CURSOR_LONG_PRESS_MS, MAX_CURSOR_LONG_PRESS_MS),
        cursorRepeatIntervalMs = preferences.getLong(KEY_CURSOR_REPEAT_INTERVAL_MS, DEFAULT_CURSOR_REPEAT_INTERVAL_MS)
            .coerceIn(MIN_CURSOR_REPEAT_INTERVAL_MS, MAX_CURSOR_REPEAT_INTERVAL_MS)
    )

    /** Serialize the complete settings snapshot used by the LibXposed remote-file bridge. */
    internal fun toJson(settings: AppSettings): JSONObject = JSONObject().apply {
        put(KEY_ENABLED, settings.enabled)
        put(KEY_KEY_CODE, settings.keyCode)
        put(KEY_INPUT_PATH, settings.inputDevicePath)
        put(KEY_LONG_PRESS_MS, settings.longPressMs)
        put(KEY_DOUBLE_WINDOW_MS, settings.doubleClickWindowMs)
        put(KEY_SINGLE_ACTION, settings.singleAction.name)
        put(KEY_DOUBLE_ACTION, settings.doubleAction.name)
        put(KEY_LONG_ACTION, settings.longAction.name)
        put(KEY_OPERATION_MODE, settings.operationMode.name)
        put(KEY_MORSE_LONG_PRESS_MS, settings.morseLongPressMs)
        put(KEY_MORSE_COMMAND_WINDOW_MS, settings.morseCommandWindowMs)
        put(KEY_MORSE_PRESS_VIBRATION, settings.morsePressVibrationEnabled)
        put(KEY_MORSE_LONG_VIBRATION, settings.morseLongVibrationEnabled)
        put(KEY_MORSE_IMMEDIATE_EXECUTION, settings.morseImmediateExecutionEnabled)
        put(KEY_MORSE_BINDINGS, JSONArray(writeMorseBindings(settings.morseBindings)))
        put("single_custom", customToJson(settings.singleCustom))
        put("double_custom", customToJson(settings.doubleCustom))
        put("long_custom", customToJson(settings.longCustom))
        put(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
        put(KEY_TOAST_ENABLED, settings.toastEnabled)
        put(KEY_TOAST_TEXT, settings.toastText)
        put(KEY_UNKNOWN_MORSE_FEEDBACK, settings.unknownMorseFeedbackEnabled)
        put(KEY_UNKNOWN_MORSE_TOAST_TEXT, settings.unknownMorseToastText)
        put(KEY_WAKE_SCREEN, settings.wakeScreenWhenOff)
        put(KEY_CURSOR_CONTROL_MODE, settings.cursorControlMode.name)
        put(KEY_CURSOR_LONG_PRESS_ACTION, settings.cursorLongPressAction.name)
        put(KEY_CURSOR_LONG_PRESS_MS, settings.cursorLongPressMs)
        put(KEY_CURSOR_REPEAT_INTERVAL_MS, settings.cursorRepeatIntervalMs)
    }

    /** Deserialize a remote-file snapshot, applying the same bounds and defaults as preferences. */
    internal fun fromJson(json: JSONObject): AppSettings = AppSettings(
        enabled = json.optBoolean(KEY_ENABLED, true),
        keyCode = json.optInt(KEY_KEY_CODE, 735),
        inputDevicePath = json.optString(KEY_INPUT_PATH, "/dev/input/event0"),
        longPressMs = json.optLong(KEY_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        doubleClickWindowMs = json.optLong(KEY_DOUBLE_WINDOW_MS, 300L).coerceIn(100L, 800L),
        singleAction = action(json.optString(KEY_SINGLE_ACTION, null)),
        doubleAction = action(json.optString(KEY_DOUBLE_ACTION, null), ActionType.NONE),
        longAction = action(json.optString(KEY_LONG_ACTION, null), ActionType.SCREENSHOT),
        operationMode = enumValue(json.optString(KEY_OPERATION_MODE, null), OperationMode.SIMPLE, "operation mode"),
        morseLongPressMs = json.optLong(KEY_MORSE_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        morseCommandWindowMs = json.optLong(KEY_MORSE_COMMAND_WINDOW_MS, 300L).coerceIn(100L, 800L),
        morsePressVibrationEnabled = json.optBoolean(KEY_MORSE_PRESS_VIBRATION, false),
        morseLongVibrationEnabled = json.optBoolean(KEY_MORSE_LONG_VIBRATION, true),
        morseImmediateExecutionEnabled = json.optBoolean(KEY_MORSE_IMMEDIATE_EXECUTION, true),
        morseBindings = readMorseBindings(json.optJSONArray(KEY_MORSE_BINDINGS)?.toString()),
        singleCustom = customFromJson(json.optJSONObject("single_custom")),
        doubleCustom = customFromJson(json.optJSONObject("double_custom")),
        longCustom = customFromJson(json.optJSONObject("long_custom")),
        vibrationEnabled = json.optBoolean(KEY_VIBRATION_ENABLED, true),
        toastEnabled = json.optBoolean(KEY_TOAST_ENABLED, false),
        toastText = json.optString(KEY_TOAST_TEXT, "侧键操作已执行"),
        unknownMorseFeedbackEnabled = json.optBoolean(KEY_UNKNOWN_MORSE_FEEDBACK, false),
        unknownMorseToastText = json.optString(KEY_UNKNOWN_MORSE_TOAST_TEXT, DEFAULT_UNKNOWN_MORSE_TOAST),
        wakeScreenWhenOff = json.optBoolean(KEY_WAKE_SCREEN, false),
        cursorControlMode = enumValue(json.optString(KEY_CURSOR_CONTROL_MODE, null), CursorControlMode.DISABLED, "cursor control mode"),
        cursorLongPressAction = enumValue(
            json.optString(KEY_CURSOR_LONG_PRESS_ACTION, null),
            CursorLongPressAction.NONE,
            "cursor long-press action"
        ),
        cursorLongPressMs = json.optLong(KEY_CURSOR_LONG_PRESS_MS, DEFAULT_CURSOR_LONG_PRESS_MS)
            .coerceIn(MIN_CURSOR_LONG_PRESS_MS, MAX_CURSOR_LONG_PRESS_MS),
        cursorRepeatIntervalMs = json.optLong(KEY_CURSOR_REPEAT_INTERVAL_MS, DEFAULT_CURSOR_REPEAT_INTERVAL_MS)
            .coerceIn(MIN_CURSOR_REPEAT_INTERVAL_MS, MAX_CURSOR_REPEAT_INTERVAL_MS)
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T, description: String): T =
        value?.let { runCatching { enumValueOf<T>(it) }.onXposedFailure("parse $description").getOrNull() } ?: fallback

    private fun customToJson(custom: CustomActionSettings): JSONObject = JSONObject().apply {
        put("common_action", custom.commonAction.name)
        put("activity_package", custom.activityPackage)
        put("activity_class", custom.activityClass)
        put("activity_action", custom.activityAction)
        put("activity_small_window", custom.launchInSmallWindow)
        put("url_scheme", custom.urlScheme)
        put("xiaobu_shortcut_id", custom.xiaobuShortcutId)
        put("shell_command", custom.shellCommand)
        put("shell_toast_enabled", custom.shellToastEnabled)
    }

    private fun customFromJson(json: JSONObject?): CustomActionSettings {
        val value = json ?: JSONObject()
        val common = enumValue(value.optString("common_action", null), CommonAction.WECHAT_PAY, "common action")
        return CustomActionSettings(
            commonAction = common,
            activityPackage = value.optString("activity_package", ""),
            activityClass = value.optString("activity_class", ""),
            activityAction = value.optString("activity_action", ""),
            launchInSmallWindow = value.optBoolean("activity_small_window", false),
            urlScheme = value.optString("url_scheme", ""),
            xiaobuShortcutId = value.optString("xiaobu_shortcut_id", ""),
            shellCommand = value.optString("shell_command", ""),
            shellToastEnabled = value.optBoolean("shell_toast_enabled", true)
        )
    }

    private fun action(value: String?, fallback: ActionType = ActionType.CYCLE_RINGER): ActionType =
        value?.let { runCatching { ActionType.valueOf(it) }.onXposedFailure("parse action").getOrNull() } ?: fallback

    private fun readCustom(preferences: SharedPreferences, prefix: String): CustomActionSettings {
        val common = preferences.getString("${prefix}common_action", null)
            ?.let { runCatching { CommonAction.valueOf(it) }.onXposedFailure("parse common action").getOrNull() }
            ?: CommonAction.WECHAT_PAY
        return CustomActionSettings(
            commonAction = common,
            activityPackage = preferences.getString("${prefix}activity_package", "") ?: "",
            activityClass = preferences.getString("${prefix}activity_class", "") ?: "",
            activityAction = preferences.getString("${prefix}activity_action", "") ?: "",
            launchInSmallWindow = preferences.getBoolean("${prefix}activity_small_window", false),
            urlScheme = preferences.getString("${prefix}url_scheme", "") ?: "",
            xiaobuShortcutId = preferences.getString("${prefix}xiaobu_shortcut_id", "") ?: "",
            shellCommand = preferences.getString("${prefix}shell_command", "") ?: "",
            shellToastEnabled = preferences.getBoolean("${prefix}shell_toast_enabled", true)
        )
    }

    private fun writeMorseBindings(bindings: List<MorseBinding>): String = JSONArray().apply {
        bindings.forEach { binding ->
            put(JSONObject().apply {
                put("sequence", binding.sequence)
                put("action", binding.action.name)
                put("custom", JSONObject().apply {
                    put("common_action", binding.custom.commonAction.name)
                    put("activity_package", binding.custom.activityPackage)
                    put("activity_class", binding.custom.activityClass)
                    put("activity_action", binding.custom.activityAction)
                    put("activity_small_window", binding.custom.launchInSmallWindow)
                    put("url_scheme", binding.custom.urlScheme)
                    put("xiaobu_shortcut_id", binding.custom.xiaobuShortcutId)
                    put("shell_command", binding.custom.shellCommand)
                    put("shell_toast_enabled", binding.custom.shellToastEnabled)
                })
            })
        }
    }.toString()

    private fun readMorseBindings(value: String?): List<MorseBinding> {
        val array = runCatching { JSONArray(value ?: "[]") }
            .onXposedFailure("parse Morse bindings JSON")
            .getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val sequence = entry.optString("sequence")
            if (sequence.isEmpty() || sequence.any { it != '0' && it != '1' }) return@mapNotNull null
            val action = runCatching { ActionType.valueOf(entry.optString("action")) }
                .onXposedFailure("parse Morse action")
                .getOrNull()
                ?.takeIf { it != ActionType.NONE } ?: return@mapNotNull null
            val custom = entry.optJSONObject("custom") ?: JSONObject()
            val common = runCatching { CommonAction.valueOf(custom.optString("common_action")) }
                .onXposedFailure("parse Morse common action")
                .getOrNull()
                ?: CommonAction.WECHAT_PAY
            MorseBinding(
                sequence = sequence,
                action = action,
                custom = CustomActionSettings(
                    commonAction = common,
                    activityPackage = custom.optString("activity_package"),
                    activityClass = custom.optString("activity_class"),
                    activityAction = custom.optString("activity_action"),
                    launchInSmallWindow = custom.optBoolean("activity_small_window", false),
                    urlScheme = custom.optString("url_scheme"),
                    xiaobuShortcutId = custom.optString("xiaobu_shortcut_id"),
                    shellCommand = custom.optString("shell_command"),
                    shellToastEnabled = custom.optBoolean("shell_toast_enabled", true)
                )
            )
        }.distinctBy { it.sequence }
    }
}
