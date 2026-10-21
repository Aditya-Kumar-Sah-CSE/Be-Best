package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_sessions",
    indices = [
        Index("packageName"),
        Index("sessionDate")
    ]
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val source: String, // "ACCESSIBILITY" or "USAGE_STATS"
    val sessionDate: String // "yyyy-MM-dd"
)
