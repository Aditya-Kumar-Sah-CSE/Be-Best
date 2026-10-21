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

        // Ignore our own app screens and system UI like notification bar
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher")
        ) {
            recordPreviousSession()
            currentForegroundPackage = null
            return
        }

        if (packageName != currentForegroundPackage) {
            recordPreviousSession()
            currentForegroundPackage = packageName
            currentSessionStartElapsed = SystemClock.elapsedRealtime()

            // Check if app is blocked
            checkAndEnforceBlock(packageName)
        }
    }

    private fun checkAndEnforceBlock(packageName: String) {
        serviceScope.launch {
            val app = FocusLockApp.instance.appRepository.getApp(packageName)
            if (app != null && app.enabled) {
                val isBlocked = FocusLockApp.instance.usageRepository.isAppBlocked(packageName)
                if (isBlocked) {
                    launchBlockScreen(packageName, app.displayName)
                }
            }
        }
    }

    private fun launchBlockScreen(packageName: String, appName: String) {
        val intent = Intent(this, BlockingActivity::class.java).apply {
            putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockingActivity.EXTRA_APP_NAME, appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
    }

    private fun recordPreviousSession() {
        val prevPkg = currentForegroundPackage ?: return
        val start = currentSessionStartElapsed
        if (start > 0) {
            val elapsedSecs = (SystemClock.elapsedRealtime() - start) / 1000
            if (elapsedSecs > 1) {
                serviceScope.launch {
                    FocusLockApp.instance.usageRepository.recordForegroundSession(
                        packageName = prevPkg,
                        durationSeconds = elapsedSecs,
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
