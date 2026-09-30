package com.slimenull.customsidebuttonfunctions

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Invisible launcher used when ColorOS blocks starting a background service directly. */
class RootCommandTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent.getStringExtra(EXTRA_COMMAND)?.takeIf { it.isNotBlank() }
        if (command != null) {
            val serviceIntent = Intent(this, RootCommandService::class.java)
                .setAction(RootCommandService.ACTION)
                .putExtra(RootCommandService.EXTRA_COMMAND, command)
                .putExtra(RootCommandService.EXTRA_SHOW_TOAST, intent.getBooleanExtra(EXTRA_SHOW_TOAST, true))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION = "com.slimenull.customsidebuttonfunctions.EXECUTE_ROOT"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_SHOW_TOAST = "show_toast"
    }
}
