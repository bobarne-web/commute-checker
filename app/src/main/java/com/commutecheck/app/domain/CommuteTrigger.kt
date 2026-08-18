package com.commutecheck.app.domain

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.service.CommuteCheckService

/**
 * Starts a commute check from car-connection or last-resort USB events.
 * Cooldown prevents a second automatic run from a flaky reconnect or charger plug-in.
 */
object CommuteTrigger {

    const val REASON_CAR_CONNECTION = "car_connection"
    const val REASON_USB_POWER = "usb_power"

    const val USB_DEBOUNCE_MS = 5_000L
    const val COOLDOWN_MS = 10 * 60 * 1000L

    private const val TAG = "CommuteTrigger"

    fun isCooldownElapsed(lastTriggerMs: Long, nowMs: Long, cooldownMs: Long = COOLDOWN_MS): Boolean {
        if (lastTriggerMs <= 0L) return true
        return nowMs - lastTriggerMs >= cooldownMs
    }

    fun requestCheck(context: Context, reason: String, force: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)

        if (!prefs.isAppEnabled()) {
            Log.d(TAG, "Skipping $reason — app disabled")
            return
        }
        if (!prefs.isConfigured()) {
            Log.d(TAG, "Skipping $reason — places or watches not configured")
            return
        }
        if (reason == REASON_USB_POWER && !prefs.isUsbTriggerEnabled()) {
            Log.d(TAG, "Skipping USB trigger — last-resort opt-in is off")
            return
        }

        val now = System.currentTimeMillis()
        if (!force && !isCooldownElapsed(prefs.getLastTriggerTime(), now)) {
            Log.i(TAG, "Skipping $reason — cooldown")
            return
        }

        prefs.markCheckTriggered(now)
        Log.i(TAG, "Starting commute check ($reason)")
        val serviceIntent = Intent(appContext, CommuteCheckService::class.java).apply {
            action = CommuteCheckService.ACTION_CHECK_COMMUTE
        }
        appContext.startForegroundService(serviceIntent)
    }

    fun requestCheckAfterDebounce(
        context: Context,
        reason: String,
        debounceMs: Long = USB_DEBOUNCE_MS,
        stillValid: () -> Boolean
    ) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            if (stillValid()) {
                requestCheck(appContext, reason)
            } else {
                Log.d(TAG, "Debounced $reason cancelled — condition no longer valid")
            }
        }, debounceMs)
    }
}
