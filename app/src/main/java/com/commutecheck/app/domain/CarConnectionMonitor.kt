package com.commutecheck.app.domain

import android.content.Context
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/**
 * Observes [CarConnection] while the process is alive so a projection/native
 * attach can start a check without waiting for USB power.
 */
object CarConnectionMonitor {

    private const val TAG = "CarConnectionMonitor"

    private var started = false
    private var lastType: Int? = null

    private val observer = Observer<Int> { type ->
        val previous = lastType
        lastType = type
        Log.i(TAG, "Car connection type=$type (was $previous)")
        if (type == CarConnection.CONNECTION_TYPE_PROJECTION ||
            type == CarConnection.CONNECTION_TYPE_NATIVE
        ) {
            val becameConnected = previous == null ||
                previous == CarConnection.CONNECTION_TYPE_NOT_CONNECTED
            if (becameConnected) {
                activeContext?.let { CommuteTrigger.requestCheck(it, CommuteTrigger.REASON_CAR_CONNECTION) }
            }
        }
    }

    @Volatile
    private var activeContext: Context? = null

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        activeContext = app
        CarConnection(app).type.observeForever(observer)
    }
}
