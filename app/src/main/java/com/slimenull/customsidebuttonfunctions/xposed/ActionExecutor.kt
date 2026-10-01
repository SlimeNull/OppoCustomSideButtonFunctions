package com.slimenull.customsidebuttonfunctions.xposed

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.DEFAULT_UNKNOWN_MORSE_TOAST
import com.slimenull.customsidebuttonfunctions.onXposedFailure
import java.util.function.Consumer

internal class ActionExecutor {
    private companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val FLASHLIGHT_SERVICE =
            "com.oplus.systemui.statusbar.notification.keymagicservice.KeyFlashlightService"
        private const val DND_SERVICE =
            "com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService"
        private const val SEEDLING_ACTION = "com.oplus.seedlingservice.action.SEEDLING_SERVICE"
        private const val RECORDING_ACTION = "oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON"
        private val RECORDING_PACKAGES = listOf(
            "com.coloros.soundrecorder",
            "com.oneplus.soundrecorder"
        )
    }

    private var context: Context? = null
    private var strategy: Any? = null
    private var torchEnabled = false
    private var flashlightMessenger: Messenger? = null
    private var flashlightServiceConnection: ServiceConnection? = null
    private var flashlightBindPending = false
    private val flashlightReplyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            XposedBridge.log("CustomSideButtonFunctions: flashlight service reply what=${message.what}")
        }
    })
    private var dndMessenger: Messenger? = null
    private var dndServiceConnection: ServiceConnection? = null
    private var dndBindPending = false
    private val dndReplyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            XposedBridge.log("CustomSideButtonFunctions: DND service reply what=${message.what}")
        }
    })
    private var ringMessenger: Messenger? = null
    private var ringServiceConnection: ServiceConnection? = null
    private var ringBindPending = false
    private val ringReplyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            XposedBridge.log("CustomSideButtonFunctions: ring-mode seedling reply what=${message.what}")
        }
    })
    private var screenshotHelper: Any? = null
    private var screenshotMethod: java.lang.reflect.Method? = null

    fun updateContext(value: Context?) {
        if (value != null) context = value
    }

    fun updateStrategy(value: Any?) {
        if (value != null) strategy = value
    }

    fun vibrateInstantCue() {
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        vibrateInstant(currentContext)
    }

    fun notifyUnknownMorseSequence(settings: AppSettings) {
        if (!settings.unknownMorseFeedbackEnabled) return
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        showToast(currentContext, settings.unknownMorseToastText.ifBlank { DEFAULT_UNKNOWN_MORSE_TOAST })
        vibrateInstant(currentContext)
        Handler(Looper.getMainLooper()).postDelayed({ vibrateInstantCue() }, 100L)
    }

    fun execute(
        action: ActionType,
        custom: CustomActionSettings,
        settings: AppSettings,
        interactive: Boolean = true
    ) {
        XposedBridge.log("CustomSideButtonFunctions: executing action=$action")
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        runCatching {
            if (!interactive && settings.wakeScreenWhenOff) {
                // Oplus' strategy exposes the same wakeup operation used by its stock shortcut.
                runCatching { strategy?.let { XposedHelpers.callMethod(it, "wakeup") } }
                    .onXposedFailure("wake screen")
            }
            when (action) {
                ActionType.CYCLE_RINGER -> cycleRinger(currentContext)
                ActionType.TOGGLE_DND -> toggleDnd(currentContext)
                ActionType.CAMERA -> openCamera(currentContext)
                ActionType.FLASHLIGHT -> toggleTorch(currentContext)
                ActionType.RECORDING -> toggleRecording(currentContext)
                ActionType.SCREENSHOT -> requestScreenshot(currentContext)
                ActionType.COMMON_FUNCTION -> executeCommon(currentContext, custom.commonAction)
                ActionType.XIAOBU_SHORTCUT -> executeXiaobuShortcut(currentContext, custom.xiaobuShortcutId)
                ActionType.CUSTOM_ACTIVITY -> startCustomActivity(currentContext, custom)
                ActionType.CUSTOM_URL -> openUrl(currentContext, custom.urlScheme)
                ActionType.SHELL_COMMAND -> executeShell(custom.shellCommand, custom.shellToastEnabled)
                ActionType.NONE -> return
            }
            feedback(currentContext, action, settings)
        }.onXposedFailure("execute action")
    }

    private fun resolveSystemContext(): Context? = runCatching {
        val threadClass = Class.forName("android.app.ActivityThread")
        val application = threadClass.getMethod("currentApplication").invoke(null) as? Context
        if (application != null) return@runCatching application
        val thread = threadClass.getMethod("currentActivityThread").invoke(null)
        threadClass.getMethod("getSystemContext").invoke(thread) as? Context
    }.onXposedFailure("resolve system context").getOrNull()

    private fun cycleRinger(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nextMode = when (getRingerModeInternal(audio)) {
            RINGER_MODE_NORMAL -> RINGER_MODE_VIBRATE
            RINGER_MODE_VIBRATE -> RINGER_MODE_SILENT
            else -> RINGER_MODE_NORMAL
        }
        // Match ActionKeyStartApp.w(): send the Seedling event and then update the same
        // internal AudioManager state used by the stock action.
        sendRingModeSeedling(context, nextMode)
        setRingerMode(audio, nextMode)
    }

    /** ActionKeyStartApp reads the internal state before cycling; public ringerMode is fallback. */
    private fun getRingerModeInternal(audio: AudioManager): Int = runCatching {
        val method = audio.javaClass.methods.firstOrNull {
            it.name == "getRingerModeInternal" && it.parameterTypes.isEmpty()
        } ?: return@runCatching audio.ringerMode
        method.isAccessible = true
        (method.invoke(audio) as? Int) ?: audio.ringerMode
    }.onXposedFailure("read internal ringer mode").getOrDefault(audio.ringerMode)

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
            }.onXposedFailure("set internal ringer mode").onSuccess { return }
        }
        audio.ringerMode = mode
    }

    private fun toggleDnd(context: Context) {
        // ColorOS SystemUI owns ZenModeController and the associated fluid-cloud/UI effects.
        if (toggleDndThroughSystemUi(context)) return

        // Keep this framework path for ROMs without the Oplus service.
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

    private fun toggleDndThroughSystemUi(context: Context): Boolean {
        dndMessenger?.let { messenger -> return sendDndCommand(messenger) }
        if (dndBindPending) return true
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                dndBindPending = false
                val messenger = Messenger(service)
                dndMessenger = messenger
                XposedBridge.log("CustomSideButtonFunctions: connected to $name")
                if (!sendDndCommand(messenger)) dndMessenger = null
            }

            override fun onServiceDisconnected(name: ComponentName) {
                dndMessenger = null
                dndBindPending = false
                XposedBridge.log("CustomSideButtonFunctions: disconnected from $name")
            }
        }
        val intent = Intent().setComponent(ComponentName(SYSTEM_UI_PACKAGE, DND_SERVICE))
        dndServiceConnection = connection
        dndBindPending = true
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onXposedFailure("bind DND service").getOrDefault(false)
        if (!bound) {
            dndServiceConnection = null
            dndBindPending = false
            return false
        }
        XposedBridge.log("CustomSideButtonFunctions: binding $DND_SERVICE")
        return true
    }

    private fun sendDndCommand(messenger: Messenger): Boolean = runCatching {
        val message = Message.obtain().apply {
            what = 2 // KeyBaseService: handleActionButtonLongPress()
            data = Bundle().apply { putString("key", "LONG_PRESS") }
            replyTo = dndReplyMessenger
        }
        messenger.send(message)
        XposedBridge.log("CustomSideButtonFunctions: sent DND long-press command")
        true
    }.onXposedFailure("send DND service command").getOrDefault(false)

    private fun sendRingModeSeedling(context: Context, mode: Int): Boolean {
        ringMessenger?.let { messenger -> return sendRingModeMessage(messenger, mode) }
        if (ringBindPending) return true
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                ringBindPending = false
                val messenger = Messenger(service)
                ringMessenger = messenger
                XposedBridge.log("CustomSideButtonFunctions: connected to $name")
                if (!sendRingModeMessage(messenger, mode)) ringMessenger = null
            }

            override fun onServiceDisconnected(name: ComponentName) {
                ringMessenger = null
                ringBindPending = false
                XposedBridge.log("CustomSideButtonFunctions: disconnected from $name")
            }
        }
        val intent = Intent(SEEDLING_ACTION).setPackage(SYSTEM_UI_PACKAGE)
        ringServiceConnection = connection
        ringBindPending = true
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onXposedFailure("bind ring-mode seedling service").getOrDefault(false)
        if (!bound) {
            ringServiceConnection = null
            ringBindPending = false
            return false
        }
        XposedBridge.log("CustomSideButtonFunctions: binding ring-mode seedling service")
        return true
    }

    private fun sendRingModeMessage(messenger: Messenger, mode: Int): Boolean = runCatching {
        val message = Message.obtain().apply {
            what = 1 // BaseService: seedling update
            data = Bundle().apply {
                putInt("ringModeType", mode)
                putString("seedling_event", "ringModeEvent")
            }
            replyTo = ringReplyMessenger
        }
        messenger.send(message)
        XposedBridge.log("CustomSideButtonFunctions: sent ring-mode seedling mode=$mode")
        true
    }.onXposedFailure("send ring-mode seedling command").getOrDefault(false)

    private fun openCamera(context: Context) {
        context.startActivity(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Uses the same ColorOS action-button entry that starts or finishes a recording. */
    private fun toggleRecording(context: Context) {
        val packageName = RECORDING_PACKAGES.firstOrNull { packageName ->
            val intent = Intent(RECORDING_ACTION).setPackage(packageName)
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
        if (packageName == null) {
            XposedBridge.log("CustomSideButtonFunctions: ColorOS recorder activity is unavailable")
            return
        }
        val intent = Intent(RECORDING_ACTION)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val startedAsCurrentUser = runCatching {
            XposedHelpers.callMethod(
                context,
                "startActivityAsUser",
                intent,
                UserHandle.getUserHandleForUid(Process.myUid())
            )
            true
        }.onXposedFailure("start system recorder as current user").getOrDefault(false)
        if (!startedAsCurrentUser) context.startActivity(intent)
        XposedBridge.log("CustomSideButtonFunctions: triggered system recorder action package=$packageName")
    }

    private fun toggleTorch(context: Context) {
        // ColorOS owns the flashlight state in SystemUI. Its stock action binds this service and
        // sends the long-press command, which toggles the flashlight through FlashlightController
        // and updates ColorOS' animation/state integration.
        val success = toggleTorchThroughSystemUi(context) || toggleTorchThroughDefault(context)

        // update status
        torchEnabled = !torchEnabled
    }

    private fun toggleTorchThroughSystemUi(context: Context): Boolean {
        if (torchEnabled) {
            // 当已经启用的时候, 就是通过系统 UI 切换了
            return false
        }

        XposedBridge.log("try system ui toggle")

        flashlightMessenger?.let { messenger ->
            return sendFlashlightCommand(messenger)
        }
        if (flashlightBindPending) return true

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                flashlightBindPending = false
                val messenger = Messenger(service)
                flashlightMessenger = messenger
                XposedBridge.log("CustomSideButtonFunctions: connected to $name")
                if (!sendFlashlightCommand(messenger)) {
                    flashlightMessenger = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                flashlightMessenger = null
                flashlightBindPending = false
                XposedBridge.log("CustomSideButtonFunctions: disconnected from $name")
            }
        }
        val intent = Intent().setComponent(ComponentName(SYSTEM_UI_PACKAGE, FLASHLIGHT_SERVICE))
        flashlightServiceConnection = connection
        flashlightBindPending = true
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            XposedBridge.log("CustomSideButtonFunctions: cannot bind flashlight service: ${it.message}")
        }.getOrDefault(false)
        if (!bound) {
            flashlightServiceConnection = null
            flashlightBindPending = false
            return false
        }
        XposedBridge.log("CustomSideButtonFunctions: binding $FLASHLIGHT_SERVICE")
        return true
    }

    private fun toggleTorchThroughDefault(context: Context): Boolean {

        XposedBridge.log("try camera control toggle")
        // Keep the CameraManager path for ROMs without an exposed vendor strategy (and for the
        // generic PhoneWindowManager/raw-input fallbacks).
        val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = camera.cameraIdList.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        val torchNewState = !torchEnabled
        camera.setTorchMode(cameraId, torchNewState)
        return true
    }

    private fun sendFlashlightCommand(messenger: Messenger): Boolean = runCatching {
        val message = Message.obtain().apply {
            what = 2 // KeyBaseService: handleActionButtonLongPress()
            data = Bundle().apply { putString("key", "LONG_PRESS") }
            replyTo = flashlightReplyMessenger
        }
        messenger.send(message)
        XposedBridge.log("CustomSideButtonFunctions: sent flashlight long-press command")
        true
    }.onFailure {
        XposedBridge.log("CustomSideButtonFunctions: flashlight service command failed: ${it.message}")
    }.getOrDefault(false)

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
        }.onXposedFailure("request screenshot through status bar").getOrDefault(false)
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
    }.onXposedFailure("request screenshot through ScreenshotHelper").getOrDefault(false)

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
        val currentContext = context ?: resolveSystemContext()?.also { context = it } ?: return
        runCatching {
            val intent = resolveFlashNotesIntent(currentContext)
                ?: error("no compatible Flash Notes service found")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentContext.startForegroundService(intent)
            } else {
                currentContext.startService(intent)
            }
            XposedBridge.log("CustomSideButtonFunctions: triggered ColorOS Flash Notes service")
        }.onXposedFailure("start Flash Notes service")
    }

    /**
     * Flash Notes moved packages/classes across ColorOS releases. Resolve the installed service
     * instead of using a ColorOS version cutoff, since regional builds do not share exact versions.
     */
    private fun resolveFlashNotesIntent(context: Context): Intent? {
        val candidates = listOf(
            Candidate(
                packageName = "com.oplus.gleanerservice",
                className = "com.oplus.gleanerservice.flashnotes.business.service.DataCollectService",
                action = "oplus.gleanerservice.intent.action.COLLECT_DATA"
            ),
            Candidate(
                packageName = "com.coloros.colordirectservice",
                className = "com.oplus.directservice.flashnotes.business.service.DataCollectService",
                action = "coloros.colordirectservice.intent.action.COLLECT_DATA"
            ),
        )
        val packageManager = context.packageManager
        for (candidate in candidates) {
            val component = ComponentName(candidate.packageName, candidate.className)
            val serviceInfo = runCatching {
                packageManager.getServiceInfo(component, PackageManager.MATCH_ALL)
            }.onXposedFailure("resolve Flash Notes service $component").getOrNull() ?: continue
            if (!serviceInfo.enabled) continue
            val intent = Intent(candidate.action)
                .setComponent(component)
                .putExtra("triggerType", 1)
                .putExtra("longPressEventType", 0)
            if (packageManager.resolveService(intent, PackageManager.MATCH_ALL) != null) {
                XposedBridge.log("CustomSideButtonFunctions: resolved Flash Notes service $component")
                return intent
            }
        }
        return null
    }

    private data class Candidate(
        val packageName: String,
        val className: String,
        val action: String
    )

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

    private fun executeShell(command: String, showToast: Boolean = true, onFailure: (() -> Unit)? = null) {
        if (command.isBlank()) {
            XposedBridge.log("CustomSideButtonFunctions: shell command is empty")
            return
        }
        Thread {
            val currentContext = context ?: return@Thread
            if (!ShellCommandRunner.executeAsRoot(currentContext, command, showToast)) onFailure?.invoke()
        }.apply {
            isDaemon = true
            name = "CustomSideButtonShell"
            start()
        }
    }

    private fun feedback(context: Context, action: ActionType, settings: AppSettings) {
        if (settings.vibrationEnabled) vibrateInstant(context)
        if (settings.toastEnabled) showToast(context, settings.toastText.ifBlank { action.title })
    }

    private fun vibrateInstant(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() != true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10L)
            }
        }.onXposedFailure("vibrate")
    }

    private fun showToast(context: Context, message: String) {
        HandlerBridge.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
