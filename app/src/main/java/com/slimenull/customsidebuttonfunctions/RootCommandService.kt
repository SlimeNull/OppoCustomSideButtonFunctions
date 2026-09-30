package com.slimenull.customsidebuttonfunctions

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.concurrent.TimeUnit

class RootCommandService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val command = intent?.getStringExtra(EXTRA_COMMAND)?.takeIf { it.isNotBlank() }
        val showToast = intent?.getBooleanExtra(EXTRA_SHOW_TOAST, true) ?: true
        if (command == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        Thread {
            try {
                Log.i(TAG, "root command received")
                if (showToast) showToast("开始 Shell 指令")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                if (!finished || process.exitValue() != 0) {
                    Log.e(TAG, "root command failed or timed out")
                    if (showToast) showToast("Shell 指令执行失败")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "root command failed", error)
                if (showToast) showToast("Shell 指令执行失败")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notification(): Notification {
        val channelId = "root_command"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Shell 指令", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("正在执行 Shell 指令")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION = "com.slimenull.customsidebuttonfunctions.EXECUTE_ROOT"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_SHOW_TOAST = "show_toast"
        private const val TAG = "CustomSideButtonFunctions"
        private const val TIMEOUT_SECONDS = 10L
        private const val NOTIFICATION_ID = 735
    }
}
