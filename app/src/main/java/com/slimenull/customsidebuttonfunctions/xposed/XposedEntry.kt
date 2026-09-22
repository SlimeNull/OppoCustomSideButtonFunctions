package com.slimenull.customsidebuttonfunctions.xposed

import android.view.KeyEvent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import com.slimenull.customsidebuttonfunctions.model.CursorLongPressAction
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
    private val lock = Any()
    private var callbackHandler: Handler? = null
    private data class Press(
        val keyCode: Int,
        val mode: CursorControlMode,
        val action: CursorLongPressAction,
        val repeatIntervalMs: Long,
        val service: InputMethodService,
        val handler: Handler,
        var longRunnable: Runnable? = null,
        var repeatRunnable: Runnable? = null
    )
    private val presses = mutableMapOf<Int, Press>()

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
                        val event = param.args.getOrNull(1) as? KeyEvent ?: return
                        val settings = SettingsReader.load()
                        val mode = settings.cursorControlMode
                        if (!settings.enabled) return
                        if (mode == CursorControlMode.DISABLED) return
                        val service = param.thisObject as? InputMethodService ?: return
                        if (!runCatching { service.isInputViewShown }.getOrDefault(false)) return
                        if (event.repeatCount == 0) {
                            startPress(keyCode, mode, settings, service)
                        }
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
                        val handled = finishPress(keyCode)
                        val settings = SettingsReader.load()
                        val mode = settings.cursorControlMode
                        if (!settings.enabled) return
                        if (mode == CursorControlMode.DISABLED) return
                        val service = param.thisObject as? InputMethodService ?: return
                        if (handled && runCatching { service.isInputViewShown }.getOrDefault(false)) {
                            param.setResult(true)
                        }
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

    private fun startPress(
        keyCode: Int,
        mode: CursorControlMode,
        settings: AppSettings,
        service: InputMethodService
    ) {
        val handler = getCallbackHandler(service)
        val press = Press(
            keyCode = keyCode,
            mode = mode,
            action = settings.cursorLongPressAction,
            repeatIntervalMs = settings.cursorRepeatIntervalMs,
            service = service,
            handler = handler
        )
        synchronized(lock) {
            cancelPressLocked(keyCode)
            presses[keyCode] = press
            service.sendDownUpKeyEvents(cursorKeyCode(keyCode, mode))
            val longRunnable = Runnable { triggerLongPress(press) }
            press.longRunnable = longRunnable
            press.handler.postDelayed(longRunnable, settings.cursorLongPressMs)
        }
    }

    private fun triggerLongPress(press: Press) {
        synchronized(lock) {
            if (presses[press.keyCode] !== press) return
            when (press.action) {
                CursorLongPressAction.NONE -> Unit
                CursorLongPressAction.REPEAT -> {
                    val repeatRunnable = object : Runnable {
                        override fun run() {
                            synchronized(lock) {
                                if (presses[press.keyCode] !== press) return
                                press.service.sendDownUpKeyEvents(cursorKeyCode(press.keyCode, press.mode))
                                press.handler.postDelayed(this, press.repeatIntervalMs)
                            }
                        }
                    }
                    press.repeatRunnable = repeatRunnable
                    press.handler.postDelayed(repeatRunnable, press.repeatIntervalMs)
                }
                CursorLongPressAction.EDGE -> {
                    press.service.sendDownUpKeyEvents(edgeKeyCode(press.keyCode, press.mode))
                }
            }
        }
    }

    private fun finishPress(keyCode: Int): Boolean {
        synchronized(lock) {
            val press = presses.remove(keyCode) ?: return false
            cancelPressLocked(press)
            return true
        }
    }

    private fun cancelPressLocked(keyCode: Int) {
        presses.remove(keyCode)?.let(::cancelPressLocked)
    }

    private fun cancelPressLocked(press: Press) {
        press.longRunnable?.let(press.handler::removeCallbacks)
        press.repeatRunnable?.let(press.handler::removeCallbacks)
    }

    private fun getCallbackHandler(service: InputMethodService): Handler = synchronized(lock) {
        callbackHandler ?: Handler(service.mainLooper).also {
            callbackHandler = it
        }
    }

    private fun edgeKeyCode(volumeKeyCode: Int, mode: CursorControlMode): Int {
        val moveLeft = if (volumeKeyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mode == CursorControlMode.VOLUME_UP_LEFT
        } else {
            mode == CursorControlMode.VOLUME_UP_RIGHT
        }
        return if (moveLeft) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
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
