package com.focuslock.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.presentation.navigation.FocusLockNavGraph
import com.focuslock.app.presentation.navigation.Screen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("focuslock_app_prefs", MODE_PRIVATE)
        val isOnboarded = prefs.getBoolean("is_onboarded", false)

        setContent {
            FocusLockTheme {
                val navController = rememberNavController()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AmoledBackground)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    FocusLockNavGraph(
                        navController = navController,
                        startDestination = if (isOnboarded) Screen.Dashboard.route else Screen.Onboarding.route
                    )
                }
            }
        }
    }
}
