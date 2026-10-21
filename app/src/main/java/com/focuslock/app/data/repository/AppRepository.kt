package com.focuslock.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isProtected: Boolean,
    val dailyLimitSeconds: Long,
    val icon: Drawable? = null
)

interface AppRepository {
    fun getProtectedAppsFlow(): Flow<List<ProtectedAppEntity>>
    suspend fun getActiveProtectedApps(): List<ProtectedAppEntity>
    suspend fun getInstalledLaunchableApps(): List<InstalledAppItem>
    suspend fun setAppProtection(packageName: String, appName: String, enabled: Boolean, limitSeconds: Long, category: AppCategory)
    suspend fun updateAppLimit(packageName: String, limitSeconds: Long)
    suspend fun getApp(packageName: String): ProtectedAppEntity?
    suspend fun selectAllSuggestedDistractions(defaultLimitSeconds: Long = 1800L)
    suspend fun deselectAllApps()
    fun getAppIcon(packageName: String): Drawable?
}

class AppRepositoryImpl(
    private val dao: ProtectedAppDao,
    private val context: Context
) : AppRepository {

    private val packageManager: PackageManager = context.packageManager

    override fun getProtectedAppsFlow(): Flow<List<ProtectedAppEntity>> {
        return dao.getAllProtectedAppsFlow()
    }

    override suspend fun getActiveProtectedApps(): List<ProtectedAppEntity> = withContext(Dispatchers.IO) {
        dao.getActiveProtectedApps()
    }

    override suspend fun getInstalledLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val protectedMap = dao.getActiveProtectedApps().associateBy { it.packageName }
        val ownPackage = context.packageName

        resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { appInfo ->
                val pkg = appInfo.packageName
                val name = appInfo.loadLabel(packageManager).toString()
                val icon = try {
                    appInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                val existing = protectedMap[pkg]
                val category = existing?.let { AppCategory.fromString(it.category) }
                    ?: AppCategory.inferCategory(pkg, appInfo)

                InstalledAppItem(
                    packageName = pkg,
                    appName = name,
                    category = category,
                    isProtected = existing?.enabled == true,
                    dailyLimitSeconds = existing?.dailyLimitSeconds ?: 1800L,
                    icon = icon
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppItem> { it.isProtected }
                    .thenByDescending { it.category.isSuggestedDistraction }
                    .thenBy { it.appName.lowercase() }
            )
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

    override suspend fun selectAllSuggestedDistractions(defaultLimitSeconds: Long) = withContext(Dispatchers.IO) {
        val installed = getInstalledLaunchableApps()
        val suggested = installed.filter { it.category.isSuggestedDistraction }
        val entities = suggested.map { app ->
            ProtectedAppEntity(
                packageName = app.packageName,
                displayName = app.appName,
                dailyLimitSeconds = defaultLimitSeconds,
                enabled = true,
                category = app.category.name
            )
        }
        dao.insertAll(entities)
    }

    override suspend fun deselectAllApps() = withContext(Dispatchers.IO) {
        val active = dao.getActiveProtectedApps()
        val disabled = active.map { it.copy(enabled = false) }
        dao.insertAll(disabled)
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
}
