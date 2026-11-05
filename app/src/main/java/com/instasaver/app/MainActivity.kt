package com.instasaver.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.instasaver.app.data.local.ThemeMode
import com.instasaver.app.network.InstagramUrlParser
import com.instasaver.app.ui.navigation.BottomNavBar
import com.instasaver.app.ui.navigation.NavGraph
import com.instasaver.app.ui.navigation.Screen
import com.instasaver.app.ui.theme.InstaSaverTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedUrlState by mutableStateOf<String?>(null)
    private var sharedMediaUriState by mutableStateOf<Uri?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val settingsRepo = remember { InstaSaverApp.instance.settingsRepository }
            val currentTheme by settingsRepo.themeMode.collectAsState(initial = ThemeMode.DARK)

            InstaSaverTheme(themeMode = currentTheme) {
                MainAppScaffold(
                    initialSharedUrl = sharedUrlState,
                    initialSharedMediaUri = sharedMediaUriState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: ""
            if (type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    val extracted = InstagramUrlParser.extractUrl(sharedText) ?: sharedText
                    sharedUrlState = extracted
                }
            } else if (type.startsWith("image/") || type.startsWith("video/")) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    sharedMediaUriState = uri
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    initialSharedUrl: String?,
    initialSharedMediaUri: Uri?
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val mediaImporter = remember { InstaSaverApp.instance.mediaImporter }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle shared media uri if received via intent
    LaunchedEffect(initialSharedMediaUri) {
        if (initialSharedMediaUri != null) {
            scope.launch {
                mediaImporter.importMedia(initialSharedMediaUri)
            }
        }
    }

    // Determine if bottom navigation bar should be visible
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Reels.route,
        Screen.Downloads.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            initialSharedUrl = initialSharedUrl
        )
    }
}
