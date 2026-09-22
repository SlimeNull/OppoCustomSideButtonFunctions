package com.slimenull.customsidebuttonfunctions.model

/** Direction used when physical volume keys are repurposed as text-cursor keys. */
enum class CursorControlMode(val title: String) {
    DISABLED("关闭"),
    VOLUME_UP_LEFT("上键左移"),
    VOLUME_UP_RIGHT("上键右移")
}
