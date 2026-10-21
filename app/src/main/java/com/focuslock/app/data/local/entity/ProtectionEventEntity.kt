package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protection_events")
data class ProtectionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: String, // "SERVICE_RESTORED", "TIMEZONE_CHANGED", "LIMIT_EXHAUSTED", "REBOOT"
    val timestamp: Long = System.currentTimeMillis(),
    val detailsCode: String
)
