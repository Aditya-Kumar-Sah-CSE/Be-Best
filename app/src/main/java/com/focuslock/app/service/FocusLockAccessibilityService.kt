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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // User turned off screen -> Reset continuous session
                    continuousScreenStartElapsed = 0L
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // Screen turned on / unlocked -> Begin continuous tracking
                    continuousScreenStartElapsed = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        continuousScreenStartElapsed = SystemClock.elapsedRealtime()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        startContinuousBreakMonitor()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return

        // Exclude system UI components, launchers, and FocusLock itself
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher") ||
            packageName.contains("trebuchet") ||
            packageName == "android"
        ) {
            recordPreviousSession()
            currentForegroundPackage = null
            activeMonitoringJob?.cancel()
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
                // Reconcile with Android's UsageStatsManager first to ensure accurate measured time
                try {
                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(packageName)
                } catch (e: Exception) {
                    // Fall back to stored Room count if UsageStats is unavailable
                }

                val evaluation = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)

                if (evaluation.status == ProtectionState.EMERGENCY_ACTIVE) {
                    // Protected app is within its one-time 30-second emergency access window
                    if (isNewApp || activeMonitoringJob?.isActive != true) {
                        activeMonitoringJob?.cancel()
                        val remainingMs = (evaluation.remainingEmergencySeconds * 1000L).coerceAtLeast(100L)
                        activeMonitoringJob = serviceScope.launch {
                            delay(remainingMs)
                            val reEval = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                            if (reEval.isBlocked && currentForegroundPackage == packageName) {
                                launchBlockScreen(packageName, app.displayName)
                            }
                        }
                    }
                    return@launch
                }

                if (evaluation.isBlocked) {
                    launchBlockScreen(packageName, app.displayName)
                    return@launch
                }

                // If this is a new app entry or monitoring job was cancelled, start active ticker
                if (isNewApp || activeMonitoringJob?.isActive != true) {
                    activeMonitoringJob?.cancel()
                    activeMonitoringJob = serviceScope.launch {
                        val sessionStart = SystemClock.elapsedRealtime()
                        val initialUsed = evaluation.usedSeconds
                        val limitSeconds = app.dailyLimitSeconds

                        while (currentForegroundPackage == packageName) {
                            delay(1000L)
                            val elapsedInSession = (SystemClock.elapsedRealtime() - sessionStart) / 1000L
                            val currentTotal = initialUsed + elapsedInSession

                            // Persist usage to Room every 3 seconds so real-time counts are preserved
                            if (elapsedInSession > 0 && elapsedInSession % 3L == 0L) {
                                FocusLockApp.instance.quotaEngine.recordUsage(
                                    packageName = packageName,
                                    additionalSeconds = 3L,
                                    source = "ACCESSIBILITY_ACTIVE"
                                )
                            }

                            // Periodically re-sync with UsageStatsManager
                            if (elapsedInSession > 0 && elapsedInSession % 10L == 0L) {
                                try {
                                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(packageName)
                                } catch (e: Exception) {}
                            }

                            val reCheck = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                            if (reCheck.isBlocked || currentTotal >= limitSeconds) {
                                launchBlockScreen(packageName, app.displayName)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchBlockScreen(packageName: String, appName: String) {
        // Prevent repeated launch loops if BlockingActivity is already visibly displayed
        if (BlockingActivity.isBlockingScreenVisible && BlockingActivity.activeBlockedPackage == packageName) {
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == packageName && (nowElapsed - lastBlockLaunchElapsed) < 800L) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockLaunchElapsed = nowElapsed

        val intent = Intent(this, BlockingActivity::class.java).apply {
            putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockingActivity.EXTRA_APP_NAME, appName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    /**
     * Monitors continuous screen usage.
     * After 30 minutes of continuous screen-on time, alerts user to take a 5-minute break.
     */
    private fun startContinuousBreakMonitor() {
        breakMonitorJob?.cancel()
        breakMonitorJob = serviceScope.launch {
            while (true) {
                delay(10_000L) // Check every 10 seconds
                val start = continuousScreenStartElapsed
                if (start > 0L) {
                    val continuousElapsedMs = SystemClock.elapsedRealtime() - start
                    val thirtyMinutesMs = 30 * 60 * 1000L

                    if (continuousElapsedMs >= thirtyMinutesMs) {
                        val prefs = getSharedPreferences("focuslock_app_prefs", Context.MODE_PRIVATE)
                        val isBreakAlertEnabled = prefs.getBoolean("continuous_break_alert", true)

                        if (isBreakAlertEnabled && !BreakAlertActivity.isBreakAlertVisible && !BlockingActivity.isBlockingScreenVisible) {
                            // Reset timer so it triggers again after another 30 mins
                            continuousScreenStartElapsed = SystemClock.elapsedRealtime()
                            BreakAlertActivity.launch(applicationContext)
                        }
                    }
                }
            }
        }
    }

    private fun recordPreviousSession() {
        activeMonitoringJob?.cancel()
        val prevPkg = currentForegroundPackage ?: return
        val start = currentSessionStartElapsed
        if (start > 0L) {
            val elapsedSecs = (SystemClock.elapsedRealtime() - start) / 1000L
            serviceScope.launch {
                try {
                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(prevPkg)
                } catch (e: Exception) {}
            }
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
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()
    }
}
