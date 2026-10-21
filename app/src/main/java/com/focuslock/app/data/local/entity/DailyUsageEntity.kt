package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_usage",
    indices = [
        Index(value = ["packageName", "localDate"], unique = true)
    ]
)
data class DailyUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val localDate: String, // "yyyy-MM-dd"
    val timezoneId: String,
    val usedSeconds: Long = 0L,
    val quotaSeconds: Long = 1800L,
    val isBlocked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
