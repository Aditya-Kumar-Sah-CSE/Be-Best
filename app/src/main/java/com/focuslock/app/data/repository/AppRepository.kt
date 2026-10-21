package com.focuslock.app.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isProtected: Boolean,
    val dailyLimitSeconds: Long
)

interface AppRepository {
    fun getProtectedAppsFlow(): Flow<List<ProtectedAppEntity>>
    suspend fun getInstalledApps(): List<InstalledAppInfo>
    suspend fun setAppProtection(packageName: String, appName: String, enabled: Boolean, limitSeconds: Long, category: AppCategory)
    suspend fun updateAppLimit(packageName: String, limitSeconds: Long)
    suspend fun getApp(packageName: String): ProtectedAppEntity?
}

class AppRepositoryImpl(
    private val dao: ProtectedAppDao,
    private val packageManager: PackageManager
) : AppRepository {

    override fun getProtectedAppsFlow(): Flow<List<ProtectedAppEntity>> {
        return dao.getAllProtectedAppsFlow()
    }

    override suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val protectedMap = dao.getActiveProtectedApps().associateBy { it.packageName }

        installedApps
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // Exclude standard system core
            .map { appInfo ->
                val pkg = appInfo.packageName
                val name = packageManager.getApplicationLabel(appInfo).toString()
                val existing = protectedMap[pkg]
                val category = existing?.let { AppCategory.fromString(it.category) }
                    ?: AppCategory.inferCategory(pkg)

                InstalledAppInfo(
                    packageName = pkg,
                    appName = name,
                    category = category,
                    isProtected = existing?.enabled == true,
                    dailyLimitSeconds = existing?.dailyLimitSeconds ?: 1800L
                )
            }
            .sortedByDescending { it.isProtected }
    }

    override suspend fun setAppProtection(
        packageName: String,
        appName: String,
        enabled: Boolean,
        limitSeconds: Long,
        category: AppCategory
    ) = withContext(Dispatchers.IO) {
        dao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = packageName,
                displayName = appName,
                dailyLimitSeconds = limitSeconds,
                enabled = enabled,
                category = category.name
            )
        )
    }

    override suspend fun updateAppLimit(packageName: String, limitSeconds: Long) = withContext(Dispatchers.IO) {
        dao.updateLimit(packageName, limitSeconds)
    }

    override suspend fun getApp(packageName: String): ProtectedAppEntity? = withContext(Dispatchers.IO) {
        dao.getAppByPackageName(packageName)
    }
}
