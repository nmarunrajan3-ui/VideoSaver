package com.instasaver.app.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "Home")
    data object Reels : Screen("reels", "Reels")
    data object Videos : Screen("videos", "Videos")
    data object Photos : Screen("photos", "Photos")
    data object Stories : Screen("stories", "Stories")
    data object Downloads : Screen("downloads", "Downloads")
    data object Settings : Screen("settings", "Settings")
    data object About : Screen("about", "About")

    data object DownloadDetails : Screen("details/{downloadId}", "Details") {
        fun createRoute(downloadId: Long): String = "details/$downloadId"
    }

    data object VideoPlayer : Screen("player/{downloadId}", "Player") {
        fun createRoute(downloadId: Long): String = "player/$downloadId"
    }
}
