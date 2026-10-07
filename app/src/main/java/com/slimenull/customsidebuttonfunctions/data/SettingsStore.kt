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
import com.slimenull.customsidebuttonfunctions.model.SideKeyCombinationAction
import io.github.libxposed.service.XposedService
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Stores a complete settings snapshot in LibXposed's shared remote file. */
object SettingsStore {
    const val PREFS_NAME = "settings"
    const val REMOTE_FILE_NAME = "settings.json"

    private val remoteService: XposedService?
        get() = XposedServiceManager.xposedService

    @Volatile
    private var pendingRemoteSettings: AppSettings? = null
    @Volatile
    private var applicationContext: Context? = null
    @Volatile
    private var remoteSyncInitialized = false

    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEY_CODE = "key_code"
    private const val KEY_INPUT_PATH = "input_device_path"
    private const val KEY_LONG_PRESS_MS = "long_press_ms"
    private const val KEY_DOUBLE_WINDOW_MS = "double_click_window_ms"
    private const val KEY_SINGLE_ACTION = "single_action"
    private const val KEY_SINGLE_WAKE_SCREEN = "single_wake_screen"
    private const val KEY_SINGLE_VIBRATION = "single_vibration_enabled"
    private const val KEY_DOUBLE_ACTION = "double_action"
    private const val KEY_DOUBLE_WAKE_SCREEN = "double_wake_screen"
    private const val KEY_DOUBLE_VIBRATION = "double_vibration_enabled"
    private const val KEY_LONG_ACTION = "long_action"
    private const val KEY_LONG_WAKE_SCREEN = "long_wake_screen"
    private const val KEY_LONG_VIBRATION = "long_vibration_enabled"
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
    private const val KEY_COMBINATION_ENABLED = "combination_enabled"
    private const val KEY_SIDE_VOLUME_UP_ACTION = "side_volume_up_action"
    private const val KEY_SIDE_VOLUME_DOWN_ACTION = "side_volume_down_action"
    private const val KEY_SIDE_POWER_ACTION = "side_power_action"

    @Synchronized
    fun initializeRemoteStorage(context: Context? = null) {
        context?.let { applicationContext = it.applicationContext }
        if (remoteSyncInitialized) return
        remoteSyncInitialized = true
        XposedServiceManager.registerBindListener { service ->
            val context = applicationContext ?: return@registerBindListener
            val localPreferences = preferences(context)
            val settings = pendingRemoteSettings
                ?: if (localPreferences.all.isNotEmpty()) {
                    fromPreferences(localPreferences)
                } else {
                    migrateEmptyLocalSettings(service, context)
                }
                ?: return@registerBindListener
            if (pendingRemoteSettings != null) writeLocal(context, settings)
            if (writeRemote(service, settings)) pendingRemoteSettings = null
        }
    }

    fun load(context: Context): AppSettings {
        initializeRemoteStorage(context)
        val localPreferences = preferences(context)
        val settings = fromPreferences(localPreferences)
        if (localPreferences.all.isNotEmpty() || remoteService != null) {
            if (!writeLocal(context, settings) || !writeRemote(settings)) {
                pendingRemoteSettings = settings
            } else {
                pendingRemoteSettings = null
            }
        }
        return settings
    }

    fun save(settings: AppSettings) {
        initializeRemoteStorage()
        val context = applicationContext
        if (context == null || !writeLocal(context, settings)) {
            pendingRemoteSettings = settings
            return
        }
        if (writeRemote(settings)) pendingRemoteSettings = null else pendingRemoteSettings = settings
    }

