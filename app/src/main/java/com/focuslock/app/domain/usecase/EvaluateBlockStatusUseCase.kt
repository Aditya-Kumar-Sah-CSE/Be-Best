package com.focuslock.app.domain.usecase

import com.focuslock.app.data.local.dao.FocusSessionDao
import com.focuslock.app.data.repository.AppRepository
import com.focuslock.app.data.repository.UsageRepository

class EvaluateBlockStatusUseCase(
    private val appRepository: AppRepository,
    private val usageRepository: UsageRepository,
    private val focusSessionDao: FocusSessionDao
) {
    /**
     * Determines whether an app should be blocked right now.
     * Block conditions:
     * 1. Daily quota exhausted (usedSeconds >= dailyLimitSeconds)
     * 2. Active Focus/Pomodoro Session is currently running and app is a protected distraction
     */
    suspend fun shouldBlockApp(packageName: String): Boolean {
        val app = appRepository.getApp(packageName) ?: return false
        if (!app.enabled) return false

        // Check 1: Active Focus Session
        val activeFocus = focusSessionDao.getActiveSession()
        if (activeFocus != null) {
            val now = System.currentTimeMillis()
            if (now < activeFocus.plannedEndAt) {
                return true
            }
        }

        // Check 2: Daily Quota Limit
        return usageRepository.isAppBlocked(packageName)
    }
}
