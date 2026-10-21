package com.focuslock.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.focuslock.app.data.local.dao.FocusSessionDao
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.dao.UserSettingsDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.FocusSessionEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.data.local.entity.ProtectionEventEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import com.focuslock.app.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        ProtectedAppEntity::class,
        UsageSessionEntity::class,
        DailyUsageEntity::class,
        FocusSessionEntity::class,
        ProtectionEventEntity::class,
        UserSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FocusLockDatabase : RoomDatabase() {

    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun usageDao(): UsageDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: FocusLockDatabase? = null

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN emergencyUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN emergencyStartedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN emergencyExpiresAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN emergencyElapsedRealtimeAtStart INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): FocusLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusLockDatabase::class.java,
                    "focuslock_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
