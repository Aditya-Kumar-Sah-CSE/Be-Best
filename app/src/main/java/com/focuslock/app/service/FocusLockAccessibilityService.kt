package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.FocusLockApp
import com.focuslock.app.domain.engine.ProtectionState
import com.focuslock.app.presentation.BlockingActivity
import com.focuslock.app.presentation.breakalert.BreakAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentForegroundPackage: String? = null
    private var currentSessionStartElapsed: Long = 0L
    private var activeMonitoringJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockLaunchElapsed: Long = 0L

    // Continuous Screen Time Tracking (30-Minute Alert)
    private var continuousScreenStartElapsed: Long = 0L
    private var breakMonitorJob: Job? = null

    private val launcherPackages = mutableSetOf<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    recordPreviousSession()
                    com.focuslock.app.core.util.BreakReminderHelper.onScreenOff(applicationContext)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    com.focuslock.app.core.util.BreakReminderHelper.onScreenOn(applicationContext)
                    refreshBlockedPackagesCache()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        refreshLauncherPackages()
        refreshBlockedPackagesCache()
        com.focuslock.app.core.util.BreakReminderHelper.onScreenOn(applicationContext)
        com.focuslock.app.core.util.BreakReminderHelper.createNotificationChannel(applicationContext)
        com.focuslock.app.core.overlay.FloatingTimerService.start(applicationContext)
        com.focuslock.app.core.overlay.FloatingTimerManager.show(applicationContext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        startContinuousBreakMonitor()
    }

    private fun refreshLauncherPackages() {
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
            synchronized(launcherPackages) {
                launcherPackages.clear()
                for (info in resolveInfos) {
                    info.activityInfo?.packageName?.let { launcherPackages.add(it) }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun refreshBlockedPackagesCache() {
        serviceScope.launch {
            try {
                val today = com.focuslock.app.core.util.TimeUtils.getTodayDateString()
                val protectedApps = FocusLockApp.instance.database.protectedAppDao().getActiveProtectedApps()
                for (app in protectedApps) {
                    appNamesCache[app.packageName] = app.displayName
                    val usage = FocusLockApp.instance.database.usageDao().getDailyUsageForApp(app.packageName, today)
                    val isEmergency = usage != null && usage.emergencyUsed && System.currentTimeMillis() < usage.emergencyExpiresAt
                    val isExhausted = usage != null && (usage.isBlocked || usage.usedSeconds >= app.dailyLimitSeconds)
                    if (isExhausted && !isEmergency) {
                        blockedPackagesCache.add(app.packageName)
                    } else {
                        blockedPackagesCache.remove(app.packageName)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun isExcludedPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == applicationContext.packageName) return true
        if (packageName == "com.android.systemui" || packageName == "android") return true
        if (packageName == "com.google.android.permissioncontroller" || packageName == "com.android.permissioncontroller") return true
        if (packageName == "com.google.android.packageinstaller" || packageName == "com.android.packageinstaller") return true
        if (packageName == "com.android.settings") return true
        synchronized(launcherPackages) {
            if (launcherPackages.contains(packageName)) return true
        }
        val lower = packageName.lowercase()
        return lower.contains("launcher") || lower.contains("trebuchet") || lower.endsWith(".home") || lower.contains(".home.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val pkg = event.packageName?.toString() ?: return
            if (!isExcludedPackage(pkg)) {
                recordScrollInteraction(pkg)
            }
            return
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return

        // Maintain universal floating screen-time timer overlay across all apps (Instagram, YouTube, etc.)
        com.focuslock.app.core.overlay.FloatingTimerManager.ensureVisible(applicationContext)

        // Exclude system UI components, launchers, and FocusLock itself
        if (isExcludedPackage(packageName)) {
            recordPreviousSession()
            currentForegroundPackage = null
            activeMonitoringJob?.cancel()
            return
        }

        // Fast In-Memory Check: If package is already known to be blocked and no emergency bypass,
        // intercept immediately on the main thread so Instagram does not remain usable in the foreground.
        if (blockedPackagesCache.contains(packageName)) {
            val cachedName = appNamesCache[packageName]
                ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            launchBlockScreen(packageName, cachedName)
            return
        }

        if (packageName != currentForegroundPackage) {
            recordPreviousSession()
            currentForegroundPackage = packageName
            currentSessionStartElapsed = SystemClock.elapsedRealtime()

            // Synchronize and enforce per-app daily limit immediately on new foreground app
            checkAndEnforceBlock(packageName, isNewApp = true)
        } else {
            // Same package changed windows (e.g. reels/stories in Instagram). Check block without resetting timer.
            checkAndEnforceBlock(packageName, isNewApp = false)
        }
    }

    private fun checkAndEnforceBlock(packageName: String, isNewApp: Boolean) {
        serviceScope.launch {
            // Check if app is explicitly protected OR if it's a known distraction (e.g., Instagram, YouTube)
            var app = FocusLockApp.instance.appRepository.getApp(packageName)
            if (app == null) {
                val category = com.focuslock.app.domain.model.AppCategory.inferCategory(packageName)
                if (category.isSuggestedDistraction) {
                    val appName = try {
                        val ai = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(ai).toString()
                    } catch (e: Exception) {
                        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    }
                    FocusLockApp.instance.appRepository.setAppProtection(
                        packageName = packageName,
                        appName = appName,
                        enabled = true,
                        limitSeconds = 1800L,
                        category = category
                    )
                    app = FocusLockApp.instance.appRepository.getApp(packageName)
                }
            }

            if (app != null && app.enabled) {
                appNamesCache[packageName] = app.displayName

                // 1. Evaluate quota FIRST (fast SQLite query, < 2ms)
                val evaluation = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)

                if (evaluation.status == ProtectionState.EMERGENCY_ACTIVE) {
                    blockedPackagesCache.remove(packageName)
                    // Protected app is within its one-time 30-second emergency access window
                    if (isNewApp || activeMonitoringJob?.isActive != true) {
                        activeMonitoringJob?.cancel()
                        val remainingMs = (evaluation.remainingEmergencySeconds * 1000L).coerceAtLeast(100L)
                        activeMonitoringJob = serviceScope.launch {
                            delay(remainingMs)
                            val reEval = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                            if (reEval.isBlocked) {
                                blockedPackagesCache.add(packageName)
                                if (currentForegroundPackage == packageName) {
                                    launchBlockScreen(packageName, app.displayName)
                                }
                            }
                        }
                    }
                    return@launch
                }

                if (evaluation.isBlocked) {
                    blockedPackagesCache.add(packageName)
                    launchBlockScreen(packageName, app.displayName)
                    return@launch
                }

                // 2. Reconcile with Android's UsageStatsManager in background to verify real foreground time
                try {
                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(packageName)
                } catch (e: Exception) {
                    // Fall back to stored Room count if UsageStats is unavailable
                }

                val postSyncEval = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                if (postSyncEval.isBlocked) {
                    blockedPackagesCache.add(packageName)
                    launchBlockScreen(packageName, app.displayName)
                    return@launch
                }

                // 3. App is allowed. Start active monitoring ticker if new app or cancelled
                if (isNewApp || activeMonitoringJob?.isActive != true) {
                    activeMonitoringJob?.cancel()
                    activeMonitoringJob = serviceScope.launch {
                        val sessionStart = SystemClock.elapsedRealtime()
                        var lastRecordedSessionSecs = 0L

                        while (currentForegroundPackage == packageName) {
                            delay(1000L)
                            val elapsedInSession = (SystemClock.elapsedRealtime() - sessionStart) / 1000L
                            val delta = elapsedInSession - lastRecordedSessionSecs

                            // Record incremental usage to QuotaEngine every 2 seconds
                            if (delta >= 2L) {
                                FocusLockApp.instance.quotaEngine.recordUsage(
                                    packageName = packageName,
                                    additionalSeconds = delta,
                                    source = "ACCESSIBILITY_ACTIVE"
                                )
                                lastRecordedSessionSecs = elapsedInSession
                            }

                            // Periodically re-sync with UsageStatsManager
                            if (elapsedInSession > 0 && elapsedInSession % 10L == 0L) {
                                try {
                                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(packageName)
                                } catch (e: Exception) {}
                            }

                            val reCheck = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                            if (reCheck.isBlocked) {
                                blockedPackagesCache.add(packageName)
                                launchBlockScreen(packageName, app.displayName)
                                delay(1000L)
                            }
                        }
                    }
                }
            } else {
                blockedPackagesCache.remove(packageName)
            }
        }
    }

    private fun launchBlockScreen(packageName: String, appName: String) {
        val nowElapsed = SystemClock.elapsedRealtime()

        // Prevent repeated activity launch loops if BlockingActivity is already visibly displayed
        if (BlockingActivity.isBlockingScreenVisible && BlockingActivity.activeBlockedPackage == packageName) {
            return
        }

        if (lastBlockedPackage == packageName && (nowElapsed - lastBlockLaunchElapsed) < 400L) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockLaunchElapsed = nowElapsed

        // Launch FocusLock's BlockingActivity overlay immediately
        val intent = Intent(this@FocusLockAccessibilityService, BlockingActivity::class.java).apply {
            putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockingActivity.EXTRA_APP_NAME, appName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        var activityStarted = false
        try {
            startActivity(intent)
            activityStarted = true
        } catch (e: Exception) {
            activityStarted = false
        }

        if (!activityStarted) {
            // Android-supported fallback when background Activity launch restrictions apply:
            // 1. Move restricted app out of the foreground immediately to the Home screen
            try {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (e: Exception) {
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                } catch (ex: Exception) {}
            }
            // 2. Post high-priority notification with full-screen intent
            showBlockingNotification(packageName, appName)
        }
    }

    private fun showBlockingNotification(packageName: String, appName: String) {
        try {
            val intent = Intent(this, BlockingActivity::class.java).apply {
                putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(BlockingActivity.EXTRA_APP_NAME, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                packageName.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(this, FocusLockApp.CHANNEL_ID_PROTECTION)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("$appName is Locked")
                .setContentText("You have reached your 30-minute daily limit.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            manager?.notify(packageName.hashCode(), builder.build())
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Monitors continuous screen usage.
     * After 30 minutes of continuous screen-on time, alerts user to take a 5-minute break.
     */
    private fun startContinuousBreakMonitor() {
        breakMonitorJob?.cancel()
        breakMonitorJob = serviceScope.launch {
            while (true) {
                delay(5000L) // Check every 5 seconds
                try {
                    if (!BlockingActivity.isBlockingScreenVisible) {
                        com.focuslock.app.core.util.BreakReminderHelper.checkAndTriggerReminder(applicationContext)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun recordPreviousSession() {
        activeMonitoringJob?.cancel()
        val prevPkg = currentForegroundPackage ?: return
        val start = currentSessionStartElapsed
        if (start > 0L) {
            val elapsedSecs = (SystemClock.elapsedRealtime() - start) / 1000L
            currentSessionStartElapsed = 0L
            serviceScope.launch {
                try {
                    if (elapsedSecs > 0L) {
                        FocusLockApp.instance.quotaEngine.recordUsage(
                            packageName = prevPkg,
                            additionalSeconds = elapsedSecs % 2L,
                            source = "ACCESSIBILITY_REMAINDER"
                        )
                    }
                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(prevPkg)
                } catch (e: Exception) {}
            }
        }
    }

    private var lastScrollTimeMs = 0L

    /**
     * Privacy-Preserving Activity Estimation:
     * Counts approximate scroll events without inspecting or logging text, UI hierarchy,
     * messages, credentials, or capturing any screenshots.
     */
    private fun recordScrollInteraction(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTimeMs < 500L) return
        lastScrollTimeMs = now
        val todayStr = com.focuslock.app.core.util.TimeUtils.getTodayDateString()
        serviceScope.launch {
            try {
                FocusLockApp.instance.database.usageDao().incrementScrollCount(
                    packageName = packageName,
                    date = todayStr,
                    count = 1,
                    timestamp = now
                )
            } catch (e: Exception) {}
        }
    }

    override fun onInterrupt() {
        recordPreviousSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        recordPreviousSession()
        breakMonitorJob?.cancel()
        activeMonitoringJob?.cancel()
        com.focuslock.app.core.overlay.FloatingTimerManager.hide()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()
    }

    companion object {
        private val blockedPackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val appNamesCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun isPackageBlockedFast(packageName: String): Boolean {
            return blockedPackagesCache.contains(packageName)
        }

        fun updateBlockedPackage(packageName: String, isBlocked: Boolean, appName: String? = null) {
            if (appName != null) {
                appNamesCache[packageName] = appName
            }
            if (isBlocked) {
                blockedPackagesCache.add(packageName)
            } else {
                blockedPackagesCache.remove(packageName)
            }
        }

        fun clearBlockedCache() {
            blockedPackagesCache.clear()
        }
    }
}
