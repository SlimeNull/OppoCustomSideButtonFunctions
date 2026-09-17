package com.slimenull.customsidebuttonfunctions.model

data class CustomActionSettings(
    val commonAction: CommonAction = CommonAction.WECHAT_PAY,
    val activityPackage: String = "",
    val activityClass: String = "",
    val activityAction: String = "",
    val urlScheme: String = "",
    val xiaobuShortcutId: String = "",
    val shellCommand: String = ""
)
