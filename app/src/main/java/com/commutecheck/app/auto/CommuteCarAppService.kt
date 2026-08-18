package com.commutecheck.app.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class CommuteCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Debug/sideload (DHU, Unknown sources) needs an open host path.
        // Release builds accept only the official Android Auto / AAOS hosts.
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return CommuteSession()
    }
}

class CommuteSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return CommuteScreen(carContext)
    }
}
