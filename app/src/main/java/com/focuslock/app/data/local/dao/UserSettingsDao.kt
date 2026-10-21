package com.focuslock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: UserSettingsEntity)

    @Query("UPDATE user_settings SET themeMode = :themeMode WHERE id = 1")
    suspend fun updateThemeMode(themeMode: String)

    @Query("UPDATE user_settings SET defaultDailyLimitSeconds = :limitSeconds WHERE id = 1")
    suspend fun updateDefaultDailyLimit(limitSeconds: Long)

    @Query("UPDATE user_settings SET isHardcoreEnabled = :enabled WHERE id = 1")
    suspend fun updateHardcoreMode(enabled: Boolean)

    @Query("UPDATE user_settings SET lastHardcoreUnlockRequest = :timestamp WHERE id = 1")
    suspend fun updateHardcoreUnlockRequest(timestamp: Long)

    @Query("UPDATE user_settings SET pinHash = :hash, pinSalt = :salt WHERE id = 1")
    suspend fun updatePin(hash: String?, salt: String?)
}
