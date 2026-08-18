package com.commutecheck.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.car.app.notification.CarPendingIntent
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.model.CarColor
import androidx.core.app.NotificationCompat
import com.commutecheck.app.R
import com.commutecheck.app.auto.CommuteCarAppService
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.domain.DelayMath
import com.commutecheck.app.ui.MainActivity

class NotificationHelper(private val context: Context) {

    private val normalTravelTimeColor = CarColor.createCustom(Color.WHITE, Color.WHITE)
    private val abnormalTravelTimeColor = CarColor.createCustom(
        Color.rgb(255, 64, 129),
        Color.rgb(255, 64, 129)
    )

    companion object {
        const val CHANNEL_SERVICE = "commute_checker_service"
        const val CHANNEL_TRAVEL_TIME = "commute_checker_travel_time_v2"
        const val NOTIFICATION_SERVICE_ID = 1001
        const val NOTIFICATION_TRAVEL_TIME_ID = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val carNotificationManager = CarNotificationManager.from(context)

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
            // Set category for better Android Auto compatibility
            setSound(null, null)
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

    /**
     * Show a summary of all checked routes. Each route shows its travel time and,
     * when extra traffic minutes are at or above the threshold, DELAY +N is flagged.
     */
    fun showResultsNotification(currentPlaceName: String?, results: List<RouteCheckResult>) {
        if (results.isEmpty()) return

        val anyDelayed = results.any { it.isDelayed }
        val title = if (currentPlaceName != null) {
            "Commute from $currentPlaceName"
        } else {
            "Commute times"
        }

        val lines = results.map { formatResultLine(it) }
        val body = lines.joinToString("\n")

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val carPendingIntent = createCarAppPendingIntent()

        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { inboxStyle.addLine(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_TRAVEL_TIME)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle(title)
            .setContentText(if (anyDelayed) "Delays detected — tap for details" else body)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(if (anyDelayed) R.color.travel_time_delayed else R.color.travel_time_clear))
            .setColorized(anyDelayed)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(carPendingIntent)
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .setColor(if (anyDelayed) abnormalTravelTimeColor else normalTravelTimeColor)
                    .build()
            )

        carNotificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, builder)
    }

    private fun formatResultLine(result: RouteCheckResult): String {
        if (result.hasError) {
            return "${result.destinationName}: unavailable"
        }
        return buildString {
            append(result.destinationName)
            append(": ")
            append(result.durationInTrafficText)
            append("  ")
            append(DelayMath.statusText(result.isDelayed, result.delayMinutes))
        }
    }

    private fun createCarAppPendingIntent(): PendingIntent {
        val carIntent = Intent(Intent.ACTION_VIEW).setComponent(
            ComponentName(context, CommuteCarAppService::class.java)
        )
        return CarPendingIntent.getCarApp(
            context,
            0,
            carIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun showSkippedNotification(reason: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle("Commute Check Skipped")
            .setContentText(reason)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle("Commute Check Skipped")
                    .setContentText(reason)
                    .build()
            )

        carNotificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, builder)
    }

    fun showErrorNotification(error: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_commute)
            .setContentTitle("Commute Check Error")
            .setContentText(error)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentTitle("Commute Check Error")
                    .setContentText(error)
                    .build()
            )

        carNotificationManager.notify(NOTIFICATION_TRAVEL_TIME_ID, builder)
    }
}
