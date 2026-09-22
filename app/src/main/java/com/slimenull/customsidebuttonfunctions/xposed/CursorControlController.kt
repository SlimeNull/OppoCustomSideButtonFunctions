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
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import com.slimenull.customsidebuttonfunctions.model.CursorLongPressAction
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
    private data class CursorPress(
        val keyCode: Int,
        val mode: CursorControlMode,
        val longPressAction: CursorLongPressAction,
        val longPressMs: Long,
        val repeatIntervalMs: Long,
        val downEvent: KeyEvent,
        var handler: Handler? = null,
        var initialCursorDownDispatched: Boolean = false,
        var initialCursorReleased: Boolean = false,
        var longTriggered: Boolean = false,
        var longRunnable: Runnable? = null,
        var repeatRunnable: Runnable? = null
    )

    private var volumeUpPress: CursorPress? = null
    private var volumeDownPress: CursorPress? = null
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
        var skipCurrentRelease = false
        synchronized(cursorLock) {
            val handlingPress = pressForKeyLocked(keyCode)
            val handlingMode = handlingPress?.mode
            currentHandlingMode = handlingMode
            if (cursorChordActive) {
                if (event.action == KeyEvent.ACTION_UP && handlingMode != null) cancelPressLocked(keyCode)
                if (event.action == KeyEvent.ACTION_UP && !volumeUpPressed && !volumeDownPressed) cursorChordActive = false
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val settings = SettingsReader.load()
                val mode = currentMode(settings)
                if (!isCursorControlAvailable(mode)) return false
                setPressForKeyLocked(
                    keyCode,
                    CursorPress(
                        keyCode = keyCode,
                        mode = mode,
                        longPressAction = settings.cursorLongPressAction,
                        longPressMs = settings.cursorLongPressMs,
                        repeatIntervalMs = settings.cursorRepeatIntervalMs,
                        downEvent = KeyEvent(event)
                    )
                )
                val handler = policyHandler ?: Handler(Looper.getMainLooper())
                scheduleCursorDownLocked(pressForKeyLocked(keyCode)!!, handler)
            } else if (handlingMode == null) {
                return false
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                // Physical repeat events are replaced by the configured long-press behavior.
            } else if (event.action == KeyEvent.ACTION_UP) {
                pendingUp = takePendingCursorDownLocked(keyCode)
                pendingUpMode = handlingMode
                val initialDispatched = handlingPress?.initialCursorDownDispatched == true
                skipCurrentRelease = handlingPress?.initialCursorReleased == true
                cancelPressLocked(keyCode)
                injectCurrent = pendingUp != null || (initialDispatched && !skipCurrentRelease)
                if (!volumeUpPressed && !volumeDownPressed) cursorChordActive = false
            }
        }
        if (pendingUp != null && pendingUpMode != null) {
            injectCursorPulse(pendingUp!!, pendingUpMode!!)
        } else if (injectCurrent) {
            injectCursorKeyEvent(event, currentHandlingMode ?: currentMode())
        }
        return true
    }

    private fun currentMode(settings: AppSettings = SettingsReader.load()): CursorControlMode {
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

    private fun scheduleCursorDownLocked(press: CursorPress, handler: Handler) {
        press.handler = handler
        val generation = ++pendingCursorGeneration
        pendingCursorDown = KeyEvent(press.downEvent)
        handler.postDelayed({ dispatchPendingCursorDown(generation) }, CURSOR_CHORD_DELAY_MS)

        val longRunnable = Runnable { triggerLongPress(press, handler) }
        press.longRunnable = longRunnable
        handler.postDelayed(longRunnable, press.longPressMs)
    }

    private fun dispatchPendingCursorDown(generation: Int) {
        val event: KeyEvent
        val mode: CursorControlMode
        synchronized(cursorLock) {
            val pending = pendingCursorDown ?: return
            val press = pressForKeyLocked(pending.keyCode) ?: return
            mode = press.mode
            if (generation != pendingCursorGeneration || cursorChordActive) return
            event = pending
            pendingCursorDown = null
            press.initialCursorDownDispatched = true
            press.initialCursorReleased = true
        }
        injectCursorPulse(event, mode)
    }

    private fun triggerLongPress(press: CursorPress, handler: Handler) {
        var releaseInitial: KeyEvent? = null
        var moveToEdge = false
        synchronized(cursorLock) {
            if (pressForKeyLocked(press.keyCode) !== press || press.longTriggered) return
            if (pendingCursorDown?.downTime == press.downEvent.downTime) {
                pendingCursorGeneration++
                pendingCursorDown = null
            }
            press.longTriggered = true
            if (press.initialCursorDownDispatched) {
                press.initialCursorReleased = true
                releaseInitial = press.downEvent
            }
            when (press.longPressAction) {
                CursorLongPressAction.NONE -> Unit
                CursorLongPressAction.REPEAT -> scheduleCursorRepeatLocked(press, handler)
                CursorLongPressAction.EDGE -> moveToEdge = true
            }
        }
        releaseInitial?.let { injectCursorKeyEvent(it, press.mode, KeyEvent.ACTION_UP, 0) }
        if (moveToEdge) injectCursorEdge(press.downEvent, press.mode)
    }

    private fun scheduleCursorRepeatLocked(press: CursorPress, handler: Handler) {
        val repeatRunnable = object : Runnable {
            override fun run() {
                val shouldRepeat = synchronized(cursorLock) {
                    pressForKeyLocked(press.keyCode) === press && press.longTriggered &&
                        press.longPressAction == CursorLongPressAction.REPEAT
                }
                if (!shouldRepeat) return
                injectCursorPulse(press.downEvent, press.mode)
                synchronized(cursorLock) {
                    if (pressForKeyLocked(press.keyCode) === press && press.longTriggered) {
                        handler.postDelayed(this, press.repeatIntervalMs)
                    }
                }
            }
        }
        press.repeatRunnable = repeatRunnable
        handler.postDelayed(repeatRunnable, press.repeatIntervalMs)
    }

    private fun takePendingCursorDownLocked(keyCode: Int): KeyEvent? {
        if (pendingCursorDown?.keyCode != keyCode) return null
        return takePendingCursorDownLocked()
    }

    private fun takePendingCursorDownLocked(): KeyEvent? {
        val event = pendingCursorDown
        pendingCursorGeneration++
        pendingCursorDown = null
        event?.let { cancelPressLocked(it.keyCode) }
        return event
    }

    private fun pressForKeyLocked(keyCode: Int): CursorPress? =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpPress else volumeDownPress

    private fun setPressForKeyLocked(keyCode: Int, press: CursorPress?) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpPress = press else volumeDownPress = press
    }

    private fun cancelPressLocked(keyCode: Int) {
        val press = pressForKeyLocked(keyCode) ?: return
        press.longRunnable?.let { press.handler?.removeCallbacks(it) }
        press.repeatRunnable?.let { press.handler?.removeCallbacks(it) }
        setPressForKeyLocked(keyCode, null)
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

    private fun injectCursorPulse(volumeEvent: KeyEvent, mode: CursorControlMode) {
        injectCursorKeyEvent(volumeEvent, mode, KeyEvent.ACTION_DOWN, 1)
        injectCursorKeyEvent(volumeEvent, mode, KeyEvent.ACTION_UP, 1)
    }

    private fun injectCursorEdge(volumeEvent: KeyEvent, mode: CursorControlMode) {
        val moveLeft = if (volumeEvent.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mode == CursorControlMode.VOLUME_UP_LEFT
        } else {
            mode == CursorControlMode.VOLUME_UP_RIGHT
        }
        val edgeCode = if (moveLeft) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
        injectCursorKeyCode(volumeEvent, edgeCode, KeyEvent.ACTION_DOWN, 0, "cursor edge")
        injectCursorKeyCode(volumeEvent, edgeCode, KeyEvent.ACTION_UP, 0, "cursor edge")
    }

    private fun injectCursorKeyEvent(
        volumeEvent: KeyEvent,
        mode: CursorControlMode,
        action: Int = volumeEvent.action,
        repeatCount: Int = volumeEvent.repeatCount
    ) {
        if (mode == CursorControlMode.DISABLED) return
        val leftOnUp = mode == CursorControlMode.VOLUME_UP_LEFT
        val cursorCode = if (volumeEvent.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (leftOnUp) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (leftOnUp) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        }
        injectCursorKeyCode(volumeEvent, cursorCode, action, repeatCount, "cursor key")
    }

    private fun injectCursorKeyCode(
        volumeEvent: KeyEvent,
        cursorCode: Int,
        action: Int,
        repeatCount: Int,
        description: String
    ) {
        val cursorEvent = KeyEvent(
            volumeEvent.downTime,
            android.os.SystemClock.uptimeMillis(),
            action,
            cursorCode,
            repeatCount,
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
            description
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
