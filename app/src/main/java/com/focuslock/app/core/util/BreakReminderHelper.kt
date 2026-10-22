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
 * 2. 5-Minute Screen-Off Grace Period (Preserves remaining time if screen back ON < 5m)
 * 3. 5-Minute Active Break Countdown
 * 4. 5-Minute Snooze Logic
 * 5. Device Reboot & Process Recovery Validation
 * 6. Notification & Alarm Dispatcher
 */
interface WellnessClock {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

object SystemWellnessClock : WellnessClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

object BreakReminderHelper {

    const val CHANNEL_ID = "focuslock_break_reminders"
    const val CHANNEL_NAME = "Screen Time Break Reminders"
    const val NOTIFICATION_ID_REMINDER = 9001
    const val NOTIFICATION_ID_COMPLETED = 9002

    const val PREFS_NAME = "focuslock_app_prefs"
    const val KEY_CONTINUOUS_BREAK_ALERT = "continuous_break_alert"
    const val KEY_SESSION_START_ELAPSED = "continuous_screen_session_start_elapsed"
    const val KEY_SCREEN_OFF_ELAPSED = "continuous_screen_off_elapsed"
    const val KEY_SCREEN_OFF_WALL_TIME = "continuous_screen_off_wall_time"
    const val KEY_ACCUMULATED_SCREEN_ON_MS = "continuous_accumulated_screen_on_ms"
    const val KEY_BOOT_SESSION_ID = "continuous_boot_session_id"
    const val KEY_IS_SCREEN_ON = "continuous_is_screen_on"
    const val KEY_LAST_REMINDER_ELAPSED = "continuous_last_reminder_elapsed"
    const val KEY_SNOOZE_UNTIL_ELAPSED = "continuous_snooze_until_elapsed"
    const val KEY_ALERT_TRIGGERED_FOR_SESSION = "continuous_alert_triggered_for_session"
    const val KEY_BREAK_ACTIVE = "continuous_break_active"
    const val KEY_BREAK_START_ELAPSED = "continuous_break_start_elapsed"
    const val KEY_BREAK_START_WALL = "continuous_break_start_wall"
    const val KEY_BREAK_DEADLINE_WALL = "continuous_break_deadline_wall"
    const val KEY_BREAK_BOOT_SESSION_ID = "continuous_break_boot_session_id"
    const val KEY_BREAK_COMPLETED = "continuous_break_completed"

    const val THIRTY_MINUTES_MILLIS = 30 * 60 * 1000L // 30 minutes
    const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L   // 5 minutes (Grace period, break duration, snooze)

    @Volatile
    var clock: WellnessClock = SystemWellnessClock

    @androidx.annotation.VisibleForTesting
    var preferencesOverride: SharedPreferences? = null

    @androidx.annotation.VisibleForTesting
    var isBreakAlertEnabledOverride: Boolean? = null

    @androidx.annotation.VisibleForTesting
    var soundManagerOverride: WellnessSoundManager? = null

    @androidx.annotation.VisibleForTesting
    var onAlarmTriggered: (() -> Unit)? = null

    private var pendingResetRunnable: Runnable? = null
    private var resetHandler: android.os.Handler? = null

    private fun getHandler(): android.os.Handler? {
        if (resetHandler == null) {
            try {
                val looper = android.os.Looper.getMainLooper()
                if (looper != null) {
                    resetHandler = android.os.Handler(looper)
                }
            } catch (e: Throwable) {}
        }
        return resetHandler
    }

    private fun schedulePendingResetTask(context: Context? = null) {
        cancelPendingResetTask()
        val runnable = Runnable {
            expireSessionIfGracePeriodOver(context)
        }
        pendingResetRunnable = runnable
        try {
            getHandler()?.postDelayed(runnable, FIVE_MINUTES_MILLIS)
        } catch (e: Throwable) {}
    }

    fun cancelPendingResetTask() {
        pendingResetRunnable?.let {
            try {
                getHandler()?.removeCallbacks(it)
            } catch (e: Throwable) {}
            pendingResetRunnable = null
        }
    }

    fun hasPendingResetTask(): Boolean {
        return pendingResetRunnable != null
    }

