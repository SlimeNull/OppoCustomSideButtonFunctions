package com.slimenull.customsidebuttonfunctions.model

enum class CursorLongPressAction(val title: String) {
    NONE("不执行任何操作"),
    REPEAT("连续移动"),
    EDGE("移动到首尾")
}

const val DEFAULT_CURSOR_LONG_PRESS_MS = 500L
const val DEFAULT_CURSOR_REPEAT_INTERVAL_MS = 32L
const val MIN_CURSOR_LONG_PRESS_MS = 100L
const val MAX_CURSOR_LONG_PRESS_MS = 1_000L
const val MIN_CURSOR_REPEAT_INTERVAL_MS = 16L
const val MAX_CURSOR_REPEAT_INTERVAL_MS = 200L
