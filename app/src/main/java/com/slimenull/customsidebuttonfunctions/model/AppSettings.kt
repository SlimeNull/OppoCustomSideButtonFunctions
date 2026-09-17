package com.slimenull.customsidebuttonfunctions.model

data class AppSettings(
    val enabled: Boolean = true,
    val keyCode: Int = 735,
    val inputDevicePath: String = "/dev/input/event0",
    val longPressMs: Long = 650L,
    val doubleClickWindowMs: Long = 280L,
    val singleAction: ActionType = ActionType.CYCLE_RINGER,
    val doubleAction: ActionType = ActionType.NONE,
    val longAction: ActionType = ActionType.SCREENSHOT,
    val singleCustom: CustomActionSettings = CustomActionSettings(),
    val doubleCustom: CustomActionSettings = CustomActionSettings(),
    val longCustom: CustomActionSettings = CustomActionSettings(),
    val vibrationEnabled: Boolean = true,
    val vibrationDurationMs: Long = 40L,
    val toastEnabled: Boolean = true,
    val toastText: String = "侧键操作已执行",
    val wakeScreenWhenOff: Boolean = true
)
