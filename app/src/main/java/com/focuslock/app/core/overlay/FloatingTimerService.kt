package com.focuslock.app.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focuslock.app.core.util.BreakReminderHelper
import com.focuslock.app.presentation.MainActivity
import com.focuslock.app.presentation.breakalert.BreakAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Universal foreground service responsible for:
 * 1. Maintaining the persistent floating screen-time pill overlay over third-party apps
 *    (Instagram, YouTube, WhatsApp, Chrome, games, etc.) even when FocusLock is closed.
 * 2. Continuously tracking screen-on duration via broadcast receivers and monotonic elapsed time.
 * 3. Checking and firing the 30-minute break reminder alert on time.
 */
class FloatingTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorJob: Job? = null
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    BreakReminderHelper.onScreenOff(applicationContext)
                    FloatingTimerManager.onScreenOff(applicationContext)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    BreakReminderHelper.onScreenOn(applicationContext)
                    FloatingTimerManager.onScreenOn(applicationContext)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()

        // Register dynamic screen on/off receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        } catch (e: Exception) {}

        // Initialize session and overlay according to actual screen interactive state
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == true) {
            BreakReminderHelper.onScreenOn(applicationContext)
            FloatingTimerManager.show(applicationContext)
        } else {
            BreakReminderHelper.onScreenOff(applicationContext)
            FloatingTimerManager.hide()
        }

        // Start background verification and alert check loop
        startMonitorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                FloatingTimerManager.hide()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_OVERLAY -> {
                FloatingTimerManager.show(applicationContext)
            }
            ACTION_HIDE_OVERLAY -> {
                FloatingTimerManager.hide()
            }
        }

        FloatingTimerManager.refresh(applicationContext)
        return START_STICKY
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (true) {
                delay(3000L)
                try {
                    // Check if permission revoked or disabled
                    if (!FloatingTimerManager.hasOverlayPermission(applicationContext) ||
                        !FloatingTimerManager.isFloatingTimerEnabled(applicationContext)
                    ) {
                        FloatingTimerManager.hide()
                    } else {
                        FloatingTimerManager.ensureVisible(applicationContext)
                    }

                    // Check continuous screen break reminder (30m trigger)
                    BreakReminderHelper.checkAndTriggerReminder(applicationContext)
                } catch (e: Exception) {}
            }
        }
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ignored: Exception) {}
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            5001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val breakIntent = Intent(this, BreakAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val breakPendingIntent = PendingIntent.getActivity(
            this,
            5002,
            breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FocusLock Screen-Time Timer")
            .setContentText("System-wide floating timer active over other apps")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_dialog_info,
                "Open FocusLock",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Break Alert",
                breakPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusLock Screen-Time Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps universal floating screen timer running smoothly over apps."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {}
            screenReceiverRegistered = false
        }
        FloatingTimerManager.hide()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "focuslock_floating_timer_service_channel"
        const val NOTIFICATION_ID = 9527

        const val ACTION_START_SERVICE = "com.focuslock.app.ACTION_START_FLOATING_SERVICE"
        const val ACTION_STOP_SERVICE = "com.focuslock.app.ACTION_STOP_FLOATING_SERVICE"
        const val ACTION_SHOW_OVERLAY = "com.focuslock.app.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.focuslock.app.ACTION_HIDE_OVERLAY"

        fun start(context: Context) {
            if (!FloatingTimerManager.isFloatingTimerEnabled(context) ||
                !FloatingTimerManager.hasOverlayPermission(context)
            ) return

            try {
                val intent = Intent(context, FloatingTimerService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingTimerService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.stopService(intent)
            } catch (e: Exception) {}
        }
    }
}
