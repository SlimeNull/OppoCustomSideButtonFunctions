package com.slimenull.customsidebuttonfunctions.xposed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

/** Copies a Xiaobu shortcut tag when the ColorOS shortcut editor opens one. */
internal object ShortcutsHook {
    private const val PACKAGE = "com.coloros.shortcuts"
    private val installed = AtomicBoolean(false)
    private var context: Context? = null

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE || !installed.compareAndSet(false, true)) return
        runCatching {
            val mainActivity = XposedHelpers.findClass(
                "com.coloros.shortcuts.ui.MainActivity",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                mainActivity,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        context = param.thisObject as? Context
                        XposedBridge.log("CustomSideButtonFunctions: shortcuts context captured")
                    }
                }
            )

            val shortcutClass = XposedHelpers.findClass(
                "com.coloros.shortcuts.framework.db.entity.Shortcut",
                lpparam.classLoader
            )
            val ownerClass = XposedHelpers.findClass("k8.a", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                ownerClass,
                "i",
                shortcutClass,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val shortcut = param.args[0] ?: return
                        runCatching {
                            val tag = XposedHelpers.getObjectField(shortcut, "tag") as? String
                            val description = XposedHelpers.getObjectField(shortcut, "des") as? String
                            XposedBridge.log("CustomSideButtonFunctions: shortcut des=$description tag=$tag")
                            if (!tag.isNullOrBlank()) copyToClipboard(tag)
                        }.onFailure {
                            XposedBridge.log("CustomSideButtonFunctions: failed to read shortcut: ${it.message}")
                        }
                    }
                }
            )
            XposedBridge.log("CustomSideButtonFunctions: shortcuts hooks installed")
        }.onFailure {
            XposedBridge.log("CustomSideButtonFunctions: shortcuts hook failed: ${it.message}")
        }
    }

    private fun copyToClipboard(tag: String) {
        val currentContext = context ?: return
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut Tag", tag))
                Toast.makeText(currentContext, "快捷方式 ID 已复制到剪贴板", Toast.LENGTH_LONG).show()
                XposedBridge.log("CustomSideButtonFunctions: shortcut tag copied")
            }.onFailure {
                XposedBridge.log("CustomSideButtonFunctions: shortcut clipboard failed: ${it.message}")
            }
        }
    }
}
