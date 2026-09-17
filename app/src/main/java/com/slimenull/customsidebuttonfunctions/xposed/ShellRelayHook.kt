package com.slimenull.customsidebuttonfunctions.xposed

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process as AndroidProcess
import com.slimenull.customsidebuttonfunctions.data.ShellRelayContract
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

/** Executes commands from a process that can obtain the module's root shell. */
internal object ShellRelayHook {
    const val ACTION = ShellRelayContract.ACTION
    const val PACKAGE = ShellRelayContract.LAUNCHER_PACKAGE
    private val registered = AtomicBoolean(false)

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE) return
        runCatching {
            val applicationClass = XposedHelpers.findClass(
                "com.android.common.LauncherApplication",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                applicationClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!registered.compareAndSet(false, true)) return
                        registerReceiver(param.thisObject as Application)
                    }
                }
            )
            XposedBridge.log("CustomSideButtonFunctions: launcher shell relay hooked")
        }.onFailure {
            XposedBridge.log("CustomSideButtonFunctions: launcher hook failed: ${it.message}")
        }
    }

    private fun registerReceiver(application: Application) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val command = intent.getStringExtra("cmd")?.trim().orEmpty()
                if (command.isEmpty()) return
                Thread {
                    executeAsRoot(command)
                }.apply {
                    isDaemon = true
                    name = "CustomSideButtonShellRelay"
                    start()
                }
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(receiver, filter)
        }
        XposedBridge.log("CustomSideButtonFunctions: launcher shell relay registered")
    }

    fun executeAsRoot(command: String, timeoutSeconds: Long = 10L): Boolean {
        var process: Process? = null
        return runCatching {
            // Match the reference module. Let Android resolve `su` through the
            // Launcher process environment instead of assuming a global path.
            logShellEnvironment()
            process = Runtime.getRuntime().exec("su")
            XposedBridge.log("CustomSideButtonFunctions: root shell started as su")
            val drain = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { input ->
                        while (input.readLine() != null) {
                            // Drain su output so a verbose command cannot block on a full pipe.
                        }
                    }
                }
            }
            drain.isDaemon = true
            drain.start()
            OutputStreamWriter(process!!.outputStream).use { output ->
                output.write(command)
                output.write("\nexit\n")
                output.flush()
            }
            val completed = process!!.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) process!!.destroyForcibly()
            drain.join(500)
            completed && process!!.exitValue() == 0
        }.getOrElse {
            XposedBridge.log("CustomSideButtonFunctions: root shell failed: ${it.message}")
            false
        }.also {
            if (process?.isAlive == true) process?.destroy()
        }
    }

    private fun logShellEnvironment() {
        val path = System.getenv("PATH").orEmpty()
        val visibleCandidates = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")
            .joinToString { candidate ->
                val file = File(candidate)
                "$candidate(exists=${file.exists()},exec=${file.canExecute()})"
            }
        val processName = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentProcessName")
                .invoke(null) as? String
        }.getOrNull().orEmpty()
        val selinux = runCatching {
            Class.forName("android.os.SELinux")
                .getMethod("getContext")
                .invoke(null) as? String
        }.getOrNull().orEmpty()
        XposedBridge.log(
            "CustomSideButtonFunctions: shell env pid=${AndroidProcess.myPid()} " +
                "process=$processName uid=${AndroidProcess.myUid()} path=$path " +
                "selinux=$selinux candidates=$visibleCandidates"
        )
    }
}
