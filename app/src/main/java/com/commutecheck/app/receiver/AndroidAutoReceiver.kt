package com.commutecheck.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.service.CommuteCheckService

/**
 * Detects when the phone is plugged into a power source (proxy for Android Auto connection).
 *
 * Android Auto requires a USB connection, which also triggers POWER_CONNECTED.
 * We use USB power detection as the trigger, since a dedicated Android Auto
 * broadcast is not publicly available. This covers the most common case of
 * plugging into a car's USB port.
 */
class AndroidAutoReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AndroidAutoReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        if (!prefs.isAppEnabled()) {
            Log.d(TAG, "App is disabled, ignoring power event")
            return
        }

        if (!prefs.isConfigured()) {
            Log.d(TAG, "App not configured (home/work locations not set)")
            return
        }

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (isUsbPower(context)) {
                    Log.i(TAG, "USB power connected — starting commute check")
                    startCommuteCheck(context)
                } else {
                    Log.d(TAG, "Non-USB power connected, ignoring")
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i(TAG, "Power disconnected — stopping commute check service")
                stopCommuteCheck(context)
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

    private fun startCommuteCheck(context: Context) {
        val serviceIntent = Intent(context, CommuteCheckService::class.java).apply {
            action = CommuteCheckService.ACTION_CHECK_COMMUTE
        }
        context.startForegroundService(serviceIntent)
    }

    private fun stopCommuteCheck(context: Context) {
        val serviceIntent = Intent(context, CommuteCheckService::class.java)
        context.stopService(serviceIntent)
    }
}
