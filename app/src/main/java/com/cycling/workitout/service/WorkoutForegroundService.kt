package com.cycling.workitout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cycling.workitout.MainActivity
import com.cycling.workitout.R
import timber.log.Timber

/**
 * Foreground service that keeps the workout process alive while the user is outside the app.
 * The ViewModel owns the engine; this service only holds the wake lock and the notification.
 */
class WorkoutForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val elapsed = intent?.getStringExtra(EXTRA_ELAPSED) ?: "0:00"
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: ""
        startForeground(NOTIFICATION_ID, buildNotification(this, elapsed, target))
        Timber.tag("WorkoutService").d("onStartCommand elapsed=$elapsed target=$target")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("WorkoutService").d("Service destroyed")
    }

    companion object {
        const val CHANNEL_ID = "workout_active"
        const val NOTIFICATION_ID = 1001
        private const val EXTRA_ELAPSED = "elapsed"
        private const val EXTRA_TARGET = "target"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun update(context: Context, elapsed: String, target: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(context, elapsed, target))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout in progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows elapsed time while a workout is running"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, elapsed: String, target: String): Notification {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val contentText = if (target.isNotEmpty()) "$elapsed · $target" else elapsed
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_bike)
                .setContentTitle("Workout in progress")
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build()
        }
    }
}
