package com.slimenull.customsidebuttonfunctions.xposed

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_UNKNOWN_MORSE_TOAST
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.function.Consumer

internal class ActionExecutor {
    private var context: Context? = null
    private var strategy: Any? = null
    private var torchEnabled = false
    private var screenshotHelper: Any? = null
    private var screenshotMethod: java.lang.reflect.Method? = null

    fun updateContext(value: Context?) {
        if (value != null) context = value
    }

    fun updateStrategy(value: Any?) {
        if (value != null) strategy = value
    }

    fun vibrateMorseCue() {
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        runCatching {
            val vibrator = currentContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { XposedBridge.log("CustomSideButtonFunctions: Morse vibration failed: ${it.message}") }
    }

    fun notifyUnknownMorseSequence(settings: AppSettings) {
        if (!settings.unknownMorseFeedbackEnabled) return
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        showToast(currentContext, settings.unknownMorseToastText.ifBlank { DEFAULT_UNKNOWN_MORSE_TOAST })
        runCatching {
            val vibrator = currentContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 50L, 50L, 50L), -1))
            }
        }.onFailure { XposedBridge.log("CustomSideButtonFunctions: unknown Morse vibration failed: ${it.message}") }
    }

    fun execute(
        action: ActionType,
        custom: CustomActionSettings,
        settings: AppSettings,
        interactive: Boolean = true
    ) {
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        runCatching {
            if (!interactive && settings.wakeScreenWhenOff) {
                // Oplus' strategy exposes the same wakeup operation used by its stock shortcut.
                runCatching { strategy?.let { XposedHelpers.callMethod(it, "wakeup") } }
            }
            when (action) {
                ActionType.CYCLE_RINGER -> cycleRinger(currentContext)
                ActionType.TOGGLE_DND -> toggleDnd(currentContext)
                ActionType.CAMERA -> openCamera(currentContext)
                ActionType.FLASHLIGHT -> toggleTorch(currentContext)
                ActionType.SCREENSHOT -> requestScreenshot(currentContext)
                ActionType.COMMON_FUNCTION -> executeCommon(currentContext, custom.commonAction)
                ActionType.XIAOBU_SHORTCUT -> executeXiaobuShortcut(currentContext, custom.xiaobuShortcutId)
                ActionType.CUSTOM_ACTIVITY -> startCustomActivity(currentContext, custom)
                ActionType.CUSTOM_URL -> openUrl(currentContext, custom.urlScheme)
                ActionType.SHELL_COMMAND -> executeShell(custom.shellCommand)
                ActionType.NONE -> return
            }
            feedback(currentContext, action, settings)
        }.onFailure { XposedBridge.log("CustomSideButtonFunctions action failed: ${it.message}") }
    }

    private fun resolveSystemContext(): Context? = runCatching {
        val threadClass = Class.forName("android.app.ActivityThread")
        val application = threadClass.getMethod("currentApplication").invoke(null) as? Context
        if (application != null) return@runCatching application
        val thread = threadClass.getMethod("currentActivityThread").invoke(null)
        threadClass.getMethod("getSystemContext").invoke(thread) as? Context
    }.getOrNull()

    private fun cycleRinger(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nextMode = when (audio.ringerMode) {
            RINGER_MODE_NORMAL -> RINGER_MODE_VIBRATE
            RINGER_MODE_VIBRATE -> RINGER_MODE_SILENT
            else -> RINGER_MODE_NORMAL
        }
        setRingerMode(audio, nextMode)
    }

    private fun setRingerMode(audio: AudioManager, mode: Int) {
        val internal = audio.javaClass.methods.firstOrNull { method ->
            method.name == "setRingerModeInternal" && method.parameterTypes.isNotEmpty()
        }
        if (internal != null) {
            runCatching {
                val args = internal.parameterTypes.mapIndexed { index, type ->
                    when {
                        type == Int::class.javaPrimitiveType -> mode
                        type == String::class.java -> if (index == 1) "customsidebuttonfunctions" else "side_key"
                        type == Boolean::class.javaPrimitiveType -> false
                        else -> null
                    }
                }.toTypedArray()
                internal.isAccessible = true
                internal.invoke(audio, *args)
            }.onSuccess { return }
        }
        audio.ringerMode = mode
    }

    private fun toggleDnd(context: Context) {
        val notification = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notification.isNotificationPolicyAccessGranted) {
            showToast(context, "请先授予免打扰访问权限")
            return
        }
        notification.setInterruptionFilter(
            if (notification.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            else android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        )
    }

    private fun openCamera(context: Context) {
        context.startActivity(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun toggleTorch(context: Context) {
        val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = camera.cameraIdList.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        torchEnabled = !torchEnabled
        camera.setTorchMode(cameraId, torchEnabled)
    }

    private fun requestScreenshot(context: Context) {
        if (requestScreenshotWithHelper(context)) return
        val statusBar = context.getSystemService("statusbar")
        val requested = runCatching {
            val method = statusBar?.javaClass?.declaredMethods?.firstOrNull {
                it.name == "requestScreenshot"
            } ?: statusBar?.javaClass?.methods?.firstOrNull { it.name == "requestScreenshot" }
                ?: return@runCatching false
            method.isAccessible = true
            val arguments = method.parameterTypes.map { type ->
                when {
                    type == Int::class.javaPrimitiveType -> 0
                    type == Long::class.javaPrimitiveType -> 0L
                    type == Boolean::class.javaPrimitiveType -> false
                    type == String::class.java -> "side_key"
                    else -> null
                }
            }.toTypedArray()
            method.invoke(statusBar, *arguments)
            true
        }.getOrDefault(false)
        if (!requested) {
            executeShell("service call color_screenshot 1") {
                context.sendBroadcast(
                    Intent("com.android.systemui.action.SCREENSHOT").setPackage("com.android.systemui")
                )
                context.sendBroadcast(Intent("android.intent.action.SCREENSHOT").setPackage("com.android.systemui"))
            }
        }
    }

    /** Uses the framework's ScreenshotHelper so the request reaches SystemUI without a shell. */
    private fun requestScreenshotWithHelper(context: Context): Boolean = runCatching {
        val helper = screenshotHelper ?: Class.forName("com.android.internal.util.ScreenshotHelper")
            .getConstructor(Context::class.java)
            .newInstance(context)
            .also { screenshotHelper = it }
        val method = screenshotMethod ?: helper.javaClass.methods.firstOrNull { candidate ->
            if (candidate.name != "takeScreenshot") return@firstOrNull false
            val types = candidate.parameterTypes
            (types.size == 3 && types[0] == Int::class.javaPrimitiveType &&
                Handler::class.java.isAssignableFrom(types[1])) ||
                (types.size == 6 && types[0] == Int::class.javaPrimitiveType &&
                    types[1] == Boolean::class.javaPrimitiveType &&
                    types[2] == Boolean::class.javaPrimitiveType &&
                    types[3] == Int::class.javaPrimitiveType &&
                    Handler::class.java.isAssignableFrom(types[4]))
        } ?: error("ScreenshotHelper.takeScreenshot is unavailable")
            .also { screenshotMethod = it }

        val callback = Consumer<Any?> { result ->
            if (result == null) {
                XposedBridge.log("CustomSideButtonFunctions: ScreenshotHelper returned no URI")
            }
        }
        val handler = Handler(Looper.getMainLooper())
        method.isAccessible = true
        when (method.parameterTypes.size) {
            3 -> method.invoke(helper, 0, handler, callback)
            6 -> method.invoke(helper, 1, true, true, 0, handler, callback)
            else -> error("Unsupported ScreenshotHelper signature")
        }
        XposedBridge.log("CustomSideButtonFunctions: screenshot requested through ScreenshotHelper")
        true
    }.onFailure {
        XposedBridge.log("CustomSideButtonFunctions: ScreenshotHelper unavailable: ${it.message}")
    }.getOrDefault(false)

    private fun executeCommon(context: Context, action: CommonAction) {
        when (action) {
            CommonAction.WECHAT_PAY -> startWechatShortcut(context, "launch_type_offline_wallet")
            CommonAction.WECHAT_SCAN -> startWechatShortcut(context, "launch_type_scan_qrcode")
            CommonAction.ALIPAY_PAY -> openUrl(context, "alipays://platformapi/startapp?saId=20000056")
            CommonAction.ALIPAY_RECEIVE -> openUrl(context, "alipays://platformapi/startapp?appId=20000123")
            CommonAction.ALIPAY_SCAN -> openUrl(context, "alipays://platformapi/startapp?saId=10000007")
            CommonAction.FLASH_MEMORY -> startFlashMemory()
            CommonAction.XIAOBU_MEMORY -> startActivity(context, "com.oplus.aimemory", "com.oplus.aimemory.MainActivity", "")
        }
    }

    private fun startWechatShortcut(context: Context, launchType: String) {
        val intent = Intent("com.tencent.mm.ui.ShortCutDispatchAction")
            .setComponent(ComponentName("com.tencent.mm", "com.tencent.mm.ui.ShortCutDispatchActivity"))
            .setPackage("com.tencent.mm")
            .putExtra("LauncherUI.Shortcut.LaunchType", launchType)
            .putExtra("LauncherUI.From.Scaner.Shortcut", false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    private fun startActivity(context: Context, packageName: String, className: String, action: String) {
        val intent = Intent().setComponent(ComponentName(packageName, className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action.isNotBlank()) intent.action = action
        context.startActivity(intent)
    }

    private fun startCustomActivity(context: Context, custom: CustomActionSettings) {
        if (custom.activityPackage.isBlank() || custom.activityClass.isBlank()) {
            XposedBridge.log("CustomSideButtonFunctions: custom Activity is empty")
            return
        }
        startActivity(context, custom.activityPackage, custom.activityClass, custom.activityAction)
    }

    private fun openUrl(context: Context, value: String) {
        if (value.isBlank()) {
            XposedBridge.log("CustomSideButtonFunctions: custom Url Scheme is empty")
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(value))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun startFlashMemory() {
        executeShell(
            "am start-foreground-service -a oplus.gleanerservice.intent.action.COLLECT_DATA " +
                "-n com.oplus.gleanerservice/.flashnotes.business.service.DataCollectService --ei triggerType 1"
        )
    }

    private fun executeXiaobuShortcut(context: Context, shortcutId: String) {
        if (shortcutId.isBlank()) {
            XposedBridge.log("CustomSideButtonFunctions: Xiaobu shortcut id is empty")
            return
        }
        val params = Bundle().apply {
            putString("tag", shortcutId)
            putString("widgetCode", "")
        }
        context.contentResolver.call(
            Uri.parse("content://com.coloros.shortcuts.basecard.provider.FunctionSpecProvider"),
            "execute_one_shortcut",
            null,
            params
        )
    }

    private fun executeShell(command: String, onFailure: (() -> Unit)? = null) {
        if (command.isBlank()) {
            XposedBridge.log("CustomSideButtonFunctions: shell command is empty")
            return
        }
        Thread {
            if (!ShellCommandRunner.executeAsRoot(command)) onFailure?.invoke()
        }.apply {
            isDaemon = true
            name = "CustomSideButtonShell"
            start()
        }
    }

    private fun feedback(context: Context, action: ActionType, settings: AppSettings) {
        if (settings.vibrationEnabled) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(settings.vibrationDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        if (settings.toastEnabled) showToast(context, settings.toastText.ifBlank { action.title })
    }

    private fun showToast(context: Context, message: String) {
        HandlerBridge.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
