package com.slimenull.customsidebuttonfunctions.model

/** Actions exposed to every gesture. KEEP enum names stable: they are persisted in SharedPreferences. */
enum class ActionType(val title: String, val description: String) {
    NONE("不执行操作", "禁用此手势"),
    CYCLE_RINGER("切换响铃 / 振动 / 静音", "按响铃、振动、静音顺序循环"),
    TOGGLE_DND("开启或关闭免打扰", "在开启和关闭之间切换"),
    CAMERA("打开相机", "启动系统默认相机"),
    FLASHLIGHT("切换手电筒", "打开或关闭摄像头手电筒"),
    SCREENSHOT("系统截屏", "调用系统截屏服务"),
    COMMON_FUNCTION("常用功能", "微信、支付宝和 ColorOS 快捷功能"),
    XIAOBU_SHORTCUT("执行小布快捷指令", "通过小布快捷指令 ID 执行"),
    CUSTOM_ACTIVITY("自定义 Activity", "启动指定应用的 Activity"),
    CUSTOM_URL("自定义 Url Scheme", "通过系统解析器打开 Url"),
    SHELL_COMMAND("执行 Shell 指令", "在系统框架进程中执行命令")
}
