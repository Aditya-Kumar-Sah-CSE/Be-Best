package com.focuslock.app.presentation.navigation

sealed class Screen(val route: String, val title: String) {
    data object Onboarding : Screen("onboarding", "Setup")
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object Apps : Screen("apps", "Manage Apps")
    data object Focus : Screen("focus", "Focus Mode")
    data object Settings : Screen("settings", "Security & Rules")
}
