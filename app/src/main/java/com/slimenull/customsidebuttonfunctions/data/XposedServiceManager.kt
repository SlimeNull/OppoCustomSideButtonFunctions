package com.slimenull.customsidebuttonfunctions.data
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

object XposedServiceManager {

    @Volatile
    var xposedService: XposedService? = null
        private set

    val isModuleActive: Boolean
        get() = xposedService != null

    private val bindListeners = CopyOnWriteArrayList<(XposedService) -> Unit>()

    fun registerBindListener(listener: (XposedService) -> Unit) {
        bindListeners += listener
        xposedService?.let(listener)
    }

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                bindListeners.forEach { listener -> listener(service) }
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService == service) {
                    xposedService = null
                }
            }
        })
    }
}
