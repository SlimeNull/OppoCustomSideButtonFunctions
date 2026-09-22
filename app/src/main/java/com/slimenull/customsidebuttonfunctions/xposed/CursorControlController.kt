package com.slimenull.customsidebuttonfunctions.xposed

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Converts volume key events to DPAD events while a real IME window is visible.
 *
 * The conversion lives in system_server so it still works on ColorOS builds that
 * consume volume events before the focused InputMethodService sees them.
 */
internal class CursorControlController {
    private companion object {
        const val TAG = "CustomSideButtonFunctions"
        const val IME_VISIBLE = 2
        const val CURSOR_CHORD_DELAY_MS = 180L
    }

    private val cursorLock = Any()
    private var context: Context? = null
    private var policyHandler: Handler? = null
    private var inputMethodManagerService: Any? = null
    @Volatile private var imeWindowVisible = false
    private var imeVisibilityReadErrorLogged = false

    private var powerKeyPressed = false
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var cursorChordActive = false
    private var volumeUpMode: CursorControlMode? = null
    private var volumeDownMode: CursorControlMode? = null
    private var pendingCursorDown: KeyEvent? = null
    private var pendingCursorGeneration = 0

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookPolicy(lpparam)
        hookImeVisibility(lpparam)
        hookMediaVolumePaths(lpparam)
        XposedBridge.log("$TAG: installed volume-key cursor-control hooks")
    }

    private fun hookPolicy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classNames = listOf(
            "com.android.server.policy.PhoneWindowManager",
            "com.android.server.policy.PhoneWindowManagerExt"
        )
        for (className in classNames) {
            if (runCatching {
                    XposedHelpers.findAndHookMethod(
                        className,
                        lpparam.classLoader,
                        "interceptKeyBeforeQueueing",
                        KeyEvent::class.java,
                        Int::class.javaPrimitiveType!!,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                capturePolicyServices(param.thisObject)
                                val event = param.args.firstOrNull() as? KeyEvent ?: return
                                if (isReplayedVolumeEvent(event)) return

                                val replay = trackCursorChord(event)
                                if (replay != null) injectReplayedVolumeEvent(replay)
                                if (handleSystemVolumeKey(event)) {
                                    param.setResult(0)
                                }
                            }
                        }
                    )
                    true
                }.getOrDefault(false)
            ) {
                XposedBridge.log("$TAG: hooked $className volume-key cursor path")
                hookPolicyLifecycle(className, lpparam)
                return
            }
        }
        XposedBridge.log("$TAG: PhoneWindowManager volume hook unavailable")
    }

    private fun hookPolicyLifecycle(
        className: String,
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        val policyClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: return
        runCatching {
            XposedBridge.hookAllMethods(policyClass, "initKeyCombinationRules", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    capturePolicyServices(param.thisObject)
                }
            })
        }
    }

    private fun hookImeVisibility(lpparam: XC_LoadPackage.LoadPackageParam) {
        val imeClass = XposedHelpers.findClassIfExists(
            "com.android.server.inputmethod.InputMethodManagerService",
            lpparam.classLoader
        ) ?: return
        runCatching {
            XposedBridge.hookAllConstructors(imeClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    inputMethodManagerService = param.thisObject
                }
            })
            XposedBridge.hookAllMethods(imeClass, "setImeWindowStatusLocked", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    inputMethodManagerService = param.thisObject
                    val visibility = param.args.firstOrNull { it is Int } as? Int ?: return
                    imeWindowVisible = visibility and IME_VISIBLE != 0
                }
            })
        }.onFailure {
            XposedBridge.log("$TAG: InputMethodManagerService visibility hook unavailable: ${it.message}")
        }
    }

    private fun hookMediaVolumePaths(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookAllMethodsIfPresent(
            "android.media.session.MediaSessionLegacyHelper",
            lpparam.classLoader,
            "sendVolumeKeyEvent"
        ) { param ->
            if (param.args.size >= 3 && param.args[0] is KeyEvent && param.args[2] == true &&
                handleSystemVolumeKey(param.args[0] as KeyEvent)
            ) {
                param.setResult(null)
            }
        }
        hookAllMethodsIfPresent(
            "com.android.server.media.MediaSessionService\$SessionManagerImpl",
            lpparam.classLoader,
            "dispatchVolumeKeyEvent"
        ) { param ->
            if (param.args.size >= 6 && param.args[3] is KeyEvent && param.args[5] == true &&
                handleSystemVolumeKey(param.args[3] as KeyEvent)
            ) {
                param.setResult(null)
            }
        }
    }

    private fun hookAllMethodsIfPresent(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        before: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return
        runCatching {
            XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) = before(param)
            })
        }.onFailure {
            XposedBridge.log("$TAG: $className.$methodName hook unavailable: ${it.message}")
        }
    }

    private fun capturePolicyServices(policy: Any) {
        context = runCatching {
            XposedHelpers.getObjectField(policy, "mContext") as? Context
        }.getOrNull() ?: context
        policyHandler = runCatching {
            XposedHelpers.getObjectField(policy, "mHandler") as? Handler
        }.getOrNull() ?: policyHandler
    }

    private fun trackCursorChord(event: KeyEvent): KeyEvent? {
        val keyCode = event.keyCode
        val action = event.action
        synchronized(cursorLock) {
            when {
                keyCode == KeyEvent.KEYCODE_POWER -> {
                    if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        powerKeyPressed = true
                        if (volumeUpPressed || volumeDownPressed) {
                            cursorChordActive = true
                            return takePendingCursorDownLocked()
                        }
                    } else if (action == KeyEvent.ACTION_UP) {
                        powerKeyPressed = false
                    }
                }
                keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        if (!volumeUpPressed && !volumeDownPressed) cursorChordActive = false
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpPressed = true
                        else volumeDownPressed = true
                        if (powerKeyPressed || (volumeUpPressed && volumeDownPressed)) {
                            cursorChordActive = true
                            return takePendingCursorDownLocked()
                        }
                    } else if (action == KeyEvent.ACTION_UP) {
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpPressed = false
                        else volumeDownPressed = false
                    }
                }
            }
        }
        return null
    }

    private fun handleSystemVolumeKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        var pendingUp: KeyEvent? = null
        var pendingUpMode: CursorControlMode? = null
        var currentHandlingMode: CursorControlMode? = null
        var injectCurrent = false
        synchronized(cursorLock) {
            val handlingMode = modeForKeyLocked(keyCode)
            currentHandlingMode = handlingMode
            if (cursorChordActive) {
                if (event.action == KeyEvent.ACTION_UP && handlingMode != null) setModeForKeyLocked(keyCode, null)
                if (event.action == KeyEvent.ACTION_UP && !volumeUpPressed && !volumeDownPressed) cursorChordActive = false
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val mode = currentMode()
                if (!isCursorControlAvailable(mode)) return false
                setModeForKeyLocked(keyCode, mode)
                val handler = policyHandler ?: Handler(Looper.getMainLooper())
                scheduleCursorDownLocked(event, handler)
            } else if (handlingMode == null) {
                return false
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                injectCurrent = true
            } else if (event.action == KeyEvent.ACTION_UP) {
                pendingUp = takePendingCursorDownLocked(keyCode)
                pendingUpMode = handlingMode
                injectCurrent = true
                setModeForKeyLocked(keyCode, null)
                if (!volumeUpPressed && !volumeDownPressed) cursorChordActive = false
            }
        }
        if (pendingUp != null && pendingUpMode != null) injectCursorKeyEvent(pendingUp!!, pendingUpMode!!)
        if (injectCurrent) injectCursorKeyEvent(event, currentHandlingMode ?: currentMode())
        return true
    }

    private fun currentMode(): CursorControlMode {
        val settings = SettingsReader.load()
        return if (settings.enabled) settings.cursorControlMode else CursorControlMode.DISABLED
    }

    private fun isCursorControlAvailable(mode: CursorControlMode): Boolean {
        if (mode == CursorControlMode.DISABLED) return false
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return false
        val power = currentContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null && !power.isInteractive) return false
        val telephony = currentContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephony != null && runCatching { telephony.callState }.getOrDefault(TelephonyManager.CALL_STATE_IDLE) != TelephonyManager.CALL_STATE_IDLE) return false
        val audio = currentContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION) return false
        val keyguard = currentContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return false
        return isImeVisible()
    }

    private fun isImeVisible(): Boolean {
        val service = inputMethodManagerService ?: return imeWindowVisible
        return runCatching {
            val userId = XposedHelpers.getIntField(service, "mCurrentImeUserId")
            val userData = XposedHelpers.callMethod(service, "getUserData", userId)
            val binding = XposedHelpers.getObjectField(userData, "mBindingController")
            val visibility = XposedHelpers.callMethod(binding, "getImeWindowVis") as Int
            imeWindowVisible = visibility and IME_VISIBLE != 0
            imeWindowVisible
        }.getOrElse {
            if (!imeVisibilityReadErrorLogged) {
                imeVisibilityReadErrorLogged = true
                XposedBridge.log("$TAG: failed to read current IME visibility: ${it.message}")
            }
            imeWindowVisible
        }
    }

    private fun scheduleCursorDownLocked(event: KeyEvent, handler: Handler) {
        val generation = ++pendingCursorGeneration
        pendingCursorDown = KeyEvent(event)
        handler.postDelayed({ dispatchPendingCursorDown(generation) }, CURSOR_CHORD_DELAY_MS)
    }

    private fun dispatchPendingCursorDown(generation: Int) {
        val event: KeyEvent
        val mode: CursorControlMode
        synchronized(cursorLock) {
            val pending = pendingCursorDown ?: return
            mode = modeForKeyLocked(pending.keyCode) ?: return
            if (generation != pendingCursorGeneration || cursorChordActive) return
            event = pending
            pendingCursorDown = null
        }
        injectCursorKeyEvent(event, mode)
    }

    private fun takePendingCursorDownLocked(keyCode: Int): KeyEvent? {
        if (pendingCursorDown?.keyCode != keyCode) return null
        return takePendingCursorDownLocked()
    }

    private fun takePendingCursorDownLocked(): KeyEvent? {
        val event = pendingCursorDown
        pendingCursorGeneration++
        pendingCursorDown = null
        return event
    }

    private fun modeForKeyLocked(keyCode: Int): CursorControlMode? =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpMode else volumeDownMode

    private fun setModeForKeyLocked(keyCode: Int, mode: CursorControlMode?) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpMode = mode else volumeDownMode = mode
    }

    private fun isReplayedVolumeEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        return (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event.flags and KeyEvent.FLAG_EDITOR_ACTION != 0
    }

    private fun injectReplayedVolumeEvent(volumeEvent: KeyEvent) {
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        val replay = KeyEvent(
            volumeEvent.downTime,
            android.os.SystemClock.uptimeMillis(),
            volumeEvent.action,
            volumeEvent.keyCode,
            volumeEvent.repeatCount,
            volumeEvent.metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            volumeEvent.scanCode,
            volumeEvent.flags or KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_EDITOR_ACTION,
            volumeEvent.source
        )
        copyDisplayId(replay, volumeEvent)
        injectInputEvent(currentContext, replay, "replay volume key")
    }

    private fun injectCursorKeyEvent(volumeEvent: KeyEvent, mode: CursorControlMode) {
        if (mode == CursorControlMode.DISABLED) return
        val leftOnUp = mode == CursorControlMode.VOLUME_UP_LEFT
        val cursorCode = if (volumeEvent.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (leftOnUp) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (leftOnUp) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        }
        val cursorEvent = KeyEvent(
            volumeEvent.downTime,
            android.os.SystemClock.uptimeMillis(),
            volumeEvent.action,
            cursorCode,
            volumeEvent.repeatCount,
            volumeEvent.metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            volumeEvent.flags or KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
            InputDevice.SOURCE_KEYBOARD
        )
        copyDisplayId(cursorEvent, volumeEvent)
        injectInputEvent(
            context ?: resolveSystemContext()?.also { context = it } ?: return,
            cursorEvent,
            "cursor key"
        )
    }

    private fun resolveSystemContext(): Context? = runCatching {
        val threadClass = Class.forName("android.app.ActivityThread")
        (threadClass.getMethod("currentApplication").invoke(null) as? Context)
            ?: run {
                val thread = threadClass.getMethod("currentActivityThread").invoke(null)
                threadClass.getMethod("getSystemContext").invoke(thread) as? Context
            }
    }.getOrNull()

    private fun copyDisplayId(target: KeyEvent, source: KeyEvent) {
        runCatching {
            XposedHelpers.callMethod(target, "setDisplayId", XposedHelpers.callMethod(source, "getDisplayId"))
        }
    }

    private fun injectInputEvent(currentContext: Context, event: KeyEvent, description: String) {
        runCatching {
            val inputManager = currentContext.getSystemService(Context.INPUT_SERVICE)
            XposedHelpers.callMethod(inputManager, "injectInputEvent", event, 0)
        }.onFailure { XposedBridge.log("$TAG: failed to inject $description: ${it.message}") }
    }
}