    fun getBootSessionId(): Long {
        return clock.currentTimeMillis() - clock.elapsedRealtime()
    }

    fun isSameBootSession(
        nowElapsed: Long,
        screenOffElapsed: Long,
        storedBootId: Long,
        currentBootId: Long
    ): Boolean {
        if (nowElapsed < screenOffElapsed) return false
        if (storedBootId == 0L || currentBootId == 0L) return true
        return kotlin.math.abs(currentBootId - storedBootId) < 15_000L
    }

    fun createNotificationChannel(context: Context? = null) {
        if (context == null) return
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

    private fun getPrefs(context: Context?): SharedPreferences {
        preferencesOverride?.let { return it }
        return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: error("Context or preferencesOverride required")
    }

    fun isBreakAlertEnabled(context: Context? = null): Boolean {
        isBreakAlertEnabledOverride?.let { return it }
        val ctx = context ?: return true
        return WellnessPreferences.getInstance(ctx).isBreakAlertEnabled
    }

    /**
     * Called when device screen turns ON.
     * 1. Cancels any pending grace period expiration task.
     * 2. If screen was already ON and active, no-op (idempotent across multiple receivers).
     * 3. If previous session exists and screen was OFF for < 5 minutes, resumes session with preserved remaining time.
     * 4. If screen was OFF for >= 5 minutes or no session exists, starts fresh 30-minute session.
     */
    fun onScreenOn(context: Context? = null) {
        cancelPendingResetTask()
        val prefs = getPrefs(context)
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.currentTimeMillis()
        val isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, false)
        val currentBootId = getBootSessionId()

        // If screen is ALREADY registered as ON and session is active, avoid resetting
        if (isScreenOn) {
            val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
            if (sessionStart > 0L) {
                context?.let { ctx ->
                    try {
                        com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOn(ctx)
                    } catch (e: Throwable) {}
                }
                return
            }
        }

        val screenOffElapsed = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, 0L)
        val screenOffWall = prefs.getLong(KEY_SCREEN_OFF_WALL_TIME, 0L)
        val accumulated = prefs.getLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
        val storedBootId = prefs.getLong(KEY_BOOT_SESSION_ID, 0L)

        val isSameBoot = isSameBootSession(nowElapsed, screenOffElapsed, storedBootId, currentBootId)

        val shouldResume: Boolean = if (screenOffElapsed > 0L && accumulated > 0L) {
            if (isSameBoot) {
                val elapsedOff = nowElapsed - screenOffElapsed
                elapsedOff in 0L until FIVE_MINUTES_MILLIS
            } else {
                // Across device reboot: Monotonic timestamps not comparable; check wall clock
                val wallOff = nowWall - screenOffWall
                wallOff in 0L until FIVE_MINUTES_MILLIS
            }
        } else {
            false
        }

        if (shouldResume) {
            // Resume previous session: preserve accumulatedScreenOnMs
            prefs.edit()
                .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
                .putBoolean(KEY_IS_SCREEN_ON, true)
                .remove(KEY_SCREEN_OFF_ELAPSED)
                .remove(KEY_SCREEN_OFF_WALL_TIME)
                .putLong(KEY_BOOT_SESSION_ID, currentBootId)
                .apply()
        } else {
            // Start fresh 30-minute session
            val edit = prefs.edit()
                .putLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
                .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
                .putBoolean(KEY_IS_SCREEN_ON, true)
                .remove(KEY_SCREEN_OFF_ELAPSED)
                .remove(KEY_SCREEN_OFF_WALL_TIME)
                .remove(KEY_SNOOZE_UNTIL_ELAPSED)
                .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
                .putLong(KEY_BOOT_SESSION_ID, currentBootId)

            // Do not erase an active break timer if the user started one
            if (!prefs.getBoolean(KEY_BREAK_ACTIVE, false)) {
                edit.remove(KEY_BREAK_ACTIVE)
                    .remove(KEY_BREAK_START_ELAPSED)
                    .remove(KEY_BREAK_START_WALL)
                    .remove(KEY_BREAK_DEADLINE_WALL)
                    .remove(KEY_BREAK_BOOT_SESSION_ID)
            }
            edit.apply()
        }

