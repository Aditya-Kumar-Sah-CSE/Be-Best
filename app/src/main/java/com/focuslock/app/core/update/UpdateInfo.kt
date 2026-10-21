package com.focuslock.app.core.update

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSha256: String,
    val apkSizeBytes: Long = 0L,
    val minSupportedVersionCode: Long = 1L,
    val isMandatory: Boolean = false
)
