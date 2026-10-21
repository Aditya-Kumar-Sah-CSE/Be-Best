package com.focuslock.app.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.focuslock.app.presentation.analytics.AnalyticsScreen
import com.focuslock.app.presentation.apps.AppDetailsScreen
import com.focuslock.app.presentation.apps.AppSelectionScreen
import com.focuslock.app.presentation.dashboard.DashboardScreen
import com.focuslock.app.presentation.focus.FocusModeScreen
import com.focuslock.app.presentation.onboarding.OnboardingScreen
import com.focuslock.app.presentation.settings.PermissionStatusScreen
import com.focuslock.app.presentation.settings.SettingsScreen
import com.focuslock.app.presentation.splash.SplashScreen

@Composable
fun FocusLockNavGraph(
    navController: NavHostController,
    isOnboarded: Boolean,
    startDestination: String = Screen.Splash.route
) {
    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() + slideInHorizontally(initialOffsetX = { 300 }) },
        exitTransition = { fadeOut() + slideOutHorizontally(targetOffsetX = { -300 }) },
        popEnterTransition = { fadeIn() + slideInHorizontally(initialOffsetX = { -300 }) },
        popExitTransition = { fadeOut() + slideOutHorizontally(targetOffsetX = { 300 }) }
    ) {
        // Native 3D-style Animated Splash Screen (Cold start only)
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    val target = if (isOnboarded) Screen.Dashboard.route else Screen.Onboarding.route
                    navController.navigate(target) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding flow
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // 1. Home Dashboard
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToApps = { navigateTab(Screen.Apps.route) },
                onNavigateToAppDetails = { pkg -> navController.navigate(Screen.AppDetails.createRoute(pkg)) },
                onNavigateToFocus = { navigateTab(Screen.Focus.route) },
                onNavigateToAnalytics = { navigateTab(Screen.Analytics.route) },
                onNavigateToSettings = { navigateTab(Screen.Settings.route) }
            )
        }

        // 2. Protected Apps Screen (Tab mode)
        composable(Screen.Apps.route) {
            AppSelectionScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetails = { pkg -> navController.navigate(Screen.AppDetails.createRoute(pkg)) },
                showBottomBar = true,
                onNavigateToHome = { navigateTab(Screen.Dashboard.route) },
                onNavigateToFocus = { navigateTab(Screen.Focus.route) },
                onNavigateToAnalytics = { navigateTab(Screen.Analytics.route) },
                onNavigateToSettings = { navigateTab(Screen.Settings.route) }
            )
        }

        // App Details Screen
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

        // 3. Focus Mode Screen (Tab mode)
        composable(Screen.Focus.route) {
            FocusModeScreen(
                onBack = { navController.popBackStack() },
                showBottomBar = true,
                onNavigateToHome = { navigateTab(Screen.Dashboard.route) },
                onNavigateToApps = { navigateTab(Screen.Apps.route) },
                onNavigateToAnalytics = { navigateTab(Screen.Analytics.route) },
                onNavigateToSettings = { navigateTab(Screen.Settings.route) }
            )
        }

        // 4. Real Analytics Screen (Tab mode)
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateToHome = { navigateTab(Screen.Dashboard.route) },
                onNavigateToApps = { navigateTab(Screen.Apps.route) },
                onNavigateToFocus = { navigateTab(Screen.Focus.route) },
                onNavigateToSettings = { navigateTab(Screen.Settings.route) },
                onNavigateToAppDetails = { pkg -> navController.navigate(Screen.AppDetails.createRoute(pkg)) }
            )
        }

        // 5. Settings Screen (Tab mode)
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPermissions = { navController.navigate(Screen.PermissionStatus.route) },
                showBottomBar = true,
                onNavigateToHome = { navigateTab(Screen.Dashboard.route) },
                onNavigateToApps = { navigateTab(Screen.Apps.route) },
                onNavigateToFocus = { navigateTab(Screen.Focus.route) },
                onNavigateToAnalytics = { navigateTab(Screen.Analytics.route) }
            )
        }

        // Permission Diagnostics Screen
        composable(Screen.PermissionStatus.route) {
            PermissionStatusScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDashboard = { navigateTab(Screen.Dashboard.route) }
            )
        }
    }
}
