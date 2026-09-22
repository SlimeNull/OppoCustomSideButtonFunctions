package com.slimenull.customsidebuttonfunctions.xposed

import android.view.KeyEvent
import android.inputmethodservice.InputMethodService
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import com.slimenull.customsidebuttonfunctions.model.OperationMode
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** Xposed entry point for the framework key hook and shortcut editor. */
class XposedEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        LegacyImeCursorHook.install()
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> SideKeyModule.install(lpparam)
            "com.coloros.shortcuts" -> ShortcutsHook.install(lpparam)
        }
    }
}

/** Fallback for ROMs where the focused InputMethodService receives volume keys directly. */
private object LegacyImeCursorHook {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.inputmethodservice.InputMethodService", null, "onKeyDown",
                Int::class.javaPrimitiveType!!, KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val keyCode = param.args.getOrNull(0) as? Int ?: return
                        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return
                        val settings = SettingsReader.load()
                        val mode = settings.cursorControlMode
                        if (!settings.enabled) return
                        if (mode == CursorControlMode.DISABLED) return
                        val service = param.thisObject as? InputMethodService ?: return
                        if (!runCatching { service.isInputViewShown }.getOrDefault(false)) return
                        service.sendDownUpKeyEvents(cursorKeyCode(keyCode, mode))
                        param.setResult(true)
                    }
                }
            )
            XposedHelpers.findAndHookMethod(
                "android.inputmethodservice.InputMethodService", null, "onKeyUp",
                Int::class.javaPrimitiveType!!, KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val keyCode = param.args.getOrNull(0) as? Int ?: return
                        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return
                        val settings = SettingsReader.load()
                        val mode = settings.cursorControlMode
                        if (!settings.enabled) return
                        if (mode == CursorControlMode.DISABLED) return
                        val service = param.thisObject as? InputMethodService ?: return
                        if (runCatching { service.isInputViewShown }.getOrDefault(false)) param.setResult(true)
                    }
                }
            )
            XposedBridge.log("CustomSideButtonFunctions: installed InputMethodService cursor fallback")
        }.onFailure {
            XposedBridge.log("CustomSideButtonFunctions: InputMethodService cursor fallback unavailable: ${it.message}")
        }
    }

    private fun cursorKeyCode(volumeKeyCode: Int, mode: CursorControlMode): Int {
        val volumeUpMovesLeft = mode == CursorControlMode.VOLUME_UP_LEFT
        return if (volumeKeyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volumeUpMovesLeft) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (volumeUpMovesLeft) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        }
    }
}

internal object SideKeyModule {
    private const val TAG = "CustomSideButtonFunctions"
    private var installed = false

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (installed) return
        installed = true

        val controller = SideKeyController()
        val actionExecutor = ActionExecutor()
        CursorControlController().install(lpparam)
        // Oplus routes its hardware shortcut through this vendor strategy before AOSP policy.
        val hookInstalled = installOplusStrategyHook(lpparam, controller, actionExecutor)
            || installPhoneWindowHook(lpparam, controller, actionExecutor)
        if (!hookInstalled) {
            XposedBridge.log("$TAG: framework key hook unavailable, starting raw input fallback")
            RawInputEventReader.start(controller, actionExecutor)
        }
    }

    private fun installOplusStrategyHook(
        lpparam: XC_LoadPackage.LoadPackageParam,
        controller: SideKeyController,
        executor: ActionExecutor
    ): Boolean {
        return try {
            val strategyClass = XposedHelpers.findClass(
                "com.android.server.policy.StrategyActionButtonKeyLaunchApp",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                strategyClass,
                "actionInterceptKeyBeforeQueueing",
                KeyEvent::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as? KeyEvent ?: return
                        val settings = SettingsReader.load()
                        if (!settings.enabled || settings.operationMode == OperationMode.DISABLED) {
                            controller.cancel()
                            return
                        }
                        if (!SettingsReader.matches(event, settings)) return

                        val down = (param.args[3] as? Boolean) ?: (event.action == KeyEvent.ACTION_DOWN)
                        val interactive = (param.args[4] as? Boolean) ?: true
                        executor.updateStrategy(param.thisObject)
                        executor.updateContext(resolveContext(param.thisObject))
                        if (event.action == KeyEvent.ACTION_DOWN && down) {
                            XposedBridge.log("$TAG: side key down keyCode=${event.keyCode} scanCode=${event.scanCode} interactive=$interactive")
                            controller.onDown(settings, executor, interactive)
                            // Prevent ColorOS from launching its original shortcut as well.
                            param.setResult(null)
                        } else if (event.action == KeyEvent.ACTION_UP && !down) {
                            XposedBridge.log("$TAG: side key up keyCode=${event.keyCode} scanCode=${event.scanCode} interactive=$interactive")
                            controller.onUp(settings, executor, interactive)
                            param.setResult(null)
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: hooked Oplus StrategyActionButtonKeyLaunchApp")
            true
        } catch (error: Throwable) {
            XposedBridge.log("$TAG: Oplus strategy hook unavailable: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
    }

    private fun installPhoneWindowHook(
        lpparam: XC_LoadPackage.LoadPackageParam,
        controller: SideKeyController,
        executor: ActionExecutor
    ): Boolean {
        val classNames = listOf(
            "com.android.server.policy.PhoneWindowManager",
            "com.android.server.policy.PhoneWindowManagerExt"
        )
        for (className in classNames) {
            try {
                XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    "interceptKeyBeforeQueueing",
                    KeyEvent::class.java,
                    Int::class.javaPrimitiveType!!,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val event = param.args[0] as? KeyEvent ?: return
                            val settings = SettingsReader.load()
                            if (!settings.enabled || settings.operationMode == OperationMode.DISABLED) {
                                controller.cancel()
                                return
                            }
                            if (!SettingsReader.matches(event, settings)) return
                            val context = runCatching {
                                XposedHelpers.getObjectField(param.thisObject, "mContext") as? android.content.Context
                            }.getOrNull()
                            executor.updateContext(context)
                            when (event.action) {
                                KeyEvent.ACTION_DOWN -> controller.onDown(settings, executor, true)
                                KeyEvent.ACTION_UP -> controller.onUp(settings, executor, true)
                            }
                            // Consume the matching key only while custom handling is enabled.
                            param.setResult(0)
                        }
                    }
                )
                XposedBridge.log("$TAG: hooked $className.interceptKeyBeforeQueueing")
                return true
            } catch (error: Throwable) {
                XposedBridge.log("$TAG: cannot hook $className: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
        return false
    }

    private fun resolveContext(instance: Any): android.content.Context? = runCatching {
        XposedHelpers.getObjectField(instance, "mContext") as? android.content.Context
    }.getOrNull() ?: runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getMethod("currentApplication").invoke(null) as? android.content.Context
    }.getOrNull()
}
