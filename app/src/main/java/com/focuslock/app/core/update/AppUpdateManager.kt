package com.focuslock.app.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AppUpdateManager(
    private val metadataUrl: String = DEFAULT_METADATA_URL
) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var isDismissedForSession = false
    private var lastCheckedTimestamp = 0L

    fun getInstalledVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && isDismissedForSession) {
            return@withContext null
        }
        // Throttling: If checked within last 60 seconds and not forced, return cached state
        if (!force && (now - lastCheckedTimestamp) < 60_000L && _updateState.value is UpdateState.UpdateAvailable) {
            return@withContext (_updateState.value as UpdateState.UpdateAvailable).info
        }

        _updateState.value = UpdateState.Checking

        try {
            val jsonString = fetchMetadataString(metadataUrl)
            val info = parseMetadata(jsonString)
            lastCheckedTimestamp = System.currentTimeMillis()

            val currentCode = getInstalledVersionCode(context)
            if (info.versionCode > currentCode) {
                if (currentCode >= info.minSupportedVersionCode) {
                    _updateState.value = UpdateState.UpdateAvailable(info)
                    return@withContext info
                } else {
                    _updateState.value = UpdateState.Error(
                        "Your current version is too old for automatic update. Please re-install fresh.",
                        canRetry = false
                    )
                    return@withContext null
                }
            } else {
                _updateState.value = UpdateState.UpToDate
                return@withContext null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errorMsg = when (e) {
                is java.net.UnknownHostException -> "No internet connection. Please check your network and try again."
                is java.net.SocketTimeoutException -> "Connection timed out while checking for updates."
                is java.net.ConnectException -> "Could not connect to update server. Please try again later."
                else -> "Failed to check for updates: ${e.localizedMessage ?: "Network error"}"
            }
            _updateState.value = UpdateState.Error(errorMsg)
            return@withContext null
        }
    }

    suspend fun downloadAndVerifyUpdate(context: Context, updateInfo: UpdateInfo): File? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Downloading(
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = updateInfo.apkSizeBytes,
            info = updateInfo
        )

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        val tempApkFile = File(updatesDir, "focuslock_update_${updateInfo.versionCode}.apk")
        if (tempApkFile.exists()) tempApkFile.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = openConnectionWithRedirects(updateInfo.apkUrl)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("Server returned HTTP $responseCode")
            }

            val contentLength = connection.contentLengthLong.let { if (it > 0) it else updateInfo.apkSizeBytes }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (contentLength > 0) {
                            ((totalDownloaded * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else 0

                        _updateState.value = UpdateState.Downloading(
                            progressPercent = percent,
                            downloadedBytes = totalDownloaded,
                            totalBytes = contentLength,
                            info = updateInfo
                        )
                    }
                    output.flush()

                    if (contentLength > 0 && totalDownloaded < contentLength) {
                        throw java.io.IOException("Incomplete download: received $totalDownloaded of $contentLength bytes")
                    }
                }
            }

            // Cryptographic and APK provenance validation
            val validation = ApkSecurityValidator.validateApk(
                context = context,
                apkFile = tempApkFile,
                expectedSha256 = updateInfo.apkSha256,
                expectedPackage = context.packageName,
                minVersionCode = updateInfo.versionCode
            )

            when (validation) {
                is ApkValidationResult.Valid -> {
                    _updateState.value = UpdateState.ReadyToInstall(tempApkFile, updateInfo)
                    return@withContext tempApkFile
                }
                is ApkValidationResult.ChecksumMismatch -> {
                    tempApkFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Security verification failed: SHA-256 checksum mismatch. Download may be corrupted."
                    )
                    return@withContext null
                }
                is ApkValidationResult.PackageMismatch -> {
                    tempApkFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Security verification failed: Package name mismatch (${validation.found})."
                    )
                    return@withContext null
                }
                is ApkValidationResult.VersionMismatch -> {
                    tempApkFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Security verification failed: APK version ${validation.foundVersion} is lower than required version ${validation.expectedMinVersion}."
                    )
                    return@withContext null
                }
                is ApkValidationResult.CertificateMismatch -> {
                    tempApkFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Security verification failed: Developer signing certificate mismatch."
                    )
                    return@withContext null
                }
                is ApkValidationResult.InvalidArchive -> {
                    tempApkFile.delete()
                    _updateState.value = UpdateState.Error(
                        "Security verification failed: Malformed APK file (${validation.reason})."
                    )
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            tempApkFile.delete()
            val errorMsg = when (e) {
                is java.net.UnknownHostException -> "Download failed: No internet connection."
                is java.net.SocketTimeoutException -> "Download timed out. Please check your internet connection."
                else -> "Download failed: ${e.localizedMessage ?: "Unknown network error"}"
            }
            _updateState.value = UpdateState.Error(errorMsg)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    fun promptInstall(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            _updateState.value = UpdateState.Error("Downloaded update file not found.")
            return false
        }

        // On Android 8.0+ (Oreo), check if app has permission to request package installs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return false
            }
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Could not launch package installer: ${e.localizedMessage}")
            false
        }
    }

    fun dismissForSession(context: Context? = null, versionCode: Long = 0L) {
        isDismissedForSession = true
        _updateState.value = UpdateState.DismissedForSession
        if (context != null && versionCode > 0L) {
            val prefs = context.getSharedPreferences("focuslock_app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("dismissed_update_version", versionCode).apply()
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    private fun fetchMetadataString(urlStr: String): String {
        val cacheBusterUrl = if (urlStr.contains("?")) "$urlStr&_cb=${System.currentTimeMillis()}" else "$urlStr?_cb=${System.currentTimeMillis()}"
        val conn = openConnectionWithRedirects(cacheBusterUrl)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "FocusLock-Android-Updater")
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            conn.setRequestProperty("Pragma", "no-cache")
            conn.connect()

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val newLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (newLocation.isNullOrBlank()) {
                    throw Exception("Redirect HTTP $code without Location header")
                }
                currentUrl = if (newLocation.startsWith("http")) {
                    newLocation
                } else {
                    URL(url, newLocation).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
        val finalConn = URL(currentUrl).openConnection() as HttpURLConnection
        finalConn.connectTimeout = 15000
        finalConn.readTimeout = 20000
        finalConn.instanceFollowRedirects = false
        finalConn.useCaches = false
        finalConn.setRequestProperty("User-Agent", "FocusLock-Android-Updater")
        finalConn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        finalConn.setRequestProperty("Pragma", "no-cache")
        finalConn.connect()
        return finalConn
    }

    fun parseMetadata(jsonStr: String): UpdateInfo {
        val obj = JSONObject(jsonStr)
        val versionCode = obj.getLong("versionCode")
        val versionName = obj.optString("versionName", "")
        val apkUrl = obj.getString("apkUrl").trim()
        val apkSha256 = obj.getString("apkSha256").trim()
        val releaseNotes = obj.optString("releaseNotes", "General performance improvements and fixes")
        val apkSizeBytes = obj.optLong("apkSizeBytes", 0L)
        val minSupportedVersionCode = obj.optLong("minSupportedVersionCode", 1L)
        val isMandatory = obj.optBoolean("isMandatory", false)

        require(versionCode > 0) { "Invalid metadata: versionCode must be positive (found $versionCode)" }
        require(versionName.isNotBlank()) { "Invalid metadata: versionName cannot be blank" }
        require(apkUrl.startsWith("https://")) { "Security violation: apkUrl must use secure HTTPS protocol" }
        require(apkSha256.length == 64 && apkSha256.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "Security violation: apkSha256 must be a valid 64-character hexadecimal SHA-256 hash"
        }

        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            releaseNotes = releaseNotes,
            apkUrl = apkUrl,
            apkSha256 = apkSha256.uppercase(),
            apkSizeBytes = apkSizeBytes,
            minSupportedVersionCode = minSupportedVersionCode,
            isMandatory = isMandatory
        )
    }

    companion object {
        const val DEFAULT_METADATA_URL =
            "https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/release-metadata.json"

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppUpdateManager().also { INSTANCE = it }
            }
        }
    }
}
