package com.focuslock.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.focuslock.app.data.local.dao.FocusSessionDao
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.FocusSessionEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.data.local.entity.ProtectionEventEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity

@Database(
    entities = [
        ProtectedAppEntity::class,
        UsageSessionEntity::class,
        DailyUsageEntity::class,
        FocusSessionEntity::class,
        ProtectionEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FocusLockDatabase : RoomDatabase() {

    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun usageDao(): UsageDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: FocusLockDatabase? = null

        fun getInstance(context: Context): FocusLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusLockDatabase::class.java,
                    "focuslock_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
