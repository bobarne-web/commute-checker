package com.commutecheck.app.domain

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.car.app.connection.CarConnection

/**
 * Best-effort Android Auto / car-mode detection. USB power is not treated as
 * a car connection here; that path is a separate last-resort opt-in.
 */
object CarPresence {

    private const val TAG = "CarPresence"
    private const val CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection"
    private const val CAR_CONNECTION_STATE = "CarConnectionState"

    fun isCarConnected(context: Context): Boolean {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            return true
        }
        val type = queryConnectionType(context)
        return type == CarConnection.CONNECTION_TYPE_PROJECTION ||
            type == CarConnection.CONNECTION_TYPE_NATIVE
    }

    fun queryConnectionType(context: Context): Int {
        return try {
            val uri = Uri.Builder().scheme("content").authority(CAR_CONNECTION_AUTHORITY).build()
            context.contentResolver.query(
                uri,
                arrayOf(CAR_CONNECTION_STATE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(CAR_CONNECTION_STATE)
                    if (index >= 0) cursor.getInt(index) else CarConnection.CONNECTION_TYPE_NOT_CONNECTED
                } else {
                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED
                }
            } ?: CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        } catch (e: Exception) {
            Log.d(TAG, "Car connection query unavailable: ${e.message}")
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        }
    }
}
