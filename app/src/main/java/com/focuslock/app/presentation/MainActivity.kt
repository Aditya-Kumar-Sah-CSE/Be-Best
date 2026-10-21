package com.focuslock.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.core.designsystem.ThemeManager
import com.focuslock.app.core.designsystem.ThemeMode
import com.focuslock.app.core.update.AppUpdateManager
import com.focuslock.app.core.update.UpdateState
import com.focuslock.app.presentation.navigation.FocusLockNavGraph
import com.focuslock.app.presentation.navigation.Screen
import com.focuslock.app.presentation.update.UpdateDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val updateManager by lazy { AppUpdateManager.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.init(this)
        val prefs = getSharedPreferences("focuslock_app_prefs", MODE_PRIVATE)
        val isOnboarded = prefs.getBoolean("is_onboarded", false)

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            FocusLockTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                val updateState by updateManager.updateState.collectAsState()

                // Check for updates silently in the background on startup (non-blocking)
                LaunchedEffect(Unit) {
                    updateManager.checkForUpdates(this@MainActivity, force = false)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    FocusLockNavGraph(
                        navController = navController,
                        isOnboarded = isOnboarded,
                        startDestination = Screen.Splash.route
                    )

                    // Global In-App Update Dialog overlay
                    UpdateDialog(
                        updateState = updateState,
                        currentVersionName = remember { updateManager.getInstalledVersionName(this@MainActivity) },
                        currentVersionCode = remember { updateManager.getInstalledVersionCode(this@MainActivity) },
                        onUpdateNow = { info ->
                            coroutineScope.launch {
                                updateManager.downloadAndVerifyUpdate(this@MainActivity, info)
                            }
                        },
                        onInstallNow = { apkFile, _ ->
                            updateManager.promptInstall(this@MainActivity, apkFile)
                        },
                        onDismiss = {
                            val verCode = (updateState as? UpdateState.UpdateAvailable)?.info?.versionCode ?: 0L
                            updateManager.dismissForSession(this@MainActivity, verCode)
                        },
                        onRetry = {
                            coroutineScope.launch {
                                updateManager.checkForUpdates(this@MainActivity, force = true)
                            }
                        }
                    )
                }
            }
        }
    }
}
