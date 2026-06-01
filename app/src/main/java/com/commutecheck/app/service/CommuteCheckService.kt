package com.commutecheck.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.commutecheck.app.domain.CommuteEngine
import com.commutecheck.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CommuteCheckService : Service() {

    companion object {
        private const val TAG = "CommuteCheckService"
        const val ACTION_CHECK_COMMUTE = "com.commutecheck.app.CHECK_COMMUTE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var engine: CommuteEngine

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        engine = CommuteEngine(this)

        startForeground(
            NotificationHelper.NOTIFICATION_SERVICE_ID,
            notificationHelper.createServiceNotification("Checking your commute...")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_COMMUTE) {
            performCommuteCheck()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun performCommuteCheck() {
        serviceScope.launch {
            try {
                when (val result = engine.runChecks()) {
                    is CommuteEngine.EngineResult.Success -> {
                        if (result.results.isEmpty()) {
                            Log.i(TAG, "No active watches matched the current location/time")
                            notificationHelper.showSkippedNotification(
                                "No commute checks scheduled for right now"
                            )
                        } else {
                            notificationHelper.showResultsNotification(
                                result.currentPlaceName, result.results
                            )
                        }
                    }
                    is CommuteEngine.EngineResult.Failure -> {
                        Log.e(TAG, "Commute check failed: ${result.message}")
                        notificationHelper.showErrorNotification(result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during commute check", e)
                notificationHelper.showErrorNotification("Error: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }
}
