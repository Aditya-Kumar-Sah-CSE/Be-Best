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
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

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

            // Synchronize and enforce per-app daily limit
            checkAndEnforceBlock(packageName)
        } else {
            // Same package changed windows (e.g. Activity switch inside Instagram)
            checkAndEnforceBlock(packageName)
        }
    }

    private fun checkAndEnforceBlock(packageName: String) {
        serviceScope.launch {
            val app = FocusLockApp.instance.appRepository.getApp(packageName)
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
                    activeMonitoringJob?.cancel()
                    val remainingMs = (evaluation.remainingEmergencySeconds * 1000L).coerceAtLeast(100L)
                    activeMonitoringJob = serviceScope.launch {
                        delay(remainingMs)
                        val reEval = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                        if (reEval.isBlocked && currentForegroundPackage == packageName) {
                            launchBlockScreen(packageName, app.displayName)
                        }
                    }
                    return@launch
                }

                if (evaluation.isBlocked) {
                    launchBlockScreen(packageName, app.displayName)
                    return@launch
                }

                // App is currently allowed with remaining time: monitor actively while in foreground
                val remainingSeconds = evaluation.remainingSeconds
                if (remainingSeconds > 0) {
                    activeMonitoringJob?.cancel()
                    activeMonitoringJob = serviceScope.launch {
                        var remaining = remainingSeconds
                        while (remaining > 0 && currentForegroundPackage == packageName) {
                            delay(1000L)
                            remaining--

                            // Periodically re-sync every 10 seconds
                            if (remaining % 10 == 0L) {
                                try {
                                    FocusLockApp.instance.realUsageTracker.syncPackageUsage(packageName)
                                } catch (e: Exception) {}
                                val reCheck = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                                if (reCheck.isBlocked) {
                                    launchBlockScreen(packageName, app.displayName)
                                    break
                                }
                                remaining = reCheck.remainingSeconds
                            }
                        }

                        if (currentForegroundPackage == packageName) {
                            // Limit hit while user was actively in the app!
                            launchBlockScreen(packageName, app.displayName)
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
        if (lastBlockedPackage == packageName && (nowElapsed - lastBlockLaunchElapsed) < 1200L) {
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
                Intent.FLAG_ACTIVITY_NO_ANIMATION
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
            if (elapsedSecs >= 1L) {
                serviceScope.launch {
                    FocusLockApp.instance.quotaEngine.recordUsage(
                        packageName = prevPkg,
                        additionalSeconds = elapsedSecs,
                        source = "ACCESSIBILITY"
                    )
                }
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
