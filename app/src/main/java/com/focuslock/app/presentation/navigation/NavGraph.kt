package com.focuslock.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.focuslock.app.presentation.apps.AppDetailsScreen
import com.focuslock.app.presentation.apps.AppSelectionScreen
import com.focuslock.app.presentation.dashboard.DashboardScreen
import com.focuslock.app.presentation.focus.FocusModeScreen
import com.focuslock.app.presentation.onboarding.OnboardingScreen
import com.focuslock.app.presentation.settings.SettingsScreen

@Composable
fun FocusLockNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToApps = { navController.navigate(Screen.Apps.route) },
                onNavigateToAppDetails = { pkg -> navController.navigate(Screen.AppDetails.createRoute(pkg)) },
                onNavigateToFocus = { navController.navigate(Screen.Focus.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Apps.route) {
            AppSelectionScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetails = { pkg -> navController.navigate(Screen.AppDetails.createRoute(pkg)) }
            )
        }

        composable(
            route = Screen.AppDetails.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailsScreen(
                packageName = pkg,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Focus.route) {
            FocusModeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
