package com.focuslock.app.core.update

import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: UpdateInfo
    ) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val info: UpdateInfo) : UpdateState()
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateState()
    data class UpToDate(val isManualCheck: Boolean = false) : UpdateState()
    object DismissedForSession : UpdateState()
}