        // Check if an active break completed while the screen was off
        if (prefs.getBoolean(KEY_BREAK_ACTIVE, false)) {
            val breakStart = prefs.getLong(KEY_BREAK_START_ELAPSED, 0L)
            val breakWall = prefs.getLong(KEY_BREAK_START_WALL, 0L)
            val breakBootId = prefs.getLong(KEY_BREAK_BOOT_SESSION_ID, 0L)
            val isSameBreakBoot = isSameBootSession(nowElapsed, breakStart, breakBootId, currentBootId)
            val breakElapsed = if (isSameBreakBoot) (nowElapsed - breakStart) else (nowWall - breakWall)

            if (breakElapsed >= FIVE_MINUTES_MILLIS) {
                completeBreak(context)
            }
        }

        context?.let { ctx ->
            try {
                com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOn(ctx)
            } catch (e: Throwable) {}
        }
    }

    /**
     * Called when device screen turns OFF.
     * 1. Saves accumulated screen-on time so far.
     * 2. Records screen-off timestamps (monotonic and wall-clock).
     * 3. Starts 5-minute grace period and schedules delayed session expiration.
     * 4. Stops any playing alarm sound.
     * Note: Does NOT erase active 5-minute break timer! Break continues counting down with screen off.
     */
    fun onScreenOff(context: Context? = null) {
        val prefs = getPrefs(context)
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.currentTimeMillis()
        val isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, false)
        val currentBootId = getBootSessionId()

