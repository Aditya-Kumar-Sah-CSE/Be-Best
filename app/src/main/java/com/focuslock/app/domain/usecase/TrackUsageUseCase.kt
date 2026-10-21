package com.focuslock.app.domain.usecase

import com.focuslock.app.data.repository.AppRepository
import com.focuslock.app.data.repository.UsageRepository

class TrackUsageUseCase(
    private val usageRepository: UsageRepository,
    private val appRepository: AppRepository
) {
    suspend fun recordSession(packageName: String, durationSeconds: Long) {
        val app = appRepository.getApp(packageName)
        if (app != null && app.enabled) {
            usageRepository.recordForegroundSession(packageName, durationSeconds, "ACCESSIBILITY")
        }
    }
}
