package com.slimenull.customsidebuttonfunctions.xposed

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.OperationMode

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
    private val morseSequence = StringBuilder()
    private var morseDownAt = 0L
    private var morseLastUpAt = 0L
    private var morseLongReached = false
    private var morseTriggeredWhilePressed = false
    private var morseThresholdRunnable: Runnable? = null
    private var morseFinishRunnable: Runnable? = null

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
            resetMorseState()
        }
    }

    fun onDown(settings: AppSettings, executor: ActionExecutor, interactive: Boolean) {
        synchronized(lock) {
            if (!settings.enabled || pressed) return
            if (settings.operationMode == OperationMode.MORSE) {
                resetClickState()
                longRunnable?.let(handler::removeCallbacks)
                longRunnable = null
                onMorseDown(settings, executor, interactive)
                return
            }
            resetMorseState()
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
                        val current = SettingsReader.load()
                        if (!current.enabled || current.operationMode != OperationMode.SIMPLE) {
                            cancel()
                            return@synchronized
                        }
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
            if (!settings.enabled || settings.operationMode != activeSettings.operationMode) {
                cancel()
                return
            }
            if (activeSettings.operationMode == OperationMode.MORSE) {
                onMorseUp(executor, interactive)
                return
            }
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
                        val current = SettingsReader.load()
                        if (current.enabled && current.operationMode == OperationMode.SIMPLE) {
                            execute(activeSettings.singleAction, activeSettings.singleCustom, activeSettings, executor, activeInteractive)
                        }
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

    private fun onMorseDown(settings: AppSettings, executor: ActionExecutor, interactive: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (morseSequence.isNotEmpty() && now - morseLastUpAt >= activeSettings.morseCommandWindowMs) {
            finishMorseSequence(executor)
        }
        morseFinishRunnable?.let(handler::removeCallbacks)
        morseFinishRunnable = null
        if (morseSequence.isEmpty()) {
            activeSettings = settings
            activeInteractive = interactive
        } else {
            activeInteractive = activeInteractive && interactive
        }
        pressed = true
        morseDownAt = now
        morseLongReached = false
        morseTriggeredWhilePressed = false
        if (activeSettings.morsePressVibrationEnabled) executor.vibrateInstantCue()

        val runnable = object : Runnable {
            override fun run() {
                synchronized(lock) {
                    if (morseThresholdRunnable === this && pressed && !morseLongReached) {
                        val current = SettingsReader.load()
                        if (!current.enabled || current.operationMode != OperationMode.MORSE) {
                            cancel()
                            return@synchronized
                        }
                        morseLongReached = true
                        val sequence = morseSequence.toString() + '1'
                        val binding = current.morseBindings.firstOrNull { it.sequence == sequence }
                        val hasLongerMatch = current.morseBindings.any {
                            it.sequence.length > sequence.length && it.sequence.startsWith(sequence)
                        }
                        if (current.morseImmediateExecutionEnabled && binding != null && !hasLongerMatch) {
                            morseTriggeredWhilePressed = true
                            morseSequence.clear()
                            morseFinishRunnable?.let(handler::removeCallbacks)
                            morseFinishRunnable = null
                            execute(binding.action, binding.custom, current, executor, activeInteractive)
                            return@synchronized
                        }
                            if (activeSettings.morseLongVibrationEnabled) executor.vibrateInstantCue()
                    }
                }
            }
        }
        morseThresholdRunnable = runnable
        handler.postDelayed(runnable, activeSettings.morseLongPressMs)
    }

    private fun onMorseUp(executor: ActionExecutor, interactive: Boolean) {
        if (!pressed) return
        pressed = false
        morseThresholdRunnable?.let(handler::removeCallbacks)
        morseThresholdRunnable = null
        if (morseTriggeredWhilePressed) {
            morseTriggeredWhilePressed = false
            morseSequence.clear()
            morseFinishRunnable?.let(handler::removeCallbacks)
            morseFinishRunnable = null
            morseLongReached = false
            return
        }
        val now = SystemClock.uptimeMillis()
        val isLong = morseLongReached || now - morseDownAt >= activeSettings.morseLongPressMs
        if (isLong && !morseLongReached && activeSettings.morseLongVibrationEnabled) {
            executor.vibrateInstantCue()
        }
        morseSequence.append(if (isLong) '1' else '0')
        activeInteractive = activeInteractive && interactive
        morseLastUpAt = now

        val current = SettingsReader.load()
        if (!current.enabled || current.operationMode != OperationMode.MORSE) {
            resetMorseState()
            return
        }
        val sequence = morseSequence.toString()
        val exactMatch = current.morseBindings.any { it.sequence == sequence }
        val hasLongerMatch = current.morseBindings.any {
            it.sequence.length > sequence.length && it.sequence.startsWith(sequence)
        }
        if (current.morseImmediateExecutionEnabled && exactMatch && !hasLongerMatch) {
            finishMorseSequence(executor, current)
            return
        }

        val runnable = object : Runnable {
            override fun run() {
                synchronized(lock) {
                    if (morseFinishRunnable === this && !pressed) finishMorseSequence(executor)
                }
            }
        }
        morseFinishRunnable = runnable
        handler.postDelayed(runnable, activeSettings.morseCommandWindowMs)
    }

    private fun finishMorseSequence(executor: ActionExecutor, settings: AppSettings = SettingsReader.load()) {
        morseFinishRunnable?.let(handler::removeCallbacks)
        morseFinishRunnable = null
        val sequence = morseSequence.toString()
        morseSequence.clear()
        if (sequence.isEmpty()) return
        if (!settings.enabled || settings.operationMode != OperationMode.MORSE) return
        val binding = settings.morseBindings.firstOrNull { it.sequence == sequence }
        if (binding == null) {
            executor.notifyUnknownMorseSequence(settings)
            return
        }
        execute(binding.action, binding.custom, settings, executor, activeInteractive)
    }

    private fun resetMorseState() {
        morseThresholdRunnable?.let(handler::removeCallbacks)
        morseThresholdRunnable = null
        morseFinishRunnable?.let(handler::removeCallbacks)
        morseFinishRunnable = null
        morseSequence.clear()
        morseLongReached = false
        morseTriggeredWhilePressed = false
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
