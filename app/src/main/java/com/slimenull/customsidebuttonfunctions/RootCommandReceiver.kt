package com.slimenull.customsidebuttonfunctions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class RootCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND)?.takeIf { it.isNotBlank() } ?: return
        if (intent.getBooleanExtra(EXTRA_SHOW_TOAST, true)) {
            Toast.makeText(context.applicationContext, "已收到 Shell 广播", Toast.LENGTH_SHORT).show()
        }
        val pending = goAsync()
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val finished = process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                if (!finished || process.exitValue() != 0) {
                    Log.e(TAG, "root command failed or timed out")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "root command failed", error)
            } finally {
                pending.finish()
            }
        }.apply {
            isDaemon = true
            name = "CustomSideButtonRootCommand"
            start()
        }
    }

    companion object {
        const val ACTION = "com.slimenull.customsidebuttonfunctions.EXECUTE_ROOT"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_SHOW_TOAST = "show_toast"
        private const val TAG = "CustomSideButtonFunctions"
        private const val TIMEOUT_SECONDS = 10L
    }
}
