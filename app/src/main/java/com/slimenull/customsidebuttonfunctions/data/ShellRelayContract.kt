package com.slimenull.customsidebuttonfunctions.data

import android.content.Context
import android.content.Intent

/** Shared by the app UI and the Xposed-side Launcher receiver. */
object ShellRelayContract {
    const val ACTION = "com.slimenull.customsidebuttonfunctions.EXECUTE_SHELL"
    const val LAUNCHER_PACKAGE = "com.android.launcher"

    fun requestRootPermission(context: Context) {
        context.sendBroadcast(
            Intent(ACTION)
                .setPackage(LAUNCHER_PACKAGE)
                .putExtra("cmd", "su")
        )
    }
}
