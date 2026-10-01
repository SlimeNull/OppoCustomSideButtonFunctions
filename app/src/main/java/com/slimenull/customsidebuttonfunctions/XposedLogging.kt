package com.slimenull.customsidebuttonfunctions

import android.util.Log
import com.slimenull.customsidebuttonfunctions.xposed.XposedBridge

private const val TAG = "CustomSideButtonFunctions"

/** Logs both a short operation context and the complete exception from a recoverable operation. */
internal fun <T> Result<T>.onXposedFailure(operation: String): Result<T> = onFailure { error ->
    try {
        XposedBridge.log("$TAG: $operation failed: ${error.message}")
        XposedBridge.log(error)
    } catch (_: LinkageError) {
        // The app process may run outside Xposed, where the compile-only bridge is unavailable.
        Log.e(TAG, "$operation failed: ${error.message}", error)
    }
}
