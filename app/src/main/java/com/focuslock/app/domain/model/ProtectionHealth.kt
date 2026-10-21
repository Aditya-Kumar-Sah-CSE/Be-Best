package com.focuslock.app.domain.model

data class ProtectionHealth(
    val isUsageAccessGranted: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    val isOverlayPermissionGranted: Boolean
) {
    val isFullyProtected: Boolean
        get() = isUsageAccessGranted && isAccessibilityEnabled

    val healthScorePercentage: Int
        get() {
            var score = 0
            if (isUsageAccessGranted) score += 40
            if (isAccessibilityEnabled) score += 40
            if (isBatteryOptimizationIgnored) score += 10
            if (isOverlayPermissionGranted) score += 10
            return score
        }
}
