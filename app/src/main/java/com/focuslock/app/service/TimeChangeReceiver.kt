package com.focuslock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.app.FocusLockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            val app = FocusLockApp.instance
            CoroutineScope(Dispatchers.IO).launch {
                app.usageRepository.logProtectionEvent(
                    eventType = "TIMEZONE_CHANGED",
                    details = "System time or timezone change detected ($action); evaluating daily boundaries"
                )
                app.quotaEngine.checkAndPerformMidnightReset()
                FocusLockAccessibilityService.refreshBlockedPackagesCache()
            }
        }
    }
}
