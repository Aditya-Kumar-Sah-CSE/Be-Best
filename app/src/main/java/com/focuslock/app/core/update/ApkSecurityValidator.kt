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
    data class VersionMismatch(val expectedMinVersion: Long, val foundVersion: Long) : ApkValidationResult()
    object CertificateMismatch : ApkValidationResult()
}

object ApkSecurityValidator {

    // SHA-256 fingerprint of the official FocusLock signing certificate
    const val FOCUSLOCK_RELEASE_CERT_SHA256 = "5DFC7E8D13125F283874DDC3FCB9DE6E8D07BE4C715C9083D38CA7EB4E435FFA"

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

    fun calculateCertSha256(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(certBytes)
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    fun validateApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedPackage: String = context.packageName,
        minVersionCode: Long = 0L
    ): ApkValidationResult {
        // 1. Checksum validation
        val actualSha256 = calculateSha256(apkFile)
        if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            return ApkValidationResult.ChecksumMismatch(expected = expectedSha256, actual = actualSha256)
        }

        // 2. Archive validity check
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            ?: return ApkValidationResult.InvalidArchive("Package manager could not parse APK archive")

        archiveInfo.applicationInfo?.let {
            it.sourceDir = apkFile.absolutePath
            it.publicSourceDir = apkFile.absolutePath
        }

        // 3. Package name verification
        if (archiveInfo.packageName != expectedPackage) {
            return ApkValidationResult.PackageMismatch(
                expected = expectedPackage,
                found = archiveInfo.packageName ?: "unknown"
            )
        }

        // 4. Version verification (prevent downgrade attacks)
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        if (minVersionCode > 0 && archiveVersionCode < minVersionCode) {
            return ApkValidationResult.VersionMismatch(
                expectedMinVersion = minVersionCode,
                foundVersion = archiveVersionCode
            )
        }

        // 5. Signing certificate verification against installed app and official certificate
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
        } else if (archiveSignatures.isNotEmpty()) {
            val matchesOfficialCert = archiveSignatures.any { sigBytes ->
                val certHash = calculateCertSha256(sigBytes)
                certHash.equals(FOCUSLOCK_RELEASE_CERT_SHA256, ignoreCase = true)
            }
            if (!matchesOfficialCert) {
                return ApkValidationResult.CertificateMismatch
            }
        }

        return ApkValidationResult.Valid
    }

    @Suppress("DEPRECATION")
    private fun getArchiveSignatures(archiveInfo: android.content.pm.PackageInfo): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.signingInfo?.apkContentsSigners?.forEach { certs.add(it.toByteArray()) }
            if (certs.isEmpty()) {
                archiveInfo.signingInfo?.signingCertificateHistory?.forEach { certs.add(it.toByteArray()) }
            }
        }
        if (certs.isEmpty()) {
            archiveInfo.signatures?.forEach { certs.add(it.toByteArray()) }
        }
        return certs
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
