package com.slimenull.customsidebuttonfunctions

import android.app.Application
import com.slimenull.customsidebuttonfunctions.data.SettingsStore

class CustomSideButtonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.initializeRemoteStorage(this)
    }
}
