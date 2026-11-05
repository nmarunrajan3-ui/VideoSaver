package com.instasaver.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.instasaver.app.ui.screens.about.AboutScreen
import com.instasaver.app.ui.screens.details.DownloadDetailsScreen
import com.instasaver.app.ui.screens.downloads.DownloadsScreen
import com.instasaver.app.ui.screens.home.HomeScreen
import com.instasaver.app.ui.screens.photos.PhotosScreen
import com.instasaver.app.ui.screens.player.VideoPlayerScreen
import com.instasaver.app.ui.screens.reels.ReelsScreen
import com.instasaver.app.ui.screens.settings.SettingsScreen
import com.instasaver.app.ui.screens.stories.StoriesScreen
import com.instasaver.app.ui.screens.videos.VideosScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    initialSharedUrl: String? = null
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(250)) },
        popEnterTransition = { fadeIn(animationSpec = tween(250)) },
        popExitTransition = { fadeOut(animationSpec = tween(250)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCategory = { route -> navController.navigate(route) },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                initialSharedUrl = initialSharedUrl
            )
        }

        composable(Screen.Reels.route) {
            ReelsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) },
                onNavigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.Videos.route) {
            VideosScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) },
                onNavigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.Photos.route) {
            PhotosScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.Stories.route) {
            StoriesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) }
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.DownloadDetails.createRoute(id)) },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) },
                onNavigateToHome = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.DownloadDetails.route,
            arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: -1L
            DownloadDetailsScreen(
                downloadId = downloadId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) }
            )
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: -1L
            VideoPlayerScreen(
                downloadId = downloadId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
