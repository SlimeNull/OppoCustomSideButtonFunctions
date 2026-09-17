package com.slimenull.customsidebuttonfunctions.xposed

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings

/** Thread-safe gesture state machine shared by framework and raw input sources. */
internal class SideKeyController {
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pressed = false
    private var longTriggered = false
    private var secondClick = false
    private var pendingSingle = false
    private var activeSettings = AppSettings()
    private var activeInteractive = true
    private var longRunnable: Runnable? = null
    private var singleRunnable: Runnable? = null

    fun onKeyEvent(event: KeyEvent, executor: ActionExecutor) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> onDown(SettingsReader.load(), executor, true)
            KeyEvent.ACTION_UP -> onUp(SettingsReader.load(), executor, true)
        }
    }

    fun cancel() {
        synchronized(lock) {
            pressed = false
            longTriggered = false
            resetClickState()
            longRunnable?.let(handler::removeCallbacks)
            longRunnable = null
        }
    }

    fun onDown(settings: AppSettings, executor: ActionExecutor, interactive: Boolean) {
        synchronized(lock) {
            if (!settings.enabled || pressed) return
            pressed = true
            activeSettings = settings
            activeInteractive = interactive
            longTriggered = false
            secondClick = pendingSingle
            if (secondClick) {
                pendingSingle = false
                singleRunnable?.let(handler::removeCallbacks)
                singleRunnable = null
            }
            val runnable = Runnable {
                synchronized(lock) {
                    if (pressed && !longTriggered) {
                        longTriggered = true
                        execute(activeSettings.longAction, activeSettings.longCustom, activeSettings, executor, activeInteractive)
                    }
                }
            }
            longRunnable = runnable
            handler.postDelayed(runnable, settings.longPressMs)
        }
    }

    fun onUp(settings: AppSettings, executor: ActionExecutor, interactive: Boolean) {
        synchronized(lock) {
            if (!pressed) return
            pressed = false
            longRunnable?.let(handler::removeCallbacks)
            longRunnable = null
            if (!longTriggered) activeInteractive = interactive
            if (longTriggered) {
                resetClickState()
                return
            }
            if (secondClick) {
                secondClick = false
                execute(activeSettings.doubleAction, activeSettings.doubleCustom, activeSettings, executor, activeInteractive)
                return
            }
            if (settings.doubleAction == ActionType.NONE) {
                execute(activeSettings.singleAction, activeSettings.singleCustom, activeSettings, executor, activeInteractive)
                return
            }
            pendingSingle = true
            val runnable = Runnable {
                synchronized(lock) {
                    if (pendingSingle) {
                        pendingSingle = false
                        execute(activeSettings.singleAction, activeSettings.singleCustom, activeSettings, executor, activeInteractive)
                    }
                }
            }
            singleRunnable = runnable
            handler.postDelayed(runnable, settings.doubleClickWindowMs)
        }
    }

    private fun resetClickState() {
        secondClick = false
        pendingSingle = false
        singleRunnable?.let(handler::removeCallbacks)
        singleRunnable = null
    }

    private fun execute(
        action: ActionType,
        custom: CustomActionSettings,
        settings: AppSettings,
        executor: ActionExecutor,
        interactive: Boolean
    ) {
        if (action != ActionType.NONE) executor.execute(action, custom, settings, interactive)
    }
}
