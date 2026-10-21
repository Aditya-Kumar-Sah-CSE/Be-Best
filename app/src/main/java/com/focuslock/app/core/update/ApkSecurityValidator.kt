package com.focuslock.app.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Arrays

sealed class ApkValidationResult {
    object Valid : ApkValidationResult()
    data class ChecksumMismatch(val expected: String, val actual: String) : ApkValidationResult()
    data class InvalidArchive(val reason: String) : ApkValidationResult()
    data class PackageMismatch(val expected: String, val found: String) : ApkValidationResult()
    object CertificateMismatch : ApkValidationResult()
}

object ApkSecurityValidator {

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val actual = calculateSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun validateApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedPackage: String = context.packageName
    ): ApkValidationResult {
        // 1. Checksum validation
        val actualSha256 = calculateSha256(apkFile)
        if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            return ApkValidationResult.ChecksumMismatch(expected = expectedSha256, actual = actualSha256)
        }

        // 2. Archive validity check
        val pm = context.packageManager
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            ?: return ApkValidationResult.InvalidArchive("Package manager could not parse APK archive")

        // 3. Package name verification
        if (archiveInfo.packageName != expectedPackage) {
            return ApkValidationResult.PackageMismatch(
                expected = expectedPackage,
                found = archiveInfo.packageName ?: "unknown"
            )
        }

        // 4. Signing certificate verification against currently installed application
        val archiveSignatures = getArchiveSignatures(archiveInfo)
        val installedSignatures = getInstalledSignatures(pm, expectedPackage)

        if (installedSignatures.isNotEmpty() && archiveSignatures.isNotEmpty()) {
            val hasMatchingCertificate = archiveSignatures.any { archSig ->
                installedSignatures.any { instSig ->
                    Arrays.equals(archSig, instSig)
                }
            }
            if (!hasMatchingCertificate) {
                return ApkValidationResult.CertificateMismatch
            }
        }

        return ApkValidationResult.Valid
    }

    @Suppress("DEPRECATION")
    private fun getArchiveSignatures(archiveInfo: android.content.pm.PackageInfo): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
                ?: archiveInfo.signingInfo?.signingCertificateHistory?.map { it.toByteArray() }
                ?: emptyList()
        } else {
            archiveInfo.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledSignatures(pm: PackageManager, packageName: String): List<ByteArray> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
                    ?: info.signingInfo?.signingCertificateHistory?.map { it.toByteArray() }
                    ?: emptyList()
            } else {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
