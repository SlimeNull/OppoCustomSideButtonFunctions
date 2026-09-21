package com.slimenull.customsidebuttonfunctions.data

import android.content.Context
import android.content.SharedPreferences
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_UNKNOWN_MORSE_TOAST
import com.slimenull.customsidebuttonfunctions.model.MorseBinding
import com.slimenull.customsidebuttonfunctions.model.OperationMode
import org.json.JSONArray
import org.json.JSONObject

/** A deliberately simple preference format so XSharedPreferences can consume it from system_server. */
object SettingsStore {
    const val PREFS_NAME = "settings"

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
    private const val KEY_VIBRATION_DURATION_MS = "vibration_duration_ms"
    private const val KEY_TOAST_ENABLED = "toast_enabled"
    private const val KEY_TOAST_TEXT = "toast_text"
    private const val KEY_UNKNOWN_MORSE_FEEDBACK = "unknown_morse_feedback"
    private const val KEY_UNKNOWN_MORSE_TOAST_TEXT = "unknown_morse_toast_text"
    private const val KEY_WAKE_SCREEN = "wake_screen_when_off"

    fun load(context: Context): AppSettings {
        val preferences = preferences(context)
        return fromPreferences(preferences)
    }

    fun save(context: Context, settings: AppSettings) {
        // Device-protected storage lets a system_server hook read settings before first unlock.
        val devicePreferences = preferences(context)
        write(devicePreferences, settings)

        // Keep a credential-protected copy for older Xposed builds that resolve the legacy path.
        val legacyPreferences = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        }.getOrElse {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        write(legacyPreferences, settings)
    }

