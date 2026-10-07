package com.slimenull.customsidebuttonfunctions.model

data class MorseBinding(
    val sequence: String,
    val action: ActionType,
    val custom: CustomActionSettings = CustomActionSettings(),
    val wakeScreen: Boolean = false,
    val vibrationEnabled: Boolean = false
)
