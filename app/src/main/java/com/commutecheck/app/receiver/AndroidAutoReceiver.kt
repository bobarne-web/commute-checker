package com.commutecheck.app.receiver

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.car.app.connection.CarConnection
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.domain.CarPresence
import com.commutecheck.app.domain.CommuteTrigger
import com.commutecheck.app.service.CommuteCheckService

/**
 * Starts a commute check when the phone actually joins a car session.
 *
 * Preferred signals: Android Auto / car-mode connection.
 * USB power is a last-resort opt-in with debounce and a shared cooldown so a
 * charger plug-in does not fire a check every time.
 */
class AndroidAutoReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AndroidAutoReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        if (!prefs.isAppEnabled()) {
            Log.d(TAG, "App is disabled, ignoring ${intent.action}")
            return
        }

        if (!prefs.isConfigured()) {
            Log.d(TAG, "App not configured (places or watches missing)")
            return
        }

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (!prefs.isUsbTriggerEnabled()) {
                    Log.d(TAG, "USB power ignored — last-resort opt-in is off")
                    return
                }
                if (!isUsbPower(context)) {
                    Log.d(TAG, "Non-USB power connected, ignoring")
                    return
                }
                if (CarPresence.isCarConnected(context)) {
                    Log.d(TAG, "USB power while already in a car session — car path owns the trigger")
                    return
                }
                Log.i(TAG, "USB power connected — debounce then last-resort check")
                val pending = goAsync()
                CommuteTrigger.requestCheckAfterDebounce(
                    context,
                    CommuteTrigger.REASON_USB_POWER
                ) {
                    try {
                        isUsbPower(context) && prefs.isUsbTriggerEnabled() &&
                            !CarPresence.isCarConnected(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i(TAG, "Power disconnected — stopping commute check service")
                stopCommuteCheck(context)
            }
            UiModeManager.ACTION_ENTER_CAR_MODE,
            CarConnection.ACTION_CAR_CONNECTION_UPDATED -> {
                if (CarPresence.isCarConnected(context)) {
                    Log.i(TAG, "Car connection detected via ${intent.action}")
                    CommuteTrigger.requestCheck(context, CommuteTrigger.REASON_CAR_CONNECTION)
                } else {
                    Log.d(TAG, "${intent.action} received but car is not connected")
                }
            }
        }
    }

    private fun isUsbPower(context: Context): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged == BatteryManager.BATTERY_PLUGGED_USB
    }

    private fun stopCommuteCheck(context: Context) {
        val serviceIntent = Intent(context, CommuteCheckService::class.java)
        context.stopService(serviceIntent)
    }
}