    private fun migrateEmptyLocalSettings(service: XposedService, context: Context): AppSettings? {
        return try {
            readRemote(service)?.let { settings ->
                writeLocal(context, settings)
                return settings
            }

            // Preserve settings written by the previous 102 preference implementation.
            val oldRemote = runCatching { service.getRemotePreferences(PREFS_NAME) }
                .onXposedFailure("read legacy remote preferences")
                .getOrNull()
            if (oldRemote?.all?.isNotEmpty() == true) {
                return fromPreferences(oldRemote).also { writeLocal(context, it) }
            }

            fromPreferences(preferences(context)).also { writeLocal(context, it) }
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

        XposedBridge.log("CustomSideButtonFunctions: remote settings file saved.")
        true
    }.onXposedFailure("save remote settings file").getOrDefault(false)

    private fun readRemote(service: XposedService?): AppSettings? {
        if (service == null) {
            XposedBridge.log("CustomSideButtonFunctions: service not initialized, can not read remote settings")
            return null
        }
        return try {

            val descriptor = service.openRemoteFile(REMOTE_FILE_NAME)
            if (descriptor.statSize <= 0L) {
                descriptor.close()
                XposedBridge.log("CustomSideButtonFunctions: remote settings file is empty.")
                null
            } else {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    fromJson(JSONObject(input.readBytes().toString(StandardCharsets.UTF_8)))
                }
            }
        } catch (_: FileNotFoundException) {
            XposedBridge.log("CustomSideButtonFunctions: remote settings file not found.")
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

    private fun writeLocal(context: Context, settings: AppSettings): Boolean = runCatching {
        preferences(context).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_KEY_CODE, settings.keyCode)
            .putString(KEY_INPUT_PATH, settings.inputDevicePath)
            .putLong(KEY_LONG_PRESS_MS, settings.longPressMs)
            .putLong(KEY_DOUBLE_WINDOW_MS, settings.doubleClickWindowMs)
            .putString(KEY_SINGLE_ACTION, settings.singleAction.name)
            .putBoolean(KEY_SINGLE_WAKE_SCREEN, settings.singleWakeScreen)
            .putBoolean(KEY_SINGLE_VIBRATION, settings.singleVibrationEnabled)
            .putString(KEY_DOUBLE_ACTION, settings.doubleAction.name)
            .putBoolean(KEY_DOUBLE_WAKE_SCREEN, settings.doubleWakeScreen)
            .putBoolean(KEY_DOUBLE_VIBRATION, settings.doubleVibrationEnabled)
            .putString(KEY_LONG_ACTION, settings.longAction.name)
            .putBoolean(KEY_LONG_WAKE_SCREEN, settings.longWakeScreen)
            .putBoolean(KEY_LONG_VIBRATION, settings.longVibrationEnabled)
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
            .putBoolean(KEY_TOAST_ENABLED, settings.toastEnabled)
            .putString(KEY_TOAST_TEXT, settings.toastText)
            .putBoolean(KEY_UNKNOWN_MORSE_FEEDBACK, settings.unknownMorseFeedbackEnabled)
            .putString(KEY_UNKNOWN_MORSE_TOAST_TEXT, settings.unknownMorseToastText)
            .putBoolean(KEY_WAKE_SCREEN, settings.wakeScreenWhenOff)
            .putString(KEY_CURSOR_CONTROL_MODE, settings.cursorControlMode.name)
            .putString(KEY_CURSOR_LONG_PRESS_ACTION, settings.cursorLongPressAction.name)
            .putLong(KEY_CURSOR_LONG_PRESS_MS, settings.cursorLongPressMs)
            .putLong(KEY_CURSOR_REPEAT_INTERVAL_MS, settings.cursorRepeatIntervalMs)
            .putBoolean(KEY_COMBINATION_ENABLED, settings.combinationEnabled)
            .putString(KEY_SIDE_VOLUME_UP_ACTION, settings.sideVolumeUpAction.name)
            .putString(KEY_SIDE_VOLUME_DOWN_ACTION, settings.sideVolumeDownAction.name)
            .putString(KEY_SIDE_POWER_ACTION, settings.sidePowerAction.name)
            .commit()
    }.onXposedFailure("save local settings").getOrDefault(false)

    fun fromPreferences(preferences: SharedPreferences): AppSettings = AppSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        keyCode = preferences.getInt(KEY_KEY_CODE, 735),
        inputDevicePath = preferences.getString(KEY_INPUT_PATH, "/dev/input/event0") ?: "/dev/input/event0",
        longPressMs = preferences.getLong(KEY_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        doubleClickWindowMs = preferences.getLong(KEY_DOUBLE_WINDOW_MS, 300L).coerceIn(100L, 800L),
        singleAction = action(preferences.getString(KEY_SINGLE_ACTION, null)),
        singleWakeScreen = preferences.getBoolean(
            KEY_SINGLE_WAKE_SCREEN,
            preferences.getString(KEY_SINGLE_ACTION, null)?.let { it == ActionType.CYCLE_RINGER.name } ?: true
        ),
        singleVibrationEnabled = preferences.getBoolean(KEY_SINGLE_VIBRATION, false),
        doubleAction = action(preferences.getString(KEY_DOUBLE_ACTION, null), ActionType.NONE),
        doubleWakeScreen = preferences.getBoolean(
            KEY_DOUBLE_WAKE_SCREEN,
            preferences.getString(KEY_DOUBLE_ACTION, null) == ActionType.CYCLE_RINGER.name
        ),
        doubleVibrationEnabled = preferences.getBoolean(KEY_DOUBLE_VIBRATION, false),
        longAction = action(preferences.getString(KEY_LONG_ACTION, null), ActionType.SCREENSHOT),
        longWakeScreen = preferences.getBoolean(
            KEY_LONG_WAKE_SCREEN,
            preferences.getString(KEY_LONG_ACTION, null) == ActionType.CYCLE_RINGER.name
        ),
        longVibrationEnabled = preferences.getBoolean(KEY_LONG_VIBRATION, false),
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
            .coerceIn(MIN_CURSOR_REPEAT_INTERVAL_MS, MAX_CURSOR_REPEAT_INTERVAL_MS),
        combinationEnabled = preferences.getBoolean(KEY_COMBINATION_ENABLED, false),
        sideVolumeUpAction = enumValue(
            preferences.getString(KEY_SIDE_VOLUME_UP_ACTION, null),
            SideKeyCombinationAction.NONE,
            "side volume-up combination action"
        ),
        sideVolumeDownAction = enumValue(
            preferences.getString(KEY_SIDE_VOLUME_DOWN_ACTION, null),
            SideKeyCombinationAction.NONE,
            "side volume-down combination action"
        ),
        sidePowerAction = enumValue(
            preferences.getString(KEY_SIDE_POWER_ACTION, null),
            SideKeyCombinationAction.NONE,
            "side power combination action"
        )
    )

    /** Serialize the complete settings snapshot used by the LibXposed remote-file bridge. */
    internal fun toJson(settings: AppSettings): JSONObject = JSONObject().apply {
        put(KEY_ENABLED, settings.enabled)
        put(KEY_KEY_CODE, settings.keyCode)
        put(KEY_INPUT_PATH, settings.inputDevicePath)
        put(KEY_LONG_PRESS_MS, settings.longPressMs)
        put(KEY_DOUBLE_WINDOW_MS, settings.doubleClickWindowMs)
        put(KEY_SINGLE_ACTION, settings.singleAction.name)
        put(KEY_SINGLE_WAKE_SCREEN, settings.singleWakeScreen)
        put(KEY_SINGLE_VIBRATION, settings.singleVibrationEnabled)
        put(KEY_DOUBLE_ACTION, settings.doubleAction.name)
        put(KEY_DOUBLE_WAKE_SCREEN, settings.doubleWakeScreen)
        put(KEY_DOUBLE_VIBRATION, settings.doubleVibrationEnabled)
        put(KEY_LONG_ACTION, settings.longAction.name)
        put(KEY_LONG_WAKE_SCREEN, settings.longWakeScreen)
        put(KEY_LONG_VIBRATION, settings.longVibrationEnabled)
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
        put(KEY_COMBINATION_ENABLED, settings.combinationEnabled)
        put(KEY_SIDE_VOLUME_UP_ACTION, settings.sideVolumeUpAction.name)
        put(KEY_SIDE_VOLUME_DOWN_ACTION, settings.sideVolumeDownAction.name)
        put(KEY_SIDE_POWER_ACTION, settings.sidePowerAction.name)
    }

    /** Deserialize a remote-file snapshot, applying the same bounds and defaults as preferences. */
    internal fun fromJson(json: JSONObject): AppSettings = AppSettings(
        enabled = json.optBoolean(KEY_ENABLED, true),
        keyCode = json.optInt(KEY_KEY_CODE, 735),
        inputDevicePath = json.optString(KEY_INPUT_PATH, "/dev/input/event0"),
        longPressMs = json.optLong(KEY_LONG_PRESS_MS, 300L).coerceIn(100L, 800L),
        doubleClickWindowMs = json.optLong(KEY_DOUBLE_WINDOW_MS, 300L).coerceIn(100L, 800L),
        singleAction = action(optionalString(json, KEY_SINGLE_ACTION)),
        singleWakeScreen = json.optBoolean(
            KEY_SINGLE_WAKE_SCREEN,
            optionalString(json, KEY_SINGLE_ACTION)?.let { it == ActionType.CYCLE_RINGER.name } ?: true
        ),
        singleVibrationEnabled = json.optBoolean(KEY_SINGLE_VIBRATION, false),
        doubleAction = action(optionalString(json, KEY_DOUBLE_ACTION), ActionType.NONE),
        doubleWakeScreen = json.optBoolean(
            KEY_DOUBLE_WAKE_SCREEN,
            optionalString(json, KEY_DOUBLE_ACTION) == ActionType.CYCLE_RINGER.name
        ),
        doubleVibrationEnabled = json.optBoolean(KEY_DOUBLE_VIBRATION, false),
        longAction = action(optionalString(json, KEY_LONG_ACTION), ActionType.SCREENSHOT),
        longWakeScreen = json.optBoolean(
            KEY_LONG_WAKE_SCREEN,
            optionalString(json, KEY_LONG_ACTION) == ActionType.CYCLE_RINGER.name
        ),
        longVibrationEnabled = json.optBoolean(KEY_LONG_VIBRATION, false),
        operationMode = enumValue(optionalString(json, KEY_OPERATION_MODE), OperationMode.SIMPLE, "operation mode"),
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
        cursorControlMode = enumValue(optionalString(json, KEY_CURSOR_CONTROL_MODE), CursorControlMode.DISABLED, "cursor control mode"),
        cursorLongPressAction = enumValue(
            optionalString(json, KEY_CURSOR_LONG_PRESS_ACTION),
            CursorLongPressAction.NONE,
            "cursor long-press action"
        ),
        cursorLongPressMs = json.optLong(KEY_CURSOR_LONG_PRESS_MS, DEFAULT_CURSOR_LONG_PRESS_MS)
            .coerceIn(MIN_CURSOR_LONG_PRESS_MS, MAX_CURSOR_LONG_PRESS_MS),
        cursorRepeatIntervalMs = json.optLong(KEY_CURSOR_REPEAT_INTERVAL_MS, DEFAULT_CURSOR_REPEAT_INTERVAL_MS)
            .coerceIn(MIN_CURSOR_REPEAT_INTERVAL_MS, MAX_CURSOR_REPEAT_INTERVAL_MS),
        combinationEnabled = json.optBoolean(KEY_COMBINATION_ENABLED, false),
        sideVolumeUpAction = enumValue(
            optionalString(json, KEY_SIDE_VOLUME_UP_ACTION),
            SideKeyCombinationAction.NONE,
            "side volume-up combination action"
        ),
        sideVolumeDownAction = enumValue(
            optionalString(json, KEY_SIDE_VOLUME_DOWN_ACTION),
            SideKeyCombinationAction.NONE,
            "side volume-down combination action"
        ),
        sidePowerAction = enumValue(
            optionalString(json, KEY_SIDE_POWER_ACTION),
            SideKeyCombinationAction.NONE,
            "side power combination action"
        )
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
        val common = enumValue(optionalString(value, "common_action"), CommonAction.WECHAT_PAY, "common action")
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

    private fun optionalString(json: JSONObject, key: String): String? =
        if (json.has(key) && !json.isNull(key)) json.optString(key) else null

    private fun action(value: String?, fallback: ActionType = ActionType.CYCLE_RINGER): ActionType =
        value?.let { runCatching { ActionType.valueOf(it) }.onXposedFailure("parse action").getOrNull() } ?: fallback

    private fun writeCustom(editor: SharedPreferences.Editor, prefix: String, custom: CustomActionSettings) {
        editor.putString("${prefix}common_action", custom.commonAction.name)
            .putString("${prefix}activity_package", custom.activityPackage)
            .putString("${prefix}activity_class", custom.activityClass)
            .putString("${prefix}activity_action", custom.activityAction)
            .putBoolean("${prefix}activity_small_window", custom.launchInSmallWindow)
            .putString("${prefix}url_scheme", custom.urlScheme)
            .putString("${prefix}xiaobu_shortcut_id", custom.xiaobuShortcutId)
            .putString("${prefix}shell_command", custom.shellCommand)
            .putBoolean("${prefix}shell_toast_enabled", custom.shellToastEnabled)
    }

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
                put("wake_screen", binding.wakeScreen)
                put("vibration_enabled", binding.vibrationEnabled)
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
                ?: return@mapNotNull null
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
                ),
                wakeScreen = entry.optBoolean("wake_screen", action == ActionType.CYCLE_RINGER),
                vibrationEnabled = entry.optBoolean("vibration_enabled", false)
            )
        }.distinctBy { it.sequence }
    }
}
