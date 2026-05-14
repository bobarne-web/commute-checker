package com.commutecheck.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.DetectedLocation
import com.commutecheck.app.data.TravelTimeResult
import com.commutecheck.app.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE = "commute_checker_service"
        const val CHANNEL_TRAVEL_TIME = "commute_checker_travel_time"
        const val NOTIFICATION_SERVICE_ID = 1001
        const val NOTIFICATION_TRAVEL_TIME_ID = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Commute Check Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the commute checker is running"
            setShowBadge(false)
        }

        val travelTimeChannel = NotificationChannel(
            CHANNEL_TRAVEL_TIME,
            "Travel Time Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows travel time estimates for your commute"
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(travelTimeChannel)
    }

    fun createServiceNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle("Commute Checker")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showTravelTimeNotification(
        detectedLocation: DetectedLocation,
        result: TravelTimeResult
    ) {
        val destination = when (detectedLocation) {
            DetectedLocation.HOME -> "Work"
            DetectedLocation.WORK -> "Home"
            DetectedLocation.UNKNOWN -> return
        }

        val title = "Travel time to $destination"
        val body = buildString {
            append(result.durationInTrafficText)
            append(" (${result.distanceText})")
            if (result.summary.isNotEmpty()) {
                append(" via ${result.summary}")
            }
            val diff = result.durationInTrafficSeconds - result.durationSeconds
            if (diff > 60) {
                val extraMin = diff / 60
                append("\n+${extraMin} min due to traffic")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRAVEL_TIME)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, notification)
    }

    fun showSkippedNotification(reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle("Commute Check Skipped")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, notification)
    }

    fun showErrorNotification(error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle("Commute Check Error")
            .setContentText(error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, notification)
    }
}
