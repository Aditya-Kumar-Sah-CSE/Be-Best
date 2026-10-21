package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val defaultDailyLimitSeconds: Long = 1800L,
    val isHardcoreEnabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val lastHardcoreUnlockRequest: Long = 0L,
    val dailyResetHour: Int = 0 // Midnight
)
