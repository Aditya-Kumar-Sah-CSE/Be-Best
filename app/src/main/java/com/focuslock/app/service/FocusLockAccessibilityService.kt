package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.FocusLockApp
import com.focuslock.app.presentation.BlockingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FocusLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentForegroundPackage: String? = null
    private var currentSessionStartElapsed: Long = 0L

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
                if (evaluation.isBlocked) {
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
