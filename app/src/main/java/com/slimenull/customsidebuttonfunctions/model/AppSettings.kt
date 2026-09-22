package com.slimenull.customsidebuttonfunctions.model

const val DEFAULT_UNKNOWN_MORSE_TOAST = "未知操作序列"

data class AppSettings(
    val enabled: Boolean = true,
    val keyCode: Int = 735,
    val inputDevicePath: String = "/dev/input/event0",
    val longPressMs: Long = 300L,
    val doubleClickWindowMs: Long = 300L,
    val singleAction: ActionType = ActionType.CYCLE_RINGER,
    val doubleAction: ActionType = ActionType.NONE,
    val longAction: ActionType = ActionType.SCREENSHOT,
    val operationMode: OperationMode = OperationMode.SIMPLE,
    val morseLongPressMs: Long = 300L,
    val morseCommandWindowMs: Long = 300L,
    val morsePressVibrationEnabled: Boolean = false,
    val morseLongVibrationEnabled: Boolean = true,
    val morseImmediateExecutionEnabled: Boolean = true,
    val morseBindings: List<MorseBinding> = emptyList(),
    val singleCustom: CustomActionSettings = CustomActionSettings(),
    val doubleCustom: CustomActionSettings = CustomActionSettings(),
    val longCustom: CustomActionSettings = CustomActionSettings(),
    val vibrationEnabled: Boolean = true,
    val toastEnabled: Boolean = false,
    val toastText: String = "侧键操作已执行",
    val unknownMorseFeedbackEnabled: Boolean = false,
    val unknownMorseToastText: String = DEFAULT_UNKNOWN_MORSE_TOAST,
    val wakeScreenWhenOff: Boolean = false,
    val cursorControlMode: CursorControlMode = CursorControlMode.DISABLED,
    val cursorLongPressAction: CursorLongPressAction = CursorLongPressAction.NONE,
    val cursorLongPressMs: Long = DEFAULT_CURSOR_LONG_PRESS_MS,
    val cursorRepeatIntervalMs: Long = DEFAULT_CURSOR_REPEAT_INTERVAL_MS
)
