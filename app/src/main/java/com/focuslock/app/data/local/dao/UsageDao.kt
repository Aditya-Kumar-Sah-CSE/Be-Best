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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyUsage(dailyUsage: DailyUsageEntity)

    @Query("UPDATE daily_usage SET usedSeconds = usedSeconds + :additionalSeconds, updatedAt = :timestamp, isBlocked = (usedSeconds + :additionalSeconds >= quotaSeconds) WHERE packageName = :packageName AND localDate = :date")
    suspend fun incrementUsage(packageName: String, date: String, additionalSeconds: Long, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageSession(session: UsageSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProtectionEvent(event: ProtectionEventEntity)

    @Query("SELECT * FROM protection_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentProtectionEventsFlow(): Flow<List<ProtectionEventEntity>>

    @Query("SELECT SUM(usedSeconds) FROM daily_usage WHERE localDate = :date")
    suspend fun getTotalUsedSecondsForDate(date: String): Long?

    @Query("SELECT COUNT(*) FROM daily_usage WHERE localDate = :date AND isBlocked = 1")
    suspend fun getBlockedAppsCount(date: String): Int
}
