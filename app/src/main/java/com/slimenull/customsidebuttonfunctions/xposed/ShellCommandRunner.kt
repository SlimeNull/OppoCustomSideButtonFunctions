package com.slimenull.customsidebuttonfunctions.xposed

import de.robv.android.xposed.XposedBridge
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/** Runs in the process that handled the side key; never blocks its input thread. */
internal object ShellCommandRunner {
    fun executeAsRoot(command: String, timeoutSeconds: Long = 10L): Boolean {
        var process: Process? = null
        return runCatching {
            process = ProcessBuilder("su").redirectErrorStream(true).start()
            val shell = process!!
            val drain = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(shell.inputStream)).use { input ->
                        while (input.readLine() != null) Unit
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            OutputStreamWriter(shell.outputStream).use { output ->
                output.write(command)
                output.write("\nexit\n")
                output.flush()
            }
            val completed = shell.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) shell.destroyForcibly()
            drain.join(500)
            completed && shell.exitValue() == 0
        }.getOrElse {
            XposedBridge.log("CustomSideButtonFunctions: root shell failed: ${it.message}")
            false
        }.also { succeeded ->
            if (!succeeded) XposedBridge.log("CustomSideButtonFunctions: root shell command failed or timed out")
            if (process?.isAlive == true) process?.destroy()
        }
    }
}
