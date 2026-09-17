package com.slimenull.customsidebuttonfunctions.xposed

import android.os.Build
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import de.robv.android.xposed.XposedBridge

/** Fallback parser for devices where the policy hook does not expose the vendor key. */
internal object RawInputEventReader {
    private const val EV_KEY: Short = 1
    private val running = AtomicBoolean(false)

    fun start(controller: SideKeyController, executor: ActionExecutor) {
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                val settings = SettingsReader.load()
                BufferedInputStream(FileInputStream(settings.inputDevicePath)).use { input ->
                    val eventSize = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) 24 else 16
                    val bytes = ByteArray(eventSize)
                    while (running.get() && input.readFully(bytes)) {
                        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        if (eventSize == 24) {
                            buffer.position(16)
                        } else {
                            buffer.position(8)
                        }
                        val type = buffer.short
                        val code = buffer.short.toInt() and 0xffff
                        val value = buffer.int
                        if (type == EV_KEY && code == SettingsReader.peekKeyCode()) {
                            when (value) {
                                1 -> controller.onDown(SettingsReader.load(), executor, true)
                                0 -> controller.onUp(SettingsReader.load(), executor, true)
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                XposedBridge.log("CustomSideButtonFunctions raw input unavailable: ${error.message}")
            } finally {
                running.set(false)
            }
        }, "CustomSideButtonRawInput").apply {
            isDaemon = true
            start()
        }
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count <= 0) return false
            offset += count
        }
        return true
    }
}
