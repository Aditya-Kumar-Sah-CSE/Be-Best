package com.focuslock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.focuslock.app.core.security.PinSecurityManager
import com.focuslock.app.data.local.FocusLockDatabase
import com.focuslock.app.data.repository.AppRepositoryImpl
import com.focuslock.app.data.repository.UsageRepositoryImpl

class FocusLockApp : Application() {

    lateinit var database: FocusLockDatabase
        private set

    lateinit var pinSecurityManager: PinSecurityManager
        private set

    lateinit var appRepository: AppRepositoryImpl
        private set

    lateinit var usageRepository: UsageRepositoryImpl
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Local Room Database
        database = FocusLockDatabase.getInstance(this)
        pinSecurityManager = PinSecurityManager(this)

        // Repositories
        appRepository = AppRepositoryImpl(database.protectedAppDao(), packageManager)
        usageRepository = UsageRepositoryImpl(database.usageDao(), this)

        createNotificationChannels()
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
