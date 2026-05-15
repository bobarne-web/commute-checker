package com.commutecheck.app.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class CommuteCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
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
