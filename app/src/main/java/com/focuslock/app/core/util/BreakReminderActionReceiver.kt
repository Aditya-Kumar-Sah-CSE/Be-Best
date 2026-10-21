package com.focuslock.app.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreakReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.focuslock.app.ACTION_SNOOZE_BREAK_REMINDER"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == ACTION_SNOOZE) {
            BreakReminderHelper.snooze(context, minutes = 5L)
        }
    }
}
