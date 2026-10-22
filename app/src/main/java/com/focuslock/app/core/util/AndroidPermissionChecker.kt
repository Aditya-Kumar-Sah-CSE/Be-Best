package com.focuslock.app.core.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.focuslock.app.domain.engine.PermissionChecker
import com.focuslock.app.service.FocusLockAccessibilityService

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        // 1. Authoritative OS check via AccessibilityManager
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                if (enabledServices.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Fall back to Secure.getString check
        }

        // 2. Fallback check via Settings.Secure
        val canonical = "${context.packageName}/${FocusLockAccessibilityService::class.java.canonicalName}"
        val shortName = "${context.packageName}/.service.FocusLockAccessibilityService"
        val altName = "${context.packageName}/${FocusLockAccessibilityService::class.java.name}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(canonical) || enabledServices.contains(shortName) || enabledServices.contains(altName)
    }

    fun hasNotificationPermission(): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun areEnforcementPermissionsActive(): Boolean {
        return isUsageAccessGranted() && isAccessibilityServiceEnabled()
    }
}
