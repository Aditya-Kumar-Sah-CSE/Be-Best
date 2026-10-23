package com.focuslock.app.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreakReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.focuslock.app.ACTION_SNOOZE_BREAK_REMINDER"
        const val ACTION_DISMISS = "com.focuslock.app.ACTION_DISMISS_BREAK_REMINDER"
        const val ACTION_START_BREAK = "com.focuslock.app.ACTION_START_BREAK"
        const val ACTION_BREAK_COMPLETE = "com.focuslock.app.ACTION_BREAK_COMPLETE"
        const val ACTION_SCREEN_OFF_EXPIRED = "com.focuslock.app.ACTION_SCREEN_OFF_EXPIRED"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        when (intent.action) {
            ACTION_SNOOZE -> {
                BreakReminderHelper.snooze(context, minutes = 5L)
            }
            ACTION_DISMISS -> {
                BreakReminderHelper.dismiss(context)
            }
            ACTION_START_BREAK -> {
                BreakReminderHelper.startBreak(context)
                try {
                    com.focuslock.app.presentation.breakalert.BreakAlertActivity.launch(context, autoStart = true)
                } catch (e: Exception) {}
            }
            ACTION_BREAK_COMPLETE -> {
                BreakReminderHelper.completeBreak(context)
            }
            ACTION_SCREEN_OFF_EXPIRED -> {
                BreakReminderHelper.onScreenOffGraceExpired(context)
            }
        }
    }
}
