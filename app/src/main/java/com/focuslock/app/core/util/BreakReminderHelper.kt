package com.focuslock.app.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focuslock.app.core.wellness.WellnessPreferences
import com.focuslock.app.core.wellness.WellnessSoundManager
import com.focuslock.app.presentation.breakalert.BreakAlertActivity

/**
 * Single Source of Truth for:
 * 1. Monotonic Continuous Screen-On Timer (30 minutes)
 * 2. 5-Minute Active Break Countdown
 * 3. 5-Minute Snooze Logic
 * 4. Automatic Screen ON/OFF Session Reset
 * 5. Notification & Alarm Dispatcher
 */
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
    const val KEY_ALERT_TRIGGERED_FOR_SESSION = "continuous_alert_triggered_for_session"
    const val KEY_BREAK_ACTIVE = "continuous_break_active"
    const val KEY_BREAK_START_ELAPSED = "continuous_break_start_elapsed"

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
        return WellnessPreferences.getInstance(context).isBreakAlertEnabled
    }

    /**
     * Called when device screen turns ON.
     * Starts fresh 30-minute session or recovers gracefully.
     */
    fun onScreenOn(context: Context) {
        val prefs = getPrefs(context)
        val nowElapsed = SystemClock.elapsedRealtime()
        val screenOffElapsed = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, 0L)
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)

        // Reset continuous-use session when screen turns off.
        // When turning on again, start a fresh 30-minute session.
        // (Grace period of 3 seconds only to absorb momentary pocket power-button misclicks)
        val wasGlitch = screenOffElapsed > 0L && (nowElapsed - screenOffElapsed) < 3_000L

        if (sessionStart == 0L || !wasGlitch) {
            prefs.edit()
                .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
                .remove(KEY_SCREEN_OFF_ELAPSED)
                .remove(KEY_SNOOZE_UNTIL_ELAPSED)
                .remove(KEY_BREAK_ACTIVE)
                .remove(KEY_BREAK_START_ELAPSED)
                .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
                .apply()
        } else {
            prefs.edit().remove(KEY_SCREEN_OFF_ELAPSED).apply()
        }
        com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOn(context)
    }

    /**
     * Called when device screen turns OFF.
     * Stops active alarm/chime and resets continuous usage session.
     */
    fun onScreenOff(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_SCREEN_OFF_ELAPSED, nowElapsed)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .apply()

        // Stop any playing alarm/speech
        WellnessSoundManager.getInstance(context).stop()
        com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOff(context)
    }

    /**
     * Resets the 30-minute continuous session to 0.
     */
    fun resetSession(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
            .remove(KEY_SCREEN_OFF_ELAPSED)
            .remove(KEY_SNOOZE_UNTIL_ELAPSED)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
            .apply()

        WellnessSoundManager.getInstance(context).stop()
        dismissReminderNotification(context)
        com.focuslock.app.core.overlay.FloatingTimerManager.refresh(context)
    }

    /**
     * Starts a 5-minute wellness break.
     * Stops the alarm sound and records active break state.
     */
    fun startBreak(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putBoolean(KEY_BREAK_ACTIVE, true)
            .putLong(KEY_BREAK_START_ELAPSED, nowElapsed)
            .remove(KEY_SNOOZE_UNTIL_ELAPSED)
            .apply()

        WellnessSoundManager.getInstance(context).stop()
        dismissReminderNotification(context)
        com.focuslock.app.core.overlay.FloatingTimerManager.refresh(context)
    }

    fun isBreakActive(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_BREAK_ACTIVE, false)) return false
        val start = prefs.getLong(KEY_BREAK_START_ELAPSED, 0L)
        val now = SystemClock.elapsedRealtime()
        return (now - start) < FIVE_MINUTES_MILLIS
    }

    fun getRemainingBreakSeconds(context: Context): Long {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_BREAK_ACTIVE, false)) return 0L
        val start = prefs.getLong(KEY_BREAK_START_ELAPSED, 0L)
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - start) / 1000L
        return (300L - elapsed).coerceAtLeast(0L)
    }

    /**
     * Action 2: Snoozes reminder for 5 minutes.
     * Stops sound, avoids re-alerting during snooze, re-alerts after 5m if screen still on.
     */
    fun snooze(context: Context, minutes: Long = 5L) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val snoozeUntil = nowElapsed + (minutes * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_SNOOZE_UNTIL_ELAPSED, snoozeUntil)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false) // Allow re-alert once snooze expires
            .apply()

        WellnessSoundManager.getInstance(context).stop()
        dismissReminderNotification(context)
    }

    /**
     * Action 3: Dismisses the reminder.
     * Stops sound, keeps timer running, but avoids repeated alerts for 15 minutes.
     */
    fun dismiss(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_LAST_REMINDER_ELAPSED, nowElapsed)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, true)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .apply()

        WellnessSoundManager.getInstance(context).stop()
        dismissReminderNotification(context)
    }

    /**
     * Returns continuous screen-on usage in milliseconds for the current session.
     * Switching apps does NOT reset this value.
     */
    fun getContinuousScreenUsageMillis(context: Context): Long {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isInteractive) {
            return 0L
        }
        val prefs = getPrefs(context)
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (sessionStart <= 0L) {
            prefs.edit().putLong(KEY_SESSION_START_ELAPSED, nowElapsed).apply()
            return 0L
        }
        if (nowElapsed < sessionStart) {
            // Device clock reset
            prefs.edit().putLong(KEY_SESSION_START_ELAPSED, nowElapsed).apply()
            return 0L
        }
        return (nowElapsed - sessionStart).coerceAtLeast(0L)
    }

    /**
     * Returns remaining seconds until the 30-minute wellness reminder.
     */
    fun getRemainingSecondsToThirtyMinutes(context: Context): Long {
        val usageMillis = getContinuousScreenUsageMillis(context)
        val usageSecs = usageMillis / 1000L
        return (1800L - usageSecs).coerceAtLeast(0L)
    }

    /**
     * Evaluates whether 30 continuous minutes have elapsed and triggers the alarm.
     */
    fun checkAndTriggerReminder(context: Context): Boolean {
        if (!isBreakAlertEnabled(context)) return false

        val prefs = getPrefs(context)
        val nowElapsed = SystemClock.elapsedRealtime()

        // 1. If currently in active 5-min break, do not alert
        if (isBreakActive(context)) {
            return false
        }

        // 2. If currently snoozed, check if snooze window has expired
        val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL_ELAPSED, 0L)
        if (snoozeUntil > 0L) {
            if (nowElapsed < snoozeUntil) {
                return false // Still in snooze
            } else {
                // Snooze expired! Clear snooze timestamp to allow re-alert
                prefs.edit().remove(KEY_SNOOZE_UNTIL_ELAPSED).apply()
            }
        }

        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        if (sessionStart <= 0L) {
            prefs.edit().putLong(KEY_SESSION_START_ELAPSED, nowElapsed).apply()
            return false
        }

        val continuousElapsed = nowElapsed - sessionStart
        if (continuousElapsed < THIRTY_MINUTES_MILLIS) {
            return false
        }

        // 3. Do not repeatedly trigger the same alarm for the same session
        val alreadyTriggered = prefs.getBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
        val lastReminder = prefs.getLong(KEY_LAST_REMINDER_ELAPSED, 0L)

        // Throttle check: At least 5 minutes between alerts if user ignored
        if (alreadyTriggered && (nowElapsed - lastReminder) < FIVE_MINUTES_MILLIS) {
            return false
        }

        // Mark triggered
        prefs.edit()
            .putLong(KEY_LAST_REMINDER_ELAPSED, nowElapsed)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, true)
            .apply()

        // 4. Start pleasant sound / custom audio + TTS + gentle vibration
        WellnessSoundManager.getInstance(context).startWellnessAlarm()

        // 5. Post High-Priority Notification with Fullscreen intent fallback
        showBreakReminderNotification(context)

        // 6. Launch BreakAlertActivity UI immediately over current screen
        try {
            if (!BreakAlertActivity.isBreakAlertVisible) {
                BreakAlertActivity.launch(context)
            }
        } catch (e: Exception) {
            // Android background activity restrictions handled by notification
        }

        return true
    }

    fun showBreakReminderNotification(context: Context) {
        createNotificationChannel(context)

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

        // Action 1: Take 5-Min Break
        val startBreakIntent = Intent(context, BreakReminderActionReceiver::class.java).apply {
            action = BreakReminderActionReceiver.ACTION_START_BREAK
        }
        val startBreakPendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            startBreakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Remind Me in 5 Minutes (Snooze)
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
            .setContentTitle("🚶 Time to Walk & Drink Water 💧")
            .setSubText("30m continuous screen time")
            .setContentText("Please, sir! It's time to take care of yourself. Go for a short walk, rest your eyes, and drink a glass of water.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🚶 Time to Walk & Drink Water 💧")
                    .setSummaryText("30m continuous screen time")
                    .bigText("Please, sir! It's time to take care of yourself.\n\n• 🚶 Go for a short walk.\n• 👀 Rest your eyes.\n• 💧 Drink a glass of water.\n\nYou've been using your screen continuously for 30 minutes. Take a small break and refresh yourself.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(openPendingIntent, true)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Take a 5-Min Break", startBreakPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Remind in 5m", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMINDER, notification)
        } catch (e: SecurityException) {}
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
