package com.focuslock.app.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focuslock.app.presentation.breakalert.BreakAlertActivity

object BreakReminderHelper {

    const val CHANNEL_ID = "focuslock_break_reminders"
    const val CHANNEL_NAME = "Screen Time Break Reminders"
    const val NOTIFICATION_ID_REMINDER = 9001
    const val NOTIFICATION_ID_COMPLETED = 9002

    const val PREFS_NAME = "focuslock_app_prefs"
    const val KEY_CONTINUOUS_BREAK_ALERT = "continuous_break_alert"
    const val KEY_SESSION_START_ELAPSED = "continuous_screen_session_start_elapsed"
    const val KEY_SCREEN_OFF_ELAPSED = "continuous_screen_off_elapsed"
    const val KEY_LAST_REMINDER_ELAPSED = "continuous_last_reminder_elapsed"
    const val KEY_SNOOZE_UNTIL_ELAPSED = "continuous_snooze_until_elapsed"

    const val THIRTY_MINUTES_MILLIS = 30 * 60 * 1000L // 30 minutes
    const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L   // 5 minutes

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to take a 5-minute break to walk and drink water after 30 minutes of continuous screen usage."
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBreakAlertEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CONTINUOUS_BREAK_ALERT, true)
    }

    fun onScreenOn(context: Context) {
        val prefs = getPrefs(context)
        val nowElapsed = SystemClock.elapsedRealtime()
        val screenOffElapsed = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, 0L)
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)

        // When screen turns on after being off, reset continuous timer for new continuous session
        val wasBriefOff = screenOffElapsed > 0L && (nowElapsed - screenOffElapsed) < 5_000L

        if (sessionStart == 0L || !wasBriefOff) {
            prefs.edit()
                .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
                .remove(KEY_SCREEN_OFF_ELAPSED)
                .apply()
        } else {
            // Keep existing sessionStart for momentary glitches (< 5s), clear off time
            prefs.edit().remove(KEY_SCREEN_OFF_ELAPSED).apply()
        }
        com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOn(context)
    }

    fun onScreenOff(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_SCREEN_OFF_ELAPSED, nowElapsed)
            .apply()
        com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOff(context)
    }

    fun resetSession(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
            .remove(KEY_SCREEN_OFF_ELAPSED)
            .remove(KEY_SNOOZE_UNTIL_ELAPSED)
            .apply()
        com.focuslock.app.core.overlay.FloatingTimerManager.refresh(context)
    }

    fun snooze(context: Context, minutes: Long = 5L) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val snoozeUntil = nowElapsed + (minutes * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_SNOOZE_UNTIL_ELAPSED, snoozeUntil)
            .apply()
        dismissReminderNotification(context)
    }

    fun getContinuousScreenUsageMillis(context: Context): Long {
        val prefs = getPrefs(context)
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        if (sessionStart <= 0L) return 0L
        val nowElapsed = SystemClock.elapsedRealtime()
        return (nowElapsed - sessionStart).coerceAtLeast(0L)
    }

    fun checkAndTriggerReminder(context: Context): Boolean {
        if (!isBreakAlertEnabled(context)) return false

        val prefs = getPrefs(context)
        val nowElapsed = SystemClock.elapsedRealtime()

        val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL_ELAPSED, 0L)
        if (snoozeUntil > 0L && nowElapsed < snoozeUntil) {
            return false // Currently snoozed
        }

        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        if (sessionStart <= 0L) {
            // Initialize start time if not set yet
            prefs.edit().putLong(KEY_SESSION_START_ELAPSED, nowElapsed).apply()
            return false
        }

        val continuousElapsed = nowElapsed - sessionStart
        if (continuousElapsed < THIRTY_MINUTES_MILLIS) {
            return false
        }

        val lastReminder = prefs.getLong(KEY_LAST_REMINDER_ELAPSED, 0L)
        // Throttle: Don't show reminder more frequently than every 5 minutes
        if (lastReminder > 0L && (nowElapsed - lastReminder) < FIVE_MINUTES_MILLIS) {
            return false
        }

        // Record reminder trigger time
        prefs.edit().putLong(KEY_LAST_REMINDER_ELAPSED, nowElapsed).apply()

        // 1. Post High-Priority Notification (Guaranteed visible fallback even if activity launch is restricted)
        showBreakReminderNotification(context)

        // 2. Launch BreakAlertActivity UI if not already visible
        try {
            if (!BreakAlertActivity.isBreakAlertVisible) {
                BreakAlertActivity.launch(context)
            }
        } catch (e: Exception) {
            // Android background activity restrictions may block this, notification covers it
        }

        return true
    }

    fun showBreakReminderNotification(context: Context) {
        createNotificationChannel(context)

        // Intent to start BreakAlertActivity directly
        val openIntent = Intent(context, BreakAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BreakAlertActivity.EXTRA_AUTO_START, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Take a Break
        val startBreakIntent = Intent(context, BreakAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BreakAlertActivity.EXTRA_AUTO_START, true)
        }
        val startBreakPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            startBreakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Remind Me Later (Snooze broadcast)
        val snoozeIntent = Intent(context, BreakReminderActionReceiver::class.java).apply {
            action = BreakReminderActionReceiver.ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            1003,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Dismiss
        val dismissIntent = Intent(context, BreakReminderActionReceiver::class.java).apply {
            action = BreakReminderActionReceiver.ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1004,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚶 Time to Walk or Drink Water 💧")
            .setSubText("30m continuous screen time")
            .setContentText("You've been using your screen continuously for 30 minutes. Take a 5-minute break, walk around, stretch, and drink some water.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🚶 Time to Walk or Drink Water 💧")
                    .setSummaryText("30m continuous screen time")
                    .bigText("You've been using your screen continuously for 30 minutes. Take a 5-minute break, walk around, stretch, and drink some water.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Take a Break",
                startBreakPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Remind Me Later",
                snoozePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMINDER, notification)
        } catch (e: SecurityException) {
            // Notification permission denied on Android 13+
        }
    }

    fun dismissReminderNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_REMINDER)
        } catch (e: Exception) {}
    }

    fun showBreakCompletedNotification(context: Context) {
        createNotificationChannel(context)

        val openDashboardIntent = Intent(context, com.focuslock.app.presentation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            openDashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Break Complete! 🎉")
            .setContentText("Great job taking a 5-minute walk and hydrating! You're ready to refocus.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Great job taking a 5-minute walk and hydrating! You're refreshed and ready to refocus.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETED, notification)
        } catch (e: SecurityException) {}
    }
}
