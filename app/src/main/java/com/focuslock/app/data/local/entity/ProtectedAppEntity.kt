package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protected_apps")
data class ProtectedAppEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val dailyLimitSeconds: Long = 1800L, // 30 minutes default
    val enabled: Boolean = true,
    val category: String = "SOCIAL",
    val createdAt: Long = System.currentTimeMillis()
)
