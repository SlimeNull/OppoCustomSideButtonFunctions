package com.slimenull.customsidebuttonfunctions.xposed

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import com.slimenull.customsidebuttonfunctions.RootCommandTrampolineActivity
import com.slimenull.customsidebuttonfunctions.RootCommandReceiver
import com.slimenull.customsidebuttonfunctions.RootCommandService
import de.robv.android.xposed.XposedBridge

/** Runs in the process that handled the side key; never blocks its input thread. */
internal object ShellCommandRunner {
    fun executeAsRoot(context: Context, command: String, showToast: Boolean = true): Boolean {
        return executeViaActivity(context, command, showToast)
    }

    fun executeViaReceiver(context: Context, command: String, showToast: Boolean = true): Boolean {
        return runCatching {
            context.sendBroadcast(appIntent(RootCommandReceiver::class.java, RootCommandReceiver.ACTION, command, showToast))
            XposedBridge.log("CustomSideButtonFunctions: dispatched root command to app receiver")
            true
        }.getOrElse {
            XposedBridge.log("CustomSideButtonFunctions: receiver dispatch failed: ${it.message}")
            false
        }
    }

    fun executeViaService(context: Context, command: String, showToast: Boolean = true): Boolean {
        return runCatching {
            val intent = appIntent(RootCommandService::class.java, RootCommandService.ACTION, command, showToast)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            XposedBridge.log("CustomSideButtonFunctions: dispatched root command to app service")
            true
        }.getOrElse {
            XposedBridge.log("CustomSideButtonFunctions: service dispatch failed: ${it.message}")
            false
        }
    }

    fun executeViaActivity(context: Context, command: String, showToast: Boolean = true): Boolean {
        return runCatching {
            val intent = appIntent(
                RootCommandTrampolineActivity::class.java,
                RootCommandTrampolineActivity.ACTION,
                command,
                showToast
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(intent)
            XposedBridge.log("CustomSideButtonFunctions: dispatched root command to app trampoline")
            true
        }.getOrElse {
            XposedBridge.log("CustomSideButtonFunctions: activity dispatch failed: ${it.message}")
            false
        }
    }

    private fun appIntent(component: Class<*>, action: String, command: String, showToast: Boolean): Intent = Intent()
        .setComponent(ComponentName(PACKAGE, component.name))
        .setAction(action)
        .putExtra("command", command)
        .putExtra("show_toast", showToast)

    private const val PACKAGE = "com.slimenull.customsidebuttonfunctions"
}