        // If screen was ON, accumulate active elapsed duration
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        val currentAccumulated = prefs.getLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)

        val addedMs = if (isScreenOn && sessionStart > 0L) {
            (nowElapsed - sessionStart).coerceAtLeast(0L)
        } else {
            0L
        }
        val newAccumulated = currentAccumulated + addedMs

        prefs.edit()
            .putLong(KEY_ACCUMULATED_SCREEN_ON_MS, newAccumulated)
            .putLong(KEY_SESSION_START_ELAPSED, 0L)
            .putLong(KEY_SCREEN_OFF_ELAPSED, nowElapsed)
            .putLong(KEY_SCREEN_OFF_WALL_TIME, nowWall)
            .putBoolean(KEY_IS_SCREEN_ON, false)
            .putLong(KEY_BOOT_SESSION_ID, currentBootId)
            .apply()

        // Start 5-minute grace period reset task
        schedulePendingResetTask(context)

        // Stop active alarm/speech
        context?.let { ctx ->
            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}

            try {
                com.focuslock.app.core.overlay.FloatingTimerManager.onScreenOff(ctx)
            } catch (e: Throwable) {}
        }
    }

    /**
     * Expire session if screen remained OFF for 5 minutes or longer.
     * Note: Only expires continuous screen session, does not cancel active break countdown!
     */
    fun expireSessionIfGracePeriodOver(context: Context? = null) {
        val prefs = getPrefs(context)
        val isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, false)
        if (isScreenOn) return

        val screenOffElapsed = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, 0L)
        val nowElapsed = clock.elapsedRealtime()

        if (screenOffElapsed > 0L && (nowElapsed - screenOffElapsed) >= FIVE_MINUTES_MILLIS) {
            prefs.edit()
                .putLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
                .putLong(KEY_SESSION_START_ELAPSED, 0L)
                .remove(KEY_SCREEN_OFF_ELAPSED)
                .remove(KEY_SCREEN_OFF_WALL_TIME)
                .remove(KEY_SNOOZE_UNTIL_ELAPSED)
                .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
                .apply()

            context?.let { ctx ->
                try {
                    com.focuslock.app.core.overlay.FloatingTimerManager.refresh(ctx)
                } catch (e: Throwable) {}
            }
        }
    }

    /**
     * Handles device reboot.
     * 1. Restores active 5-minute break if wall-clock elapsed time < 5 minutes, else completes break.
     * 2. Validates whether reboot occurred during a valid continuous screen-off grace period (<5 min).
     */
    fun onDeviceReboot(context: Context? = null) {
        cancelPendingResetTask()
        val prefs = getPrefs(context)
        val isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, false)
        val screenOffWall = prefs.getLong(KEY_SCREEN_OFF_WALL_TIME, 0L)
        val accumulated = prefs.getLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
        val nowWall = clock.currentTimeMillis()

        // 1. Recover active 5-minute break across device reboot
        if (prefs.getBoolean(KEY_BREAK_ACTIVE, false)) {
            val breakStartWall = prefs.getLong(KEY_BREAK_START_WALL, 0L)
            if (breakStartWall > 0L) {
                val breakElapsed = nowWall - breakStartWall
                if (breakElapsed >= FIVE_MINUTES_MILLIS) {
                    completeBreak(context)
                } else {
                    // Break still valid: restore monotonic elapsed start for current boot session
                    val currentBootId = getBootSessionId()
                    val newElapsedStart = clock.elapsedRealtime() - breakElapsed
                    prefs.edit()
                        .putLong(KEY_BREAK_BOOT_SESSION_ID, currentBootId)
                        .putLong(KEY_BREAK_START_ELAPSED, newElapsedStart)
                        .apply()
                    context?.let { ctx ->
                        val deadlineWall = prefs.getLong(KEY_BREAK_DEADLINE_WALL, breakStartWall + FIVE_MINUTES_MILLIS)
                        scheduleBreakCompletionAlarm(ctx, deadlineWall)
                    }
                }
            }
        }

        // 2. Continuous 30m screen-off grace period handling
        if (!isScreenOn && screenOffWall > 0L && accumulated > 0L) {
            val wallDiff = nowWall - screenOffWall
            if (wallDiff in 0L until FIVE_MINUTES_MILLIS) {
                // Restore state safely across reboot! Grace period remains active.
                val currentBootId = getBootSessionId()
                prefs.edit()
                    .putLong(KEY_BOOT_SESSION_ID, currentBootId)
                    .putLong(KEY_SCREEN_OFF_ELAPSED, clock.elapsedRealtime())
                    .apply()
                schedulePendingResetTask(context)
                return
            }
        }

        // Grace period expired or invalid: reset cleanly
        resetSession(context)
    }

    /**
     * Resets the 30-minute continuous session to 0.
     * Does NOT cancel an active 5-minute break timer.
     */
    fun resetSession(context: Context? = null) {
        cancelPendingResetTask()
        val nowElapsed = clock.elapsedRealtime()
        val currentBootId = getBootSessionId()

        getPrefs(context).edit()
            .putLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
            .putLong(KEY_SESSION_START_ELAPSED, nowElapsed)
            .putBoolean(KEY_IS_SCREEN_ON, true)
            .remove(KEY_SCREEN_OFF_ELAPSED)
            .remove(KEY_SCREEN_OFF_WALL_TIME)
            .remove(KEY_SNOOZE_UNTIL_ELAPSED)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false)
            .putLong(KEY_BOOT_SESSION_ID, currentBootId)
            .apply()

        context?.let { ctx ->
            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}

            try {
                dismissReminderNotification(ctx)
            } catch (e: Throwable) {}

            try {
                com.focuslock.app.core.overlay.FloatingTimerManager.refresh(ctx)
            } catch (e: Throwable) {}
        }
    }

    /**
     * Starts a 5-minute wellness break.
     * Stops the alarm sound, records start time and deadline, and schedules alarm.
     */
    fun startBreak(context: Context? = null) {
        cancelPendingResetTask()
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.currentTimeMillis()
        val currentBootId = getBootSessionId()
        val deadlineWall = nowWall + FIVE_MINUTES_MILLIS

        getPrefs(context).edit()
            .putBoolean(KEY_BREAK_ACTIVE, true)
            .putLong(KEY_BREAK_START_ELAPSED, nowElapsed)
            .putLong(KEY_BREAK_START_WALL, nowWall)
            .putLong(KEY_BREAK_DEADLINE_WALL, deadlineWall)
            .putLong(KEY_BREAK_BOOT_SESSION_ID, currentBootId)
            .putBoolean(KEY_BREAK_COMPLETED, false)
            .remove(KEY_SNOOZE_UNTIL_ELAPSED)
            .apply()

        context?.let { ctx ->
            scheduleBreakCompletionAlarm(ctx, deadlineWall)

            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}

            try {
                dismissReminderNotification(ctx)
            } catch (e: Throwable) {}

            try {
                com.focuslock.app.core.overlay.FloatingTimerManager.refresh(ctx)
            } catch (e: Throwable) {}
        }
    }

    fun isBreakActive(context: Context? = null): Boolean {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_BREAK_ACTIVE, false)) return false
        val startElapsed = prefs.getLong(KEY_BREAK_START_ELAPSED, 0L)
        val startWall = prefs.getLong(KEY_BREAK_START_WALL, 0L)
        val bootId = prefs.getLong(KEY_BREAK_BOOT_SESSION_ID, 0L)
        val currentBootId = getBootSessionId()
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.currentTimeMillis()

        val isSameBoot = isSameBootSession(nowElapsed, startElapsed, bootId, currentBootId)
        val elapsedMs = if (isSameBoot && startElapsed > 0L) {
            nowElapsed - startElapsed
        } else if (startWall > 0L) {
            nowWall - startWall
        } else {
            0L
        }

        return elapsedMs in 0L until FIVE_MINUTES_MILLIS
    }

    fun isBreakCompleted(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_BREAK_COMPLETED, false)
    }

    fun getRemainingBreakSeconds(context: Context? = null): Long {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_BREAK_ACTIVE, false)) return 0L
        val startElapsed = prefs.getLong(KEY_BREAK_START_ELAPSED, 0L)
        val startWall = prefs.getLong(KEY_BREAK_START_WALL, 0L)
        val bootId = prefs.getLong(KEY_BREAK_BOOT_SESSION_ID, 0L)
        val currentBootId = getBootSessionId()
        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.currentTimeMillis()

        val isSameBoot = isSameBootSession(nowElapsed, startElapsed, bootId, currentBootId)
        val elapsedMs = if (isSameBoot && startElapsed > 0L) {
            nowElapsed - startElapsed
        } else if (startWall > 0L) {
            nowWall - startWall
        } else {
            0L
        }

        val remainingMs = FIVE_MINUTES_MILLIS - elapsedMs
        return (remainingMs / 1000L).coerceAtLeast(0L)
    }

    fun completeBreak(context: Context? = null) {
        val prefs = getPrefs(context)
        val wasActive = prefs.getBoolean(KEY_BREAK_ACTIVE, false)
        val alreadyCompleted = prefs.getBoolean(KEY_BREAK_COMPLETED, false)
        if (!wasActive && alreadyCompleted) return

        prefs.edit()
            .putBoolean(KEY_BREAK_ACTIVE, false)
            .putBoolean(KEY_BREAK_COMPLETED, true)
            .remove(KEY_BREAK_START_ELAPSED)
            .remove(KEY_BREAK_START_WALL)
            .remove(KEY_BREAK_DEADLINE_WALL)
            .remove(KEY_BREAK_BOOT_SESSION_ID)
            .apply()

        context?.let { ctx ->
            cancelBreakAlarm(ctx)
            dismissReminderNotification(ctx)
            showBreakCompletedNotification(ctx)
            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}
        }

        resetSession(context)
    }

    fun scheduleBreakCompletionAlarm(context: Context, deadlineWallMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context, BreakReminderActionReceiver::class.java).apply {
                action = BreakReminderActionReceiver.ACTION_BREAK_COMPLETE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1005,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    deadlineWallMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    deadlineWallMs,
                    pendingIntent
                )
            }
        } catch (e: Throwable) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
                val intent = Intent(context, BreakReminderActionReceiver::class.java).apply {
                    action = BreakReminderActionReceiver.ACTION_BREAK_COMPLETE
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1005,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    deadlineWallMs,
                    pendingIntent
                )
            } catch (e2: Throwable) {}
        }
    }

    fun cancelBreakAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context, BreakReminderActionReceiver::class.java).apply {
                action = BreakReminderActionReceiver.ACTION_BREAK_COMPLETE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1005,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Throwable) {}
    }

    /**
     * Action 2: Snoozes reminder for 5 minutes.
     * Stops sound, avoids re-alerting during snooze, re-alerts after 5m if screen still on.
     */
    fun snooze(context: Context? = null, minutes: Long = 5L) {
        val nowElapsed = clock.elapsedRealtime()
        val snoozeUntil = nowElapsed + (minutes * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_SNOOZE_UNTIL_ELAPSED, snoozeUntil)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, false) // Allow re-alert once snooze expires
            .apply()

        context?.let { ctx ->
            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}

            try {
                dismissReminderNotification(ctx)
            } catch (e: Throwable) {}
        }
    }

    /**
     * Action 3: Dismisses the reminder.
     * Stops sound, keeps timer running, but avoids repeated alerts for 15 minutes.
     */
    fun dismiss(context: Context? = null) {
        val nowElapsed = clock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_LAST_REMINDER_ELAPSED, nowElapsed)
            .putBoolean(KEY_ALERT_TRIGGERED_FOR_SESSION, true)
            .remove(KEY_BREAK_ACTIVE)
            .remove(KEY_BREAK_START_ELAPSED)
            .apply()

        context?.let { ctx ->
            try {
                WellnessSoundManager.getInstance(ctx).stop()
            } catch (e: Throwable) {}

            try {
                dismissReminderNotification(ctx)
            } catch (e: Throwable) {}
        }
    }

    /**
     * Returns continuous screen-on usage in milliseconds for the current session.
     * Switching apps does NOT reset this value.
     * Screen-off time during grace period does NOT add to continuous screen-on usage.
     */
    fun getContinuousScreenUsageMillis(context: Context? = null): Long {
        val prefs = getPrefs(context)
        val nowElapsed = clock.elapsedRealtime()
        val isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, false)
        val accumulated = prefs.getLong(KEY_ACCUMULATED_SCREEN_ON_MS, 0L)
        val sessionStart = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L)
        val screenOffElapsed = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, 0L)

        if (isScreenOn) {
            val currentSegment = if (sessionStart > 0L) {
                (nowElapsed - sessionStart).coerceAtLeast(0L)
            } else {
                0L
            }
            return (accumulated + currentSegment).coerceAtLeast(0L)
        } else {
            // Screen is OFF: check if within grace period
            if (screenOffElapsed > 0L) {
                val elapsedOff = nowElapsed - screenOffElapsed
                if (elapsedOff in 0L until FIVE_MINUTES_MILLIS) {
                    return accumulated.coerceAtLeast(0L)
                }
            }
            return 0L
        }
    }

    /**
     * Returns remaining seconds until the 30-minute wellness reminder.
     */
    fun getRemainingSecondsToThirtyMinutes(context: Context? = null): Long {
        val usageMillis = getContinuousScreenUsageMillis(context)
        val usageSecs = usageMillis / 1000L
        return (1800L - usageSecs).coerceAtLeast(0L)
    }

    /**
     * Evaluates whether 30 continuous minutes have elapsed and triggers the alarm.
     */
    fun checkAndTriggerReminder(context: Context? = null): Boolean {
        if (!isBreakAlertEnabled(context)) return false

        val prefs = getPrefs(context)
        val nowElapsed = clock.elapsedRealtime()

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

        val totalScreenUsage = getContinuousScreenUsageMillis(context)
        if (totalScreenUsage < THIRTY_MINUTES_MILLIS) {
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

        onAlarmTriggered?.invoke()

        // 4. Start pleasant sound / custom audio + TTS + gentle vibration
        context?.let { ctx ->
            try {
                if (soundManagerOverride != null) {
                    soundManagerOverride?.startWellnessAlarm()
                } else {
                    WellnessSoundManager.getInstance(ctx).startWellnessAlarm()
                }
            } catch (e: Throwable) {}

            // 5. Post High-Priority Notification with Fullscreen intent fallback
            try {
                showBreakReminderNotification(ctx)
            } catch (e: Throwable) {}

            // 6. Launch BreakAlertActivity UI immediately over current screen
            try {
                if (!BreakAlertActivity.isBreakAlertVisible) {
                    BreakAlertActivity.launch(ctx)
                }
            } catch (e: Throwable) {}
        }

        return true
    }

    @androidx.annotation.VisibleForTesting
    fun resetTestOverrides() {
        preferencesOverride = null
        clock = SystemWellnessClock
        isBreakAlertEnabledOverride = null
        soundManagerOverride = null
        onAlarmTriggered = null
        cancelPendingResetTask()
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
