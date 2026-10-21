package com.focuslock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.app.FocusLockApp
import com.focuslock.app.domain.usecase.ResetMidnightQuotaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = FocusLockApp.instance
            CoroutineScope(Dispatchers.IO).launch {
                // Log reboot event
                app.usageRepository.logProtectionEvent("REBOOT", "Device reboot detected; validating quota state")

                // Ensure quotas are properly initialized for current calendar day
                val resetUseCase = ResetMidnightQuotaUseCase(
                    app.database.protectedAppDao(),
                    app.database.usageDao()
                )
                resetUseCase.resetQuotasForNewDay()
            }
        }
    }
}
