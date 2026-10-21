package com.focuslock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedAppDao {

    @Query("SELECT * FROM protected_apps ORDER BY displayName ASC")
    fun getAllProtectedAppsFlow(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT * FROM protected_apps WHERE enabled = 1")
    suspend fun getActiveProtectedApps(): List<ProtectedAppEntity>

    @Query("SELECT * FROM protected_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): ProtectedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: ProtectedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<ProtectedAppEntity>)

    @Update
    suspend fun update(app: ProtectedAppEntity)

    @Delete
    suspend fun delete(app: ProtectedAppEntity)

    @Query("UPDATE protected_apps SET dailyLimitSeconds = :limitSeconds WHERE packageName = :packageName")
    suspend fun updateLimit(packageName: String, limitSeconds: Long)
}
