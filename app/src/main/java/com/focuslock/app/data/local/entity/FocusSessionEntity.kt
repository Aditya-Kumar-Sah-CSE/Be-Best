package com.focuslock.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val actualEndAt: Long = 0L,
    val status: String // "ACTIVE", "COMPLETED", "INTERRUPTED"
)