    private fun preferences(context: Context): SharedPreferences = runCatching {
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
    }.getOrElse {
        // Android N+ may reject MODE_WORLD_READABLE; LSPosed can still reload this file
        // through its privileged preference bridge in that case.
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun write(preferences: SharedPreferences, settings: AppSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_KEY_CODE, settings.keyCode)
            .putString(KEY_INPUT_PATH, settings.inputDevicePath)
            .putLong(KEY_LONG_PRESS_MS, settings.longPressMs)
            .putLong(KEY_DOUBLE_WINDOW_MS, settings.doubleClickWindowMs)
            .putString(KEY_SINGLE_ACTION, settings.singleAction.name)
            .putString(KEY_DOUBLE_ACTION, settings.doubleAction.name)
            .putString(KEY_LONG_ACTION, settings.longAction.name)
            .putString(KEY_OPERATION_MODE, settings.operationMode.name)
            .putLong(KEY_MORSE_LONG_PRESS_MS, settings.morseLongPressMs)
            .putLong(KEY_MORSE_COMMAND_WINDOW_MS, settings.morseCommandWindowMs)
            .putBoolean(KEY_MORSE_PRESS_VIBRATION, settings.morsePressVibrationEnabled)
            .putBoolean(KEY_MORSE_LONG_VIBRATION, settings.morseLongVibrationEnabled)
            .putBoolean(KEY_MORSE_IMMEDIATE_EXECUTION, settings.morseImmediateExecutionEnabled)
            .putString(KEY_MORSE_BINDINGS, writeMorseBindings(settings.morseBindings))
            .also { writeCustom(it, "single_", settings.singleCustom) }
            .also { writeCustom(it, "double_", settings.doubleCustom) }
            .also { writeCustom(it, "long_", settings.longCustom) }
            .putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            .putLong(KEY_VIBRATION_DURATION_MS, settings.vibrationDurationMs)
            .putBoolean(KEY_TOAST_ENABLED, settings.toastEnabled)
            .putString(KEY_TOAST_TEXT, settings.toastText)
            .putBoolean(KEY_UNKNOWN_MORSE_FEEDBACK, settings.unknownMorseFeedbackEnabled)
            .putString(KEY_UNKNOWN_MORSE_TOAST_TEXT, settings.unknownMorseToastText)
            .putBoolean(KEY_WAKE_SCREEN, settings.wakeScreenWhenOff)
            // commit() ensures XSharedPreferences.reload() sees a just-saved gesture immediately.
            .commit()
    }

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
            ?.let { runCatching { OperationMode.valueOf(it) }.getOrNull() } ?: OperationMode.SIMPLE,
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
        vibrationDurationMs = preferences.getLong(KEY_VIBRATION_DURATION_MS, 50L).coerceIn(1L, 2_000L),
        toastEnabled = preferences.getBoolean(KEY_TOAST_ENABLED, false),
        toastText = preferences.getString(KEY_TOAST_TEXT, "侧键操作已执行") ?: "侧键操作已执行",
        unknownMorseFeedbackEnabled = preferences.getBoolean(KEY_UNKNOWN_MORSE_FEEDBACK, false),
        unknownMorseToastText = preferences.getString(KEY_UNKNOWN_MORSE_TOAST_TEXT, DEFAULT_UNKNOWN_MORSE_TOAST)
            ?: DEFAULT_UNKNOWN_MORSE_TOAST,
        wakeScreenWhenOff = preferences.getBoolean(KEY_WAKE_SCREEN, false)
    )

    private fun action(value: String?, fallback: ActionType = ActionType.CYCLE_RINGER): ActionType =
        value?.let { runCatching { ActionType.valueOf(it) }.getOrNull() } ?: fallback

    private fun writeCustom(editor: SharedPreferences.Editor, prefix: String, custom: CustomActionSettings) {
        editor.putString("${prefix}common_action", custom.commonAction.name)
            .putString("${prefix}activity_package", custom.activityPackage)
            .putString("${prefix}activity_class", custom.activityClass)
            .putString("${prefix}activity_action", custom.activityAction)
            .putString("${prefix}url_scheme", custom.urlScheme)
            .putString("${prefix}xiaobu_shortcut_id", custom.xiaobuShortcutId)
            .putString("${prefix}shell_command", custom.shellCommand)
    }

    private fun readCustom(preferences: SharedPreferences, prefix: String): CustomActionSettings {
        val common = preferences.getString("${prefix}common_action", null)
            ?.let { runCatching { CommonAction.valueOf(it) }.getOrNull() }
            ?: CommonAction.WECHAT_PAY
        return CustomActionSettings(
            commonAction = common,
            activityPackage = preferences.getString("${prefix}activity_package", "") ?: "",
            activityClass = preferences.getString("${prefix}activity_class", "") ?: "",
            activityAction = preferences.getString("${prefix}activity_action", "") ?: "",
            urlScheme = preferences.getString("${prefix}url_scheme", "") ?: "",
            xiaobuShortcutId = preferences.getString("${prefix}xiaobu_shortcut_id", "") ?: "",
            shellCommand = preferences.getString("${prefix}shell_command", "") ?: ""
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
                    put("url_scheme", binding.custom.urlScheme)
                    put("xiaobu_shortcut_id", binding.custom.xiaobuShortcutId)
                    put("shell_command", binding.custom.shellCommand)
                })
            })
        }
    }.toString()

    private fun readMorseBindings(value: String?): List<MorseBinding> {
        val array = runCatching { JSONArray(value ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val sequence = entry.optString("sequence")
            if (sequence.isEmpty() || sequence.any { it != '0' && it != '1' }) return@mapNotNull null
            val action = runCatching { ActionType.valueOf(entry.optString("action")) }.getOrNull()
                ?.takeIf { it != ActionType.NONE } ?: return@mapNotNull null
            val custom = entry.optJSONObject("custom") ?: JSONObject()
            val common = runCatching { CommonAction.valueOf(custom.optString("common_action")) }.getOrNull()
                ?: CommonAction.WECHAT_PAY
            MorseBinding(
                sequence = sequence,
                action = action,
                custom = CustomActionSettings(
                    commonAction = common,
                    activityPackage = custom.optString("activity_package"),
                    activityClass = custom.optString("activity_class"),
                    activityAction = custom.optString("activity_action"),
                    urlScheme = custom.optString("url_scheme"),
                    xiaobuShortcutId = custom.optString("xiaobu_shortcut_id"),
                    shellCommand = custom.optString("shell_command")
                )
            )
        }.distinctBy { it.sequence }
    }
}
