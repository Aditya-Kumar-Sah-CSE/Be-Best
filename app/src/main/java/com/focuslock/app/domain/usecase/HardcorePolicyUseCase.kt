package com.focuslock.app.domain.usecase

import com.focuslock.app.core.security.PinSecurityManager

class HardcorePolicyUseCase(
    private val pinSecurityManager: PinSecurityManager
) {
    /**
     * In Hardcore Mode, the user cannot change limits or disable protection immediately.
     * They must trigger an unlock request and wait for a 60-second cooldown period.
     */
    fun canModifySettings(): Boolean {
        if (!pinSecurityManager.isHardcoreModeActive()) return true

        val requestTime = pinSecurityManager.getHardcoreUnlockRequestTime()
        if (requestTime == 0L) return false

        val elapsed = System.currentTimeMillis() - requestTime
        val requiredWait = 60 * 1000L // 60 seconds friction cooldown
        return elapsed >= requiredWait
    }

    fun requestUnlock() {
        pinSecurityManager.setHardcoreUnlockRequestTime(System.currentTimeMillis())
    }

    fun clearUnlockRequest() {
        pinSecurityManager.setHardcoreUnlockRequestTime(0L)
    }

    fun getRemainingWaitSeconds(): Long {
        val requestTime = pinSecurityManager.getHardcoreUnlockRequestTime()
        if (requestTime == 0L) return 60L

        val elapsed = System.currentTimeMillis() - requestTime
        val remaining = (60 * 1000L - elapsed) / 1000L
        return remaining.coerceAtLeast(0L)
    }
}
