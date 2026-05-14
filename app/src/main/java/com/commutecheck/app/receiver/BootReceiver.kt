package com.commutecheck.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-registers listeners after device boot.
 * The AndroidAutoReceiver is manifest-declared, so it will be active automatically.
 * This receiver is here for any future initialization that may be needed on boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device booted — CommuteChecker receivers are active")
        }
    }
}
