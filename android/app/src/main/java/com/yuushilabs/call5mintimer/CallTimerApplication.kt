package com.yuushilabs.call5mintimer

import android.app.Application
import com.yuushilabs.call5mintimer.service.Notifications

class CallTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
