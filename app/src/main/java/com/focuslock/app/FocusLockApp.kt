package com.focuslock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.focuslock.app.core.security.PinSecurityManager
import com.focuslock.app.core.usage.RealUsageTracker
import com.focuslock.app.core.util.SystemTimeProvider
import com.focuslock.app.data.local.FocusLockDatabase
import com.focuslock.app.data.repository.AppRepositoryImpl
import com.focuslock.app.data.repository.UsageRepositoryImpl
import com.focuslock.app.domain.engine.QuotaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusLockApp : Application() {

    lateinit var database: FocusLockDatabase
        private set

    lateinit var pinSecurityManager: PinSecurityManager
        private set

    lateinit var appRepository: AppRepositoryImpl
        private set

    lateinit var usageRepository: UsageRepositoryImpl
        private set

    lateinit var quotaEngine: QuotaEngine
        private set

    lateinit var realUsageTracker: RealUsageTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Local Room Database
        database = FocusLockDatabase.getInstance(this)
        pinSecurityManager = PinSecurityManager(this)

        // Repositories & Engine
        appRepository = AppRepositoryImpl(database.protectedAppDao(), this)
        usageRepository = UsageRepositoryImpl(database.usageDao(), this)

        quotaEngine = QuotaEngine(
            protectedAppDao = database.protectedAppDao(),
            usageDao = database.usageDao(),
            focusSessionDao = database.focusSessionDao(),
            timeProvider = SystemTimeProvider(),
            permissionChecker = com.focuslock.app.core.util.AndroidPermissionChecker(this)
        )

        realUsageTracker = RealUsageTracker(
            context = this,
            protectedAppDao = database.protectedAppDao(),
            usageDao = database.usageDao(),
            timeProvider = SystemTimeProvider()
        )

        createNotificationChannels()

        // Start universal floating screen timer if enabled and permission granted
        if (com.focuslock.app.core.overlay.FloatingTimerManager.isFloatingTimerEnabled(this) &&
            com.focuslock.app.core.overlay.FloatingTimerManager.hasOverlayPermission(this)
        ) {
            com.focuslock.app.core.overlay.FloatingTimerService.start(this)
            com.focuslock.app.core.overlay.FloatingTimerManager.show(this)
        }

        // Ensure default distractions (Instagram, YouTube, etc.) have 30-min limits on startup
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (database.protectedAppDao().getActiveProtectedApps().isEmpty()) {
                    appRepository.selectAllSuggestedDistractions(1800L)
                }
            } catch (e: Exception) {}
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                "FocusLock Protection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for quota alerts and protection health status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_PROTECTION = "focuslock_protection_channel"
        lateinit var instance: FocusLockApp
            private set
    }
}
