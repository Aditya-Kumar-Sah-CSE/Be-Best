package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.FocusLockApp
import com.focuslock.app.domain.engine.ProtectionState
import com.focuslock.app.presentation.BlockingActivity
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
    private var emergencyExpiryJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockLaunchElapsed: Long = 0L

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
            return
        }

        if (packageName != currentForegroundPackage) {
            recordPreviousSession()
            currentForegroundPackage = packageName
            currentSessionStartElapsed = SystemClock.elapsedRealtime()

            // Evaluate authoritative quota and enforce blocking if exhausted
            checkAndEnforceBlock(packageName)
        }
    }

    private fun checkAndEnforceBlock(packageName: String) {
        serviceScope.launch {
            val app = FocusLockApp.instance.appRepository.getApp(packageName)
            if (app != null && app.enabled) {
                val evaluation = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                if (evaluation.status == ProtectionState.EMERGENCY_ACTIVE) {
                    // Protected app is within its one-time 30-second emergency access window
                    emergencyExpiryJob?.cancel()
                    val remainingMs = (evaluation.remainingEmergencySeconds * 1000L).coerceAtLeast(100L)
                    emergencyExpiryJob = serviceScope.launch {
                        delay(remainingMs)
                        // Once 30s expires: re-evaluate and block immediately if user is still inside app
                        val reEval = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
                        if (reEval.isBlocked && currentForegroundPackage == packageName) {
                            launchBlockScreen(packageName, app.displayName)
                        }
                    }
                    return@launch
                }

                if (evaluation.isBlocked) {
                    // Prevent repeated launch loops if BlockingActivity is already visibly displayed
                    if (BlockingActivity.isBlockingScreenVisible && BlockingActivity.activeBlockedPackage == packageName) {
                        return@launch
                    }

                    val nowElapsed = SystemClock.elapsedRealtime()
                    if (lastBlockedPackage == packageName && (nowElapsed - lastBlockLaunchElapsed) < 1200L) {
                        return@launch
                    }

                    lastBlockedPackage = packageName
                    lastBlockLaunchElapsed = nowElapsed
                    launchBlockScreen(packageName, app.displayName)
                }
            }
        }
    }

    private fun launchBlockScreen(packageName: String, appName: String) {
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

    private fun recordPreviousSession() {
        emergencyExpiryJob?.cancel()
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
        serviceScope.cancel()
    }
}
