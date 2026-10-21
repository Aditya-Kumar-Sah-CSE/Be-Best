package com.focuslock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT 20")
    fun getRecentSessionsFlow(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("UPDATE focus_sessions SET status = :status, actualEndAt = :endAt WHERE id = :sessionId")
    suspend fun completeSession(sessionId: Long, status: String, endAt: Long)
}
