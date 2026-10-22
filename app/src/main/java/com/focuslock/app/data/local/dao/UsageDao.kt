package com.focuslock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectionEventEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Query("SELECT * FROM daily_usage WHERE localDate = :date")
    fun getDailyUsageForDateFlow(date: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND localDate = :date LIMIT 1")
    suspend fun getDailyUsageForApp(packageName: String, date: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName ORDER BY localDate DESC LIMIT 30")
    suspend fun getHistoricalUsageForApp(packageName: String): List<DailyUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyUsage(dailyUsage: DailyUsageEntity)

    @Query("UPDATE daily_usage SET usedSeconds = usedSeconds + :additionalSeconds, updatedAt = :timestamp, isBlocked = (usedSeconds + :additionalSeconds >= quotaSeconds) WHERE packageName = :packageName AND localDate = :date")
    suspend fun incrementUsage(packageName: String, date: String, additionalSeconds: Long, timestamp: Long)

    @Query("UPDATE daily_usage SET approximateScrollCount = approximateScrollCount + :count, lastActivityAt = :timestamp, updatedAt = :timestamp WHERE packageName = :packageName AND localDate = :date")
    suspend fun incrementScrollCount(packageName: String, date: String, count: Int = 1, timestamp: Long)

    @Query("UPDATE daily_usage SET sessionCount = :count, longestSessionSeconds = :longestSecs, firstActivityAt = :firstAt, lastActivityAt = :lastAt, updatedAt = :now WHERE packageName = :packageName AND localDate = :date")
    suspend fun updateSessionStats(
        packageName: String,
        date: String,
        count: Int,
        longestSecs: Long,
        firstAt: Long,
        lastAt: Long,
        now: Long
    )

    @Query("UPDATE daily_usage SET emergencyUsed = 1, emergencyStartedAt = :startedAt, emergencyExpiresAt = :expiresAt, emergencyElapsedRealtimeAtStart = :elapsedStart, updatedAt = :now WHERE packageName = :packageName AND localDate = :date")
    suspend fun recordEmergencySession(
        packageName: String,
        date: String,
        startedAt: Long,
        expiresAt: Long,
        elapsedStart: Long,
        now: Long
    )

    @Query("UPDATE daily_usage SET warning10Triggered = 1, updatedAt = :now WHERE packageName = :packageName AND localDate = :date")
    suspend fun markWarning10Triggered(packageName: String, date: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE daily_usage SET warning5Triggered = 1, updatedAt = :now WHERE packageName = :packageName AND localDate = :date")
    suspend fun markWarning5Triggered(packageName: String, date: String, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageSession(session: UsageSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageSessions(sessions: List<UsageSessionEntity>)

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND sessionDate = :date ORDER BY startedAt ASC")
    suspend fun getSessionsForAppAndDate(packageName: String, date: String): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions WHERE packageName = :packageName AND sessionDate = :date")
    suspend fun deleteSessionsForAppAndDate(packageName: String, date: String)

    @Transaction
    suspend fun replaceSessionsForAppAndDate(packageName: String, date: String, sessions: List<UsageSessionEntity>) {
        deleteSessionsForAppAndDate(packageName, date)
        if (sessions.isNotEmpty()) {
            insertUsageSessions(sessions)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProtectionEvent(event: ProtectionEventEntity)

    @Query("SELECT * FROM protection_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentProtectionEventsFlow(): Flow<List<ProtectionEventEntity>>

    @Query("SELECT SUM(usedSeconds) FROM daily_usage WHERE localDate = :date")
    suspend fun getTotalUsedSecondsForDate(date: String): Long?

    @Query("SELECT COUNT(*) FROM daily_usage WHERE localDate = :date AND isBlocked = 1")
    suspend fun getBlockedAppsCount(date: String): Int
}
