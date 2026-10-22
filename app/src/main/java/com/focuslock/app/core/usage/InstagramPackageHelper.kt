package com.focuslock.app.core.usage

import android.content.Context
import android.content.pm.PackageManager

/**
 * Dynamically resolves and identifies installed Instagram packages on the device.
 * Handles standard Instagram, Instagram Lite, and package variations.
 */
object InstagramPackageHelper {

    const val PACKAGE_INSTAGRAM = "com.instagram.android"
    const val PACKAGE_INSTAGRAM_LITE = "com.instagram.lite"
    const val PACKAGE_THREADS = "com.instagram.threadsapp"

    val KNOWN_INSTAGRAM_PACKAGES = listOf(
        PACKAGE_INSTAGRAM,
        PACKAGE_INSTAGRAM_LITE,
        PACKAGE_THREADS
    )

    fun getInstalledInstagramPackage(packageManager: PackageManager): String? {
        for (pkg in KNOWN_INSTAGRAM_PACKAGES) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: Exception) {}
        }
        return null
    }

    fun isInstagramInstalled(packageManager: PackageManager): Boolean {
        return getInstalledInstagramPackage(packageManager) != null
    }

    fun isInstagramPackage(packageName: String): Boolean {
        return packageName in KNOWN_INSTAGRAM_PACKAGES ||
                packageName.startsWith("com.instagram.", ignoreCase = true)
    }

    fun getInstagramVariantLabel(packageName: String): String {
        return when (packageName) {
            PACKAGE_INSTAGRAM -> "Instagram"
            PACKAGE_INSTAGRAM_LITE -> "Instagram Lite"
            PACKAGE_THREADS -> "Threads"
            else -> "Instagram Variant"
        }
    }
}
