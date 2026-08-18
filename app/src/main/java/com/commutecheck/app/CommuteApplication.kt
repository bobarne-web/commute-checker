package com.commutecheck.app

import android.app.Application
import com.commutecheck.app.domain.CarConnectionMonitor

class CommuteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CarConnectionMonitor.start(this)
    }
}
