package com.focuslock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.app.FocusLockApp
import com.focuslock.app.domain.usecase.ResetMidnightQuotaUseCase
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
                    details = "System clock or timezone event detected: $action"
                )

                // Validate calendar date boundary
                val resetUseCase = ResetMidnightQuotaUseCase(
                    app.database.protectedAppDao(),
                    app.database.usageDao()
                )
                resetUseCase.resetQuotasForNewDay()
            }
        }
    }
}
