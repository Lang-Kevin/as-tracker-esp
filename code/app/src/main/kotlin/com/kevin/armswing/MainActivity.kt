package com.kevin.armswing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.armswing.service.ArmSwingRecordingService
import com.kevin.armswing.ui.detail.DetailScreen
import com.kevin.armswing.ui.history.HistoryScreen
import com.kevin.armswing.ui.settings.SettingsScreen
import com.kevin.armswing.ui.live.LiveScreen
import com.kevin.armswing.ui.scan.ScanScreen
import com.kevin.armswing.ui.scan.ScanViewModel
import com.kevin.armswing.ui.theme.ArmSwingTheme
import dagger.hilt.android.AndroidEntryPoint

private object Route {
    const val SCAN     = "scan"
    const val LIVE     = "live"
    const val HISTORY  = "history"
    const val DETAIL   = "detail/{sessionId}"
    const val SETTINGS = "settings"
    fun detail(id: Long) = "detail/$id"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArmSwingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArmSwingNav()
                }
            }
        }
    }

    @Composable
    private fun ArmSwingNav() {
        val navController = rememberNavController()
        val scanViewModel: ScanViewModel = hiltViewModel()
        val activeSessionId by scanViewModel.activeSessionId.collectAsStateWithLifecycle()

        LaunchedEffect(activeSessionId) {
            if (activeSessionId != null) {
                navController.navigate(Route.LIVE) { launchSingleTop = true }
            }
        }

        NavHost(navController = navController, startDestination = Route.SCAN) {

            composable(Route.SCAN) {
                ScanScreen(
                    viewModel = scanViewModel,
                    onSessionStarted = { label -> startRecordingService(label) },
                    onNavigateToHistory = { navController.navigate(Route.HISTORY) },
                    onNavigateToSettings = { navController.navigate(Route.SETTINGS) }
                )
            }

            composable(Route.LIVE) {
                LiveScreen(onStopSession = {
                    scanViewModel.stopSession()
                    stopService(ArmSwingRecordingService.stopIntent(this@MainActivity))
                    navController.popBackStack()
                })
            }

            composable(Route.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onSessionClick = { id -> navController.navigate(Route.detail(id)) }
                )
            }

            composable(
                route = Route.DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                DetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Route.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    private fun startRecordingService(label: String) {
        val intent = ArmSwingRecordingService.startIntent(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
